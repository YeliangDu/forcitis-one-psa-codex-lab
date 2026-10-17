#!/usr/bin/env python3
"""Scan the DDL for the five "measurable raw material" items (one-data, METRIC design; Kimball 3, Adamson, Snodgrass).

The database must keep the raw material the metrics are built from, so the check is made on the tables, not on the reports:
  M1 money carries a currency, a quantity carries a unit
       a numeric column named like money (amount, price, fee, cost, salary, balance, total) needs a currency column in the same table;
       a column named quantity / qty needs a unit column (`unit_code`, `<x>_unit`), unless the unit is in the name (`_hours`, `_minutes`, `_days`)
  M2 the three times of a business fact: when it happened (business date), when it was booked or confirmed, when it was entered (created_at)
       applies to BUSINESS_DOCUMENT tables (data-access-registry role) and to tables that name a business_date_column
  M3 state transition history: who, when, from which state to which
       a table with a status column that has an enumeration CHECK must say where the history lives: a sibling `<table>_status_history` /
       `<table>_transitions` table, or the registry field `status_history: audit | events | table:<name>` (audit rows carry actor, time and
       before / after text; events carry the new state and the aggregate version)
  M4 master data keeps validity intervals (source of slowly changing dimensions)
       a MUTABLE AGGREGATE_ROOT / SNAPSHOT / CONFIG table of a master data schema has a versions table in the same schema,
       or the registry field `versioning: none:<reason>`; candidates only: the registry has no data-category field yet (DG-0)
  M5 key relations are foreign keys, not remarks
       a `<x>_id` column whose `<x>` is a table of the same schema (singular or plural, with `_id` of the table's own key excluded)
       needs a FOREIGN KEY unless the registry lists it in fk_exceptions; a cross-schema id is by design only an id, and is not reported

Usage: measurable_ddl_scan.py [--root DIR] [--json] | measurable_ddl_scan.py --self-test
`--root` points at a checkout (or an export) holding backend/src/main/resources/db/migration and backend/architecture/data-access-registry.yaml.
Report only: the exit status is 0 unless the SQL cannot be read. Regress by `schema.table.column`, never by line number.
Framework tables (event_publication*, sessions*, scheduler_locks, act_*) are not scanned.
"""
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
MONEY = re.compile(r"(^|_)(amount|price|fee|cost|salary|balance|total)($|_)")
QUANTITY = re.compile(r"(^|_)(quantity|qty)($|_)")
UNIT_IN_NAME = re.compile(r"(^|_)(hours|minutes|days|seconds|percent|rate)($|_)")
NUMERIC = re.compile(r"^(numeric|decimal|bigint|integer|int|smallint|double|real|money)", re.I)
BOOKED = re.compile(r"(^|_)(confirmed|posted|booked|submitted|approved|recorded|settled)_(at|date)$")
FRAMEWORK = re.compile(r"^(?:[a-z_]+\.)?(event_publication|sessions|scheduler_locks|act_)")
MASTER_SCHEMAS = ("organization_workforce", "platform_identity", "platform_numbering", "organization_attendance")


