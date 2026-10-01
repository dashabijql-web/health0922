package com.xzkj.hv2.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.xzkj.hv2.common.exception.BizException;

/**
 * 两个人真的同时提交（docs/06 第七节"并发"）：甲的事务改完先不提交，乙这时也提交同一个基站，
 * 乙要等甲提交后才往下走，然后收到 409。用两个线程、两个数据库事务模拟，不靠版本号预先错开。
 */
class MapConcurrencyTest extends MapDbTestBase {

    @Autowired
    StationMarkService marks;
    @Autowired
    TransactionTemplate tx;

    @Test
    void secondFirstPlacementCollidesOnPrimaryKey() throws Exception {
        String st = newStation(AREA_A, "甲", 0);
        assertSecondGetsConflict(() -> marks.save(st, X0, Y0, null, 0), () -> marks.save(st, X0, Y0, "乙起的名", 0));
        assertThat(logs(st)).hasSize(1);
    }

    @Test
    void secondMoveWithSameVersionConflicts() throws Exception {
        String st = newStation(AREA_A, "甲", 0);
        marks.save(st, X0, Y0, null, 0);
        assertSecondGetsConflict(() -> marks.save(st, X0.add(BigDecimal.ONE), Y0, null, 1),
                () -> marks.save(st, X0.add(BigDecimal.TEN), Y0, null, 1));
        assertThat(count("SELECT VERSION FROM POS_STATION_MARK WHERE STATION_CODE = ?", st)).isEqualTo(2);
    }

    @Test
    void deleteWhileSomeoneMovesConflicts() throws Exception {
        String st = newStation(AREA_A, "甲", 0);
        marks.save(st, X0, Y0, null, 0);
        assertSecondGetsConflict(() -> marks.save(st, X0.add(BigDecimal.ONE), Y0, null, 1), () -> marks.delete(st, 1));
        assertThat(count("SELECT COUNT(*) FROM POS_STATION_MARK WHERE STATION_CODE = ?", st)).isEqualTo(1);
    }

    /** 甲在事务里做完 first 但先不提交；乙做 second，应当卡住，等甲提交后收到 409。 */
    private void assertSecondGetsConflict(Runnable first, Runnable second) throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            first.run();
            firstDone.countDown();
            await(release);
        }));
        assertThat(firstDone.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Throwable> b = CompletableFuture.supplyAsync(() -> {
            try {
                second.run();
                return null;
            } catch (Throwable e) {
                return e;
            }
        });
        Thread.sleep(500);
        assertThat(b).as("甲提交之前，乙应当在等锁").isNotDone();

        release.countDown();
        a.get(10, TimeUnit.SECONDS);
        Throwable e = b.get(10, TimeUnit.SECONDS);
        assertThat(e).isInstanceOfSatisfying(BizException.class, biz -> {
            assertThat(biz.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(biz.getMessage()).isEqualTo("这个基站刚刚被 admin 修改，请刷新");
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
