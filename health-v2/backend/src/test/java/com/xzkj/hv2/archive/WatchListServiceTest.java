package com.xzkj.hv2.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.xzkj.hv2.archive.ArchiveViews.WatchListItem;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.dashboard.DashboardService;

/** 重点监护、今日关注名单（docs/05 第八节）：人工加入和移除、今日关注当天有效、写操作日志。 */
class WatchListServiceTest extends ArchiveDbTestBase {

    @Autowired
    WatchListService lists;
    @Autowired
    ArchiveService archive;
    @Autowired
    DashboardService dashboard;

    @Test
    void addAndRemoveKeyPersonAreLoggedAndShowOnDashboard() {
        person(1, "张三", null, "综采一队", null);
        loginAs("zhang");

        WatchListItem added = lists.add(card(1), "KEY", "  高血压  ");

        assertThat(added.type()).isEqualTo("KEY");
        assertThat(added.note()).isEqualTo("高血压");
        assertThat(added.expireDate()).as("重点监护一直有效").isNull();
        assertThat(added.addedBy()).isEqualTo("zhang");
        assertThat(added.name()).isEqualTo("张三");
        assertThat(lists.list("KEY")).extracting(WatchListItem::cardCode).containsExactly(card(1));
        assertThat(dashboard.keyPersons()).hasSize(1);
        assertThat(archive.detail(card(1)).lists().get("KEY").id()).isEqualTo(added.id());

        // 再加一次：已经在名单里，原样返回，不重复记日志
        assertThat(lists.add(card(1), "KEY", "别的备注").id()).isEqualTo(added.id());
        lists.remove(added.id());

        assertThat(lists.list("KEY")).isEmpty();
        assertThat(archive.detail(card(1)).lists().get("KEY")).isNull();
        String obj = "{\"id\":" + added.id() + ",\"type\":\"KEY\",\"note\":\"高血压\",\"expireDate\":null}";
        assertThat(logs()).containsExactly(
                "WATCH_LIST_ADD|zhang|PERSON:" + card(1) + "||" + obj,
                "WATCH_LIST_REMOVE|zhang|PERSON:" + card(1) + "|" + obj + "|");
    }

    @Test
    void todayListExpiresAtMidnightAndCanBeAddedAgainTheNextDay() {
        person(1, "张三", null, null, null);
        WatchListItem first = lists.add(card(1), "TODAY", null);
        assertThat(first.expireDate()).isEqualTo(TODAY);
        assertThat(lists.list("TODAY")).hasSize(1);

        clock.set(TODAY.plusDays(1).atStartOfDay());

        assertThat(lists.list("TODAY")).as("过了 24 点自动失效").isEmpty();
        assertThat(archive.detail(card(1)).lists().get("TODAY")).isNull();
        WatchListItem again = lists.add(card(1), "TODAY", "复查");
        assertThat(again.id()).as("覆盖原来那一行").isEqualTo(first.id());
        assertThat(again.expireDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(lists.list("TODAY")).extracting(WatchListItem::note).containsExactly("复查");
        assertThat(logs()).hasSize(2).allMatch(l -> l.startsWith("WATCH_LIST_ADD|admin|"));
    }

    @Test
    void keyAndTodayAreIndependent() {
        person(1, "张三", null, null, null);
        lists.add(card(1), "KEY", null);
        lists.add(card(1), "TODAY", null);

        var detail = archive.detail(card(1)).lists();

        assertThat(detail.get("KEY")).isNotNull();
        assertThat(detail.get("TODAY")).isNotNull();
        assertThat(detail.get("KEY").id()).isNotEqualTo(detail.get("TODAY").id());
    }

    @Test
    void errors() {
        person(1, "张三", null, null, null);

        assertThatThrownBy(() -> lists.add(card(99), "KEY", null)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> lists.add(card(1), "KEY", "字".repeat(201)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> lists.remove(123456)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(logs()).isEmpty();
    }
}
