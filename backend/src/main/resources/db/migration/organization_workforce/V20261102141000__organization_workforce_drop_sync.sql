-- D2（#934，ADR-0068 第 2.5 节）：删除旧同步的表与列。旧行不转入新表（已处置的放行有审计）。
-- 仍有处于 HELD 的同步记录下的扣留离职行时，直接报错、不删，也不经 CASCADE 静默删除；没有扣留行的 HELD 运行不拦（与上线前预检一致）。
DO $$
DECLARE
    held_rows bigint;
BEGIN
    SELECT count(*) INTO held_rows FROM organization_workforce.sync_run_held_separations s
        JOIN organization_workforce.sync_runs r ON r.id = s.sync_run_id WHERE r.status = 'HELD';
    IF held_rows <> 0 THEN
        RAISE EXCEPTION 'cannot drop the old sync tables: % separation row(s) are still held by a HELD sync run; release or withdraw them first', held_rows
            USING ERRCODE = 'restrict_violation';
    END IF;
END
$$;

DROP TABLE organization_workforce.sync_run_held_separations;
DROP TABLE organization_workforce.sync_diffs;
DROP TABLE organization_workforce.sync_runs;
ALTER TABLE organization_workforce.org_units DROP COLUMN last_sync_run_id;
