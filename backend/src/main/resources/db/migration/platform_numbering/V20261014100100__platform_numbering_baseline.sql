CREATE SCHEMA IF NOT EXISTS platform_numbering;
COMMENT ON SCHEMA platform_numbering IS '单据编号（权限流程与集成平台 BC · 平台配置）';

-- ADR-0066 决定 4：单据类型登记在这里，平台代码与表不枚举业务单据；新增一种单据 = 插一行，不动 DDL。
CREATE TABLE platform_numbering.document_types (
    document_type_id uuid        NOT NULL,
    code             text        NOT NULL,
    number_code     text,
    owner_bc_code         text        NOT NULL,
    is_finance_confirmation_required boolean NOT NULL,
    effective_from_date date        NOT NULL,
    retired_date date,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_document_types PRIMARY KEY (document_type_id),
    CONSTRAINT uk_document_types_code UNIQUE (code),
    CONSTRAINT uk_document_types_number_code UNIQUE (number_code),
    CONSTRAINT ck_document_types_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_document_types_number_code CHECK (number_code ~ '^[A-Z]{2,4}$'),
    CONSTRAINT ck_document_types_owner_bc_code CHECK (owner_bc_code ~ '^[a-z]+(\.[a-z]+)?$'),
    CONSTRAINT ck_document_types_retired_after_effective CHECK (retired_date IS NULL OR retired_date > effective_from_date)
);
COMMENT ON TABLE platform_numbering.document_types IS '单据类型登记：业务 BC 在自己的 api 声明类型码，编号模块登记；审批与编号写入时经编号模块接口校验类型码，其他 schema 不建外键。只由迁移增删，运行时只读。粒度：每种单据类型一行';
COMMENT ON COLUMN platform_numbering.document_types.document_type_id IS '单据类型 ID（UUIDv7）';
COMMENT ON COLUMN platform_numbering.document_types.code IS '单据类型码：业务键，大写字母、数字与下划线，2 至 40 位，全局唯一；已登记的码不改写';
COMMENT ON COLUMN platform_numbering.document_types.number_code IS '编号缩写码：编号模板占位符 {TYPE} 的取值，大写字母 2 至 4 位，全局唯一；为空表示该类型尚未登记进统一编号格式';
COMMENT ON COLUMN platform_numbering.document_types.owner_bc_code IS '登记属主：声明该单据类型的业务 BC（glossary/bcs.yaml 中的 id）；登记属主存在性由守护测试核对';
COMMENT ON COLUMN platform_numbering.document_types.is_finance_confirmation_required IS '是否须财务确认：该类型的编号模板除业务确认外另需财务确认（凭证号）';
COMMENT ON COLUMN platform_numbering.document_types.effective_from_date IS '生效日：从这一天起可用该类型码写入单据与发号';
COMMENT ON COLUMN platform_numbering.document_types.retired_date IS '退休日：为空表示长期有效；已有单据照常可读，从退休日起不再接受新写入';
COMMENT ON COLUMN platform_numbering.document_types.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.document_types.updated_at IS '更新时间';

CREATE FUNCTION platform_numbering.guard_document_type_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_numbering.document_types is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.document_type_id IS DISTINCT FROM OLD.document_type_id OR NEW.code IS DISTINCT FROM OLD.code
       OR NEW.owner_bc_code IS DISTINCT FROM OLD.owner_bc_code OR NEW.is_finance_confirmation_required IS DISTINCT FROM OLD.is_finance_confirmation_required
       OR NEW.effective_from_date IS DISTINCT FROM OLD.effective_from_date OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'a registered document type cannot be rewritten; only retired_date and number_code may be set, once' USING ERRCODE = 'restrict_violation';
    END IF;
    IF (OLD.retired_date IS NOT NULL AND NEW.retired_date IS DISTINCT FROM OLD.retired_date)
       OR (OLD.number_code IS NOT NULL AND NEW.number_code IS DISTINCT FROM OLD.number_code) THEN
        RAISE EXCEPTION 'retired_date and number_code of a document type are set once and never changed' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_document_types_append_only BEFORE UPDATE OR DELETE ON platform_numbering.document_types
    FOR EACH ROW EXECUTE FUNCTION platform_numbering.guard_document_type_change();
CREATE TRIGGER tg_document_types_append_only_truncate BEFORE TRUNCATE ON platform_numbering.document_types
    FOR EACH STATEMENT EXECUTE FUNCTION platform_numbering.guard_document_type_change();

