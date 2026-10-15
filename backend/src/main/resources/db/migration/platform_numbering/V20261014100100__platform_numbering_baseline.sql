CREATE SCHEMA IF NOT EXISTS platform_numbering;
COMMENT ON SCHEMA platform_numbering IS '单据编号（权限流程与集成平台 BC · 平台配置）';

-- 模板内容在 numbering_rule_versions；pattern、reset_policy、start_value、confirmation_status 是停用的旧列，不再写入。
CREATE TABLE platform_numbering.numbering_rules (
    numbering_rule_id   uuid        PRIMARY KEY,
    document_type       text        NOT NULL,
    pattern             text,
    reset_policy        text        CHECK (reset_policy IN ('NEVER', 'YEARLY', 'MONTHLY', 'DAILY')),
    start_value         bigint      CHECK (start_value >= 1),
    is_active           boolean     NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    confirmation_status text        DEFAULT 'PENDING_CONFIRMATION' CHECK (confirmation_status IN ('PENDING_CONFIRMATION', 'CONFIRMED')),
    event_count         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uk_numbering_rules_document_type UNIQUE (document_type),
    CONSTRAINT ck_numbering_rules_document_type CHECK (document_type IN ('QUOTE', 'TIMESHEET', 'JOURNAL_VOUCHER'))
);
COMMENT ON TABLE platform_numbering.numbering_rules IS '编号规则：每种单据一条，是版本的聚合根；模板内容在 numbering_rule_versions，由组织数据管理员维护、业务管理员确认（凭证号另需财务确认）。粒度：每种单据一行';
COMMENT ON COLUMN platform_numbering.numbering_rules.numbering_rule_id IS '编号规则 ID（UUIDv7）';
COMMENT ON COLUMN platform_numbering.numbering_rules.document_type IS '单据类型（DocumentType 枚举名）';
COMMENT ON COLUMN platform_numbering.numbering_rules.pattern IS '停用，由 numbering_rule_versions 取代';
COMMENT ON COLUMN platform_numbering.numbering_rules.reset_policy IS '流水号重置周期：停用，由 numbering_rule_versions 取代';
COMMENT ON COLUMN platform_numbering.numbering_rules.start_value IS '停用，由 numbering_rule_versions 取代';
COMMENT ON COLUMN platform_numbering.numbering_rules.is_active IS '是否启用';
COMMENT ON COLUMN platform_numbering.numbering_rules.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_rules.updated_at IS '更新时间';
COMMENT ON COLUMN platform_numbering.numbering_rules.version IS '乐观锁版本';
COMMENT ON COLUMN platform_numbering.numbering_rules.confirmation_status IS '确认状态：停用，由 numbering_rule_versions 取代';
COMMENT ON COLUMN platform_numbering.numbering_rules.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

CREATE TABLE platform_numbering.numbering_counters (
    numbering_counter_id uuid        NOT NULL,
    document_type        text        NOT NULL,
    counter_scope_kind   text        NOT NULL CONSTRAINT ck_numbering_counters_counter_scope_kind CHECK (counter_scope_kind IN ('GLOBAL', 'LEGAL_ENTITY')),
    legal_entity_id      uuid,
    period_key           text        NOT NULL,
    last_value           bigint      NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_numbering_counters PRIMARY KEY (numbering_counter_id),
    CONSTRAINT uk_numbering_counters_document_type_legal_entity_id_period_key UNIQUE NULLS NOT DISTINCT (document_type, legal_entity_id, period_key),
    CONSTRAINT ck_numbering_counters_scope_kind_matches_legal_entity CHECK ((counter_scope_kind = 'GLOBAL') = (legal_entity_id IS NULL))
);
CREATE INDEX ix_numbering_counters_legal_entity_id ON platform_numbering.numbering_counters (legal_entity_id);
COMMENT ON TABLE platform_numbering.numbering_counters IS '编号计数器：按单据类型 × 法人 × 周期计数，与业务写入同事务递增（不跳号）。粒度：每个单据类型、计数范围（全局或某法人）、周期一行';
COMMENT ON COLUMN platform_numbering.numbering_counters.numbering_counter_id IS '编号计数器 ID（UUIDv7）';
COMMENT ON COLUMN platform_numbering.numbering_counters.document_type IS '单据类型';
COMMENT ON COLUMN platform_numbering.numbering_counters.counter_scope_kind IS '计数范围类别：GLOBAL 全局（模板不含法人代码，各法人共用一个计数）/ LEGAL_ENTITY 按法人（每个法人各计各的）；与法人主体 ID 成对：GLOBAL 时法人为空，LEGAL_ENTITY 时法人必填';
COMMENT ON COLUMN platform_numbering.numbering_counters.legal_entity_id IS '法人主体 ID；计数范围为按法人时必填，为全局时为空（空 = 不分法人）';
COMMENT ON COLUMN platform_numbering.numbering_counters.period_key IS '周期键：- / 2026 / 202610 / 20261014';
COMMENT ON COLUMN platform_numbering.numbering_counters.last_value IS '已发出的最大流水号';
COMMENT ON COLUMN platform_numbering.numbering_counters.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_counters.updated_at IS '更新时间';

