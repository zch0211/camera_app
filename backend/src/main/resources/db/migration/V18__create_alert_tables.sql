-- ───────────────────────────────────────────────
-- V18: 告警与风险模块底座
-- ───────────────────────────────────────────────

CREATE TABLE alerts (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    asset_id           BIGINT,
    title              VARCHAR(255) NOT NULL,
    source_type        VARCHAR(32)  NOT NULL,
    severity           VARCHAR(16)  NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    summary            TEXT,
    rule_code          VARCHAR(64),
    rule_name          VARCHAR(128),
    fingerprint        VARCHAR(128),
    first_triggered_at DATETIME(6)  NOT NULL,
    last_triggered_at  DATETIME(6)  NOT NULL,
    trigger_count      INT          NOT NULL DEFAULT 1,
    resolved_at        DATETIME(6),
    resolved_by        VARCHAR(64),
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_alerts_asset_id   (asset_id),
    INDEX idx_alerts_status     (status),
    INDEX idx_alerts_severity   (severity),
    INDEX idx_alerts_source_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alert_evidences (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    alert_id      BIGINT       NOT NULL,
    evidence_type VARCHAR(32)  NOT NULL,
    ref_id        BIGINT,
    field_name    VARCHAR(128),
    field_value   TEXT,
    description   TEXT,
    confidence    DECIMAL(5,3),
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_alert_evidences_alert_id (alert_id),
    CONSTRAINT fk_alert_evidences_alert FOREIGN KEY (alert_id) REFERENCES alerts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alert_operations (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    alert_id          BIGINT      NOT NULL,
    operation_type    VARCHAR(32) NOT NULL,
    operator_username VARCHAR(64),
    comment           TEXT,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_alert_operations_alert_id (alert_id),
    CONSTRAINT fk_alert_operations_alert FOREIGN KEY (alert_id) REFERENCES alerts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
