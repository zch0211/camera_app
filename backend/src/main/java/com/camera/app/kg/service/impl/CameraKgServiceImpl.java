package com.camera.app.kg.service.impl;

import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.kg.client.CameraKgClient;
import com.camera.app.kg.dto.*;
import com.camera.app.kg.service.CameraKgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraKgServiceImpl implements CameraKgService {

    // ──────────────────────────────────────────────────────────────────────────
    // Cypher 模板（只读）
    // ──────────────────────────────────────────────────────────────────────────

    /** 按型号查产品及周边节点 */
    private static final String ENRICH_BY_MODEL = """
            MATCH (p:产品)
            WHERE any(k IN keys(p) WHERE toLower(toString(p[k])) CONTAINS toLower($model))
            WITH p LIMIT 3
            OPTIONAL MATCH (v:厂商)-[*1..2]->(p)
            OPTIONAL MATCH (p)-[:运行]->(f:固件)
            OPTIONAL MATCH (p)-[:开放]->(port:端口)
            WITH p,
                 collect(DISTINCT v) AS vendors,
                 collect(DISTINCT f) AS firmwares,
                 collect(DISTINCT port) AS ports
            RETURN p, vendors, firmwares, ports
            """;

    /** 按品牌查厂商及其产品（model 缺失时的降级路径） */
    private static final String ENRICH_BY_BRAND = """
            MATCH (v:厂商)
            WHERE any(k IN keys(v) WHERE toLower(toString(v[k])) CONTAINS toLower($brand))
            WITH v LIMIT 3
            OPTIONAL MATCH (v)-[*1..2]->(p:产品)
            RETURN v, collect(DISTINCT p) AS products
            """;

    /** 查与产品可能关联的漏洞（最多 3 跳） */
    private static final String VULN_HINTS = """
            MATCH (p:产品)
            WHERE any(k IN keys(p) WHERE toLower(toString(p[k])) CONTAINS toLower($model))
            WITH p LIMIT 1
            OPTIONAL MATCH path = (p)-[*1..3]-(vuln:漏洞)
            RETURN p,
                   vuln,
                   [n IN nodes(path) |
                       coalesce(head(labels(n)), '节点') + ':' +
                       coalesce(toString(n.name), toString(n.`名称`), toString(n.`cve_id`), elementId(n))
                   ] AS pathNodes,
                   CASE WHEN path IS NOT NULL THEN length(path) ELSE 0 END AS hops
            ORDER BY hops ASC
            LIMIT 20
            """;

    /** 查一跳子图（用于前端图谱可视化） */
    private static final String GRAPH_1HOP = """
            MATCH (p:产品)
            WHERE any(k IN keys(p) WHERE toLower(toString(p[k])) CONTAINS toLower($model))
            WITH p LIMIT 1
            MATCH (p)-[r]-(neighbor)
            RETURN p, r, neighbor
            LIMIT 50
            """;

    private final CameraKgClient kgClient;
    private final AssetRepository assetRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // 公开接口实现
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public EnrichResponse enrich(Long assetId) {
        Asset asset = requireAsset(assetId);
        String model = asset.getModel();
        String brand = asset.getBrand();

        if (hasText(model)) {
            return enrichByModel(asset);
        } else if (hasText(brand)) {
            return enrichByBrand(asset);
        } else {
            return EnrichResponse.builder()
                    .assetId(assetId)
                    .matched(false)
                    .confidence("LOW")
                    .relatedNodesSummary(List.of("资产缺少品牌和型号信息，无法进行图谱匹配"))
                    .evidencePaths(List.of())
                    .build();
        }
    }

    @Override
    public VulnHintsResponse vulnHints(Long assetId) {
        Asset asset = requireAsset(assetId);
        String model = asset.getModel();

        if (!hasText(model)) {
            return VulnHintsResponse.builder()
                    .assetId(assetId)
                    .matched(false)
                    .vulnerabilityHints(List.of())
                    .summary("资产缺少型号信息，无法进行漏洞图谱匹配")
                    .build();
        }

        List<Record> records = kgClient.readQuery(VULN_HINTS, Map.of("model", model));

        if (records.isEmpty()) {
            return VulnHintsResponse.builder()
                    .assetId(assetId)
                    .matched(false)
                    .vulnerabilityHints(List.of())
                    .summary("图谱中未找到型号 [" + model + "] 对应的产品节点")
                    .build();
        }

        // 第一条记录用来判断产品是否命中（vuln 可能为 null）
        boolean productMatched = !records.get(0).get("p").isNull();

        List<VulnHintItem> hints = records.stream()
                .filter(r -> !r.get("vuln").isNull())
                .map(r -> buildVulnHintItem(r, model))
                .collect(Collectors.toList());

        long highCount = hints.stream().filter(h -> "HIGH".equals(h.getConfidence())).count();
        String summary = productMatched
                ? String.format("在图谱中找到型号 [%s] 对应产品，发现 %d 条潜在漏洞关联提示（%d 条高置信度）。" +
                "本结果仅为图谱推理，不代表资产已确认受影响。", model, hints.size(), highCount)
                : "图谱中未找到匹配产品节点";

        return VulnHintsResponse.builder()
                .assetId(assetId)
                .matched(productMatched)
                .vulnerabilityHints(hints)
                .summary(summary)
                .build();
    }

    @Override
    public GraphResponse graph(Long assetId) {
        Asset asset = requireAsset(assetId);
        String model = asset.getModel();

        if (!hasText(model)) {
            return GraphResponse.builder()
                    .nodes(List.of())
                    .edges(List.of())
                    .build();
        }

        List<Record> records = kgClient.readQuery(GRAPH_1HOP, Map.of("model", model));

        if (records.isEmpty()) {
            return GraphResponse.builder()
                    .nodes(List.of())
                    .edges(List.of())
                    .build();
        }

        // 用 LinkedHashMap 保持插入顺序并去重
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (Record r : records) {
            Node center = safeNode(r, "p");
            Relationship rel = safeRelationship(r, "r");
            Node neighbor = safeNode(r, "neighbor");

            if (center == null || rel == null || neighbor == null) continue;

            nodeMap.putIfAbsent(center.elementId(), toGraphNode(center));
            nodeMap.putIfAbsent(neighbor.elementId(), toGraphNode(neighbor));

            edges.add(GraphEdge.builder()
                    .source(rel.startNodeElementId())
                    .target(rel.endNodeElementId())
                    .type(rel.type())
                    .build());
        }

        return GraphResponse.builder()
                .nodes(new ArrayList<>(nodeMap.values()))
                .edges(edges)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 私有辅助方法
    // ──────────────────────────────────────────────────────────────────────────

    private Asset requireAsset(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + id));
    }

    private EnrichResponse enrichByModel(Asset asset) {
        List<Record> records = kgClient.readQuery(ENRICH_BY_MODEL, Map.of("model", asset.getModel()));

        if (records.isEmpty()) {
            return EnrichResponse.builder()
                    .assetId(asset.getId())
                    .matched(false)
                    .confidence("LOW")
                    .relatedNodesSummary(List.of("图谱中未找到型号 [" + asset.getModel() + "] 对应的产品节点"))
                    .evidencePaths(List.of())
                    .build();
        }

        Record first = records.get(0);
        Node product = safeNode(first, "p");
        if (product == null) {
            return EnrichResponse.builder()
                    .assetId(asset.getId())
                    .matched(false)
                    .confidence("LOW")
                    .relatedNodesSummary(List.of())
                    .evidencePaths(List.of())
                    .build();
        }

        List<Node> vendors = first.get("vendors").asList(v -> v.asNode());
        List<Node> firmwares = first.get("firmwares").asList(v -> v.asNode());
        List<Node> ports = first.get("ports").asList(v -> v.asNode());

        String inferredProduct = getNodeName(product);
        List<String> evidence = new ArrayList<>();
        evidence.add("产品节点命中: " + inferredProduct);

        // 厂商
        String inferredBrand = null;
        boolean brandMatched = false;
        if (!vendors.isEmpty()) {
            inferredBrand = getNodeName(vendors.get(0));
            evidence.add("关联厂商: " + inferredBrand);
            if (hasText(asset.getBrand()) &&
                    inferredBrand.toLowerCase().contains(asset.getBrand().toLowerCase())) {
                brandMatched = true;
                evidence.add("图谱厂商与资产 brand 字段吻合 ✓");
            }
        }

        // 固件
        String inferredFirmware = null;
        if (!firmwares.isEmpty()) {
            inferredFirmware = getNodeName(firmwares.get(0));
            evidence.add("关联固件: " + inferredFirmware);
        }

        // 端口
        List<String> inferredPorts = ports.stream()
                .map(this::getNodeName)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        if (!inferredPorts.isEmpty()) {
            evidence.add("开放端口: " + String.join(", ", inferredPorts));
        }

        // 置信度：品牌与型号均命中 → HIGH；仅型号命中 → MEDIUM
        String confidence = brandMatched ? "HIGH" : "MEDIUM";

        List<String> summary = new ArrayList<>();
        summary.add("产品: " + inferredProduct);
        if (inferredBrand != null) summary.add("厂商: " + inferredBrand);
        if (inferredFirmware != null) summary.add("固件: " + inferredFirmware);
        if (!inferredPorts.isEmpty()) summary.add("端口: " + String.join(", ", inferredPorts));

        return EnrichResponse.builder()
                .assetId(asset.getId())
                .matched(true)
                .inferredBrand(inferredBrand)
                .inferredProduct(inferredProduct)
                .inferredFirmware(inferredFirmware)
                .inferredPorts(inferredPorts.isEmpty() ? null : inferredPorts)
                .relatedNodesSummary(summary)
                .evidencePaths(evidence)
                .confidence(confidence)
                .build();
    }

    private EnrichResponse enrichByBrand(Asset asset) {
        List<Record> records = kgClient.readQuery(ENRICH_BY_BRAND, Map.of("brand", asset.getBrand()));

        if (records.isEmpty()) {
            return EnrichResponse.builder()
                    .assetId(asset.getId())
                    .matched(false)
                    .confidence("LOW")
                    .relatedNodesSummary(List.of("图谱中未找到品牌 [" + asset.getBrand() + "] 对应的厂商节点"))
                    .evidencePaths(List.of())
                    .build();
        }

        Record first = records.get(0);
        Node vendor = safeNode(first, "v");
        if (vendor == null) {
            return EnrichResponse.builder()
                    .assetId(asset.getId())
                    .matched(false)
                    .confidence("LOW")
                    .relatedNodesSummary(List.of())
                    .evidencePaths(List.of())
                    .build();
        }

        String inferredBrand = getNodeName(vendor);
        List<Node> products = first.get("products").asList(v -> v.asNode());

        List<String> evidence = new ArrayList<>();
        evidence.add("厂商节点命中: " + inferredBrand + "（按品牌模糊匹配，无型号过滤）");

        List<String> productNames = products.stream()
                .map(this::getNodeName)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        if (!productNames.isEmpty()) {
            evidence.add("该厂商关联产品: " + String.join(", ", productNames));
        }

        List<String> summary = new ArrayList<>();
        summary.add("厂商: " + inferredBrand);
        if (!productNames.isEmpty()) {
            summary.add("关联产品: " + String.join(", ", productNames));
        }

        return EnrichResponse.builder()
                .assetId(asset.getId())
                .matched(true)
                .inferredBrand(inferredBrand)
                .relatedNodesSummary(summary)
                .evidencePaths(evidence)
                .confidence("LOW") // 仅品牌命中，无型号精确匹配
                .build();
    }

    private VulnHintItem buildVulnHintItem(Record r, String model) {
        Node vuln = r.get("vuln").asNode();
        List<String> pathNodes = r.get("pathNodes").asList(v -> v.asString());
        int hops = r.get("hops").asInt(0);

        String vulnName = getNodeName(vuln);
        String severity = getFirstProp(vuln, "severity", "等级", "cvss", "level");

        String confidence = switch (hops) {
            case 1 -> "HIGH";
            case 2 -> "MEDIUM";
            default -> "LOW";
        };

        String reason = String.format("图谱中产品 [%s] 通过 %d 跳关系与漏洞 [%s] 可能关联",
                model, hops, vulnName);

        String evidencePath = pathNodes.isEmpty() ? "" : String.join(" → ", pathNodes);

        return VulnHintItem.builder()
                .vulnName(vulnName)
                .severity(severity)
                .reason(reason)
                .evidencePath(evidencePath)
                .confidence(confidence)
                .build();
    }

    private GraphNode toGraphNode(Node n) {
        String label = n.labels().iterator().hasNext() ? n.labels().iterator().next() : "未知";
        return GraphNode.builder()
                .id(n.elementId())
                .label(getNodeName(n))
                .type(label)
                .properties(n.asMap())
                .build();
    }

    /**
     * 按优先级依次尝试常见的名称属性，兜底返回 "标签#elementId" 格式。
     */
    private String getNodeName(Node node) {
        for (String key : List.of("name", "名称", "型号", "版本", "版本号", "cve_id", "title", "id")) {
            Value v = node.get(key);
            if (!v.isNull()) {
                Object obj = v.asObject();
                if (obj != null) return obj.toString();
            }
        }
        // 兜底：遍历全部属性取第一个非空字符串
        for (String key : node.keys()) {
            Value v = node.get(key);
            if (!v.isNull()) {
                try {
                    String s = v.asString();
                    if (!s.isBlank()) return s;
                } catch (Exception ignored) {
                    // 非字符串类型，继续
                }
            }
        }
        String label = node.labels().iterator().hasNext() ? node.labels().iterator().next() : "节点";
        return label + "#" + node.elementId();
    }

    /**
     * 按优先级依次尝试多个属性名，返回第一个非空值的字符串表示，没有则返回 null。
     */
    private String getFirstProp(Node node, String... keys) {
        for (String key : keys) {
            Value v = node.get(key);
            if (!v.isNull()) {
                Object obj = v.asObject();
                if (obj != null) return obj.toString();
            }
        }
        return null;
    }

    private Node safeNode(Record r, String key) {
        Value v = r.get(key);
        return v.isNull() ? null : v.asNode();
    }

    private Relationship safeRelationship(Record r, String key) {
        Value v = r.get(key);
        return v.isNull() ? null : v.asRelationship();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
