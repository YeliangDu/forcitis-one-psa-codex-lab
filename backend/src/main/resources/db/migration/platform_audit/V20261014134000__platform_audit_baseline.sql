CREATE SCHEMA IF NOT EXISTS platform_audit;
COMMENT ON SCHEMA platform_audit IS '审计日志（权限流程与集成平台 BC）';

-- 审计订阅领域事件：事件号与创建时间一起兼作幂等键，重复投递不产生重复条目；关联号把条目与同一操作的事件、日志串起来。
-- 按创建时间（UTC 月）范围分区（ADR-0048，O-8）：在线 3 年后归档、校验，再由维护角色整块删除分区；不建默认分区，落不进任何分区的行直接报错。
-- 带登录与会话轨迹的条目（登录、会话、管理员模式、拒绝访问、特权请求、强制下线等）的来源地址、结果、登录方式放在结构化列：员工查自己的登录记录只取这些列，不解析说明文字（意图 155、ADR-0027 第 3 项）。
CREATE TABLE platform_audit.audit_entries (
    id                uuid        NOT NULL,
    action            text        NOT NULL,
    actor_id          uuid,
    on_behalf_of_id   uuid,
    subject_kind      text        NOT NULL CONSTRAINT ck_audit_entries_subject_kind CHECK (subject_kind IN ('USER_ACCOUNT', 'EMPLOYEE', 'ORG_UNIT', 'EMERGENCY_ADMIN', 'ROLE', 'APPROVAL_TASK', 'APPROVAL_REQUEST', 'SYNC_RUN', 'LEGAL_ENTITY', 'LEGAL_ENTITY_ATTRIBUTION', 'TIMESHEET', 'WORK_CALENDAR_YEAR', 'NUMBERING_RULE', 'SOURCE_REFERENCE', 'SOURCE_REFERENCE_REFUSAL', 'AUTHORIZATION_REQUEST', 'STORED_FILE', 'REQUEST', 'OPERATION', 'LOGIN_ATTEMPT')),
    subject_id        uuid,
    detail            text,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    event_id          uuid,
    correlation_id    uuid,
    change_reason     text,
    before_text       text,
    after_text        text,
    source_address    text,
    outcome_kind      text        CHECK (outcome_kind IN ('SUCCEEDED', 'FAILED')),
    login_method_kind text        CHECK (login_method_kind IN ('WECOM_WORKBENCH', 'WECOM_MINI_PROGRAM', 'WECOM_QR_CODE_LOGIN', 'SSO', 'SIGNED_TICKET', 'EMERGENCY')),
    CONSTRAINT pk_audit_entries PRIMARY KEY (id, created_at),
    CONSTRAINT uk_audit_entries_event_id_created_at UNIQUE (event_id, created_at),
    CONSTRAINT ck_audit_entries_subject_id CHECK ((subject_id IS NULL) = (subject_kind IN ('REQUEST', 'OPERATION', 'LOGIN_ATTEMPT'))),
    CONSTRAINT ck_audit_entries_login_method_outcome CHECK (login_method_kind IS NULL OR outcome_kind IS NOT NULL),
    CONSTRAINT ck_audit_entries_action_length CHECK (char_length(action) BETWEEN 1 AND 2000),
    CONSTRAINT ck_audit_entries_detail_length CHECK (char_length(detail) BETWEEN 1 AND 2000),
    CONSTRAINT ck_audit_entries_change_reason_length CHECK (char_length(change_reason) BETWEEN 1 AND 2000),
    CONSTRAINT ck_audit_entries_before_text_length CHECK (char_length(before_text) BETWEEN 1 AND 2000),
    CONSTRAINT ck_audit_entries_after_text_length CHECK (char_length(after_text) BETWEEN 1 AND 2000),
    CONSTRAINT ck_audit_entries_source_address_length CHECK (char_length(source_address) BETWEEN 1 AND 64)
)
PARTITION BY RANGE (created_at);
CREATE INDEX ix_audit_entries_actor_id_created_at ON platform_audit.audit_entries (actor_id, created_at DESC, id DESC);
CREATE INDEX ix_audit_entries_action_created_at ON platform_audit.audit_entries (action text_pattern_ops, created_at DESC, id DESC);
CREATE INDEX ix_audit_entries_subject_kind_subject_id_created_at ON platform_audit.audit_entries (subject_kind, subject_id, created_at DESC, id DESC);
CREATE INDEX ix_audit_entries_created_at_id ON platform_audit.audit_entries (created_at, id);
COMMENT ON TABLE platform_audit.audit_entries IS '审计条目：只追加，数据库触发器禁止修改和删除（G34），在线分区只能由维护角色在归档校验后整块删除（O-8）；粒度：每个被审计的动作或领域事件一行';
COMMENT ON COLUMN platform_audit.audit_entries.id IS '审计条目 ID（UUIDv7）';
COMMENT ON COLUMN platform_audit.audit_entries.action IS '动作：{子域}.{限界上下文}.{对象}.{动词}，如 platform.identity.role.created';
COMMENT ON COLUMN platform_audit.audit_entries.actor_id IS '操作人用户 ID；系统动作（同步作业等）为空';
COMMENT ON COLUMN platform_audit.audit_entries.on_behalf_of_id IS '被代理人用户 ID；仅显式代理动作填写（RULE-270）';
COMMENT ON COLUMN platform_audit.audit_entries.subject_kind IS '对象类别：被记录或被投递的业务对象的类别';
COMMENT ON COLUMN platform_audit.audit_entries.subject_id IS '对象 ID：被记录或被投递的业务对象的自有 ID；为空表示这条记录没有对象';
COMMENT ON COLUMN platform_audit.audit_entries.detail IS '说明文字，不含密钥和个人敏感信息';
COMMENT ON COLUMN platform_audit.audit_entries.created_at IS '审计事实发生的时间：事件条目取事件信封的发生时刻，直接审计取注入的时钟；分区键';
COMMENT ON COLUMN platform_audit.audit_entries.updated_at IS '更新时间（行不可更新，恒等于创建时间）';
COMMENT ON COLUMN platform_audit.audit_entries.event_id IS '事件号：由领域事件写入的条目才有；与创建时间一起唯一，重复投递同一事件不产生第二条';
COMMENT ON COLUMN platform_audit.audit_entries.correlation_id IS '关联号：产生该条目的请求、作业或事件处理的关联号';
COMMENT ON COLUMN platform_audit.audit_entries.change_reason IS '变更说明：操作人填写的原因（放行、强制下线等必填原因的动作）';
COMMENT ON COLUMN platform_audit.audit_entries.before_text IS '变更前的值：只写业务值，不含密钥和个人敏感信息（L3 / L4 不进审计）';
COMMENT ON COLUMN platform_audit.audit_entries.after_text IS '变更后的值：规则同变更前';
COMMENT ON COLUMN platform_audit.audit_entries.source_address IS '来源地址：带登录与会话轨迹的条目（登录、会话、管理员模式、拒绝访问、特权请求、强制下线）记录的客户端地址；其他条目、系统动作与取不到可信地址时为空';
COMMENT ON COLUMN platform_audit.audit_entries.outcome_kind IS '结果：SUCCEEDED / FAILED；记录动作成败的条目才有（登录与会话、拒绝访问、特权请求、归属更正被拒等），其他条目为空';
COMMENT ON COLUMN platform_audit.audit_entries.login_method_kind IS '登录方式：企业微信（工作台、小程序、扫码）、单点登录、签名票据、应急登录；登录、会话、管理员模式、特权请求类条目才有，强制下线、拒绝访问等为空；有登录方式必有结果';

