-- IMP-1022-PAR-03（#711，one-design 签发 #711 评论修订 2 及用户答复「1a 2a 3a」）：任职版本化，主任职不重叠、兼职不限。
-- 本迁移只改表结构与约束：任职根表、版本与人事命令对任职根的引用、新增兼职与结束兼职两种命令。应用代码只补主任职根的写入（保证既有写入方在新约束下照常写入）。

-- 1. 任职根：每个员工一条主任职根（终身一条），兼职每个一条；只追加。
CREATE TABLE organization_workforce.employee_assignments (
    assignment_id   uuid        NOT NULL,
    employee_id     uuid        NOT NULL,
    assignment_kind text        NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT pk_employee_assignments PRIMARY KEY (assignment_id),
    CONSTRAINT uk_employee_assignments_identity UNIQUE (assignment_id, employee_id, assignment_kind),
    CONSTRAINT fk_employee_assignments_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT ck_employee_assignments_assignment_kind CHECK (assignment_kind IN ('PRIMARY', 'CONCURRENT'))
);
CREATE UNIQUE INDEX uk_employee_assignments_primary ON organization_workforce.employee_assignments (employee_id) WHERE assignment_kind = 'PRIMARY';
CREATE INDEX ix_employee_assignments_employee_id ON organization_workforce.employee_assignments (employee_id);
COMMENT ON TABLE organization_workforce.employee_assignments IS '任职根：员工的一个任职（主任职或兼职），版本与人事命令都按它归属；主任职每个员工终身一条，兼职不限；只追加；粒度：每个员工的每个任职一行';
COMMENT ON COLUMN organization_workforce.employee_assignments.assignment_id IS '任职 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.employee_assignments.employee_id IS '员工 ID';
COMMENT ON COLUMN organization_workforce.employee_assignments.assignment_kind IS '任职类别：PRIMARY 主任职 / CONCURRENT 兼职；复合外键把它抄到版本与命令上，使检查约束与部分唯一索引能直接判断';
COMMENT ON COLUMN organization_workforce.employee_assignments.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.employee_assignments.updated_at IS '更新时间';

CREATE FUNCTION organization_workforce.refuse_employee_assignment_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'employee assignments are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'employee assignments are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'employee assignments are append-only: update on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_employee_assignment_rewrite() IS '任职根只追加的触发器：任何 UPDATE 拒绝，DELETE 只放行表属主（清除通道随 W2 第 8 节另批），TRUNCATE 拒绝';
CREATE TRIGGER tg_employee_assignments_append_only BEFORE UPDATE OR DELETE ON organization_workforce.employee_assignments
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_employee_assignment_rewrite();
CREATE TRIGGER tg_employee_assignments_append_only_truncate BEFORE TRUNCATE ON organization_workforce.employee_assignments
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_employee_assignment_rewrite();

-- 2. 已有数据：有任职版本或人事命令的员工各补一条主任职根；补写列要在只追加触发器拆掉之后、重建之前，触发器不放行这些列的改动。
INSERT INTO organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)
SELECT uuidv7(), e.employee_id, 'PRIMARY', now(), now()
  FROM (SELECT employee_id FROM organization_workforce.assignment_versions UNION SELECT employee_id FROM organization_workforce.personnel_commands) e;

ALTER TABLE organization_workforce.assignment_versions ADD COLUMN assignment_id uuid, ADD COLUMN assignment_kind text;
ALTER TABLE organization_workforce.personnel_commands ADD COLUMN target_assignment_id uuid, ADD COLUMN target_assignment_kind text;
DROP TRIGGER tg_assignment_versions_append_only ON organization_workforce.assignment_versions;
DROP TRIGGER tg_assignment_versions_append_only_truncate ON organization_workforce.assignment_versions;
DROP TRIGGER tg_personnel_commands_append_only ON organization_workforce.personnel_commands;
DROP TRIGGER tg_personnel_commands_append_only_truncate ON organization_workforce.personnel_commands;
UPDATE organization_workforce.assignment_versions v SET assignment_id = r.assignment_id, assignment_kind = r.assignment_kind
  FROM organization_workforce.employee_assignments r WHERE r.employee_id = v.employee_id AND r.assignment_kind = 'PRIMARY';
