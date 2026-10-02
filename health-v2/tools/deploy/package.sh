#!/usr/bin/env bash
# 在开发机上打离线安装包（docs/11"二、离线安装包"）。在 health-v2/ 下运行：
#   tools/deploy/package.sh
# 产物在 runtime/deploy-package/hv2-<日期时间>/ 和同名 .zip，拷到服务器上用。
# 只放本项目自己的文件；JDK、nginx、Redis、GeoServer、FileZilla Server、WinSW 的安装包另外下载（版本见 docs/11）。
# 检查两件事，不通过就停下：前端构建产物里有 page-flip 补丁的改动（docs/07"阶段 6 的约定"）；
# 包里任何文件都不含 .env.local 里的密码。
set -euo pipefail
HV2="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HV2/runtime/deploy-package/hv2-$STAMP"
WORK="$HV2/runtime/deploy-package/work"
mkdir -p "$OUT"/{backend,ops,frontend,nginx,windows,oracle,map}
rm -rf "$WORK" && mkdir -p "$WORK"

echo "== 后端 JAR"
(cd "$HV2/backend" && mvn -B -q -DskipTests package)
cp "$HV2/backend/target/hv2-backend.jar" "$OUT/backend/"

echo "== 部署工具 hv2-ops.jar（迁移脚本、底图样式打包在里面）"
(cd "$HV2/tools/ops" && mvn -B -q package)
cp "$HV2/tools/ops/target/hv2-ops.jar" "$OUT/ops/"

echo "== 前端：在干净的副本里按锁文件安装、生产构建"
rsync -a --exclude node_modules --exclude dist "$HV2/frontend/" "$WORK/frontend/"
(cd "$WORK/frontend" && { pnpm install --frozen-lockfile --offline >/dev/null 2>&1 || pnpm install --frozen-lockfile >/dev/null; } && pnpm build >/dev/null)
# page-flip 补丁的三处改动，构建后仍能找到（属性名不会被压缩改名）
for marker in '.render.stop()' 'cancelAnimationFrame(this.rafId)' 'distElement.offsetWidth/'; do
  if ! grep -q -F "$marker" "$WORK"/frontend/dist/assets/*.js; then
    echo "构建产物里找不到 page-flip 补丁的改动：$marker（补丁没打上？）" >&2
    exit 1
  fi
done
echo "   page-flip 补丁已在构建产物里"
cp -R "$WORK/frontend/dist/." "$OUT/frontend/"

echo "== 配置模板、数据库脚本"
cp "$HV2/tools/deploy/nginx/hv2.conf" "$OUT/nginx/"
cp "$HV2/tools/deploy/windows/hv2-backend.xml" "$OUT/windows/"
cp "$HV2"/tools/deploy/oracle/*.sql "$OUT/oracle/"

echo "== 底图数据和字体"
if [[ -f "$HV2/runtime/map-prep/mine-map.gpkg" ]]; then
  cp "$HV2/runtime/map-prep/mine-map.gpkg" "$OUT/map/"
else
  echo "   缺少 runtime/map-prep/mine-map.gpkg（先运行 tools/map-prep/prepare.sh）" >&2
  exit 1
fi
# 巷道名称用文泉驿微米黑（tools/map-prep/styles/roadway_label.sld），Windows 上没有，从开发用的 GeoServer 镜像里取
if docker cp hv2-geoserver:/usr/share/fonts/truetype/wqy/wqy-microhei.ttc "$OUT/map/" 2>/dev/null; then
  echo "   已取出 wqy-microhei.ttc"
else
  echo "   取不到字体：GeoServer 容器 hv2-geoserver 没在运行（docker compose ... start）" >&2
  exit 1
fi

echo "== 版本和校验"
{
  echo "health-v2 离线安装包 $STAMP"
  echo "代码版本：$(git -C "$HV2" rev-parse --short HEAD)$(git -C "$HV2" diff --quiet HEAD -- . || echo '（含未提交的改动）')"
  echo "前端版本：$(node -p "require('$HV2/frontend/package.json').version")"
} > "$OUT/VERSION.txt"
(cd "$OUT" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 shasum -a 256 > SHA256SUMS)

echo "== 检查包里没有 .env.local 的密码"
if [[ -f "$HV2/.env.local" ]]; then
  while IFS='=' read -r key value; do
    [[ "$key" == *PASSWORD* && -n "$value" ]] || continue
    if grep -r -q -F -- "$value" "$OUT"; then
      echo "包里出现了 $key 的值，停止" >&2
      exit 1
    fi
  done < <(grep -E '^[A-Z_]+=' "$HV2/.env.local")
fi

(cd "$(dirname "$OUT")" && rm -f "hv2-$STAMP.zip" && zip -q -r "hv2-$STAMP.zip" "hv2-$STAMP")
rm -rf "$WORK"
echo "完成：$OUT"
echo "      $(dirname "$OUT")/hv2-$STAMP.zip（$(du -h "$(dirname "$OUT")/hv2-$STAMP.zip" | cut -f1)）"
