#!/usr/bin/env python3
"""Every numeric policy.json key in the checked sections has a script that reads it (#822): an unread limit only looks enforced.

A key counts as read when its name appears quoted ("key" or 'key') in a file under scripts/, tools/ or .github/, leaving out self-test files,
self-test functions inside a script (def self_test..., def *_selftest...) and this file. Keys read only by the tool library (outside the repository) are listed in TOOL_LIBRARY; where the library exists (local runs) the listed
file must really name the key, on CI the entry is trusted.

Each failure names the rule, the key and the fix:
  PK-1 a numeric key in a checked section is read by nothing
  PK-2 a TOOL_LIBRARY entry names a file that does not name the key (local runs only)
  PK-3 a TOOL_LIBRARY entry is for a key policy.json no longer has

  policy_keys_read.py              check the repository (run from its root)
  policy_keys_read.py --self-test  a red case per rule
"""
import ast
import json
import os
import sys
import tempfile

SECTIONS = ("rules", "start")
SEARCH = ("scripts", "tools", ".github")
LIBRARY = os.path.expanduser("~/.modernize/psa/tools")
TOOL_LIBRARY = {  # section.key -> file in the tool library that reads it
    "start.handoff_max_kb": "one-session/handoff/handoff.py",
    "start.sheet_max_kb": "one-session/start_sheet/start_sheet.py",
}
SELF = os.path.realpath(__file__)


def numeric_keys(policy):
    for section in SECTIONS:
        for key, value in (policy.get(section) or {}).items():
            if not key.startswith("_") and isinstance(value, (int, float)) and not isinstance(value, bool):
                yield f"{section}.{key}"


def without_self_tests(text):
    """A Python script's text with its self-test functions blanked: a key a self-test writes into a fake policy is no read."""
    try:
        tree = ast.parse(text)
    except SyntaxError:
        return text
    lines = text.split("\n")
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and ("self_test" in node.name or "selftest" in node.name):
            for i in range(node.lineno - 1, node.end_lineno):
                lines[i] = ""
    return "\n".join(lines)


def corpus(root):
    """Text of every file that may read policy.json: scripts, tools and workflows, without self-tests and this check."""
    texts = []
    for top in SEARCH:
        for folder, dirs, files in os.walk(os.path.join(root, top)):
            dirs[:] = [d for d in dirs if d not in ("node_modules", "__pycache__")]
            for name in files:
                path = os.path.join(folder, name)
                if "selftest" in name or os.path.realpath(path) == SELF:
                    continue
                try:
                    text = open(path, encoding="utf-8").read()
                    texts.append(without_self_tests(text) if name.endswith(".py") else text)
                except (UnicodeDecodeError, OSError):
                    continue
    return "\n".join(texts)


def named(text, key):
    return f'"{key}"' in text or f"'{key}'" in text


def problems(root, library=LIBRARY, tool_library=TOOL_LIBRARY):
    policy = json.load(open(os.path.join(root, "policy.json"), encoding="utf-8"))
    keys = list(numeric_keys(policy))
    text = corpus(root)
    found = []
    for full in keys:
        key = full.split(".", 1)[1]
        if full in tool_library:
            path = os.path.join(library, tool_library[full])
            reads = os.path.exists(path) and "selftest" not in os.path.basename(path) and named(without_self_tests(open(path, encoding="utf-8").read()), key)
            if os.path.isdir(library) and not reads:
                found.append(f"PK-2 {full}: TOOL_LIBRARY says {tool_library[full]} reads it, but that file does not name \"{key}\"."
                             f" fix: point the entry at the reader, or drop the key")
        elif not named(text, key):
            found.append(f"PK-1 {full}: no script under {', '.join(SEARCH)}/ reads it, so the limit is not enforced."
                         f" fix: delete the key, or make the check that needs it read it")
    for full in tool_library:
        if full not in keys:
            found.append(f"PK-3 {full}: listed in TOOL_LIBRARY but not a numeric key of policy.json. fix: delete the entry")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        os.makedirs(os.path.join(tmp, "scripts"))
        lib = os.path.join(tmp, "lib")
        os.makedirs(os.path.join(lib, "x"))

        def rules(policy, script, library_text="kb('sheet', 4)", tool_library=None):
            json.dump(policy, open(os.path.join(tmp, "policy.json"), "w"))
            open(os.path.join(tmp, "scripts", "check.py"), "w").write(script)
            open(os.path.join(lib, "x", "reader.py"), "w").write(library_text)
            return sorted({p.split()[0] for p in problems(tmp, lib, {} if tool_library is None else tool_library)})

        good = {"rules": {"_note": "x", "a_max_kb": 6}, "start": {"b_kb": 2, "flag": True, "name": "s"}, "other": {"c": 1}}
        assert rules(good, 'policy["rules"]["a_max_kb"]; get("b_kb")') == [], "quoted names count; non-numeric, _ and other sections are skipped"
        assert rules(good, 'policy["rules"]["a_max_kb"]') == ["PK-1"], "start.b_kb unread"
        assert rules(good, "a_max_kb = 6; b_kb = 2") == ["PK-1"], "a bare word is no read"
        open(os.path.join(tmp, "scripts", "check_selftest.py"), "w").write('"b_kb"')
        assert rules(good, '"a_max_kb"') == ["PK-1"], "a self-test is no reader"
        os.remove(os.path.join(tmp, "scripts", "check_selftest.py"))
        assert rules(good, 'x = policy["rules"]["a_max_kb"]\n\ndef self_test():\n    write({"b_kb": 2})\n') == ["PK-1"], "a self-test function inside a script is no reader"
        assert rules(good, 'x = policy["rules"]["a_max_kb"]\n\ndef self_test():\n    pass\n\ny = get("b_kb")\n') == [], "code after the self-test still counts"
        assert rules(good, '"a_max_kb"', "get('b_kb')", {"start.b_kb": "x/reader.py"}) == [], "a tool-library reader counts"
        assert rules(good, '"a_max_kb"', "nothing", {"start.b_kb": "x/reader.py"}) == ["PK-2"]
        open(os.path.join(lib, "x", "reader_selftest.py"), "w").write("get('b_kb')")
        assert rules(good, '"a_max_kb"', "x", {"start.b_kb": "x/reader_selftest.py"}) == ["PK-2"], "a tool-library self-test is no reader"
        assert rules(good, '"a_max_kb"', "def self_test():\n    get('b_kb')\n", {"start.b_kb": "x/reader.py"}) == ["PK-2"], "nor is a self-test function there"
        assert rules(good, '"a_max_kb" "b_kb"', "x", {"start.gone": "x/reader.py"}) == ["PK-3"]
        json.dump(good, open(os.path.join(tmp, "policy.json"), "w"))
        open(os.path.join(tmp, "scripts", "check.py"), "w").write('"a_max_kb"')
        assert problems(tmp, os.path.join(tmp, "absent"), {"start.b_kb": "x/reader.py"}) == [], "no library (CI): the entry is trusted"
        open(os.path.join(tmp, "scripts", "check.py"), "w").write("")
        assert "fix: delete the key" in problems(tmp, lib, {})[0], "a failure says how to fix it"
    print("policy_keys_read self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(os.getcwd())
    for p in found:
        print(p)
    print(f"policy_keys_read: {len(found)} problems")
    sys.exit(1 if found else 0)
