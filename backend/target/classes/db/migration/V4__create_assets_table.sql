CREATE TABLE assets (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    ip          VARCHAR(64)     NOT NULL,
    name        VARCHAR(128)    NOT NULL,
    brand       VARCHAR(64),
    model       VARCHAR(64),
    location    VARCHAR(256),
    online      TINYINT(1)      NOT NULL DEFAULT 0,
    risk_score  INT             DEFAULT 0,
    org_id      BIGINT,
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assets_ip (ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
