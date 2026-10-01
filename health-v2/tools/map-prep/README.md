# map-prep：CAD 矿图 → 底图数据

设计见 `docs/06` 第三节。两条命令（在 health-v2/ 下运行）：

```bash
tools/map-prep/prepare.sh            # CAD → mine-map.gpkg
tools/map-prep/publish.sh            # mine-map.gpkg → GeoServer 图层和样式（GeoServer 要先启动）
```

GeoServer 第一次用时先建镜像、建容器（见 `tools/dev/docker-compose.yml` 开头和 `geoserver` 服务的注释）：

```bash
docker build --platform linux/amd64 -t hv2-geoserver:2.28.5 tools/dev/geoserver
docker compose --env-file .env.local -f tools/dev/docker-compose.yml up -d geoserver
```

| 文件 | 作用 |
| --- | --- |
| `prepare.sh` | 整个流程：dwg → dxf → 图层清单页 → `mine-map.gpkg` |
| `layers.py` | 图层清单和每层预览图（`runtime/map-prep/roads/index.html`，浏览器用 `file://` 完整路径打开） |
| `build.py` | 选中的图层（名单写在文件开头）平移回真实坐标，写成 `roadway_line`、`roadway_label` 两张表 |
| `publish.sh` | 用 GeoServer 的 REST 接口删掉工作区 `hv2` 再重建：数据源、两个图层、两个样式。可以反复运行 |
| `styles/*.sld` | 线按图层类别上色、文字白底黑字沿巷道摆放（做法和原因写在文件开头） |

输出都在 `runtime/map-prep/`，里面是矿上的图纸，不提交。

需要：本机 `dwg2dxf`（`brew install libredwg`）、Docker。GDAL 不装在本机，用官方镜像 `ghcr.io/osgeo/gdal`（本机 Homebrew 装 GDAL 失败：Homebrew 不支持 macOS 27）。