CREATE TABLE platform_numbering.numbering_rules (
    numbering_rule_id   uuid        PRIMARY KEY,
    document_type_id    uuid        NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    event_count         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uk_numbering_rules_document_type_id UNIQUE (document_type_id),
    CONSTRAINT fk_numbering_rules_document_type_id FOREIGN KEY (document_type_id) REFERENCES platform_numbering.document_types (document_type_id) ON DELETE RESTRICT
);
COMMENT ON TABLE platform_numbering.numbering_rules IS '编号规则：每种单据一条，是版本的聚合根；模板内容在 numbering_rule_versions，由组织数据管理员维护、业务管理员确认（凭证号另需财务确认）。粒度：每种单据一行';
COMMENT ON COLUMN platform_numbering.numbering_rules.numbering_rule_id IS '编号规则 ID（UUIDv7）';
COMMENT ON COLUMN platform_numbering.numbering_rules.document_type_id IS '单据类型 ID：所属单据类型，见 document_types；每种类型一条规则';
COMMENT ON COLUMN platform_numbering.numbering_rules.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_rules.updated_at IS '更新时间';
COMMENT ON COLUMN platform_numbering.numbering_rules.version IS '乐观锁版本';
COMMENT ON COLUMN platform_numbering.numbering_rules.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

CREATE TABLE platform_numbering.numbering_counters (
    numbering_counter_id uuid        NOT NULL,
    document_type_id     uuid        NOT NULL,
    counter_scope_kind   text        NOT NULL CONSTRAINT ck_numbering_counters_counter_scope_kind CHECK (counter_scope_kind IN ('GLOBAL', 'LEGAL_ENTITY')),
    legal_entity_id      uuid,
    period_key           text        NOT NULL CONSTRAINT ck_numbering_counters_period_key CHECK (period_key ~ '^(-|[0-9]{4}|[0-9]{6}|[0-9]{8})$'),
    last_value           bigint      NOT NULL CONSTRAINT ck_numbering_counters_last_value CHECK (last_value BETWEEN 1 AND 999999999999),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_numbering_counters PRIMARY KEY (numbering_counter_id),
    CONSTRAINT uk_numbering_counters_document_type_legal_entity_period UNIQUE NULLS NOT DISTINCT (document_type_id, legal_entity_id, period_key),
    CONSTRAINT fk_numbering_counters_document_type_id FOREIGN KEY (document_type_id) REFERENCES platform_numbering.document_types (document_type_id) ON DELETE RESTRICT,
    CONSTRAINT ck_numbering_counters_scope_kind_matches_legal_entity CHECK ((counter_scope_kind = 'GLOBAL') = (legal_entity_id IS NULL))
);
CREATE INDEX ix_numbering_counters_legal_entity_id ON platform_numbering.numbering_counters (legal_entity_id);
COMMENT ON TABLE platform_numbering.numbering_counters IS '编号计数器：按单据类型 × 法人 × 周期计数，与业务写入同事务递增（不跳号）。粒度：每个单据类型、计数范围（全局或某法人）、周期一行';
COMMENT ON COLUMN platform_numbering.numbering_counters.numbering_counter_id IS '编号计数器 ID（UUIDv7）';
COMMENT ON COLUMN platform_numbering.numbering_counters.document_type_id IS '单据类型 ID：见 document_types';
COMMENT ON COLUMN platform_numbering.numbering_counters.counter_scope_kind IS '计数范围类别：GLOBAL 全局（模板不含法人代码，各法人共用一个计数）/ LEGAL_ENTITY 按法人（每个法人各计各的）；与法人主体 ID 成对：GLOBAL 时法人为空，LEGAL_ENTITY 时法人必填';
COMMENT ON COLUMN platform_numbering.numbering_counters.legal_entity_id IS '法人主体 ID；计数范围为按法人时必填，为全局时为空（空 = 不分法人）';
COMMENT ON COLUMN platform_numbering.numbering_counters.period_key IS '周期键：- / 2026 / 202610 / 20261014';
COMMENT ON COLUMN platform_numbering.numbering_counters.last_value IS '已发出的最大流水号：1 至 999999999999（{seq:12} 的容量）';
COMMENT ON COLUMN platform_numbering.numbering_counters.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_counters.updated_at IS '更新时间';

