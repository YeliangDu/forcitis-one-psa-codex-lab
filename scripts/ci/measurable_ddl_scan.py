#!/usr/bin/env python3
"""Scan the DDL for the "measurable raw material" items M1-M6 (rules: MEASURABLE_RAW_MATERIAL_SPEC.md).

M6 (D15): a registry entry with role CONFIG_VERSIONED names `versions_table` (schema.table); that table has author, authored_at, confirmer,
confirmed_at, decision_ref and effective_from_date columns and is APPEND_ONLY in the registry.

Usage: measurable_ddl_scan.py [--root DIR] [--json] | measurable_ddl_scan.py --self-test
Report only: the exit status is 0 unless the SQL or the registry cannot be read. Regress by `schema.table.column`, never by line number.
"""
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
CURRENCY_COLUMN = re.compile(r"(^|_)currency_code$")
UNIT_COLUMN = re.compile(r"(^|_)unit_code$")
DATA_CATEGORIES = ("主数据", "交易", "参照", "配置", "技术")
MONEY = re.compile(r"(^|_)(amount|price|fee|cost|salary|balance|total)($|_)")
QUANTITY = re.compile(r"(^|_)(quantity|qty)($|_)")
UNIT_IN_NAME = re.compile(r"(^|_)(hours|minutes|days|seconds|percent|rate)($|_)")
NUMERIC = re.compile(r"^(numeric|decimal|bigint|integer|int|smallint|double|real|money)", re.I)
BOOKED = re.compile(r"(^|_)(confirmed|posted|booked|submitted|approved|recorded|settled)_(at|date)$")
FRAMEWORK = re.compile(r"^(?:[a-z_]+\.)?(event_publication|sessions|scheduler_locks|act_)")
MASTER_SCHEMAS = ("organization_workforce", "platform_identity", "platform_numbering", "organization_attendance")


def load_scan_module():
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
            m = re.match(r"ALTER TABLE\s+(?:ONLY\s+)?([a-z0-9_]+\.[a-z0-9_]+)\s+(.*)", stmt, re.I | re.S)
            if m:
                for clause in scan.split_top(m.group(2)):
                    fk = re.match(r"ADD\s+(?:CONSTRAINT\s+\w+\s+)?FOREIGN KEY\s*\(([^)]*)\)", clause.strip(), re.I)
                    if fk:
                        fks.setdefault(m.group(1), set()).update(c.strip() for c in fk.group(1).split(","))
    return fks


def registry_tables(root):
    path = os.path.join(root, "backend", "architecture", "data-access-registry.yaml")
    if not os.path.exists(path):
        sys.exit(f"registry not found: {path}")
    import yaml
    return (yaml.safe_load(open(path, encoding="utf-8")) or {}).get("tables") or {}


def has_enum_check(table, column):
    for _, expr in table["checks"]:
        if re.search(r"\b" + re.escape(column) + r"\s+IN\s*\(", expr, re.I) or re.search(r"\b" + re.escape(column) + r"\s*=\s*('|ANY\s*\(\s*ARRAY)", expr, re.I):
            return True
    for _, expr in table["columns"][column]["checks"]:
        if re.search(r"\bIN\s*\(|=\s*('|ANY\s*\(\s*ARRAY)", expr, re.I):
            return True
    return False


