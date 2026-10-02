package com.xzkj.hv2ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoakSummaryTest {

    @Test
    void summarizesFirstAndLastSamplesAndCountsDowntime(@TempDir Path dir) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", Soak.COLUMNS));
        for (int i = 0; i < 8; i++) {
            // 前 2 次每批 10 ms、后面 20 ms；堆从 100 涨到 170
            String flushAvg = i < 2 ? "10.0" : "20.0";
            lines.add("2026-10-02 10:0" + i + ":00,1,200,200," + i + ",12," + flushAvg + ",30.0,500,1,0,0,0,0,1,0,"
                    + (100 + i * 10) + ",40,0.05,1");
        }
        lines.add("2026-10-02 10:08:00,0,,,,,,,,,,,,,,,,,,");
        Path csv = dir.resolve("soak.csv");
        Files.write(csv, lines);

        String s = Soak.summary(csv);

        assertThat(s).contains("采样 9 次，后端无响应 1 次")
                .contains("连接数：200–200；在线：200–200")
                .contains("缓冲积压：最大 7，开头 2 次采样最大 1，最后 2 次最大 7")
                .contains("每批写库平均：开头 10.0 ms，最后 20.0 ms")
                .contains("堆内存平均：开头 105.0 MB，最后 165.0 MB")
                .contains("收包 4000，丢弃 8，进死信 0")
                .contains("定位文件：入库 8 份，失败 0 份");
    }
}