def load_scan_module(root):
    path = os.path.join(root, "scripts", "ci", "baseline_ddl_scan.py")
    if not os.path.exists(path):
        path = os.path.join(HERE, "baseline_ddl_scan.py")
    spec = importlib.util.spec_from_file_location("baseline_ddl_scan", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def read_foreign_keys(scan, migrations):
    """schema.table -> set of columns that carry a FOREIGN KEY (inline REFERENCES or table-level FOREIGN KEY (...))."""
    fks = {}
    files = sorted(os.path.join(d, f) for d, _, fs in os.walk(migrations) for f in fs if f.endswith(".sql"))
    for path in files:
        for stmt in scan.strip_statements(open(path, encoding="utf-8").read()):
            m = re.match(r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?([a-z0-9_]+\.[a-z0-9_]+)\s*\(", stmt, re.I)
            if m:
                name = m.group(1)
                start = stmt.index("(", m.end() - 1)
                body = stmt[start + 1:scan.balanced(stmt, start)]
                cols = fks.setdefault(name, set())
                for element in scan.split_top(body):
                    inline = re.match(r"([a-z_][a-z0-9_]*)\s+.*\bREFERENCES\b", element, re.I | re.S)
                    if inline:
                        cols.add(inline.group(1))
                    for fk in re.finditer(r"FOREIGN KEY\s*\(([^)]*)\)", element, re.I):
                        cols.update(c.strip() for c in fk.group(1).split(","))
                continue
            m = re.match(r"ALTER TABLE\s+(?:ONLY\s+)?([a-z0-9_]+\.[a-z0-9_]+)\s+ADD\s+CONSTRAINT\s+\w+\s+FOREIGN KEY\s*\(([^)]*)\)", stmt, re.I | re.S)
            if m:
                fks.setdefault(m.group(1), set()).update(c.strip() for c in m.group(2).split(","))
    return fks


def registry_tables(root):
    path = os.path.join(root, "backend", "architecture", "data-access-registry.yaml")
    if not os.path.exists(path):
        return {}
    import yaml
    return (yaml.safe_load(open(path, encoding="utf-8")) or {}).get("tables", {})


def has_enum_check(table, column):
    for _, expr in table["checks"]:
        if re.search(r"\b" + re.escape(column) + r"\s+IN\s*\(", expr, re.I) or re.search(r"\b" + re.escape(column) + r"\s*=\s*'", expr, re.I):
            return True
    for _, expr in table["columns"][column]["checks"]:
        if re.search(r"\bIN\s*\(|=\s*'", expr, re.I):
            return True
    return False


def scan_all(root):
    scan = load_scan_module(root)
    migrations = os.path.join(root, "backend", "src", "main", "resources", "db", "migration")
    tables, _, unsupported = scan.load(migrations)
    fks = read_foreign_keys(scan, migrations)
    registry = registry_tables(root)
    by_schema = {}
    for name in tables:
        by_schema.setdefault(name.split(".")[0], set()).add(name.split(".")[1])
    found = {"M1": [], "M2": [], "M3": [], "M4": [], "M5": []}
    for name, t in sorted(tables.items()):
        if FRAMEWORK.match(name):
            continue
        schema, short = name.split(".")
        cols = t["columns"]
        entry = registry.get(name, {})
        # M1
        for c, col in cols.items():
            if MONEY.search(c) and NUMERIC.match(col["type"]):
                if not any(re.search(r"(^|_)currency($|_)", x) for x in cols):
                    found["M1"].append((f"{name}.{c}", "money column without a currency column in the table"))
            if QUANTITY.search(c) and NUMERIC.match(col["type"]) and not UNIT_IN_NAME.search(c):
                if not any(re.search(r"(^|_)unit($|_)", x) for x in cols):
                    found["M1"].append((f"{name}.{c}", "quantity column without a unit column in the table"))
        # M2
        if entry.get("role") == "BUSINESS_DOCUMENT" or entry.get("business_date_column"):
            missing = []
            bdc = entry.get("business_date_column")
            if not bdc or bdc not in cols:
                missing.append("business date (registry business_date_column)")
            if not any(BOOKED.search(c) for c in cols):
                missing.append("booked / confirmed time (*_confirmed|posted|booked|submitted|approved|recorded|settled_at|date)")
            if "created_at" not in cols:
                missing.append("entry time (created_at)")
            if missing:
                found["M2"].append((name, "missing: " + "; ".join(missing)))
        # M3
        status_cols = [c for c in cols if c == "status" or c.endswith("_status")]
        enum_status = [c for c in status_cols if has_enum_check(t, c)]
        if enum_status and entry.get("role") not in ("DIMENSION_VERSIONED",):
            declared = entry.get("status_history")
            sibling = any(s in by_schema[schema] for s in (f"{short}_status_history", f"{short}_transitions", f"{short}_status_changes"))
            if not declared and not sibling:
                found["M3"].append((f"{name}.{enum_status[0]}", "state column without a declared transition history (registry status_history or a history table)"))
        # M4
        if schema in MASTER_SCHEMAS and entry.get("role") in ("AGGREGATE_ROOT", "SNAPSHOT", "CONFIG") and entry.get("mutability") in ("MUTABLE", "REPLACE_SET", None):
            stem = re.sub(r"ies$", "y", short) if short.endswith("ies") else short.rstrip("s")
            versioned = f"{stem}_versions" in by_schema[schema] or entry.get("versioning")
            if not versioned:
                found["M4"].append((name, f"{entry.get('role')} master data candidate without a versions table or registry versioning reason"))
        # M5
        own_pk = {c for c, col in cols.items() if col["primary"]}
        for c in cols:
            if not c.endswith("_id") or c in own_pk or c in fks.get(name, set()) or c in (entry.get("fk_exceptions") or {}):
                continue
            stem = c[:-3]
            for tn in (stem, stem + "s", stem + "es", re.sub(r"y$", "ies", stem)):
                target = tn if tn in by_schema[schema] else None
                if target and f"{schema}.{target}" != name:
                    found["M5"].append((f"{name}.{c}", f"names the same-schema table {schema}.{target} but has no FOREIGN KEY"))
                    break
    return found, len(tables), len(unsupported)


def report(found, total, unsupported, as_json):
    if as_json:
        print(json.dumps({"tables": total, "unsupported": unsupported, **{k: [{"item": i, "why": w} for i, w in v] for k, v in found.items()}}, ensure_ascii=False, indent=1))
        return
    titles = {"M1": "money without currency, quantity without unit", "M2": "business fact lacks one of the three times",
              "M3": "state column without a declared transition history", "M4": "master data candidate without validity intervals",
              "M5": "key relation not a foreign key"}
    print(f"tables scanned: {total} (statements the scan does not model: {unsupported}; must be 0 for the counts to be complete)")
    for k in ("M1", "M2", "M3", "M4", "M5"):
        print(f"\n== {k} {titles[k]}: {len(found[k])}")
        for item, why in found[k]:
            print(f"  {item} | {why}")


def self_test():
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        mig = os.path.join(tmp, "backend", "src", "main", "resources", "db", "migration", "demo")
        os.makedirs(mig)
        os.makedirs(os.path.join(tmp, "backend", "architecture"))
        os.makedirs(os.path.join(tmp, "scripts", "ci"))
        open(os.path.join(mig, "V1__demo.sql"), "w", encoding="utf-8").write("""
CREATE TABLE demo.orders (
    id uuid PRIMARY KEY, customer_id uuid NOT NULL, total_amount numeric(18,2) NOT NULL, quantity numeric(10,2),
    order_status text NOT NULL CHECK (order_status IN ('OPEN','DONE')), created_at timestamptz NOT NULL
);
CREATE TABLE demo.customers (id uuid PRIMARY KEY);
CREATE TABLE demo.lines (id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES demo.orders (id), unit_code text, line_quantity numeric(10,2), price_currency text, price numeric);
""")
        open(os.path.join(tmp, "backend", "architecture", "data-access-registry.yaml"), "w", encoding="utf-8").write(
            "tables:\n  demo.orders:\n    role: BUSINESS_DOCUMENT\n    business_date_column: order_date\n")
        found, total, _ = scan_all(tmp)
        items = {k: {i for i, _ in v} for k, v in found.items()}
        assert "demo.orders.total_amount" in items["M1"], "M1 money without currency"
        assert "demo.orders.quantity" in items["M1"], "M1 quantity without unit"
        assert "demo.lines.line_quantity" not in items["M1"] and "demo.lines.price" not in items["M1"], "M1: unit / currency present"
        assert "demo.orders" in items["M2"], "M2"
        assert "demo.orders.order_status" in items["M3"], "M3"
        assert "demo.orders.customer_id" in items["M5"], "M5: same-schema table without FK"
        assert "demo.lines.order_id" not in items["M5"], "M5: inline REFERENCES counts"
    print("self-test: ok")


def main(argv):
    if "--self-test" in argv:
        self_test()
        return
    root = ROOT
    if "--root" in argv:
        root = os.path.abspath(argv[argv.index("--root") + 1])
    found, total, unsupported = scan_all(root)
    report(found, total, unsupported, "--json" in argv)


if __name__ == "__main__":
    main(sys.argv[1:])