CREATE TABLE platform_numbering.legacy_numbers (
    id              uuid        PRIMARY KEY,
    document_type   text        NOT NULL,
    legacy_number   text        NOT NULL,
    document_id     uuid        NOT NULL,
    legal_entity_id uuid,
    import_batch    text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT legacy_numbers_type_number_key UNIQUE (document_type, legacy_number)
);
CREATE INDEX legacy_numbers_document_idx ON platform_numbering.legacy_numbers (document_type, document_id);
COMMENT ON TABLE platform_numbering.legacy_numbers IS '原编号：新系统单据与其迁移前编号的对照，只读检索；各单据在其切换阶段导入。粒度：每个单据类型的每个原编号一行';
COMMENT ON COLUMN platform_numbering.legacy_numbers.id IS '主键（UUIDv7）';
COMMENT ON COLUMN platform_numbering.legacy_numbers.document_type IS '单据类型（DocumentType 枚举名）；与原编号合起来唯一';
COMMENT ON COLUMN platform_numbering.legacy_numbers.legacy_number IS '原编号：迁移前该单据在老系统中的编号，按原样保留';
COMMENT ON COLUMN platform_numbering.legacy_numbers.document_id IS '单据：新系统单据 id，与单据类型合起来定位一张单据';
COMMENT ON COLUMN platform_numbering.legacy_numbers.legal_entity_id IS '法人主体 ID；导入时推不出法人的原编号留空，原编号仍可检索（D-1a-152）';
COMMENT ON COLUMN platform_numbering.legacy_numbers.import_batch IS '导入批次：写入这条对照的导入批次标识，便于整批核对与回溯';
COMMENT ON COLUMN platform_numbering.legacy_numbers.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.legacy_numbers.updated_at IS '更新时间';

-- ADR-0027 第 2 项第 4 条：编号模板按版本保存、不可变；作者不得确认自己的版本；未确认的规则不发号。
CREATE TABLE platform_numbering.numbering_rule_versions (
    numbering_rule_version_id uuid   NOT NULL,
    numbering_rule_id    uuid        NOT NULL,
    version_number       integer     NOT NULL CHECK (version_number >= 1),
    template_text        text        NOT NULL,
    reset_policy         text        NOT NULL CHECK (reset_policy IN ('NEVER', 'YEARLY', 'MONTHLY', 'DAILY')),
    start_value          bigint      NOT NULL CHECK (start_value >= 1),
    effective_from_date  date        NOT NULL,
    author_id            bigint,
    authored_at          timestamptz NOT NULL,
    status               text        NOT NULL CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'SUPERSEDED')),
    confirmed_by         bigint,
    confirmed_at         timestamptz,
    finance_confirmed_by bigint,
    finance_confirmed_at timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_numbering_rule_versions PRIMARY KEY (numbering_rule_version_id),
    CONSTRAINT uk_numbering_rule_versions_numbering_rule_id_version_number UNIQUE (numbering_rule_id, version_number),
    CONSTRAINT fk_numbering_rule_versions_numbering_rule_id FOREIGN KEY (numbering_rule_id) REFERENCES platform_numbering.numbering_rules (numbering_rule_id),
    CONSTRAINT ck_numbering_rule_versions_confirmer_is_not_author
        CHECK (author_id IS NULL OR ((confirmed_by IS NULL OR confirmed_by <> author_id) AND (finance_confirmed_by IS NULL OR finance_confirmed_by <> author_id))),
    CONSTRAINT ck_numbering_rule_versions_two_different_confirmers
        CHECK (confirmed_by IS NULL OR finance_confirmed_by IS NULL OR confirmed_by <> finance_confirmed_by),
    CONSTRAINT ck_numbering_rule_versions_confirmation_time
        CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL) AND (finance_confirmed_by IS NULL) = (finance_confirmed_at IS NULL))
);
CREATE UNIQUE INDEX uk_numbering_rule_versions_numbering_rule_id ON platform_numbering.numbering_rule_versions (numbering_rule_id)
    WHERE status = 'PENDING_CONFIRMATION';
