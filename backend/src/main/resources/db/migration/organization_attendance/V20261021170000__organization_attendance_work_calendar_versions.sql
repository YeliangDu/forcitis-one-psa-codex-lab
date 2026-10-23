-- 工作日历版本化（IMP-1021-PAR-02，one-data 审查 WORK-CALENDAR-DDL-2026-10-21 及更正一至九；ADR-0033 第 9 项、ADR-0072）：
-- 根表 + 版本表 + 版本日期表，全部只追加。待确认状态留在版本表（同 numbering_rule_versions）；确认只能把待确认改成已确认，并同时挂官方通知与公司加休登记。
-- 旧的 work_calendar_years / work_calendar_days 本迁移不动：读写代码切到新表（四眼流程）的 PR 之后再下线。

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: the database setup script (one_db_roles_schemas.sql) creates it before the migrations run';
    END IF;
END $$;

CREATE TABLE organization_attendance.work_calendars (
    id                  uuid        NOT NULL,
    calendar_scope_kind text        NOT NULL CONSTRAINT ck_work_calendars_calendar_scope_kind CHECK (calendar_scope_kind IN ('DEFAULT', 'LEGAL_ENTITY')),
    legal_entity_id     uuid,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    CONSTRAINT pk_work_calendars PRIMARY KEY (id),
    CONSTRAINT ck_work_calendars_scope_matches_legal_entity CHECK ((calendar_scope_kind = 'DEFAULT') = (legal_entity_id IS NULL))
);
CREATE UNIQUE INDEX uk_work_calendars_calendar_scope_kind ON organization_attendance.work_calendars (calendar_scope_kind) WHERE calendar_scope_kind = 'DEFAULT';
CREATE UNIQUE INDEX uk_work_calendars_legal_entity_id ON organization_attendance.work_calendars (legal_entity_id) WHERE calendar_scope_kind = 'LEGAL_ENTITY';
COMMENT ON TABLE organization_attendance.work_calendars IS '工作日历：日历的身份，决定一份日历适用于谁；默认日历至多一份，每个法人至多一份自己的日历；只追加。粒度：每份日历一行';
COMMENT ON COLUMN organization_attendance.work_calendars.id IS '工作日历 ID（UUIDv7）';
COMMENT ON COLUMN organization_attendance.work_calendars.calendar_scope_kind IS '日历范围类别：DEFAULT 默认日历（无自有日历的法人用它）/ LEGAL_ENTITY 某个法人自己的日历；与法人主体 ID 成对：DEFAULT 时法人为空，LEGAL_ENTITY 时法人必填';
COMMENT ON COLUMN organization_attendance.work_calendars.legal_entity_id IS '法人主体 ID；范围为法人日历时必填，为默认日历时为空（空表示不属于某个法人）';
COMMENT ON COLUMN organization_attendance.work_calendars.created_at IS '创建时间（由应用写入）';
COMMENT ON COLUMN organization_attendance.work_calendars.updated_at IS '更新时间（行只追加，由应用写入，等于创建时间；版本表的确认不改它，确认时间看 confirmed_at）';

