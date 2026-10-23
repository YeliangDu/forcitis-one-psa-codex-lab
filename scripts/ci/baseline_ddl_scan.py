#!/usr/bin/env python3
"""Report, never fail: scan the baseline DDL for the low-level items that are easy to miss by eye (one-data's SCAN_SUPPLEMENT).

Seven classes, each listed in full as `schema.table.column`:
  1 text / text[] column with no format constraint (no char_length, pattern or enumeration CHECK); marks columns already in data-classification.txt
  2 business default: a DEFAULT that is not a technical initial value (now() and the numeric 0 are excluded)
  3 nullable non-key column whose comment does not say what NULL means (literal 「为空表示」, an equivalent wording, or nothing)
  4 paired nullable columns with no paired CHECK (a candidate list by name: <x>_by with <x>_at, and a nullable *reason / *_at / *_kind column of a table with a status column when no CHECK mentions both;
    groups that name patterns cannot see, e.g. idempotent_responses.response_*, timesheets.submitted_date with round_number, are not found; a candidate is not yet a defect)
  5 DEFAULT now() (the application injects the clock, NAMING_STANDARD 2.4a-3); marks columns whose own comment says the time comes from the injected clock; INSERTs that call now()
  6 enumeration CHECK written inline instead of `CONSTRAINT ck_... CHECK`
  7 enumeration CHECK or seed row holding a value that belongs to a module not built yet (VALUE_OWNER below against ssot/t-transitions/transitions.yaml: delivers of stages that are done or in progress count as built)

Usage: baseline_ddl_scan.py [--json] [--migrations DIR] | baseline_ddl_scan.py --self-test
Class 8 counts statements the scan does not model (ALTER / DROP forms, LIKE, PARTITION OF, comments on unknown columns): it must stay 0 for the other counts to be complete.
Exit status is always 0 for a report; non-zero only if the SQL cannot be read. Regress by `table.column`, not by line number: line numbers drift as the baseline changes.
Framework tables (event_publication*, sessions*, scheduler_locks) are not scanned.
"""
import json
import os
import re
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MIGRATIONS = os.path.join(ROOT, "backend", "src", "main", "resources", "db", "migration")
PAIRS = os.path.join(ROOT, "scripts", "ci", "baseline_ddl_scan_pairs.txt")
CLASSIFICATION = os.path.join(ROOT, "backend", "architecture", "data-classification.txt")
TRANSITIONS = os.path.join(ROOT, "docs", "modernization", "architecture", "ssot", "t-transitions", "transitions.yaml")
FRAMEWORK = re.compile(r"^(?:[a-z_]+\.)?(event_publication|sessions|scheduler_locks)")
# Values that belong to a module which is not built yet: value -> (owner, module id checked against the built set or None for "never", tables the rule is limited to or None for all).
# Kept by hand next to the transitions they come from; one-data / one-architecture review it. ORG_SOURCE is a legal source system elsewhere (platform_integration.source_references).
VALUE_OWNER = {"QUOTE": ("presales.quoting", "presales.quoting", None), "JOURNAL_VOUCHER": ("accounting.ledger", "accounting.ledger", None),
               "CUSTOM_ORG_UNITS": ("data_scope_grants (1b), not in the baseline or any ADR", None, None),
               "ORG_SOURCE": ("the organisation source holds no document numbers (LN-2)", None, {"platform_numbering.legacy_numbers"})}


def unbuilt(value, where_table, built):
    owner = VALUE_OWNER.get(value)
    if not owner:
        return None
    label, module, tables = owner
    if tables and where_table not in tables:
        return None
    return label if module is None or module not in built else None


