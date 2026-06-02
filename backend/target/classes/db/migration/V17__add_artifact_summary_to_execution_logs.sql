-- 执行结果附件支持：记录 IMAGE/FILE 类动作（FETCH_SNAPSHOT / DOWNLOAD_CONFIG）产生的文件元数据
-- 字段格式：JSON 数组，每项含 name / type / mimeType / sizeBytes / objectKey
ALTER TABLE poc_execution_logs
    ADD COLUMN artifact_summary TEXT NULL AFTER output_type;