UPDATE organization_workforce.personnel_commands c SET target_assignment_id = r.assignment_id, target_assignment_kind = r.assignment_kind
  FROM organization_workforce.employee_assignments r
 WHERE r.employee_id = c.employee_id AND r.assignment_kind = 'PRIMARY'
   AND (c.command_kind = 'TRANSFER' OR (c.command_kind = 'CORRECTION' AND num_nonnulls(c.target_org_unit_id, c.target_position_id, c.target_reports_to_employee_id) > 0));
CREATE TRIGGER tg_assignment_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.assignment_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_assignment_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.assignment_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_assignment_version_rewrite();
CREATE TRIGGER tg_personnel_commands_append_only BEFORE UPDATE OR DELETE ON organization_workforce.personnel_commands
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_personnel_command_rewrite();
CREATE TRIGGER tg_personnel_commands_append_only_truncate BEFORE TRUNCATE ON organization_workforce.personnel_commands
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_personnel_command_rewrite();

-- 3. 任职版本：挂在任职根上；同一任职内不重叠（主任职只有一条根，正是 INV-62），兼职之间与对主任职都可并行；兼职没有汇报线；更正留在自己的任职上。
ALTER TABLE organization_workforce.assignment_versions
    ALTER COLUMN assignment_id SET NOT NULL,
    ALTER COLUMN assignment_kind SET NOT NULL,
    ADD CONSTRAINT fk_assignment_versions_assignment FOREIGN KEY (assignment_id, employee_id, assignment_kind)
        REFERENCES organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_assignment_versions_assignment_kind CHECK (assignment_kind IN ('PRIMARY', 'CONCURRENT')),
    ADD CONSTRAINT ck_assignment_versions_concurrent_reports_to CHECK (assignment_kind = 'PRIMARY' OR reports_to_employee_id IS NULL),
    ADD CONSTRAINT uk_assignment_versions_id_assignment UNIQUE (assignment_version_id, assignment_id);
ALTER TABLE organization_workforce.assignment_versions DROP CONSTRAINT ex_assignment_versions_overlap;
ALTER TABLE organization_workforce.assignment_versions
    ADD CONSTRAINT ex_assignment_versions_overlap EXCLUDE USING gist (assignment_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL);
ALTER TABLE organization_workforce.assignment_versions DROP CONSTRAINT fk_assignment_versions_corrected_from_version_id;
ALTER TABLE organization_workforce.assignment_versions
    ADD CONSTRAINT fk_assignment_versions_corrected_from FOREIGN KEY (corrected_from_version_id, assignment_id)
        REFERENCES organization_workforce.assignment_versions (assignment_version_id, assignment_id) ON DELETE RESTRICT;
DROP INDEX organization_workforce.ix_assignment_versions_corrected_from_version_id;
CREATE INDEX ix_assignment_versions_corrected_from ON organization_workforce.assignment_versions (corrected_from_version_id, assignment_id) WHERE corrected_from_version_id IS NOT NULL;
CREATE INDEX ix_assignment_versions_assignment_id ON organization_workforce.assignment_versions (assignment_id, employee_id, assignment_kind);
COMMENT ON TABLE organization_workforce.assignment_versions IS '任职版本：员工一个任职（主任职或兼职）所在部门、岗位与汇报线的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订；作废只能由空变非空一次）；生效依据为人事业务生效日（DECLARED）；粒度：每个任职的每个生效区间每个修订一行；主任职内不重叠，兼职可与任何任职并行；与快照在同一事务写入';
COMMENT ON COLUMN organization_workforce.assignment_versions.assignment_id IS '任职 ID：本版本所属的任职根（employee_assignments）；更正重新追加的行与被更正的行属于同一任职';
COMMENT ON COLUMN organization_workforce.assignment_versions.assignment_kind IS '任职类别：抄自任职根，由复合外键保证相等；兼职版本没有直属领导（检查约束 ck_assignment_versions_concurrent_reports_to）';
COMMENT ON COLUMN organization_workforce.assignment_versions.reports_to_employee_id IS '直属领导（员工 ID）；只有主任职有汇报线，兼职版本必为空';

-- 4. 人事命令：命令作用的任职、新增兼职与结束兼职两种命令、按人或主任职与按兼职分别一天一条未生效命令、合并按任职各产生一条调岗。
ALTER TABLE organization_workforce.personnel_commands
    ADD CONSTRAINT fk_personnel_commands_target_assignment FOREIGN KEY (target_assignment_id, employee_id, target_assignment_kind)
        REFERENCES organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_personnel_commands_target_assignment_kind CHECK (target_assignment_kind IN ('PRIMARY', 'CONCURRENT')),
    ADD CONSTRAINT ck_personnel_commands_target_assignment_pair CHECK ((target_assignment_id IS NULL) = (target_assignment_kind IS NULL));
