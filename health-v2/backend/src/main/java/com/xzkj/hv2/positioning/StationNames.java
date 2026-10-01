package com.xzkj.hv2.positioning;

/** 基站显示名称：用户起的名字 &gt; 厂家名称 &gt; 区域名称 + 编码后 6 位（docs/04 POS_STATION_MARK）。 */
public final class StationNames {

    private StationNames() {
    }

    public static String resolve(String displayName, String vendorName, String areaName, String stationCode) {
        if (displayName != null) {
            return displayName;
        }
        if (vendorName != null) {
            return vendorName;
        }
        if (stationCode == null) {
            return null;
        }
        String tail = stationCode.length() > 6 ? stationCode.substring(stationCode.length() - 6) : stationCode;
        return (areaName != null ? areaName : "基站") + " " + tail;
    }
}
