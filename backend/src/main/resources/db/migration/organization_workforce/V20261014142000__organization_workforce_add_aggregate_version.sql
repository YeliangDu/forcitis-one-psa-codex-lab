-- 同步记录与法人成为带状态机的聚合：写入带 version 乐观锁，version 同时是聚合事件的序号。
ALTER TABLE organization_workforce.legal_entities ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE organization_workforce.sync_runs ADD COLUMN version bigint NOT NULL DEFAULT 0;
