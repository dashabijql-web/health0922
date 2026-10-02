package com.xzkj.hv2ops;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * health-v2 部署工具（docs/11）。服务器不能联网、不装 Maven，只要 Java 21：
 * <pre>
 *   java -jar hv2-ops.jar migrate | info | validate          建表迁移（用建表账号）
 *   java -jar hv2-ops.jar set-password &lt;登录名&gt;              改密码，运行时输入，不回显
 *   java -jar hv2-ops.jar import-devices &lt;清单.csv&gt; [--apply] 导入手表绑定清单，不加 --apply 只检查
 *   java -jar hv2-ops.jar publish-map --gpkg &lt;路径&gt; [--geoserver &lt;地址&gt;]  发布底图到 GeoServer
 *   java -jar hv2-ops.jar soak [--url ...] [--minutes 1440] [--out soak.csv]   稳定性观察，每分钟采一次指标
 * </pre>
 * 数据库连接读环境变量 ORACLE_HOST、ORACLE_PORT、ORACLE_SERVICE、ORACLE_USER（可选 ORACLE_SCHEMA）；
 * 密码不设 ORACLE_PASSWORD 时运行中提示输入。
 */
public final class OpsMain {

    private OpsMain() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        List<String> rest = Arrays.asList(args).subList(1, args.length);
        try {
            int code = switch (args[0]) {
                case "migrate", "info", "validate" -> Migrate.run(args[0], Db.fromEnv());
                case "set-password" -> {
                    requireArgs(rest, 1, "set-password <登录名>");
                    yield SetPassword.run(Db.fromEnv(), rest.get(0), Prompt.system());
                }
                case "import-devices" -> {
                    requireArgs(rest, 1, "import-devices <清单.csv> [--apply]");
                    yield DeviceImport.run(Db.fromEnv(), Path.of(rest.get(0)), rest.contains("--apply"));
                }
                case "publish-map" -> MapPublisher.run(rest, Prompt.system());
                case "soak" -> Soak.run(rest);
                case "soak-summary" -> Soak.summaryOnly(rest);
                default -> {
                    usage();
                    yield 2;
                }
            };
            System.exit(code);
        } catch (OpsException e) {
            System.err.println("失败：" + e.getMessage());
            System.exit(1);
        } catch (RuntimeException | LinkageError e) {
            // 意外的程序错误：照样打出调用堆栈，方便排查（这里不会有密码，密码只在输入时用到）
            System.err.println("程序出错：" + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void requireArgs(List<String> rest, int n, String form) {
        if (rest.size() < n) {
            throw new OpsException("用法：java -jar hv2-ops.jar " + form);
        }
    }

    private static void usage() {
        System.err.println("""
                用法：java -jar hv2-ops.jar <命令>
                  migrate | info | validate                    建表迁移（用建表账号，如 HEALTH_V2）
                  set-password <登录名>                         改密码（运行时输入，不回显）
                  import-devices <清单.csv> [--apply]           导入手表绑定清单；不加 --apply 只检查、不写库
                  publish-map --gpkg <GeoServer 能读到的路径> [--geoserver http://127.0.0.1:8082/geoserver]
                  soak [--url http://127.0.0.1:8081] [--minutes 1440] [--interval 60] [--out soak.csv]
                                                               稳定性观察：每分钟采一次后端指标，结束时出摘要
                  soak-summary <soak.csv>                      对已有的 CSV 重新出摘要
                数据库连接：环境变量 ORACLE_HOST ORACLE_PORT ORACLE_SERVICE ORACLE_USER [ORACLE_SCHEMA]，
                密码不设 ORACLE_PASSWORD 时提示输入。详见 docs/11。""");
    }
}
