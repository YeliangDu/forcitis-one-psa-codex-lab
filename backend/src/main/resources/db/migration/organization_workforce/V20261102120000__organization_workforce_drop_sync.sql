-- D2（#934，ADR-0068 第 2.5 节）：删除旧同步的表与列。旧行不转入新表（已处置的放行有审计）。
-- 仍有处于 HELD 的同步记录时说明还有未放行的暂停离职，直接报错、不删，也不经 CASCADE 静默删除。
DO $$
DECLARE
    held_runs bigint;
    held_rows bigint;
BEGIN
    SELECT count(*) INTO held_runs FROM organization_workforce.sync_runs WHERE status = 'HELD';
    SELECT count(*) INTO held_rows FROM organization_workforce.sync_run_held_separations s
        JOIN organization_workforce.sync_runs r ON r.id = s.sync_run_id WHERE r.status = 'HELD';
    IF held_runs <> 0 OR held_rows <> 0 THEN
        RAISE EXCEPTION 'cannot drop the old sync tables: % HELD sync run(s) still hold % separation row(s); release or withdraw them first', held_runs, held_rows
            USING ERRCODE = 'restrict_violation';
    END IF;
END
$$;

DROP TABLE organization_workforce.sync_run_held_separations;
DROP TABLE organization_workforce.sync_diffs;
DROP TABLE organization_workforce.sync_runs;
ALTER TABLE organization_workforce.org_units DROP COLUMN last_sync_run_id;
