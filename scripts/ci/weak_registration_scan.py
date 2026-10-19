#!/usr/bin/env python3
"""Report (never fail unless --enforce): tests that build an object registering itself only by a weak reference and then drop it.

Micrometer's `Gauge.builder(name, obj, fn)` keeps `obj` behind a WeakReference, so once nothing else refers to the object the gauge reads NaN after the next GC; a listener or cache held weakly goes the same way.
In production the object is a Spring singleton and lives on; in a test, `new SomeMetrics(registry);` as a bare statement leaves the only reference to the garbage collector, and the test fails now and then (QUAL-019, DeadLetterJobMetricsTest; the same class of failure as SingleAggregateGuardTest).

A main class is "weakly registering" when its source has `Gauge.builder(` with an object argument, `WeakReference` / `WeakHashMap` / `weakKeys()` / `weakValues()`, or hands `this` to `addListener(` / `register(` / `subscribe(`.
Flagged: a test statement that is only `new Name(...)` (preceded by `;`, `{` or `}`, so not assigned, passed or returned) for such a class. The fix: keep the instance in a local or a field and end the test with `Reference.reachabilityFence(instance)`.
Usage: weak_registration_scan.py [repo-root] [--json] [--enforce] | weak_registration_scan.py --self-test
"""
import json
import os
import re
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MAIN = os.path.join("backend", "src", "main", "java")
TEST = os.path.join("backend", "src", "test", "java")
WEAK_FORMS = (
    re.compile(r"Gauge\s*\.\s*builder\s*\(\s*[^,()]+,\s*[^,()]+,"),
    re.compile(r"\b(?:WeakReference|WeakHashMap)\b|\.weak(?:Keys|Values)\s*\("),
    re.compile(r"\.(?:addListener|register|subscribe)\s*\(\s*this\b"),
)
CLASS_DECL = re.compile(r"^\s*(?:public\s+|protected\s+|private\s+|final\s+|abstract\s+|static\s+)*(?:class|record)\s+(\w+)", re.M)


def strip_comments(text):
    return re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), text, flags=re.S))


def java_files(base):
    for dirpath, _, names in os.walk(base):
        for name in sorted(names):
            if name.endswith(".java"):
                yield os.path.join(dirpath, name)


def weak_classes(root):
    found = {}
    for path in java_files(os.path.join(root, MAIN)):
        text = strip_comments(open(path, encoding="utf-8").read())
        if any(form.search(text) for form in WEAK_FORMS):
            name = os.path.splitext(os.path.basename(path))[0]
            if any(match.group(1) == name for match in CLASS_DECL.finditer(text)):
                found[name] = os.path.relpath(path, root)
    return found


def statement_end(text, start):
    """Index of the `;` closing the statement whose `new Name(` starts at `start`, or -1 when the parentheses never balance."""
    depth, quote, i = 0, False, text.index("(", start)
    while i < len(text):
        ch = text[i]
        if quote:
            if ch == "\\":
                i += 1
            elif ch == '"':
                quote = False
        elif ch == '"':
            quote = True
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
            if depth == 0:
                rest = text[i + 1:].lstrip()
                return len(text) - len(rest) if rest.startswith(";") else -1
        i += 1
    return -1


def scan(root):
    classes = weak_classes(root)
    findings = []
    if not classes:
        return findings, classes
    pattern = re.compile(r"\bnew\s+(" + "|".join(sorted(map(re.escape, classes))) + r")\s*(?:<[^>()]*>)?\s*\(")
    for path in java_files(os.path.join(root, TEST)):
        text = strip_comments(open(path, encoding="utf-8").read())
        for match in pattern.finditer(text):
            before = text[:match.start()].rstrip()
            if before and before[-1] not in ";{}":
                continue
            if statement_end(text, match.start()) < 0:
                continue
            findings.append({"file": os.path.relpath(path, root), "line": text.count("\n", 0, match.start()) + 1, "class": match.group(1), "main": classes[match.group(1)]})
    return findings, classes


def report(findings, classes, as_json):
    if as_json:
        print(json.dumps({"weak_classes": sorted(classes), "findings": findings}, ensure_ascii=False, indent=2))
        return
    for item in findings:
        print(f"{item['file']}:{item['line']}: new {item['class']}(...) 的实例未保存；该类 ({item['main']}) 只以弱引用注册，GC 后读出 NaN/失效。保存实例并在末尾 Reference.reachabilityFence(实例)")
    print(f"weak-registration scan: {len(findings)} finding(s) in {len({f['file'] for f in findings})} file(s); {len(classes)} weakly registering main class(es)")


