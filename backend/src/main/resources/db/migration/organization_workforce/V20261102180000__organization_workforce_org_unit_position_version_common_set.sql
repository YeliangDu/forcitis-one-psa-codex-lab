-- IMP-1030-ORG-F0（#904，one-design 签发评论及补充）：组织单元版本与组织单元法人归属期间补上 W2 设计第 2、3.6 节的版本通用列与对组织结构命令的引用，
-- 新建岗位版本表（W2 第 3.3 节，空表，岗位根表仍由原路径维护）。三张表都换成允许作废已关闭行的专用只追加触发器。
-- 没有命令的写入渠道不用改：写入渠道缺省 MIGRATION，命令写入的行必须显式写 COMMAND。

-- 一、组织单元版本
ALTER TABLE organization_workforce.org_unit_versions
    ADD COLUMN change_channel_kind text NOT NULL DEFAULT 'MIGRATION',
    ADD COLUMN change_org_structure_command_id uuid,
    ADD COLUMN version_audit_entry_id uuid,
    ADD COLUMN closed_at timestamptz,
    ADD COLUMN superseded_at timestamptz,
    ADD COLUMN superseding_org_structure_command_id uuid,
    ADD COLUMN corrected_from_version_id uuid,
    ADD COLUMN short_name text;
-- 已关闭的行补写关闭时刻（取最后更新时间），再加关闭列对的检查约束；补写要在旧触发器拆掉之后、新触发器建好之前，旧触发器不放行已关闭行的改动。
DROP TRIGGER tg_org_unit_versions_append_only ON organization_workforce.org_unit_versions;
DROP TRIGGER tg_org_unit_versions_append_only_truncate ON organization_workforce.org_unit_versions;
UPDATE organization_workforce.org_unit_versions SET closed_at = updated_at WHERE effective_to_date IS NOT NULL;
ALTER TABLE organization_workforce.org_unit_versions
    ADD CONSTRAINT fk_org_unit_versions_change_org_structure_command_id FOREIGN KEY (change_org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_org_unit_versions_superseding_org_structure_command_id FOREIGN KEY (superseding_org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_org_unit_versions_corrected_from_version_id FOREIGN KEY (corrected_from_version_id) REFERENCES organization_workforce.org_unit_versions (org_unit_version_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_org_unit_versions_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    ADD CONSTRAINT ck_org_unit_versions_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    ADD CONSTRAINT ck_org_unit_versions_command CHECK ((change_channel_kind = 'COMMAND') = (change_org_structure_command_id IS NOT NULL)),
    ADD CONSTRAINT ck_org_unit_versions_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    ADD CONSTRAINT ck_org_unit_versions_closed CHECK ((effective_to_date IS NULL) = (closed_at IS NULL)),
    ADD CONSTRAINT ck_org_unit_versions_superseded CHECK ((superseded_at IS NULL) = (superseding_org_structure_command_id IS NULL)),
    ADD CONSTRAINT ck_org_unit_versions_short_name CHECK (short_name IS NULL OR (char_length(short_name) BETWEEN 1 AND 50 AND btrim(short_name) <> ''));
ALTER TABLE organization_workforce.org_unit_versions DROP CONSTRAINT ex_org_unit_versions_overlap;
ALTER TABLE organization_workforce.org_unit_versions
    ADD CONSTRAINT ex_org_unit_versions_overlap EXCLUDE USING gist (org_unit_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL);
CREATE INDEX ix_org_unit_versions_change_org_structure_command_id ON organization_workforce.org_unit_versions (change_org_structure_command_id) WHERE change_org_structure_command_id IS NOT NULL;
CREATE INDEX ix_org_unit_versions_superseding_org_structure_command_id ON organization_workforce.org_unit_versions (superseding_org_structure_command_id) WHERE superseding_org_structure_command_id IS NOT NULL;
CREATE INDEX ix_org_unit_versions_corrected_from_version_id ON organization_workforce.org_unit_versions (corrected_from_version_id) WHERE corrected_from_version_id IS NOT NULL;

COMMENT ON TABLE organization_workforce.org_unit_versions IS '组织单元版本：组织单元的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订；作废只能由空变非空一次，已关闭的版本也可作废）；生效依据为组织业务生效日（DECLARED）；粒度：每个组织单元的每个生效区间每个修订一行';
COMMENT ON COLUMN organization_workforce.org_unit_versions.short_name IS '部门简称（1 至 50 字）；为空表示没有简称';
COMMENT ON COLUMN organization_workforce.org_unit_versions.change_channel_kind IS '写入渠道：COMMAND 组织结构命令 / MIGRATION 迁入；缺省 MIGRATION，没有命令的写入取缺省，命令写入的行必须显式写 COMMAND，忘写时由命令列的检查约束拒绝';
COMMENT ON COLUMN organization_workforce.org_unit_versions.change_org_structure_command_id IS '写入本行的组织结构命令 ID；为空表示迁入行';
COMMENT ON COLUMN organization_workforce.org_unit_versions.version_audit_entry_id IS '写入本行的同一事务里写下的审计条目 ID（跨 schema 只存 ID）；迁入行可空';
COMMENT ON COLUMN organization_workforce.org_unit_versions.closed_at IS '关闭时刻：写失效日的同时写入，由空变非空一次；为空表示开放版本';
COMMENT ON COLUMN organization_workforce.org_unit_versions.superseded_at IS '作废时刻：被更正或补登替换的行，由空变非空一次，开放行与已关闭行都可作废；为空表示当前有效的事实';
COMMENT ON COLUMN organization_workforce.org_unit_versions.superseding_org_structure_command_id IS '作废它的组织结构命令 ID；为空表示未被作废';
COMMENT ON COLUMN organization_workforce.org_unit_versions.corrected_from_version_id IS '重新追加行的来源：指向被作废的行；为空表示不是重新追加的行';

CREATE FUNCTION organization_workforce.refuse_org_unit_version_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    column_name text;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'version rows are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['effective_to_date', 'closed_at', 'superseded_at', 'superseding_org_structure_command_id'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_org_structure_command_id' - 'updated_at')
            <> (to_jsonb(OLD) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_org_structure_command_id' - 'updated_at') THEN
        IF OLD.effective_to_date IS NOT NULL THEN
            RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_org_unit_version_rewrite() IS '组织单元版本只追加的触发器：关闭列与作废列各只放行由空变非空一次，作废开放行与已关闭行都放行，其余列除更新时间外不变，任何行不能删或清空';
CREATE TRIGGER tg_org_unit_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.org_unit_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_org_unit_version_rewrite();
CREATE TRIGGER tg_org_unit_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.org_unit_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_org_unit_version_rewrite();

-- 二、组织单元法人归属期间
ALTER TABLE organization_workforce.org_unit_legal_entities
    ADD COLUMN change_channel_kind text NOT NULL DEFAULT 'MIGRATION',
    ADD COLUMN change_org_structure_command_id uuid,
    ADD COLUMN version_audit_entry_id uuid,
    ADD COLUMN closed_at timestamptz,
    ADD COLUMN superseded_at timestamptz,
    ADD COLUMN superseding_org_structure_command_id uuid,
    ADD COLUMN corrected_from_version_id uuid;
DROP TRIGGER tg_org_unit_legal_entities_append_only ON organization_workforce.org_unit_legal_entities;
DROP TRIGGER tg_org_unit_legal_entities_append_only_truncate ON organization_workforce.org_unit_legal_entities;
UPDATE organization_workforce.org_unit_legal_entities SET closed_at = updated_at WHERE effective_to_date IS NOT NULL;
ALTER TABLE organization_workforce.org_unit_legal_entities
    ADD CONSTRAINT fk_org_unit_legal_entities_change_org_structure_command_id FOREIGN KEY (change_org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_org_unit_legal_entities_superseding_org_structure_command_id FOREIGN KEY (superseding_org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_org_unit_legal_entities_corrected_from_version_id FOREIGN KEY (corrected_from_version_id) REFERENCES organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_org_unit_legal_entities_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    ADD CONSTRAINT ck_org_unit_legal_entities_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    ADD CONSTRAINT ck_org_unit_legal_entities_command CHECK ((change_channel_kind = 'COMMAND') = (change_org_structure_command_id IS NOT NULL)),
    ADD CONSTRAINT ck_org_unit_legal_entities_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    ADD CONSTRAINT ck_org_unit_legal_entities_closed CHECK ((effective_to_date IS NULL) = (closed_at IS NULL)),
    ADD CONSTRAINT ck_org_unit_legal_entities_superseded CHECK ((superseded_at IS NULL) = (superseding_org_structure_command_id IS NULL));
-- 自然键与重叠约束只管当前有效的行：被作废的行不占生效日，也不挡重叠。
ALTER TABLE organization_workforce.org_unit_legal_entities DROP CONSTRAINT uk_org_unit_legal_entities_effective_from;
CREATE UNIQUE INDEX uk_org_unit_legal_entities_effective_from ON organization_workforce.org_unit_legal_entities (org_unit_id, effective_from_date) WHERE superseded_at IS NULL;
ALTER TABLE organization_workforce.org_unit_legal_entities DROP CONSTRAINT ex_org_unit_legal_entities_overlap;
ALTER TABLE organization_workforce.org_unit_legal_entities
    ADD CONSTRAINT ex_org_unit_legal_entities_overlap EXCLUDE USING gist (org_unit_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL);
CREATE INDEX ix_org_unit_legal_entities_change_org_structure_command_id ON organization_workforce.org_unit_legal_entities (change_org_structure_command_id) WHERE change_org_structure_command_id IS NOT NULL;
CREATE INDEX ix_org_unit_legal_entities_superseding_org_structure_command_id ON organization_workforce.org_unit_legal_entities (superseding_org_structure_command_id) WHERE superseding_org_structure_command_id IS NOT NULL;
CREATE INDEX ix_org_unit_legal_entities_corrected_from_version_id ON organization_workforce.org_unit_legal_entities (corrected_from_version_id) WHERE corrected_from_version_id IS NOT NULL;

COMMENT ON TABLE organization_workforce.org_unit_legal_entities IS '部门法人对照：部门默认归属的法人，子部门继承，可在子树上覆盖；生效区间维度，只追加（开放期间只能被关闭，同一部门同一生效日至多一个当前行；作废只能由空变非空一次，已关闭的期间也可作废）；粒度：每个部门的每个归属期间一行，被更正或补登替换的行保留为作废行';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.change_channel_kind IS '写入渠道：COMMAND 组织结构命令 / MIGRATION 迁入；缺省 MIGRATION，没有命令的写入取缺省，命令写入的行必须显式写 COMMAND，忘写时由命令列的检查约束拒绝';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.change_org_structure_command_id IS '写入本行的组织结构命令 ID；为空表示迁入行';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.version_audit_entry_id IS '写入本行的同一事务里写下的审计条目 ID（跨 schema 只存 ID）；迁入行可空';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.closed_at IS '关闭时刻：写失效日的同时写入，由空变非空一次；为空表示开放期间';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.superseded_at IS '作废时刻：被更正或补登替换的行，由空变非空一次，开放行与已关闭行都可作废；为空表示当前有效的事实';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.superseding_org_structure_command_id IS '作废它的组织结构命令 ID；为空表示未被作废';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.corrected_from_version_id IS '重新追加行的来源：指向被作废的行；为空表示不是重新追加的行';

CREATE FUNCTION organization_workforce.refuse_org_unit_legal_entity_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    column_name text;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'version rows are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['effective_to_date', 'closed_at', 'superseded_at', 'superseding_org_structure_command_id'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_org_structure_command_id' - 'updated_at')
            <> (to_jsonb(OLD) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_org_structure_command_id' - 'updated_at') THEN
        IF OLD.effective_to_date IS NOT NULL THEN
            RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_org_unit_legal_entity_rewrite() IS '部门法人对照只追加的触发器：关闭列与作废列各只放行由空变非空一次，作废开放行与已关闭行都放行，其余列除更新时间外不变，任何行不能删或清空';
CREATE TRIGGER tg_org_unit_legal_entities_append_only BEFORE UPDATE OR DELETE ON organization_workforce.org_unit_legal_entities
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_org_unit_legal_entity_rewrite();
CREATE TRIGGER tg_org_unit_legal_entities_append_only_truncate BEFORE TRUNCATE ON organization_workforce.org_unit_legal_entities
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_org_unit_legal_entity_rewrite();

-- 三、岗位版本（空表：岗位根表保留原列并仍由原路径维护，根表收窄与迁移批次前不回填，也没有写入方）
CREATE TABLE organization_workforce.position_versions (
    position_version_id         uuid        NOT NULL,
    position_id                 uuid        NOT NULL,
    effective_from_date         date        NOT NULL,
    effective_to_date           date,
    revision_number             integer     NOT NULL DEFAULT 0,
    code                        text,
    name                        text        NOT NULL,
    sort_order                  integer,
    status                      text        NOT NULL,
    change_channel_kind         text        NOT NULL,
    change_org_structure_command_id uuid,
    version_audit_entry_id      uuid,
    closed_at                   timestamptz,
    superseded_at               timestamptz,
    superseding_org_structure_command_id uuid,
    corrected_from_version_id   uuid,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_position_versions PRIMARY KEY (position_version_id),
    CONSTRAINT fk_position_versions_position_id FOREIGN KEY (position_id) REFERENCES organization_workforce.positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_position_versions_change_org_structure_command_id FOREIGN KEY (change_org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_position_versions_superseding_org_structure_command_id FOREIGN KEY (superseding_org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_position_versions_corrected_from_version_id FOREIGN KEY (corrected_from_version_id) REFERENCES organization_workforce.position_versions (position_version_id) ON DELETE RESTRICT,
    CONSTRAINT uk_position_versions_revision UNIQUE (position_id, effective_from_date, revision_number),
    CONSTRAINT ck_position_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_position_versions_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    CONSTRAINT ck_position_versions_revision_number CHECK (revision_number >= 0),
    CONSTRAINT ck_position_versions_name CHECK (char_length(name) <= 500 AND btrim(name) <> ''),
    CONSTRAINT ck_position_versions_code CHECK (code IS NULL OR (char_length(code) <= 500 AND btrim(code) <> '')),
    CONSTRAINT ck_position_versions_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_position_versions_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    CONSTRAINT ck_position_versions_command CHECK ((change_channel_kind = 'COMMAND') = (change_org_structure_command_id IS NOT NULL)),
    CONSTRAINT ck_position_versions_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    CONSTRAINT ck_position_versions_closed CHECK ((effective_to_date IS NULL) = (closed_at IS NULL)),
    CONSTRAINT ck_position_versions_superseded CHECK ((superseded_at IS NULL) = (superseding_org_structure_command_id IS NULL)),
    CONSTRAINT ex_position_versions_overlap EXCLUDE USING gist (position_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL),
    CONSTRAINT ex_position_versions_code EXCLUDE USING gist (code WITH =, position_id WITH <>) WHERE (code IS NOT NULL AND superseded_at IS NULL)
);
CREATE INDEX ix_position_versions_change_org_structure_command_id ON organization_workforce.position_versions (change_org_structure_command_id) WHERE change_org_structure_command_id IS NOT NULL;
CREATE INDEX ix_position_versions_superseding_org_structure_command_id ON organization_workforce.position_versions (superseding_org_structure_command_id) WHERE superseding_org_structure_command_id IS NOT NULL;
CREATE INDEX ix_position_versions_corrected_from_version_id ON organization_workforce.position_versions (corrected_from_version_id) WHERE corrected_from_version_id IS NOT NULL;

COMMENT ON TABLE organization_workforce.position_versions IS '岗位版本：岗位的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订；作废只能由空变非空一次）；生效依据为组织业务生效日（DECLARED）；粒度：每个岗位的每个生效区间每个修订一行；现为空表，没有写入方：岗位根表保留原列并仍由原路径维护，直到根表收窄';
COMMENT ON COLUMN organization_workforce.position_versions.position_version_id IS '岗位版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.position_versions.position_id IS '岗位 ID';
COMMENT ON COLUMN organization_workforce.position_versions.effective_from_date IS '生效日（含），按业务时区截日';
COMMENT ON COLUMN organization_workforce.position_versions.effective_to_date IS '失效日（不含）；为空表示至今；等于生效日表示当天被同日再次变化取代的空区间';
COMMENT ON COLUMN organization_workforce.position_versions.revision_number IS '修订序号：同一生效日内的序号，同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';
COMMENT ON COLUMN organization_workforce.position_versions.code IS '岗位编码：全公司唯一且不复用（一个编码只属于一个岗位，作废行除外）；为空表示没有编码';
COMMENT ON COLUMN organization_workforce.position_versions.name IS '岗位名称';
COMMENT ON COLUMN organization_workforce.position_versions.sort_order IS '显示顺序；为空表示不排序';
COMMENT ON COLUMN organization_workforce.position_versions.status IS '状态：ACTIVE 启用 / DISABLED 停用';
COMMENT ON COLUMN organization_workforce.position_versions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.position_versions.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.position_versions.change_channel_kind IS '写入渠道：COMMAND 组织结构命令 / MIGRATION 迁入；必填，命令写入的行写 COMMAND、迁入的行写 MIGRATION，两者与命令列一并由检查约束核对';
COMMENT ON COLUMN organization_workforce.position_versions.change_org_structure_command_id IS '写入本行的组织结构命令 ID；为空表示迁入行';
COMMENT ON COLUMN organization_workforce.position_versions.version_audit_entry_id IS '写入本行的同一事务里写下的审计条目 ID（跨 schema 只存 ID）；迁入行可空';
COMMENT ON COLUMN organization_workforce.position_versions.closed_at IS '关闭时刻：写失效日的同时写入，由空变非空一次；为空表示开放版本';
COMMENT ON COLUMN organization_workforce.position_versions.superseded_at IS '作废时刻：被更正或补登替换的行，由空变非空一次，开放行与已关闭行都可作废；为空表示当前有效的事实';
COMMENT ON COLUMN organization_workforce.position_versions.superseding_org_structure_command_id IS '作废它的组织结构命令 ID；为空表示未被作废';
COMMENT ON COLUMN organization_workforce.position_versions.corrected_from_version_id IS '重新追加行的来源：指向被作废的行；为空表示不是重新追加的行';

CREATE FUNCTION organization_workforce.refuse_position_version_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    column_name text;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'version rows are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['effective_to_date', 'closed_at', 'superseded_at', 'superseding_org_structure_command_id'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_org_structure_command_id' - 'updated_at')
            <> (to_jsonb(OLD) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_org_structure_command_id' - 'updated_at') THEN
        IF OLD.effective_to_date IS NOT NULL THEN
            RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_position_version_rewrite() IS '岗位版本只追加的触发器：关闭列与作废列各只放行由空变非空一次，作废开放行与已关闭行都放行，其余列除更新时间外不变，任何行不能删或清空';
CREATE TRIGGER tg_position_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.position_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_position_version_rewrite();
CREATE TRIGGER tg_position_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.position_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_position_version_rewrite();
