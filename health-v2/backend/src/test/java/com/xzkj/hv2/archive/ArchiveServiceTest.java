package com.xzkj.hv2.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.xzkj.hv2.archive.ArchiveViews.AlertRecord;
import com.xzkj.hv2.archive.ArchiveViews.ArchivePerson;
import com.xzkj.hv2.archive.ArchiveViews.PersonDetail;
import com.xzkj.hv2.archive.ArchiveViews.StepDay;
import com.xzkj.hv2.archive.ArchiveViews.Trend;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.exception.BizException;

/** 健康档案、个人档案、年龄录入（docs/05 第六、七节，docs/08 阶段 5 验收），连 Oracle 测试用户。 */
class ArchiveServiceTest extends ArchiveDbTestBase {

    @Autowired
    ArchiveService archive;

    // ================= 健康档案列表 =================

    /** 6 个人：1–4 绑了启用的手表，5 绑的手表停用了，6 没绑。 */
    private void sixPeople() {
        person(1, "张三", "采煤工", "综采一队", null);
        person(2, "李四", "电工", "机电队", 45);
        person(3, "王五", "采煤工", "综采一队", null);
        person(4, "白大", null, null, null);
        person(5, "赵六", "电工", "通风队", null);
        person(6, "钱七", "掘进工", "掘进二队", null);
        for (int i = 1; i <= 4; i++) {
            bind(i, 1, NOW.minusMinutes(1));
        }
        bind(5, 0, NOW.minusMinutes(1));
    }

    @Test
    void listShowsOnlyPeopleWithAnEnabledWatchSortedByPinyin() {
        sixPeople();

        PageResult<ArchivePerson> all = archive.persons(null, null, null, 1, 12);

        assertThat(all.total()).isEqualTo(4).isEqualTo(count("""
                SELECT COUNT(*) FROM POS_PERSON p
                 WHERE EXISTS (SELECT 1 FROM DEVICE d WHERE d.CARD_CODE = p.CARD_CODE AND d.STATUS = 1)"""));
        // 拼音：白大 b、李四 l、王五 w、张三 z
        assertThat(all.list()).extracting(ArchivePerson::name).containsExactly("白大", "李四", "王五", "张三");
        ArchivePerson li = all.list().get(1);
        assertThat(li.cardCode()).isEqualTo(card(2));
        assertThat(li.age()).isEqualTo(45);
        assertThat(all.list().get(0).age()).as("没录入年龄是 null，页面显示\"未录入\"").isNull();
    }

    @Test
    void filtersByDeptJobKindAndKeyword() {
        sixPeople();

        assertThat(archive.persons("综采一队", null, null, 1, 12).list()).extracting(ArchivePerson::name)
                .containsExactly("王五", "张三");
        assertThat(archive.persons(null, "电工", null, 1, 12).list()).extracting(ArchivePerson::name)
                .as("赵六的手表停用了，不算").containsExactly("李四");
        assertThat(archive.persons("综采一队", "电工", null, 1, 12).total()).isZero();
        assertThat(archive.persons(null, null, "王", 1, 12).list()).extracting(ArchivePerson::name)
                .containsExactly("王五");
        // 卡号（卡编码后 5 位）也能查
        assertThat(archive.persons(null, null, card(3).substring(12), 1, 12).list())
                .extracting(ArchivePerson::cardCode).containsExactly(card(3));
        // % 不是通配符
        assertThat(archive.persons(null, null, "%", 1, 12).total()).isZero();
        assertThat(archive.persons(" ", " ", " ", 1, 12).total()).as("空白当作不限").isEqualTo(4);
    }

    @Test
    void pagesAreConsistentWithTotal() {
        for (int i = 1; i <= 26; i++) {
            person(i, "测试" + String.format("%02d", i), "采煤工", "综采一队", null);
            bind(i, 1, null);
        }

        PageResult<ArchivePerson> p1 = archive.persons(null, null, null, 1, 12);
        PageResult<ArchivePerson> p3 = archive.persons(null, null, null, 3, 12);

        assertThat(p1.total()).isEqualTo(26);
        assertThat(p1.list()).hasSize(12);
        assertThat(p3.list()).extracting(ArchivePerson::name).containsExactly("测试25", "测试26");
        assertThat(archive.persons(null, null, null, 4, 12).list()).isEmpty();
    }

    @Test
    void filterOptionsComeFromBoundPeople() {
        sixPeople();

        ArchiveViews.Filters f = archive.filters();

        assertThat(f.depts()).containsExactly("机电队", "综采一队");
        assertThat(f.jobKinds()).containsExactly("采煤工", "电工");
    }