# the implicit wording (才有 / 仅 / 清空) is only a hint: one-data counted 16 by hand, this pattern finds 13; the other three (effect_kind, escalate_at, handled_result) are implicit by context, not by a keyword
NULL_WORDS = re.compile(r"为空|留空|空表示|NULL|未设置|没有则空")
IMPLICIT = re.compile(r"才有|仅|只在|只有|清空")
FORMAT_CHECK = re.compile(r"char_length|length\s*\(|~|similar to|\bIN\s*\(|=\s*ANY\s*\(\s*ARRAY|<@|=\s*'", re.I)


def strip_statements(sql):
    """Split on `;` outside quotes and comments; drop comments."""
    out, buf, i, quote, n = [], [], 0, False, len(sql)
    while i < n:
        ch = sql[i]
        if quote:
            buf.append(ch)
            if ch == "'":
                if i + 1 < n and sql[i + 1] == "'":
                    buf.append("'")
                    i += 1
                else:
                    quote = False
        elif ch == "'":
            quote = True
            buf.append(ch)
        elif ch == "$" and re.match(r"\$[A-Za-z_0-9]*\$", sql[i:]):
            tag = re.match(r"\$[A-Za-z_0-9]*\$", sql[i:]).group(0)
            end = sql.find(tag, i + len(tag))
            end = n if end < 0 else end + len(tag)
            buf.append(sql[i:end])
            i = end
            continue
        elif sql.startswith("--", i):
            while i < n and sql[i] != "\n":
                i += 1
            continue
        elif sql.startswith("/*", i):
            end = sql.find("*/", i + 2)
            i = n if end < 0 else end + 1
        elif ch == ";":
            out.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
        i += 1
    if "".join(buf).strip():
        out.append("".join(buf).strip())
    return [s for s in out if s]


def balanced(text, start):
    """Index of the parenthesis that closes the one opened at text[start]."""
    depth, quote, i = 0, False, start
    while i < len(text):
        ch = text[i]
        if quote:
            if ch == "'":
                if text[i + 1:i + 2] == "'":
                    i += 1
                else:
                    quote = False
        elif ch == "'":
            quote = True
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError("unbalanced parentheses")


def split_top(body):
    parts, depth, quote, cur = [], 0, False, []
    i = 0
    while i < len(body):
        ch = body[i]
        if quote:
            cur.append(ch)
            if ch == "'":
                if body[i + 1:i + 2] == "'":
                    cur.append("'")
                    i += 1
                else:
                    quote = False
        elif ch == "'":
            quote = True
            cur.append(ch)
        elif ch == "(":
            depth += 1
            cur.append(ch)
        elif ch == ")":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
        i += 1
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def read_checks(text):
    """(name or None, expression) for every CHECK (...) in text."""
    found = []
    for m in re.finditer(r"(?:CONSTRAINT\s+([a-z0-9_]+)\s+)?CHECK\s*\(", text, re.I):
        open_at = m.end() - 1
        found.append((m.group(1), text[open_at + 1:balanced(text, open_at)]))
    return found


def parse_column(element):
    m = re.match(r"([a-z_][a-z0-9_]*)\s+(.*)", element, re.I | re.S)
    name, rest = m.group(1), m.group(2)
    type_end = re.search(r"\s(NOT NULL|NULL|DEFAULT|CHECK|PRIMARY|REFERENCES|UNIQUE|GENERATED|CONSTRAINT)\b|$", rest, re.I)
    ctype = rest[:type_end.start()].strip().lower()
    default = None
    d = re.search(r"\bDEFAULT\s+", rest, re.I)
    if d:
        tail = rest[d.end():]
        depth, quote, j = 0, False, 0
        while j < len(tail):
            ch = tail[j]
            if quote:
                if ch == "'" and tail[j + 1:j + 2] != "'":
                    quote = False
                elif ch == "'":
                    j += 1
            elif ch == "'":
                quote = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            elif depth == 0 and re.match(r"\s(NOT NULL|NULL|CHECK|PRIMARY|REFERENCES|UNIQUE|CONSTRAINT)\b", tail[j:], re.I):
                break
            j += 1
        default = tail[:j].strip()
    key = bool(re.search(r"\bPRIMARY KEY\b", rest, re.I))
    nullable = not key and not re.search(r"\bNOT NULL\b", rest, re.I)
    return {"name": name, "type": ctype, "nullable": nullable, "default": default, "primary": key, "checks": read_checks(rest), "comment": None}


