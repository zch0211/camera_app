package com.camera.app.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    /** Base64-encoded HMAC secret, must decode to >= 32 bytes */
    private String secret;
    /** Token validity in milliseconds (default 24 h) */
    private long expirationMs = 86_400_000L;
}
