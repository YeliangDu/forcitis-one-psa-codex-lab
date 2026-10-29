#!/usr/bin/env python3
"""No value is concatenated into the SQL of a JdbcClient call in backend/src/main (#810 item 5; lead decision 10-29: values are bound).

The argument of every `.sql(...)` call is split at its top-level `+`; each piece must be one of:
  - a string literal or text block;
  - a constant: an UPPER_SNAKE name, plain or qualified (OrgSnapshotRows.SELECT_STAFF);
  - `<literal or constant>.formatted(...)` or `String.join(...)` whose arguments are themselves allowed pieces;
  - `cond ? a : b` or `(piece)` whose branches are allowed pieces;
  - a call of a registered SQL fragment builder (BUILDERS), which keeps values out on its own.
Anything else (a local variable, a parameter, an unregistered call) may carry a value. A call site that builds its SQL that way is
listed in SITES; both registries only shrink, each entry with its reason, and an entry no longer used fails.

Each failure names the rule, the place and the fix:
  SV-1 a piece of the SQL is not a literal, constant or registered builder
  SV-2 a BUILDERS or SITES entry is no longer used

  sql_values.py              check the repository (run from its root)
  sql_values.py --self-test  a red case per rule
"""
import os
import re
import subprocess
import sys
import tempfile

ROOT_GLOB = "backend/src/main/**.java"
BUILDERS = {  # Class.method -> reason; its own code keeps values out (bound or from an enum)
    "EffectiveAttributionPeriods.select": "picks a fixed table and column by AttributionKind; values are bound",
    "EffectiveAttributionPeriods.subjectColumn": "org_unit_id or employee_id by AttributionKind",
}
_W = "backend/src/main/java/com/forcitis/one/"
SITES = {  # "path: piece" -> reason; the piece is the argument text as the check prints it
    _W + "organization/workforce/infrastructure/persistence/JdbcLegalEntityAttributionRepository.java: sql":
        "a local picking one of two text blocks by AttributionKind",
    _W + "organization/workforce/infrastructure/persistence/JdbcLegalEntityRepository.java: lock":
        "private load(id, lock): every caller passes \"\" or \" for update\"",
    _W + "platform/approval/infrastructure/persistence/JdbcApprovalRequestRepository.java: where":
        "private select(where, lock): every caller passes a literal WHERE with :params",
    _W + "platform/approval/infrastructure/persistence/JdbcApprovalRequestRepository.java: lock":
        "private select(where, lock): every caller passes \"\" or \" FOR UPDATE\"",
    _W + "platform/audit/infrastructure/archive/AuditArchiveJob.java: after": "a local choosing between two literals; values bound as :at, :id",
    _W + "platform/audit/infrastructure/persistence/JdbcAuditEntryQueries.java: where":
        "where(filter, params) and after(...) append fixed conditions and put every value into params",
    _W + "platform/audit/infrastructure/persistence/JdbcAuditEntryQueries.java: where(filter,params)":
        "appends fixed conditions per present filter and puts every value into params",
    _W + "platform/identity/infrastructure/persistence/JdbcEmergencyAdminRepository.java: newCount":
        "a local literal CASE expression formatted in twice; values bound as :now",
    _W + "platform/identity/infrastructure/persistence/RoleRows.java: rest": "every caller passes a text block with :params",
    _W + "platform/integration/infrastructure/connection/JdbcReadOnlyCatalog.java: asked":
        "role names are checked against [a-z]+ just before; IS_SRVROLEMEMBER over a list takes no bind parameter",
    _W + "platform/integration/infrastructure/connection/JdbcReadOnlyCatalog.java: bracketed(systemDatabase)":
        "a database name is an identifier (cannot be bound); bracketed() quotes it and doubles ]",
    _W + "platform/integration/infrastructure/legacy/LegacyOrgNames.java: shortNameSql(viewName)":
        "the view name is an identifier from configuration, checked against [schema.]name in LegacyProperties",
    _W + "platform/integration/infrastructure/persistence/JdbcOutboundDeliveryRepository.java: lock":
        "private cardOf(subject, lock): every caller passes \"\" or \"for update\"",
    _W + "platform/integration/infrastructure/persistence/JdbcSourceReferenceQueries.java: sql.toString()":
        "a StringBuilder appending fixed conditions per present filter; values bound",
    _W + "platform/integration/infrastructure/persistence/JdbcSourceReferenceRepository.java: onConflict":
        "private insertStatement(reference, onConflict): every caller passes \"\" or \" on conflict do nothing\"",
}
CONSTANT = re.compile(r"(?:[A-Za-z_]\w*\.)*[A-Z][A-Z0-9_]*")
CALL = re.compile(r"((?:[A-Za-z_]\w*\.)*[A-Za-z_]\w*)\((.*)\)")


