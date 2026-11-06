-- 会计期间（AGG-81，#1439；one-dev 依据 one-design 数据设计审查记录 accounting-spine-p1，#1434）：
-- 一张表 fiscal_periods。本迁移不写任何行：OpenFiscalYear 命令写十二个期间。
-- 期间只前进（OPEN → CLOSING → CLOSED），已关闭的期间不可再改，任何期间行都不删除（INV-99，ADR-0045）。

CREATE SCHEMA IF NOT EXISTS accounting_period;
COMMENT ON SCHEMA accounting_period IS '会计期间：法人 × 会计年度 × 期间序号的开放与关闭状态';

CREATE TABLE accounting_period.fiscal_periods (
    id                 uuid        NOT NULL,
    legal_entity_id    uuid        NOT NULL,
    fiscal_year_number integer     NOT NULL CONSTRAINT ck_fiscal_periods_fiscal_year_number CHECK (fiscal_year_number BETWEEN 2000 AND 2999),
    period_number      integer     NOT NULL CONSTRAINT ck_fiscal_periods_period_number CHECK (period_number BETWEEN 1 AND 13),
    status             text        NOT NULL CONSTRAINT ck_fiscal_periods_status CHECK (status IN ('OPEN', 'CLOSING', 'CLOSED')),
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_fiscal_periods PRIMARY KEY (id),
    CONSTRAINT uk_fiscal_periods_entity_year_number UNIQUE (legal_entity_id, fiscal_year_number, period_number)
);

CREATE FUNCTION accounting_period.guard_fiscal_period_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    moved_columns text[] := ARRAY['status', 'updated_at', 'version'];
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'tg_fiscal_periods_change: no fiscal period row is deleted (facts refer to a period by entity, year and number)' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'tg_fiscal_periods_change: a CLOSED fiscal period never changes (INV-99)' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NOT ((OLD.status = 'OPEN' AND NEW.status = 'CLOSING') OR (OLD.status = 'CLOSING' AND NEW.status = 'CLOSED'))
            OR (to_jsonb(NEW) - moved_columns) <> (to_jsonb(OLD) - moved_columns) THEN
        RAISE EXCEPTION 'tg_fiscal_periods_change: a fiscal period only moves OPEN to CLOSING and CLOSING to CLOSED, changing status, updated_at and version alone' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION accounting_period.reject_fiscal_period_truncate() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'tg_fiscal_periods_truncate: fiscal periods are never truncated' USING ERRCODE = 'restrict_violation';
END $$;

CREATE TRIGGER tg_fiscal_periods_change BEFORE UPDATE OR DELETE ON accounting_period.fiscal_periods
    FOR EACH ROW EXECUTE FUNCTION accounting_period.guard_fiscal_period_change();
CREATE TRIGGER tg_fiscal_periods_truncate BEFORE TRUNCATE ON accounting_period.fiscal_periods
    FOR EACH STATEMENT EXECUTE FUNCTION accounting_period.reject_fiscal_period_truncate();

COMMENT ON TABLE accounting_period.fiscal_periods IS '账务期间：粒度：每个法人 × 会计年度 × 期间序号一行，已关闭的期间不可再改，不删除';
COMMENT ON COLUMN accounting_period.fiscal_periods.id IS '会计期间 ID（UUIDv7）';
COMMENT ON COLUMN accounting_period.fiscal_periods.legal_entity_id IS '法人主体 ID：期间属于这个法人的账；只存 ID，不建外键（法人在别的模块，存在性由开账年命令经法人目录校验）';
COMMENT ON COLUMN accounting_period.fiscal_periods.fiscal_year_number IS '会计年度：公历年（2000 到 2999，防录错）；起止日期由年度与序号推出，不存日期';
COMMENT ON COLUMN accounting_period.fiscal_periods.period_number IS '期间序号：1 到 12 为自然月，13 为调整期（本期不建 13 号行，约束允许它以免以后改表）';
COMMENT ON COLUMN accounting_period.fiscal_periods.status IS '状态：OPEN 开放 / CLOSING 关账中 / CLOSED 已关闭（只能 OPEN 到 CLOSING、CLOSING 到 CLOSED，已关闭的不再变）';
COMMENT ON COLUMN accounting_period.fiscal_periods.created_at IS '创建时间';
COMMENT ON COLUMN accounting_period.fiscal_periods.updated_at IS '最近修改时间';
COMMENT ON COLUMN accounting_period.fiscal_periods.version IS '乐观并发版本号：状态每推进一步加 1';
