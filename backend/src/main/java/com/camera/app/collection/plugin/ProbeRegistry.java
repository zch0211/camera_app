package com.camera.app.collection.plugin;

import com.camera.app.collection.entity.TaskPreset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProbeRegistry {

    private final List<ProbePlugin> allPlugins;

    /** Preset → canonical plugin name list (order determines execution sequence). */
    private static final Map<TaskPreset, List<String>> PRESET_PLUGINS = Map.of(
            TaskPreset.CAMERA_PRESET, List.of(
                    "port-probe", "http-fingerprint", "rtsp-probe",
                    "onvif-probe", "web-fingerprint"),
            TaskPreset.NVR_PRESET, List.of(
                    "port-probe", "http-fingerprint", "rtsp-probe",
                    "onvif-probe", "ssh-banner", "telnet-banner", "web-fingerprint"),
            TaskPreset.ROUTER_PRESET, List.of(
                    "port-probe", "http-fingerprint", "snmp-probe",
                    "ssh-banner", "telnet-banner", "web-fingerprint"),
            TaskPreset.FULL_PRESET, List.of(
                    "port-probe", "http-fingerprint", "rtsp-probe", "onvif-probe",
                    "web-fingerprint", "snmp-probe", "ssh-banner", "telnet-banner", "upnp-probe"),
            TaskPreset.CUSTOM, List.of()
    );

    /** Default ports per preset. */
    public static final Map<TaskPreset, List<Integer>> PRESET_PORTS = Map.of(
            TaskPreset.CAMERA_PRESET, List.of(80, 443, 554, 8000, 8080, 8443, 8554, 8888),
            TaskPreset.NVR_PRESET,    List.of(80, 443, 554, 8000, 8080, 8443, 22, 23, 8554, 37777, 34568),
            TaskPreset.ROUTER_PRESET, List.of(80, 443, 8080, 22, 23),
            TaskPreset.FULL_PRESET,   List.of(80, 443, 554, 8000, 8080, 8443, 22, 23, 8554, 8888, 37777, 34568),
            TaskPreset.CUSTOM,        List.of(80, 443, 554, 8000, 8080, 8443)
    );

    public List<ProbePlugin> resolvePlugins(TaskPreset preset, List<String> customPlugins) {
        List<String> names;
        if (preset == TaskPreset.CUSTOM && customPlugins != null && !customPlugins.isEmpty()) {
            names = customPlugins;
        } else {
            names = PRESET_PLUGINS.getOrDefault(preset, List.of());
        }

        Set<String> nameSet = Set.copyOf(names);
        Map<String, ProbePlugin> byName = allPlugins.stream()
                .collect(Collectors.toMap(ProbePlugin::getName, p -> p));

        return names.stream()
                .filter(byName::containsKey)
                .map(byName::get)
                .collect(Collectors.toList());
    }

    public List<Integer> resolveDefaultPorts(TaskPreset preset) {
        return PRESET_PORTS.getOrDefault(preset, PRESET_PORTS.get(TaskPreset.CUSTOM));
    }
}
