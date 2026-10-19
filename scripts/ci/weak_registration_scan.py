#!/usr/bin/env python3
"""Report (never fail unless --enforce): tests that build an object registering itself only by a weak reference and then drop it.

Micrometer's `Gauge.builder(name, obj, fn)` keeps `obj` behind a WeakReference, so once nothing else refers to the object the gauge reads NaN after the next GC; a cache keyed weakly goes the same way.
In production the object is a Spring singleton and lives on; in a test, `new SomeMetrics(registry);` as a bare statement leaves the only reference to the garbage collector, and the test fails now and then (QUAL-019, DeadLetterJobMetricsTest; the same class of failure as SingleAggregateGuardTest).

A main class is "weakly registering" when its source (comments and string contents masked) has
  - `Gauge.builder` / `TimeGauge.builder` / `FunctionCounter.builder` / `FunctionTimer.builder` with an object and a function argument (three or more arguments), unless the chain says `.strongReference(true)`;
  - `.gauge(` with three or more arguments, `.gaugeCollectionSize(`, `.gaugeMapSize(`, or `Row.of(` with three or more arguments (MultiGauge);
  - `WeakReference` / `WeakHashMap` / `.weakKeys(` / `.weakValues(`.
Listener registrations (`addListener(this)` and the like) are not flagged: most event buses hold listeners strongly.
Flagged in a test:
  - a statement that is only `new Name(...)` (a bare statement after `;`, `{`, `}`, `else`, `->`, `:` or an if/for/while header), including `new pkg.Name(...)`, generics and an anonymous body;
  - `Type v = new Name(...)` where `v` is never mentioned again in the enclosing block (the JIT may treat an unused local as dead).
The fix: keep the instance and end the test with `Reference.reachabilityFence(instance)`.
Exit codes: 0 nothing to report (or findings without --enforce); 1 findings with --enforce; 2 the scan could not read its input.
Usage: weak_registration_scan.py [repo-root] [--json] [--enforce] | weak_registration_scan.py --self-test
"""
import json
import os
import re
import contextlib
import io
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MAIN = os.path.join("backend", "src", "main", "java")
TEST = os.path.join("backend", "src", "test", "java")
ARG_CALLS = (
    (re.compile(r"\b(?:Gauge|TimeGauge|FunctionCounter|FunctionTimer)\s*\.\s*builder\s*\("), 3),
    (re.compile(r"\.\s*gauge\s*\("), 3),
    (re.compile(r"\bRow\s*\.\s*of\s*\("), 3),
)
PLAIN_FORMS = (re.compile(r"\.\s*gauge(?:CollectionSize|MapSize)\s*\("), re.compile(r"\b(?:WeakReference|WeakHashMap)\b|\.\s*weak(?:Keys|Values)\s*\("))
CLASS_DECL = re.compile(r"^[ \t]*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|protected|private|final|abstract|static|sealed|non-sealed|strictfp)\s+)*(?:class|record)\s+(\w+)", re.M)
LOCAL_DECL = re.compile(r"(?:^|[\s(,])(?:final\s+)?[A-Za-z_][\w.]*(?:<[^;=()]*>)?(?:\[\])*[ \t]+(\w+)[ \t]*=[ \t]*$")


class ScanError(Exception):
    pass


def mask(text):
    """Comments become spaces and the inside of string / char / text-block literals becomes `x`; newlines and quotes stay, so offsets and line numbers survive."""
    out, i, n = [], 0, len(text)
    while i < n:
        two = text[i:i + 2]
        if two == "//":
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif two == "/*":
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join(c if c == "\n" else " " for c in text[i:j]))
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append('"""' + "".join(c if c == "\n" else "x" for c in text[i + 3:j - 3]) + '"""' if j - i >= 6 else text[i:j])
            i = j
        elif text[i] in "\"'":
            quote, j = text[i], i + 1
            while j < n and text[j] != quote and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append(quote + "x" * max(0, min(j, n) - i - 1) + (quote if j < n and text[j] == quote else ""))
            i = min(j + 1, n) if j < n and text[j] == quote else min(j, n)
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def java_files(base):
    def fail(error):
        raise ScanError(f"{error.filename}: {error.strerror}")
    for dirpath, _, names in os.walk(base, onerror=fail):
        for name in sorted(names):
            if name.endswith(".java"):
                yield os.path.join(dirpath, name)