CREATE TABLE organization_attendance.work_calendar_versions (
    id                          uuid        NOT NULL,
    work_calendar_id            uuid        NOT NULL,
    year_number                 integer     NOT NULL CONSTRAINT ck_work_calendar_versions_year_number CHECK (year_number BETWEEN 2000 AND 2100),
    version_number              integer     NOT NULL CONSTRAINT ck_work_calendar_versions_version_number CHECK (version_number >= 1),
    confirmation_status         text        NOT NULL CONSTRAINT ck_work_calendar_versions_confirmation_status CHECK (confirmation_status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'WITHDRAWN')),
    source_kind                 text        NOT NULL CONSTRAINT ck_work_calendar_versions_source_kind CHECK (source_kind IN ('AUTHORED', 'MIGRATION')),
    effective_from_date         date        NOT NULL,
    effective_to_date           date,
    authored_by                 uuid,
    authored_at                 timestamptz,
    confirmed_by                uuid,
    confirmed_at                timestamptz,
    decision_audit_entry_id     uuid,
    official_notice_number      text,
    official_notice_url         text,
    company_leave_register_kind text CONSTRAINT ck_work_calendar_versions_company_leave_register_kind CHECK (company_leave_register_kind IN ('REGISTERED', 'NONE')),
    company_leave_register_key  text,
    withdrawn_by                uuid,
    withdrawn_at                timestamptz,
    withdraw_reason_text        text,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    CONSTRAINT pk_work_calendar_versions PRIMARY KEY (id),
    CONSTRAINT fk_work_calendar_versions_work_calendar_id FOREIGN KEY (work_calendar_id) REFERENCES organization_attendance.work_calendars (id) ON DELETE RESTRICT,
    CONSTRAINT uk_work_calendar_versions_calendar_year_version UNIQUE (work_calendar_id, year_number, version_number),
    CONSTRAINT uk_work_calendar_versions_id_year_number UNIQUE (id, year_number),
    CONSTRAINT ex_work_calendar_versions_overlap EXCLUDE USING gist (work_calendar_id WITH =, year_number WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&)
        WHERE (confirmation_status = 'CONFIRMED'),
    CONSTRAINT ck_work_calendar_versions_effective_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_work_calendar_versions_migration_is_first_version CHECK (source_kind <> 'MIGRATION' OR version_number = 1),
    CONSTRAINT ck_work_calendar_versions_author_matches_source_kind CHECK ((source_kind = 'MIGRATION') = (authored_by IS NULL) AND (authored_by IS NULL) = (authored_at IS NULL)),
    CONSTRAINT ck_work_calendar_versions_confirmer_is_not_author CHECK (authored_by IS NULL OR confirmed_by IS NULL OR authored_by <> confirmed_by),
    CONSTRAINT ck_work_calendar_versions_confirmation_columns CHECK (
        (confirmation_status = 'PENDING_CONFIRMATION' AND confirmed_by IS NULL AND confirmed_at IS NULL AND decision_audit_entry_id IS NULL)
        OR (confirmation_status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND decision_audit_entry_id IS NOT NULL
            AND confirmed_by IS NOT NULL)
        OR (confirmation_status = 'WITHDRAWN' AND confirmed_by IS NULL AND confirmed_at IS NULL AND decision_audit_entry_id IS NULL)),
    CONSTRAINT ck_work_calendar_versions_withdrawal_columns CHECK (
        (confirmation_status = 'WITHDRAWN') = (withdrawn_by IS NOT NULL)
        AND (withdrawn_by IS NULL) = (withdrawn_at IS NULL) AND (withdrawn_by IS NULL) = (withdraw_reason_text IS NULL)
        AND (withdraw_reason_text IS NULL OR (withdraw_reason_text = btrim(withdraw_reason_text) AND char_length(withdraw_reason_text) BETWEEN 1 AND 500))),
    CONSTRAINT ck_work_calendar_versions_first_version_covers_year CHECK (version_number <> 1 OR effective_from_date <= make_date(year_number, 1, 1)),
    CONSTRAINT ck_work_calendar_versions_confirmed_after_authored CHECK (authored_at IS NULL OR confirmed_at IS NULL OR confirmed_at >= authored_at),
    CONSTRAINT ck_work_calendar_versions_confirmed_has_evidence CHECK (
        confirmation_status <> 'CONFIRMED'
        OR (official_notice_number IS NOT NULL AND official_notice_url IS NOT NULL AND company_leave_register_kind IS NOT NULL)),
    CONSTRAINT ck_work_calendar_versions_company_leave_register_key CHECK (
        coalesce(company_leave_register_kind = 'REGISTERED', false) = (company_leave_register_key IS NOT NULL)
        AND (company_leave_register_key IS NULL OR (company_leave_register_key = btrim(company_leave_register_key) AND char_length(company_leave_register_key) BETWEEN 1 AND 200))),
    CONSTRAINT ck_work_calendar_versions_official_notice_number CHECK (
        official_notice_number IS NULL OR (official_notice_number = btrim(official_notice_number) AND char_length(official_notice_number) BETWEEN 1 AND 100)),
    CONSTRAINT ck_work_calendar_versions_official_notice_url CHECK (
        official_notice_url IS NULL OR (official_notice_url ~ '^https://[^[:space:]]+$' AND char_length(official_notice_url) <= 2048))
);
CREATE UNIQUE INDEX uk_work_calendar_versions_pending ON organization_attendance.work_calendar_versions (work_calendar_id, year_number) WHERE confirmation_status = 'PENDING_CONFIRMATION';
CREATE INDEX ix_work_calendar_versions_confirmed ON organization_attendance.work_calendar_versions (work_calendar_id, year_number) WHERE confirmation_status = 'CONFIRMED';
COMMENT ON TABLE organization_attendance.work_calendar_versions IS '工作日历版本：某份日历某个年份的一个版本，只追加；先以待确认状态产生，确认时挂上官方通知与公司加休登记后生效，确认后不可再改，新的改动另开新版本。粒度：每份日历每个年份的每个版本一行';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.id IS '工作日历版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.work_calendar_id IS '工作日历 ID：所属日历';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.year_number IS '日历年份';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.version_number IS '版本号：同一日历同一年份从 1 起逐个加 1；迁入版本恒为 1';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.confirmation_status IS '确认状态：PENDING_CONFIRMATION 待确认（不生效，同一日历同一年份至多一个）/ CONFIRMED 已确认（生效，按生效区间取用）/ WITHDRAWN 已撤回（终态，版本号不回收）；只能由待确认改成已确认或已撤回';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.source_kind IS '来源类别：AUTHORED 界面或运维起草（有作者，确认人不得是作者）/ MIGRATION 迁入（作者为空，只能由库的属主角色或迁入角色写入，恒为第 1 版；确认由业务人员在应用内做，与有作者的版本一样）';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.effective_from_date IS '生效日（含）：此日及以后取本版本；首个已确认版本以外的版本不得早于确认日（按库的事务时间取日），且此日之前的例外日必须与上一版本一致（不追溯）；第 1 版不得晚于该年 1 月 1 日';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.effective_to_date IS '生效截止日（不含）；为空表示仍然开放。下一版本确认时由库自动关闭上一版本，其余时候不可改';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.authored_by IS '作者的用户账号 ID；来源为迁入时为空（迁入没有作者）';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.authored_at IS '起草时间；与作者同时有、同时空';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.confirmed_by IS '确认人的用户账号 ID；确认前为空，确认后必填（迁入版本也由确认人确认）；不得等于作者';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.confirmed_at IS '确认时间；确认前为空表示尚未确认';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.decision_audit_entry_id IS '决定审计记录：确认操作的审计记录 ID；确认前为空表示尚未确认，跨 schema 只按 ID 引用，不建外键';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.official_notice_number IS '官方通知文号；起草时可为空表示尚未挂，确认时必填';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.official_notice_url IS '官方通知链接（https）；起草时可为空表示尚未挂，确认时必填';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.company_leave_register_kind IS '公司加休登记类别：REGISTERED 已登记（见登记键）/ NONE 登记为没有加休；为空表示尚未登记（确认时必填）。有公司加休或取消调休的日期时只能是 REGISTERED';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.company_leave_register_key IS '公司加休登记键：已登记时填登记的标识文本；为空表示登记类别不是已登记';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.withdrawn_by IS '撤回人的用户账号 ID；为空表示未撤回';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.withdrawn_at IS '撤回时间；为空表示未撤回';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.withdraw_reason_text IS '撤回原因；为空表示未撤回，撤回时必填';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.created_at IS '创建时间（由应用写入）';
COMMENT ON COLUMN organization_attendance.work_calendar_versions.updated_at IS '更新时间（行只追加，由应用写入，等于创建时间；版本表的确认不改它，确认时间看 confirmed_at）';

