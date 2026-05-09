package com.camera.app.kg.client;

import com.camera.app.kg.config.CameraKgProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 封装 Neo4j 只读访问。
 * 所有查询通过 executeRead 执行，禁止任何写入操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CameraKgClient {

    private final Driver driver;
    private final CameraKgProperties properties;

    /**
     * 执行只读 Cypher 查询，返回物化后的记录列表。
     *
     * @param cypher Cypher 查询语句
     * @param params 参数 Map
     * @return 记录列表，查询无结果时返回空列表
     */
    public List<Record> readQuery(String cypher, Map<String, Object> params) {
        try (Session session = driver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            return session.executeRead(tx -> tx.run(cypher, params).list());
        } catch (Exception e) {
            log.warn("Neo4j 只读查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Record> readQuery(String cypher) {
        return readQuery(cypher, Map.of());
    }
}
