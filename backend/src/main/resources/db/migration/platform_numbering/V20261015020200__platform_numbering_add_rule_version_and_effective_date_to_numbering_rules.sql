-- 事件信封与负载里的 ruleVersion、生效日必须同时落在业务行上，分析层才能从交易库重建（EVENT_ANALYTICS_FIELDS §6.8）。
ALTER TABLE platform_numbering.numbering_rules ADD COLUMN rule_version_number bigint NOT NULL DEFAULT 1 CHECK (rule_version_number >= 1);
ALTER TABLE platform_numbering.numbering_rules ADD COLUMN effective_from_date date;
-- 旧行为是保存即确认、即刻生效，已确认的行以保存当天（Asia/Shanghai）为生效日。
UPDATE platform_numbering.numbering_rules
   SET effective_from_date = (updated_at AT TIME ZONE 'Asia/Shanghai')::date
 WHERE confirmation_status = 'CONFIRMED';
ALTER TABLE platform_numbering.numbering_rules
    ADD CONSTRAINT ck_numbering_rules_confirmed_has_effective_from_date
        CHECK (confirmation_status <> 'CONFIRMED' OR effective_from_date IS NOT NULL);
COMMENT ON COLUMN platform_numbering.numbering_rules.rule_version_number IS '模板版本号：从 1 起，已确认的模板被替换时加 1；事件负载的 ruleVersion';
COMMENT ON COLUMN platform_numbering.numbering_rules.effective_from_date IS '当前模板的生效日（含）；待确认时为空；确认与重新配置事件的业务日期';
