-- IMP-1030-ORG-F3（#908，one-design 签发 #908 评论）：组织结构命令的法人归属会签依据表、会签表的组织结构命令父键，以及合并调岗的未会签拒绝触发器。只建表、列、约束、触发器与登记，没有应用代码。

-- 1. 法人归属会签依据：因组织结构命令而成本法人变化的每个员工一行，写入后不变。
CREATE TABLE organization_workforce.attribution_countersign_bases (
    attribution_countersign_basis_id uuid        NOT NULL,
    org_structure_command_id         uuid        NOT NULL,
    employee_id                      uuid        NOT NULL,
    previous_legal_entity_id         uuid        NOT NULL,
    new_legal_entity_id              uuid        NOT NULL,
    created_at                       timestamptz NOT NULL,
    updated_at                       timestamptz NOT NULL,
    CONSTRAINT pk_attribution_countersign_bases PRIMARY KEY (attribution_countersign_basis_id),
    CONSTRAINT uk_attribution_countersign_bases_command_employee UNIQUE (org_structure_command_id, employee_id),
    CONSTRAINT fk_attribution_countersign_bases_org_structure_command_id FOREIGN KEY (org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_attribution_countersign_bases_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attribution_countersign_bases_previous_legal_entity_id FOREIGN KEY (previous_legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attribution_countersign_bases_new_legal_entity_id FOREIGN KEY (new_legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT,
    CONSTRAINT ck_attribution_countersign_bases_changed CHECK (previous_legal_entity_id <> new_legal_entity_id)
);
CREATE INDEX ix_attribution_countersign_bases_employee_id ON organization_workforce.attribution_countersign_bases (employee_id);
CREATE INDEX ix_attribution_countersign_bases_previous_legal_entity_id ON organization_workforce.attribution_countersign_bases (previous_legal_entity_id);
CREATE INDEX ix_attribution_countersign_bases_new_legal_entity_id ON organization_workforce.attribution_countersign_bases (new_legal_entity_id);

COMMENT ON TABLE organization_workforce.attribution_countersign_bases IS '法人归属会签依据：组织结构命令（合并、指定法人）使员工的成本法人发生变化时，逐个员工记下变化前后的法人，写入后不变；只追加。粒度：每个组织结构命令的每个受影响员工一行';
COMMENT ON COLUMN organization_workforce.attribution_countersign_bases.attribution_countersign_basis_id IS '法人归属会签依据 ID（UUIDv7，由应用层生成）';
COMMENT ON COLUMN organization_workforce.attribution_countersign_bases.org_structure_command_id IS '组织结构命令 ID：产生这条依据的命令';
COMMENT ON COLUMN organization_workforce.attribution_countersign_bases.employee_id IS '员工 ID：成本法人发生变化的员工';
COMMENT ON COLUMN organization_workforce.attribution_countersign_bases.previous_legal_entity_id IS '原法人主体 ID：变化前的成本法人，与新法人不同';
COMMENT ON COLUMN organization_workforce.attribution_countersign_bases.new_legal_entity_id IS '新法人主体 ID：变化后的成本法人，与原法人不同';
COMMENT ON COLUMN organization_workforce.attribution_countersign_bases.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.attribution_countersign_bases.updated_at IS '更新时间：写入时等于创建时间，之后不会变（触发器拒绝任何 UPDATE）';

-- 只追加：任何 UPDATE 都拒绝，DELETE 只放行表属主（清除），TRUNCATE 拒绝。
CREATE FUNCTION organization_workforce.refuse_attribution_countersign_basis_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'attribution countersign bases are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'attribution countersign bases are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'attribution countersign bases are append-only: update on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_attribution_countersign_basis_rewrite() IS '法人归属会签依据只追加的触发器：任何 UPDATE 拒绝，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_attribution_countersign_bases_append_only BEFORE UPDATE OR DELETE ON organization_workforce.attribution_countersign_bases
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_attribution_countersign_basis_rewrite();
CREATE TRIGGER tg_attribution_countersign_bases_append_only_truncate BEFORE TRUNCATE ON organization_workforce.attribution_countersign_bases
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_attribution_countersign_basis_rewrite();

-- 2. 法人归属会签表：父可以是人事命令或组织结构命令。
ALTER TABLE organization_workforce.attribution_countersigns
    ALTER COLUMN personnel_command_id DROP NOT NULL,
    ALTER COLUMN employee_id DROP NOT NULL,
    ADD COLUMN org_structure_command_id uuid,
    ADD CONSTRAINT ck_attribution_countersigns_parent CHECK (num_nonnulls(personnel_command_id, org_structure_command_id) = 1 AND (personnel_command_id IS NULL) = (employee_id IS NULL)),
    ADD CONSTRAINT fk_attribution_countersigns_org_command FOREIGN KEY (org_structure_command_id, drafted_by)
        REFERENCES organization_workforce.org_structure_commands (org_structure_command_id, created_by) ON DELETE RESTRICT;
ALTER TABLE organization_workforce.attribution_countersigns DROP CONSTRAINT uk_attribution_countersigns_request;
DROP INDEX organization_workforce.uk_attribution_countersigns_open;
CREATE UNIQUE INDEX uk_attribution_countersigns_request_personnel ON organization_workforce.attribution_countersigns
    (personnel_command_id, request_kind, legal_entity_id, request_sequence_number) WHERE personnel_command_id IS NOT NULL;
CREATE UNIQUE INDEX uk_attribution_countersigns_request_org ON organization_workforce.attribution_countersigns
    (org_structure_command_id, request_kind, legal_entity_id, request_sequence_number) WHERE org_structure_command_id IS NOT NULL;
CREATE UNIQUE INDEX uk_attribution_countersigns_open_personnel ON organization_workforce.attribution_countersigns
    (personnel_command_id, request_kind, legal_entity_id) WHERE decision_kind IS NULL AND personnel_command_id IS NOT NULL;
CREATE UNIQUE INDEX uk_attribution_countersigns_open_org ON organization_workforce.attribution_countersigns
    (org_structure_command_id, request_kind, legal_entity_id) WHERE decision_kind IS NULL AND org_structure_command_id IS NOT NULL;
CREATE INDEX ix_attribution_countersigns_personnel_command_id ON organization_workforce.attribution_countersigns (personnel_command_id) WHERE personnel_command_id IS NOT NULL;
CREATE INDEX ix_attribution_countersigns_org_structure_command_id ON organization_workforce.attribution_countersigns (org_structure_command_id) WHERE org_structure_command_id IS NOT NULL;
COMMENT ON COLUMN organization_workforce.attribution_countersigns.personnel_command_id IS '人事命令 ID：被会签的法人变更命令；为空表示会签的是组织结构命令';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.employee_id IS '员工 ID：命令所涉员工，与人事命令一致；为空表示会签的是组织结构命令';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.org_structure_command_id IS '组织结构命令 ID：被会签的合并或指定法人命令；为空表示会签的是人事命令';

-- 3. 合并调岗的未会签拒绝：合并产生的逐人调岗，要求合并在执行中，且所有变更会签请求都已批准（或合并无需会签）。
CREATE FUNCTION organization_workforce.refuse_unsigned_merge_transfer() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    merge_command record;
    change_requests integer;
    unapproved_requests integer;
    basis_rows integer;
BEGIN
    IF NEW.org_structure_command_id IS NULL OR NEW.status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' THEN
        RETURN NEW;
    END IF;
    SELECT command_kind, status INTO merge_command FROM organization_workforce.org_structure_commands WHERE org_structure_command_id = NEW.org_structure_command_id;
    IF NOT FOUND THEN
        RETURN NEW;  -- 命令不存在：由外键 fk_personnel_commands_org_structure_command_id 拒绝
    END IF;
    IF merge_command.command_kind <> 'MERGE' OR merge_command.status <> 'IN_PROGRESS' THEN
        RAISE EXCEPTION 'a transfer made by an org structure command needs a merge in progress' USING ERRCODE = 'restrict_violation';
    END IF;
    SELECT count(*), count(*) FILTER (WHERE decision_kind IS DISTINCT FROM 'APPROVED') INTO change_requests, unapproved_requests
      FROM organization_workforce.attribution_countersigns WHERE org_structure_command_id = NEW.org_structure_command_id AND request_kind = 'CHANGE';
    SELECT count(*) INTO basis_rows FROM organization_workforce.attribution_countersign_bases WHERE org_structure_command_id = NEW.org_structure_command_id;
    IF change_requests > 0 THEN
        IF unapproved_requests > 0 THEN
            RAISE EXCEPTION 'a transfer made by a merge needs every attribution countersign of the merge approved' USING ERRCODE = 'restrict_violation';
        END IF;
    ELSIF basis_rows > 0 THEN
        RAISE EXCEPTION 'a transfer made by a merge that changes legal entities needs its attribution countersigns' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_unsigned_merge_transfer() IS '合并调岗的未会签拒绝：组织结构命令产生的人事命令，除非本身处于待法人归属会签，否则要求该命令是执行中的合并，且合并的所有变更会签请求都已批准，或合并没有会签请求也没有会签依据';
CREATE TRIGGER tg_personnel_commands_merge_transfer BEFORE INSERT ON organization_workforce.personnel_commands
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_unsigned_merge_transfer();