    // ================= 个人档案 =================

    @Test
    void personWithoutAnyWatchDataHasNullsNotErrors() {
        person(7, "孙八", null, null, null);

        PersonDetail d = archive.detail(card(7));

        assertThat(d.name()).isEqualTo("孙八");
        assertThat(d.age()).isNull();
        assertThat(d.watch().state()).isEqualTo("UNBOUND");
        assertThat(d.battery()).isNull();
        assertThat(d.vitals()).containsOnlyKeys("HEART_RATE", "SPO2", "TEMPERATURE", "BLOOD_PRESSURE");
        assertThat(d.vitals().values()).containsOnlyNulls();
        assertThat(d.steps()).isNull();
        assertThat(d.position()).isNull();
        assertThat(d.lists()).containsEntry("KEY", null).containsEntry("TODAY", null);
        assertThat(archive.trend(card(7), "HEART_RATE", null).points()).isEmpty();
        assertThat(archive.steps(card(7), 7).days()).hasSize(7).extracting(StepDay::steps).containsOnlyNulls();
        assertThat(archive.alerts(card(7), 1, 20).total()).isZero();
    }

    @Test
    void detailHasLatestVitalsWithCollectionTimeWatchPositionAndLists() {
        person(1, "张三", "采煤工", "综采一队", 40);
        bind(1, 1, NOW.minusMinutes(2));
        latest(1, "HEART_RATE", "72", null, NOW.minusMinutes(3));
        latest(1, "BLOOD_PRESSURE", "121", "79", NOW.minusMinutes(30));
        steps(1, TODAY, 3456);
        steps(1, TODAY.minusDays(1), 8000);
        jdbc.update("INSERT INTO POS_AREA (AREA_CODE, AREA_NAME, SRC_DATA_TIME) VALUES (?, '下口区域', SYSTIMESTAMP)", AREA);
        jdbc.update("INSERT INTO POS_STATION (STATION_CODE, STATION_NAME, RUN_STATUS) VALUES (?, '新副井下口1', 1)", STATION);
        inWell(1, NOW.minusMinutes(4));
        jdbc.update("INSERT INTO WATCH_LIST (CARD_CODE, LIST_TYPE, ADDED_BY) VALUES (?, 'KEY', 'admin')", card(1));
        // 昨天的今日关注已经失效
        jdbc.update("""
                INSERT INTO WATCH_LIST (CARD_CODE, LIST_TYPE, EXPIRE_DATE, ADDED_BY) VALUES (?, 'TODAY', ?, 'admin')""",
                card(1), java.sql.Date.valueOf(TODAY.minusDays(1)));

        PersonDetail d = archive.detail(card(1));

        assertThat(d.jobKind()).isEqualTo("采煤工");
        assertThat(d.dept()).isEqualTo("综采一队");
        assertThat(d.age()).isEqualTo(40);
        assertThat(d.watch().state()).isEqualTo("ONLINE");
        assertThat(d.battery().pct()).isEqualTo(76);
        assertThat(d.vitals().get("HEART_RATE").value()).isEqualByComparingTo("72");
        assertThat(d.vitals().get("HEART_RATE").collectedAt()).isEqualTo(NOW.minusMinutes(3));
        assertThat(d.vitals().get("HEART_RATE").stale()).isFalse();
        assertThat(d.vitals().get("BLOOD_PRESSURE").value2()).isEqualByComparingTo("79");
        assertThat(d.vitals().get("BLOOD_PRESSURE").stale()).as("30 分钟前的，超过 12 分钟").isTrue();
        assertThat(d.vitals().get("SPO2")).isNull();
        assertThat(d.steps().steps()).as("今天的步数，不是昨天的").isEqualTo(3456);
        assertThat(d.position().areaName()).isEqualTo("下口区域");
        assertThat(d.position().stationName()).isEqualTo("新副井下口1");
        assertThat(d.position().placed()).isFalse();
        assertThat(d.position().stationAbnormal()).as("基站通讯中断").isTrue();
        assertThat(d.lists().get("KEY")).isNotNull();
        assertThat(d.lists().get("TODAY")).isNull();
    }

    @Test
    void unknownPersonIs404() {
        assertThatThrownBy(() -> archive.detail(card(99))).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> archive.trend(card(99), "SPO2", null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> archive.alerts(card(99), 1, 20)).isInstanceOf(BizException.class);
    }