def load(migrations):
    tables, inserts, unsupported = {}, [], []
    files = sorted(os.path.join(d, f) for d, _, fs in os.walk(migrations) for f in fs if f.endswith(".sql"))
    if not files:
        raise SystemExit(f"no SQL files under {migrations}")
    for path in files:
        for stmt in strip_statements(open(path, encoding="utf-8").read()):
            m = re.match(r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?([a-z0-9_]+\.[a-z0-9_]+)\s*\(", stmt, re.I)
            if m:
                name = m.group(1)
                start = stmt.index("(", m.end() - 1)
                body = stmt[start + 1:balanced(stmt, start)]
                table = {"columns": {}, "order": [], "checks": [], "file": os.path.basename(path)}
                for element in split_top(body):
                    if re.match(r"LIKE\b", element, re.I):
                        unsupported.append(f"{os.path.basename(path)}: {name} inherits columns with LIKE")
                    elif re.match(r"(CONSTRAINT\b|PRIMARY KEY\b|UNIQUE\b|CHECK\b|FOREIGN KEY\b|EXCLUDE\b)", element, re.I):
                        table["checks"] += read_checks(element)
                    else:
                        col = parse_column(element)
                        table["columns"][col["name"]] = col
                        table["order"].append(col["name"])
                tables[name] = table
                continue
            m = re.match(r"COMMENT ON COLUMN\s+([a-z0-9_]+\.[a-z0-9_]+)\.([a-z0-9_]+)\s+IS\s+'((?:[^']|'')*)'", stmt, re.I | re.S)
            if m:
                t = tables.get(m.group(1))
                if t and m.group(2) in t["columns"]:
                    t["columns"][m.group(2)]["comment"] = m.group(3).replace("''", "'")
                else:
                    unsupported.append(f"{os.path.basename(path)}: COMMENT on a column the scan does not know: {m.group(1)}.{m.group(2)}")
                continue
            m = re.match(r"ALTER TABLE\s+(?:ONLY\s+)?([a-z0-9_]+\.[a-z0-9_]+)\s+(.*)", stmt, re.I | re.S)
            if m:
                clauses = split_top(m.group(2))
                if m.group(1) in tables and all(re.match(r"ADD\s+(CONSTRAINT\b|CHECK\b)", c, re.I) for c in clauses):
                    for c in clauses:
                        tables[m.group(1)]["checks"] += read_checks(c)
                else:
                    unsupported.append(f"{os.path.basename(path)}: {stmt[:90]!r}")
                continue
            if re.match(r"INSERT INTO", stmt, re.I):
                inserts.append((os.path.basename(path), stmt))
            elif re.match(r"COMMENT ON COLUMN", stmt, re.I) or re.match(r"CREATE\s+(\w+\s+)*TABLE", stmt, re.I) or re.match(r"(DROP|ALTER|RENAME|TRUNCATE|UPDATE|DELETE)\b", stmt, re.I):
                unsupported.append(f"{os.path.basename(path)}: {stmt[:90]!r}")
    return tables, inserts, unsupported


def declared_pairs(path):
    pairs = []
    if os.path.exists(path):
        for number, line in enumerate(open(path, encoding="utf-8"), 1):
            if not line.strip() or line.startswith("#"):
                continue
            parts = [part.strip() for part in line.split("|")]
            if len(parts) != 3 or not re.fullmatch(r"[a-z0-9_]+\.[a-z0-9_]+", parts[0]) or not all(c.strip() for c in parts[1].split(",")) or not parts[2]:
                raise SystemExit(f"{path}:{number}: expected `<schema.table> | <column>, <column> | <why>`")
            pairs.append((parts[0], [c.strip() for c in parts[1].split(",")], parts[2]))
    return pairs


def classification(path):
    registered = set()
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                registered.add(line.split("|")[0].strip())
    return registered


def built_modules(path):
    """Modules delivered by stages that are done or in progress; `delivers: [...]` may continue over several lines."""
    modules = set()
    if not os.path.exists(path):
        return modules
    text = open(path, encoding="utf-8").read()
    for block in re.split(r"(?m)^- id:", text)[1:]:
        status = re.search(r"(?m)^\s*status:\s*(\S+)", block)
        delivers = re.search(r"delivers:\s*\[(.*?)\]", block, re.S)
        if status and delivers and status.group(1) in ("done", "in-progress"):
            modules.update(re.findall(r"[a-z]+\.[a-z]+", re.sub(r"#.*", "", delivers.group(1))))
    return modules


def scan(tables, inserts, registered, built, pairs=()):
    r = {k: [] for k in ("text_no_format", "business_default", "null_meaning", "paired_nullable", "default_now", "inline_enum_check", "unbuilt_value")}
    extra = {"named_enum_checks": 0, "technical_zero": 0, "now_in_insert": []}
    for tname, table in tables.items():
        if FRAMEWORK.match(tname.split(".", 1)[1]) or FRAMEWORK.match(tname):
            continue
        all_checks = [c for col in table["columns"].values() for c in col["checks"]] + table["checks"]
        for cname in table["order"]:
            col = table["columns"][cname]
            key = f"{tname}.{cname}"
            col_checks = col["checks"] + [c for c in table["checks"] if re.search(rf"\b{cname}\b", c[1])]
            if col["type"] in ("text", "text[]"):
                # a CHECK that only mentions the column (a comparison, a denial of one value) is no format; FORMAT_CHECK errs towards "constrained" for `col = 'X'` conditions
                strong = any(FORMAT_CHECK.search(expr) for _, expr in col_checks)
                if not strong:
                    r["text_no_format"].append({"column": key, "nullable": col["nullable"], "array": col["type"] == "text[]",
                                                "registered": key in registered, "has_other_check": bool(col_checks)})
            d = col["default"]
            if d is not None:
                if re.fullmatch(r"(now\(\)|clock_timestamp\(\)|current_timestamp)", d, re.I):
                    contradicted = bool(col["comment"] and re.search(r"注入|时钟", col["comment"]))
                    r["default_now"].append({"column": key, "comment_says_injected_clock": contradicted})
                elif re.fullmatch(r"0", d):
                    extra["technical_zero"] += 1
                else:
                    r["business_default"].append({"column": key, "default": d})
            if col["nullable"] and not col["primary"]:
                comment = col["comment"] or ""
                kind = "literal" if "为空表示" in comment else "equivalent" if NULL_WORDS.search(comment) else "none"
                r["null_meaning"].append({"column": key, "wording": kind, "implicit": kind == "none" and bool(IMPLICIT.search(comment))})
            for name, expr in col["checks"]:
                if re.search(r"\bIN\s*\(|<@|=\s*ANY\s*\(\s*ARRAY", expr, re.I):
                    if name:
                        extra["named_enum_checks"] += 1
                    else:
                        r["inline_enum_check"].append({"column": key, "expression": expr[:80]})
                    for value in re.findall(r"'([A-Z0-9_]+)'", expr):
                        owner = unbuilt(value, tname, built)
                        if owner:
                            r["unbuilt_value"].append({"where": key, "value": value, "module": owner})
        for name, expr in table["checks"]:
            if re.search(r"\bIN\s*\(|<@|=\s*ANY\s*\(\s*ARRAY", expr, re.I):
                if name:
                    extra["named_enum_checks"] += 1
                else:
                    r["inline_enum_check"].append({"column": tname, "expression": expr[:80]})
                for value in re.findall(r"'([A-Z0-9_]+)'", expr):
                    owner = unbuilt(value, tname, built)
                    if owner:
                        r["unbuilt_value"].append({"where": f"{tname} ({name or 'unnamed'})", "value": value, "module": owner})
        pair_at = set()
        for cname in table["order"]:
            col = table["columns"][cname]
            m = re.fullmatch(r"(.+)_by", cname)
            if m and f"{m.group(1)}_at" in table["columns"] and col["nullable"] and table["columns"][f"{m.group(1)}_at"]["nullable"]:
                if not any(re.search(rf"\b{cname}\b", e) and re.search(rf"\b{m.group(1)}_at\b", e) for _, e in all_checks):
                    r["paired_nullable"].append({"group": f"{tname}.{m.group(1)}_by/_at", "missing": "paired CHECK"})
                    pair_at.add(f"{tname}.{m.group(1)}_at")
        status = [c for c in table["order"] if re.fullmatch(r"(status|[a-z]+_status|[a-z]+_kind)", c) and not table["columns"][c]["nullable"]]
        for cname in table["order"]:
            col = table["columns"][cname]
            if col["nullable"] and (re.search(r"(reason|_at|_kind)$", cname) and cname not in ("created_at", "updated_at")) and status and f"{tname}.{cname}" not in pair_at:
                if not any(re.search(rf"\b{cname}\b", e) and any(re.search(rf"\b{s}\b", e) for s in status) for _, e in all_checks):
                    r["paired_nullable"].append({"group": f"{tname}.{cname} with status", "missing": "CHECK tying it to " + "/".join(status)})
    for tname, columns, why in pairs:
        table = tables.get(tname)
        unknown = [c for c in columns if not table or c not in table["columns"]]
        if unknown:
            r["paired_nullable"].append({"group": f"{tname} (declared: {'/'.join(columns)})", "missing": "declared column does not exist: " + ", ".join(unknown)})
            continue
        checks = table["checks"] + [c for cn in table["order"] for c in table["columns"][cn]["checks"]]
        if not any(all(re.search(rf"\b{c}\b", expr) for c in columns) for _, expr in checks):
            r["paired_nullable"].append({"group": f"{tname} (declared: {'/'.join(columns)})", "missing": "no CHECK mentions all of them: " + why})
    for file, stmt in inserts:
        if re.search(r"\bnow\(\)", stmt, re.I):
            m = re.match(r"INSERT INTO\s+([a-z0-9_.]+)", stmt, re.I)
            extra["now_in_insert"].append(f"{file}: INSERT INTO {m.group(1) if m else '?'}")
        m = re.match(r"INSERT INTO\s+([a-z0-9_.]+)", stmt, re.I)
        for value in re.findall(r"'([A-Z0-9_]+)'", stmt):
            owner = unbuilt(value, m.group(1) if m else "?", built)
            if owner:
                r["unbuilt_value"].append({"where": f"seed {m.group(1) if m else '?'}", "value": value, "module": owner})
    return r, extra


def report(result, extra, unsupported, as_json):
    if as_json:
        print(json.dumps({"classes": result, "extra": extra, "unmodelled_statements": unsupported}, ensure_ascii=False, indent=1))
        return
    names = {"text_no_format": "1 text columns without a format constraint", "business_default": "2 business defaults",
             "null_meaning": "3 nullable columns, what NULL means", "paired_nullable": "4 paired nullable columns without a paired CHECK",
             "default_now": "5 DEFAULT now()", "inline_enum_check": "6 inline (unnamed) enumeration CHECK", "unbuilt_value": "7 values of modules not built yet"}
    for key, title in names.items():
        items = result[key]
        extra_text = ""
        if key == "text_no_format":
            extra_text = f", registered in data-classification.txt: {sum(i['registered'] for i in items)}"
        elif key == "null_meaning":
            counts = {w: sum(i['wording'] == w for i in items) for w in ("literal", "equivalent", "none")}
            extra_text = f", literal 「为空表示」 {counts['literal']}, equivalent {counts['equivalent']}, nothing {counts['none']} (implicit {sum(i['implicit'] for i in items)})"
        elif key == "default_now":
            extra_text = f", comment says injected clock: {sum(i['comment_says_injected_clock'] for i in items)}; INSERTs calling now(): {len(extra['now_in_insert'])}"
        elif key == "business_default":
            extra_text = f" (technical DEFAULT 0 excluded: {extra['technical_zero']})"
        elif key == "inline_enum_check":
            extra_text = f" (already named: {extra['named_enum_checks']})"
        print(f"== {title}: {len(items)}{extra_text}")
        for item in items:
            print("  " + "; ".join(f"{k}={v}" for k, v in item.items() if v not in (False, None)))
    if extra["now_in_insert"]:
        print("== INSERTs calling now():")
        for line in extra["now_in_insert"]:
            print("  " + line)
    print(f"== 8 statements not modelled by this scan (a finding may hide behind them): {len(unsupported)}")
    for line in unsupported:
        print("  " + line)


def run(migrations=MIGRATIONS):
    tables, inserts, unsupported = load(migrations)
    return scan(tables, inserts, classification(CLASSIFICATION), built_modules(TRANSITIONS), declared_pairs(PAIRS)), tables, unsupported


def self_test():
    sql = """
CREATE SCHEMA IF NOT EXISTS platform_approval;
CREATE TABLE platform_approval.approval_requests (
    id uuid PRIMARY KEY,
    summary_text text NOT NULL,
    status text NOT NULL CHECK (status IN ('RUNNING', 'DONE')),
    result_reason text,
    document_type text NOT NULL CHECK (document_type IN ('QUOTE', 'TIMESHEET', 'JOURNAL_VOUCHER')),
    released_by uuid,
    released_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    plain text NOT NULL CHECK (char_length(plain) <= 20),
    CONSTRAINT ck_approval_requests_status CHECK (status IN ('RUNNING', 'DONE', 'X'))
);
COMMENT ON COLUMN platform_approval.approval_requests.created_at IS '创建时间：来自注入的时钟；含分号; 与 ''引号''';
COMMENT ON COLUMN platform_approval.approval_requests.result_reason IS '驳回原因；为空表示未驳回';
CREATE TABLE platform_identity.user_accounts (
    id uuid PRIMARY KEY,
    status text NOT NULL DEFAULT 'ENABLED',
    version bigint NOT NULL DEFAULT 0
);
INSERT INTO platform_approval.approval_requests (id, created_at, document_type) VALUES ('x', now(), 'QUOTE');
ALTER TABLE platform_approval.approval_requests ADD CONSTRAINT ck_approval_requests_pair CHECK (plain IS NOT NULL);
"""
    with tempfile.TemporaryDirectory() as folder:
        os.makedirs(os.path.join(folder, "a"))
        open(os.path.join(folder, "a", "V1__baseline.sql"), "w", encoding="utf-8").write(sql)
        tables, inserts, unsupported = load(folder)
        result, extra = scan(tables, inserts, {"platform_approval.approval_requests.summary_text"}, {"platform.approval", "platform.identity"})
    names = lambda key, field="column": {i[field] for i in result[key]}
    ar = "platform_approval.approval_requests"
    assert f"{ar}.summary_text" in names("text_no_format"), "class 1: summary_text has no format constraint"
    assert f"{ar}.plain" not in names("text_no_format"), "class 1: a char_length CHECK is a format constraint"
    assert next(i for i in result["text_no_format"] if i["column"] == f"{ar}.summary_text")["registered"], "class 1: registered columns are marked"
    assert "platform_identity.user_accounts.status" in names("business_default"), "class 2: DEFAULT 'ENABLED'"
    assert "platform_identity.user_accounts.version" not in names("business_default") and extra["technical_zero"] == 1, "class 2: DEFAULT 0 is a technical initial value"
    kinds = {i["column"]: i["wording"] for i in result["null_meaning"]}
    assert kinds[f"{ar}.result_reason"] == "literal" and kinds[f"{ar}.released_by"] == "none", "class 3"
    assert any(i["column"] == f"{ar}.created_at" and i["comment_says_injected_clock"] for i in result["default_now"]), "class 5: created_at DEFAULT now() against 'injected clock'"
    assert len(extra["now_in_insert"]) == 1, "class 5: INSERT calling now()"
    assert f"{ar}.status" in names("inline_enum_check") and extra["named_enum_checks"] == 1, "class 6: inline vs named"
    assert any(i["value"] == "QUOTE" for i in result["unbuilt_value"]) and any(i["value"] == "JOURNAL_VOUCHER" for i in result["unbuilt_value"]), "class 7: QUOTE and JOURNAL_VOUCHER"
    assert not any(i["value"] == "TIMESHEET" for i in result["unbuilt_value"]), "class 7: a value of a built module is fine"
    assert unbuilt("ORG_SOURCE", "platform_integration.source_references", set()) is None and unbuilt("ORG_SOURCE", "platform_numbering.legacy_numbers", set()), "class 7: ORG_SOURCE only counts in legacy_numbers"
    assert unbuilt("QUOTE", "x.y", {"presales.quoting"}) is None, "class 7: once the module is built the value is fine"
    assert any("released_by" in i["group"] for i in result["paired_nullable"]), "class 4: released_by/_at without a paired CHECK"
    assert strip_statements("SELECT 'a;b'; -- x; y\nSELECT 2") == ["SELECT 'a;b'", "SELECT 2"], "statement splitting respects quotes and comments"
    fixture = """
CREATE TABLE platform_idempotency.idempotent_responses (id uuid PRIMARY KEY, response_number integer, response_name text, response_text text, response_header_text text);
CREATE TABLE cost_timesheet.timesheets (id uuid PRIMARY KEY, submitted_date date, round_number integer NOT NULL, CONSTRAINT ck_timesheets_round CHECK (round_number >= 0));
"""
    with tempfile.TemporaryDirectory() as folder:
        open(os.path.join(folder, "V1__f.sql"), "w", encoding="utf-8").write(fixture)
        ft, fi, _ = load(folder)
        declared = declared_pairs(PAIRS)
        found, _ = scan(ft, fi, set(), set(), declared)
        stale, _ = scan(ft, fi, set(), set(), [("cost_timesheet.timesheets", ["submitted_date", "no_such_column"], "x")])
    groups = " ".join(i["group"] for i in found["paired_nullable"])
    assert "idempotent_responses (declared: response_number/response_name/response_text/response_header_text)" in groups, "class 4 declared: the response_* group is found"
    assert "timesheets (declared: submitted_date/round_number)" in groups, "class 4 declared: submitted_date with round_number is found"
    with tempfile.TemporaryDirectory() as folder:
        open(os.path.join(folder, "V1__f.sql"), "w", encoding="utf-8").write(fixture.replace("CHECK (round_number >= 0)", "CHECK (round_number >= 0 AND (submitted_date IS NULL) = (round_number = 0))"))
        ft2, fi2, _ = load(folder)
        fixed, _ = scan(ft2, fi2, set(), set(), [("cost_timesheet.timesheets", ["submitted_date", "round_number"], "x")])
    assert not [i for i in fixed["paired_nullable"] if "declared" in i["group"]], "class 4 declared: a CHECK naming all columns satisfies the group"
    assert any("declared column does not exist" in i["missing"] for i in stale["paired_nullable"]), "class 4 declared: a stale declaration is reported"
    with tempfile.TemporaryDirectory() as folder:
        open(os.path.join(folder, "V1__f.sql"), "w", encoding="utf-8").write(fixture.replace("submitted_date date,", "submitted_date date CHECK (submitted_date IS NULL OR round_number > 0),"))
        ft3, fi3, _ = load(folder)
        inline, _ = scan(ft3, fi3, set(), set(), [("cost_timesheet.timesheets", ["submitted_date", "round_number"], "x")])
    assert not [i for i in inline["paired_nullable"] if "declared" in i["group"]], "class 4 declared: a column-level CHECK naming all columns satisfies the group"
    path = os.path.join(tempfile.mkdtemp(), "pairs.txt")
    open(path, "w", encoding="utf-8").write("a.b | c, , d | why\n")
    try:
        declared_pairs(path)
    except SystemExit:
        pass
    else:
        raise AssertionError("class 4 declared: a blank column name in the list must be refused")
    kinds_all = {i["column"]: i["wording"] for i in result["null_meaning"]}
    assert kinds_all[f"{ar}.released_at"] == "none", "class 3"
    assert any(i["where"] == "seed platform_approval.approval_requests" and i["value"] == "QUOTE" for i in result["unbuilt_value"]), "class 7: a seed row holding QUOTE"
    assert any(i["group"] == f"{ar}.released_by/_at" for i in result["paired_nullable"]) and sum(i["group"].startswith(f"{ar}.released_at") for i in result["paired_nullable"]) == 0, "class 4: a _by/_at pair is reported once"
    assert any(i["group"] == f"{ar}.result_reason with status" for i in result["paired_nullable"]), "class 4: a nullable reason of a table with a status column and no CHECK tying them"
    assert len(unsupported) == 0, unsupported
    hidden = """
CREATE TABLE platform_x.t (id uuid PRIMARY KEY, note text);
ALTER TABLE platform_x.t ADD CONSTRAINT ck_t_note CHECK (char_length(note) < 5), ALTER COLUMN note SET DEFAULT 'a';
ALTER TABLE platform_x.t ADD note2 text;
ALTER TABLE platform_x.u ADD CONSTRAINT ck_u CHECK (true);
CREATE TABLE platform_x.v (LIKE platform_x.t);
CREATE TABLE platform_x.w PARTITION OF platform_x.t FOR VALUES IN (1);
COMMENT ON COLUMN platform_x.t.ghost IS 'x';
CREATE FUNCTION f() RETURNS void AS $$ BEGIN PERFORM 1; END; $$ LANGUAGE plpgsql;
DROP TABLE platform_x.old;
COMMENT ON COLUMN t.c IS 'no schema';
COMMENT ON COLUMN platform_x.t.note IS E'escaped';
CREATE UNLOGGED TABLE platform_x.z (id uuid);
"""
    with tempfile.TemporaryDirectory() as folder:
        open(os.path.join(folder, "V1__x.sql"), "w", encoding="utf-8").write(hidden)
        _, _, lost = load(folder)
    for needle in ("ck_t_note", "ADD note2", "platform_x.u", "LIKE", "PARTITION OF", "ghost", "DROP TABLE", "COMMENT ON COLUMN t.c", "E'escaped'", "UNLOGGED"):
        assert sum(needle in line for line in lost) == 1, f"the form {needle!r} must be reported exactly once: {lost}"
    assert len(lost) == 10, f"no statement form is reported twice or skipped: {lost}"
    assert strip_statements("SELECT $$a;b$$; SELECT 2") == ["SELECT $$a;b$$", "SELECT 2"], "dollar-quoted bodies are not split"
    transitions = built_modules(TRANSITIONS)
    assert {"platform.approval", "organization.workforce", "cost.timesheet"} <= transitions, f"delivers lists that continue over lines are read in full: {sorted(transitions)}"
    assert "presales.quoting" not in transitions, "a not-started stage delivers nothing yet"
    print("baseline_ddl_scan self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    args = sys.argv[1:]
    folder = args[args.index("--migrations") + 1] if "--migrations" in args else MIGRATIONS
    (res, ex), _, unsup = run(folder)
    report(res, ex, unsup, "--json" in args)
