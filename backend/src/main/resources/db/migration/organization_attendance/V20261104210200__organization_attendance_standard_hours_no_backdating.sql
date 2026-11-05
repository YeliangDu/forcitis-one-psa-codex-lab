-- 标准工时版本不追溯到过去：首版与全部作废后的新版同样不得早于今天（卡 #1310 裁定）。已合并迁移只追加，故整函数重建，触发器沿用。
CREATE OR REPLACE FUNCTION organization_attendance.guard_standard_hours_version_insert() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    scope_key uuid := NEW.legal_entity_id;
    latest integer;
    latest_from date;
    today date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date;
    closed integer;
BEGIN
    PERFORM set_config('forcitis.standard_hours_new_versions', coalesce(current_setting('forcitis.standard_hours_new_versions', true), '') || NEW.id::text || ',', true);
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
    IF latest_from IS NOT NULL AND NEW.effective_from_date <= latest_from THEN
        RAISE EXCEPTION 'effective_from_date % must be later than the latest version of the scope (%): versions are appended in order', NEW.effective_from_date, latest_from USING ERRCODE = 'check_violation';
    END IF;
    -- 不追溯：任何版本（含首版、全部作废后的新版）都不得早于库的事务日期（按库时间取日）。
    IF NEW.effective_from_date < today THEN
        RAISE EXCEPTION 'effective_from_date % is earlier than today: a standard-hours version does not act on the past', NEW.effective_from_date USING ERRCODE = 'check_violation';
    END IF;
    IF latest_from IS NOT NULL THEN
        UPDATE organization_attendance.standard_hours_versions SET effective_to_date = NEW.effective_from_date
            WHERE legal_entity_id IS NOT DISTINCT FROM scope_key AND status = 'IN_EFFECT' AND effective_to_date IS NULL;
        GET DIAGNOSTICS closed = ROW_COUNT;
        IF closed > 1 THEN
            RAISE EXCEPTION 'more than one open version in a scope' USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;