CREATE FUNCTION platform_audit.reject_audit_entry_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit.audit_entries is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER audit_entries_reject_update_delete
    BEFORE UPDATE OR DELETE ON platform_audit.audit_entries
    FOR EACH ROW EXECUTE FUNCTION platform_audit.reject_audit_entry_change();

CREATE TRIGGER audit_entries_reject_truncate
    BEFORE TRUNCATE ON platform_audit.audit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION platform_audit.reject_audit_entry_change();
-- 语句级触发器不随分区克隆：下面的建分区函数在每个新分区上各建一条。

-- 审计在线 3 年后按批写入归档库（对象存储，WORM）：每批一行记录覆盖的条目范围、对象键与校验值，只追加，作为归档进度。
CREATE TABLE platform_audit.audit_archive_batches (
    id             uuid        NOT NULL,
    first_entry_id uuid        NOT NULL,
    first_entry_created_at timestamptz NOT NULL,
    last_entry_id  uuid        NOT NULL,
    last_entry_created_at timestamptz NOT NULL,
    entry_count    integer     NOT NULL,
    object_key     text        NOT NULL,
    sha256_hash    text        NOT NULL,
    archived_at    timestamptz NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_archive_batches PRIMARY KEY (id),
    CONSTRAINT uk_audit_archive_batches_first_entry_id UNIQUE (first_entry_id),
    CONSTRAINT uk_audit_archive_batches_last_entry_id UNIQUE (last_entry_id),
    CONSTRAINT uk_audit_archive_batches_object_key UNIQUE (object_key),
    CONSTRAINT ck_audit_archive_batches_sha256_hash CHECK (sha256_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_audit_archive_batches_range CHECK ((first_entry_created_at, first_entry_id) <= (last_entry_created_at, last_entry_id) AND entry_count > 0)
);
COMMENT ON TABLE platform_audit.audit_archive_batches IS '审计归档批次：已写入归档库的审计条目范围，只追加；粒度：每个归档批次一行；批次区间不重叠、无缺口由归档作业按位置推进保证、由对账核验（只比在线月份），库内不设排他约束';
COMMENT ON COLUMN platform_audit.audit_archive_batches.id IS '归档批次 ID（UUIDv7）';
COMMENT ON COLUMN platform_audit.audit_archive_batches.first_entry_id IS '本批第一条审计条目的 ID（按创建时刻、ID 排序的第一条）';
COMMENT ON COLUMN platform_audit.audit_archive_batches.first_entry_created_at IS '本批第一条审计条目的创建时间';
COMMENT ON COLUMN platform_audit.audit_archive_batches.last_entry_id IS '本批最后一条审计条目的 ID；与其创建时间一起是归档进度：下一批从（创建时间，ID）之后开始';
COMMENT ON COLUMN platform_audit.audit_archive_batches.last_entry_created_at IS '本批最后一条审计条目的创建时间，归档进度的时刻部分';
COMMENT ON COLUMN platform_audit.audit_archive_batches.entry_count IS '本批审计条目数';
COMMENT ON COLUMN platform_audit.audit_archive_batches.object_key IS '归档库中的对象键';
COMMENT ON COLUMN platform_audit.audit_archive_batches.sha256_hash IS '归档对象的 SHA-256（小写十六进制），归档库已确认一致';
COMMENT ON COLUMN platform_audit.audit_archive_batches.archived_at IS '归档完成时间';
COMMENT ON COLUMN platform_audit.audit_archive_batches.created_at IS '创建时间';
COMMENT ON COLUMN platform_audit.audit_archive_batches.updated_at IS '更新时间';

CREATE FUNCTION platform_audit.reject_archive_batch_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit.audit_archive_batches is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;
CREATE TRIGGER tg_audit_archive_batches_append_only BEFORE UPDATE OR DELETE ON platform_audit.audit_archive_batches
    FOR EACH ROW EXECUTE FUNCTION platform_audit.reject_archive_batch_change();
CREATE TRIGGER tg_audit_archive_batches_append_only_truncate BEFORE TRUNCATE ON platform_audit.audit_archive_batches
    FOR EACH STATEMENT EXECUTE FUNCTION platform_audit.reject_archive_batch_change();

-- 按月分区（UTC 月初为界，分区名 audit_entries_pYYYYMM）：为 [p_from, p_through] 内缺失的月份建分区并加 TRUNCATE 触发器，已有的跳过，返回新建个数。
-- 不读数据库时钟：调用方（预建作业）按注入的时钟给出月份。月份已在归档位置之内的不再建分区，以免迟到的行进入已归档或已删除的区间。
CREATE FUNCTION platform_audit.create_audit_entry_partitions(p_from date, p_through date) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, platform_audit, pg_temp SET lock_timeout = '5s' AS $$
DECLARE
    v_month    date;
    v_next     date;
    v_name     text;
    v_archived timestamptz;
    v_created  integer := 0;
BEGIN
    IF p_from IS NULL OR p_through IS NULL THEN
        RAISE EXCEPTION 'create_audit_entry_partitions: both months are required' USING ERRCODE = 'invalid_parameter_value';
    END IF;
    IF extract(day FROM p_from) <> 1 OR extract(day FROM p_through) <> 1 THEN
        RAISE EXCEPTION 'create_audit_entry_partitions: % and % must both be the first day of a month', p_from, p_through USING ERRCODE = 'invalid_parameter_value';
    END IF;
    IF p_from > p_through THEN
        RAISE EXCEPTION 'create_audit_entry_partitions: from % is after through %', p_from, p_through USING ERRCODE = 'invalid_parameter_value';
    END IF;
    IF (extract(year FROM p_through) * 12 + extract(month FROM p_through)) - (extract(year FROM p_from) * 12 + extract(month FROM p_from)) + 1 > 24 THEN
        RAISE EXCEPTION 'create_audit_entry_partitions: % to % is more than 24 months', p_from, p_through USING ERRCODE = 'invalid_parameter_value';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('platform_audit.audit_entry_partitions'));
    SELECT max(last_entry_created_at) INTO v_archived FROM platform_audit.audit_archive_batches;
    v_month := p_from;
    WHILE v_month <= p_through LOOP
        v_next := (v_month + interval '1 month')::date;
        v_name := 'audit_entries_p' || to_char(v_month, 'YYYYMM');
        IF to_regclass(format('%I.%I', 'platform_audit', v_name)) IS NULL THEN
            IF v_archived IS NOT NULL AND make_timestamptz(extract(year FROM v_next)::int, extract(month FROM v_next)::int, 1, 0, 0, 0, 'UTC') <= v_archived THEN
                RAISE EXCEPTION 'create_audit_entry_partitions: month % is already within the archived position %', v_month, v_archived USING ERRCODE = 'object_not_in_prerequisite_state';
            END IF;
            EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.%I FOR VALUES FROM (%L) TO (%L)', 'platform_audit', v_name, 'platform_audit', 'audit_entries',
                           to_char(v_month, 'YYYY-MM-DD') || ' 00:00:00+00', to_char(v_next, 'YYYY-MM-DD') || ' 00:00:00+00');
            EXECUTE format('CREATE TRIGGER audit_entries_reject_truncate BEFORE TRUNCATE ON %I.%I FOR EACH STATEMENT EXECUTE FUNCTION platform_audit.reject_audit_entry_change()',
                           'platform_audit', v_name);
            v_created := v_created + 1;
        END IF;
        v_month := v_next;
    END LOOP;
    RETURN v_created;
