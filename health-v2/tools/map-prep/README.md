# map-prep：CAD 矿图 → 底图数据

设计见 `docs/06` 第三节。一条命令跑完：

```bash
tools/map-prep/prepare.sh            # 在 health-v2/ 下运行
```

| 文件 | 作用 |
| --- | --- |
| `prepare.sh` | 整个流程：dwg → dxf → 图层清单页 → `mine-map.gpkg` |
| `layers.py` | 图层清单和每层预览图（`runtime/map-prep/roads/index.html`，浏览器用 `file://` 完整路径打开） |
| `build.py` | 选中的图层（名单写在文件开头）平移回真实坐标，写成 `roadway_line`、`roadway_label` 两张表 |

输出都在 `runtime/map-prep/`，里面是矿上的图纸，不提交。

需要：本机 `dwg2dxf`（`brew install libredwg`）、Docker。GDAL 不装在本机，用官方镜像 `ghcr.io/osgeo/gdal`（本机 Homebrew 装 GDAL 失败：Homebrew 不支持 macOS 27）。
