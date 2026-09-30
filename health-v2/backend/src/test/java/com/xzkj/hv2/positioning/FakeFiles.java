package com.xzkj.hv2.positioning;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 按 42 号文附件2 格式造脱敏假数据（docs/02 第六节）。姓名都是"测试"开头的假名，
 * 编码格式与真实数据一致：卡编码 17 位、区域编码 16 位、基站编码 22 位。
 */
final class FakeFiles {

    static final String MINE = "620823009203";
    static final String OTHER_MINE = "610000000001";

    private FakeFiles() {
    }

    static String fileName(String type, String time14) {
        return MINE + "_" + type + "_" + time14 + ".txt";
    }

    /** 文件头：煤矿编码;矿井名称;数据上传时间 */
    static String header(String time) {
        return MINE + ";测试煤矿;" + time;
    }

    /** RYXX 的文件头有 8 段，上传时间在最后 */
    static String ryxxHeader(String time) {
        return MINE + ";测试煤矿;500;KJ000;测试人员定位系统;测试厂家;2030-01-01;" + time;
    }

    /** 文件头 + 记录，每条记录以 ~ 结束，文件以 || 结束 */
    static String file(String header, List<String> records) {
        StringBuilder sb = new StringBuilder(header).append('~');
        records.forEach(r -> sb.append(r).append('~'));
        return sb.append("||").toString();
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String card(int n) {
        return MINE + String.format("%05d", 90000 + n);
    }

    static String area(int n) {
        return MINE + String.format("%04d", n);
    }

    static String station(int area, int n) {
        return area(area) + String.format("%06d", n);
    }

    /** RYSS 一条：已入井，在第 area 区域第 station 个基站 */
    static String ryssIn(int person, int area, int station, String inTime) {
        return String.join(";", card(person), "测试" + person, "1", inTime, "",
                area(area), inTime, station(area, station), inTime, "三八制", "-3.50", "正常", "0", "0",
                station(area, station) + "&" + inTime);
    }

    /** RYSS 一条：已出井 */
    static String ryssOut(int person, int area, int station, String inTime, String outTime) {
        return String.join(";", card(person), "测试" + person, "2", inTime, outTime,
                area(area), inTime, station(area, station), inTime, "三八制", "1.25", "正常", "0", "0", "");
    }

    /** RYXX 一条 */
    static String ryxx(int person, String name, String jobKind, String dept) {
        return String.join(";", card(person), name, jobKind, "未设置", dept, "1990-01-01", "", "0", "1");
    }

    static String ryqy(int area, String name) {
        return String.join(";", "其它区域", area(area), "50", name);
    }

    static String ryjz(int area, int station, String name) {
        return String.join(";", station(area, station), name, "0", "0", "0", name);
    }

    static String jzss(int area, int station, String run, String time) {
        return String.join(";", station(area, station), run, "1", time);
    }
}
