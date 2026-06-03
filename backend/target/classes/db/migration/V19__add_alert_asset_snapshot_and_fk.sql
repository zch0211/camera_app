-- ─────────────────────────────────────────────────────────────────────────
-- V19: alerts 表资产快照字段 + FK 外键约束 (ON DELETE SET NULL)
--
-- 策略说明：
--   1. alerts.asset_id 可为空（未关联资产的告警合法）
--   2. 创建告警时若传 assetId，应用层强制校验资产存在，并同步写入快照字段
--   3. 资产被物理删除时，alerts.asset_id 自动置 NULL（FK ON DELETE SET NULL）
--      历史快照字段保留，告警列表/详情仍可展示历史关联信息
-- ─────────────────────────────────────────────────────────────────────────

-- Step 1: 添加快照字段
ALTER TABLE alerts
    ADD COLUMN asset_name_snapshot VARCHAR(128) AFTER asset_id,
    ADD COLUMN asset_ip_snapshot   VARCHAR(64)  AFTER asset_name_snapshot,
    ADD COLUMN asset_type_snapshot VARCHAR(32)  AFTER asset_ip_snapshot;

-- Step 2: 将孤儿告警（指向已不存在资产）的 asset_id 置空，避免加 FK 时报错
UPDATE alerts
SET asset_id = NULL
WHERE asset_id IS NOT NULL
  AND asset_id NOT IN (SELECT id FROM assets);

-- Step 3: 添加 FK，资产删除时自动将 asset_id 置 NULL
ALTER TABLE alerts
    ADD CONSTRAINT fk_alerts_asset_id
    FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE SET NULL;
