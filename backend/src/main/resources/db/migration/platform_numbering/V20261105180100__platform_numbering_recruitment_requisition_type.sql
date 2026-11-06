-- 招聘 R1（#1450）：招聘需求单据类型登记（ADR-0066 决定 4）；模板与其他种子类型相同，待业务管理员确认，确认前提交不发号。
INSERT INTO platform_numbering.document_types (document_type_id, code, number_code, owner_bc_code, is_finance_confirmation_required, effective_from_date) VALUES
    ('019a0000-0000-7000-8000-000000000405', 'RECRUITMENT_REQUISITION', 'REQ', 'organization.recruitment', false, DATE '2000-01-01');

INSERT INTO platform_numbering.numbering_rules (numbering_rule_id, document_type_id, version, event_count) VALUES
    ('019a0000-0000-7000-8000-000000000305', '019a0000-0000-7000-8000-000000000405', 1, 0);

INSERT INTO platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value, effective_from_date, authored_at, status, source_kind)
VALUES ('019a0000-0000-7000-8000-000000000505', '019a0000-0000-7000-8000-000000000305', 1, '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1, DATE '2000-01-01', now(), 'PENDING_CONFIRMATION', 'SEEDED');
