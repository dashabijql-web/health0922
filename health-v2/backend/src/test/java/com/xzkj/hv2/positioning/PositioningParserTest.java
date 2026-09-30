package com.xzkj.hv2.positioning;

import static com.xzkj.hv2.positioning.FakeFiles.bytes;
import static com.xzkj.hv2.positioning.FakeFiles.card;
import static com.xzkj.hv2.positioning.FakeFiles.file;
import static com.xzkj.hv2.positioning.FakeFiles.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 定位文件格式解析（docs/02 第三节）。只用脱敏假数据。 */
class PositioningParserTest {

    private static final String T = "2026-09-23 10:13:19";
    private static final String IN = "2026-09-23 06:33:52";

    private static ParsedFile parse(PositioningFileType type, String content) throws PositioningFormatException {
        return PositioningParser.parse(type, FakeFiles.MINE, bytes(content));
    }

    @Test
    void normalRyss() throws Exception {
        ParsedFile p = parse(PositioningFileType.RYSS, file(header(T), List.of(
                FakeFiles.ryssIn(1, 8, 12, IN),
                FakeFiles.ryssIn(2, 8, 13, IN),
                FakeFiles.ryssOut(3, 23, 170, IN, "2026-09-23 10:00:00"))));

        assertThat(p.headerTime()).isEqualTo(LocalDateTime.of(2026, 9, 23, 10, 13, 19));
        assertThat(p.recordCount()).isEqualTo(3);
        assertThat(p.issues()).isEmpty();
        List<PositioningRow.PersonState> rows = p.rowsOf(PositioningRow.PersonState.class);
        assertThat(rows).extracting(PositioningRow.PersonState::cardCode).containsExactly(card(1), card(2), card(3));

        PositioningRow.PersonState first = rows.get(0);
        assertThat(first.inOutFlag()).isEqualTo(1);
        assertThat(first.areaCode()).isEqualTo(FakeFiles.area(8));
        assertThat(first.stationCode()).isEqualTo(FakeFiles.station(8, 12));
        assertThat(first.shiftMode()).isEqualTo("三八制");
        assertThat(first.distanceM()).isEqualByComparingTo(new BigDecimal("-3.50"));
        assertThat(first.workStatus()).isEqualTo("正常");
        assertThat(rows.get(2).outTime()).isEqualTo(LocalDateTime.of(2026, 9, 23, 10, 0));
    }

    @Test
    void emptyFieldsKeepTheirPosition() throws Exception {
        // 出井时刻、区域、距离为空：后面的字段不能错位
        String rec = String.join(";", card(1), "测试1", "1", IN, "", "", "", FakeFiles.station(8, 12), IN,
                "三八制", "", "正常", "", "", "");
        ParsedFile p = parse(PositioningFileType.RYSS, file(header(T), List.of(rec)));

        PositioningRow.PersonState r = p.rowsOf(PositioningRow.PersonState.class).get(0);
        assertThat(r.outTime()).isNull();
        assertThat(r.areaCode()).isNull();
        assertThat(r.areaEnterTime()).isNull();
        assertThat(r.stationCode()).isEqualTo(FakeFiles.station(8, 12));
        assertThat(r.distanceM()).isNull();
        assertThat(r.workStatus()).isEqualTo("正常");
        assertThat(r.isLeader()).isNull();
    }

    @Test
    void ryxxHeaderTimeIsTheEighthSegment() throws Exception {
        ParsedFile p = parse(PositioningFileType.RYXX, file(FakeFiles.ryxxHeader("2026-03-30 16:25:21"), List.of(
                FakeFiles.ryxx(1, "测试1", "未设置", "综采一队"))));

        assertThat(p.headerTime()).isEqualTo(LocalDateTime.of(2026, 3, 30, 16, 25, 21));
        PositioningRow.Person person = p.rowsOf(PositioningRow.Person.class).get(0);
        assertThat(person.jobKind()).as("工种\"未设置\"存为空").isNull();
        assertThat(person.jobTitle()).isNull();
        assertThat(person.dept()).isEqualTo("综采一队");
        assertThat(person.isSpecial()).isEqualTo(1);
    }

