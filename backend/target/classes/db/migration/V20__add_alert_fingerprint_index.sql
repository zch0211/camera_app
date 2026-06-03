-- V20: alerts.fingerprint 加索引，用于自动告警去重查询性能优化
ALTER TABLE alerts ADD INDEX idx_alerts_fingerprint (fingerprint);
