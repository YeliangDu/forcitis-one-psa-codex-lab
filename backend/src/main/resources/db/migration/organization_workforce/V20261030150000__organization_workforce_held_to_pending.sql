-- IMP-1030-PAR-C1（#931，one-design 签发 #732 评论）：放行原生效日在将来的待放行离职时，命令由 HELD 转 PENDING（到日作业再转 EFFECTIVE，不再进入待放行）。
-- 只放宽人事命令的 HELD 到 PENDING 一对；已生效、已撤回的命令仍不能改回 PENDING，暂停记录仍只由 HELD 走向 RELEASED 或 WITHDRAWN，不加列。
CREATE OR REPLACE FUNCTION organization_workforce.refuse_personnel_command_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'personnel commands are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'personnel commands are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.status <> OLD.status AND NOT ((OLD.status = 'PENDING' AND NEW.status IN ('EFFECTIVE', 'WITHDRAWN', 'HELD'))
            OR (OLD.status = 'HELD' AND NEW.status IN ('EFFECTIVE', 'WITHDRAWN', 'PENDING'))) THEN
        RAISE EXCEPTION 'personnel command status cannot move from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['applied_at', 'withdrawn_at', 'withdrawn_by', 'withdrawal_reason'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only the status and timestamp columns of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_personnel_command_rewrite() IS '人事命令只追加的触发器：状态按 PENDING 到 EFFECTIVE、WITHDRAWN、HELD 以及 HELD 到 EFFECTIVE、WITHDRAWN、PENDING（放行原生效日在将来的待放行离职）前进，时刻列只放行由空变非空一次，内容列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_status_of_aggregate;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_status_of_aggregate CHECK (coalesce(
        (aggregate_type_code = 'PersonnelCommand'
            AND ((from_status IS NULL AND to_status IN ('PENDING', 'HELD', 'EFFECTIVE'))
                 OR (from_status = 'PENDING' AND to_status IN ('EFFECTIVE', 'WITHDRAWN', 'HELD'))
                 OR (from_status = 'HELD' AND to_status IN ('EFFECTIVE', 'WITHDRAWN', 'PENDING'))))
        OR (aggregate_type_code = 'SeparationHold'
            AND ((from_status IS NULL AND to_status = 'HELD')
                 OR (from_status = 'HELD' AND to_status IN ('RELEASED', 'WITHDRAWN')))), false));
COMMENT ON COLUMN organization_workforce.state_transitions.from_status IS '原状态：为空表示新建或迁入的初始状态；取值为本 schema 各状态机的状态并集，合法流转见 ck_state_transitions_status_of_aggregate：PersonnelCommand 为空到待生效、待放行、已生效，待生效到已生效、已撤回、待放行，待放行到已生效、已撤回、待生效（放行原生效日在将来的离职）；SeparationHold 为空到待放行，待放行到已放行、已撤回';
