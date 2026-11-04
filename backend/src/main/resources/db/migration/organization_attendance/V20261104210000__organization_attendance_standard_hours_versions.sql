-- 标准工时版本（AGG-108，#1310；one-design 数据设计审查记录 1310，改动 C1-C6 与 Q1-Q5 裁决）：
-- 版本表 + 日期覆盖表。标准工时没有默认值（INV-116）：本迁移不写任何数据，数值由用户经发布命令录入。
-- 版本只追加；唯一允许的更新是关闭上一版本（空改成日期，由发布下一版本的触发器完成）、重开被作废版本的上一版本、作废未来版本。

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: the database setup script (one_db_roles_schemas.sql) creates it before the migrations run';
    END IF;
END $$;

CREATE TABLE organization_attendance.standard_hours_versions (
    id                         uuid        NOT NULL,
    scope_kind                 text        NOT NULL CONSTRAINT ck_standard_hours_versions_scope_kind CHECK (scope_kind IN ('DEFAULT', 'LEGAL_ENTITY')),
    legal_entity_id            uuid,
    version_number             integer     NOT NULL CONSTRAINT ck_standard_hours_versions_version_number CHECK (version_number >= 1),
    status                     text        NOT NULL CONSTRAINT ck_standard_hours_versions_status CHECK (status IN ('IN_EFFECT', 'VOIDED')),
    effective_from_date        date        NOT NULL,
    effective_to_date          date,
    workday_minutes_count            integer     NOT NULL CONSTRAINT ck_standard_hours_versions_workday_minutes_count CHECK (workday_minutes_count BETWEEN 1 AND 1440),
    adjusted_workday_minutes_count   integer     NOT NULL CONSTRAINT ck_standard_hours_versions_adjusted_workday_minutes_count CHECK (adjusted_workday_minutes_count BETWEEN 1 AND 1440),
    basis_ref_text                  text        NOT NULL CONSTRAINT ck_standard_hours_versions_basis_ref_text CHECK (basis_ref_text = btrim(basis_ref_text) AND char_length(basis_ref_text) BETWEEN 1 AND 200),
    confirmed_by               uuid        NOT NULL,
    confirmed_at               timestamptz NOT NULL,
    decision_audit_entry_id    uuid        NOT NULL,
    voided_by                  uuid,
    voided_at                  timestamptz,
    void_reason_text           text,
    created_at                 timestamptz NOT NULL,
    updated_at                 timestamptz NOT NULL,
    CONSTRAINT pk_standard_hours_versions PRIMARY KEY (id),
    CONSTRAINT ck_standard_hours_versions_scope_matches_legal_entity CHECK ((scope_kind = 'DEFAULT') = (legal_entity_id IS NULL)),
    CONSTRAINT ck_standard_hours_versions_effective_range CHECK (effective_to_date IS NULL OR effective_to_date > effective_from_date),
    CONSTRAINT ck_standard_hours_versions_void_columns CHECK (
        (status = 'VOIDED') = (voided_by IS NOT NULL)
        AND (voided_by IS NULL) = (voided_at IS NULL) AND (voided_by IS NULL) = (void_reason_text IS NULL)
        AND (void_reason_text IS NULL OR (void_reason_text = btrim(void_reason_text) AND char_length(void_reason_text) BETWEEN 1 AND 500))),
    CONSTRAINT ex_standard_hours_versions_overlap_legal_entity EXCLUDE USING gist (legal_entity_id WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (status = 'IN_EFFECT' AND legal_entity_id IS NOT NULL),
    CONSTRAINT ex_standard_hours_versions_overlap_default EXCLUDE USING gist (daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (status = 'IN_EFFECT' AND legal_entity_id IS NULL)
);
CREATE UNIQUE INDEX uk_standard_hours_versions_legal_entity_version_number ON organization_attendance.standard_hours_versions (legal_entity_id, version_number) WHERE legal_entity_id IS NOT NULL;
CREATE UNIQUE INDEX uk_standard_hours_versions_default_version_number ON organization_attendance.standard_hours_versions (version_number) WHERE legal_entity_id IS NULL;

CREATE TABLE organization_attendance.standard_hours_date_overrides (
    id                    uuid        NOT NULL,
    standard_hours_version_id uuid    NOT NULL,
    work_date             date        NOT NULL,
    minutes_count               integer     NOT NULL CONSTRAINT ck_standard_hours_date_overrides_minutes_count CHECK (minutes_count BETWEEN 1 AND 1440),
    reason_text           text        NOT NULL CONSTRAINT ck_standard_hours_date_overrides_reason_text CHECK (reason_text = btrim(reason_text) AND char_length(reason_text) BETWEEN 1 AND 200),
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    CONSTRAINT pk_standard_hours_date_overrides PRIMARY KEY (id),
    CONSTRAINT fk_standard_hours_date_overrides_standard_hours_version_id FOREIGN KEY (standard_hours_version_id) REFERENCES organization_attendance.standard_hours_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uk_standard_hours_date_overrides_version_work_date UNIQUE (standard_hours_version_id, work_date)
);

CREATE FUNCTION organization_attendance.guard_standard_hours_version_insert() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    scope_key uuid := NEW.legal_entity_id;
    latest integer;
    latest_from date;
    today date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date;
    closed integer;
BEGIN
    IF NEW.status <> 'IN_EFFECT' OR NEW.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'a new standard-hours version is IN_EFFECT and open: effective_to_date is set when the next version is published' USING ERRCODE = 'check_violation';
    END IF;
    SELECT max(version_number) INTO latest FROM organization_attendance.standard_hours_versions
        WHERE legal_entity_id IS NOT DISTINCT FROM scope_key;
    IF NEW.version_number <> coalesce(latest, 0) + 1 THEN
        RAISE EXCEPTION 'version_number must be % for this scope, got %', coalesce(latest, 0) + 1, NEW.version_number USING ERRCODE = 'check_violation';
    END IF;
    SELECT max(effective_from_date) INTO latest_from FROM organization_attendance.standard_hours_versions
        WHERE legal_entity_id IS NOT DISTINCT FROM scope_key AND status = 'IN_EFFECT';
    IF latest_from IS NOT NULL THEN
        IF NEW.effective_from_date <= latest_from THEN
            RAISE EXCEPTION 'effective_from_date % must be later than the latest version of the scope (%): versions are appended in order', NEW.effective_from_date, latest_from USING ERRCODE = 'check_violation';
        END IF;
        -- 不追溯：非首版不得早于库的事务日期（按库时间取日）；首版没有被它拆分过的日子，可以补录历史。
        IF NEW.effective_from_date < today THEN
            RAISE EXCEPTION 'effective_from_date % is earlier than today: a standard-hours version does not act on the past', NEW.effective_from_date USING ERRCODE = 'check_violation';
        END IF;
        UPDATE organization_attendance.standard_hours_versions SET effective_to_date = NEW.effective_from_date
            WHERE legal_entity_id IS NOT DISTINCT FROM scope_key AND status = 'IN_EFFECT' AND effective_to_date IS NULL;
        GET DIAGNOSTICS closed = ROW_COUNT;
        IF closed > 1 THEN
            RAISE EXCEPTION 'more than one open version in a scope' USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION organization_attendance.guard_standard_hours_version_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    void_columns text[] := ARRAY['status', 'voided_by', 'voided_at', 'void_reason_text'];
    scope_key uuid := OLD.legal_entity_id;
    today date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'standard-hours versions are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;

    -- 关闭与重开上一版本只在发布、作废的触发器里发生（触发器嵌套深度 > 1），且只改 effective_to_date。
    IF pg_trigger_depth() > 1 AND OLD.status = 'IN_EFFECT' AND NEW.status = 'IN_EFFECT'
            AND (to_jsonb(NEW) - 'effective_to_date') = (to_jsonb(OLD) - 'effective_to_date')
            AND ((OLD.effective_to_date IS NULL AND NEW.effective_to_date IS NOT NULL) OR (OLD.effective_to_date IS NOT NULL AND NEW.effective_to_date IS NULL)) THEN
        RETURN NEW;
    END IF;

    -- 作废：只作废未来的版本（生效日晚于库的事务日期），且必须是同范围最新的一个；被它关闭的上一版本由 AFTER 触发器重开（此处重开会与仍有效的本行重叠）。
    IF OLD.status = 'IN_EFFECT' AND NEW.status = 'VOIDED' AND (to_jsonb(NEW) - void_columns) = (to_jsonb(OLD) - void_columns) THEN
        IF OLD.effective_from_date <= today THEN
            RAISE EXCEPTION 'a version in force or in the past cannot be voided: correct it with a new version from today' USING ERRCODE = 'check_violation';
        END IF;
        IF EXISTS (SELECT 1 FROM organization_attendance.standard_hours_versions later
                WHERE later.legal_entity_id IS NOT DISTINCT FROM scope_key AND later.status = 'IN_EFFECT' AND later.effective_from_date > OLD.effective_from_date) THEN
            RAISE EXCEPTION 'only the latest version of a scope can be voided' USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'a standard-hours version only changes by being closed by its successor or voided once: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
END $$;

CREATE FUNCTION organization_attendance.reopen_standard_hours_predecessor() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    UPDATE organization_attendance.standard_hours_versions SET effective_to_date = NULL
        WHERE legal_entity_id IS NOT DISTINCT FROM OLD.legal_entity_id AND status = 'IN_EFFECT' AND effective_to_date = OLD.effective_from_date;
    RETURN NULL;
END $$;

CREATE FUNCTION organization_attendance.guard_standard_hours_override_insert() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    parent_from date;
    parent_xmin bigint;
BEGIN
    SELECT effective_from_date, xmin::text::bigint INTO parent_from, parent_xmin FROM organization_attendance.standard_hours_versions WHERE id = NEW.standard_hours_version_id;
    IF parent_from IS NULL THEN
        RAISE EXCEPTION 'standard-hours version % does not exist', NEW.standard_hours_version_id USING ERRCODE = 'foreign_key_violation';
    END IF;
    IF NEW.work_date < parent_from THEN
        RAISE EXCEPTION 'work_date % is earlier than the effective_from_date % of its version', NEW.work_date, parent_from USING ERRCODE = 'check_violation';
    END IF;
    -- 覆盖行只能在创建父版本的同一事务里插入：父行的创建事务就是当前事务（父行此后只被关闭或作废，那是新事务，不能再加覆盖行）。
    IF parent_xmin <> (pg_current_xact_id()::text::bigint % 4294967296) THEN
        RAISE EXCEPTION 'override rows are written in the transaction that creates their version: version % is already committed', NEW.standard_hours_version_id USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_standard_hours_versions_insert BEFORE INSERT ON organization_attendance.standard_hours_versions
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.guard_standard_hours_version_insert();
CREATE TRIGGER tg_standard_hours_versions_change BEFORE UPDATE OR DELETE ON organization_attendance.standard_hours_versions
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.guard_standard_hours_version_change();
CREATE TRIGGER tg_standard_hours_versions_void_reopens AFTER UPDATE ON organization_attendance.standard_hours_versions
    FOR EACH ROW WHEN (OLD.status = 'IN_EFFECT' AND NEW.status = 'VOIDED') EXECUTE FUNCTION organization_attendance.reopen_standard_hours_predecessor();
CREATE TRIGGER tg_standard_hours_versions_truncate BEFORE TRUNCATE ON organization_attendance.standard_hours_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_attendance.reject_calendar_row_change();
CREATE TRIGGER tg_standard_hours_date_overrides_insert BEFORE INSERT ON organization_attendance.standard_hours_date_overrides
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.guard_standard_hours_override_insert();
CREATE TRIGGER tg_standard_hours_date_overrides_append_only BEFORE UPDATE OR DELETE ON organization_attendance.standard_hours_date_overrides
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.reject_calendar_row_change();
CREATE TRIGGER tg_standard_hours_date_overrides_append_only_truncate BEFORE TRUNCATE ON organization_attendance.standard_hours_date_overrides
    FOR EACH STATEMENT EXECUTE FUNCTION organization_attendance.reject_calendar_row_change();

COMMENT ON TABLE organization_attendance.standard_hours_versions IS '标准工时版本：某个范围（默认或某个法人）从某天起每个工作日的标准分钟数；没有默认值，数值由用户经发布命令录入（INV-116）。粒度：每个范围每个版本一行。版本只追加，错值用今天起的新版本更正；新版本发布时关闭上一版本，未来的版本可作废。';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.id IS '标准工时版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.scope_kind IS '范围类别：DEFAULT 默认（没有自有版本的法人用它）/ LEGAL_ENTITY 某个法人自己的；与法人主体 ID 成对：默认范围无法人、法人范围必有法人';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.legal_entity_id IS '法人主体 ID；范围为法人时必填，默认范围为空；只存 ID，不建外键（法人在别的模块，存在性由应用经 api 校验）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.version_number IS '版本号：同一范围内从 1 起逐个加 1，作废的版本也占号';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.status IS '状态：IN_EFFECT 有效 / VOIDED 已作废（只能作废生效日在未来的最新版本）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.effective_from_date IS '生效日期：从这一天起适用；同范围必须晚于已有版本的生效日期；非首版不得早于发布当天（不追溯）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.effective_to_date IS '失效日期（不含当天）；空表示仍开放；只由发布下一版本的触发器写入，作废下一版本时改回空';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.workday_minutes_count IS '工作日标准分钟数：日历判为工作日的那天的标准工时（1 到 1440）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.adjusted_workday_minutes_count IS '调休上班日标准分钟数：日历判为调休上班日的那天的标准工时（1 到 1440）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.basis_ref_text IS '依据引用：这个标准工时的出处（决定、通知或制度的编号或名称，1 到 200 字）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.confirmed_by IS '确认人账号 ID：命令里写明的确认人，可以等于操作人（本版本没有第二人确认）；操作人看决定审计记录';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.confirmed_at IS '确认时间（由应用写入）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.decision_audit_entry_id IS '决定审计记录 ID：发布这个版本的审计记录';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.voided_by IS '作废人账号 ID；未作废为空；与作废时间、作废原因同时填或同时空';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.voided_at IS '作废时间；未作废为空';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.void_reason_text IS '作废原因（1 到 500 字，已去首尾空白）；未作废为空';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.created_at IS '创建时间（由应用写入）';
COMMENT ON COLUMN organization_attendance.standard_hours_versions.updated_at IS '更新时间（行只追加，由应用写入，等于创建时间；关闭与作废不改它）';

COMMENT ON TABLE organization_attendance.standard_hours_date_overrides IS '标准工时日期覆盖：某个版本里指定日期的标准分钟数（例如半天调休 240）。粒度：每个版本每个日期一行。只追加，只能在创建父版本的同一事务里写入；落在版本生效区间之外的行永远不被读。';
COMMENT ON COLUMN organization_attendance.standard_hours_date_overrides.id IS '标准工时日期覆盖 ID（UUIDv7）';
COMMENT ON COLUMN organization_attendance.standard_hours_date_overrides.standard_hours_version_id IS '标准工时版本 ID：所属版本';
COMMENT ON COLUMN organization_attendance.standard_hours_date_overrides.work_date IS '工作日期：被覆盖的那一天，不得早于所属版本的生效日期；非工作日由工作日历决定，不用覆盖表达';
COMMENT ON COLUMN organization_attendance.standard_hours_date_overrides.minutes_count IS '标准分钟数：这一天的标准工时（1 到 1440）';
COMMENT ON COLUMN organization_attendance.standard_hours_date_overrides.reason_text IS '覆盖原因（1 到 200 字，已去首尾空白）';
COMMENT ON COLUMN organization_attendance.standard_hours_date_overrides.created_at IS '创建时间（由应用写入）';
COMMENT ON COLUMN organization_attendance.standard_hours_date_overrides.updated_at IS '更新时间（行只追加，由应用写入，等于创建时间）';