def write(root, relative, text):
    path = os.path.join(root, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as stream:
        stream.write(text)


def self_test():
    gauge = "class SyncMetrics {\n    SyncMetrics(MeterRegistry r) { Gauge.builder(\"x\", this, SyncMetrics::lag).register(r); }\n}\n"
    listener = "class Hub {\n    Hub(Bus bus) { bus.addListener(this); }\n}\n"
    strong = "class Safe {\n    Safe(MeterRegistry r) { Gauge.builder(\"x\", () -> 1.0).register(r); }\n}\n"
    plain = "class Plain {\n    Plain() { }\n}\n"
    commented = "class Quiet {\n    // Gauge.builder(\"x\", this, Quiet::v)\n    /* bus.addListener(this); */\n}\n"
    cases = {
        "bare statement": ("void t() {\n        new SyncMetrics(registry);\n    }", 1),
        "multi-line bare statement": ("void t() {\n        new SyncMetrics(\n            registry);\n    }", 1),
        "after a block": ("void t() {\n        if (a) { b(); }\n        new SyncMetrics(registry);\n    }", 1),
        "listener class": ("void t() {\n        new Hub(bus);\n    }", 1),
        "generic-looking": ("void t() {\n        new Hub (bus);\n    }", 1),
        "assigned": ("void t() {\n        SyncMetrics m = new SyncMetrics(registry);\n    }", 0),
        "var assigned": ("void t() {\n        var m = new SyncMetrics(registry);\n    }", 0),
        "passed on": ("void t() {\n        keep(new SyncMetrics(registry));\n    }", 0),
        "returned": ("SyncMetrics t() {\n        return new SyncMetrics(registry);\n    }", 0),
        "field": ("final SyncMetrics m = new SyncMetrics(registry);", 0),
        "chained": ("void t() {\n        new SyncMetrics(registry).lag();\n    }", 0),
        "supplier gauge (strongly held)": ("void t() {\n        new Safe(registry);\n    }", 0),
        "plain class": ("void t() {\n        new Plain();\n    }", 0),
        "commented class": ("void t() {\n        new Quiet();\n    }", 0),
        "inside a string": ("void t() {\n        String s = \"new SyncMetrics(registry);\";\n    }", 0),
        "inside a comment": ("void t() {\n        // new SyncMetrics(registry);\n    }", 0),
        "unbalanced": ("void t() {\n        new SyncMetrics(registry\n    }", 0),
    }
    for label, (body, expected) in cases.items():
        with tempfile.TemporaryDirectory() as root:
            for name, source in (("SyncMetrics", gauge), ("Hub", listener), ("Safe", strong), ("Plain", plain), ("Quiet", commented)):
                write(root, f"{MAIN}/p/{name}.java", source)
            write(root, f"{TEST}/p/T.java", "class T {\n    " + body + "\n}\n")
            findings, _ = scan(root)
            assert len(findings) == expected, (label, findings)
    with tempfile.TemporaryDirectory() as root:
        findings, classes = scan(root)
        assert findings == [] and classes == {}, "an empty tree reports nothing"
        write(root, f"{MAIN}/p/SyncMetrics.java", gauge)
        write(root, f"{TEST}/p/T.java", "class T {\n    void t() {\n        new SyncMetrics(r);\n    }\n}\n")
        findings, classes = scan(root)
        assert findings == [{"file": os.path.join(TEST, "p", "T.java"), "line": 3, "class": "SyncMetrics", "main": os.path.join(MAIN, "p", "SyncMetrics.java")}], findings
    print("weak_registration_scan self-test: ok")
    return 0


def main(argv):
    if argv == ["--self-test"]:
        return self_test()
    flags = {arg for arg in argv if arg.startswith("--")}
    positional = [arg for arg in argv if not arg.startswith("--")]
    if flags - {"--json", "--enforce"} or len(positional) > 1:
        print(__doc__, file=sys.stderr)
        return 2
    root = positional[0] if positional else ROOT
    if not os.path.isdir(os.path.join(root, TEST)):
        print(f"weak-registration scan: no test sources under {root}", file=sys.stderr)
        return 2
    findings, classes = scan(root)
    report(findings, classes, "--json" in flags)
    return 1 if findings and "--enforce" in flags else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