CREATE TABLE organization_attendance.work_calendar_version_days (
    id                        uuid        NOT NULL,
    work_calendar_version_id  uuid        NOT NULL,
    year_number               integer     NOT NULL,
    calendar_date             date        NOT NULL,
    day_kind                  text        NOT NULL CONSTRAINT ck_work_calendar_version_days_day_kind CHECK (day_kind IN ('HOLIDAY', 'MAKE_UP_WORKDAY', 'MAKE_UP_WORKDAY_CANCELLED')),
    basis_kind                text        NOT NULL CONSTRAINT ck_work_calendar_version_days_basis_kind CHECK (basis_kind IN ('OFFICIAL', 'COMPANY', 'MANUAL')),
    basis_note                text,
    approved_by               uuid,
    created_at                timestamptz NOT NULL,
    updated_at                timestamptz NOT NULL,
    CONSTRAINT pk_work_calendar_version_days PRIMARY KEY (id),
    CONSTRAINT fk_work_calendar_version_days_work_calendar_version_id FOREIGN KEY (work_calendar_version_id, year_number)
        REFERENCES organization_attendance.work_calendar_versions (id, year_number) ON DELETE RESTRICT,
    CONSTRAINT uk_work_calendar_version_days_version_calendar_date UNIQUE (work_calendar_version_id, calendar_date),
    CONSTRAINT ck_work_calendar_version_days_calendar_date_in_year CHECK (EXTRACT(YEAR FROM calendar_date) = year_number),
    CONSTRAINT ck_work_calendar_version_days_make_up_weekend CHECK (day_kind NOT IN ('MAKE_UP_WORKDAY', 'MAKE_UP_WORKDAY_CANCELLED') OR EXTRACT(ISODOW FROM calendar_date) IN (6, 7)),
    CONSTRAINT ck_work_calendar_version_days_cancelled_basis CHECK (day_kind <> 'MAKE_UP_WORKDAY_CANCELLED' OR basis_kind IN ('COMPANY', 'MANUAL')),
    CONSTRAINT ck_work_calendar_version_days_basis_note CHECK (
        (basis_kind = 'MANUAL' OR day_kind = 'MAKE_UP_WORKDAY_CANCELLED') = (basis_note IS NOT NULL)
        AND (basis_note IS NULL OR (basis_note = btrim(basis_note) AND char_length(basis_note) BETWEEN 1 AND 500))),
    CONSTRAINT ck_work_calendar_version_days_approved_by CHECK ((basis_kind = 'MANUAL') = (approved_by IS NOT NULL))
);
COMMENT ON TABLE organization_attendance.work_calendar_version_days IS '工作日历版本日期：一个版本的例外日期全集（周一至周五上班、周末休息之外的日子），只追加，且只能在版本待确认时加入。粒度：每个版本内每个例外日期一行';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.id IS '工作日历版本日期 ID（UUIDv7）';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.work_calendar_version_id IS '工作日历版本 ID：所属版本';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.year_number IS '日历年份：与所属版本一致，日期必须落在这一年';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.calendar_date IS '例外日期';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.day_kind IS '类型：HOLIDAY 节假日 / MAKE_UP_WORKDAY 调休上班日（必须是周六或周日）/ MAKE_UP_WORKDAY_CANCELLED 官方调休上班日被公司取消（必须是周六或周日，按普通周末休息计）';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.basis_kind IS '依据类别：OFFICIAL 官方通知 / COMPANY 公司加休（取消调休也属于此类）/ MANUAL 手工更正（须有依据说明与批准人）';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.basis_note IS '依据说明；手工更正与取消调休必填（取消调休写明取消的是哪份官方通知里的哪个调休），其余为空表示无需说明';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.approved_by IS '批准人的用户账号 ID；手工更正必填且不得是版本作者，其余为空表示无需单独批准';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.created_at IS '创建时间（由应用写入）';
COMMENT ON COLUMN organization_attendance.work_calendar_version_days.updated_at IS '更新时间（行只追加，由应用写入，等于创建时间；版本表的确认不改它，确认时间看 confirmed_at）';

