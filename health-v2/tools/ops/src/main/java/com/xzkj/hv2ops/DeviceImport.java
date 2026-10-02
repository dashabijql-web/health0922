package com.xzkj.hv2ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导入手表绑定清单（docs/00 第 2 项、docs/03 第二节"绑定的办法"、docs/11）。
 * <p>
 * 先检查、列出要做的改动；加 --apply 才写库，有任何错误都不写。写库在一个事务里：
 * 先把清单里要换人的表、以及清单里的人原来戴的别的表解绑（卡编码唯一，否则对调两块表会冲突），
 * 再逐块登记或更新；每块有变化的表记一条 DEVICE_BIND 操作日志。后端 60 秒内刷新绑定缓存，手表不用重连。
 */
final class DeviceImport {

    private static final Pattern LINE = Pattern.compile("^第 (\\d+) 行");

    enum Kind { NEW, REBIND, ENABLE, UNCHANGED }

    record Existing(String imei, String card, int status) {
    }

    record Change(DeviceCsv.Row row, Kind kind, String oldCard) {
    }

    /** 检查结果：要做的改动、因为清单里的人改戴别的表而被解绑的旧表、错误。 */
    record Plan(List<Change> changes, Map<String, String> released, List<String> errors) {
        long count(Kind k) {
            return changes.stream().filter(c -> c.kind() == k).count();
        }
    }

    private DeviceImport() {
    }

