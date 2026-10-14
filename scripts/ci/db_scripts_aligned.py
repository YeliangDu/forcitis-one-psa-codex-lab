#!/usr/bin/env python3
"""The executable database scripts (C-3 dev, C-9 prod) must list the same schemas and version tables as the reference DDL
one_db_roles_schemas.sql, and every migration module directory must have its schema created, owned by the owner role and granted. These lists are kept
in step by hand and drift; the guard compares lists and the few grants that make a schema append-only, not full semantics
(c3_dev_db_check.sh exercises the grants in a container).
Usage: db_scripts_aligned.py  |  db_scripts_aligned.py --self-test"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "docs/modernization/phase1a/infra/one_db_roles_schemas.sql"
SQL = ROOT / "docs/modernization/phase1a/infra/runbooks/sql"
MIGRATIONS = ROOT / "backend/src/main/resources/db/migration"
OWNER_ROLE = "one_dev_owner"
HISTORY_SCHEMA = "one_migration"
# platform_audit is append-only: granted SELECT, INSERT on its own lines instead of in the read-write loop
APPEND_ONLY_SCHEMA = "platform_audit"


def without_comments(text):
    return re.sub(r"--[^\n]*", "", re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL))


def names(fragment):
    return re.findall(r"'([\w.]+)'", fragment) or re.findall(r"\b([a-z_]+(?:\.[a-z_]+)?)\b", fragment)


def one(pattern, text, what):
    found = re.findall(pattern, text, re.DOTALL)
    if len(found) != 1:
        raise ValueError(f"expected exactly one {what}, found {len(found)}")
    return found[0]


def created_schemas(text):
    return set(re.findall(r"^\s*CREATE SCHEMA\s+(\w+)\s+AUTHORIZATION", text, re.MULTILINE))


def foreign_owned_schemas(text):
    return {schema for schema, owner in re.findall(r"^\s*CREATE SCHEMA\s+(\w+)\s+AUTHORIZATION\s+(\w+)", text, re.MULTILINE) if owner != OWNER_ROLE}


def granted_schemas(text):
    return set(names(one(r"FOREACH s IN ARRAY ARRAY\[(.*?)\]", text, "schema grant loop")))


def revoked_create(text):
    return set(names(one(r"REVOKE CREATE ON SCHEMA ([^;]*?)\s+FROM\s+one_dev_app", text, "REVOKE CREATE list")))


def version_tables(text):
    return set(names(one(r"FOREACH t IN ARRAY ARRAY\[(.*?)\]", text, "version table loop")))


def nspname_list(text, what):
    return set(names(one(r"WHERE nspname IN \((.*?)\)", text, what)))


def like_prefixes(text):
    patterns = re.findall(r"'([^']*)'", one(r"LIKE ANY \(ARRAY\[(.*?)\]\)", text, "V4 schema patterns"))
    return [re.compile(re.escape(p).replace(r"\\_", "_").replace("%", ".*") + r"\Z") for p in patterns]


def has_append_only_grant(text):
    return bool(re.search(rf"GRANT USAGE ON SCHEMA {APPEND_ONLY_SCHEMA} TO", text)) and bool(
        re.search(rf"FOR ROLE {OWNER_ROLE} IN SCHEMA {APPEND_ONLY_SCHEMA} GRANT SELECT, INSERT ON TABLES", text))


def widens_append_only(text):
    return any(re.search(r"\b(UPDATE|DELETE|TRUNCATE|ALL)\b", statement)
               for statement in re.findall(rf"[^;]*\bGRANT\b[^;]*\b{APPEND_ONLY_SCHEMA}\b[^;]*;", text))


def problems(modules, reference, c3, c3_after, c3_verify):
    reference, c3, c3_after, c3_verify = map(without_comments, (reference, c3, c3_after, c3_verify))
    schemas = set(modules) | {HISTORY_SCHEMA}
    read_write = set(modules) - {APPEND_ONLY_SCHEMA}
    expected = [
        ("one_db_roles_schemas.sql §0 conflict check", nspname_list(reference, "§0 conflict check"), schemas),
        ("one_db_roles_schemas.sql CREATE SCHEMA", created_schemas(reference), schemas),
        ("one_db_roles_schemas.sql §3 grant loop", granted_schemas(reference), read_write),
        ("one_db_roles_schemas.sql REVOKE CREATE", revoked_create(reference), schemas),
        ("c3_dev_db.sql STEP 2 conflict check", nspname_list(c3, "STEP 2 conflict check"), schemas),
        ("c3_dev_db.sql CREATE SCHEMA", created_schemas(c3), schemas),
        ("c3_dev_db.sql STEP 5 grant loop", granted_schemas(c3), read_write),
        ("c3_dev_db.sql REVOKE CREATE", revoked_create(c3), schemas),
        ("c3_dev_db_verify.sql V3", nspname_list(c3_verify, "V3 schema list"), schemas),
        ("c3_dev_db_after_migrations.sql version tables", version_tables(c3_after), version_tables(reference)),
    ]
    found = [f"{where}: missing {sorted(want - got)}, extra {sorted(got - want)}" for where, got, want in expected if got != want]
    prefixes = like_prefixes(c3_verify)
    found += [f"c3_dev_db_verify.sql V4: no pattern covers {schema}" for schema in sorted(schemas) if not any(p.match(schema) for p in prefixes)]
    for where, text in (("one_db_roles_schemas.sql", reference), ("c3_dev_db.sql", c3)):
        found += [f"{where}: schema {schema} not owned by {OWNER_ROLE}" for schema in sorted(foreign_owned_schemas(text))]
        if APPEND_ONLY_SCHEMA in modules and not has_append_only_grant(text):
            found.append(f"{where}: no append-only grant for {APPEND_ONLY_SCHEMA}")
        if widens_append_only(text):
            found.append(f"{where}: {APPEND_ONLY_SCHEMA} granted UPDATE, DELETE, TRUNCATE or ALL")
    return found


def read_all():
    modules = sorted(p.name for p in MIGRATIONS.iterdir() if p.is_dir())
    texts = [REFERENCE.read_text(), *((SQL / f"c3_dev_db{s}.sql").read_text() for s in ("", "_after_migrations", "_verify"))]
    return modules, texts


def self_test():
    modules, (reference, c3, c3_after, c3_verify) = read_all()
    assert problems(modules, reference, c3, c3_after, c3_verify) == []
    assert any("missing ['extra_module']" in p for p in problems([*modules, "extra_module"], reference, c3, c3_after, c3_verify))
    dropped = c3.replace("CREATE SCHEMA platform_idempotency", "-- CREATE SCHEMA platform_idempotency", 1)
    assert any(p.startswith("c3_dev_db.sql CREATE SCHEMA") for p in problems(modules, reference, dropped, c3_after, c3_verify))
    ungranted = c3.replace("'platform_idempotency', 'cost_timesheet']", "'cost_timesheet']", 1)
    assert any(p.startswith("c3_dev_db.sql STEP 5") for p in problems(modules, reference, ungranted, c3_after, c3_verify))
    short_after = c3_after.replace("'organization_workforce.legal_entity_versions',", "", 1)
    assert any(p.startswith("c3_dev_db_after_migrations.sql") for p in problems(modules, reference, c3, short_after, c3_verify))
    no_audit = c3.replace("GRANT SELECT, INSERT ON TABLES TO one_dev_app", "GRANT SELECT ON TABLES TO one_dev_app", 1)
    assert any("append-only" in p for p in problems(modules, reference, no_audit, c3_after, c3_verify))
    commented = c3.replace("CREATE SCHEMA platform_idempotency    AUTHORIZATION one_dev_owner;",
                           "/*\nCREATE SCHEMA platform_idempotency    AUTHORIZATION one_dev_owner;\n*/", 1)
    assert commented != c3
    assert any(p.startswith("c3_dev_db.sql CREATE SCHEMA") for p in problems(modules, reference, commented, c3_after, c3_verify))
    short_reference = reference.replace("'platform_idempotency', 'cost_timesheet', 'one_migration');", "'cost_timesheet', 'one_migration');", 1)
    assert any(p.startswith("one_db_roles_schemas.sql §0") for p in problems(modules, reference=short_reference, c3=c3, c3_after=c3_after, c3_verify=c3_verify))
    assert any("V4: no pattern covers delivery_project" in p for p in problems([*modules, "delivery_project"], reference, c3, c3_after, c3_verify))
    app_owned = c3.replace("CREATE SCHEMA cost_timesheet          AUTHORIZATION one_dev_owner", "CREATE SCHEMA cost_timesheet          AUTHORIZATION one_dev_app", 1)
    assert any("not owned by" in p for p in problems(modules, reference, app_owned, c3_after, c3_verify))
    widened = c3 + "\nGRANT UPDATE, DELETE ON ALL TABLES IN SCHEMA platform_audit TO one_dev_app;\n"
    assert any("granted UPDATE" in p for p in problems(modules, reference, widened, c3_after, c3_verify))
    hardened = c3 + "\nREVOKE CREATE ON SCHEMA public FROM PUBLIC;\n"
    assert problems(modules, reference, hardened, c3_after, c3_verify) == []
    print("db_scripts_aligned self-test passed")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    modules, texts = read_all()
    try:
        found = problems(modules, *texts)
    except ValueError as unparsable:
        found = [f"cannot read the scripts' structure: {unparsable}"]
    rendered = subprocess.run([str(SQL / "render_prod_sql.sh"), "--check"], capture_output=True, text=True)
    if rendered.returncode != 0:
        found.append("c9_prod_db*.sql differ from the rendered c3 scripts: " + rendered.stderr.strip())
    if found:
        print("database scripts out of line:\n  " + "\n  ".join(found), file=sys.stderr)
        sys.exit(1)
    print(f"database scripts aligned: {len(modules) + 1} schemas, {len(version_tables(without_comments(texts[2])))} version tables")


main()