def read(path):
    try:
        with open(path, encoding="utf-8") as stream:
            return mask(stream.read())
    except (OSError, UnicodeDecodeError) as error:
        raise ScanError(f"{path}: {error}")


def close_of(text, open_index):
    """Index of the bracket closing the one at open_index, or -1."""
    depth = 0
    for i in range(open_index, len(text)):
        if text[i] in "([{":
            depth += 1
        elif text[i] in ")]}":
            depth -= 1
            if depth == 0:
                return i
    return -1


def top_level_args(text, open_index, close_index):
    depth, args, start = 0, 0, open_index + 1
    for i in range(open_index + 1, close_index):
        if text[i] in "([{<" and not (text[i] == "<" and text[i + 1:i + 2] in ("=", " ")):
            depth += 1
        elif text[i] in ")]}>" and not (text[i] == ">" and text[i - 1:i] in ("-", "=")):
            depth -= 1
        elif text[i] == "," and depth == 0:
            args += 1
    return args + 1 if text[start:close_index].strip() else 0


def weakly_registers(text):
    if any(form.search(text) for form in PLAIN_FORMS):
        return True
    for pattern, minimum in ARG_CALLS:
        for match in pattern.finditer(text):
            open_index = match.end() - 1
            close_index = close_of(text, open_index)
            if close_index < 0 or top_level_args(text, open_index, close_index) < minimum:
                continue
            chain_end = text.find(";", close_index)
            if "strongReference(true)" in text[close_index:chain_end if chain_end >= 0 else len(text)]:
                continue
            return True
    return False


def weak_classes(root):
    found = {}
    base = os.path.join(root, MAIN)
    if not os.path.isdir(base):
        raise ScanError(f"{base}: no main sources")
    for path in java_files(base):
        text = read(path)
        name = os.path.splitext(os.path.basename(path))[0]
        if any(match.group(1) == name for match in CLASS_DECL.finditer(text)) and weakly_registers(text):
            found[name] = os.path.relpath(path, root)
    return found


def statement_end(text, start):
    """Index just past the `;` closing the statement whose `new Name(` starts at `start`, allowing an anonymous class body; -1 when it never closes."""
    open_index = text.index("(", start)
    close_index = close_of(text, open_index)
    if close_index < 0:
        return -1
    rest = text[close_index + 1:]
    stripped = rest.lstrip()
    position = close_index + 1 + (len(rest) - len(stripped))
    if stripped.startswith("{"):
        body_end = close_of(text, position)
        if body_end < 0:
            return -1
        stripped = text[body_end + 1:].lstrip()
        position = len(text) - len(stripped)
    return position + 1 if stripped.startswith(";") else -1


def statement_starts_here(text, index):
    before = text[:index].rstrip()
    if not before or before[-1] in ";{}:" or before.endswith("->"):
        return True
    if re.search(r"(?<![\w$])else$", before):
        return True
    if before.endswith(")"):
        depth, i = 0, len(before) - 1
        while i >= 0:
            depth += before[i] == ")"
            depth -= before[i] == "("
            if depth == 0:
                break
            i -= 1
        return i > 0 and re.search(r"(?<![\w$])(?:if|for|while)\s*$", before[:i]) is not None
    return False


def enclosing_block(text, index):
    """(open, close) of the innermost block around index when it is a method, constructor, lambda or statement block (not a class body), else None."""
    depth, i = 0, index - 1
    while i >= 0:
        if text[i] == "}":
            depth += 1
        elif text[i] == "{":
            if depth == 0:
                before = text[:i].rstrip()
                if before.endswith(")") or before.endswith("->") or re.search(r"(?<![\w$])(?:else|try|finally|do)$", before):
                    return i, close_of(text, i)
                return None
            depth -= 1
        i -= 1
    return None