    static int run(Db db, Path csv, boolean apply) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(csv);
        } catch (IOException e) {
            throw new OpsException("读不了文件 " + csv);
        }
        DeviceCsv.Parsed parsed = DeviceCsv.parse(bytes);
        System.out.println("文件编码按 " + parsed.charset() + " 读取，有效行 " + parsed.rows().size());
        try (Connection c = db.open()) {
            Plan plan = plan(c, parsed);
            print(plan);
            if (!plan.errors().isEmpty()) {
                System.out.println("有 " + plan.errors().size() + " 处错误，没有写库。改好清单后重新运行。");
                return 1;
            }
            if (!apply) {
                System.out.println("这次只检查、没有写库。确认无误后加 --apply 再运行一次。");
                return 0;
            }
            try {
                applyPlan(c, plan);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
            System.out.println("已写库。后端 60 秒内按新的绑定入库，手表不用重连。");
            return 0;
        } catch (SQLException e) {
            throw new OpsException("写库失败：ORA-" + e.getErrorCode() + "，已回滚，什么都没改");
        }
    }

    static Plan plan(Connection c, DeviceCsv.Parsed parsed) throws SQLException {
        List<String> errors = new ArrayList<>(parsed.errors());
        List<DeviceCsv.Row> rows = parsed.rows();
        Map<String, Existing> byImei = new HashMap<>();
        Map<String, Existing> byCard = new HashMap<>();
        Set<String> persons = new HashSet<>();
        try (PreparedStatement byImeiPs = c.prepareStatement("SELECT IMEI, CARD_CODE, STATUS FROM DEVICE WHERE IMEI = ?");
             PreparedStatement byCardPs = c.prepareStatement("SELECT IMEI, CARD_CODE, STATUS FROM DEVICE WHERE CARD_CODE = ?");
             PreparedStatement personPs = c.prepareStatement("SELECT COUNT(*) FROM POS_PERSON WHERE CARD_CODE = ?")) {
            for (DeviceCsv.Row r : rows) {
                Existing e = one(byImeiPs, r.imei());
                if (e != null) {
                    byImei.put(r.imei(), e);
                }
                if (r.card() != null) {
                    Existing b = one(byCardPs, r.card());
                    if (b != null) {
                        byCard.put(r.card(), b);
                    }
                    personPs.setString(1, r.card());
                    try (ResultSet rs = personPs.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            persons.add(r.card());
                        }
                    }
                }
            }
        }

        List<Change> changes = new ArrayList<>();
        Map<String, String> released = new LinkedHashMap<>();
        Set<String> listed = new HashSet<>();
        rows.forEach(r -> listed.add(r.imei()));
        for (DeviceCsv.Row r : rows) {
            if (r.card() != null && !persons.contains(r.card())) {
                errors.add("第 " + r.line() + " 行：卡编码 " + r.card()
                        + " 不在人员表里（定位系统还没发过这个人的 RYXX/RYSS？）");
                continue;
            }
            Existing e = byImei.get(r.imei());
            Kind kind;
            if (e == null) {
                kind = Kind.NEW;
            } else if (!Objects.equals(e.card(), r.card())) {
                kind = Kind.REBIND;
            } else if (e.status() != 1) {
                kind = Kind.ENABLE;
            } else {
                kind = Kind.UNCHANGED;
            }
            changes.add(new Change(r, kind, e == null ? null : e.card()));
            Existing other = r.card() == null ? null : byCard.get(r.card());
            // 这个人原来戴的是另一块表，而那块表不在清单里：解绑它
            if (other != null && !other.imei().equals(r.imei()) && !listed.contains(other.imei())) {
                released.put(other.imei(), other.card());
            }
        }
        errors.sort(Comparator.comparingInt(DeviceImport::lineOf));
        return new Plan(changes, released, errors);
    }

    /** 错误信息开头的"第 N 行"，用来按行号排序；没有行号的排最前 */
    private static int lineOf(String error) {
        Matcher m = LINE.matcher(error);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static void applyPlan(Connection c, Plan plan) throws SQLException {
        try (PreparedStatement release = c.prepareStatement(
                "UPDATE DEVICE SET CARD_CODE = NULL, BOUND_AT = NULL WHERE IMEI = ?")) {
            for (String imei : plan.released().keySet()) {
                release.setString(1, imei);
                release.executeUpdate();
            }
            // 换人的表先全部解绑，再统一写新绑定，清单里对调两块表也不会撞上卡编码唯一约束
            for (Change ch : plan.changes()) {
                if (ch.kind() == Kind.REBIND) {
                    release.setString(1, ch.row().imei());
                    release.executeUpdate();
                }
            }
        }
        try (PreparedStatement update = c.prepareStatement("""
                UPDATE DEVICE SET CARD_CODE = ?, MODEL = COALESCE(?, MODEL), STATUS = 1,
                       BOUND_AT = CASE WHEN ? IS NULL THEN NULL ELSE LOCALTIMESTAMP(0) END
                 WHERE IMEI = ?""");
             PreparedStatement enable = c.prepareStatement(
                     "UPDATE DEVICE SET STATUS = 1, MODEL = COALESCE(?, MODEL) WHERE IMEI = ?");
             PreparedStatement insert = c.prepareStatement("""
                INSERT INTO DEVICE (IMEI, CARD_CODE, MODEL, BOUND_AT, STATUS)
                VALUES (?, ?, ?, CASE WHEN ? IS NULL THEN NULL ELSE LOCALTIMESTAMP(0) END, 1)""")) {
            for (Change ch : plan.changes()) {
                DeviceCsv.Row r = ch.row();
                switch (ch.kind()) {
                    case NEW -> {
                        insert.setString(1, r.imei());
                        insert.setString(2, r.card());
                        insert.setString(3, r.model());
                        insert.setString(4, r.card());
                        insert.executeUpdate();
                    }
                    case REBIND -> {
                        update.setString(1, r.card());
                        update.setString(2, r.model());
                        update.setString(3, r.card());
                        update.setString(4, r.imei());
                        update.executeUpdate();
                    }
                    case ENABLE -> {
                        enable.setString(1, r.model());
                        enable.setString(2, r.imei());
                        enable.executeUpdate();
                    }
                    case UNCHANGED -> {
                        continue;
                    }
                }
                OpLog.write(c, "DEVICE_BIND", "DEVICE", r.imei(),
                        ch.kind() == Kind.NEW ? null : "{\"cardCode\":" + OpLog.json(ch.oldCard()) + "}",
                        "{\"cardCode\":" + OpLog.json(r.card()) + ",\"tool\":\"hv2-ops\"}");
            }
        }
        for (Map.Entry<String, String> e : plan.released().entrySet()) {
            OpLog.write(c, "DEVICE_BIND", "DEVICE", e.getKey(),
                    "{\"cardCode\":" + OpLog.json(e.getValue()) + "}", "{\"cardCode\":null,\"tool\":\"hv2-ops\"}");
        }
    }

    private static void print(Plan plan) {
        System.out.printf("新登记 %d 块，换绑/解绑 %d 块，重新启用 %d 块，不变 %d 块%n",
                plan.count(Kind.NEW), plan.count(Kind.REBIND), plan.count(Kind.ENABLE), plan.count(Kind.UNCHANGED));
        for (Change ch : plan.changes()) {
            if (ch.kind() == Kind.REBIND) {
                System.out.println("  第 " + ch.row().line() + " 行 " + ch.row().imei() + "：" + show(ch.oldCard())
                        + " → " + show(ch.row().card()));
            }
        }
        plan.released().forEach((imei, card) ->
                System.out.println("  清单外的表 " + imei + " 原来绑定 " + card + "，这个人改戴清单里的表，将解绑"));
        plan.errors().forEach(e -> System.out.println("  错误：" + e));
    }

    private static String show(String card) {
        return card == null ? "未绑定" : card;
    }

    private static Existing one(PreparedStatement ps, String key) throws SQLException {
        ps.setString(1, key);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? new Existing(rs.getString(1), rs.getString(2), rs.getInt(3)) : null;
        }
    }
}
