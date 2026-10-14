-- 数据打分卡 T1：版本表只追加。开放版本（失效日为空）只能被关闭；已关闭的行不能改、不能删。
-- 同一天的第二次变化不再删除当天版本：关闭它（失效日等于生效日，空区间）并追加 revision 加 1 的新行。
ALTER TABLE organization_workforce.org_unit_versions ADD COLUMN revision_number integer NOT NULL DEFAULT 0;
ALTER TABLE organization_workforce.org_unit_versions DROP CONSTRAINT pk_org_unit_versions;
ALTER TABLE organization_workforce.org_unit_versions ADD CONSTRAINT pk_org_unit_versions PRIMARY KEY (org_unit_id, effective_from_date, revision_number);
ALTER TABLE organization_workforce.org_unit_versions DROP CONSTRAINT ck_org_unit_versions_range;
ALTER TABLE organization_workforce.org_unit_versions ADD CONSTRAINT ck_org_unit_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date);
COMMENT ON COLUMN organization_workforce.org_unit_versions.revision_number IS '同一生效日内的修订序号：同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';

ALTER TABLE organization_workforce.assignment_versions ADD COLUMN revision_number integer NOT NULL DEFAULT 0;
ALTER TABLE organization_workforce.assignment_versions DROP CONSTRAINT pk_assignment_versions;
ALTER TABLE organization_workforce.assignment_versions ADD CONSTRAINT pk_assignment_versions PRIMARY KEY (employee_id, effective_from_date, revision_number);
ALTER TABLE organization_workforce.assignment_versions DROP CONSTRAINT ck_assignment_versions_range;
ALTER TABLE organization_workforce.assignment_versions ADD CONSTRAINT ck_assignment_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date);
COMMENT ON COLUMN organization_workforce.assignment_versions.revision_number IS '同一生效日内的修订序号：同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';

CREATE FUNCTION organization_workforce.refuse_version_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'version rows are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.effective_to_date IS NULL OR (to_jsonb(NEW) - 'effective_to_date' - 'updated_at') <> (to_jsonb(OLD) - 'effective_to_date' - 'updated_at') THEN
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_version_rewrite() IS '版本表只追加的触发器：只允许关闭开放版本，已关闭的行不能改，任何行不能删';

CREATE TRIGGER tg_org_unit_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.org_unit_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.assignment_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
