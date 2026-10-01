package com.xzkj.hv2.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cn.dev33.satoken.spring.SaBeanInject;
import cn.dev33.satoken.spring.SaBeanRegister;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.config.JacksonConfig;
import com.xzkj.hv2.common.config.SaTokenConfig;
import com.xzkj.hv2.common.config.TimeConfig;
import com.xzkj.hv2.common.exception.GlobalExceptionHandler;
import com.xzkj.hv2.positioning.PositioningFreshness;
import com.xzkj.hv2.report.WearExportController;
import com.xzkj.hv2.report.WearExportService;
import com.xzkj.hv2.support.MutableClock;

/** 接口层：要登录、参数校验、时间格式、Excel 下载的响应头（docs/05 第二、四节，docs/01 第五节）。 */
@WebMvcTest(controllers = {DashboardController.class, PortalController.class, WearExportController.class})
@ImportAutoConfiguration({SaTokenContextRegister.class, SaBeanRegister.class, SaBeanInject.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class, JacksonConfig.class, DashboardControllerTest.Time.class})
class DashboardControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0, 0);

    @TestConfiguration
    static class Time {
        @Bean
        Clock clock() {
            return new MutableClock(NOW, TimeConfig.ZONE);
        }
    }

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DashboardService service;
    @MockitoBean
    private WearExportService export;

    private String token;

    @BeforeEach
    void login() {
        token = StpUtil.createLoginSession(1L);
    }

    @Test
    void everyEndpointNeedsLogin() throws Exception {
        for (String url : List.of("/api/portal/summary", "/api/dashboard/overview", "/api/dashboard/headcount",
                "/api/dashboard/alerts?category=SOS", "/api/dashboard/device-events", "/api/dashboard/key-persons",
                "/api/dashboard/headcount-series", "/api/dashboard/in-well-persons", "/api/dashboard/steps-rank",
                "/api/export/wear")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        }
    }

    @Test
    void headcountWithNullsAndTimeFormat() throws Exception {
        when(service.headcount()).thenReturn(new DashboardViews.Headcount(null, null, null, null, null, null,
                new PositioningFreshness(LocalDateTime.of(2026, 9, 23, 9, 55, 7), true)));

        mvc.perform(get("/api/dashboard/headcount").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.inWell").value((Object) null))
                .andExpect(jsonPath("$.data.positioning.dataTime").value("2026-09-23 09:55:07"))
                .andExpect(jsonPath("$.data.positioning.stale").value(true));
    }

    @Test
    void badParamsAre400() throws Exception {
        mvc.perform(get("/api/dashboard/alerts?category=OTHER").header("satoken", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("告警类别不正确"));
        mvc.perform(get("/api/dashboard/alerts").header("satoken", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/device-events?size=0").header("satoken", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/in-well-persons?page=0").header("satoken", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/steps-rank?limit=51").header("satoken", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/headcount-series?date=2026-9-1").header("satoken", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/export/wear?date=abc").header("satoken", token)).andExpect(status().isBadRequest());
    }

    @Test
    void pagedListsPassParams() throws Exception {
        when(service.inWellPersons(anyString(), anyInt(), anyInt())).thenReturn(new PageResult<>(0, 2, 5, List.of()));
        mvc.perform(get("/api/dashboard/in-well-persons?keyword=张&page=2&size=5").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(2));
        verify(service).inWellPersons("张", 2, 5);
    }

    @Test
    void seriesDefaultsToToday() throws Exception {
        when(service.headcountSeries(any())).thenReturn(new DashboardViews.HeadcountSeries("2026-09-23", 15, List.of()));
        mvc.perform(get("/api/dashboard/headcount-series").header("satoken", token)).andExpect(status().isOk());
        verify(service).headcountSeries(LocalDate.of(2026, 9, 23));
    }

    @Test
    void wearDownloadsXlsx() throws Exception {
        when(export.defaultDate()).thenReturn(LocalDate.of(2026, 9, 22));
        when(export.today()).thenReturn(LocalDate.of(2026, 9, 23));
        when(export.workbook(eq(LocalDate.of(2026, 9, 22)))).thenReturn(new byte[] {1, 2, 3});
        when(export.fileName()).thenReturn("佩戴情况_20260923100000.xlsx");

        mvc.perform(get("/api/export/wear").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                // 中文文件名按 RFC 6266 的 filename* 编码，浏览器下载时还原成"佩戴情况_…xlsx"
                .andExpect(header().string("Content-Disposition", containsString(
                        "filename*=UTF-8''%E4%BD%A9%E6%88%B4%E6%83%85%E5%86%B5_20260923100000.xlsx")))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void wearRejectsFutureDate() throws Exception {
        when(export.today()).thenReturn(LocalDate.of(2026, 9, 23));
        mvc.perform(get("/api/export/wear?date=2026-09-24").header("satoken", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能导出还没到的日期"));
    }
}
