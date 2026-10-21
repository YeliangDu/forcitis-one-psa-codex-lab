#!/usr/bin/env python3
"""Build data/table_inventory.csv: one row per table (built and proposed), machine readable, for the panoramic trace generator.

Inputs (all read only):
  --registry   backend/architecture/data-access-registry.yaml (built tables; role, aggregate)
  --migrations backend/src/main/resources/db/migration (columns: status column, legal entity column) through scripts/ci/baseline_ddl_scan.py
  --object-list docs/modernization/data/TABLE_OBJECT_LIST.md (OBJ for built tables, my judgement)
  --aggregates ENTERPRISE_AGGREGATES_DRAFT.md (AGG name, OBJ in brackets, BC id and module)
  --finance / --people / --revenue  data-model.<group>.yaml (proposed tables; column families only)
Columns: schema, table, bc_module, bc_id, aggregate, obj, kind, has_status, legal_entity_column, source, registry_role
  kind: 业务记录 | 版本 | 关系 | 投影 | 技术   (registry_role is kept so the mapping can be redone)
  has_status / legal_entity_column: yes | no | unknown (proposed tables only have column families, so a family named 状态 / 法人 is the evidence)
Run from the repository root: python3 docs/modernization/data/build_table_inventory.py --registry … (see --help).
"""
import argparse
import csv
import importlib.util
import os
import re
import sys

import yaml

TECH = {"QUEUE", "COUNTER", "IDEMPOTENCY", "FRAMEWORK", "ONE_TIME", "LINEAGE", "LOG_APPEND_ONLY", "PLACEHOLDER"}
RELATION_TABLES = re.compile(r"(legal_entities|_links?|role_permissions|account_roles|account_role_grants)$")
RELATION_EXACT = {"organization_workforce.org_unit_legal_entities", "organization_workforce.employee_legal_entities",
                  "organization_workforce.legal_entity_attributions", "platform_identity.role_permissions", "platform_identity.account_roles"}
PROJECTION_ROLES = {"SNAPSHOT"}
# technical by one-data's judgement although the registry role is AGGREGATE_*: the organisation synchronisation run records
TECH_TABLES = {"organization_workforce.sync_runs", "organization_workforce.sync_diffs", "organization_workforce.sync_run_held_separations"}
# BCs whose module the aggregate draft's BC table does not give in the format the parser reads (B-31 has no module yet)
BC_FALLBACK = {"B-01": "masterdata.counterparty", "B-02": "masterdata.catalog", "B-03": "presales.opportunity", "B-05": "contract.agreement",
               "B-26": "delivery.inspection", "B-27": "organization.leave"}


def kind_of(table, role):
    if role in TECH or table in TECH_TABLES:
        return "技术"
    if table in RELATION_EXACT:
        return "关系"
    if role == "DIMENSION_VERSIONED" or table.endswith("_versions"):
        return "版本"
    if role in PROJECTION_ROLES:
        return "投影"
    return "业务记录"


