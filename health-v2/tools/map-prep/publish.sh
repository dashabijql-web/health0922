#!/usr/bin/env bash
# 把 runtime/map-prep/mine-map.gpkg 发布到本项目的 GeoServer（docs/06 第三节"③ 发布"）。在 health-v2/ 下运行：
#   tools/map-prep/publish.sh
# 做的事：删掉工作区 hv2（连同里面的图层、样式）再重建——可以反复运行，结果一样；重新生成 gpkg 后也要再跑一次。
#   工作区 hv2 → 数据源 mine_map（GeoPackage，容器里的 /opt/hv2-map/mine-map.gpkg）
#   → 图层 hv2:roadway_line、hv2:roadway_label（EPSG:4527）→ 样式 tools/map-prep/styles/*.sld
# 需要：GeoServer 容器 hv2-geoserver 已启动（tools/dev/docker-compose.yml）；管理员密码在 .env.local。
set -euo pipefail
HV2="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GS="${GEOSERVER_URL:-http://127.0.0.1:8082/geoserver}"
STYLES="$HV2/tools/map-prep/styles"
[[ -f "$HV2/runtime/map-prep/mine-map.gpkg" ]] || { echo "缺少 runtime/map-prep/mine-map.gpkg，先运行 tools/map-prep/prepare.sh" >&2; exit 1; }

password="$(grep -E '^GEOSERVER_ADMIN_PASSWORD=' "$HV2/.env.local" | head -1 | cut -d= -f2-)"
[[ -n "$password" ]] || { echo ".env.local 里没有 GEOSERVER_ADMIN_PASSWORD" >&2; exit 1; }

# 账号密码经 curl 的配置文件（进程替换）传进去，不出现在命令行上
gs() {
  curl -sS -K <(printf 'user = "admin:%s"\n' "$password") "$@"
}

# 发请求并检查 HTTP 状态码：gs_ok <期望的状态码，逗号分隔> curl 参数…
gs_ok() {
  local want="$1" code body
  shift
  body="$(mktemp)"
  code="$(gs -o "$body" -w '%{http_code}' "$@")"
  if [[ ",$want," != *",$code,"* ]]; then
    echo "GeoServer 返回 HTTP ${code}（期望 ${want}）：$*" >&2
    cat "$body" >&2
    rm -f "$body"
    exit 1
  fi
  rm -f "$body"
}

echo "等 GeoServer 就绪……"
for ((i = 0; i < 120; i++)); do
  [[ "$(gs -o /dev/null -w '%{http_code}' "$GS/rest/about/version.json" 2>/dev/null || true)" == 200 ]] && break
  sleep 2
done
gs_ok 200 "$GS/rest/about/version.json"

echo "重建工作区 hv2"
gs_ok 200,404 -X DELETE "$GS/rest/workspaces/hv2?recurse=true"
gs_ok 201 -X POST -H 'Content-Type: application/json' \
  -d '{"workspace":{"name":"hv2"}}' "$GS/rest/workspaces"

echo "数据源 mine_map"
gs_ok 201 -X POST -H 'Content-Type: application/json' -d '{
  "dataStore": {
    "name": "mine_map",
    "description": "矿图底图（tools/map-prep 生成）",
    "connectionParameters": { "entry": [
      { "@key": "dbtype", "$": "geopkg" },
      { "@key": "database", "$": "file:/opt/hv2-map/mine-map.gpkg" },
      { "@key": "read_only", "$": "true" }
    ] }
  } }' "$GS/rest/workspaces/hv2/datastores"

for layer in roadway_line roadway_label; do
  echo "图层 hv2:$layer"
  gs_ok 201 -X POST -H 'Content-Type: application/json' -d "{
    \"featureType\": { \"name\": \"$layer\", \"nativeName\": \"$layer\",
                      \"srs\": \"EPSG:4527\", \"projectionPolicy\": \"FORCE_DECLARED\" } }" \
    "$GS/rest/workspaces/hv2/datastores/mine_map/featuretypes"
  gs_ok 201 -X POST -H 'Content-Type: application/vnd.ogc.sld+xml' \
    --data-binary "@$STYLES/$layer.sld" "$GS/rest/workspaces/hv2/styles?name=$layer"
  gs_ok 200 -X PUT -H 'Content-Type: application/json' \
    -d "{\"layer\":{\"defaultStyle\":{\"name\":\"$layer\",\"workspace\":\"hv2\"}}}" "$GS/rest/layers/hv2:$layer"
done

echo "完成。试一下（WMS 1.1.1，先东后北）："
echo "  $GS/hv2/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=hv2:roadway_line,hv2:roadway_label&STYLES=&SRS=EPSG:4527&BBOX=39480087.9,3851797.4,39489391.2,3854743.4&WIDTH=1400&HEIGHT=444&FORMAT=image/png&TRANSPARENT=true"
