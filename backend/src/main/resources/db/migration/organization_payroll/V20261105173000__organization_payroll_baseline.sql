-- 薪酬 PY0：#1429，逐表依据签署的数据设计 organization-payroll-py0（#1425）。只建结构，不写任何数据行。
CREATE SCHEMA IF NOT EXISTS organization_payroll;
COMMENT ON SCHEMA organization_payroll IS '薪酬；PY0 两表，薪酬规则版本及其规则行，无数值';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: database setup creates it before migrations';
    END IF;
END $$;

-- INV-139（ex_ 约束）：同类别同适用地区已生效与已被取代的区间不重叠；草稿可重叠，发布时才检查。
CREATE TABLE organization_payroll.payroll_rule_versions (
    id uuid NOT NULL,
    rule_kind text NOT NULL,
    jurisdiction_key text NOT NULL,
    effective_from_date date NOT NULL,
    effective_to_date date,
    status text NOT NULL,
    drafted_by_user_id uuid NOT NULL,
    approved_by_user_id uuid,
    published_at timestamptz,
    source_note text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_payroll_rule_versions PRIMARY KEY (id),
    CONSTRAINT ck_payroll_rule_versions_rule_kind CHECK (rule_kind IN ('INCOME_TAX', 'SOCIAL_INSURANCE', 'HOUSING_FUND', 'ROUNDING')),
    CONSTRAINT ck_payroll_rule_versions_jurisdiction_key CHECK (jurisdiction_key ~ '^[A-Z][A-Z0-9_]{1,31}$'),
    CONSTRAINT ck_payroll_rule_versions_effective_interval CHECK (effective_to_date IS NULL OR effective_to_date > effective_from_date),
    CONSTRAINT ck_payroll_rule_versions_status CHECK (status IN ('DRAFT', 'EFFECTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_payroll_rule_versions_approver_columns CHECK ((status = 'DRAFT') = (approved_by_user_id IS NULL) AND (status = 'DRAFT') = (published_at IS NULL)),
    CONSTRAINT ck_payroll_rule_versions_drafter_not_approver CHECK (approved_by_user_id IS NULL OR approved_by_user_id <> drafted_by_user_id),
    CONSTRAINT ck_payroll_rule_versions_supersede_has_end CHECK (status <> 'SUPERSEDED' OR effective_to_date IS NOT NULL),
    CONSTRAINT ck_payroll_rule_versions_source_note CHECK (char_length(source_note) BETWEEN 1 AND 500 AND source_note = btrim(source_note)),
    CONSTRAINT ex_payroll_rule_versions_effective_interval_overlap EXCLUDE USING gist (rule_kind WITH =, jurisdiction_key WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (status IN ('EFFECTIVE', 'SUPERSEDED'))
);

CREATE INDEX ix_payroll_rule_versions_lookup ON organization_payroll.payroll_rule_versions (rule_kind, jurisdiction_key, effective_from_date);

-- ck_payroll_rule_rows_kind_columns：每种行类别恰好设哪些值列、哪些必须为空。
CREATE TABLE organization_payroll.payroll_rule_rows (
    id uuid NOT NULL,
    rule_version_id uuid NOT NULL,
    row_kind text NOT NULL,
    row_key text NOT NULL,
    party_kind text,
    lower_bound_amount numeric(19,2),
    lower_bound_currency text,
    upper_bound_amount numeric(19,2),
    upper_bound_currency text,
    applied_rate numeric(9,6),
    quick_deduction_amount numeric(19,2),
    quick_deduction_currency text,
    flat_amount numeric(19,2),
    flat_currency text,
    rounding_kind text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_payroll_rule_rows PRIMARY KEY (id),
    CONSTRAINT fk_payroll_rule_rows_rule_version_id FOREIGN KEY (rule_version_id) REFERENCES organization_payroll.payroll_rule_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uk_payroll_rule_rows_version_kind_key_party UNIQUE NULLS NOT DISTINCT (rule_version_id, row_kind, row_key, party_kind),
    CONSTRAINT ck_payroll_rule_rows_row_kind CHECK (row_kind IN ('INCOME_TAX_BAND', 'INCOME_TAX_MONTHLY_BASIC_DEDUCTION', 'SOCIAL_INSURANCE_RATE', 'SOCIAL_INSURANCE_BASE_LIMIT', 'HOUSING_FUND_RATE', 'HOUSING_FUND_BASE_LIMIT', 'ROUNDING_MODE')),
    CONSTRAINT ck_payroll_rule_rows_row_key CHECK (row_key ~ '^[A-Za-z0-9_-]{1,40}$' AND (row_kind <> 'INCOME_TAX_BAND' OR row_key ~ '^[0-9]{2}$')),
    CONSTRAINT ck_payroll_rule_rows_party_kind CHECK (party_kind IS NULL OR party_kind IN ('EMPLOYER', 'EMPLOYEE')),
    CONSTRAINT ck_payroll_rule_rows_lower_bound_pair CHECK ((lower_bound_amount IS NULL) = (lower_bound_currency IS NULL)),
    CONSTRAINT ck_payroll_rule_rows_upper_bound_pair CHECK ((upper_bound_amount IS NULL) = (upper_bound_currency IS NULL)),
    CONSTRAINT ck_payroll_rule_rows_quick_deduction_pair CHECK ((quick_deduction_amount IS NULL) = (quick_deduction_currency IS NULL)),
    CONSTRAINT ck_payroll_rule_rows_flat_pair CHECK ((flat_amount IS NULL) = (flat_currency IS NULL)),
    CONSTRAINT ck_payroll_rule_rows_currency CHECK (COALESCE(lower_bound_currency, 'CNY') = 'CNY' AND COALESCE(upper_bound_currency, 'CNY') = 'CNY'
        AND COALESCE(quick_deduction_currency, 'CNY') = 'CNY' AND COALESCE(flat_currency, 'CNY') = 'CNY'),
    CONSTRAINT ck_payroll_rule_rows_amounts_not_negative CHECK (COALESCE(lower_bound_amount, 0) >= 0 AND COALESCE(upper_bound_amount, 0) >= 0
        AND COALESCE(quick_deduction_amount, 0) >= 0 AND COALESCE(flat_amount, 0) >= 0),
    CONSTRAINT ck_payroll_rule_rows_applied_rate CHECK (applied_rate IS NULL OR applied_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_payroll_rule_rows_rounding_kind CHECK (rounding_kind IS NULL OR rounding_kind IN ('HALF_UP', 'HALF_EVEN', 'DOWN')),
    CONSTRAINT ck_payroll_rule_rows_bounds_order CHECK (lower_bound_amount IS NULL OR upper_bound_amount IS NULL OR upper_bound_amount > lower_bound_amount),
    CONSTRAINT ck_payroll_rule_rows_kind_columns CHECK (CASE row_kind
        WHEN 'INCOME_TAX_BAND' THEN party_kind IS NULL AND lower_bound_amount IS NOT NULL AND applied_rate IS NOT NULL AND quick_deduction_amount IS NOT NULL
            AND flat_amount IS NULL AND rounding_kind IS NULL
        WHEN 'INCOME_TAX_MONTHLY_BASIC_DEDUCTION' THEN party_kind IS NULL AND flat_amount IS NOT NULL AND lower_bound_amount IS NULL AND upper_bound_amount IS NULL
            AND applied_rate IS NULL AND quick_deduction_amount IS NULL AND rounding_kind IS NULL
        WHEN 'SOCIAL_INSURANCE_RATE' THEN party_kind IS NOT NULL AND applied_rate IS NOT NULL AND lower_bound_amount IS NULL AND upper_bound_amount IS NULL
            AND quick_deduction_amount IS NULL AND flat_amount IS NULL AND rounding_kind IS NULL
        WHEN 'HOUSING_FUND_RATE' THEN party_kind IS NOT NULL AND applied_rate IS NOT NULL AND lower_bound_amount IS NULL AND upper_bound_amount IS NULL
            AND quick_deduction_amount IS NULL AND flat_amount IS NULL AND rounding_kind IS NULL
        WHEN 'SOCIAL_INSURANCE_BASE_LIMIT' THEN party_kind IS NULL AND lower_bound_amount IS NOT NULL AND upper_bound_amount IS NOT NULL
            AND applied_rate IS NULL AND quick_deduction_amount IS NULL AND flat_amount IS NULL AND rounding_kind IS NULL
        WHEN 'HOUSING_FUND_BASE_LIMIT' THEN party_kind IS NULL AND lower_bound_amount IS NOT NULL AND upper_bound_amount IS NOT NULL
            AND applied_rate IS NULL AND quick_deduction_amount IS NULL AND flat_amount IS NULL AND rounding_kind IS NULL
        WHEN 'ROUNDING_MODE' THEN party_kind IS NULL AND rounding_kind IS NOT NULL AND lower_bound_amount IS NULL AND upper_bound_amount IS NULL
            AND applied_rate IS NULL AND quick_deduction_amount IS NULL AND flat_amount IS NULL
        ELSE false END)
);

-- 已发布版本不可改写（D15）：全部合法写入如下，其余一概拒绝。
CREATE FUNCTION organization_payroll.guard_rule_version_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'payroll_rule_versions cannot be truncated' USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'a payroll rule version is inserted as DRAFT' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'only a DRAFT payroll rule version can be deleted' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'id and created_at of a payroll rule version never change' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status = 'DRAFT' AND NEW.status = 'DRAFT' THEN
        RETURN NEW;
    END IF;
    IF OLD.status = 'DRAFT' AND NEW.status = 'EFFECTIVE' THEN
        IF NEW.rule_kind IS DISTINCT FROM OLD.rule_kind OR NEW.jurisdiction_key IS DISTINCT FROM OLD.jurisdiction_key
                OR NEW.effective_from_date IS DISTINCT FROM OLD.effective_from_date OR NEW.effective_to_date IS DISTINCT FROM OLD.effective_to_date
                OR NEW.drafted_by_user_id IS DISTINCT FROM OLD.drafted_by_user_id OR NEW.source_note IS DISTINCT FROM OLD.source_note THEN
            RAISE EXCEPTION 'publishing a payroll rule version changes only its status and approval' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status = 'EFFECTIVE' AND NEW.status = 'SUPERSEDED' THEN
        IF OLD.effective_to_date IS NOT NULL OR NEW.rule_kind IS DISTINCT FROM OLD.rule_kind OR NEW.jurisdiction_key IS DISTINCT FROM OLD.jurisdiction_key
                OR NEW.effective_from_date IS DISTINCT FROM OLD.effective_from_date OR NEW.drafted_by_user_id IS DISTINCT FROM OLD.drafted_by_user_id
                OR NEW.approved_by_user_id IS DISTINCT FROM OLD.approved_by_user_id OR NEW.published_at IS DISTINCT FROM OLD.published_at
                OR NEW.source_note IS DISTINCT FROM OLD.source_note THEN
            RAISE EXCEPTION 'superseding a payroll rule version only closes its open interval' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'a % payroll rule version cannot become %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_payroll_rule_versions_change BEFORE INSERT OR UPDATE OR DELETE ON organization_payroll.payroll_rule_versions
    FOR EACH ROW EXECUTE FUNCTION organization_payroll.guard_rule_version_change();
CREATE TRIGGER tg_payroll_rule_versions_truncate BEFORE TRUNCATE ON organization_payroll.payroll_rule_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_payroll.guard_rule_version_change();

-- 规则行只在所属版本还是草稿时可写；FOR SHARE 锁住父版本，并发的发布不能把行塞进刚生效的版本。
CREATE FUNCTION organization_payroll.guard_rule_row_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    parent_status text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'payroll_rule_rows cannot be truncated' USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP <> 'INSERT' THEN
        SELECT status INTO parent_status FROM organization_payroll.payroll_rule_versions WHERE id = OLD.rule_version_id FOR SHARE;
        IF parent_status IS DISTINCT FROM 'DRAFT' THEN
            RAISE EXCEPTION 'the rows of a published payroll rule version never change' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        SELECT status INTO parent_status FROM organization_payroll.payroll_rule_versions WHERE id = NEW.rule_version_id FOR SHARE;
        IF parent_status IS DISTINCT FROM 'DRAFT' THEN
            RAISE EXCEPTION 'the rows of a published payroll rule version never change' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_payroll_rule_rows_change BEFORE INSERT OR UPDATE OR DELETE ON organization_payroll.payroll_rule_rows
    FOR EACH ROW EXECUTE FUNCTION organization_payroll.guard_rule_row_change();
CREATE TRIGGER tg_payroll_rule_rows_truncate BEFORE TRUNCATE ON organization_payroll.payroll_rule_rows
    FOR EACH STATEMENT EXECUTE FUNCTION organization_payroll.guard_rule_row_change();

COMMENT ON TABLE organization_payroll.payroll_rule_versions IS '薪酬规则版本。粒度：每个薪酬规则类别 × 适用地区 × 生效区间一行，已生效的行不可改，只能被后继版本关闭区间。';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.rule_kind IS '规则类别：INCOME_TAX 个人所得税、SOCIAL_INSURANCE 社会保险、HOUSING_FUND 住房公积金、ROUNDING 舍入';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.jurisdiction_key IS '适用地区代码：全国个税规则用 CN，社保与公积金规则用地区键；取值是数据，不在约束里列举';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.effective_from_date IS '生效日（含），区间自此日起';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.effective_to_date IS '生效截止日（不含），后继版本的起始日；为空表示尚无后继版本，区间开放';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.status IS '版本状态：DRAFT 草稿、EFFECTIVE 已生效、SUPERSEDED 已被后继版本取代';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.drafted_by_user_id IS '起草人用户 ID，跨上下文不建外键';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.approved_by_user_id IS '批准人用户 ID，不得与起草人相同；为空表示尚未发布';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.published_at IS '发布时刻；为空表示尚未发布';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.source_note IS '依据说明：法规条款、地方通知或遗留系统证据的引用，只写引用，不写个人信息';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.created_at IS '创建时刻';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.updated_at IS '更新时刻';
COMMENT ON COLUMN organization_payroll.payroll_rule_versions.version IS '乐观锁版本';

COMMENT ON TABLE organization_payroll.payroll_rule_rows IS '薪酬规则行。粒度：每个规则版本 × 行类别 × 键 × 一方一行，版本生效后冻结。';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.rule_version_id IS '规则版本 ID，所属版本';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.row_kind IS '规则行类别：INCOME_TAX_BAND 个税税率档、INCOME_TAX_MONTHLY_BASIC_DEDUCTION 个税月基本减除、SOCIAL_INSURANCE_RATE 社保比例、SOCIAL_INSURANCE_BASE_LIMIT 社保基数上下限、HOUSING_FUND_RATE 公积金比例、HOUSING_FUND_BASE_LIMIT 公积金基数上下限、ROUNDING_MODE 舍入方式';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.row_key IS '规则行键：险种键，税率档用两位补零序号，同类别只有一行时用 -';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.party_kind IS '缴费方：EMPLOYER 单位、EMPLOYEE 个人；为空表示该行无一方之分';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.lower_bound_amount IS '区间下限金额；为空表示该类别无下限';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.lower_bound_currency IS '区间下限币种，只有 CNY；与金额同空同非空';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.upper_bound_amount IS '上限金额；为空表示无上限（最高档或该类别无上限）';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.upper_bound_currency IS '上限币种，只有 CNY；与金额同空同非空';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.applied_rate IS '适用比例，0 至 1；为空表示该类别无比例';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.quick_deduction_amount IS '速算扣除数金额；为空表示不是税率档';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.quick_deduction_currency IS '速算扣除数币种，只有 CNY；与金额同空同非空';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.flat_amount IS '定额金额，月基本减除用；为空表示该类别无定额';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.flat_currency IS '定额币种，只有 CNY；与金额同空同非空';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.rounding_kind IS '取整方式：HALF_UP 四舍五入、HALF_EVEN 银行家舍入、DOWN 截断；为空表示不是舍入行';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.created_at IS '创建时刻';
COMMENT ON COLUMN organization_payroll.payroll_rule_rows.updated_at IS '更新时刻';