CREATE TABLE platform_numbering.legacy_numbers (
    id                 uuid        NOT NULL,
    source_system      text        NOT NULL CONSTRAINT ck_legacy_numbers_source_system CHECK (source_system IN ('ORG_SOURCE', 'LEGACY_SYSTEM', 'ACCOUNTING_SYSTEM')),
    document_type_id   uuid        NOT NULL,
    legacy_number      text        NOT NULL,
    document_id        uuid        NOT NULL,
    legal_entity_id    uuid,
    migration_batch_id uuid        NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_legacy_numbers PRIMARY KEY (id),
    CONSTRAINT uk_legacy_numbers_source_system_document_type_id_legacy_number UNIQUE (source_system, document_type_id, legacy_number),
    CONSTRAINT fk_legacy_numbers_document_type_id FOREIGN KEY (document_type_id) REFERENCES platform_numbering.document_types (document_type_id) ON DELETE RESTRICT
);
CREATE INDEX ix_legacy_numbers_document_type_id ON platform_numbering.legacy_numbers (document_type_id, document_id);
COMMENT ON TABLE platform_numbering.legacy_numbers IS '原编号：新系统单据与其迁移前编号的对照，只读检索，只追加；各单据在其切换阶段导入。粒度：每个来源系统的每个单据类型的每个原编号一行';
COMMENT ON COLUMN platform_numbering.legacy_numbers.id IS '主键（UUIDv7）';
COMMENT ON COLUMN platform_numbering.legacy_numbers.source_system IS '来源系统：该原编号出自哪个系统，ORG_SOURCE 组织源 / LEGACY_SYSTEM 老系统 / ACCOUNTING_SYSTEM 核算系统；与单据类型、原编号合起来唯一';
COMMENT ON COLUMN platform_numbering.legacy_numbers.document_type_id IS '单据类型 ID：见 document_types；与来源系统、原编号合起来唯一';
COMMENT ON COLUMN platform_numbering.legacy_numbers.legacy_number IS '原编号：迁移前该单据在来源系统中的编号，按原样保留';
COMMENT ON COLUMN platform_numbering.legacy_numbers.document_id IS '单据：新系统单据 id，与单据类型合起来定位一张单据';
COMMENT ON COLUMN platform_numbering.legacy_numbers.legal_entity_id IS '法人主体 ID；导入时推不出法人的原编号留空，原编号仍可检索（D-1a-152）';
COMMENT ON COLUMN platform_numbering.legacy_numbers.migration_batch_id IS '迁入批次 ID：写入这条对照的迁入批次，便于整批核对与回溯；批次登记在 platform_integration（ADR-0036），跨 schema 只按 ID 引用，不建外键';
COMMENT ON COLUMN platform_numbering.legacy_numbers.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.legacy_numbers.updated_at IS '更新时间（行不可更新，恒等于创建时间）';

CREATE FUNCTION platform_numbering.reject_legacy_number_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'platform_numbering.legacy_numbers is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;
CREATE TRIGGER tg_legacy_numbers_append_only BEFORE UPDATE OR DELETE ON platform_numbering.legacy_numbers
    FOR EACH ROW EXECUTE FUNCTION platform_numbering.reject_legacy_number_change();
CREATE TRIGGER tg_legacy_numbers_append_only_truncate BEFORE TRUNCATE ON platform_numbering.legacy_numbers
    FOR EACH STATEMENT EXECUTE FUNCTION platform_numbering.reject_legacy_number_change();

