package com.camera.app.kg.controller;

import com.camera.app.common.response.ApiResponse;
import com.camera.app.kg.dto.EnrichResponse;
import com.camera.app.kg.dto.GraphResponse;
import com.camera.app.kg.dto.VulnHintsResponse;
import com.camera.app.kg.service.CameraKgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "知识图谱增强 (KG)",
        description = """
                基于 Neo4j 摄像头知识图谱的只读增强接口。
                所有结果均为图谱推理/路径匹配，不写入 MySQL，不执行真实扫描。
                权限：ROLE_ADMIN / ROLE_OPERATOR 可访问；ROLE_VIEWER 无权限。
                """
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/kg/assets")
@RequiredArgsConstructor
public class KgController {

    private final CameraKgService cameraKgService;

    @Operation(
            summary = "资产信息补全建议",
            description = """
                    根据 MySQL 资产的 brand / model 字段在 Neo4j 图谱中进行匹配，
                    返回图谱中找到的厂商、产品、固件、端口等补全建议及推断依据路径。
                    本接口**不会**将任何结果回写 MySQL，仅返回建议。
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功（matched=false 表示图谱未命中，非错误）",
                    content = @Content(schema = @Schema(implementation = EnrichResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（VIEWER 无权限）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "资产 ID 不存在")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}/enrich")
    public ApiResponse<EnrichResponse> enrich(
            @Parameter(description = "MySQL 资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(cameraKgService.enrich(id));
    }

    @Operation(
            summary = "漏洞潜在风险提示（图谱推理）",
            description = """
                    根据 MySQL 资产的 brand / model 字段在 Neo4j 图谱中查找可能关联的漏洞节点（最多 3 跳路径）。
                    **重要说明**：本接口结果仅为"图谱提示"，表示"图谱中存在可能关联的漏洞记录"，
                    不代表该资产已确认受影响，不等同于真实漏洞扫描结果。
                    置信度说明：HIGH=直接相邻（1跳）/ MEDIUM=2跳 / LOW=3跳。
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功（matched=false + 空列表表示图谱未命中，非错误）",
                    content = @Content(schema = @Schema(implementation = VulnHintsResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "资产 ID 不存在")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}/vuln-hints")
    public ApiResponse<VulnHintsResponse> vulnHints(
            @Parameter(description = "MySQL 资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(cameraKgService.vulnHints(id));
    }

    @Operation(
            summary = "资产关联子图（用于图谱可视化）",
            description = """
                    返回以该资产对应产品节点为中心的一跳子图（节点 + 边），规模受控（最多 50 条边）。
                    前端可使用 D3.js / ECharts / vis.js 等图谱库直接消费 nodes + edges 数据结构。
                    当图谱未命中时返回空 nodes/edges，不报错。
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = GraphResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "资产 ID 不存在")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}/graph")
    public ApiResponse<GraphResponse> graph(
            @Parameter(description = "MySQL 资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(cameraKgService.graph(id));
    }
}
