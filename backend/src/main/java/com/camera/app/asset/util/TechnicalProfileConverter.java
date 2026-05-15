package com.camera.app.asset.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
public final class TechnicalProfileConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TechnicalProfileConverter() {}

    public static String portsToJson(List<Integer> ports) {
        if (ports == null || ports.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(ports);
        } catch (Exception e) {
            log.warn("端口列表序列化失败", e);
            return null;
        }
    }

    public static String protocolsToJson(List<String> protocols) {
        if (protocols == null || protocols.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(protocols);
        } catch (Exception e) {
            log.warn("协议列表序列化失败", e);
            return null;
        }
    }

    public static List<Integer> parsePorts(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            log.warn("端口列表反序列化失败，原始值: {}", json);
            return Collections.emptyList();
        }
    }

    public static List<String> parseProtocols(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("协议列表反序列化失败，原始值: {}", json);
            return Collections.emptyList();
        }
    }
}
