package com.xzkj.hv2.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cn.dev33.satoken.spring.SaBeanInject;
import cn.dev33.satoken.spring.SaBeanRegister;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.hv2.common.config.JacksonConfig;
import com.xzkj.hv2.common.config.SaTokenConfig;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.common.exception.GlobalExceptionHandler;
import com.xzkj.hv2.report.MonthlyReportViews.Months;
import com.xzkj.hv2.report.MonthlyReportViews.MonthlyReport;

/** 接口层：要登录、月份格式、没有数据的块是 null（docs/07 第二部分"四、接口"）。 */
@WebMvcTest(controllers = MonthlyReportController.class)
@ImportAutoConfiguration({SaTokenContextRegister.class, SaBeanRegister.class, SaBeanInject.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
class MonthlyReportControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private MonthlyReportService service;

    private String token;

    @BeforeEach
    void login() {
        token = StpUtil.createLoginSession(1L);
    }

    @Test
    void needsLogin() throws Exception {
        mvc.perform(get("/api/report/months")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/report/monthly?month=2026-08")).andExpect(status().isUnauthorized());
        verify(service, never()).monthly(any());
    }

    @Test
    void monthsAndDefault() throws Exception {
        when(service.months()).thenReturn(new Months(List.of("2026-08", "2026-09"), "2026-08"));
        mvc.perform(get("/api/report/months").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.months[1]").value("2026-09"))
                .andExpect(jsonPath("$.data.defaultMonth").value("2026-08"));
    }

    @Test
    void monthMustBeYyyyMm() throws Exception {
        for (String bad : List.of("2026-8", "2026-13", "2026-00", "202608", "2026-08-01", "abc")) {
            mvc.perform(get("/api/report/monthly").param("month", bad).header("satoken", token))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/report/monthly").header("satoken", token)).andExpect(status().isBadRequest());
        verify(service, never()).monthly(any());
    }

    @Test
    void futureMonthIs400() throws Exception {
        when(service.monthly(YearMonth.of(2026, 10))).thenThrow(BizException.badRequest("不能查看还没到的月份"));
        mvc.perform(get("/api/report/monthly?month=2026-10").header("satoken", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能查看还没到的月份"));
    }

    @Test
    void emptySectionsAreNull() throws Exception {
        when(service.monthly(YearMonth.of(2026, 7))).thenReturn(new MonthlyReport("2026-07",
                LocalDateTime.of(2026, 9, 23, 10, 0), null, null, null, null, List.of(), null, null, null));
        mvc.perform(get("/api/report/monthly?month=2026-07").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-07"))
                .andExpect(jsonPath("$.data.generatedAt").value("2026-09-23 10:00:00"))
                .andExpect(jsonPath("$.data.overview").isEmpty())
                .andExpect(jsonPath("$.data.stability").isEmpty())
                .andExpect(jsonPath("$.data.risk").isEmpty());
    }
}