def blank_strings(text, start):
    """From the '(' at start: the call's argument with every string, text block and char literal replaced by S."""
    out, depth, i = [], 0, start
    while i < len(text):
        if text.startswith('"""', i):
            i = text.index('"""', i + 3) + 3
            out.append("S")
            continue
        c = text[i]
        if c == '"':
            i += 1
            while text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
            out.append("S")
            continue
        if c == "'":
            i = text.index("'", i + (3 if text[i + 1] == "\\" else 2)) + 1
            out.append("S")
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return "".join(out[1:])
        out.append(c)
        i += 1
    raise ValueError("unbalanced parentheses")


def without_comments(text):
    """text with // and /* */ comments turned into spaces (newlines kept, so line numbers hold); strings and text blocks untouched."""
    out, i = [], 0
    while i < len(text):
        if text.startswith('"""', i):
            end = text.index('"""', i + 3) + 3
        elif text[i] == '"':
            end = i + 1
            while text[end] != '"':
                end += 2 if text[end] == "\\" else 1
            end += 1
        elif text[i] == "'" and i + 2 < len(text):
            end = text.index("'", i + (3 if text[i + 1] == "\\" else 2)) + 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            end = len(text) if end < 0 else end
            out.append(" " * (end - i))
            i = end
            continue
        elif text.startswith("/*", i):
            end = text.index("*/", i + 2) + 2
            out.append(re.sub(r"[^\n]", " ", text[i:end]))
            i = end
            continue
        else:
            out.append(text[i])
            i += 1
            continue
        out.append(text[i:end])
        i = end
    return "".join(out)


def split_top(text, sep):
    """text split at sep where no (), [] or {} is open."""
    parts, depth, last = [], 0, 0
    for i, c in enumerate(text):
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == sep and depth == 0:
            parts.append(text[last:i])
            last = i + 1
    parts.append(text[last:])
    return parts


def wrapped(piece):
    """True when piece is one parenthesised expression, (a + b), not (a) + (b)."""
    if not (piece.startswith("(") and piece.endswith(")")):
        return False
    depth = 0
    for i, c in enumerate(piece):
        depth += c == "("
        depth -= c == ")"
        if depth == 0 and i < len(piece) - 1:
            return False
    return True


def registered(name, builders):
    return name in builders or ".".join(name.split(".")[-2:]) in builders


def bad_pieces(expr, builders):
    """The pieces of expr that may carry a value."""
    bad = []
    for piece in split_top(expr, "+"):
        piece = piece.strip()
        if piece == "S" or CONSTANT.fullmatch(piece):
            continue
        if wrapped(piece):
            bad += bad_pieces(piece[1:-1], builders)
            continue
        question = split_top(piece, "?")
        branches = split_top(question[1], ":") if len(question) == 2 else []
        if len(branches) == 2:
            bad += bad_pieces(branches[0], builders) + bad_pieces(branches[1], builders)
            continue
        call = CALL.fullmatch(piece)
        if call:
            name, args = call.group(1), call.group(2)
            target, _, method = name.rpartition(".")
            if (method == "formatted" and (target == "S" or CONSTANT.fullmatch(target))) or name == "String.join":
                bad += [b for a in split_top(args, ",") if a.strip() for b in bad_pieces(a, builders)]
                continue
            if registered(name, builders):
                continue
        bad.append(piece)
    return bad


