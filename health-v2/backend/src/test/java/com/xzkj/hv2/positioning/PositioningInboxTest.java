package com.xzkj.hv2.positioning;

import static com.xzkj.hv2.positioning.FakeFiles.MINE;
import static com.xzkj.hv2.positioning.FakeFiles.card;
import static com.xzkj.hv2.positioning.FakeFiles.file;
import static com.xzkj.hv2.positioning.FakeFiles.fileName;
import static com.xzkj.hv2.positioning.FakeFiles.header;
import static com.xzkj.hv2.positioning.FakeFiles.ryssIn;
import static com.xzkj.hv2.positioning.FakeFiles.ryssOut;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


/** 定位文件接入的端到端测试：真实的收件箱目录 + Oracle 测试用户（docs/02 第六节的必测用例）。 */
class PositioningInboxTest extends PositioningDbTestBase {

    private static final String IN = "2026-09-23 06:33:52";

    @Autowired
    private PositioningInbox inbox;
    @Autowired
    private PositioningStatusService statusService;

    // ---------------- 正常流程 ----------------

    @Test
    void normalRyssIsIngestedAndBackedUp() throws IOException {
        String name = fileName("RYSS", "20260923101319");
        drop(name, file(header("2026-09-23 10:13:19"), List.of(
                ryssIn(1, 8, 12, IN), ryssIn(2, 8, 13, IN), ryssOut(3, 23, 170, IN, "2026-09-23 10:00:00"))));

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("DONE");

        assertThat(count("SELECT COUNT(*) FROM POS_PERSON_STATE")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM V_POS_IN_WELL")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON WHERE FROM_RYXX = 0")).as("RYSS 里的人补录进人员表")
                .isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT IN_WELL_COUNT, OUT_COUNT, TOTAL_COUNT FROM POS_HEADCOUNT_SERIES"))
                .singleElement().satisfies(m -> assertThat(m.values()).extracting(Object::toString)
                        .containsExactly("2", "1", "3"));
        assertThat(count("SELECT COUNT(*) FROM POS_PRESENCE_DAILY WHERE STAT_DATE = DATE '2026-09-23'"))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT STATUS || '/' || RECORD_COUNT || '/' || BACKUP_PATH FROM POS_INGEST_FILE",
                String.class)).isEqualTo("DONE/3/backup/jxry/" + MINE + "/20260923/" + name);

        assertThat(backupDir().resolve("20260923").resolve(name)).exists();
        assertThat(mineInbox()).isEmptyDirectory();
    }

    @Test
    void emptyFieldsAreStoredAsNullWithoutShifting() {
        String rec = String.join(";", card(1), "测试1", "1", IN, "", FakeFiles.area(8), IN,
                FakeFiles.station(8, 12), IN, "", "", "正常", "", "", "");
        drop(fileName("RYSS", "20260923101319"), file(header("2026-09-23 10:13:19"), List.of(rec)));

        inbox.scanOnce();

        var row = jdbc.queryForMap("SELECT * FROM POS_PERSON_STATE");
        assertThat(row.get("OUT_TIME")).isNull();
        assertThat(row.get("SHIFT_MODE")).isNull();
        assertThat(row.get("DISTANCE_M")).isNull();
        assertThat(row.get("STATION_CODE")).isEqualTo(FakeFiles.station(8, 12));
        assertThat(row.get("WORK_STATUS")).isEqualTo("正常");
    }

    @Test
    void ryxxUsesEighthHeaderSegmentAndCompletesPeopleSupplementedFromRyss() {
        drop(fileName("RYSS", "20260923101319"), file(header("2026-09-23 10:13:19"), List.of(ryssIn(1, 8, 12, IN))));
        inbox.scanOnce();
        jdbc.update("UPDATE POS_PERSON SET AGE = 40 WHERE CARD_CODE = ?", card(1));

        // RYXX 比 RYSS 早半年（和厂家样例一样），仍要补全从 RYSS 补录的人
        drop(fileName("RYXX", "20260330162521"), file(FakeFiles.ryxxHeader("2026-03-30 16:25:21"), List.of(
                FakeFiles.ryxx(1, "测试1", "采煤工", "综采一队"),
                FakeFiles.ryxx(2, "测试2", "未设置", "掘进二队"),
                FakeFiles.ryxx(2, "测试二", "电工", "机电队"))));
        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("DONE");

        assertThat(jdbc.queryForObject("SELECT HEADER_TIME FROM POS_INGEST_FILE WHERE FILE_TYPE = 'RYXX'",
                LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026, 3, 30, 16, 25, 21));
        var p1 = jdbc.queryForMap("SELECT * FROM POS_PERSON WHERE CARD_CODE = ?", card(1));
        assertThat(p1.get("FROM_RYXX").toString()).isEqualTo("1");
        assertThat(p1.get("DEPT")).isEqualTo("综采一队");
        assertThat(p1.get("AGE").toString()).as("不碰人工录入的年龄").isEqualTo("40");
        var p2 = jdbc.queryForMap("SELECT * FROM POS_PERSON WHERE CARD_CODE = ?", card(2));
        assertThat(p2.get("PERSON_NAME")).as("同一文件里卡编码重复，以靠后的为准").isEqualTo("测试二");
        assertThat(count("SELECT COUNT(*) FROM POS_INGEST_ERROR WHERE KIND = 'WARN' AND REASON LIKE '人员卡编码%重复%'"))
                .isEqualTo(1);
    }

    /** docs/00 第 21 项：卡编码就代表人；同一卡编码换了姓名时清空人工录入的年龄，记警告提示人工核对。 */
    @Test
    void sameCardWithNewNameClearsAgeAndWarns() {
        drop(fileName("RYXX", "20260920080000"), file(FakeFiles.ryxxHeader("2026-09-20 08:00:00"), List.of(
                FakeFiles.ryxx(1, "测试甲", "采煤工", "综采一队"),
                FakeFiles.ryxx(2, "测试乙", "电工", "机电队"),
                FakeFiles.ryxx(3, "测试丙", "电工", "机电队"))));
        inbox.scanOnce();
        jdbc.update("UPDATE POS_PERSON SET AGE = 45, AGE_UPDATED_BY = 'admin', AGE_UPDATED_AT = ? WHERE CARD_CODE IN (?, ?)",
                java.sql.Timestamp.valueOf(NOW), card(1), card(2));
        long logStart = jdbc.queryForObject("SELECT NVL(MAX(ID), 0) FROM SYS_OPERATION_LOG", Long.class);

        // 更新的一份：卡 1 换了人（有年龄），卡 3 换了人（没录年龄），卡 2 没变
        String newer = fileName("RYXX", "20260922080000");
        drop(newer, file(FakeFiles.ryxxHeader("2026-09-22 08:00:00"), List.of(
                FakeFiles.ryxx(2, "测试乙", "电工", "机电队"),
                FakeFiles.ryxx(1, "测试丁", "掘进工", "掘进二队"),
                FakeFiles.ryxx(3, "测试戊", "电工", "机电队"))));
        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).as("警告不算出错")
                .containsExactly("DONE");

        var p1 = jdbc.queryForMap("SELECT * FROM POS_PERSON WHERE CARD_CODE = ?", card(1));
        assertThat(p1.get("PERSON_NAME")).as("以最新文件为准").isEqualTo("测试丁");
        assertThat(p1.get("DEPT")).isEqualTo("掘进二队");
        assertThat(p1.get("AGE")).as("清空人工录入的年龄").isNull();
        assertThat(p1.get("AGE_UPDATED_BY")).isNull();
        assertThat(jdbc.queryForObject("SELECT AGE FROM POS_PERSON WHERE CARD_CODE = ?", Integer.class, card(2)))
                .as("姓名没变，年龄不动").isEqualTo(45);
        assertThat(jdbc.queryForList("""
                SELECT e.LINE_NO || '|' || e.REASON FROM POS_INGEST_ERROR e JOIN POS_INGEST_FILE f ON f.ID = e.FILE_ID
                 WHERE f.FILE_NAME = ? AND e.KIND = 'WARN' ORDER BY e.LINE_NO""", String.class, newer))
                .containsExactly(
                        "2|卡编码换了姓名（原：测试甲），已清空人工录入的年龄 45，请人工核对手表绑定和名单",
                        "3|卡编码换了姓名（原：测试丙），请人工核对手表绑定和名单");
        assertThat(jdbc.queryForList("""
                SELECT ACTION || '|' || USERNAME || '|' || NVL(TO_CHAR(USER_ID), '-') || '|' || TARGET_TYPE || ':'
                       || TARGET_ID || '|' || DBMS_LOB.SUBSTR(BEFORE_JSON, 200) || '|' || DBMS_LOB.SUBSTR(AFTER_JSON, 200)
                  FROM SYS_OPERATION_LOG WHERE ID > ? ORDER BY ID""", String.class, logStart))
                .as("清空年龄写操作日志，操作人是系统")
                .containsExactly("PERSON_AGE_SET|SYSTEM|-|PERSON:" + card(1)
                        + "|{\"age\":45}|{\"age\":null,\"reason\":\"定位文件里这个卡编码换了姓名\"}");

        // 旧文件后到：不覆盖新姓名，也不算换名
        jdbc.update("UPDATE POS_PERSON SET AGE = 50 WHERE CARD_CODE = ?", card(1));
        drop(fileName("RYXX", "20260921080000"), file(FakeFiles.ryxxHeader("2026-09-21 08:00:00"), List.of(
                FakeFiles.ryxx(1, "测试甲", "采煤工", "综采一队"))));
        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("STALE");
        assertThat(jdbc.queryForMap("SELECT PERSON_NAME, AGE FROM POS_PERSON WHERE CARD_CODE = ?", card(1)).values())
                .extracting(Object::toString).containsExactly("测试丁", "50");
        assertThat(count("SELECT COUNT(*) FROM POS_INGEST_ERROR WHERE REASON LIKE '卡编码换了姓名%'")).isEqualTo(2);
    }

    @Test
    void stationNameAndStatusComeFromDifferentFiles() {
        String t = "2026-09-23 10:13:19";
        drop(fileName("JZSS", "20260923101319"), file(header(t), List.of(
                FakeFiles.jzss(8, 12, "0", t), FakeFiles.jzss(8, 99, "1", t))));
        drop(fileName("RYJZ", "20260330171121"), file(header("2026-03-30 17:11:21"), List.of(
                FakeFiles.ryjz(8, 12, "测试巷道"))));
        drop(fileName("RYQY", "20260923080019"), file(header("2026-09-23 08:00:19"), List.of(
                FakeFiles.ryqy(8, "-750大巷"))));

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsOnly("DONE");

        var s = jdbc.queryForMap("SELECT * FROM POS_STATION WHERE STATION_CODE = ?", FakeFiles.station(8, 12));
        assertThat(s.get("STATION_NAME")).isEqualTo("测试巷道");
        assertThat(s.get("RUN_STATUS").toString()).isEqualTo("0");
        assertThat(s.get("AREA_CODE")).as("虚拟列：基站编码前 16 位").isEqualTo(FakeFiles.area(8));
        assertThat(jdbc.queryForObject("SELECT STATION_NAME FROM POS_STATION WHERE STATION_CODE = ?", String.class,
                FakeFiles.station(8, 99))).as("JZSS 多出的基站没有名称").isNull();
        assertThat(count("SELECT COUNT(*) FROM POS_AREA")).isEqualTo(1);
    }

    @Test
    void nightShiftAcrossMidnightHasOneRowPerDay() {
        clock.set(LocalDateTime.of(2026, 9, 24, 6, 10));
        String in = "2026-09-23 22:00:00";
        drop(fileName("RYSS", "20260923235500"), file(header("2026-09-23 23:55:00"), List.of(ryssIn(1, 8, 12, in))));
        drop(fileName("RYSS", "20260924000500"), file(header("2026-09-24 00:05:00"), List.of(ryssIn(1, 8, 12, in))));
        drop(fileName("RYSS", "20260924060000"), file(header("2026-09-24 06:00:00"), List.of(
                ryssOut(1, 8, 12, in, "2026-09-24 05:50:00"))));

        inbox.scanOnce();

        List<String> rows = jdbc.queryForList("SELECT TO_CHAR(STAT_DATE, 'MM-DD') || ' ' || "
                + "TO_CHAR(FIRST_IN_TIME, 'DD HH24:MI') || ' ' || TO_CHAR(LAST_OUT_TIME, 'DD HH24:MI') "
                + "FROM POS_PRESENCE_DAILY ORDER BY STAT_DATE", String.class);
        assertThat(rows).containsExactly("09-23 23 22:00 24 05:50", "09-24 23 22:00 24 05:50");
        assertThat(count("SELECT COUNT(*) FROM V_POS_IN_WELL")).as("最新快照里已出井").isZero();
    }

    // ---------------- 必测的异常情况 ----------------

    @Test
    void incompleteFileIsSkippedUntilTimeout() throws IOException {
        String name = fileName("RYSS", "20260923101319");
        String full = file(header("2026-09-23 10:13:19"), List.of(ryssIn(1, 8, 12, IN)));
        Path f = drop(name, full.substring(0, full.length() - 2));
        Files.setLastModifiedTime(f, FileTime.from(clock.instant()));

        assertThat(inbox.scanOnce()).as("不完整：本轮跳过").isEmpty();
        assertThat(f).exists();
        assertThat(count("SELECT COUNT(*) FROM POS_INGEST_FILE")).isZero();

        clock.advance(Duration.ofMinutes(11));
        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("FAILED");
        assertThat(f).doesNotExist();
        assertThat(errorDir().resolve(name)).exists();
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON_STATE")).isZero();
    }

    @Test
    void sameFileTwiceIsIngestedOnce() {
        String name = fileName("RYSS", "20260923101319");
        String content = file(header("2026-09-23 10:13:19"), List.of(ryssIn(1, 8, 12, IN)));
        drop(name, content);
        inbox.scanOnce();
        drop(name, content);

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("DUPLICATE");

        assertThat(jdbc.queryForList("SELECT STATUS FROM POS_INGEST_FILE ORDER BY ID", String.class))
                .containsExactly("DONE", "DUPLICATE");
        assertThat(backupDir().resolve("20260923").resolve(name)).exists();
        assertThat(backupDir().resolve("20260923").resolve(name + ".dup1")).exists();
        assertThat(count("SELECT COUNT(*) FROM POS_HEADCOUNT_SERIES")).isEqualTo(1);
    }

    @Test
    void olderFileArrivingLaterDoesNotOverwrite() {
        drop(fileName("RYSS", "20260923101319"), file(header("2026-09-23 10:13:19"), List.of(
                ryssIn(1, 8, 12, IN), ryssIn(2, 8, 12, IN))));
        inbox.scanOnce();

        drop(fileName("RYSS", "20260923100819"), file(header("2026-09-23 10:08:19"), List.of(
                ryssIn(1, 23, 170, IN), ryssIn(3, 23, 170, IN))));
        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("STALE");

        assertThat(jdbc.queryForObject("SELECT STATION_CODE FROM POS_PERSON_STATE WHERE CARD_CODE = ?",
                String.class, card(1))).as("新数据不被旧文件覆盖").isEqualTo(FakeFiles.station(8, 12));
        assertThat(jdbc.queryForList("SELECT CARD_CODE FROM V_POS_IN_WELL ORDER BY CARD_CODE", String.class))
                .as("井下人数不变").containsExactly(card(1), card(2));
        assertThat(count("SELECT COUNT(*) FROM POS_HEADCOUNT_SERIES")).as("旧文件照常补全曲线").isEqualTo(2);
    }

    @Test
    void headerTimeInTheFutureIsRejected() {
        drop(fileName("RYSS", "20260923101319"), file(header("2026-09-23 10:13:19"), List.of(ryssIn(1, 8, 12, IN))));
        inbox.scanOnce();

        String future = fileName("RYSS", "20260923103000");
        drop(future, file(header("2026-09-23 10:30:00"), List.of(ryssIn(2, 8, 12, IN))));
        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("FAILED");

        assertThat(errorDir().resolve(future)).exists();
        assertThat(jdbc.queryForList("SELECT CARD_CODE FROM V_POS_IN_WELL", String.class)).containsExactly(card(1));
        assertThat(jdbc.queryForObject("SELECT ERROR_MSG FROM POS_INGEST_FILE WHERE FILE_NAME = ?", String.class,
                future)).contains("晚超过 5 分钟");
    }

    @Test
    void moreThanTwentyPercentBadRecordsRollsBackTheWholeFile() {
        List<String> records = new ArrayList<>();
        IntStream.rangeClosed(1, 4).forEach(i -> records.add(ryssIn(i, 8, 12, IN)));
        records.add(card(5) + ";测试5;1");
        records.add(card(6) + ";测试6;1");
        String name = fileName("RYSS", "20260923101319");
        drop(name, file(header("2026-09-23 10:13:19"), records));

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("FAILED");

        assertThat(count("SELECT COUNT(*) FROM POS_PERSON_STATE")).isZero();
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON")).isZero();
        assertThat(errorDir().resolve(name)).exists();
        assertThat(count("SELECT COUNT(*) FROM POS_INGEST_ERROR WHERE KIND = 'ERROR'")).isEqualTo(2);
    }

    @Test
    void fewBadRecordsArePartial() {
        List<String> records = new ArrayList<>();
        IntStream.rangeClosed(1, 9).forEach(i -> records.add(ryssIn(i, 8, 12, IN)));
        records.add(card(10) + ";测试10;1");
        drop(fileName("RYSS", "20260923101319"), file(header("2026-09-23 10:13:19"), records));

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("PARTIAL");

        assertThat(count("SELECT COUNT(*) FROM V_POS_IN_WELL")).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT RECORD_COUNT || '/' || ERROR_COUNT FROM POS_INGEST_FILE",
                String.class)).isEqualTo("9/1");
    }

    @Test
    void discardedTypesAreDeletedWithoutBackup() {
        String name = fileName("RYCS", "20260923085239");
        drop(name, file(header("2026-09-23 08:52:39"), List.of("x")));

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("DELETED");

        assertThat(mineInbox()).isEmptyDirectory();
        assertThat(ROOT.resolve("backup")).satisfiesAnyOf(p -> assertThat(p).doesNotExist(),
                p -> assertThat(filesUnder(p)).isEmpty());
        assertThat(count("SELECT COUNT(*) FROM POS_INGEST_FILE")).isZero();
    }

    @Test
    void symlinkIsMovedToErrorWithoutReadingItsTarget() throws IOException {
        Path outside = Files.createDirectories(ROOT.resolve("outside")).resolve("secret.txt");
        String secret = file(header("2026-09-23 10:13:19"), List.of(ryssIn(1, 8, 12, IN)));
        Files.writeString(outside, secret);
        String name = fileName("RYSS", "20260923101319");
        Files.createSymbolicLink(mineInbox().resolve(name), outside);

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("FAILED");

        assertThat(Files.isSymbolicLink(errorDir().resolve(name))).as("移走的是链接本身").isTrue();
        assertThat(Files.readString(outside)).as("指向的文件原样保留").isEqualTo(secret);
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON_STATE")).isZero();
        assertThat(jdbc.queryForObject("SELECT SHA256 FROM POS_INGEST_FILE", String.class)).as("没有读取内容").isNull();
    }

    @Test
    void otherMinesFilesGoToError() throws IOException {
        String other = FakeFiles.OTHER_MINE + "_RYSS_20260923101319.txt";
        Path otherDir = Files.createDirectories(ROOT.resolve("inbox/jxry/" + FakeFiles.OTHER_MINE));
        Files.writeString(otherDir.resolve(other), FakeFiles.OTHER_MINE + ";别的矿;2026-09-23 10:13:19~||");
        // 本矿的文件放错了目录
        Files.writeString(otherDir.resolve(fileName("RYSS", "20260923101319")), "x||");
        // 直接放在收件箱根目录
        Files.writeString(ROOT.resolve("inbox").resolve(fileName("RYQY", "20260923080019")), "x||");

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsOnly("FAILED").hasSize(3);

        assertThat(ROOT.resolve("error/jxry/" + FakeFiles.OTHER_MINE + "/" + other)).exists();
        assertThat(jdbc.queryForList("SELECT ERROR_MSG FROM POS_INGEST_FILE ORDER BY ERROR_MSG", String.class))
                .anySatisfy(m -> assertThat(m).contains("不是本矿"))
                .anySatisfy(m -> assertThat(m).contains("目录与文件名里的煤矿编码不一致"))
                .anySatisfy(m -> assertThat(m).contains("应放在"));
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON_STATE")).isZero();
    }

    @Test
    void unknownTypeIsBackedUpOnly() {
        String name = fileName("RYQJ", "20260923101319");
        drop(name, file(header("2026-09-23 10:13:19"), List.of("a;b;c")));

        assertThat(inbox.scanOnce()).extracting(PositioningInbox.Outcome::status).containsExactly("UNKNOWN");
        assertThat(backupDir().resolve("20260923").resolve(name)).exists();
    }

    @Test
    void hiddenFilesAreIgnored() throws IOException {
        Files.writeString(mineInbox().resolve(".DS_Store"), "x");
        assertThat(inbox.scanOnce()).isEmpty();
        assertThat(mineInbox().resolve(".DS_Store")).exists();
    }

    // ---------------- 状态接口 ----------------

    @Test
    void statusTurnsStaleAfterFifteenMinutesWithoutNewRyss() {
        assertThat(statusService.status().stale()).as("从没收到过 RYSS").isTrue();
        assertThat(statusService.status().inWellCount()).isNull();

        drop(fileName("RYSS", "20260923101319"), file(header("2026-09-23 10:13:19"), List.of(
                ryssIn(1, 8, 12, IN), ryssIn(2, 8, 12, IN))));
        inbox.scanOnce();

        PositioningStatus s = statusService.status();
        assertThat(s.stale()).isFalse();
        assertThat(s.lastDataTime()).isEqualTo(LocalDateTime.of(2026, 9, 23, 10, 13, 19));
        assertThat(s.inWellCount()).isEqualTo(2);
        assertThat(s.types()).extracting(PositioningStatus.TypeStatus::fileType)
                .startsWith("RYQY", "RYJZ", "RYXX", "RYSS", "JZSS");
        assertThat(s.types()).filteredOn(t -> t.fileType().equals("RYSS")).singleElement()
                .satisfies(t -> assertThat(t.lastFile().status()).isEqualTo("DONE"));

        clock.set(LocalDateTime.of(2026, 9, 23, 10, 29, 0));
        assertThat(statusService.status().stale()).isTrue();
    }

    // ---------------- 工具 ----------------

    private static Path backupDir() {
        return ROOT.resolve("backup/jxry/" + MINE);
    }

    private static Path errorDir() {
        return ROOT.resolve("error/jxry/" + MINE);
    }

    private static Path drop(String name, String content) {
        try {
            Path f = mineInbox().resolve(name);
            Files.writeString(f, content, StandardCharsets.UTF_8);
            return f;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Path> filesUnder(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }
}
