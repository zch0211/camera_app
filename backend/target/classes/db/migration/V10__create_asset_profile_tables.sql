-- 设备技术画像表（1:1）
CREATE TABLE asset_technical_profiles (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    asset_id            BIGINT NOT NULL,
    open_ports          TEXT,
    protocols           TEXT,
    service_banner      VARCHAR(512),
    web_title           VARCHAR(256),
    firmware_version    VARCHAR(128),
    serial_number       VARCHAR(128),
    mac_address         VARCHAR(64),
    vendor_hint         VARCHAR(128),
    last_fingerprint_at DATETIME(6),
    created_at          DATETIME(6),
    updated_at          DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_atp_asset_id (asset_id),
    CONSTRAINT fk_atp_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 候选推断结果表（1:N）
CREATE TABLE asset_inference_candidates (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    asset_id        BIGINT NOT NULL,
    field_name      VARCHAR(64)  NOT NULL,
    candidate_value VARCHAR(512) NOT NULL,
    confidence      DECIMAL(4, 3) NOT NULL DEFAULT 0.000,
    reason          TEXT,
    source_type     VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    confirmed       TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_aic_asset_id (asset_id),
    KEY idx_aic_field_name (field_name),
    CONSTRAINT fk_aic_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 证据来源表（1:N）
CREATE TABLE asset_evidences (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    asset_id     BIGINT NOT NULL,
    field_name   VARCHAR(64)  NOT NULL,
    field_value  VARCHAR(512),
    source_type  VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    raw_evidence TEXT,
    confidence   DECIMAL(4, 3) NOT NULL DEFAULT 1.000,
    collected_at DATETIME(6),
    note         VARCHAR(512),
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_ae_asset_id (asset_id),
    KEY idx_ae_field_name (field_name),
    CONSTRAINT fk_ae_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
