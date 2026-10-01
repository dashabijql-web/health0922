package com.xzkj.hv2.map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/** 接口层：要登录、参数校验、返回格式（docs/05 第五节、docs/06 第五节）。 */
@WebMvcTest(controllers = MapController.class)
@ImportAutoConfiguration({SaTokenContextRegister.class, SaBeanRegister.class, SaBeanInject.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
class MapControllerTest {

    private static final String ST = "6208230092030001000012";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private MapService service;
    @MockitoBean
    private StationMarkService marks;

    private String token;

    @BeforeEach
    void login() {
        token = StpUtil.createLoginSession(1L);
    }

    @Test
    void everyEndpointNeedsLogin() throws Exception {
        for (String url : List.of("/api/map/config", "/api/map/persons", "/api/map/persons/62082300920390001/card",
                "/api/map/stations", "/api/map/station-logs")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        mvc.perform(put("/api/map/stations/" + ST + "/mark").contentType(MediaType.APPLICATION_JSON)
                .content("{\"x\":1,\"y\":1,\"version\":0}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/map/stations/" + ST + "/mark?version=1")).andExpect(status().isUnauthorized());
        verify(marks, never()).save(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void configSaysWms111AndExtent() throws Exception {
        when(service.config()).thenReturn(new MapViews.MapConfig("/geoserver/hv2/wms", "1.1.1", "hv2:roadway_line",
                "hv2:roadway_label", "EPSG:4527", List.of(new BigDecimal("39480087.9"), new BigDecimal("3851797.4"),
                new BigDecimal("39489391.2"), new BigDecimal("3854743.4"))));
        mvc.perform(get("/api/map/config").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wmsVersion").value("1.1.1"))
                .andExpect(jsonPath("$.data.projection").value("EPSG:4527"))
                .andExpect(jsonPath("$.data.extent[0]").value(39480087.9));
    }

    @Test
    void personsFilterMapsToListType() throws Exception {
        when(service.persons(any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/map/persons?filter=key").header("satoken", token)).andExpect(status().isOk());
        verify(service).persons("KEY", null);
        mvc.perform(get("/api/map/persons?filter=today&keyword=张").header("satoken", token)).andExpect(status().isOk());
        verify(service).persons("TODAY", "张");
        mvc.perform(get("/api/map/persons").header("satoken", token)).andExpect(status().isOk());
        verify(service).persons(isNull(), isNull());
        mvc.perform(get("/api/map/persons?filter=x").header("satoken", token)).andExpect(status().isBadRequest());
    }

    @Test
    void badParamsAre400() throws Exception {
        mvc.perform(get("/api/map/persons/123/card").header("satoken", token)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/map/stations/" + ST + "/mark").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"y\":1,\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/map/stations/" + ST + "/mark").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"x\":1,\"y\":1,\"version\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/map/stations/a-b/mark").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"x\":1,\"y\":1,\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/map/stations/" + ST + "/mark").header("satoken", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/map/station-logs?size=101").header("satoken", token)).andExpect(status().isBadRequest());
    }

    @Test
    void saveAndConflict() throws Exception {
        when(marks.save(eq(ST), any(), any(), any(), eq(0L)))
                .thenThrow(BizException.conflict("这个基站刚刚被 zhang 修改，请刷新"));
        mvc.perform(put("/api/map/stations/" + ST + "/mark").header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":39482000.5,\"y\":3852000.5,\"displayName\":\"新副井下口\",\"version\":0}"))
                .andExpect(status().is(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("这个基站刚刚被 zhang 修改，请刷新"));
        verify(marks).save(ST, new BigDecimal("39482000.5"), new BigDecimal("3852000.5"), "新副井下口", 0L);

        mvc.perform(delete("/api/map/stations/" + ST + "/mark?version=3").header("satoken", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(marks).delete(ST, 3L);
    }
}
