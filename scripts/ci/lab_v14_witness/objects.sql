\set ON_ERROR_STOP on
SET ROLE one_dev_owner;
CREATE TABLE operations_facts.fixture_table(id integer PRIMARY KEY,value text);
CREATE SEQUENCE operations_facts.fixture_sequence AS bigint;
CREATE FUNCTION operations_facts.fixture_invoker() RETURNS integer LANGUAGE sql AS $$SELECT 1$$;
CREATE FUNCTION operations_facts.fixture_definer() RETURNS integer LANGUAGE sql SECURITY DEFINER AS $$SELECT 1$$;
REVOKE ALL ON FUNCTION operations_facts.fixture_definer() FROM PUBLIC;
RESET ROLE;
DO $fixture$
BEGIN
 IF EXISTS(SELECT 1 FROM pg_class c WHERE c.relnamespace='operations_facts'::regnamespace AND pg_get_userbyid(c.relowner)<>'one_dev_owner')
 OR EXISTS(SELECT 1 FROM pg_proc p WHERE p.pronamespace='operations_facts'::regnamespace AND pg_get_userbyid(p.proowner)<>'one_dev_owner') THEN
  RAISE EXCEPTION 'FIXTURE_SETUP_FAILED: owner-created objects not owned';
 END IF;
 IF NOT has_function_privilege('one_dev_app','operations_facts.fixture_invoker()','EXECUTE')
 OR has_schema_privilege('one_dev_app','operations_facts','USAGE') THEN
  RAISE EXCEPTION 'FIXTURE_SETUP_FAILED: native invoker PUBLIC EXECUTE/schema isolation precondition';
 END IF;
END $fixture$;
