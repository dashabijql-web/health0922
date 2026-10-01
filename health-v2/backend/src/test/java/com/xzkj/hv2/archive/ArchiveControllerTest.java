package com.xzkj.hv2.archive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cn.dev33.satoken.spring.SaBeanInject;
import cn.dev33.satoken.spring.SaBeanRegister;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.config.JacksonConfig;
import com.xzkj.hv2.common.config.SaTokenConfig;
import com.xzkj.hv2.common.exception.GlobalExceptionHandler;

/** 接口层：要登录、参数校验、返回格式（docs/05 第六～八节）。 */
@WebMvcTest(controllers = {ArchiveController.class, WatchListController.class})
@ImportAutoConfiguration({SaTokenContextRegister.class, SaBeanRegister.class, SaBeanInject.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
class ArchiveControllerTest {

    private static final String CARD = "62082300920390001";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ArchiveService service;
    @MockitoBean
    private WatchListService lists;

    private String token;

    @BeforeEach
    void login() {
        token = StpUtil.createLoginSession(1L);
    }

    @Test
    void everyEndpointNeedsLogin() throws Exception {
        for (String url : List.of("/api/archive/filters", "/api/archive/persons", "/api/archive/persons/" + CARD,
                "/api/archive/persons/" + CARD + "/trend?metric=SPO2", "/api/archive/persons/" + CARD + "/steps",
                "/api/archive/persons/" + CARD + "/alerts", "/api/watch-list?type=KEY")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        mvc.perform(put("/api/archive/persons/" + CARD + "/age").contentType(MediaType.APPLICATION_JSON)
                .content("{\"age\":45}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/watch-list").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardCode\":\"" + CARD + "\",\"type\":\"KEY\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/watch-list/1")).andExpect(status().isUnauthorized());
        verify(service, never()).setAge(anyString(), anyInt());
        verify(lists, never()).add(any(), any(), any());
    }

    @Test
    void personsPassFiltersAndPage() throws Exception {
        when(service.persons(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(13, 2, 12, List.of(
                        new ArchiveViews.ArchivePerson(CARD, "张三", "采煤工", "综采一队", null))));
        mvc.perform(get("/api/archive/persons?dept=综采一队&jobKind=采煤工&keyword=张&page=2").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(13))
                .andExpect(jsonPath("$.data.list[0].age").doesNotExist());
        verify(service).persons("综采一队", "采煤工", "张", 2, 12);
        mvc.perform(get("/api/archive/persons?size=51").header("satoken", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/archive/persons?page=0").header("satoken", token)).andExpect(status().isBadRequest());
    }

    @Test
    void trendChecksMetricAndDate() throws Exception {
        when(service.trend(any(), any(), any())).thenReturn(
                new ArchiveViews.Trend("HEART_RATE", LocalDate.of(2026, 9, 24), 12, List.of(
                        new ArchiveViews.TrendPoint(LocalDateTime.of(2026, 9, 24, 8, 0), new java.math.BigDecimal("72"),
                                null))));
        String base = "/api/archive/persons/" + CARD + "/trend";
        mvc.perform(get(base + "?metric=HEART_RATE&date=2026-09-24").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].time").value("2026-09-24 08:00:00"))
                .andExpect(jsonPath("$.data.date").value("2026-09-24"));
        verify(service).trend(CARD, "HEART_RATE", LocalDate.of(2026, 9, 24));
        mvc.perform(get(base + "?metric=SPO2").header("satoken", token)).andExpect(status().isOk());
        verify(service).trend(CARD, "SPO2", null);
        mvc.perform(get(base + "?metric=STEPS").header("satoken", token)).andExpect(status().isBadRequest());
        mvc.perform(get(base).header("satoken", token)).andExpect(status().isBadRequest());
        mvc.perform(get(base + "?metric=SPO2&date=2026-13-01").header("satoken", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badParamsAre400() throws Exception {
        mvc.perform(get("/api/archive/persons/123").header("satoken", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/archive/persons/" + CARD + "/steps?days=32").header("satoken", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/archive/persons/" + CARD + "/alerts?size=101").header("satoken", token))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/archive/persons/" + CARD + "/age").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/archive/persons/" + CARD + "/age").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"age\":\"abc\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/watch-list?type=ALL").header("satoken", token)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/watch-list").header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardCode\":\"" + CARD + "\",\"type\":\"VIP\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/watch-list").header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardCode\":\"12\",\"type\":\"KEY\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/watch-list/0").header("satoken", token)).andExpect(status().isBadRequest());
        verify(lists, never()).add(any(), any(), any());
    }

    @Test
    void ageAndWatchListPassThrough() throws Exception {
        when(service.setAge(CARD, 45)).thenReturn(new ArchiveViews.AgeResult(45, "admin",
                LocalDateTime.of(2026, 9, 24, 10, 0)));
        mvc.perform(put("/api/archive/persons/" + CARD + "/age").header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"age\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.age").value(45))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-09-24 10:00:00"));

        mvc.perform(post("/api/watch-list").header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardCode\":\"" + CARD + "\",\"type\":\"TODAY\"}")).andExpect(status().isOk());
        verify(lists).add(CARD, "TODAY", null);
        mvc.perform(get("/api/watch-list?type=KEY").header("satoken", token)).andExpect(status().isOk());
        verify(lists).list("KEY");
        mvc.perform(delete("/api/watch-list/7").header("satoken", token)).andExpect(status().isOk());
        verify(lists).remove(7L);
        verify(service, never()).trend(any(), any(), isNull());
    }
}
