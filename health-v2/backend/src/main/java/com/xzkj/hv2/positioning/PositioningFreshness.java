package com.xzkj.hv2.positioning;

import java.time.LocalDateTime;

/**
 * 定位数据新不新（页面顶部"定位数据已过期"横幅、角落的定位数据时间用，docs/05 第二节）。
 *
 * @param dataTime 最新一份生效的 RYSS 的数据上传时间；从没收到过时为 null
 * @param stale    距现在超过过期阈值，或者从没收到过 RYSS
 */
public record PositioningFreshness(LocalDateTime dataTime, boolean stale) {
}
