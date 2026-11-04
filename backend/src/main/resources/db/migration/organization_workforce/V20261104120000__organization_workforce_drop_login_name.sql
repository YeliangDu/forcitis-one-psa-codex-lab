-- ADR-0069 section 7 (#1175): the old sync wrote login_name (empty meant separated); nothing writes or reads it any more.
-- separated_at stays until the one-time initial version migration is confirmed run in production (#1222).
ALTER TABLE organization_workforce.employees DROP COLUMN login_name;
