-- Lab #30：仅 accounting-spine-f1 签署的三张空表及序列。
-- Runtime prerequisite, not established by this migration:
-- migration runs as the trusted DDL owner; runtime DML users cannot become that
-- owner, change its functions/tables/triggers, or suppress triggers. The owner
-- needs database TEMP privilege. No application/owner role is created or granted.
-- The transaction guard creates one owner-only TEMP UUID membership table,
-- ON COMMIT DROP. It is not a fourth persistent domain table.
CREATE SCHEMA IF NOT EXISTS operations_facts;
COMMENT ON SCHEMA operations_facts IS '过账事实；F1 仅建立空表结构，不提供过账命令';

CREATE SEQUENCE operations_facts.posting_fact_post_seq AS bigint;

CREATE TABLE operations_facts.posting_facts (
    id uuid NOT NULL,
    post_number bigint NOT NULL DEFAULT nextval('operations_facts.posting_fact_post_seq'),
    legal_entity_id uuid NOT NULL,
    fact_type text NOT NULL,
    business_date date NOT NULL,
    posted_fiscal_year_number integer NOT NULL,
    posted_period_number integer NOT NULL,
    original_fiscal_year_number integer,
    original_period_number integer,
    source_context_code text NOT NULL,
    source_id uuid NOT NULL,
    idempotency_key text NOT NULL,
    reverses_fact_id uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_posting_facts PRIMARY KEY (id),
    CONSTRAINT uk_posting_facts_post_number UNIQUE (post_number),
    CONSTRAINT uk_posting_facts_replay_key
        UNIQUE (legal_entity_id, source_context_code, idempotency_key),
    CONSTRAINT uk_posting_facts_reverses_fact_id UNIQUE (reverses_fact_id),
    CONSTRAINT fk_posting_facts_reverses_fact_id
        FOREIGN KEY (reverses_fact_id) REFERENCES operations_facts.posting_facts(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_posting_facts_fact_type CHECK (fact_type IN ('PAYROLL')),
    CONSTRAINT ck_posting_facts_posted_fiscal_year_number
        CHECK (posted_fiscal_year_number BETWEEN 2000 AND 2999),
    CONSTRAINT ck_posting_facts_posted_period_number
        CHECK (posted_period_number BETWEEN 1 AND 12),
    CONSTRAINT ck_posting_facts_original_fiscal_year_number
        CHECK (original_fiscal_year_number BETWEEN 2000 AND 2999),
    CONSTRAINT ck_posting_facts_original_period_number
        CHECK (original_period_number BETWEEN 1 AND 12),
    CONSTRAINT ck_posting_facts_original_pair CHECK (
        (original_fiscal_year_number IS NULL AND original_period_number IS NULL)
        OR
        (original_fiscal_year_number IS NOT NULL AND original_period_number IS NOT NULL)
    ),
    CONSTRAINT ck_posting_facts_source_context_code
        CHECK (source_context_code ~ '^[a-z]+(\.[a-z]+)+$'),
    CONSTRAINT ck_posting_facts_idempotency_key CHECK (
        idempotency_key = btrim(idempotency_key)
        AND char_length(idempotency_key) BETWEEN 1 AND 200
    ),
    CONSTRAINT ck_posting_facts_reverses_not_self CHECK (reverses_fact_id <> id),
    CONSTRAINT ck_posting_facts_audit_times CHECK (updated_at = created_at)
);

ALTER SEQUENCE operations_facts.posting_fact_post_seq
    OWNED BY operations_facts.posting_facts.post_number;

CREATE INDEX ix_posting_facts_legal_entity_id_post_number
    ON operations_facts.posting_facts (legal_entity_id, post_number);
CREATE INDEX ix_posting_facts_legal_entity_id_posted_period
    ON operations_facts.posting_facts
        (legal_entity_id, posted_fiscal_year_number, posted_period_number, post_number);
CREATE INDEX ix_posting_facts_source
    ON operations_facts.posting_facts (source_context_code, source_id);

CREATE TABLE operations_facts.posting_components (
    id uuid NOT NULL,
    fact_type text NOT NULL,
    component_key text NOT NULL,
    side_hint_kind text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_posting_components PRIMARY KEY (id),
    CONSTRAINT uk_posting_components_fact_type_component_key
        UNIQUE (fact_type, component_key),
    CONSTRAINT ck_posting_components_fact_type CHECK (fact_type IN ('PAYROLL')),
    CONSTRAINT ck_posting_components_component_key
        CHECK (component_key ~ '^[A-Z][A-Z0-9_]{1,59}$'),
    CONSTRAINT ck_posting_components_side_hint_kind
        CHECK (side_hint_kind IN ('DEBIT', 'CREDIT'))
);

CREATE TABLE operations_facts.posting_fact_lines (
    id uuid NOT NULL,
    fact_id uuid NOT NULL,
    line_number integer NOT NULL,
    side_kind text NOT NULL,
    component_key text NOT NULL,
    line_amount numeric(19,2) NOT NULL,
    line_currency text NOT NULL,
    department_id uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_posting_fact_lines PRIMARY KEY (id),
    CONSTRAINT fk_posting_fact_lines_fact_id
        FOREIGN KEY (fact_id) REFERENCES operations_facts.posting_facts(id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_posting_fact_lines_fact_id_line_number UNIQUE (fact_id, line_number),
    CONSTRAINT ck_posting_fact_lines_line_number CHECK (line_number >= 1),
    CONSTRAINT ck_posting_fact_lines_side_kind CHECK (side_kind IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_posting_fact_lines_component_key
        CHECK (component_key ~ '^[A-Z][A-Z0-9_]{1,59}$'),
    CONSTRAINT ck_posting_fact_lines_line_amount_positive CHECK (
        line_amount > 0
        AND line_amount NOT IN (
            'NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric
        )
    ),
    CONSTRAINT ck_posting_fact_lines_line_currency CHECK (line_currency = 'CNY'),
    CONSTRAINT ck_posting_fact_lines_audit_times CHECK (updated_at = created_at)
);

CREATE FUNCTION operations_facts.refuse_posting_fact_change()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $body$
BEGIN
    RAISE EXCEPTION 'posting facts and lines are append-only'
        USING ERRCODE = 'restrict_violation';
END
$body$;

CREATE TRIGGER tg_posting_facts_append_only
    BEFORE UPDATE OR DELETE ON operations_facts.posting_facts
    FOR EACH ROW EXECUTE FUNCTION operations_facts.refuse_posting_fact_change();
CREATE TRIGGER tg_posting_facts_truncate
    BEFORE TRUNCATE ON operations_facts.posting_facts
    FOR EACH STATEMENT EXECUTE FUNCTION operations_facts.refuse_posting_fact_change();
CREATE TRIGGER tg_posting_fact_lines_append_only
    BEFORE UPDATE OR DELETE ON operations_facts.posting_fact_lines
    FOR EACH ROW EXECUTE FUNCTION operations_facts.refuse_posting_fact_change();
CREATE TRIGGER tg_posting_fact_lines_truncate
    BEFORE TRUNCATE ON operations_facts.posting_fact_lines
    FOR EACH STATEMENT EXECUTE FUNCTION operations_facts.refuse_posting_fact_change();

CREATE FUNCTION operations_facts.guard_posting_fact_transaction()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp AS $body$
DECLARE
    members oid;
    temp_namespace oid;
    trusted_owner oid := current_user::pg_catalog.regrole;
    header_relation oid := 'operations_facts.posting_facts'::pg_catalog.regclass;
    line_relation oid := 'operations_facts.posting_fact_lines'::pg_catalog.regclass;
BEGIN
    -- Reject lookalike trigger attachment before any privileged TEMP operation.
    IF TG_OP <> 'INSERT' OR TG_LEVEL <> 'ROW'
        OR NOT (
            (TG_RELID = header_relation AND TG_WHEN = 'AFTER')
            OR (TG_RELID = line_relation AND TG_WHEN = 'BEFORE')
        )
        OR NOT EXISTS (
            SELECT 1 FROM pg_catalog.pg_class c
            WHERE c.oid = TG_RELID AND c.relowner = trusted_owner
              AND c.relkind = 'r' AND c.relpersistence = 'p'
        )
    THEN
        RAISE EXCEPTION 'invalid posting transaction trigger context'
            USING ERRCODE = 'restrict_violation';
    END IF;

    temp_namespace := pg_catalog.pg_my_temp_schema();
    SELECT c.oid INTO members
      FROM pg_catalog.pg_class c
     WHERE c.relnamespace = temp_namespace
       AND c.relname = 'f1_posting_transaction_members';

    IF members IS NULL AND TG_RELID = header_relation THEN
        CREATE TEMP TABLE pg_temp.f1_posting_transaction_members (
            fact_id uuid NOT NULL,
            CONSTRAINT pk_f1_posting_transaction_members PRIMARY KEY (fact_id)
        ) ON COMMIT DROP;
        temp_namespace := pg_catalog.pg_my_temp_schema();
        SELECT c.oid INTO members
          FROM pg_catalog.pg_class c
         WHERE c.relnamespace = temp_namespace
           AND c.relname = 'f1_posting_transaction_members';
    END IF;

    -- Owner-only creation is the provenance of ON COMMIT DROP. An untrusted
    -- same-name relation is refused, never dropped, repaired or adopted.
    IF members IS NULL
        OR NOT EXISTS (
            SELECT 1 FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE c.oid = members AND n.oid = temp_namespace
              AND temp_namespace = pg_catalog.pg_my_temp_schema()
              AND c.relowner = trusted_owner
              AND c.relkind = 'r' AND c.relpersistence = 't'
              AND c.relnatts = 1 AND c.relchecks = 0
              AND NOT c.relhasrules AND NOT c.relhastriggers
              AND NOT c.relispartition
              AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity
        )
        OR (SELECT count(*) FROM pg_catalog.pg_attribute a
            WHERE a.attrelid = members AND a.attnum > 0) <> 1
        OR NOT EXISTS (
            SELECT 1 FROM pg_catalog.pg_attribute a
            WHERE a.attrelid = members AND a.attnum = 1
              AND a.attname = 'fact_id'
              AND a.atttypid = 'pg_catalog.uuid'::pg_catalog.regtype
              AND a.atttypmod = -1 AND a.attnotnull
              AND NOT a.attisdropped AND NOT a.atthasdef
              AND a.attidentity = '' AND a.attgenerated = ''
              AND a.attacl IS NULL
        )
        -- PostgreSQL 18 stores the column NOT NULL as a separate constraint.
        -- Require exactly one local NOT NULL and one primary key on fact_id.
        OR (SELECT count(*) FROM pg_catalog.pg_constraint k
            WHERE k.conrelid = members) <> 2
        OR NOT EXISTS (
            SELECT 1 FROM pg_catalog.pg_constraint k
            WHERE k.conrelid = members AND k.contype = 'n'
              AND k.conkey = ARRAY[1]::smallint[]
              AND k.convalidated AND NOT k.condeferrable
              AND NOT k.condeferred AND k.conislocal
              AND k.coninhcount = 0 AND k.conparentid = 0
        )
        OR NOT EXISTS (
            SELECT 1 FROM pg_catalog.pg_constraint k
            JOIN pg_catalog.pg_index i ON i.indexrelid = k.conindid
            WHERE k.conrelid = members AND k.contype = 'p'
              AND k.conkey = ARRAY[1]::smallint[]
              AND k.convalidated AND NOT k.condeferrable
              AND NOT k.condeferred AND k.conislocal
              AND k.coninhcount = 0 AND k.conparentid = 0
              AND i.indrelid = members AND i.indisprimary
              AND i.indisunique AND i.indisvalid
              AND i.indexprs IS NULL AND i.indpred IS NULL
        )
        OR EXISTS (
            SELECT 1 FROM pg_catalog.pg_inherits i
            WHERE i.inhrelid = members OR i.inhparent = members
        )
        OR EXISTS (
            SELECT 1 FROM pg_catalog.pg_trigger t WHERE t.tgrelid = members
        )
        OR EXISTS (
            SELECT 1 FROM pg_catalog.pg_rewrite r WHERE r.ev_class = members
        )
        OR EXISTS (
            SELECT 1 FROM pg_catalog.pg_attrdef d WHERE d.adrelid = members
        )
        OR EXISTS (
            SELECT 1
              FROM pg_catalog.pg_class c,
                   LATERAL pg_catalog.aclexplode(
                       coalesce(c.relacl, pg_catalog.acldefault('r', c.relowner))
                   ) a
             WHERE c.oid = members AND a.grantee <> trusted_owner
        )
    THEN
        RAISE EXCEPTION 'no trusted posting transaction membership relation'
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_RELID = header_relation THEN
        INSERT INTO pg_temp.f1_posting_transaction_members (fact_id) VALUES (NEW.id);
    ELSIF NOT EXISTS (
        SELECT 1 FROM pg_temp.f1_posting_transaction_members m
        WHERE m.fact_id = NEW.fact_id
    ) THEN
        RAISE EXCEPTION 'posting lines require a header inserted in this transaction'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END
$body$;

CREATE TRIGGER tg_posting_facts_transaction
    AFTER INSERT ON operations_facts.posting_facts
    FOR EACH ROW EXECUTE FUNCTION operations_facts.guard_posting_fact_transaction();
CREATE TRIGGER tg_posting_fact_lines_transaction
    BEFORE INSERT ON operations_facts.posting_fact_lines
    FOR EACH ROW EXECUTE FUNCTION operations_facts.guard_posting_fact_transaction();

REVOKE ALL ON FUNCTION operations_facts.guard_posting_fact_transaction() FROM PUBLIC;

CREATE FUNCTION operations_facts.guard_posting_fact_component()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $body$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM operations_facts.posting_facts f
          JOIN operations_facts.posting_components c
            ON c.fact_type = f.fact_type
           AND c.component_key = NEW.component_key
           AND c.is_active
         WHERE f.id = NEW.fact_id
    ) THEN
        RAISE EXCEPTION 'posting line requires an active component of its fact type'
            USING ERRCODE = 'check_violation',
                  CONSTRAINT = 'tg_posting_fact_lines_component';
    END IF;
    RETURN NEW;
END
$body$;

CREATE TRIGGER tg_posting_fact_lines_component
    BEFORE INSERT ON operations_facts.posting_fact_lines
    FOR EACH ROW EXECUTE FUNCTION operations_facts.guard_posting_fact_component();

CREATE FUNCTION operations_facts.guard_posting_fact_balance()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $body$
DECLARE
    checked_fact uuid;
    debits bigint;
    credits bigint;
    currencies bigint;
    debit_amount numeric;
    credit_amount numeric;
BEGIN
    IF TG_RELID = 'operations_facts.posting_facts'::pg_catalog.regclass THEN
        checked_fact := NEW.id;
    ELSE
        checked_fact := NEW.fact_id;
    END IF;

    SELECT count(*) FILTER (WHERE side_kind = 'DEBIT'),
           count(*) FILTER (WHERE side_kind = 'CREDIT'),
           count(DISTINCT line_currency),
           coalesce(sum(line_amount) FILTER (WHERE side_kind = 'DEBIT'), 0),
           coalesce(sum(line_amount) FILTER (WHERE side_kind = 'CREDIT'), 0)
      INTO debits, credits, currencies, debit_amount, credit_amount
      FROM operations_facts.posting_fact_lines
     WHERE fact_id = checked_fact;

    IF debits = 0 OR credits = 0 OR currencies <> 1
        OR debit_amount IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        OR credit_amount IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)
        OR debit_amount <> credit_amount
    THEN
        RAISE EXCEPTION 'posting fact requires balanced debit and credit lines in one currency'
            USING ERRCODE = 'check_violation',
                  CONSTRAINT = 'tg_posting_fact_lines_balance';
    END IF;
    RETURN NULL;
END
$body$;

CREATE CONSTRAINT TRIGGER tg_posting_facts_balance
    AFTER INSERT ON operations_facts.posting_facts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION operations_facts.guard_posting_fact_balance();
CREATE CONSTRAINT TRIGGER tg_posting_fact_lines_balance
    AFTER INSERT ON operations_facts.posting_fact_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION operations_facts.guard_posting_fact_balance();

COMMENT ON SEQUENCE operations_facts.posting_fact_post_seq IS
    '过账编号分配序列；允许回滚空号，不代表提交顺序或无间断编号';
COMMENT ON TABLE operations_facts.posting_facts IS
    '过账事实。粒度：每个法人 × 业务来源 × 幂等键一行（一次成功过账或冲销），只增不改不删';
COMMENT ON COLUMN operations_facts.posting_facts.id IS '主键（UUIDv7）';
COMMENT ON COLUMN operations_facts.posting_facts.post_number IS '过账编号，序列分配，允许空号';
COMMENT ON COLUMN operations_facts.posting_facts.legal_entity_id IS '法人主体 ID，跨上下文无外键';
COMMENT ON COLUMN operations_facts.posting_facts.fact_type IS '事实类型，F1 仅 PAYROLL';
COMMENT ON COLUMN operations_facts.posting_facts.business_date IS '业务日期';
COMMENT ON COLUMN operations_facts.posting_facts.posted_fiscal_year_number IS '落账会计年度';
COMMENT ON COLUMN operations_facts.posting_facts.posted_period_number IS '落账期间编号，仅 1 至 12';
COMMENT ON COLUMN operations_facts.posting_facts.original_fiscal_year_number IS '原属会计年度；为空表示未从关闭期间路由';
COMMENT ON COLUMN operations_facts.posting_facts.original_period_number IS '原属期间编号；为空表示未从关闭期间路由';
COMMENT ON COLUMN operations_facts.posting_facts.source_context_code IS '业务来源上下文代码';
COMMENT ON COLUMN operations_facts.posting_facts.source_id IS '来源对象 ID，跨上下文无外键';
COMMENT ON COLUMN operations_facts.posting_facts.idempotency_key IS '法人和业务来源内的幂等键';
COMMENT ON COLUMN operations_facts.posting_facts.reverses_fact_id IS '被冲销事实 ID；为空表示不是冲销事实';
COMMENT ON COLUMN operations_facts.posting_facts.created_at IS '创建时刻';
COMMENT ON COLUMN operations_facts.posting_facts.updated_at IS '更新时刻，等于创建时刻且不再改变';

COMMENT ON TABLE operations_facts.posting_fact_lines IS
    '过账事实行。粒度：每个事实 × 行号一行，与事实同事务写入，只增不改不删';
COMMENT ON COLUMN operations_facts.posting_fact_lines.id IS '主键（UUIDv7）';
COMMENT ON COLUMN operations_facts.posting_fact_lines.fact_id IS '所属过账事实 ID';
COMMENT ON COLUMN operations_facts.posting_fact_lines.line_number IS '事实内行号，从 1 开始';
COMMENT ON COLUMN operations_facts.posting_fact_lines.side_kind IS '借贷方向，DEBIT 或 CREDIT';
COMMENT ON COLUMN operations_facts.posting_fact_lines.component_key IS '事实类型内已登记且有效的组成项键';
COMMENT ON COLUMN operations_facts.posting_fact_lines.line_amount IS '正数金额，精度 19 位、小数 2 位';
COMMENT ON COLUMN operations_facts.posting_fact_lines.line_currency IS '币种，F1 仅 CNY';
COMMENT ON COLUMN operations_facts.posting_fact_lines.department_id IS '部门 ID，跨上下文无外键；为空表示无部门维度';
COMMENT ON COLUMN operations_facts.posting_fact_lines.created_at IS '创建时刻';
COMMENT ON COLUMN operations_facts.posting_fact_lines.updated_at IS '更新时刻，等于创建时刻且不再改变';

COMMENT ON TABLE operations_facts.posting_components IS
    '事实组成项注册表。粒度：每个事实类型 × 组成项键一行，只由迁移写入';
COMMENT ON COLUMN operations_facts.posting_components.id IS '主键（UUIDv7）';
COMMENT ON COLUMN operations_facts.posting_components.fact_type IS '事实类型，F1 仅 PAYROLL';
COMMENT ON COLUMN operations_facts.posting_components.component_key IS '事实类型内唯一的组成项键';
COMMENT ON COLUMN operations_facts.posting_components.side_hint_kind IS '借贷方向提示；为空表示两种方向均可';
COMMENT ON COLUMN operations_facts.posting_components.is_active IS '是否有效，退役组成项保留其历史含义';
COMMENT ON COLUMN operations_facts.posting_components.created_at IS '创建时刻';
COMMENT ON COLUMN operations_facts.posting_components.updated_at IS '更新时刻';

COMMENT ON FUNCTION operations_facts.guard_posting_fact_transaction() IS
    'F1 同事务守卫：可信 DDL owner 的 TEMP UUID 集合，ON COMMIT DROP；要求 owner 与运行 DML 角色隔离及 owner 的数据库 TEMP 权限；不信任 GUC、xmin 或调用者临时表';
