ALTER TABLE poc_execution_logs
    ADD COLUMN mode            VARCHAR(32)  NULL AFTER executed_by,
    ADD COLUMN target_strategy VARCHAR(32)  NULL AFTER mode,
    ADD COLUMN final_port      INT          NULL AFTER target_strategy,
    ADD COLUMN used_target     VARCHAR(500) NULL AFTER final_port;