def sources(root):
    out = subprocess.run(["git", "-C", root, "ls-files", ROOT_GLOB], capture_output=True, text=True, check=True).stdout
    return out.split()


def problems(root, files=None, builders=None, sites=None):
    builders = BUILDERS if builders is None else builders
    sites = SITES if sites is None else sites
    found, used = [], set()
    for rel in sources(root) if files is None else files:
        text = without_comments(open(os.path.join(root, rel), encoding="utf-8").read())
        for m in re.finditer(r"\.sql\s*\(", text):
            arg = re.sub(r"\s+", "", blank_strings(text, m.end() - 1))
            used.update(name for name in builders if name + "(" in arg)
            line = text.count("\n", 0, m.start()) + 1
            for piece in bad_pieces(arg, builders):
                key = f"{rel}: {piece}"
                if key in sites:
                    used.add(key)
                    continue
                found.append(f"SV-1 {rel}:{line}: `{piece}` in the SQL is not a literal, constant or registered builder, so it can carry a value."
                             f" fix: bind it as :param, or register the builder in BUILDERS of scripts/guards/sql_values.py with its reason")
    for key in list(builders) + list(sites):
        if key not in used:
            found.append(f"SV-2 {key}: registered in scripts/guards/sql_values.py but no .sql(...) uses it any more. fix: delete the entry")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def hit(code, builders=None, sites=None):
            open(os.path.join(tmp, "A.java"), "w").write(code)
            return sorted({p.split()[0] for p in problems(tmp, ["A.java"], builders or {}, sites or {})})

        ok = '''jdbc.sql("select 1").query(); jdbc.sql("""
            select a from t where id = :id
            """); jdbc.sql(SELECT + " where x = :x"); jdbc.sql(Rows.SELECT_ALL + "s");
            jdbc.sql("select " + String.join(", ", COLUMNS) + " from t"); jdbc.sql(TEMPLATE.formatted(TABLE));
            jdbc.sql("x" + (locked ? " for update" : "")); jdbc.sql("a ')' b" + C);'''
        assert hit(ok) == [], "literals, text blocks, constants, joins, ternaries and parens inside strings pass"
        assert hit('jdbc.sql("select * from t where id = " + id)') == ["SV-1"], "a value concatenated"
        assert hit('jdbc.sql(sql)') == ["SV-1"], "a variable holding SQL"
        assert hit('jdbc.sql("%s".formatted(name))') == ["SV-1"], "a value formatted in"
        assert hit('jdbc.sql("x" + (c ? name : ""))') == ["SV-1"], "a value in a ternary branch"
        assert hit('jdbc.sql((a) + (b))') == ["SV-1"], "two groups are not one"
        assert hit('jdbc.sql(Periods.select(kind) + " x")') == ["SV-1"], "an unregistered builder"
        assert hit('jdbc.sql(Periods.select(kind) + " x")', {"Periods.select": "r"}) == [], "a registered builder"
        assert hit('jdbc.sql(x.Periods.select(kind))', {"Periods.select": "r"}) == [], "a qualified call of a registered builder"
        assert hit('jdbc.sql(sql)', sites={"A.java: sql": "r"}) == [], "a registered site"
        assert hit('jdbc.sql("x")', {"Periods.select": "r"}) == ["SV-2"], "an unused builder entry"
        assert hit('jdbc.sql("x")', sites={"A.java: sql": "r"}) == ["SV-2"], "an unused site entry"
        assert hit('// jdbc.sql(a + b)\n/* x.sql(c) */ jdbc.sql("// not a comment" + C)') == [], "comments are not code; // inside a string is"
        open(os.path.join(tmp, "A.java"), "w").write('jdbc.sql("a" + id)')
        assert "fix: bind it as :param, or register the builder" in problems(tmp, ["A.java"], {}, {})[0], "the fix is named"
    print("sql_values self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(os.getcwd())
    for p in found:
        print(p)
    print(f"sql_values: {len(found)} problems")
    sys.exit(1 if found else 0)
