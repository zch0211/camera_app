-- Audit log: records all write operations

CREATE TABLE IF NOT EXISTS audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT,
    username      VARCHAR(64),
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(128),
    ip_address    VARCHAR(45),
    details       TEXT,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_audit_user_id   (user_id),
    INDEX idx_audit_action    (action),
    INDEX idx_audit_created   (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
