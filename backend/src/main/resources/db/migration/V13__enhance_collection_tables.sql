-- 采集任务表增加预设与插件配置
ALTER TABLE asset_collection_tasks
    ADD COLUMN preset          VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' AFTER task_type,
    ADD COLUMN enabled_plugins TEXT                                   AFTER preset;

-- 采集结果表增加插件名称与置信度
ALTER TABLE asset_collection_results
    ADD COLUMN plugin_name VARCHAR(64)                        AFTER probe_type,
    ADD COLUMN confidence  DECIMAL(4, 3) NOT NULL DEFAULT 0.000 AFTER plugin_name;
