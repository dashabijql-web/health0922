#!/usr/bin/env bash
# CAD 矿图 → 底图数据（docs/06 第三节 ①②）。用法（在 health-v2/ 下）：tools/map-prep/prepare.sh [dwg 路径]
# 默认读 reference/cad/ 下唯一的 .dwg；输出都在 runtime/map-prep/（含矿上图纸，不提交）：
#   mine.dxf        dwg2dxf（LibreDWG）转来的 DXF
#   all.gpkg        DXF 原样转成的 GeoPackage（图层预览用）
#   full/ roads/    图层清单页 index.html：full 是整张图纸，roads 是巷道所在的那幅图
#   mine-map.gpkg   挑好的图层，已平移回真实坐标：roadway_line、roadway_label（发布到 GeoServer 用）
# 需要：本机 dwg2dxf（brew 的 libredwg）、Docker（GDAL 镜像，见 GDAL_IMAGE）。
set -euo pipefail
HV2="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GDAL_IMAGE="${GDAL_IMAGE:-ghcr.io/osgeo/gdal:ubuntu-small-3.13.3}"
OUT="$HV2/runtime/map-prep"
DWG="${1:-}"
if [[ -z "$DWG" ]]; then
  shopt -s nullglob
  files=("$HV2"/reference/cad/*.dwg)
  [[ ${#files[@]} -eq 1 ]] || { echo "reference/cad/ 下要正好一个 .dwg，或者把路径作为参数传进来" >&2; exit 1; }
  DWG="${files[0]}"
fi
mkdir -p "$OUT"

gdal() {
  docker run --rm -v "$OUT":/w -v "$HV2/tools/map-prep":/tools:ro -v "$HV2/reference/cad":/ref:ro -w /w "$GDAL_IMAGE" "$@"
}

echo "① dwg → dxf：$DWG"
dwg2dxf -y -o "$OUT/mine.dxf" "$DWG" 2>&1 | grep -v "Unstable Class" || true

echo "② dxf → all.gpkg（只读模型空间；图块里没被用到的定义不算，解析报错的都在那里）"
gdal ogr2ogr -f GPKG -overwrite all.gpkg mine.dxf entities -nln entities -nlt GEOMETRY -lco SPATIAL_INDEX=YES 2>/dev/null

echo "③ 图层清单页"
ref=()
[[ -f "$HV2/reference/cad/zsl_line2.shp" ]] && ref=(--ref /ref/zsl_line2.shp)
gdal python3 /tools/layers.py all.gpkg full "${ref[@]}" --width 1800 2>/dev/null
gdal python3 /tools/layers.py all.gpkg roads --window 39491000,3849700,39501700,3854600 --width 1500 2>/dev/null

echo "④ 选中的图层 → mine-map.gpkg（平移回真实坐标）"
rm -f "$OUT/mine-map.gpkg"
gdal python3 /tools/build.py mine.dxf mine-map.gpkg 2>/dev/null
echo "完成：$OUT"