CREATE FUNCTION organization_attendance.reject_calendar_row_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION '%.% is append-only: % is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP USING ERRCODE = 'restrict_violation';
END $$;
COMMENT ON FUNCTION organization_attendance.reject_calendar_row_change() IS '工作日历根表与版本日期表只追加的触发器：任何更新、删除、清空都拒绝，没有例外身份';

CREATE TRIGGER tg_work_calendars_append_only BEFORE UPDATE OR DELETE ON organization_attendance.work_calendars
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.reject_calendar_row_change();
CREATE TRIGGER tg_work_calendars_append_only_truncate BEFORE TRUNCATE ON organization_attendance.work_calendars
    FOR EACH STATEMENT EXECUTE FUNCTION organization_attendance.reject_calendar_row_change();
CREATE TRIGGER tg_work_calendar_version_days_append_only BEFORE UPDATE OR DELETE ON organization_attendance.work_calendar_version_days
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.reject_calendar_row_change();
CREATE TRIGGER tg_work_calendar_version_days_append_only_truncate BEFORE TRUNCATE ON organization_attendance.work_calendar_version_days
    FOR EACH STATEMENT EXECUTE FUNCTION organization_attendance.reject_calendar_row_change();

-- 判定函数是 SECURITY INVOKER：current_user 就是执行插入的角色，若改成 SECURITY DEFINER 则恒为属主、等于放行所有人。
CREATE FUNCTION organization_attendance.is_owner_member(table_oid oid) RETURNS boolean
    LANGUAGE sql STABLE SET search_path = pg_catalog, pg_temp AS $$
    SELECT COALESCE(pg_has_role(current_user, (SELECT relowner FROM pg_catalog.pg_class WHERE oid = table_oid), 'MEMBER'), false)
