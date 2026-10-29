#!/usr/bin/env python3
"""The executable database scripts (C-3 dev, C-9 prod) must list the same schemas and version tables as the reference DDL
one_db_roles_schemas.sql, and every migration module directory must have its schema created, owned by the owner role and granted. These lists are kept
in step by hand and drift; the guard compares lists, the created roles, every statement that grants or revokes on functions, and the few grants that make a schema append-only, not full semantics.
Usage: db_scripts_aligned.py  |  db_scripts_aligned.py --self-test"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "docs/modernization/phase1a/infra/one_db_roles_schemas.sql"
SQL = ROOT / "docs/ops/runbooks/sql"
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


def table_revokes(text):
    """Plain REVOKE statements on one named table from the runtime role (the registry tables only migrations write), normalised."""
    return {re.sub(r"\s+", " ", m).upper() for m in re.findall(r"REVOKE\s+[A-Z, ]*\bON\s+(?!SCHEMA\b|ALL\b|FUNCTION|ROUTINE|PROCEDURE)[a-z_]+\.[a-z_]+\s+FROM\s+one_dev_app\s*;", text, re.IGNORECASE)}


def nspname_list(text, what):
    return set(names(one(r"WHERE nspname IN \((.*?)\)", text, what)))


def like_prefixes(text):
    patterns = re.findall(r"'([^']*)'", one(r"LIKE ANY \(ARRAY\[(.*?)\]\)", text, "V4 schema patterns"))
    return [re.compile(re.escape(p).replace(r"\\_", "_").replace("%", ".*") + r"\Z") for p in patterns]


def has_append_only_grant(text):
    return bool(re.search(rf"GRANT USAGE ON SCHEMA {APPEND_ONLY_SCHEMA} TO", text)) and bool(
        re.search(rf"FOR ROLE {OWNER_ROLE} IN SCHEMA {APPEND_ONLY_SCHEMA} GRANT SELECT, INSERT ON TABLES", text))


def widens_append_only(text):
    return any(re.search(r"\b(UPDATE|DELETE|TRUNCATE|ALL(?!\s+(?:FUNCTIONS|ROUTINES|PROCEDURES|SEQUENCES)\b))\b", statement)
               for statement in re.findall(rf"[^;]*\bGRANT\b[^;]*\b{APPEND_ONLY_SCHEMA}\b[^;]*;", text))


def role_statements(text):
    """{role: the CREATE ROLE statement, whitespace and case normalised}: the attributes (LOGIN, INHERIT, IN ROLE) decide what the role may do, so they are compared too."""
    return {m.group(1).lower(): " ".join(m.group(0).lower().split()) for m in re.finditer(r"^\s*CREATE ROLE\s+(\w+)[^;]*;", text, re.MULTILINE | re.IGNORECASE)}


def created_roles(text):
    return set(role_statements(text))


def function_privileges(text):
    """Every GRANT / REVOKE / ALTER DEFAULT PRIVILEGES that is about functions, whitespace and case normalised (a function nobody may execute is a runtime failure no table-level check sees)."""
    statements = re.findall(r"(?:\bALTER\s+DEFAULT\s+PRIVILEGES\b[^;]*?)?\b(?:GRANT|REVOKE)\b[^;]*?\bON\s+(?:ALL\s+)?(?:FUNCTIONS?|ROUTINES?|PROCEDURES?)\b[^;]*;", text, re.IGNORECASE)
    return {" ".join(statement.lower().split()) for statement in statements}


def grantees(statement):
    match = re.search(r"^.*\s(?:to|from)\s+(.*?);", statement)
    if not match:
        return set()
    tail = re.sub(r"\s+(with\s+grant\s+option|granted\s+by\s+\S+|cascade|restrict)\b", "", match.group(1))
    roles = {re.sub(r"^group\s+", "", role.strip()).strip('"') for role in tail.split(",")}
    return roles - {"public", "current_user", "current_role", "session_user"}


def problems(modules, reference, c3, c3_after, c3_verify):
    reference, c3, c3_after, c3_verify = map(without_comments, (reference, c3, c3_after, c3_verify))
    schemas = set(modules) | {HISTORY_SCHEMA}
    read_write = set(modules) - {APPEND_ONLY_SCHEMA}
    expected = [
        ("one_db_roles_schemas.sql §0 conflict check", nspname_list(reference, "§0 conflict check"), schemas),
        ("one_db_roles_schemas.sql CREATE SCHEMA", created_schemas(reference), schemas),
        ("one_db_roles_schemas.sql §3 grant loop", granted_schemas(reference), read_write),
        ("one_db_roles_schemas.sql REVOKE CREATE", revoked_create(reference), schemas),
        ("dev-db.sql STEP 2 conflict check", nspname_list(c3, "STEP 2 conflict check"), schemas),
        ("dev-db.sql CREATE SCHEMA", created_schemas(c3), schemas),
        ("dev-db.sql STEP 5 grant loop", granted_schemas(c3), read_write),
        ("dev-db.sql REVOKE CREATE", revoked_create(c3), schemas),
        ("dev-db-verify.sql V3", nspname_list(c3_verify, "V3 schema list"), schemas),
        ("dev-db-after-migrations.sql version tables", version_tables(c3_after), version_tables(reference)),
        ("dev-db-after-migrations.sql table revokes", table_revokes(c3_after), table_revokes(reference)),
    ]
    found = [f"{where}: missing {sorted(want - got)}, extra {sorted(got - want)}" for where, got, want in expected if got != want]
    reference_roles, c3_roles = role_statements(reference), role_statements(c3)
    found += [f"dev-db.sql CREATE ROLE {role}: {'missing' if role not in c3_roles else 'extra' if role not in reference_roles else 'differs from one_db_roles_schemas.sql'}"
              for role in sorted(set(reference_roles) | set(c3_roles)) if reference_roles.get(role) != c3_roles.get(role)]
    # the functions come from the migrations, so their grants belong after them (dev-db-after-migrations.sql); c3 runs before the functions exist
    reference_functions, c3_functions = function_privileges(reference), function_privileges(c3) | function_privileges(c3_after)
    if reference_functions != c3_functions:
        found.append(f"dev-db*.sql function grants differ from one_db_roles_schemas.sql: missing {sorted(reference_functions - c3_functions)}, extra {sorted(c3_functions - reference_functions)}")
    for where, text, roles in (("one_db_roles_schemas.sql", reference, created_roles(reference)), ("dev-db.sql", c3, created_roles(c3)), ("dev-db-after-migrations.sql", c3_after, created_roles(c3))):
        found += [f"{where}: {statement} names role {role}, which the scripts do not create"
                  for statement in sorted(function_privileges(text)) for role in sorted(grantees(statement) - roles)]
        if re.search(r"EXECUTE\s+format\s*\(\s*'[^']*\b(?:GRANT|REVOKE)\b[^']*\b(?:FUNCTIONS?|ROUTINES?|PROCEDURES?)\b", text, re.IGNORECASE):
            found.append(f"{where}: grant or revoke on functions inside EXECUTE format(...): write them as plain statements so they can be compared")
    prefixes = like_prefixes(c3_verify)
    found += [f"dev-db-verify.sql V4: no pattern covers {schema}" for schema in sorted(schemas) if not any(p.match(schema) for p in prefixes)]
    for where, text in (("one_db_roles_schemas.sql", reference), ("dev-db.sql", c3)):
        found += [f"{where}: schema {schema} not owned by {OWNER_ROLE}" for schema in sorted(foreign_owned_schemas(text))]
        if APPEND_ONLY_SCHEMA in modules and not has_append_only_grant(text):
            found.append(f"{where}: no append-only grant for {APPEND_ONLY_SCHEMA}")
        if widens_append_only(text):
            found.append(f"{where}: {APPEND_ONLY_SCHEMA} granted UPDATE, DELETE, TRUNCATE or ALL")
    return found


def read_all():
    modules = sorted(p.name for p in MIGRATIONS.iterdir() if p.is_dir())
    texts = [REFERENCE.read_text(), *((SQL / f"dev-db{s}.sql").read_text() for s in ("", "-after-migrations", "-verify"))]
    return modules, texts


def self_test():
    modules, (reference, c3, c3_after, c3_verify) = read_all()
    assert problems(modules, reference, c3, c3_after, c3_verify) == []
    assert any("missing ['extra_module']" in p for p in problems([*modules, "extra_module"], reference, c3, c3_after, c3_verify))
    dropped = c3.replace("CREATE SCHEMA platform_idempotency", "-- CREATE SCHEMA platform_idempotency", 1)
    assert any(p.startswith("dev-db.sql CREATE SCHEMA") for p in problems(modules, reference, dropped, c3_after, c3_verify))
    ungranted = c3.replace("'platform_idempotency', 'cost_timesheet']", "'cost_timesheet']", 1)
    assert any(p.startswith("dev-db.sql STEP 5") for p in problems(modules, reference, ungranted, c3_after, c3_verify))
    short_after = c3_after.replace("'organization_workforce.legal_entity_versions',", "", 1)
    assert any(p.startswith("dev-db-after-migrations.sql") for p in problems(modules, reference, c3, short_after, c3_verify))
    no_audit = c3.replace("GRANT SELECT, INSERT ON TABLES TO one_dev_app", "GRANT SELECT ON TABLES TO one_dev_app", 1)
    assert any("append-only" in p for p in problems(modules, reference, no_audit, c3_after, c3_verify))
    commented = c3.replace("CREATE SCHEMA platform_idempotency    AUTHORIZATION one_dev_owner;",
                           "/*\nCREATE SCHEMA platform_idempotency    AUTHORIZATION one_dev_owner;\n*/", 1)
    assert commented != c3
    assert any(p.startswith("dev-db.sql CREATE SCHEMA") for p in problems(modules, reference, commented, c3_after, c3_verify))
    short_reference = reference.replace("'platform_idempotency', 'cost_timesheet', 'one_migration');", "'cost_timesheet', 'one_migration');", 1)
    assert any(p.startswith("one_db_roles_schemas.sql §0") for p in problems(modules, reference=short_reference, c3=c3, c3_after=c3_after, c3_verify=c3_verify))
    assert any("V4: no pattern covers delivery_project" in p for p in problems([*modules, "delivery_project"], reference, c3, c3_after, c3_verify))
    app_owned = c3.replace("CREATE SCHEMA cost_timesheet          AUTHORIZATION one_dev_owner", "CREATE SCHEMA cost_timesheet          AUTHORIZATION one_dev_app", 1)
    assert any("not owned by" in p for p in problems(modules, reference, app_owned, c3_after, c3_verify))
    widened = c3 + "\nGRANT UPDATE, DELETE ON ALL TABLES IN SCHEMA platform_audit TO one_dev_app;\n"
    assert any("granted UPDATE" in p for p in problems(modules, reference, widened, c3_after, c3_verify))
    grant = "\nGRANT EXECUTE ON FUNCTION platform_audit.selftest_only_fn(date, date) TO one_dev_app;\n"
    differ = lambda ref, c3_, after=c3_after: [p for p in problems(modules, ref, c3_, after, c3_verify) if "function grants differ" in p]
    assert differ(reference + grant, c3), "a function grant only in the reference"
    assert not differ(reference + grant, c3, c3_after + grant), "the grant after the migrations, where it belongs, satisfies the reference"
    assert not differ(reference + grant, c3 + grant), "the same function grant in c3"
    assert differ(reference + grant, c3, c3_after + grant.replace("(date, date)", "(date)")), "another signature"
    assert differ(reference + grant, c3, c3_after + grant.replace("one_dev_app", "one_dev_readonly")), "another role"
    spaced = "\ngrant   execute on function platform_audit.selftest_only_fn(date, date)\n  to one_dev_app;\n"
    assert not differ(reference + grant, c3, c3_after + spaced), "case and layout do not matter"
    assert differ(reference + grant, c3, c3_after + grant.replace("FUNCTION platform_audit.selftest_only_fn(date, date)", "ALL FUNCTIONS IN SCHEMA platform_audit")), "ON ALL FUNCTIONS is not ON FUNCTION"
    assert differ(reference + grant.replace("FUNCTION", "ROUTINE"), c3), "ON ROUTINE is read too"
    assert differ(reference + grant.replace("FUNCTION", "PROCEDURE"), c3), "ON PROCEDURE is read too"
    default_a = "\nALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_audit GRANT EXECUTE ON FUNCTIONS TO one_dev_app;\n"
    assert differ(reference + default_a, c3), "default privileges on functions"
    assert differ(reference + default_a, c3 + default_a.replace("platform_audit", "cost_timesheet")), "default privileges for another schema"
    ghost = "\nGRANT EXECUTE ON FUNCTION platform_audit.f() TO one_dev_ghost;\n"
    assert any("one_dev_ghost, which the scripts do not create" in p for p in problems(modules, reference + ghost, c3, c3_after + ghost, c3_verify))
    assert any("one_db_roles_schemas.sql: grant execute" in p and "one_dev_ghost" in p for p in problems(modules, reference + ghost, c3, c3_after + ghost, c3_verify)), "a ghost role in the reference alone is found too"
    assert any(p.startswith("dev-db-after-migrations.sql: grant execute") and "one_dev_ghost" in p for p in problems(modules, reference + ghost, c3, c3_after + ghost, c3_verify)), "a ghost role in the script after the migrations"
    all_grant = "\nGRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA platform_audit TO one_dev_app;\n"
    assert differ(reference + all_grant, c3), "ON ALL FUNCTIONS only in the reference"
    assert problems(modules, reference + all_grant, c3, c3_after + all_grant, c3_verify) == [], "ON ALL FUNCTIONS IN SCHEMA platform_audit is no widening of the append-only tables"
    interval = "\nGRANT EXECUTE ON FUNCTION platform_audit.f(interval day to second) TO one_dev_app;\n"
    assert problems(modules, reference + interval, c3, c3_after + interval, c3_verify) == [], "a type name with `to` inside the signature"
    assert any("granted UPDATE" in p for p in problems(modules, reference + "\nGRANT ALL ON ALL FUNCTIONS IN SCHEMA platform_audit TO one_dev_app;\n", c3, c3_after, c3_verify)), "GRANT ALL is still a widening"
    not_ghosts = ["TO one_dev_app, one_dev_readonly", "TO one_dev_app WITH GRANT OPTION", "TO GROUP one_dev_app", 'TO "one_dev_app"', "TO PUBLIC", "TO CURRENT_USER", "TO one_dev_app GRANTED BY one_dev_owner"]
    for tail in not_ghosts:
        text = f"\nGRANT EXECUTE ON FUNCTION platform_audit.f() {tail};\n"
        assert problems(modules, reference + text, c3, c3_after + text, c3_verify) == [], tail
    revoke = "\nREVOKE EXECUTE ON FUNCTION platform_audit.f() FROM PUBLIC CASCADE;\n"
    assert problems(modules, reference + revoke, c3, c3_after + revoke, c3_verify) == [], "REVOKE FROM PUBLIC names no role"
    assert differ(reference + revoke, c3), "a revoke only in the reference"
    dynamic = "\nDO $$ BEGIN EXECUTE format('GRANT EXECUTE ON FUNCTION %I.f() TO one_dev_app', 'x'); END $$;\n"
    assert any("inside EXECUTE format" in p for p in problems(modules, reference + dynamic, c3, c3_after, c3_verify))
    audit = "\nCREATE ROLE one_dev_selftest_role LOGIN NOINHERIT CONNECTION LIMIT 1;\n"
    assert any(p == "dev-db.sql CREATE ROLE one_dev_selftest_role: missing" for p in problems(modules, reference + audit, c3, c3_after, c3_verify)), "a role only in the reference"
    assert any(p == "dev-db.sql CREATE ROLE one_dev_selftest_role: extra" for p in problems(modules, reference, c3 + audit, c3_after, c3_verify)), "a role only in c3"
    assert any("differs from one_db_roles_schemas.sql" in p for p in problems(modules, reference + audit, c3 + audit.replace("NOINHERIT", "INHERIT IN ROLE one_dev_owner"), c3_after, c3_verify)), "the same role with other attributes"
    commented_grant = c3 + "\n-- GRANT EXECUTE ON FUNCTION platform_audit.x() TO one_dev_app;\n"
    assert problems(modules, reference, commented_grant, c3_after, c3_verify) == [], "a commented grant is no grant"
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
    rendered = subprocess.run([str(SQL / "render-prod-sql.sh"), "--check"], capture_output=True, text=True)
    if rendered.returncode != 0:
        found.append("prod-db*.sql differ from the rendered c3 scripts: " + rendered.stderr.strip())
    if found:
        print("database scripts out of line:\n  " + "\n  ".join(found), file=sys.stderr)
        sys.exit(1)
    print(f"database scripts aligned: {len(modules) + 1} schemas, {len(version_tables(without_comments(texts[2])))} version tables")


main()
