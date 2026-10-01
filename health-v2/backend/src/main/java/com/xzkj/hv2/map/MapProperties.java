package com.xzkj.hv2.map;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 底图配置（docs/06 第三、四节）。前端从 GET /api/map/config 读，不写死在前端。
 *
 * @param wmsUrl     GeoServer 的 WMS 地址。默认是站内路径 /geoserver/hv2/wms，由前端开发服务器（上线时由 nginx）
 *                   转给 GeoServer，浏览器不直接连 GeoServer
 * @param lineLayer  巷道线图层
 * @param labelLayer 巷道名称图层
 * @param projection 坐标系，EPSG:4527
 * @param extent     地图范围：按选中的巷道线算出来的（不用 DXF 文件头），基站只能摆在这个范围里
 */
@ConfigurationProperties("hv2.map")
public record MapProperties(String wmsUrl, String lineLayer, String labelLayer, String projection, Extent extent) {

    public record Extent(BigDecimal minX, BigDecimal minY, BigDecimal maxX, BigDecimal maxY) {

        public boolean contains(BigDecimal x, BigDecimal y) {
            return x.compareTo(minX) >= 0 && x.compareTo(maxX) <= 0 && y.compareTo(minY) >= 0 && y.compareTo(maxY) <= 0;
        }
    }
}
