-- 版本表只追加（B2-M5）：补语句级 BEFORE TRUNCATE 触发器；法人归属期间表同样只允许关闭开放期间，已关闭的行不能改、任何行不能删。
CREATE OR REPLACE FUNCTION organization_workforce.refuse_version_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'version rows are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.effective_to_date IS NULL OR (to_jsonb(NEW) - 'effective_to_date' - 'updated_at') <> (to_jsonb(OLD) - 'effective_to_date' - 'updated_at') THEN
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_org_unit_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.org_unit_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.assignment_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_legal_entity_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.legal_entity_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_legal_entity_attribution_periods_append_only BEFORE UPDATE OR DELETE ON organization_workforce.legal_entity_attribution_periods
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_legal_entity_attribution_periods_append_only_truncate BEFORE TRUNCATE ON organization_workforce.legal_entity_attribution_periods
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