COMMENT ON TABLE platform_numbering.numbering_rule_versions IS '编号规则版本：模板按版本保存，保存后内容不可变（触发器拒绝修改内容与删除），只补确认；每条规则至多一个待确认版本，已确认的最高版本发号。粒度：每种单据的每个模板版本一行';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.numbering_rule_version_id IS '编号规则版本 ID（UUIDv7）';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.numbering_rule_id IS '编号规则 ID：所属规则，规则的单据类型见 numbering_rules';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.version_number IS '版本号：每种单据从 1 递增，每保存一次新模板加 1';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.template_text IS '编号模板，占位符 {TYPE} {LE} {yyyy} {yy} {MM} {dd} {yyyyMM} {yyyyMMdd} {yyMMdd} {seq:N}（N 为 1 至 12），如 {TYPE}-{LE}-{yyyyMM}-{seq:4}';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.reset_policy IS '流水号重置周期：NEVER / YEARLY / MONTHLY / DAILY';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.start_value IS '每个周期的起始流水号';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.effective_from_date IS '生效日：作者提交内容的一部分，不可改写；业务日期从这一天起用该版本发号。确认时若早于当天则拒绝，作者改日期重新提交；种子默认模板为 2000-01-01';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.author_id IS '作者用户 ID：保存该版本的人；种子默认模板为空；确认人不得是作者（四眼）';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.authored_at IS '保存该版本的时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.status IS '版本状态：PENDING_CONFIRMATION 待确认 / CONFIRMED 已确认 / SUPERSEDED 被新版本取代而未经确认';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.confirmed_by IS '业务确认人用户 ID：持有 numbering-rule:approve 的人';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.confirmed_at IS '业务确认时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.finance_confirmed_by IS '财务确认人用户 ID：凭证号模板另需；与业务确认人、作者都不是同一人';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.finance_confirmed_at IS '财务确认时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.updated_at IS '更新时间';

CREATE FUNCTION platform_numbering.guard_rule_version_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_numbering.numbering_rule_versions is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.numbering_rule_id IS DISTINCT FROM OLD.numbering_rule_id OR NEW.version_number IS DISTINCT FROM OLD.version_number
       OR NEW.template_text IS DISTINCT FROM OLD.template_text OR NEW.reset_policy IS DISTINCT FROM OLD.reset_policy
       OR NEW.start_value IS DISTINCT FROM OLD.start_value OR NEW.effective_from_date IS DISTINCT FROM OLD.effective_from_date
       OR NEW.author_id IS DISTINCT FROM OLD.author_id OR NEW.authored_at IS DISTINCT FROM OLD.authored_at THEN
        RAISE EXCEPTION 'a saved numbering template version is immutable' USING ERRCODE = 'restrict_violation';
    END IF;
    IF (OLD.confirmed_by IS NOT NULL AND (NEW.confirmed_by IS DISTINCT FROM OLD.confirmed_by OR NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at))
       OR (OLD.finance_confirmed_by IS NOT NULL AND (NEW.finance_confirmed_by IS DISTINCT FROM OLD.finance_confirmed_by
           OR NEW.finance_confirmed_at IS DISTINCT FROM OLD.finance_confirmed_at)) THEN
        RAISE EXCEPTION 'a confirmation of a numbering template version cannot be changed or taken back' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (OLD.status = 'PENDING_CONFIRMATION' AND NEW.status IN ('CONFIRMED', 'SUPERSEDED')) THEN
        RAISE EXCEPTION 'a numbering template version cannot go from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER numbering_rule_versions_guard
    BEFORE UPDATE OR DELETE ON platform_numbering.numbering_rule_versions
    FOR EACH ROW EXECUTE FUNCTION platform_numbering.guard_rule_version_change();
CREATE TRIGGER tg_numbering_rule_versions_append_only_truncate
    BEFORE TRUNCATE ON platform_numbering.numbering_rule_versions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_numbering.guard_rule_version_change();

-- ADR-0010 第 5 项：统一新格式。三种单据都从默认模板起步，待管理员确认；老系统的编号格式不沿用（旧编号进 legacy_numbers）。
INSERT INTO platform_numbering.numbering_rules (numbering_rule_id, document_type, pattern, reset_policy, start_value, confirmation_status, version, event_count) VALUES
    ('019a0000-0000-7000-8000-000000000301', 'QUOTE', '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1, 'PENDING_CONFIRMATION', 1, 1),
    ('019a0000-0000-7000-8000-000000000302', 'TIMESHEET', '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1, 'PENDING_CONFIRMATION', 1, 1),
    ('019a0000-0000-7000-8000-000000000303', 'JOURNAL_VOUCHER', '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1, 'PENDING_CONFIRMATION', 1, 1);

-- 种子规则的模板成为版本 1，一律待确认：由业务管理员按四眼确认（凭证号模板另由财务确认）。
INSERT INTO platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value, effective_from_date, authored_at, status)
SELECT seeds.version_id, rules.numbering_rule_id, 1, rules.pattern, rules.reset_policy, rules.start_value, DATE '2000-01-01', now(), 'PENDING_CONFIRMATION'
  FROM platform_numbering.numbering_rules rules
  JOIN (VALUES ('QUOTE', '019a0000-0000-7000-8000-000000000501'::uuid), ('TIMESHEET', '019a0000-0000-7000-8000-000000000502'::uuid), ('JOURNAL_VOUCHER', '019a0000-0000-7000-8000-000000000503'::uuid)) AS seeds (document_type, version_id)
    ON seeds.document_type = rules.document_type;