$$;
COMMENT ON FUNCTION organization_attendance.is_owner_member(oid) IS '当前角色是否属主角色的成员；表不存在或属主不存在时为假';
-- 迁入角色的名字由属主角色名推出（ADR-0036：one_<环境>_owner 对 one_<环境>_history_loader），由属主名替换后缀推出，按推出的全名精确比对；该角色须由部署脚本先建好。
CREATE FUNCTION organization_attendance.is_migration_writer(table_oid oid) RETURNS boolean
    LANGUAGE sql STABLE SET search_path = pg_catalog, pg_temp AS $$
    SELECT organization_attendance.is_owner_member(table_oid) OR COALESCE(pg_has_role(current_user,
        (SELECT r.oid FROM pg_catalog.pg_roles r
          WHERE r.rolname = regexp_replace(pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid = table_oid)), '_owner$', '_history_loader')
            AND r.rolname <> pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid = table_oid))), 'MEMBER'), false)
$$;
COMMENT ON FUNCTION organization_attendance.is_migration_writer(oid) IS '当前角色是否可以写迁入行：属主角色成员，或由属主角色名推出的迁入角色的成员；其余（含运行角色）为假';

CREATE FUNCTION organization_attendance.guard_work_calendar_version_insert() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    latest integer;
BEGIN
    IF NEW.confirmation_status <> 'PENDING_CONFIRMATION' THEN
        RAISE EXCEPTION 'a calendar version starts as PENDING_CONFIRMATION and is confirmed afterwards' USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'a new calendar version is open: effective_to_date is set when the next version is confirmed' USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.source_kind = 'MIGRATION' AND NOT organization_attendance.is_migration_writer(TG_RELID) THEN
        RAISE EXCEPTION 'a migrated calendar version can only be written by the owner role or the migration role: % is refused', current_user USING ERRCODE = 'insufficient_privilege';
    END IF;
    SELECT max(version_number) INTO latest FROM organization_attendance.work_calendar_versions
        WHERE work_calendar_id = NEW.work_calendar_id AND year_number = NEW.year_number;
    IF NEW.version_number <> coalesce(latest, 0) + 1 THEN
        RAISE EXCEPTION 'version_number must be % for this calendar and year, got %', coalesce(latest, 0) + 1, NEW.version_number USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_attendance.guard_work_calendar_version_insert() IS '新版本只能以待确认状态进入、版本号逐个加 1、迁入版本只放行属主角色与迁入角色';