CREATE INDEX ix_personnel_commands_target_assignment ON organization_workforce.personnel_commands (target_assignment_id, employee_id, target_assignment_kind) WHERE target_assignment_id IS NOT NULL;

ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_command_kind;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_command_kind CHECK (command_kind IN
    ('HIRE', 'REGULARIZE', 'TRANSFER', 'SEPARATE', 'REINSTATE', 'CORRECTION', 'UNPAID_LEAVE_START', 'UNPAID_LEAVE_END', 'CHANGE_LEGAL_ENTITY', 'START_CONCURRENT', 'END_CONCURRENT'));
-- 目标任职：调岗必填；更正带任职列时必填、不带时必空；结束兼职必填且指向兼职根；其余种类（含新增兼职，它自己建根）不带。
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_target_assignment CHECK (
    CASE
        WHEN command_kind = 'TRANSFER' THEN target_assignment_id IS NOT NULL
        WHEN command_kind = 'CORRECTION' THEN (target_assignment_id IS NOT NULL) = (num_nonnulls(target_org_unit_id, target_position_id, target_reports_to_employee_id) > 0)
        WHEN command_kind = 'END_CONCURRENT' THEN target_assignment_id IS NOT NULL AND target_assignment_kind = 'CONCURRENT'
        ELSE target_assignment_id IS NULL
    END);
-- 兼职没有汇报线与收费员工标记；新增兼职要部门与岗位；结束兼职不带别的目标列。
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_concurrent_columns CHECK (
    CASE
        WHEN command_kind = 'START_CONCURRENT' THEN target_org_unit_id IS NOT NULL AND target_position_id IS NOT NULL
            AND num_nonnulls(target_reports_to_employee_id, is_target_fee_earner, target_employment_phase_kind, is_target_on_unpaid_leave) = 0
        WHEN command_kind = 'END_CONCURRENT' THEN num_nonnulls(target_org_unit_id, target_position_id, target_reports_to_employee_id, is_target_fee_earner,
            target_employment_phase_kind, is_target_on_unpaid_leave) = 0
        WHEN command_kind IN ('TRANSFER', 'CORRECTION') AND target_assignment_kind = 'CONCURRENT' THEN num_nonnulls(target_reports_to_employee_id, is_target_fee_earner) = 0
        ELSE true
    END);

DROP INDEX organization_workforce.uk_personnel_commands_open_per_day;
CREATE UNIQUE INDEX uk_personnel_commands_open_per_day ON organization_workforce.personnel_commands (employee_id, effective_date)
    WHERE status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN') AND command_kind <> 'START_CONCURRENT' AND target_assignment_kind IS DISTINCT FROM 'CONCURRENT';
CREATE UNIQUE INDEX uk_personnel_commands_open_per_assignment_day ON organization_workforce.personnel_commands (target_assignment_id, effective_date)
    WHERE status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN') AND target_assignment_kind = 'CONCURRENT';
DROP INDEX organization_workforce.uk_personnel_commands_merge_employee;
CREATE UNIQUE INDEX uk_personnel_commands_merge_assignment ON organization_workforce.personnel_commands (org_structure_command_id, target_assignment_id)
    WHERE org_structure_command_id IS NOT NULL;

COMMENT ON COLUMN organization_workforce.personnel_commands.command_kind IS '命令种类：HIRE 入职 / REGULARIZE 转正 / TRANSFER 调岗 / SEPARATE 离职 / REINSTATE 复职 / CORRECTION 更正 / UNPAID_LEAVE_START 停薪留职开始 / UNPAID_LEAVE_END 停薪留职结束 / CHANGE_LEGAL_ENTITY 变更法人归属 / START_CONCURRENT 新增兼职 / END_CONCURRENT 结束兼职';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_assignment_id IS '目标任职 ID：命令作用的任职根；调岗必填，更正带任职列时必填，结束兼职必填；新增兼职由命令自己建根，其余种类为空';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_assignment_kind IS '目标任职类别：抄自任职根，由复合外键保证相等，与目标任职 ID 同空同非空；按兼职计一天一条未生效命令的唯一索引据此判断';
