-- 执行模型升级：从 mode 二元模式迁移到 action 动作驱动（schemaVersion=2）
-- action_key: 动作唯一标识，如 CHECK_VULN / EXEC_COMMAND / FETCH_SNAPSHOT / LIST_USERS / DOWNLOAD_CONFIG
-- output_type: 输出类型，如 BOOLEAN_TEXT / TEXT / JSON / IMAGE / FILE / MIXED
ALTER TABLE poc_execution_logs
    ADD COLUMN action_key  VARCHAR(64) NULL AFTER mode,
    ADD COLUMN output_type VARCHAR(32) NULL AFTER action_key;

-- 兼容历史数据：mode=CHECK → CHECK_VULN, mode=EXPLOIT → EXEC_COMMAND
UPDATE poc_execution_logs SET action_key = 'CHECK_VULN'    WHERE mode = 'CHECK'   AND action_key IS NULL;
UPDATE poc_execution_logs SET action_key = 'EXEC_COMMAND'  WHERE mode = 'EXPLOIT' AND action_key IS NULL;
UPDATE poc_execution_logs SET output_type = 'BOOLEAN_TEXT' WHERE action_key = 'CHECK_VULN'   AND output_type IS NULL;
UPDATE poc_execution_logs SET output_type = 'TEXT'         WHERE action_key = 'EXEC_COMMAND'  AND output_type IS NULL;