    @Test
    void duplicateCardKeepsTheLaterRecordWithAWarning() throws Exception {
        ParsedFile p = parse(PositioningFileType.RYXX, file(FakeFiles.ryxxHeader(T), List.of(
                FakeFiles.ryxx(1, "测试甲", "采煤工", "综采一队"),
                FakeFiles.ryxx(2, "测试2", "掘进工", "掘进二队"),
                FakeFiles.ryxx(1, "测试乙", "电工", "机电队"))));

        assertThat(p.recordCount()).isEqualTo(3);
        assertThat(p.errorCount()).isZero();
        List<PositioningRow.Person> rows = p.rowsOf(PositioningRow.Person.class);
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r -> r.cardCode().equals(card(1)))
                .singleElement().extracting(PositioningRow.Person::personName).isEqualTo("测试乙");
        assertThat(p.issues()).singleElement().satisfies(i -> {
            assertThat(i.kind()).isEqualTo(RecordIssue.Kind.WARN);
            assertThat(i.lineNo()).isEqualTo(3);
            assertThat(i.reason()).contains("第 1 条重复");
        });
    }

    @Test
    void incompleteFileIsDetected() {
        String complete = file(header(T), List.of(FakeFiles.ryssIn(1, 8, 12, IN)));
        String half = complete.substring(0, complete.length() / 2);

        assertThat(PositioningParser.isComplete(bytes(complete))).isTrue();
        assertThat(PositioningParser.isComplete(bytes(complete + "\r\n  "))).as("末尾空白不影响").isTrue();
        assertThat(PositioningParser.isComplete(bytes(half))).isFalse();
        assertThat(PositioningParser.isComplete(bytes(complete.substring(0, complete.length() - 1)))).isFalse();
        assertThatThrownBy(() -> parse(PositioningFileType.RYSS, half))
                .isInstanceOf(PositioningFormatException.class);
    }

    @Test
    void badHeaderFailsTheWholeFile() {
        String rec = FakeFiles.ryssIn(1, 8, 12, IN);
        assertThatThrownBy(() -> parse(PositioningFileType.RYSS, file(FakeFiles.OTHER_MINE + ";别的矿;" + T,
                List.of(rec)))).hasMessageContaining("煤矿编码");
        assertThatThrownBy(() -> parse(PositioningFileType.RYSS, file(FakeFiles.MINE + ";测试煤矿", List.of(rec))))
                .hasMessageContaining("文件头只有 2 段");
        assertThatThrownBy(() -> parse(PositioningFileType.RYSS, file(header("2026/09/23 10:13:19"), List.of(rec))))
                .hasMessageContaining("时间格式");
        assertThatThrownBy(() -> parse(PositioningFileType.RYXX, file(header(T), List.of())))
                .as("RYXX 文件头要 8 段").hasMessageContaining("应为 8 段");
    }

    @Test
    void badRecordsAreSkippedWithReasons() throws Exception {
        ParsedFile p = parse(PositioningFileType.RYSS, file(header(T), List.of(
                FakeFiles.ryssIn(1, 8, 12, IN),
                FakeFiles.ryssIn(2, 8, 12, IN).replaceFirst(";", "；"),
                card(3) + ";测试3;1",
                FakeFiles.ryssIn(4, 8, 12, IN).replace(card(4), ""),
                FakeFiles.ryssIn(5, 8, 12, "2026-13-01 00:00:00"),
                FakeFiles.ryssIn(6, 8, 12, IN).replace(";1;" + IN, ";7;" + IN))));

        assertThat(p.recordCount()).isEqualTo(6);
        assertThat(p.rows()).hasSize(1);
        assertThat(p.issues()).extracting(RecordIssue::lineNo, RecordIssue::reason).containsExactly(
                org.assertj.core.groups.Tuple.tuple(2, "含全角分号"),
                org.assertj.core.groups.Tuple.tuple(3, "字段数 3，应为 15"),
                org.assertj.core.groups.Tuple.tuple(4, "人员卡编码为空"),
                org.assertj.core.groups.Tuple.tuple(5, "入井时刻格式不对"),
                org.assertj.core.groups.Tuple.tuple(6, "出入井标志应为 0、1 或 2"));
        assertThat(p.errorCount()).isEqualTo(5);
    }

    @Test
    void strayLineBreaksBomAndBlanksAreCleaned() throws Exception {
        String content = "﻿" + header(T) + "\r\n~ " + FakeFiles.ryssIn(1, 8, 12, IN) + "\r\n~\n~||\r\n";
        ParsedFile p = parse(PositioningFileType.RYSS, content);

        assertThat(p.recordCount()).isEqualTo(1);
        assertThat(p.issues()).isEmpty();
        assertThat(p.rowsOf(PositioningRow.PersonState.class).get(0).cardCode()).isEqualTo(card(1));
    }

    @Test
    void emptySnapshotIsNormal() throws Exception {
        ParsedFile p = parse(PositioningFileType.RYSS, header(T) + "~||");
        assertThat(p.recordCount()).isZero();
        assertThat(p.rows()).isEmpty();
    }

    @Test
    void nonUtf8IsRejected() {
        byte[] gbk = file(header(T), List.of(FakeFiles.ryssIn(1, 8, 12, IN))).getBytes(Charset.forName("GBK"));
        assertThatThrownBy(() -> PositioningParser.parse(PositioningFileType.RYSS, FakeFiles.MINE, gbk))
                .hasMessageContaining("UTF-8");
    }

    @Test
    void otherTypes() throws Exception {
        ParsedFile qy = parse(PositioningFileType.RYQY, file(header(T), List.of(FakeFiles.ryqy(23, "-750大巷"))));
        assertThat(qy.rowsOf(PositioningRow.Area.class).get(0))
                .isEqualTo(new PositioningRow.Area(FakeFiles.area(23), "其它区域", 50, "-750大巷"));

        ParsedFile jz = parse(PositioningFileType.RYJZ, file(header(T), List.of(FakeFiles.ryjz(8, 12, "测试巷道"))));
        assertThat(jz.rowsOf(PositioningRow.StationName.class).get(0))
                .isEqualTo(new PositioningRow.StationName(FakeFiles.station(8, 12), "测试巷道"));

        ParsedFile ss = parse(PositioningFileType.JZSS, file(header(T), List.of(
                FakeFiles.jzss(8, 12, "1", T), FakeFiles.jzss(8, 13, "5", T))));
        assertThat(ss.rowsOf(PositioningRow.StationStatus.class).get(0))
                .isEqualTo(new PositioningRow.StationStatus(FakeFiles.station(8, 12), 1, 1,
                        LocalDateTime.of(2026, 9, 23, 10, 13, 19)));
        assertThat(ss.issues()).singleElement().extracting(RecordIssue::reason).isEqualTo("运行状态应为 0、1、2 或 9");
    }

    @Test
    void fileNameRules() {
        PositioningFileName n = PositioningFileName.parse("620823009203_RYSS_20260923101319.txt");
        assertThat(n).isNotNull();
        assertThat(n.knownType()).isEqualTo(PositioningFileType.RYSS);
        assertThat(n.uploadDate()).isEqualTo("20260923");
        assertThat(PositioningFileName.parse("620823009203_RYCS_20260923085239.txt").discarded()).isTrue();
        assertThat(PositioningFileName.parse("620823009203_RYYJCS_20260923085239.txt").discarded()).isTrue();
        assertThat(PositioningFileName.parse("620823009203_RYQJ_20260923085239.txt").knownType()).isNull();
        assertThat(PositioningFileName.parse("620823009203_ryss_20260923101319.txt")).isNull();
        assertThat(PositioningFileName.parse("620823009203_RYSS_20260923101319.txt.tmp")).isNull();
        assertThat(PositioningFileName.parse("../620823009203_RYSS_20260923101319.txt")).isNull();
    }
}
