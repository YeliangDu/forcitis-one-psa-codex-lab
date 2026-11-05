-- IMP-1372（雇佣关系建表，ADR-0086；数据设计见雇佣关系设计文档附录 A，评审记录见 #1308）。
-- 本迁移只改表结构与已有行：新增雇佣关系表；员工版本表改名为雇佣关系版本表并加关系 ID；任职版本与人事命令各加关系 ID；同日一条的唯一索引改按关系计。
-- 已有行：每个在版本表、任职版本表或（关系绑定的）人事命令里有行的员工回填一条雇佣关系，用人单位取该员工最早一行生效日当天的有效法人归属，来源 MIGRATION_ATTRIBUTION、未确认。
-- 取不到归属的员工不回填，整体失败并报聚合数（不猜用人单位）。三张只追加表补写关系 ID 要在只追加触发器拆掉之后、重建之前（同 V20261103130000 第 2 节）。

-- 1. 雇佣关系：员工聚合内的实体；除确认回填外只追加。
CREATE TABLE organization_workforce.employment_relationships (
    employment_relationship_id  uuid        NOT NULL,
    employee_id                 uuid        NOT NULL,
    employer_legal_entity_id    uuid        NOT NULL,
    predecessor_relationship_id uuid,
    employer_source_kind        text        NOT NULL,
    employer_confirmed_at       timestamptz,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    CONSTRAINT pk_employment_relationships PRIMARY KEY (employment_relationship_id),
    CONSTRAINT uk_employment_relationships_employee_employer UNIQUE (employee_id, employer_legal_entity_id),
    CONSTRAINT uk_employment_relationships_id_employee UNIQUE (employment_relationship_id, employee_id),
    CONSTRAINT fk_employment_relationships_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_employment_relationships_employer_legal_entity_id FOREIGN KEY (employer_legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT,
    CONSTRAINT fk_employment_relationships_predecessor FOREIGN KEY (predecessor_relationship_id, employee_id)
        REFERENCES organization_workforce.employment_relationships (employment_relationship_id, employee_id) ON DELETE RESTRICT,
    CONSTRAINT ck_employment_relationships_predecessor_not_self CHECK (predecessor_relationship_id <> employment_relationship_id),
    CONSTRAINT ck_employment_relationships_employer_source_kind CHECK (employer_source_kind IN ('DECLARED', 'MIGRATION_ATTRIBUTION')),
    CONSTRAINT ck_employment_relationships_confirmed_only_migration CHECK (employer_source_kind = 'MIGRATION_ATTRIBUTION' OR employer_confirmed_at IS NULL)
);
CREATE INDEX ix_employment_relationships_employer_legal_entity_id ON organization_workforce.employment_relationships (employer_legal_entity_id);
CREATE INDEX ix_employment_relationships_predecessor ON organization_workforce.employment_relationships (predecessor_relationship_id, employee_id)
    WHERE predecessor_relationship_id IS NOT NULL;
COMMENT ON TABLE organization_workforce.employment_relationships IS '雇佣关系：一个自然人与一个用人单位之间的在职关系，员工聚合内的实体；终身不删；除迁入回填行的一次确认外只追加；粒度：每个员工与每个用人单位一行';
COMMENT ON COLUMN organization_workforce.employment_relationships.employment_relationship_id IS '雇佣关系 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.employment_relationships.employee_id IS '员工 ID';
COMMENT ON COLUMN organization_workforce.employment_relationships.employer_legal_entity_id IS '用人单位（法人 ID），决定社保公积金与请假口径；建立后不变';
COMMENT ON COLUMN organization_workforce.employment_relationships.predecessor_relationship_id IS '前任雇佣关系：为空表示非连续任职换用人单位产生';
COMMENT ON COLUMN organization_workforce.employment_relationships.employer_source_kind IS '用人单位来源：DECLARED 人事申报 / MIGRATION_ATTRIBUTION 迁入按归属法人回填、待人事确认';
COMMENT ON COLUMN organization_workforce.employment_relationships.employer_confirmed_at IS '用人单位确认时刻：为空表示回填行尚未确认或非回填行；回填行确认后置值，之后冻结；确认不改用人单位';
COMMENT ON COLUMN organization_workforce.employment_relationships.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.employment_relationships.updated_at IS '最近一次变更时间；行除确认回填雇主外不可改，故等于创建时间，确认时与 employer_confirmed_at 一起写（设计附录 A 之外加的列，lead 裁定）';
-- 与附录 A 的偏差：附录 A 只列 created_at；DdlNamingTest D2 要求每表都有 updated_at，经 lead 裁定加列（设计将在下一份设计 PR 修订附录 A）。


-- 只追加：UPDATE 只放行一种，迁入回填行把确认时刻由空变非空一次，其余列逐列不变；DELETE 只放行表属主（离职满 10 年的清除函数，ADR-0068 第 7 项）；TRUNCATE 拒绝。
CREATE FUNCTION organization_workforce.refuse_employment_relationship_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'employment relationships are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'employment relationships are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.employer_source_kind = 'MIGRATION_ATTRIBUTION' AND OLD.employer_confirmed_at IS NULL AND NEW.employer_confirmed_at IS NOT NULL
            AND NEW.updated_at IS NOT NULL AND NEW.updated_at >= OLD.updated_at
            AND (to_jsonb(NEW) - 'employer_confirmed_at' - 'updated_at') = (to_jsonb(OLD) - 'employer_confirmed_at' - 'updated_at') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'employment relationships are append-only: only the one-time confirmation of a migrated row can change %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_employment_relationship_rewrite() IS '雇佣关系只追加的触发器：只放行迁入回填行确认时刻由空变非空一次且其余列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_employment_relationships_append_only BEFORE UPDATE OR DELETE ON organization_workforce.employment_relationships
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_employment_relationship_rewrite();
CREATE TRIGGER tg_employment_relationships_append_only_truncate BEFORE TRUNCATE ON organization_workforce.employment_relationships
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_employment_relationship_rewrite();

-- 2. 已有数据：每个在员工版本、任职版本或关系绑定命令里有行的员工回填一条关系。用人单位 = 最早一行生效日当天的有效法人归属：
--    先看员工自己的归属，否则沿任职部门向上找最近的部门归属（与 LegalEntityResolution 同一规则，更正后的终止日以最新更正为准）。
DO $$
DECLARE
    r record;
    employer uuid;
    unit uuid;
    seen uuid[];
    rejected integer := 0;
BEGIN
    FOR r IN
        SELECT employee_id, min(first_date) AS first_date FROM (
            SELECT employee_id, effective_from_date AS first_date FROM organization_workforce.employee_versions
            UNION ALL SELECT employee_id, effective_from_date FROM organization_workforce.assignment_versions
            UNION ALL SELECT employee_id, effective_date FROM organization_workforce.personnel_commands WHERE command_kind <> 'CHANGE_LEGAL_ENTITY'
        ) rows GROUP BY employee_id
    LOOP
        SELECT p.legal_entity_id INTO employer
          FROM organization_workforce.employee_legal_entities p
          LEFT JOIN LATERAL (SELECT new_effective_to_date FROM organization_workforce.legal_entity_attribution_corrections
                              WHERE employee_id = p.employee_id AND period_effective_from_date = p.effective_from_date
                              ORDER BY correction_number DESC LIMIT 1) c ON true
         WHERE p.employee_id = r.employee_id AND p.superseded_at IS NULL AND p.effective_from_date <= r.first_date
           AND (coalesce(c.new_effective_to_date, p.effective_to_date) IS NULL OR r.first_date < coalesce(c.new_effective_to_date, p.effective_to_date));
        IF employer IS NULL THEN
            SELECT org_unit_id INTO unit FROM organization_workforce.assignment_versions
             WHERE employee_id = r.employee_id AND assignment_kind = 'PRIMARY' AND superseded_at IS NULL AND effective_from_date <= r.first_date
               AND (effective_to_date IS NULL OR r.first_date < effective_to_date);
            seen := ARRAY[]::uuid[];
            WHILE employer IS NULL AND unit IS NOT NULL AND NOT (unit = ANY (seen)) LOOP
                seen := seen || unit;
                SELECT p.legal_entity_id INTO employer
                  FROM organization_workforce.org_unit_legal_entities p
                  LEFT JOIN LATERAL (SELECT new_effective_to_date FROM organization_workforce.legal_entity_attribution_corrections
                                      WHERE org_unit_id = p.org_unit_id AND period_effective_from_date = p.effective_from_date
                                      ORDER BY correction_number DESC LIMIT 1) c ON true
                 WHERE p.org_unit_id = unit AND p.superseded_at IS NULL AND p.effective_from_date <= r.first_date
                   AND (coalesce(c.new_effective_to_date, p.effective_to_date) IS NULL OR r.first_date < coalesce(c.new_effective_to_date, p.effective_to_date));
                IF employer IS NULL THEN
                    SELECT parent_org_unit_id INTO unit FROM organization_workforce.org_unit_versions
                     WHERE org_unit_id = unit AND superseded_at IS NULL AND effective_from_date <= r.first_date
                       AND (effective_to_date IS NULL OR r.first_date < effective_to_date) AND parent_org_unit_id IS NOT NULL;
                END IF;
            END LOOP;
        END IF;
        IF employer IS NULL THEN
            rejected := rejected + 1;
        ELSE
            INSERT INTO organization_workforce.employment_relationships
                (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, created_at, updated_at)
            VALUES (uuidv7(), r.employee_id, employer, 'MIGRATION_ATTRIBUTION', now(), now());
        END IF;
        employer := NULL;
        unit := NULL;
    END LOOP;
    IF rejected > 0 THEN
        RAISE EXCEPTION 'employment relationship backfill: % employee(s) have no legal entity attribution on their first version date; attribute them first (no employer is guessed)', rejected;
    END IF;
END $$;

-- 3. 员工版本表改名为雇佣关系版本表，对象一并改名（索引 ix_..._separated_effective_from_date 改名后超 63 字节，改为 ..._separated_from_date）。
ALTER TABLE organization_workforce.employee_versions RENAME TO employment_relationship_versions;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME COLUMN employee_version_id TO employment_relationship_version_id;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT pk_employee_versions TO pk_employment_relationship_versions;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT fk_employee_versions_employee_id TO fk_employment_relationship_versions_employee_id;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT fk_employee_versions_change_command_id TO fk_employment_relationship_versions_change_command_id;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT fk_employee_versions_superseding_command_id TO fk_employment_relationship_versions_superseding_command_id;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT uk_employee_versions_revision TO uk_employment_relationship_versions_revision;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ex_employee_versions_overlap TO ex_employment_relationship_versions_overlap;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_range TO ck_employment_relationship_versions_range;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_finite TO ck_employment_relationship_versions_finite;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_employment_status TO ck_employment_relationship_versions_employment_status;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_employment_phase_kind TO ck_employment_relationship_versions_employment_phase_kind;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_phase_status TO ck_employment_relationship_versions_phase_status;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_leave_employed TO ck_employment_relationship_versions_leave_employed;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_revision_number TO ck_employment_relationship_versions_revision_number;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_change_channel_kind TO ck_employment_relationship_versions_change_channel_kind;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_command TO ck_employment_relationship_versions_command;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_audit TO ck_employment_relationship_versions_audit;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_closed TO ck_employment_relationship_versions_closed;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_superseded TO ck_employment_relationship_versions_superseded;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_employment_start_basis_kind TO ck_employment_relationship_versions_employment_start_basis_kind;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_employment_start_employed TO ck_employment_relationship_versions_employment_start_employed;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_employment_start_known TO ck_employment_relationship_versions_employment_start_known;
ALTER TABLE organization_workforce.employment_relationship_versions RENAME CONSTRAINT ck_employee_versions_employment_start_not_after TO ck_employment_relationship_versions_employment_start_not_after;
ALTER INDEX organization_workforce.ix_employee_versions_change_command_id RENAME TO ix_employment_relationship_versions_change_command_id;
ALTER INDEX organization_workforce.ix_employee_versions_superseding_command_id RENAME TO ix_employment_relationship_versions_superseding_command_id;
ALTER INDEX organization_workforce.ix_employee_versions_separated_effective_from_date RENAME TO ix_employment_relationship_versions_separated_from_date;
ALTER INDEX organization_workforce.ix_employee_versions_employment_start_month_day RENAME TO ix_employment_relationship_versions_employment_start_month_day;
ALTER FUNCTION organization_workforce.refuse_employee_version_rewrite() RENAME TO refuse_employment_relationship_version_rewrite;
ALTER TRIGGER tg_employee_versions_append_only ON organization_workforce.employment_relationship_versions RENAME TO tg_employment_relationship_versions_append_only;
ALTER TRIGGER tg_employee_versions_append_only_truncate ON organization_workforce.employment_relationship_versions RENAME TO tg_employment_relationship_versions_append_only_truncate;
COMMENT ON FUNCTION organization_workforce.refuse_employment_relationship_version_rewrite() IS '雇佣关系版本只追加的触发器：关闭列与作废列各只放行由空变非空一次，其余列不变，DELETE 只放行表属主，TRUNCATE 拒绝';

-- 4. 三张表加关系 ID（先可空）；补写要在只追加触发器拆掉之后、重建之前。
ALTER TABLE organization_workforce.employment_relationship_versions ADD COLUMN employment_relationship_id uuid;
ALTER TABLE organization_workforce.assignment_versions ADD COLUMN employment_relationship_id uuid;
ALTER TABLE organization_workforce.personnel_commands ADD COLUMN employment_relationship_id uuid;
DROP TRIGGER tg_employment_relationship_versions_append_only ON organization_workforce.employment_relationship_versions;
DROP TRIGGER tg_employment_relationship_versions_append_only_truncate ON organization_workforce.employment_relationship_versions;
DROP TRIGGER tg_assignment_versions_append_only ON organization_workforce.assignment_versions;
DROP TRIGGER tg_assignment_versions_append_only_truncate ON organization_workforce.assignment_versions;
DROP TRIGGER tg_personnel_commands_append_only ON organization_workforce.personnel_commands;
DROP TRIGGER tg_personnel_commands_append_only_truncate ON organization_workforce.personnel_commands;
UPDATE organization_workforce.employment_relationship_versions v SET employment_relationship_id = r.employment_relationship_id
  FROM organization_workforce.employment_relationships r WHERE r.employee_id = v.employee_id;
UPDATE organization_workforce.assignment_versions v SET employment_relationship_id = r.employment_relationship_id
  FROM organization_workforce.employment_relationships r WHERE r.employee_id = v.employee_id;
UPDATE organization_workforce.personnel_commands c SET employment_relationship_id = r.employment_relationship_id
  FROM organization_workforce.employment_relationships r WHERE r.employee_id = c.employee_id AND c.command_kind <> 'CHANGE_LEGAL_ENTITY';
CREATE TRIGGER tg_employment_relationship_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.employment_relationship_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_employment_relationship_version_rewrite();
CREATE TRIGGER tg_employment_relationship_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.employment_relationship_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_employment_relationship_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.assignment_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_assignment_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.assignment_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_assignment_version_rewrite();
CREATE TRIGGER tg_personnel_commands_append_only BEFORE UPDATE OR DELETE ON organization_workforce.personnel_commands
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_personnel_command_rewrite();
CREATE TRIGGER tg_personnel_commands_append_only_truncate BEFORE TRUNCATE ON organization_workforce.personnel_commands
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_personnel_command_rewrite();

DO $$
DECLARE
    missing_versions integer;
    missing_assignment_versions integer;
    missing_commands integer;
BEGIN
    SELECT count(*) INTO missing_versions FROM organization_workforce.employment_relationship_versions WHERE employment_relationship_id IS NULL;
    SELECT count(*) INTO missing_assignment_versions FROM organization_workforce.assignment_versions WHERE employment_relationship_id IS NULL;
    SELECT count(*) INTO missing_commands FROM organization_workforce.personnel_commands WHERE employment_relationship_id IS NULL AND command_kind <> 'CHANGE_LEGAL_ENTITY';
    IF missing_versions + missing_assignment_versions + missing_commands > 0 THEN
        RAISE EXCEPTION 'employment relationship backfill left rows without a relationship: % version(s), % assignment version(s), % command(s)',
            missing_versions, missing_assignment_versions, missing_commands;
    END IF;
END $$;

-- 5. 雇佣关系版本表：关系 ID 必填，复合外键保证同一员工；排他约束与修订号唯一键改按关系；更正留在本关系。
ALTER TABLE organization_workforce.employment_relationship_versions
    ALTER COLUMN employment_relationship_id SET NOT NULL,
    ADD CONSTRAINT fk_employment_relationship_versions_relationship FOREIGN KEY (employment_relationship_id, employee_id)
        REFERENCES organization_workforce.employment_relationships (employment_relationship_id, employee_id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_employment_relationship_versions_id_relationship UNIQUE (employment_relationship_version_id, employment_relationship_id);
ALTER TABLE organization_workforce.employment_relationship_versions DROP CONSTRAINT ex_employment_relationship_versions_overlap;
ALTER TABLE organization_workforce.employment_relationship_versions
    ADD CONSTRAINT ex_employment_relationship_versions_overlap EXCLUDE USING gist (employment_relationship_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL);
ALTER TABLE organization_workforce.employment_relationship_versions DROP CONSTRAINT uk_employment_relationship_versions_revision;
ALTER TABLE organization_workforce.employment_relationship_versions
    ADD CONSTRAINT uk_employment_relationship_versions_revision UNIQUE (employment_relationship_id, effective_from_date, revision_number);
ALTER TABLE organization_workforce.employment_relationship_versions DROP CONSTRAINT fk_employee_versions_corrected_from_version_id;
ALTER TABLE organization_workforce.employment_relationship_versions
    ADD CONSTRAINT fk_employment_relationship_versions_corrected_from FOREIGN KEY (corrected_from_version_id, employment_relationship_id)
        REFERENCES organization_workforce.employment_relationship_versions (employment_relationship_version_id, employment_relationship_id) ON DELETE RESTRICT;
DROP INDEX organization_workforce.ix_employee_versions_corrected_from_version_id;
CREATE INDEX ix_employment_relationship_versions_corrected_from ON organization_workforce.employment_relationship_versions (corrected_from_version_id, employment_relationship_id)
    WHERE corrected_from_version_id IS NOT NULL;
CREATE INDEX ix_employment_relationship_versions_employee ON organization_workforce.employment_relationship_versions (employee_id, effective_from_date) WHERE superseded_at IS NULL;
CREATE INDEX ix_employment_relationship_versions_relationship_employee ON organization_workforce.employment_relationship_versions (employment_relationship_id, employee_id);
COMMENT ON TABLE organization_workforce.employment_relationship_versions IS '雇佣关系版本：一段雇佣关系的在职状态、雇佣阶段、停薪留职、收费员工与入职日期的变化历史，只追加（关闭、作废只能由空变非空一次）；生效依据为人事业务生效日（DECLARED）；粒度：每个雇佣关系的每个生效区间每个修订一行';
COMMENT ON COLUMN organization_workforce.employment_relationship_versions.employment_relationship_version_id IS '雇佣关系版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.employment_relationship_versions.employment_relationship_id IS '雇佣关系：所属雇佣关系，复合外键保证同一员工';
COMMENT ON COLUMN organization_workforce.employment_relationship_versions.corrected_from_version_id IS '重新追加行的来源：指向被作废的行，复合外键保证留在同一雇佣关系内';

-- 6. 任职版本：关系 ID 在版本上而不在任职根上（主任职终身一根，要能随版本换到另一段关系）。
ALTER TABLE organization_workforce.assignment_versions
    ALTER COLUMN employment_relationship_id SET NOT NULL,
    ADD CONSTRAINT fk_assignment_versions_relationship FOREIGN KEY (employment_relationship_id, employee_id)
        REFERENCES organization_workforce.employment_relationships (employment_relationship_id, employee_id) ON DELETE RESTRICT;
CREATE INDEX ix_assignment_versions_relationship ON organization_workforce.assignment_versions (employment_relationship_id, employee_id);
COMMENT ON COLUMN organization_workforce.assignment_versions.employment_relationship_id IS '雇佣关系：本任职版本所属的雇佣关系；主任职换关系时追加新版本，同一任职根不变';

-- 7. 人事命令：关系 ID 可空，按种类约束；入职与复职的部门、岗位同有或同无；同日一条改按关系计。
ALTER TABLE organization_workforce.personnel_commands
    ADD CONSTRAINT fk_personnel_commands_relationship FOREIGN KEY (employment_relationship_id, employee_id)
        REFERENCES organization_workforce.employment_relationships (employment_relationship_id, employee_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_personnel_commands_employment_relationship CHECK ((employment_relationship_id IS NULL) = (command_kind = 'CHANGE_LEGAL_ENTITY'));
CREATE INDEX ix_personnel_commands_relationship ON organization_workforce.personnel_commands (employment_relationship_id, employee_id) WHERE employment_relationship_id IS NOT NULL;
ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_hire_columns;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_hire_columns CHECK (command_kind <> 'HIRE' OR (
    (target_org_unit_id IS NULL) = (target_position_id IS NULL) AND is_target_fee_earner IS NOT NULL AND target_employment_phase_kind IS NOT NULL));
ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_reinstate_columns;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_reinstate_columns CHECK (command_kind <> 'REINSTATE' OR (
    (target_org_unit_id IS NULL) = (target_position_id IS NULL) AND is_target_fee_earner IS NOT NULL AND target_employment_phase_kind IS NOT NULL));

DROP INDEX organization_workforce.uk_personnel_commands_open_per_day;
CREATE UNIQUE INDEX uk_personnel_commands_open_per_relationship_day ON organization_workforce.personnel_commands (employment_relationship_id, effective_date)
    WHERE status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN') AND employment_relationship_id IS NOT NULL
        AND command_kind <> 'START_CONCURRENT' AND target_assignment_kind IS DISTINCT FROM 'CONCURRENT';
CREATE UNIQUE INDEX uk_personnel_commands_open_person_day ON organization_workforce.personnel_commands (employee_id, effective_date)
    WHERE status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN') AND employment_relationship_id IS NULL;
DROP INDEX organization_workforce.uk_personnel_commands_open_per_assignment_day;
CREATE UNIQUE INDEX uk_personnel_commands_open_per_assignment_day ON organization_workforce.personnel_commands (target_assignment_id, effective_date)
    WHERE status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN') AND target_assignment_id IS NOT NULL;
COMMENT ON COLUMN organization_workforce.personnel_commands.employment_relationship_id IS '雇佣关系：命令所属的雇佣关系；为空表示人员层命令（变更法人归属），其余种类必填';
