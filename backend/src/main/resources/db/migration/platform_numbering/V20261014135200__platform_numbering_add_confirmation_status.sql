ALTER TABLE platform_numbering.numbering_rules
    ADD COLUMN confirmation_status text NOT NULL DEFAULT 'PENDING_CONFIRMATION'
        CHECK (confirmation_status IN ('PENDING_CONFIRMATION', 'CONFIRMED'));
COMMENT ON COLUMN platform_numbering.numbering_rules.confirmation_status IS '确认状态：PENDING_CONFIRMATION 待管理员确认 / CONFIRMED 已确认；管理员保存规则即确认';
COMMENT ON COLUMN platform_numbering.numbering_rules.pattern IS '编号模板，如 {TYPE}-{LE}-{yyyyMM}-{seq:4}；占位符 {TYPE} {LE} {yyyy} {yy} {MM} {dd} {seq:N}';

-- ADR-0010 decision 5: unified new format. The quote starts on the default template (code QT), awaiting the administrator's
-- confirmation; the old number format is not carried over (old numbers become "original numbers", see legacy_numbers).
UPDATE platform_numbering.numbering_rules
   SET pattern = '{TYPE}-{LE}-{yyyyMM}-{seq:4}', reset_policy = 'MONTHLY', start_value = 1,
       confirmation_status = 'PENDING_CONFIRMATION', updated_at = now(), version = version + 1
 WHERE document_type = 'QUOTE';