    @Test
    void trendIsOneDayOfRawReadingsOfOneMetric() {
        person(1, "张三", null, null, null);
        record(1, "HEART_RATE", "70", null, TODAY.atTime(8, 0), 1);
        record(1, "HEART_RATE", "75", null, TODAY.atTime(8, 4), 2);
        record(1, "HEART_RATE", "99", null, TODAY.minusDays(1).atTime(23, 59, 59), 3); // 昨天
        record(1, "SPO2", "97", null, TODAY.atTime(8, 1), 4);                         // 别的指标
        record(1, "BLOOD_PRESSURE", "121", "79", TODAY.atTime(8, 2), 5);

        Trend hr = archive.trend(card(1), "HEART_RATE", null);

        assertThat(hr.date()).isEqualTo(TODAY);
        assertThat(hr.gapMinutes()).isEqualTo(12);
        assertThat(hr.points()).extracting(p -> p.value().intValue()).containsExactly(70, 75);
        assertThat(hr.points().get(0).time()).isEqualTo(TODAY.atTime(8, 0));
        assertThat(archive.trend(card(1), "HEART_RATE", TODAY.minusDays(1)).points())
                .extracting(p -> p.value().intValue()).containsExactly(99);
        Trend bp = archive.trend(card(1), "BLOOD_PRESSURE", null);
        assertThat(bp.points().get(0).value2()).isEqualByComparingTo(new BigDecimal("79"));
    }

    @Test
    void stepsAreTheLastSevenDaysWithGaps() {
        person(1, "张三", null, null, null);
        steps(1, TODAY, 100);
        steps(1, TODAY.minusDays(2), 0);
        steps(1, TODAY.minusDays(7), 9999); // 第 8 天前，不在范围里

        List<StepDay> days = archive.steps(card(1), 7).days();

        assertThat(days).extracting(StepDay::date).first().isEqualTo(TODAY.minusDays(6));
        assertThat(days).extracting(StepDay::date).last().isEqualTo(TODAY);
        assertThat(days).extracting(StepDay::steps).containsExactly(null, null, null, null, 0L, null, 100L);
    }

    @Test
    void alertsArePagedNewestFirstAndDeviceCodesAreHidden() {
        person(1, "张三", null, null, null);
        event(1, "THRESHOLD", "HR_HIGH", "HEART_RATE", "132", NOW.minusHours(3), 4);
        event(1, "DEVICE", "LOW_BATTERY", "OTHER", "03", NOW.minusHours(2), 1);
        event(1, "DEVICE", "SOS", "SOS", "01", NOW.minusHours(1), 1);

        PageResult<AlertRecord> p1 = archive.alerts(card(1), 1, 2);

        assertThat(p1.total()).isEqualTo(3);
        assertThat(p1.list()).extracting(AlertRecord::event).containsExactly("SOS 求救", "低电");
        assertThat(p1.list()).extracting(AlertRecord::value).containsOnlyNulls();
        AlertRecord hr = archive.alerts(card(1), 2, 2).list().getFirst();
        assertThat(hr.event()).isEqualTo("心率偏高");
        assertThat(hr.value()).isEqualTo("132");
        assertThat(hr.count()).isEqualTo(4);
    }

    // ================= 年龄 =================

    @Test
    void settingAgeShowsImmediatelyAndIsLogged() {
        person(1, "张三", null, null, null);
        loginAs("zhang");

        ArchiveViews.AgeResult r = archive.setAge(card(1), 45);

        assertThat(r.age()).isEqualTo(45);
        assertThat(r.updatedBy()).isEqualTo("zhang");
        assertThat(r.updatedAt()).isEqualTo(NOW);
        assertThat(archive.detail(card(1)).age()).isEqualTo(45);
        assertThat(archive.persons(null, null, null, 1, 12).total()).as("没绑手表，不在列表里").isZero();

        archive.setAge(card(1), 45); // 一样的值：不改、不记
        archive.setAge(card(1), 46);

        assertThat(logs()).containsExactly(
                "PERSON_AGE_SET|zhang|PERSON:" + card(1) + "|{\"age\":null}|{\"age\":45}",
                "PERSON_AGE_SET|zhang|PERSON:" + card(1) + "|{\"age\":45}|{\"age\":46}");
    }

    @Test
    void ageOutOfRangeOrUnknownPersonIsRejectedWithoutLog() {
        person(1, "张三", null, null, null);

        assertThatThrownBy(() -> archive.setAge(card(1), 15)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> archive.setAge(card(1), 76)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> archive.setAge(card(98), 30)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        archive.setAge(card(1), 16);
        archive.setAge(card(1), 75);

        assertThat(logs()).hasSize(2);
    }
}
