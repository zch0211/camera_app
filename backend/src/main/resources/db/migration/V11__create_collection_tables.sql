-- 采集任务表
CREATE TABLE asset_collection_tasks (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    asset_id          BIGINT       NOT NULL,
    task_type         VARCHAR(32)  NOT NULL DEFAULT 'LIGHTWEIGHT_PROBE',
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    trigger_type      VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    started_at        DATETIME(6),
    finished_at       DATETIME(6),
    success           TINYINT(1),
    summary           TEXT,
    error_message     TEXT,
    writeback_applied TINYINT(1)   NOT NULL DEFAULT 0,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_act_asset_id (asset_id),
    KEY idx_act_status (status),
    CONSTRAINT fk_act_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 原始采集结果表
CREATE TABLE asset_collection_results (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    task_id       BIGINT      NOT NULL,
    asset_id      BIGINT      NOT NULL,
    probe_type    VARCHAR(32) NOT NULL,
    success       TINYINT(1)  NOT NULL DEFAULT 0,
    target_host   VARCHAR(128),
    target_port   INT,
    protocol_hint VARCHAR(32),
    raw_data      TEXT,
    parsed_data   TEXT,
    error_message TEXT,
    collected_at  DATETIME(6),
    created_at    DATETIME(6),
    updated_at    DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_acr_task_id (task_id),
    KEY idx_acr_asset_id (asset_id),
    CONSTRAINT fk_acr_task FOREIGN KEY (task_id) REFERENCES asset_collection_tasks (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
