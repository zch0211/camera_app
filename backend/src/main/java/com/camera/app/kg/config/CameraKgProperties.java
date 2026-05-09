package com.camera.app.kg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "kg.camera")
public class CameraKgProperties {

    private String uri = "neo4j://127.0.0.1:7687";
    private String database = "neo4j";
    private String username = "neo4j";
    private String password;
}
