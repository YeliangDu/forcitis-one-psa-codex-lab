-- ADR-0069 section 7 (#1175): the old sync wrote login_name (empty meant separated) and separated_at; nothing writes or reads them any more.
-- The leaving day lives in the employee version of a separation; wecom_user_id stays until DA-3.
ALTER TABLE organization_workforce.employees DROP COLUMN login_name;
ALTER TABLE organization_workforce.employees DROP COLUMN separated_at;
