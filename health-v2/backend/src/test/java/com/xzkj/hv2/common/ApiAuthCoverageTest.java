package com.xzkj.hv2.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import cn.dev33.satoken.spring.SaBeanInject;
import cn.dev33.satoken.spring.SaBeanRegister;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import com.xzkj.hv2.archive.ArchiveService;
import com.xzkj.hv2.archive.WatchListService;
import com.xzkj.hv2.auth.AuthService;
import com.xzkj.hv2.common.config.JacksonConfig;
import com.xzkj.hv2.common.config.SaTokenConfig;
import com.xzkj.hv2.common.exception.GlobalExceptionHandler;
import com.xzkj.hv2.dashboard.DashboardService;
import com.xzkj.hv2.map.MapService;
import com.xzkj.hv2.map.StationMarkService;
import com.xzkj.hv2.positioning.PositioningStatusService;
import com.xzkj.hv2.report.MonthlyReportService;
import com.xzkj.hv2.report.WearExportService;

/**
 * 接口鉴权覆盖（docs/01 第五节"安全"、docs/08 阶段 7 验收 A6）：
 * 加载全部 controller，逐个接口不带令牌请求，除登录和登录页提示外都必须是 401。
 * 以后新增接口不用改这个测试，它会自动被列进来。
 */
@WebMvcTest
@ImportAutoConfiguration({SaTokenContextRegister.class, SaBeanRegister.class, SaBeanInject.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
class ApiAuthCoverageTest {

    private static final Set<String> PUBLIC = Set.of("GET /api/auth/login-hint", "POST /api/auth/login");

    @Autowired
    private MockMvc mvc;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private ArchiveService archiveService;
    @MockitoBean
    private WatchListService watchListService;
    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private PositioningStatusService positioningStatusService;
    @MockitoBean
    private MapService mapService;
    @MockitoBean
    private StationMarkService stationMarkService;
    @MockitoBean
    private MonthlyReportService monthlyReportService;
    @MockitoBean
    private WearExportService wearExportService;
    @MockitoBean
    private Clock clock;

    @Test
    void everyEndpointExceptLoginNeedsToken() throws Exception {
        List<String> endpoints = new ArrayList<>();
        mappings.getHandlerMethods().forEach((info, method) -> {
            for (String pattern : info.getPatternValues()) {
                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                    endpoints.add(m.name() + " " + pattern);
                }
            }
        });
        // 至少要把现有的接口都列出来，防止测试因为没加载到 controller 而"空跑通过"
        assertThat(endpoints).hasSizeGreaterThanOrEqualTo(30).containsAll(PUBLIC);

        List<String> unprotected = new ArrayList<>();
        for (String endpoint : endpoints) {
            String[] parts = endpoint.split(" ", 2);
            // 所有接口都要在 /api/ 下，拦截器只拦 /api/**
            assertThat(parts[1]).as(endpoint).startsWith("/api/");
            if (PUBLIC.contains(endpoint)) {
                continue;
            }
            String path = parts[1].replaceAll("\\{[^}]+}", "1");
            int code = mvc.perform(request(HttpMethod.valueOf(parts[0]), path)).andReturn().getResponse().getStatus();
            if (code != 401) {
                unprotected.add(endpoint + " → " + code);
            }
        }
        assertThat(unprotected).as("不带令牌也能访问的接口").isEmpty();
    }
}