def scan(root):
    classes = weak_classes(root)
    findings = []
    if not classes:
        return findings, classes
    names = "|".join(sorted(map(re.escape, classes)))
    pattern = re.compile(r"\bnew\s+(?:[\w$]+\s*\.\s*)*(" + names + r")\s*(?:<[^;(){}]*>)?\s*\(")
    for path in java_files(os.path.join(root, TEST)):
        text = read(path)
        for match in pattern.finditer(text):
            end = statement_end(text, match.start())
            if end < 0:
                continue
            kind = None
            if statement_starts_here(text, match.start()):
                kind = "discarded"
            else:
                declaration = LOCAL_DECL.search(text[max(0, match.start() - 160):match.start()].rsplit(";", 1)[-1].rsplit("{", 1)[-1].rsplit("}", 1)[-1].lstrip("\n"))
                if declaration and text[:match.start()].rstrip().endswith("=") and not text[:match.start()].rstrip().endswith("=="):
                    block = enclosing_block(text, match.start())
                    if block and block[1] > match.start() and not re.search(r"(?<![\w$])" + re.escape(declaration.group(1)) + r"(?![\w$])", text[end:block[1]]):
                        kind = "unused-local"
            if kind:
                findings.append({"file": os.path.relpath(path, root), "line": text.count("\n", 0, match.start()) + 1, "class": match.group(1), "main": classes[match.group(1)], "kind": kind})
    return findings, classes


def report(findings, classes, as_json):
    if as_json:
        print(json.dumps({"weak_classes": sorted(classes), "findings": findings}, ensure_ascii=False, indent=2))
        return
    why = {"discarded": "实例未保存", "unused-local": "实例只赋给之后再没用到的局部变量"}
    for item in findings:
        print(f"{item['file']}:{item['line']}: new {item['class']}(...) {why[item['kind']]}；该类 ({item['main']}) 只以弱引用注册，GC 后读出 NaN/失效。保存实例并在末尾 Reference.reachabilityFence(实例)")
    print(f"weak-registration scan: {len(findings)} finding(s) in {len({f['file'] for f in findings})} file(s); {len(classes)} weakly registering main class(es)")