def scan_all(root):
    scan = load_scan_module()
    migrations = os.path.join(root, "backend", "src", "main", "resources", "db", "migration")
    tables, _, unsupported = scan.load(migrations)
    fks = read_foreign_keys(scan, migrations)
    registry = registry_tables(root)
    by_schema = {}
    for name in tables:
        by_schema.setdefault(name.split(".")[0], set()).add(name.split(".")[1])
    version_tables = {(e or {}).get("versions_table") for e in registry.values() if (e or {}).get("role") == "CONFIG_VERSIONED"}
    found = {"M1": [], "M2": [], "M3": [], "M4": [], "M5": [], "M6": []}
    for name, t in sorted(tables.items()):
        if FRAMEWORK.match(name):
            continue
        schema, short = name.split(".")
        cols = t["columns"]
        entry = registry.get(name) or {}
        for c, col in cols.items():
            if MONEY.search(c) and NUMERIC.match(col["type"]) and not UNIT_IN_NAME.search(c):
                if not any(CURRENCY_COLUMN.search(x) for x in cols):
                    found["M1"].append((f"{name}.{c}", "money column without a currency column in the table"))
            if QUANTITY.search(c) and NUMERIC.match(col["type"]) and not UNIT_IN_NAME.search(c):
                if not any(UNIT_COLUMN.search(x) for x in cols):
                    found["M1"].append((f"{name}.{c}", "quantity column without a unit column in the table"))
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
        status_cols = [c for c in cols if c == "status" or c.endswith("_status")]
        enum_status = [c for c in status_cols if has_enum_check(t, c)]
        if enum_status and entry.get("role") != "DIMENSION_VERSIONED" and name not in version_tables:
            exempt = entry.get("state_transitions_exempt")
            if not exempt and "state_transitions" not in by_schema[schema]:
                found["M3"].append((f"{name}.{enum_status[0]}", "state column in a schema without a state_transitions table (ADR-0058) and no registry state_transitions_exempt reason"))
        category = entry.get("data_category")
        if category is not None and category not in DATA_CATEGORIES:
            found["M4"].append((name, f"registry data_category {category!r} is not one of " + " | ".join(DATA_CATEGORIES)))
        if category is not None:
            is_master = category == "主数据"
        else:
            is_master = schema in MASTER_SCHEMAS and entry.get("role") in ("AGGREGATE_ROOT", "SNAPSHOT", "CONFIG") and entry.get("mutability") in ("MUTABLE", "REPLACE_SET", None)
        if is_master and entry.get("mutability") in ("MUTABLE", "REPLACE_SET", None):
            stems = {short[:-3] + "y"} if short.endswith("ies") else set()
            stems |= {short[:-2]} if short.endswith("es") else set()
            stems |= {short[:-1]} if short.endswith("s") else {short}
            versioning = entry.get("versioning")
            if versioning is not None and not re.match(r"none:\S", str(versioning)):
                found["M4"].append((name, "registry versioning must be written none:<reason>"))
            if not (any(f"{stem}_versions" in by_schema[schema] for stem in stems) or versioning):
                found["M4"].append((name, f"{entry.get('role')} master data candidate without a versions table or registry versioning reason"))
        if entry.get("role") == "CONFIG_VERSIONED":
            vt = entry.get("versions_table")
            vcols = tables[vt]["columns"] if vt in tables else None
            if not vt:
                found["M6"].append((name, "CONFIG_VERSIONED entry without versions_table"))
            elif "." not in vt:
                found["M6"].append((name, "versions_table must be written schema.table"))
            elif vcols is None:
                found["M6"].append((name, f"versions_table {vt} not found in the migrations"))
            else:
                if (registry.get(vt) or {}).get("mutability") not in ("APPEND_ONLY", "CONDITIONAL_UPDATE"):
                    found["M6"].append((vt, "config versions table is not APPEND_ONLY (or CONDITIONAL_UPDATE for the confirm step) in the registry"))
                required = [("author", ("author_id", "authored_by")), ("authored_at", ("authored_at",)), ("confirmer", ("confirmed_by",)),
                            ("confirmed_at", ("confirmed_at",)), ("decision_ref", ("decision_ref",)), ("effective date", ("effective_from_date",))]
                missing = [label for label, names in required if not any(n in vcols for n in names)]
                if missing:
                    found["M6"].append((vt, "config versions table missing: " + ", ".join(missing)))
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
              "M3": "state column without a declared transition history", "M4": "master data candidate without validity intervals", "M6": "config versions table without author / confirmer / decision_ref / effective date",
              "M5": "key relation not a foreign key"}
    print(f"tables scanned: {total} (statements the scan does not model: {unsupported}; must be 0 for the counts to be complete)")
    for k in ("M1", "M2", "M3", "M4", "M5", "M6"):
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
CREATE TABLE demo.rules (id uuid PRIMARY KEY);
CREATE TABLE demo.addresses (id uuid PRIMARY KEY);
CREATE TABLE demo.address_versions (id uuid PRIMARY KEY, address_id uuid REFERENCES demo.addresses (id));
CREATE TABLE demo.sheets (id uuid PRIMARY KEY, total_hours numeric, balance_days integer, cost_rate numeric, other_id uuid, third_id uuid);
CREATE TABLE demo.others (id uuid PRIMARY KEY);
CREATE TABLE demo.thirds (id uuid PRIMARY KEY);
ALTER TABLE demo.sheets ADD CONSTRAINT fk_o FOREIGN KEY (other_id) REFERENCES demo.others (id), ADD CONSTRAINT fk_t FOREIGN KEY (third_id) REFERENCES demo.thirds (id);
CREATE TABLE demo.empty (id uuid PRIMARY KEY);
CREATE TABLE demo.flags (id uuid PRIMARY KEY, flag_status text CHECK (flag_status = ANY (ARRAY['A'::text, 'B'::text])));
CREATE TABLE demo.bad_cat (id uuid PRIMARY KEY);
CREATE TABLE demo.cfg (id uuid PRIMARY KEY);
CREATE TABLE demo.no_vt_cfg (id uuid PRIMARY KEY);
CREATE TABLE demo.bare_cfg (id uuid PRIMARY KEY);
CREATE TABLE demo.ghost_cfg (id uuid PRIMARY KEY);
CREATE TABLE demo.mutable_cfg (id uuid PRIMARY KEY);
CREATE TABLE demo.mutable_cfg_versions (id uuid PRIMARY KEY, author_id uuid, authored_at timestamptz, confirmed_by uuid, confirmed_at timestamptz, decision_ref text, effective_from_date date);
CREATE TABLE demo.cfg_versions (id uuid PRIMARY KEY, cfg_id uuid REFERENCES demo.cfg (id), author_id uuid, authored_at timestamptz, confirmed_by uuid, confirmed_at timestamptz, decision_ref text, effective_from_date date, version_status text CHECK (version_status = ANY (ARRAY['A'::text, 'B'::text])));
CREATE TABLE demo.rule_versions (id uuid PRIMARY KEY, rule_id uuid REFERENCES demo.rules (id), author_id uuid, authored_at timestamptz, confirmed_by uuid, effective_from_date date);
CREATE TABLE demo2.tickets (id uuid PRIMARY KEY, ticket_status text NOT NULL CHECK (ticket_status IN ('NEW','CLOSED')), created_at timestamptz NOT NULL);
CREATE TABLE demo2.state_transitions (id uuid PRIMARY KEY);
CREATE TABLE demo3.notes (id uuid PRIMARY KEY, note_status text NOT NULL CHECK (note_status IN ('A','B')));
CREATE TABLE demo.lines (id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES demo.orders (id), unit_code text, line_quantity numeric(10,2), price_currency_code text, price numeric);
""")
        open(os.path.join(tmp, "backend", "architecture", "data-access-registry.yaml"), "w", encoding="utf-8").write(
            "tables:\n  demo.customers:\n    role: AGGREGATE_ROOT\n    mutability: MUTABLE\n    data_category: 主数据\n  demo.orders:\n    role: BUSINESS_DOCUMENT\n    business_date_column: order_date\n  demo.rules:\n    role: CONFIG_VERSIONED\n    versions_table: demo.rule_versions\n  demo.rule_versions:\n    mutability: APPEND_ONLY\n  demo.addresses:\n    role: AGGREGATE_ROOT\n    mutability: MUTABLE\n    data_category: 主数据\n  demo.empty:\n  demo.bad_cat:\n    data_category: master\n  demo.cfg:\n    role: CONFIG_VERSIONED\n    versions_table: demo.cfg_versions\n  demo.cfg_versions:\n    mutability: APPEND_ONLY\n  demo.no_vt_cfg:\n    role: CONFIG_VERSIONED\n  demo.bare_cfg:\n    role: CONFIG_VERSIONED\n    versions_table: bare_cfg_versions\n  demo.ghost_cfg:\n    role: CONFIG_VERSIONED\n    versions_table: demo.ghost_cfg_versions\n  demo.mutable_cfg:\n    role: CONFIG_VERSIONED\n    versions_table: demo.mutable_cfg_versions\n  demo.mutable_cfg_versions:\n    mutability: MUTABLE\n  demo3.notes:\n    state_transitions_exempt: technical note, no lifecycle\n")
        found, total, _ = scan_all(tmp)
        items = {k: {i for i, _ in v} for k, v in found.items()}
        assert "demo.orders.total_amount" in items["M1"], "M1 money without currency"
        assert "demo.orders.quantity" in items["M1"], "M1 quantity without unit"
        assert "demo.lines.line_quantity" not in items["M1"] and "demo.lines.price" not in items["M1"], "M1: unit / currency present"
        assert "demo.orders" in items["M2"], "M2"
        assert "demo.customers" in items["M4"], "M4: a 主数据 table without a versions table"
        assert items["M6"] == {"demo.rule_versions", "demo.no_vt_cfg", "demo.bare_cfg", "demo.ghost_cfg", "demo.mutable_cfg_versions"}, "M6: the four errors; a complete APPEND_ONLY table passes"
        why = {i: w for i, w in found["M6"]}
        assert "missing" in why["demo.rule_versions"] and "confirmed_at" in why["demo.rule_versions"], "M6: missing columns reported with their names"
        assert "without versions_table" in why["demo.no_vt_cfg"] and "schema.table" in why["demo.bare_cfg"] and "not found" in why["demo.ghost_cfg"], "M6: distinct reasons"
        assert "APPEND_ONLY" in why["demo.mutable_cfg_versions"], "M6: mutability reason"
        assert "demo.flags.flag_status" in items["M3"], "M3: = ANY (ARRAY[...]) counts as an enumeration CHECK"
        assert "demo.addresses" not in items["M4"], "M4: address_versions found for addresses"
        assert "demo.bad_cat" in items["M4"], "M4: invalid data_category"
        assert not {"demo.sheets.total_hours", "demo.sheets.balance_days", "demo.sheets.cost_rate"} & items["M1"], "M1: unit in the name"
        assert not {"demo.sheets.other_id", "demo.sheets.third_id"} & items["M5"], "M5: every clause of ALTER TABLE counts"
        assert "demo.cfg_versions.version_status" not in items["M3"], "M3: a CONFIG_VERSIONED versions table is its own history; ANY (ARRAY) check parsed"
        assert "demo.orders.order_status" in items["M3"], "M3"
        assert "demo2.tickets.ticket_status" not in items["M3"], "M3: the schema has a state_transitions table"
        assert "demo3.notes.note_status" not in items["M3"], "M3: a registry exemption with a reason"
        assert "demo.orders.customer_id" in items["M5"], "M5: same-schema table without FK"
        assert "demo.lines.order_id" not in items["M5"], "M5: inline REFERENCES counts"
    print("self-test: ok")


def main(argv):
    if "--self-test" in argv:
        self_test()
        return
    root = ROOT
    if "--root" in argv:
        if argv.index("--root") + 1 >= len(argv):
            sys.exit("--root needs a directory")
        root = os.path.abspath(argv[argv.index("--root") + 1])
    found, total, unsupported = scan_all(root)
    report(found, total, unsupported, "--json" in argv)


if __name__ == "__main__":
    main(sys.argv[1:])
