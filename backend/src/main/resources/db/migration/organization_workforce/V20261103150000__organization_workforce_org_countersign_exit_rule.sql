-- #910 S2（one-design 裁定 #908 评论 6590861391 第 1 项，与人事命令同一条 fail-closed 规则）：待法人归属会签的组织结构命令只有在至少有一行 CHANGE 会签、且每一行 CHANGE 都已批准时，
-- 才能由数据库放行为待生效或已生效；转已撤回不受限（拒签、失效、起草人撤回）。其余转换规则与上一版触发器相同。
CREATE OR REPLACE FUNCTION organization_workforce.refuse_org_structure_command_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'org structure commands are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'org structure commands are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.status <> OLD.status AND NOT ((OLD.status = 'PENDING' AND NEW.status IN ('IN_PROGRESS', 'EFFECTIVE', 'WITHDRAWN'))
            OR (OLD.status = 'IN_PROGRESS' AND NEW.status = 'EFFECTIVE')
            OR (OLD.status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND NEW.status IN ('PENDING', 'EFFECTIVE', 'WITHDRAWN'))) THEN
        RAISE EXCEPTION 'org structure command status cannot move from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND NEW.status IN ('PENDING', 'EFFECTIVE') THEN
        IF NOT EXISTS (SELECT 1 FROM organization_workforce.attribution_countersigns
                        WHERE org_structure_command_id = OLD.org_structure_command_id AND request_kind = 'CHANGE')
                OR EXISTS (SELECT 1 FROM organization_workforce.attribution_countersigns
                            WHERE org_structure_command_id = OLD.org_structure_command_id AND request_kind = 'CHANGE' AND decision_kind IS DISTINCT FROM 'APPROVED') THEN
            RAISE EXCEPTION 'org structure command % leaves the countersign wait only when every CHANGE countersign is approved', OLD.org_structure_command_id USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    FOREACH column_name IN ARRAY ARRAY['applied_at', 'withdrawn_at', 'withdrawn_by', 'withdrawal_reason', 'affected_employee_count'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF OLD.transferred_employee_count IS NOT NULL AND (NEW.transferred_employee_count IS NULL OR NEW.transferred_employee_count < OLD.transferred_employee_count) THEN
        RAISE EXCEPTION 'transferred_employee_count of %.% can only increase', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'affected_employee_count' - 'transferred_employee_count' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'affected_employee_count' - 'transferred_employee_count' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only the status, progress and timestamp columns of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_org_structure_command_rewrite() IS '组织结构命令只追加的触发器：状态按 PENDING 到 IN_PROGRESS、EFFECTIVE、WITHDRAWN，IN_PROGRESS 到 EFFECTIVE，待会签到 PENDING、EFFECTIVE、WITHDRAWN 前进（转 PENDING 或 EFFECTIVE 须至少有一行 CHANGE 会签且每行 CHANGE 都已批准），时刻列、撤回列与涉及人数只放行由空变非空一次，已转人数只增不减，其余列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