-- ADR-0027 第 2 项第 4 条：编号模板按版本保存、不可变；作者不得确认自己的版本；未确认的规则不发号。
CREATE TABLE platform_numbering.numbering_rule_versions (
    numbering_rule_version_id uuid   NOT NULL,
    numbering_rule_id    uuid        NOT NULL,
    version_number       integer     NOT NULL CHECK (version_number >= 1),
    template_text        text        NOT NULL,
    reset_policy         text        NOT NULL CHECK (reset_policy IN ('NEVER', 'YEARLY', 'MONTHLY', 'DAILY')),
    start_value          bigint      NOT NULL CHECK (start_value >= 1),
    effective_from_date  date        NOT NULL,
    author_id            uuid,
    authored_at          timestamptz NOT NULL,
    status               text        NOT NULL CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'SUPERSEDED')),
    source_kind          text        NOT NULL CONSTRAINT ck_numbering_rule_versions_source_kind CHECK (source_kind IN ('AUTHORED', 'SEEDED', 'MIGRATION')),
    confirmed_by         uuid,
    confirmed_at         timestamptz,
    finance_confirmed_by uuid,
    finance_confirmed_at timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_numbering_rule_versions PRIMARY KEY (numbering_rule_version_id),
    CONSTRAINT uk_numbering_rule_versions_numbering_rule_id_version_number UNIQUE (numbering_rule_id, version_number),
    CONSTRAINT fk_numbering_rule_versions_numbering_rule_id FOREIGN KEY (numbering_rule_id) REFERENCES platform_numbering.numbering_rules (numbering_rule_id),
    CONSTRAINT ck_numbering_rule_versions_author_by_source
        CHECK ((author_id IS NULL) = (source_kind IN ('SEEDED', 'MIGRATION'))),
    CONSTRAINT ck_numbering_rule_versions_confirmer_is_not_author
        CHECK (author_id IS NULL OR ((confirmed_by IS NULL OR confirmed_by <> author_id) AND (finance_confirmed_by IS NULL OR finance_confirmed_by <> author_id))),
    CONSTRAINT ck_numbering_rule_versions_two_different_confirmers
        CHECK (confirmed_by IS NULL OR finance_confirmed_by IS NULL OR confirmed_by <> finance_confirmed_by),
    CONSTRAINT ck_numbering_rule_versions_confirmation_time
        CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL) AND (finance_confirmed_by IS NULL) = (finance_confirmed_at IS NULL)),
    CONSTRAINT ck_numbering_rule_versions_confirmed
        CHECK (status <> 'CONFIRMED' OR confirmed_by IS NOT NULL)
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
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.source_kind IS '来源类别：AUTHORED 界面起草（作者必填，确认人不得是作者）/ SEEDED 种子默认模板 / MIGRATION 迁入；只有种子与迁入的行允许作者为空：种子只由属主角色成员写，迁入只由属主角色成员或其迁入角色成员写（插入触发器核对），运行角色写不进';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.status IS '版本状态：PENDING_CONFIRMATION 待确认 / CONFIRMED 已确认 / SUPERSEDED 被新版本取代而未经确认';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.confirmed_by IS '业务确认人用户 ID：持有 numbering-rule:approve 的人；为空表示尚未业务确认，已确认状态必填';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.confirmed_at IS '业务确认时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.finance_confirmed_by IS '财务确认人用户 ID：凭证号模板另需；与业务确认人、作者都不是同一人';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.finance_confirmed_at IS '财务确认时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.updated_at IS '更新时间';

CREATE FUNCTION platform_numbering.guard_rule_version_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_numbering.numbering_rule_versions is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.numbering_rule_id IS DISTINCT FROM OLD.numbering_rule_id OR NEW.version_number IS DISTINCT FROM OLD.version_number
       OR NEW.template_text IS DISTINCT FROM OLD.template_text OR NEW.reset_policy IS DISTINCT FROM OLD.reset_policy
       OR NEW.start_value IS DISTINCT FROM OLD.start_value OR NEW.effective_from_date IS DISTINCT FROM OLD.effective_from_date
       OR NEW.author_id IS DISTINCT FROM OLD.author_id OR NEW.authored_at IS DISTINCT FROM OLD.authored_at
       OR NEW.source_kind IS DISTINCT FROM OLD.source_kind THEN
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
-- 判定函数是 SECURITY INVOKER：current_user 就是执行插入的角色，若改成 SECURITY DEFINER 则恒为属主、等于放行所有人。
CREATE FUNCTION platform_numbering.is_owner_member(table_oid oid) RETURNS boolean
    LANGUAGE sql STABLE SET search_path = pg_catalog, pg_temp AS $$
    SELECT COALESCE(pg_has_role(current_user, (SELECT relowner FROM pg_catalog.pg_class WHERE oid = table_oid), 'MEMBER'), false)
$$;
-- 迁入角色的名字由属主角色名推出（ADR-0036：one_<环境>_owner 对 one_<环境>_history_loader），按推出的名字精确比对，不用正则匹配；该角色须由部署脚本先建好，否则持 CREATEROLE 的账号可抢建同名角色。
CREATE FUNCTION platform_numbering.is_migration_writer(table_oid oid) RETURNS boolean
    LANGUAGE sql STABLE SET search_path = pg_catalog, pg_temp AS $$
    SELECT platform_numbering.is_owner_member(table_oid) OR COALESCE(pg_has_role(current_user,
        (SELECT r.oid FROM pg_catalog.pg_roles r
          WHERE r.rolname = regexp_replace(pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid = table_oid)), '_owner$', '_history_loader')
            AND r.rolname <> pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid = table_oid))), 'MEMBER'), false)
