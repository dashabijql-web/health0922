# 本项目的 GeoServer（docs/06 第三节）：官方镜像 + 中文字体。
# 官方镜像只有 DejaVu 字体，巷道名称会显示成方块。文泉驿微米黑是 TrueType 轮廓的字体，Java 能直接用；
# 样式里写的字体名是 "WenQuanYi Micro Hei"（tools/map-prep/styles/roadway_label.sld）。
# 官方镜像只有 Intel 版本，Mac 上转译运行（docker-compose.yml 里写了 platform）。
FROM docker.osgeo.org/geoserver:2.28.5
RUN apt-get update \
 && apt-get install -y --no-install-recommends fonts-wqy-microhei \
 && rm -rf /var/lib/apt/lists/*
