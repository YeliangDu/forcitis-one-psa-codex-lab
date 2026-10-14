-- D-1a-28：法人表增加核算组织编码（数字、唯一）；字母代码继续用于业务编号，对接核算系统与老系统按数字编码。
ALTER TABLE organization_workforce.legal_entities
    ADD COLUMN accounting_organization_number bigint,
    ADD CONSTRAINT uk_legal_entities_accounting_organization_number UNIQUE (accounting_organization_number),
    ADD CONSTRAINT ck_legal_entities_accounting_organization_number CHECK (accounting_organization_number IS NULL OR accounting_organization_number > 0);
COMMENT ON COLUMN organization_workforce.legal_entities.accounting_organization_number IS '核算组织编码：核算系统中该法人的数字编码，全表唯一；尚未取得时为空';
