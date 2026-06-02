-- Round 11: dual-level discovery semantics (ALIVE vs CANDIDATE)

ALTER TABLE discovery_results
    ADD COLUMN discovery_level VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE' AFTER source_type;

ALTER TABLE discovery_tasks
    ADD COLUMN alive_count     INT NOT NULL DEFAULT 0 AFTER discovered_count,
    ADD COLUMN candidate_count INT NOT NULL DEFAULT 0 AFTER alive_count;
