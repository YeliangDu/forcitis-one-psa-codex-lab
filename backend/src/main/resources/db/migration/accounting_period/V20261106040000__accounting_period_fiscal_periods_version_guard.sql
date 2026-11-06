-- 会计期间（#1551，#1454 的跟进 S2）：版本号列注释写明「状态每推进一步加 1」，触发器此前不查。
-- 现在一次状态推进必须把 version 加 1，且 updated_at 不早于原值；其余规则不变。已应用的迁移不改，这里整体替换触发器函数。

CREATE OR REPLACE FUNCTION accounting_period.guard_fiscal_period_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
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
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'tg_fiscal_periods_change: a move adds exactly 1 to version (version %, was %)', NEW.version, OLD.version USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION 'tg_fiscal_periods_change: updated_at never goes back' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
