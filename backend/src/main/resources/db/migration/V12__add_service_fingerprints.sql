-- 资产服务指纹表（1:N，同一资产同一端口唯一）
CREATE TABLE asset_service_fingerprints (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    asset_id             BIGINT       NOT NULL,
    port                 INT          NOT NULL,
    transport_protocol   VARCHAR(16)  NOT NULL DEFAULT 'TCP',
    application_protocol VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN',
    scheme               VARCHAR(16),
    service_banner       VARCHAR(512),
    web_title            VARCHAR(256),
    server_header        VARCHAR(256),
    vendor_hint          VARCHAR(256),
    product_hint         VARCHAR(128),
    status               VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    last_collected_at    DATETIME(6),
    last_task_id         BIGINT,
    raw_summary          TEXT,
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_asf_asset_port (asset_id, port),
    KEY idx_asf_asset_id (asset_id),
    CONSTRAINT fk_asf_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 证据来源表增加端口归属字段
ALTER TABLE asset_evidences
    ADD COLUMN related_port    INT    NULL AFTER note,
    ADD COLUMN related_task_id BIGINT NULL AFTER related_port;
