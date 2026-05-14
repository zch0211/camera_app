package com.camera.app.dashboard;

import com.camera.app.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "首页概览", description = "首页数据概览接口，汇总各模块统计数据")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "首页概览数据",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。返回用户数、资产数（含在线数）、"
                    + "POC 总数/启用数、近 24h 执行次数及系统健康状态"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview() {
        return ApiResponse.ok(dashboardService.overview());
    }
}
