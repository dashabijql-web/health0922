package com.xzkj.hv2ops;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * 把底图发布到 GeoServer（docs/06 第三节"③ 发布"、docs/11）：和 tools/map-prep/publish.sh 做的事一样，
 * 改成 Java 是为了在 Windows 服务器上不用装 bash 也能跑。可以反复运行，结果一样：
 * 删掉工作区 hv2 再重建 → 数据源 mine_map（GeoPackage）→ 图层 roadway_line、roadway_label（EPSG:4527）→ 样式。
 * 样式文件打包在 jar 里（来自 tools/map-prep/styles）。管理员密码读环境变量 GEOSERVER_ADMIN_PASSWORD，没设时提示输入。
 */
final class MapPublisher {

    private static final List<String> LAYERS = List.of("roadway_line", "roadway_label");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base;
    private final String auth;

    private MapPublisher(String base, String user, String password) {
        this.base = base.replaceAll("/+$", "");
        this.auth = "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    static int run(List<String> args, Prompt prompt) {
        String gpkg = option(args, "--gpkg", null);
        if (gpkg == null) {
            throw new OpsException("用法：publish-map --gpkg <GeoServer 能读到的 mine-map.gpkg 路径> "
                    + "[--geoserver http://127.0.0.1:8082/geoserver] [--user admin]");
        }
        String password = System.getenv("GEOSERVER_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            password = prompt.secret("GeoServer 管理员密码：");
        }
        MapPublisher p = new MapPublisher(option(args, "--geoserver", "http://127.0.0.1:8082/geoserver"),
                option(args, "--user", "admin"), password);
        p.publish(databaseUrl(gpkg));
        return 0;
    }

    /** GeoPackage 数据源的 database 参数：Windows 路径 D:\a\b.gpkg 写成 file:D:/a/b.gpkg */
    static String databaseUrl(String path) {
        String p = path.replace('\\', '/');
        return p.startsWith("file:") ? p : "file:" + p;
    }

    private void publish(String database) {
        System.out.println("GeoServer " + base);
        send("GET", "/rest/about/version.json", null, null, Set.of(200));
        System.out.println("重建工作区 hv2");
        send("DELETE", "/rest/workspaces/hv2?recurse=true", null, null, Set.of(200, 404));
        send("POST", "/rest/workspaces", "application/json", "{\"workspace\":{\"name\":\"hv2\"}}", Set.of(201));
        System.out.println("数据源 mine_map：" + database);
        send("POST", "/rest/workspaces/hv2/datastores", "application/json", """
                {"dataStore": {"name": "mine_map", "description": "矿图底图（tools/map-prep 生成）",
                  "connectionParameters": {"entry": [
                    {"@key": "dbtype", "$": "geopkg"},
                    {"@key": "database", "$": %s},
                    {"@key": "read_only", "$": "true"}]}}}""".formatted(OpLog.json(database)), Set.of(201));
        for (String layer : LAYERS) {
            System.out.println("图层 hv2:" + layer);
            send("POST", "/rest/workspaces/hv2/datastores/mine_map/featuretypes", "application/json", """
                    {"featureType": {"name": "%s", "nativeName": "%s",
                      "srs": "EPSG:4527", "projectionPolicy": "FORCE_DECLARED"}}""".formatted(layer, layer),
                    Set.of(201));
            send("POST", "/rest/workspaces/hv2/styles?name=" + layer, "application/vnd.ogc.sld+xml",
                    style(layer), Set.of(201));
            send("PUT", "/rest/layers/hv2:" + layer, "application/json",
                    "{\"layer\":{\"defaultStyle\":{\"name\":\"" + layer + "\",\"workspace\":\"hv2\"}}}", Set.of(200));
        }
        System.out.println("完成。浏览器经 nginx 的 /geoserver/hv2/wms 取图（WMS 1.1.1，先东后北）");
    }

    private void send(String method, String path, String contentType, String body, Set<Integer> ok) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(60)).header("Authorization", auth);
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> resp;
        try {
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new OpsException("连不上 GeoServer " + base + "：" + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpsException("被中断");
        }
        if (!ok.contains(resp.statusCode())) {
            String text = resp.body() == null ? "" : resp.body();
            throw new OpsException(method + " " + path + " 返回 HTTP " + resp.statusCode()
                    + (resp.statusCode() == 401 ? "（管理员账号或密码不对）" : "") + "："
                    + text.substring(0, Math.min(300, text.length())));
        }
    }

    private static String style(String layer) {
        try (InputStream in = MapPublisher.class.getResourceAsStream("/map-styles/" + layer + ".sld")) {
            if (in == null) {
                throw new OpsException("jar 里缺少样式 " + layer + ".sld");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OpsException("读样式失败：" + layer);
        }
    }

    private static String option(List<String> args, String name, String def) {
        int i = args.indexOf(name);
        return i >= 0 && i + 1 < args.size() ? args.get(i + 1) : def;
    }
}