def write(root, relative, text):
    path = os.path.join(root, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as stream:
        stream.write(text)


def fixtures(root):
    sources = {
        "SyncMetrics": "class SyncMetrics {\n    SyncMetrics(MeterRegistry r) { Gauge.builder(\"x\", this, SyncMetrics::lag).register(r); }\n}\n",
        "CallName": "class CallName {\n    CallName(MeterRegistry r) { Gauge.builder(names.of(\"x\"), this, CallName::v).register(r); }\n}\n",
        "Timer": "record Timer(int a) {\n    Timer { FunctionTimer.builder(\"t\", this, Timer::n, Timer::total, SECONDS).register(r); }\n}\n",
        "Direct": "class Direct {\n    Direct(MeterRegistry r) { r.gauge(\"x\", Tags.empty(), this, Direct::v); }\n}\n",
        "Sizes": "class Sizes {\n    Sizes(MeterRegistry r) { r.gaugeCollectionSize(\"x\", Tags.empty(), items); }\n}\n",
        "Cache": "class Cache {\n    private final Map<Key, V> map = new WeakHashMap<>();\n}\n",
        "Multi": "class Multi {\n    void t(MultiGauge g) { g.register(List.of(Row.of(Tags.empty(), this, Multi::v))); }\n}\n",
        "Strongly": "class Strongly {\n    Strongly(MeterRegistry r) { Gauge.builder(\"x\", this, Strongly::v).strongReference(true).register(r); }\n}\n",
        "Safe": "class Safe {\n    Safe(MeterRegistry r) { Gauge.builder(\"x\", () -> 1.0).register(r); }\n}\n",
        "Hub": "class Hub {\n    Hub(Bus bus) { bus.addListener(this); }\n}\n",
        "Plain": "class Plain {\n    Plain() { }\n}\n",
        "Quiet": "class Quiet {\n    // Gauge.builder(\"x\", this, Quiet::v)\n    /* new WeakHashMap<>() */\n    String s = \"Gauge.builder(a, this, b)\";\n}\n",
    }
    for name, source in sources.items():
        write(root, f"{MAIN}/p/{name}.java", source)
    return set(sources)


def self_test():
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        run_self_test()
    print("weak_registration_scan self-test: ok")
    return 0


def run_self_test():
    cases = {
        "bare statement": ("void t() {\n        new SyncMetrics(registry);\n    }", 1),
        "multi-line bare statement": ("void t() {\n        new SyncMetrics(\n            registry);\n    }", 1),
        "after a block": ("void t() {\n        if (a) { b(); }\n        new SyncMetrics(registry);\n    }", 1),
        "name call with parentheses in the first argument": ("void t() {\n        new CallName(registry);\n    }", 1),
        "record with FunctionTimer": ("void t() {\n        new Timer(registry);\n    }", 1),
        "registry.gauge with tags": ("void t() {\n        new Direct(registry);\n    }", 1),
        "gaugeCollectionSize": ("void t() {\n        new Sizes(registry);\n    }", 1),
        "weak map class": ("void t() {\n        new Cache();\n    }", 1),
        "multigauge row": ("void t() {\n        new Multi();\n    }", 1),
        "fully qualified": ("void t() {\n        new p.SyncMetrics(registry);\n    }", 1),
        "generic": ("void t() {\n        new SyncMetrics<Map<String, List<Long>>>(registry);\n    }", 1),
        "anonymous body": ("void t() {\n        new SyncMetrics(registry) { };\n    }", 1),
        "if without braces": ("void t() {\n        if (a) new SyncMetrics(registry);\n    }", 1),
        "else without braces": ("void t() {\n        if (a) { b(); } else new SyncMetrics(registry);\n    }", 1),
        "switch arrow": ("void t() {\n        switch (k) { case 1 -> new SyncMetrics(registry); default -> { } }\n    }", 1),
        "lambda block": ("void t() {\n        run(() -> { new SyncMetrics(registry); });\n    }", 1),
        "paren in argument string": ("void t() {\n        new SyncMetrics(\")\");\n    }", 1),
        "url in argument string": ("void t() {\n        new SyncMetrics(\"http://x/y\");\n    }", 1),
        "glob before it": ("void t() {\n        String s = \"/api/**\";\n        new SyncMetrics(registry);\n    }", 1),
        "text block argument": ("void t() {\n        new SyncMetrics(\"\"\"\n            a)\n            \"\"\");\n    }", 1),
        "unused local": ("void t() {\n        SyncMetrics m = new SyncMetrics(registry);\n        assertThat(1).isOne();\n    }", 1),
        "unused var": ("void t() {\n        var m = new SyncMetrics(registry);\n    }", 1),
        "used local": ("void t() {\n        SyncMetrics m = new SyncMetrics(registry);\n        m.lag();\n    }", 0),
        "local kept alive by a fence": ("void t() {\n        SyncMetrics m = new SyncMetrics(registry);\n        Reference.reachabilityFence(m);\n    }", 0),
        "same name only in another method": ("void a() {\n        SyncMetrics m = new SyncMetrics(registry);\n    }\n    void b() {\n        int m = 1;\n    }", 1),
        "passed on": ("void t() {\n        keep(new SyncMetrics(registry));\n    }", 0),
        "returned": ("SyncMetrics t() {\n        return new SyncMetrics(registry);\n    }", 0),
        "field": ("final SyncMetrics m = new SyncMetrics(registry);", 0),
        "assigned field": ("void t() {\n        this.m = new SyncMetrics(registry);\n    }", 0),
        "chained": ("void t() {\n        new SyncMetrics(registry).lag();\n    }", 0),
        "cast": ("void t() {\n        Object o = (Object) new SyncMetrics(registry);\n        use(o);\n    }", 0),
        "supplier gauge": ("void t() {\n        new Safe(registry);\n    }", 0),
        "strong reference gauge": ("void t() {\n        new Strongly(registry);\n    }", 0),
        "listener only": ("void t() {\n        new Hub(bus);\n    }", 0),
        "plain class": ("void t() {\n        new Plain();\n    }", 0),
        "masked class": ("void t() {\n        new Quiet();\n    }", 0),
        "inside a string": ("void t() {\n        String s = \"new SyncMetrics(registry);\";\n    }", 0),
        "inside a comment": ("void t() {\n        // new SyncMetrics(registry);\n    }", 0),
        "escaped quote and paren in the argument": ("void t() {\n        new SyncMetrics(\"a\\\")\");\n    }", 1),
        "unbalanced": ("void t() {\n        new SyncMetrics(registry\n    }", 0),
    }
    for label, (body, expected) in cases.items():
        with tempfile.TemporaryDirectory() as root:
            fixtures(root)
            write(root, f"{TEST}/p/T.java", "class T {\n    " + body + "\n}\n")
            findings, _ = scan(root)
            assert len(findings) == expected, (label, findings)
    with tempfile.TemporaryDirectory() as root:
        fixtures(root)
        assert set(weak_classes(root)) == {"SyncMetrics", "CallName", "Timer", "Direct", "Sizes", "Cache", "Multi"}, weak_classes(root)
        write(root, f"{MAIN}/p/Annotated.java", "@Component public class Annotated {\n    Annotated(MeterRegistry r) { Gauge.builder(\"x\", this, Annotated::v).register(r); }\n}\n")
        write(root, f"{MAIN}/p/Sealed.java", "public sealed class Sealed {\n    Sealed(MeterRegistry r) { Gauge.builder(\"x\", this, Sealed::v).register(r); }\n}\n")
        assert {"Annotated", "Sealed"} <= set(weak_classes(root)), weak_classes(root)
        write(root, f"{MAIN}/p/Notes.java", "class Other {\n    Other(MeterRegistry r) { Gauge.builder(\"x\", this, Other::v).register(r); }\n}\n")
        assert "Notes" not in weak_classes(root) and "Other" not in weak_classes(root), "a class must live in the file named after it"
        write(root, f"{TEST}/p/T.java", "class T {\n    void t() {\n        new SyncMetrics(r);\n    }\n}\n")
        findings, _ = scan(root)
        assert findings == [{"file": os.path.join(TEST, "p", "T.java"), "line": 3, "class": "SyncMetrics", "main": os.path.join(MAIN, "p", "SyncMetrics.java"), "kind": "discarded"}], findings
        assert main([root, "--enforce"]) == 1 and main([root]) == 0, "findings fail only with --enforce"
        write(root, f"{TEST}/p/T.java", "class T {\n    void t() {\n        keep(new SyncMetrics(r));\n    }\n}\n")
        assert main([root, "--enforce"]) == 0, "nothing to report passes with --enforce"
        write(root, f"{TEST}/p/Bad.java", "class Bad {\n}\n")
        with open(os.path.join(root, TEST, "p", "Bad.java"), "wb") as stream:
            stream.write(b"\xff\xfe\x00 not utf-8")
        assert main([root]) == 2 and main([root, "--enforce"]) == 2, "an unreadable file is an error, not a pass"
    with tempfile.TemporaryDirectory() as root:
        assert main([root]) == 2, "no test sources"
        os.makedirs(os.path.join(root, TEST))
        assert main([root]) == 2, "no main sources"
    assert main(["--bogus"]) == 2 and main(["--self-test", "--json"]) == 2


def main(argv):
    if argv == ["--self-test"]:
        return self_test()
    flags = {arg for arg in argv if arg.startswith("--")}
    positional = [arg for arg in argv if not arg.startswith("--")]
    if flags - {"--json", "--enforce"} or len(positional) > 1:
        print(__doc__, file=sys.stderr)
        return 2
    root = positional[0] if positional else ROOT
    try:
        if not os.path.isdir(os.path.join(root, TEST)):
            raise ScanError(f"{os.path.join(root, TEST)}: no test sources")
        findings, classes = scan(root)
    except ScanError as error:
        print(f"weak-registration scan: {error}", file=sys.stderr)
        return 2
    report(findings, classes, "--json" in flags)
    return 1 if findings and "--enforce" in flags else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