END;
$$;
REVOKE EXECUTE ON FUNCTION platform_audit.create_audit_entry_partitions(date, date) FROM PUBLIC;
COMMENT ON FUNCTION platform_audit.create_audit_entry_partitions(date, date) IS '预建审计条目月分区（UTC 月初为界）；运行角色经预建作业调用，返回新建个数';

-- 维护角色删除在线分区（O-8，只追加约束的登记例外）：分区存在、整月在归档位置之内、行数等于首末条都落在该月的归档批次条目数之和，三者都成立才摘除并删除，返回删除的行数；任一不符报错，不删。
-- 只授予维护角色，运行角色不能执行，也不是属主，因而不能 DETACH 或 DROP；应用代码不调用本函数。
CREATE FUNCTION platform_audit.drop_archived_audit_entry_partition(p_month date) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, platform_audit, pg_temp SET lock_timeout = '5s' AS $$
DECLARE
    v_name     text;
    v_nextday  date;
    v_start    timestamptz;
    v_next     timestamptz;
    v_pos_at   timestamptz;
    v_pos_id   uuid;
    v_rows     bigint;
    v_beyond   bigint;
    v_archived bigint;
BEGIN
    LOCK TABLE platform_audit.audit_archive_batches IN SHARE MODE;
    IF p_month IS NULL OR extract(day FROM p_month) <> 1 THEN
        RAISE EXCEPTION 'drop_archived_audit_entry_partition: % must be the first day of a month', p_month USING ERRCODE = 'invalid_parameter_value';
    END IF;
    v_name := 'audit_entries_p' || to_char(p_month, 'YYYYMM');
    IF to_regclass(format('%I.%I', 'platform_audit', v_name)) IS NULL THEN
        RAISE EXCEPTION 'drop_archived_audit_entry_partition: partition % does not exist', v_name USING ERRCODE = 'invalid_parameter_value';
    END IF;
    v_nextday := (p_month + interval '1 month')::date;
    v_start := make_timestamptz(extract(year FROM p_month)::int, extract(month FROM p_month)::int, 1, 0, 0, 0, 'UTC');
    v_next := make_timestamptz(extract(year FROM v_nextday)::int, extract(month FROM v_nextday)::int, 1, 0, 0, 0, 'UTC');
    -- 先父后子锁住父表与分区，之后才取位置、计数和核对：未提交的插入使本函数等待至超时，已提交的在核对中计入。
    EXECUTE format('LOCK TABLE %I.%I, %I.%I IN ACCESS EXCLUSIVE MODE', 'platform_audit', 'audit_entries', 'platform_audit', v_name);
    SELECT last_entry_created_at, last_entry_id INTO v_pos_at, v_pos_id
      FROM platform_audit.audit_archive_batches ORDER BY last_entry_created_at DESC, last_entry_id DESC LIMIT 1;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'drop_archived_audit_entry_partition: nothing has been archived yet' USING ERRCODE = 'object_not_in_prerequisite_state';
    END IF;
    EXECUTE format('SELECT count(*), count(*) FILTER (WHERE (created_at, id) > ($1, $2)) FROM %I.%I', 'platform_audit', v_name)
        INTO v_rows, v_beyond USING v_pos_at, v_pos_id;
    IF v_beyond > 0 THEN
        RAISE EXCEPTION 'drop_archived_audit_entry_partition: % holds % entries after the archived position', v_name, v_beyond USING ERRCODE = 'object_not_in_prerequisite_state';
    END IF;
    SELECT coalesce(sum(entry_count), 0) INTO v_archived FROM platform_audit.audit_archive_batches
     WHERE first_entry_created_at >= v_start AND first_entry_created_at < v_next AND last_entry_created_at >= v_start AND last_entry_created_at < v_next;
    IF v_rows <> v_archived THEN
        RAISE EXCEPTION 'drop_archived_audit_entry_partition: % holds % entries, the archived batches of the month hold %', v_name, v_rows, v_archived USING ERRCODE = 'object_not_in_prerequisite_state';
    END IF;
    EXECUTE format('ALTER TABLE %I.%I DETACH PARTITION %I.%I', 'platform_audit', 'audit_entries', 'platform_audit', v_name);
    EXECUTE format('DROP TABLE %I.%I', 'platform_audit', v_name);
    RETURN v_rows;
END;
$$;
REVOKE EXECUTE ON FUNCTION platform_audit.drop_archived_audit_entry_partition(date) FROM PUBLIC;
COMMENT ON FUNCTION platform_audit.drop_archived_audit_entry_partition(date) IS '维护角色删除已归档并校验的在线月分区；校验不过报错不删，返回删除的行数';

-- 初建 2026-10 起 6 个月分区（与迁移时刻无关）；之后由 AuditPartitionJob 按注入的时钟预建。
SELECT platform_audit.create_audit_entry_partitions(DATE '2026-10-01', DATE '2027-03-01');
