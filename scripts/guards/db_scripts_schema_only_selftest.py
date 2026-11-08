"""Lab #33 independent actual guard CLI mutations; hosted only, no SQL executed.

Reference healthy15511fad03b8d2ae38e3a337f24556bd9d9071c6. Copy actual guard,
renderer/reference/dev/prod SQL into temp tree; register only synthetic migration
SCHEMA operations_facts in all existence/owner/revoke/V3/V4 sites, NEVER grant.
Old expected red is EXACTLY two missing broad-loop diagnostics. Fixtures first
prove unmodified guard+renderer align; render every mutation with real renderer.
No problems() mock/import, no DB/role/grant execution, no fixed CI file changes.
New negative privilege diagnostics must explicitly identify 'schema-only' and
operations_facts, not parity/renderer/other-fixture errors. Full static tokenizer
and effective PostgreSQL ACL proof remain separate; this suite authorizes none.
"""
import copy
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

OPS="operations_facts"
REF="docs/ops/one-db-roles-schemas.sql"
DEV="docs/ops/runbooks/sql/dev-db.sql"
AFTER="docs/ops/runbooks/sql/dev-db-after-migrations.sql"
VERIFY="docs/ops/runbooks/sql/dev-db-verify.sql"


class SchemaOnlyGuardAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary=tempfile.TemporaryDirectory();self.addCleanup(temporary.cleanup)
        self.root=Path(temporary.name).resolve()
        source=Path(__file__).resolve().parents[2]
        guard=self.root/"scripts/guards/db_scripts_aligned.py";guard.parent.mkdir(parents=True)
        shutil.copy2(source/"scripts/guards/db_scripts_aligned.py",guard)
        sql=self.root/"docs/ops/runbooks/sql";sql.parent.mkdir(parents=True)
        shutil.copytree(source/"docs/ops/runbooks/sql",sql)
        (self.root/REF).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source/REF,self.root/REF)
        self.migrations=self.root/"backend/src/main/resources/db/migration";self.migrations.mkdir(parents=True)
        for directory in (source/"backend/src/main/resources/db/migration").iterdir():
            if directory.is_dir():(self.migrations/directory.name).mkdir()
        code,out=self.check()
        self.assertEqual(0,code,"SETUP BLOCKED: actual copied baseline guard/renderer must align before synthetic change: "+out)
        self.assertFalse((self.migrations/OPS).exists(),"SETUP BLOCKED: freeze fixture before actual ops schema registration")
        (self.migrations/OPS).mkdir()
        for path in (REF,DEV):
            text=(self.root/path).read_text()
            text=self.once(text,r"(WHERE nspname IN \()",lambda m:m[1]+"'operations_facts', ")
            text=self.once(text,r"(?m)^(CREATE SCHEMA\s+)",lambda m:"CREATE SCHEMA operations_facts AUTHORIZATION one_dev_owner;\n"+m[0],first=True)
            text=self.once(text,r"(REVOKE CREATE ON SCHEMA )",lambda m:m[1]+"operations_facts, ")
            (self.root/path).write_text(text)
        text=(self.root/VERIFY).read_text()
        text=self.once(text,r"(WHERE nspname IN \()",lambda m:m[1]+"'operations_facts', ")
        text=self.once(text,r"(LIKE ANY \(ARRAY\[)",lambda m:m[1]+"'operations\\_facts', ")
        (self.root/VERIFY).write_text(text)
        self.render()
        self.prepared={path:(self.root/path).read_text() for path in (REF,DEV,AFTER,VERIFY)}

    def once(self,text,pattern,replacement,first=False):
        matches=list(re.finditer(pattern,text,re.DOTALL))
        self.assertTrue(matches,"SETUP BLOCKED: copied SQL fixture structure changed")
        if not first:self.assertEqual(1,len(matches),"SETUP BLOCKED: ambiguous SQL registration anchor")
        match=matches[0]
        return text[:match.start()]+replacement(match)+text[match.end():]

    def render(self):
        script=self.root/"docs/ops/runbooks/sql/render-prod-sql.sh"
        result=subprocess.run([str(script)],cwd=self.root,capture_output=True,text=True,timeout=10)
        self.assertEqual(0,result.returncode,"SETUP BLOCKED: actual temporary renderer failed")
        result=subprocess.run([str(script),"--check"],cwd=self.root,capture_output=True,text=True,timeout=10)
        self.assertEqual(0,result.returncode,"SETUP BLOCKED: temporary dev/prod parity")

    def check(self):
        result=subprocess.run([sys.executable,"-B",str(self.root/"scripts/guards/db_scripts_aligned.py")],cwd=self.root,capture_output=True,text=True,timeout=10)
        return result.returncode,result.stdout+result.stderr

    def reset(self):
        for path,text in self.prepared.items():(self.root/path).write_text(text)

    def poison(self,statement,after=False):
        self.reset()
        for path in (REF,AFTER if after else DEV):
            with (self.root/path).open("a") as stream:stream.write("\n"+statement+"\n")
        self.render()

    def schema_only_refusal(self):
        code,out=self.check();self.assertEqual(1,code)
        self.assertIn(OPS,out);self.assertIn("schema-only",out.lower(),"must detect forbidden privilege, not old broad-loop missing")
        self.assertNotIn("prod-db*.sql differ",out);self.assertNotIn("cannot read the scripts' structure",out)

    def test_exact_empty_stage_registration_without_any_runtime_grant_is_valid(self):
        code,out=self.check()
        if code:
            lines=[line.strip() for line in out.splitlines() if line.strip() and line.strip()!="database scripts out of line:"]
            self.assertEqual([
                "one_db_roles_schemas.sql §3 grant loop: missing ['operations_facts'], extra []",
                "dev-db.sql STEP 5 grant loop: missing ['operations_facts'], extra []"],lines,
                "red must be ONLY old blanket grant-loop requirement, never malformed fixture/render/setup")
        self.assertEqual(0,code,"BEHAVIOR RED: exact schema-only existence must not require runtime grant-loop inclusion")
        self.assertIn("database scripts aligned",out)

    def test_grant_loop_inclusion_direct_table_sequence_and_public_grants_reject(self):
        for statement in ("GRANT USAGE ON SCHEMA operations_facts TO one_dev_app;",
            "GRANT SELECT ON operations_facts.synthetic_table TO one_dev_readonly;",
            "GRANT SELECT (synthetic_column) ON operations_facts.synthetic_table TO PUBLIC;",
            "GRANT USAGE ON SEQUENCE operations_facts.synthetic_seq TO one_dev_app;",
            "GRANT EXECUTE ON FUNCTION operations_facts.synthetic_function() TO PUBLIC;",
            'GRANT ALL ON SCHEMA "operations_facts" TO one_dev_app, one_dev_readonly WITH GRANT OPTION;'):
            with self.subTest(statement=statement):self.poison(statement);self.schema_only_refusal()
        self.reset()
        for path in (REF,DEV):
            text=(self.root/path).read_text()
            text=self.once(text,r"(FOREACH s IN ARRAY ARRAY\[)",lambda m:m[1]+"'operations_facts', ")
            (self.root/path).write_text(text)
        self.render();self.schema_only_refusal()

    def test_matching_default_global_or_schema_specific_grants_never_pass_by_parity(self):
        statements=(
            "ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA operations_facts GRANT SELECT ON TABLES TO one_dev_readonly;",
            "ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA operations_facts GRANT USAGE ON SEQUENCES TO PUBLIC;",
            "ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner GRANT SELECT ON TABLES TO one_dev_app;",
            "ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner GRANT EXECUTE ON FUNCTIONS TO PUBLIC;")
        for statement in statements:
            for after in (False,True):
                with self.subTest(statement=statement,after=after):self.poison(statement,after);self.schema_only_refusal()

    def test_grant_then_revoke_or_unknown_dynamic_sql_cannot_hide_widening(self):
        for statement in (
            "GRANT USAGE ON SCHEMA operations_facts TO one_dev_app; REVOKE ALL ON SCHEMA operations_facts FROM one_dev_app;",
            "DO $$ BEGIN EXECUTE 'GRANT USAGE ON SCHEMA operations_facts TO one_dev_app'; END $$;"):
            with self.subTest(statement=statement):self.poison(statement);self.schema_only_refusal()

    def test_existence_owner_revoke_v3_v4_requirements_are_not_exempted(self):
        cases=[(REF,"CREATE SCHEMA operations_facts AUTHORIZATION one_dev_owner;","", "CREATE SCHEMA"),
            (DEV,"CREATE SCHEMA operations_facts AUTHORIZATION one_dev_owner;","", "CREATE SCHEMA"),
            (REF,"CREATE SCHEMA operations_facts AUTHORIZATION one_dev_owner;","CREATE SCHEMA operations_facts AUTHORIZATION one_dev_app;","not owned"),
            (DEV,"REVOKE CREATE ON SCHEMA operations_facts, ","REVOKE CREATE ON SCHEMA ","REVOKE CREATE"),
            (VERIFY,"'operations_facts', ","", "V3"),
            (VERIFY,"'operations\\_facts', ","", "V4")]
        for path,old,new,expected in cases:
            with self.subTest(path=path,expected=expected):
                self.reset();text=(self.root/path).read_text();self.assertEqual(1,text.count(old));(self.root/path).write_text(text.replace(old,new,1))
                self.render();code,out=self.check();self.assertEqual(1,code);self.assertIn(expected,out);self.assertIn(OPS,out)

    def test_ordinary_schema_and_append_only_audit_requirements_remain_strict(self):
        self.reset()
        for path in (REF,DEV):
            text=(self.root/path).read_text()
            text=self.once(text,r"(FOREACH s IN ARRAY ARRAY\[)(.*?)(\])",lambda m:m[0].replace("'cost_timesheet'", "" ).replace(", ]", "]"))
            (self.root/path).write_text(text)
        self.render();code,out=self.check();self.assertEqual(1,code);self.assertIn("cost_timesheet",out);self.assertIn("grant loop",out)
        self.reset()
        for path in (REF,DEV):
            with (self.root/path).open("a") as stream:stream.write("\nGRANT UPDATE ON ALL TABLES IN SCHEMA platform_audit TO one_dev_app;\n")
        self.render();code,out=self.check();self.assertEqual(1,code);self.assertIn("platform_audit granted UPDATE",out)


if __name__=="__main__":unittest.main()
