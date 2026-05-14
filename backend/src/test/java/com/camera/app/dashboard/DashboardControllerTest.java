package com.camera.app.dashboard;

import com.camera.app.security.JwtTokenProvider;
import com.camera.app.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DashboardService dashboardService;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsOverview() throws Exception {
        DashboardOverviewResponse resp = DashboardOverviewResponse.builder()
                .userCount(5).assetCount(10).onlineAssetCount(3)
                .pocCount(8).enabledPocCount(6).recentExecutionCount(2)
                .systemHealthy(true).build();
        when(dashboardService.overview()).thenReturn(resp);

        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userCount").value(5))
                .andExpect(jsonPath("$.data.assetCount").value(10))
                .andExpect(jsonPath("$.data.onlineAssetCount").value(3))
                .andExpect(jsonPath("$.data.pocCount").value(8))
                .andExpect(jsonPath("$.data.enabledPocCount").value(6))
                .andExpect(jsonPath("$.data.recentExecutionCount").value(2))
                .andExpect(jsonPath("$.data.systemHealthy").value(true));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorGetsOverview() throws Exception {
        when(dashboardService.overview()).thenReturn(DashboardOverviewResponse.builder()
                .userCount(1).assetCount(0).onlineAssetCount(0)
                .pocCount(0).enabledPocCount(0).recentExecutionCount(0)
                .systemHealthy(true).build());

        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemHealthy").value(true));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }
}
