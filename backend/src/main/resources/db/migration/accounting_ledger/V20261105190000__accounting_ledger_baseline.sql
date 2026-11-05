-- 账簿 G1：#1440，依据签署的数据设计 accounting-spine-g1；只建账簿一张表，不插任何行（账簿由代码在法人注册事件和回填时创建）。
CREATE SCHEMA IF NOT EXISTS accounting_ledger;
COMMENT ON SCHEMA accounting_ledger IS '总账账簿；G1 只有账簿一张表，科目表与映射由后续切片建立';

CREATE TABLE accounting_ledger.ledgers (
    id uuid NOT NULL,
    legal_entity_id uuid NOT NULL,
    basis_kind text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_ledgers PRIMARY KEY (id),
    CONSTRAINT uk_ledgers_legal_entity_id_basis_kind UNIQUE (legal_entity_id, basis_kind),
    CONSTRAINT ck_ledgers_basis_kind CHECK (basis_kind IN ('ACCRUAL', 'CASH'))
);

-- 账簿创建后既不改也不删：后续的科目表版本与映射版本按账簿 ID 引用它。
CREATE FUNCTION accounting_ledger.guard_ledger_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'a ledger is created once and never changed or removed' USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_ledgers_append_only BEFORE UPDATE OR DELETE ON accounting_ledger.ledgers
    FOR EACH ROW EXECUTE FUNCTION accounting_ledger.guard_ledger_change();
CREATE TRIGGER tg_ledgers_truncate BEFORE TRUNCATE ON accounting_ledger.ledgers
    FOR EACH STATEMENT EXECUTE FUNCTION accounting_ledger.guard_ledger_change();

COMMENT ON TABLE accounting_ledger.ledgers IS '账簿。粒度：每个法人 × 记账基础（权责或收付）一行，创建后不可改、不删';
COMMENT ON COLUMN accounting_ledger.ledgers.id IS '主键（UUIDv7）';
COMMENT ON COLUMN accounting_ledger.ledgers.legal_entity_id IS '法人主体 ID，账簿所属法人，跨上下文不建外键';
COMMENT ON COLUMN accounting_ledger.ledgers.basis_kind IS '记账基础：ACCRUAL 权责发生制、CASH 收付实现制';
COMMENT ON COLUMN accounting_ledger.ledgers.created_at IS '创建时刻';
COMMENT ON COLUMN accounting_ledger.ledgers.updated_at IS '最后更新时刻（账簿无更新路径，恒等于创建时刻）';
