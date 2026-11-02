-- IMP-1030-FC-A（#892，one-design 签发评论）：员工法人归属期间补上 W2 设计第 2、3.6 节的版本通用列与对人事命令的引用，
-- 并把只追加触发器换成允许作废已关闭行的专用函数。写入归属期间的仓储不带写入渠道，缺省 MIGRATION；人事命令写入的行必须显式写 COMMAND。
ALTER TABLE organization_workforce.employee_legal_entities
    ADD COLUMN change_channel_kind text NOT NULL DEFAULT 'MIGRATION',
    ADD COLUMN change_command_id uuid,
    ADD COLUMN version_audit_entry_id uuid,
    ADD COLUMN closed_at timestamptz,
    ADD COLUMN superseded_at timestamptz,
    ADD COLUMN superseding_command_id uuid,
    ADD COLUMN corrected_from_version_id uuid;
-- 已关闭的行补写关闭时刻（取最后更新时间），再加关闭列对的检查约束；补写要在旧触发器拆掉之后、新触发器建好之前，旧触发器不放行已关闭行的改动。
DROP TRIGGER tg_employee_legal_entities_append_only ON organization_workforce.employee_legal_entities;
DROP TRIGGER tg_employee_legal_entities_append_only_truncate ON organization_workforce.employee_legal_entities;
UPDATE organization_workforce.employee_legal_entities SET closed_at = updated_at WHERE effective_to_date IS NOT NULL;
ALTER TABLE organization_workforce.employee_legal_entities
    ADD CONSTRAINT fk_employee_legal_entities_change_command_id FOREIGN KEY (change_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_employee_legal_entities_superseding_command_id FOREIGN KEY (superseding_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_employee_legal_entities_corrected_from_version_id FOREIGN KEY (corrected_from_version_id) REFERENCES organization_workforce.employee_legal_entities (employee_legal_entity_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_employee_legal_entities_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    ADD CONSTRAINT ck_employee_legal_entities_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    ADD CONSTRAINT ck_employee_legal_entities_command CHECK ((change_channel_kind = 'COMMAND') = (change_command_id IS NOT NULL)),
    ADD CONSTRAINT ck_employee_legal_entities_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    ADD CONSTRAINT ck_employee_legal_entities_closed CHECK ((effective_to_date IS NULL) = (closed_at IS NULL)),
    ADD CONSTRAINT ck_employee_legal_entities_superseded CHECK ((superseded_at IS NULL) = (superseding_command_id IS NULL));
-- 自然键与重叠约束只管当前有效的行：被作废的行不占生效日，也不挡重叠。
ALTER TABLE organization_workforce.employee_legal_entities DROP CONSTRAINT uk_employee_legal_entities_effective_from;
CREATE UNIQUE INDEX uk_employee_legal_entities_effective_from ON organization_workforce.employee_legal_entities (employee_id, effective_from_date) WHERE superseded_at IS NULL;
ALTER TABLE organization_workforce.employee_legal_entities DROP CONSTRAINT ex_employee_legal_entities_overlap;
ALTER TABLE organization_workforce.employee_legal_entities
    ADD CONSTRAINT ex_employee_legal_entities_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL);
CREATE INDEX ix_employee_legal_entities_change_command_id ON organization_workforce.employee_legal_entities (change_command_id) WHERE change_command_id IS NOT NULL;
CREATE INDEX ix_employee_legal_entities_superseding_command_id ON organization_workforce.employee_legal_entities (superseding_command_id) WHERE superseding_command_id IS NOT NULL;
CREATE INDEX ix_employee_legal_entities_corrected_from_version_id ON organization_workforce.employee_legal_entities (corrected_from_version_id) WHERE corrected_from_version_id IS NOT NULL;

COMMENT ON TABLE organization_workforce.employee_legal_entities IS '员工法人归属：个别员工单独指定的法人，优先于部门对照；生效区间维度，只追加（开放期间只能被关闭，同一员工同一生效日至多一个当前行；作废只能由空变非空一次，已关闭的期间也可作废）；粒度：每个员工的每个归属期间一行，被更正或补登替换的行保留为作废行';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.change_channel_kind IS '写入渠道：COMMAND 人事命令 / MIGRATION 迁入；缺省 MIGRATION，归属仓储写入的行取缺省，人事命令写入的行必须显式写 COMMAND，忘写时由命令列的检查约束拒绝';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.change_command_id IS '写入本行的人事命令 ID；为空表示迁入行';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.version_audit_entry_id IS '写入本行的同一事务里写下的审计条目 ID（跨 schema 只存 ID）；迁入行可空';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.closed_at IS '关闭时刻：写失效日的同时写入，由空变非空一次；为空表示开放期间';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.superseded_at IS '作废时刻：被更正或补登替换的行，由空变非空一次，开放行与已关闭行都可作废；为空表示当前有效的事实';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.superseding_command_id IS '作废它的人事命令 ID；为空表示未被作废';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.corrected_from_version_id IS '重新追加行的来源：指向被作废的行；为空表示不是重新追加的行';

-- 员工法人归属只追加：关闭列对与作废列对各只能由空变非空一次，作废开放行与已关闭行都放行，其余列除更新时间外不变；任何行不能删或清空。
CREATE FUNCTION organization_workforce.refuse_employee_legal_entity_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    column_name text;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'version rows are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['effective_to_date', 'closed_at', 'superseded_at', 'superseding_command_id'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_command_id' - 'updated_at')
            <> (to_jsonb(OLD) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_command_id' - 'updated_at') THEN
        IF OLD.effective_to_date IS NOT NULL THEN
            RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_employee_legal_entity_rewrite() IS '员工法人归属只追加的触发器：关闭列与作废列各只放行由空变非空一次，作废开放行与已关闭行都放行，其余列除更新时间外不变，任何行不能删或清空';
CREATE TRIGGER tg_employee_legal_entities_append_only BEFORE UPDATE OR DELETE ON organization_workforce.employee_legal_entities
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_employee_legal_entity_rewrite();
CREATE TRIGGER tg_employee_legal_entities_append_only_truncate BEFORE TRUNCATE ON organization_workforce.employee_legal_entities
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_employee_legal_entity_rewrite();