CREATE FUNCTION organization_attendance.guard_work_calendar_version_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    -- 确认时允许一起写入的列：状态、确认三列、官方通知与公司加休登记。其余列确认后与确认前一致。
    confirmation_columns text[] := ARRAY['confirmation_status', 'confirmed_by', 'confirmed_at', 'decision_audit_entry_id', 'official_notice_number',
            'official_notice_url', 'company_leave_register_kind', 'company_leave_register_key'];
    withdrawal_columns text[] := ARRAY['confirmation_status', 'withdrawn_by', 'withdrawn_at', 'withdraw_reason_text'];
    previous organization_attendance.work_calendar_versions;
    confirmation_day date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date;
    day_conflict boolean;
    closed integer;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'calendar versions are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;

    -- 关闭上一版本只在确认下一版本的触发器里发生；另要求下一版本（待确认）已存在且起算日就是这个截止日，别的关法一概拒绝。
    IF pg_trigger_depth() > 1 AND OLD.confirmation_status = 'CONFIRMED' AND OLD.effective_to_date IS NULL AND NEW.effective_to_date IS NOT NULL
            AND (to_jsonb(NEW) - 'effective_to_date') = (to_jsonb(OLD) - 'effective_to_date')
            AND EXISTS (SELECT 1 FROM organization_attendance.work_calendar_versions successor
                WHERE successor.work_calendar_id = OLD.work_calendar_id AND successor.year_number = OLD.year_number AND successor.version_number > OLD.version_number
                  AND successor.confirmation_status = 'PENDING_CONFIRMATION' AND successor.effective_from_date = NEW.effective_to_date) THEN
        RETURN NEW;
    END IF;

    IF OLD.confirmation_status = 'PENDING_CONFIRMATION' AND NEW.confirmation_status = 'WITHDRAWN'
            AND (to_jsonb(NEW) - withdrawal_columns) = (to_jsonb(OLD) - withdrawal_columns) THEN
        RETURN NEW;
    END IF;

    IF NOT (OLD.confirmation_status = 'PENDING_CONFIRMATION' AND NEW.confirmation_status = 'CONFIRMED'
            AND (to_jsonb(NEW) - confirmation_columns) = (to_jsonb(OLD) - confirmation_columns)) THEN
        RAISE EXCEPTION 'a calendar version can only be confirmed or withdrawn once; nothing else of it changes: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;

    IF EXISTS (SELECT 1 FROM organization_attendance.work_calendar_version_days
            WHERE work_calendar_version_id = NEW.id AND (basis_kind = 'COMPANY' OR day_kind = 'MAKE_UP_WORKDAY_CANCELLED'))
            AND NEW.company_leave_register_kind IS DISTINCT FROM 'REGISTERED' THEN
        RAISE EXCEPTION 'a version with company leave or cancelled make-up days needs company_leave_register_kind REGISTERED' USING ERRCODE = 'check_violation';
    END IF;

    -- 上一版本 = 同日历同年版本号最大的已确认版本（撤回的版本不算）；没有就是这一年的第一个已确认版本。
    SELECT * INTO previous FROM organization_attendance.work_calendar_versions
        WHERE work_calendar_id = NEW.work_calendar_id AND year_number = NEW.year_number AND confirmation_status = 'CONFIRMED' AND version_number < NEW.version_number
        ORDER BY version_number DESC LIMIT 1;
    IF previous.id IS NULL THEN
        IF NEW.effective_from_date > make_date(NEW.year_number, 1, 1) THEN
            RAISE EXCEPTION 'the first confirmed version of a year starts on 1 January at the latest: the days before it would have no version' USING ERRCODE = 'check_violation';
        END IF;
    ELSE
        -- 确认日取库的事务时间，不取应用写入的 confirmed_at（后者只作记录），倒填绕不过去。
        IF confirmation_day > NEW.effective_from_date THEN
            RAISE EXCEPTION 'effective_from_date % is earlier than the confirmation day: a calendar version does not act on the past', NEW.effective_from_date USING ERRCODE = 'check_violation';
        END IF;
        SELECT EXISTS (
            (SELECT calendar_date, day_kind, basis_kind FROM organization_attendance.work_calendar_version_days WHERE work_calendar_version_id = NEW.id AND calendar_date < NEW.effective_from_date
             EXCEPT
             SELECT calendar_date, day_kind, basis_kind FROM organization_attendance.work_calendar_version_days WHERE work_calendar_version_id = previous.id AND calendar_date < NEW.effective_from_date)
            UNION ALL
            (SELECT calendar_date, day_kind, basis_kind FROM organization_attendance.work_calendar_version_days WHERE work_calendar_version_id = previous.id AND calendar_date < NEW.effective_from_date
             EXCEPT
             SELECT calendar_date, day_kind, basis_kind FROM organization_attendance.work_calendar_version_days WHERE work_calendar_version_id = NEW.id AND calendar_date < NEW.effective_from_date)
        ) INTO day_conflict;
        IF day_conflict THEN
            RAISE EXCEPTION 'the exception days (date, kind, basis) before effective_from_date % differ from the previous version: a calendar version does not act on the past', NEW.effective_from_date USING ERRCODE = 'check_violation';
        END IF;
        UPDATE organization_attendance.work_calendar_versions SET effective_to_date = NEW.effective_from_date
            WHERE id = previous.id AND effective_to_date IS NULL;
        GET DIAGNOSTICS closed = ROW_COUNT;
        IF closed <> 1 THEN
            RAISE EXCEPTION 'the version below % is not open and cannot be closed at %', NEW.version_number, NEW.effective_from_date USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_attendance.guard_work_calendar_version_change() IS '版本表只追加：不删不清空；允许的改动只有：待确认改成已确认（同时挂确认三列与官方通知、公司加休登记，检查不追溯，并关闭上一版本）、待确认改成已撤回，以及上述关闭本身（只在触发器内）';

