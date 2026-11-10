"""#33 additive actual CLI security regressions; hosted only, no SQL executed.

Exact reference7b69888004e078499ab84abf71147cce49c4109e. Reuse unchanged frozen
six-case temp-tree/renderer helpers, not guard internals. Both scripts receive
same poison so parity cannot detect/hide the access change. Full actual CLI
must reject with explicit schema-only diagnostic, never renderer/setup drift.
"""
import unittest

import db_scripts_schema_only_selftest as frozen


class SchemaOnlyBypassAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.fixture=frozen.SchemaOnlyGuardAcceptanceTest(methodName="runTest")
        self.fixture.setUp();self.addCleanup(self.fixture.doCleanups)
        code,out=self.fixture.check()
        self.assertEqual(0,code,"SETUP BLOCKED: exact prepared no-grant schema-only fixture must pass first: "+out)

    def assert_explicit_rejection(self):
        code,out=self.fixture.check()
        self.assertNotIn("prod-db*.sql differ",out,"renderer drift is not privilege evidence")
        self.assertNotIn("cannot read the scripts' structure",out,"fixture parse failure is not specific security rejection")
        if code==0:self.assertIn("database scripts aligned",out,"old bypass must actually pass CLI, not crash")
        self.assertEqual(1,code,"SECURITY RED: guard accepted an executable schema-only privilege bypass")
        self.assertIn("schema-only",out.lower())
        self.assertIn("operations_facts",out)
        self.assertIn("one_db_roles_schemas.sql",out)
        self.assertIn("dev-db-after-migrations.sql",out)

    def test_search_path_unqualified_relation_grant_cannot_evade_schema_only_deny(self):
        self.fixture.poison("SET search_path = operations_facts, pg_catalog;\nGRANT SELECT ON posting_facts TO one_dev_readonly;",after=True)
        self.assert_explicit_rejection()

    def test_psql_gexec_of_grant_string_cannot_be_treated_as_inert_select(self):
        # No semicolon before gexec: psql evaluates current query buffer, then
        # executes resulting rows. Static guard must reject this unsupported
        # grant-bearing execution path; we do NOT run psql or issue the GRANT.
        self.fixture.poison("SELECT 'GRANT USAGE ON SCHEMA operations_facts TO one_dev_app'\n\\gexec",after=True)
        self.assert_explicit_rejection()


if __name__=="__main__":unittest.main()