def load_scan(path):
    spec = importlib.util.spec_from_file_location("baseline_ddl_scan", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".", help="checkout or export holding backend/ and scripts/ci/")
    ap.add_argument("--object-list", required=True)
    ap.add_argument("--aggregates", required=True)
    ap.add_argument("--finance", required=True)
    ap.add_argument("--people", required=True)
    ap.add_argument("--revenue", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    root = a.root
    registry = yaml.safe_load(open(os.path.join(root, "backend/architecture/data-access-registry.yaml"), encoding="utf-8"))["tables"]
    scan = load_scan(os.path.join(root, "scripts/ci/baseline_ddl_scan.py"))
    tables, _, _ = scan.load(os.path.join(root, "backend/src/main/resources/db/migration"))
    # OBJ for built tables from my table object list
    obj_of = {}
    for line in open(a.object_list, encoding="utf-8"):
        m = re.match(r"^\| ([a-z_]+\.[a-z_*, ]+)[^|]* \|.*\| (OBJ-\d+)[^|]* \|", line)
        cells = [c.strip() for c in line.split("|")]
        if len(cells) > 6 and re.match(r"^[a-z_]+\.[a-z_]+", cells[1]):
            found = re.search(r"OBJ-\d+", cells[5])
            for name in re.split(r"、", cells[1].split("（")[0]):
                name = name.strip()
                if "." not in name and ("、" in cells[1]):
                    name = cells[1].split(".")[0] + "." + name
                if found:
                    obj_of[name] = found.group(0)
    # aggregates: AGG -> (name, OBJ), module -> B-id
    agg_text = open(a.aggregates, encoding="utf-8").read()
    agg = {}
    for l in agg_text.split("\n"):
        m = re.match(r"^\| (AGG-\d+) \| ([^|]+)\|", l)
        if m and m.group(1) not in agg:
            name = m.group(2).strip()
            o = re.search(r"OBJ-\d+", name)
            agg[m.group(1)] = (name, o.group(0) if o else "")
    bc_mod, bc_id_of = {}, {}
    for l in agg_text.split("\n"):
        m = re.match(r"^\| (B-\d+) \| [^|]+\| ([^|]+)\|", l)
        if m and m.group(1) not in bc_mod:
            mod = re.search(r"`([^`]+)`", m.group(2))
            if mod:
                bc_mod[m.group(1)] = mod.group(1)
                bc_id_of[mod.group(1)] = m.group(1)
    rows = []
    for full, entry in registry.items():
        schema, table = full.split(".", 1)
        module = schema.replace("_", ".", 1)
        t = tables.get(full)
        if t:
            cols = t["columns"]
            has_status = "yes" if any(c == "status" or c.endswith("_status") for c in cols) else "no"
            legal = "yes" if any("legal_entity" in c for c in cols) else "no"
        else:
            has_status = legal = "unknown"
        rows.append([schema, table, module, bc_id_of.get(module, ""), entry.get("aggregate", ""), obj_of.get(full, ""),
                     kind_of(full, entry.get("role")), has_status, legal, "已建", entry.get("role", "")])
    extra = [  # proposed tables the registry on main does not hold yet
        ("platform_identity", "role_versions", "Role", "版本", "DIMENSION_VERSIONED", "提议·ADR-0034-IMPL PR-1（#476，已复核）", "yes", "no"),
        ("platform_identity", "account_role_grants", "UserAccount", "版本", "DIMENSION_VERSIONED", "提议·ADR-0034-IMPL PR-1（#476，已复核）", "no", "no"),
        ("platform_identity", "role_version_custom_org_units", "Role", "业务记录", "AGGREGATE_CHILD", "提议·F-1（已签发）", "no", "no"),
        ("platform_identity", "authorization_request_custom_org_units", "AuthorizationRequest", "业务记录", "AGGREGATE_CHILD", "提议·F-1（已签发）", "no", "no"),
        ("platform_identity", "permission_adjustments", "PermissionAdjustment", "业务记录", "AGGREGATE_ROOT", "提议·F-1（已签发）", "yes", "no"),
    ]
    for schema, table, aggregate, kind, role, source, st, le in extra:
        module = schema.replace("_", ".", 1)
        rows.append([schema, table, module, bc_id_of.get(module, ""), aggregate, "", kind, st, le, source, role])
    for schema in ("cost_timesheet", "platform_approval", "platform_identity", "platform_integration", "platform_numbering", "organization_workforce"):
        module = schema.replace("_", ".", 1)
        rows.append([schema, "state_transitions", module, bc_id_of.get(module, ""), "", "", "业务记录", "no", "yes",
                     "提议·ADR-0058（设计第 2 版，待复核签发）", "LOG_APPEND_ONLY"])
    for group, path in (("finance", a.finance), ("people", a.people), ("revenue", a.revenue)):
        d = yaml.safe_load(open(path, encoding="utf-8"))
        if isinstance(d, dict):
            d = d["aggregates"]
        for ag in d:
            module = bc_mod.get(ag["bc"]) or BC_FALLBACK.get(ag["bc"], "")
            schema = module.replace(".", "_") if module else "tbd"
            name, obj = agg.get(ag["aggregate"], ("", ""))
            for t in ag.get("tables", []):
                fam = " ".join(map(str, t.get("column_families", [])))
                role = t.get("role", "")
                kind = {"root": "业务记录", "child": "业务记录", "version": "版本", "link": "关系", "staging": "技术"}.get(role, "业务记录")
                rows.append([schema, t["name"], module, ag["bc"], f'{ag["aggregate"]} {name}'.strip(), obj, kind,
                             "yes" if "状态" in fam else "no", "yes" if "法人" in fam else "no", f"提议·{group}·未签发", role])
    with open(a.out, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["schema", "table", "bc_module", "bc_id", "aggregate", "obj", "kind", "has_status", "legal_entity_column", "source", "registry_role"])
        w.writerows(rows)
    print(f"{len(rows)} rows -> {a.out}")


if __name__ == "__main__":
    main()
