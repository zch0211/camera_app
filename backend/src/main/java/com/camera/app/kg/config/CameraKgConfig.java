package com.camera.app.kg.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CameraKgConfig {

    /**
     * 创建 Neo4j Driver Bean。
     * 仅当 kg.camera.uri 已配置时才激活，避免未配置环境启动失败。
     */
    @Bean
    @ConditionalOnProperty(prefix = "kg.camera", name = "uri")
    public Driver neo4jDriver(CameraKgProperties props) {
        Config config = Config.builder()
                .withConnectionTimeout(5, TimeUnit.SECONDS)
                .withMaxConnectionLifetime(30, TimeUnit.MINUTES)
                .withConnectionAcquisitionTimeout(10, TimeUnit.SECONDS)
                .build();
        return GraphDatabase.driver(
                props.getUri(),
                AuthTokens.basic(props.getUsername(), props.getPassword()),
                config
        );
    }
}