$$;
-- 作者可空的行只由迁移写：种子行只认属主角色成员，迁入行认属主角色成员或其迁入角色成员，运行角色两者都不是（c3/c9 校验脚本核对），在这里被拒。
-- 非属主角色（运行角色、迁入角色）只能以待确认插入，任何来源类别都一样：确认要由管理员与财务在界面完成，迁入角色没有 UPDATE，也不能把四眼确认写进插入语句；迁入行连属主也只能待确认插入。
CREATE FUNCTION platform_numbering.guard_rule_version_source() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF NEW.source_kind <> 'AUTHORED' AND NOT (CASE NEW.source_kind WHEN 'MIGRATION' THEN platform_numbering.is_migration_writer(TG_RELID)
                                                                   ELSE platform_numbering.is_owner_member(TG_RELID) END) THEN
        RAISE EXCEPTION 'a numbering template version that is not authored (%) can only be written by a migration', NEW.source_kind USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.source_kind = 'MIGRATION' AND (NEW.status <> 'PENDING_CONFIRMATION' OR NEW.confirmed_by IS NOT NULL OR NEW.finance_confirmed_by IS NOT NULL) THEN
        RAISE EXCEPTION 'a migrated numbering template version must be inserted pending confirmation' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NOT platform_numbering.is_owner_member(TG_RELID) AND (NEW.status <> 'PENDING_CONFIRMATION' OR NEW.confirmed_by IS NOT NULL OR NEW.finance_confirmed_by IS NOT NULL) THEN
        RAISE EXCEPTION 'a numbering template version can only be inserted pending confirmation by this role' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.source_kind = 'AUTHORED' AND NOT platform_numbering.is_owner_member(TG_RELID) AND platform_numbering.is_migration_writer(TG_RELID) THEN
        RAISE EXCEPTION 'the migration role can only write migrated numbering template versions' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_numbering_rule_versions_source BEFORE INSERT ON platform_numbering.numbering_rule_versions
    FOR EACH ROW EXECUTE FUNCTION platform_numbering.guard_rule_version_source();
CREATE TRIGGER numbering_rule_versions_guard
    BEFORE UPDATE OR DELETE ON platform_numbering.numbering_rule_versions
    FOR EACH ROW EXECUTE FUNCTION platform_numbering.guard_rule_version_change();
CREATE TRIGGER tg_numbering_rule_versions_append_only_truncate
    BEFORE TRUNCATE ON platform_numbering.numbering_rule_versions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_numbering.guard_rule_version_change();

-- ADR-0010 第 5 项：统一新格式。三种单据都从默认模板起步，待管理员确认；老系统的编号格式不沿用（旧编号进 legacy_numbers）。
INSERT INTO platform_numbering.document_types (document_type_id, code, number_code, owner_bc_code, is_finance_confirmation_required, effective_from_date) VALUES
    ('019a0000-0000-7000-8000-000000000401', 'QUOTE', 'QT', 'presales.opportunity', false, DATE '2000-01-01'),
    ('019a0000-0000-7000-8000-000000000402', 'TIMESHEET', 'TS', 'cost.timesheet', false, DATE '2000-01-01'),
    ('019a0000-0000-7000-8000-000000000403', 'JOURNAL_VOUCHER', 'JV', 'accounting.ledger', true, DATE '2000-01-01');

INSERT INTO platform_numbering.numbering_rules (numbering_rule_id, document_type_id, version, event_count) VALUES
    ('019a0000-0000-7000-8000-000000000301', '019a0000-0000-7000-8000-000000000401', 1, 0),
    ('019a0000-0000-7000-8000-000000000302', '019a0000-0000-7000-8000-000000000402', 1, 0),
    ('019a0000-0000-7000-8000-000000000303', '019a0000-0000-7000-8000-000000000403', 1, 0);

-- 种子规则的模板成为版本 1，一律待确认：由业务管理员按四眼确认（凭证号模板另由财务确认）。
INSERT INTO platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value, effective_from_date, authored_at, status, source_kind)
SELECT seeds.version_id, rules.numbering_rule_id, 1, '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1, DATE '2000-01-01', now(), 'PENDING_CONFIRMATION', 'SEEDED'
  FROM platform_numbering.numbering_rules rules
  JOIN (VALUES ('019a0000-0000-7000-8000-000000000401'::uuid, '019a0000-0000-7000-8000-000000000501'::uuid), ('019a0000-0000-7000-8000-000000000402'::uuid, '019a0000-0000-7000-8000-000000000502'::uuid), ('019a0000-0000-7000-8000-000000000403'::uuid, '019a0000-0000-7000-8000-000000000503'::uuid)) AS seeds (document_type_id, version_id)
    ON seeds.document_type_id = rules.document_type_id;