CREATE TRIGGER tg_work_calendar_versions_insert BEFORE INSERT ON organization_attendance.work_calendar_versions
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.guard_work_calendar_version_insert();
CREATE TRIGGER tg_work_calendar_versions_change BEFORE UPDATE OR DELETE ON organization_attendance.work_calendar_versions
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.guard_work_calendar_version_change();
CREATE TRIGGER tg_work_calendar_versions_change_truncate BEFORE TRUNCATE ON organization_attendance.work_calendar_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_attendance.guard_work_calendar_version_change();

-- SECURITY DEFINER：迁入角色只有 SELECT、INSERT，加共享锁（FOR SHARE）要对版本表有 UPDATE 类权限，所以由属主身份执行。函数体不看 current_user、session_user，不放行也不拒绝任何人；
-- 谁能插入日期仍由表的 INSERT 权限决定。search_path 已固定。
-- 对版本行加共享锁使日期插入与确认互斥，前提是 READ COMMITTED（应用只用它）；REPEATABLE READ 下确认与加日期之间可能漏检（one-data 更正十，记录在案）。
CREATE FUNCTION organization_attendance.guard_work_calendar_version_day_insert() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    parent organization_attendance.work_calendar_versions;
BEGIN
    SELECT * INTO parent FROM organization_attendance.work_calendar_versions WHERE id = NEW.work_calendar_version_id FOR SHARE;
    IF parent.id IS NOT NULL AND parent.confirmation_status <> 'PENDING_CONFIRMATION' THEN
        RAISE EXCEPTION 'days can only be added to a calendar version that waits for confirmation' USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.approved_by IS NOT NULL AND NEW.approved_by = parent.authored_by THEN
        RAISE EXCEPTION 'the approver of a manual day cannot be the author of the version' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_attendance.guard_work_calendar_version_day_insert() IS '版本日期只能加进待确认的版本；手工更正的批准人不得是版本作者。对版本行加共享锁，与确认互斥';
-- 触发器函数在建触发器时才查 EXECUTE，触发时不查；SECURITY DEFINER 函数不留给 PUBLIC（R-SP-1）。
REVOKE EXECUTE ON FUNCTION organization_attendance.guard_work_calendar_version_day_insert() FROM PUBLIC;

CREATE TRIGGER tg_work_calendar_version_days_insert BEFORE INSERT ON organization_attendance.work_calendar_version_days
    FOR EACH ROW EXECUTE FUNCTION organization_attendance.guard_work_calendar_version_day_insert();
