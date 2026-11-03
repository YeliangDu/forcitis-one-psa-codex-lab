-- IMP-1030-FC-D（#895，one-design 裁定 #908 评论 6590861391 第 1 项）：待法人归属会签的人事命令只有在至少有一行 CHANGE 会签、且每一行 CHANGE 都已批准时，
-- 才能由数据库放行为待生效或已生效；转已撤回不受限（拒签、失效、起草人撤回）。其余转换规则与上一版触发器相同。
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
            OR (OLD.status = 'HELD' AND NEW.status IN ('EFFECTIVE', 'WITHDRAWN', 'PENDING'))
            OR (OLD.status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND NEW.status IN ('PENDING', 'EFFECTIVE', 'WITHDRAWN'))) THEN
        RAISE EXCEPTION 'personnel command status cannot move from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND NEW.status IN ('PENDING', 'EFFECTIVE') THEN
        IF NOT EXISTS (SELECT 1 FROM organization_workforce.attribution_countersigns
                        WHERE personnel_command_id = OLD.personnel_command_id AND request_kind = 'CHANGE')
                OR EXISTS (SELECT 1 FROM organization_workforce.attribution_countersigns
                            WHERE personnel_command_id = OLD.personnel_command_id AND request_kind = 'CHANGE' AND decision_kind IS DISTINCT FROM 'APPROVED') THEN
            RAISE EXCEPTION 'personnel command % leaves the countersign wait only when every CHANGE countersign is approved', OLD.personnel_command_id USING ERRCODE = 'restrict_violation';
        END IF;
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
COMMENT ON FUNCTION organization_workforce.refuse_personnel_command_rewrite() IS '人事命令只追加的触发器：状态按 PENDING 到 EFFECTIVE、WITHDRAWN、HELD，HELD 到 EFFECTIVE、WITHDRAWN、PENDING（放行原生效日在将来的待放行离职），待法人归属会签到 PENDING、EFFECTIVE、WITHDRAWN 前进（转入待法人归属会签只在建立时；转 PENDING 或 EFFECTIVE 须至少有一行 CHANGE 会签且每行 CHANGE 都已批准），时刻列只放行由空变非空一次，内容列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
