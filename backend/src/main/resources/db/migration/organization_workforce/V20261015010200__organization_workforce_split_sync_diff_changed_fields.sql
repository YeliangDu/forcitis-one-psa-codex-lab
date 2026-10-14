-- 数据打分卡 K14：第一范式。一次变更的多个字段不再拼成逗号分隔的字符串，改为文本数组。
ALTER TABLE organization_workforce.sync_diffs ADD COLUMN changed_field_codes text[];
UPDATE organization_workforce.sync_diffs SET changed_field_codes = string_to_array(changed_fields, ',') WHERE changed_fields IS NOT NULL;
ALTER TABLE organization_workforce.sync_diffs DROP COLUMN changed_fields;
COMMENT ON COLUMN organization_workforce.sync_diffs.changed_field_codes IS '变更的字段代码，每个字段一个元素；仅 UPDATE 类变更有值';
