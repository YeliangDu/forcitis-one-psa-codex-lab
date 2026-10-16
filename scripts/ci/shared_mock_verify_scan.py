#!/usr/bin/env python3
"""Report, never fail: list the exact-count verifications on mocks of a shared Spring context that name no argument of their own.

A `@MockitoBean` / `@MockBean` (and the spy forms) of a Spring test is shared by everything that runs in that cached context, and other modules answer earlier events asynchronously: they call the mock too.
`verify(audit).record(entry.capture())` (once, any argument) then counts calls it did not cause, and the test fails now and then (#341, OrgSnapshotSyncIntegrationTest).
A count is safe when the call names something only this test produces: `argThat(...)` on an action or id, `eq(...)`, or a concrete value. The fix in CS-5.6 terms: capture with
`atLeastOnce()` and filter the captured values by action / id, or verify with an `argThat` that picks this test's call.

Flagged: `verify(m)`, `verify(m, times(n))`, `verify(m, only())`, `verify(m, timeout(..))`, `verify(m, never())` (and the same behind `Mockito.`) where every argument is absent, `any*()`, `isNull()`, or `<captor>.capture()`,
in a test class that runs in a Spring context (own @SpringBootTest, or an ancestor's) and whose mock field is declared with @MockitoBean / @MockBean in the class or an ancestor.
`verifyNoInteractions` / `verifyNoMoreInteractions` on such a mock are flagged too.
Usage: shared_mock_verify_scan.py [test-source-root] [--json] | shared_mock_verify_scan.py --self-test
"""
import json
import os
import re
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "backend", "src", "test", "java"))
SPRING = re.compile(r"@SpringBootTest|@ContextConfiguration|@WebMvcTest|@DataJdbcTest")
MOCK_FIELD = re.compile(r"@(?:MockitoBean|MockBean|MockitoSpyBean|SpyBean)\b[^;{]*?\b(?:protected\s+|public\s+|private\s+)?(?:final\s+)?([\w.<>]+)\s+(\w+)\s*;", re.S)
BROAD = re.compile(r"^(?:org\.mockito\.ArgumentMatchers\.|ArgumentMatchers\.|Mockito\.)?(?:any\w*\(\s*(?:[\w.]+\.class)?\s*\)|isNull\(\)|isA\([\w.]+\.class\)|\w+\.capture\(\))$")


def strip_comments(text):
    return re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", text, flags=re.S))


def split_args(text):
    out, depth, cur, quote = [], 0, "", False
    for ch in text:
        if ch == '"':
            quote = not quote
        if not quote and ch in "([{":
            depth += 1
        if not quote and ch in ")]}":
            depth -= 1
        if not quote and depth == 0 and ch == ",":
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def call_after(text, start):
    """The argument text of the call whose `(` is at text[start], and the index after its `)`."""
    depth, quote, i = 0, False, start
    while i < len(text):
        ch = text[i]
        if ch == '"':
            quote = not quote
        elif not quote and ch == "(":
            depth += 1
        elif not quote and ch == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:i], i + 1
        i += 1
    return None, len(text)


def load(root):
    classes = {}
    for folder, _, files in os.walk(root):
        for name in files:
            if name.endswith(".java"):
                path = os.path.join(folder, name)
                classes[name[:-5]] = (path, strip_comments(open(path, encoding="utf-8").read()))
    return classes


def chain(classes, name, seen=()):
    """The class and its ancestors (by simple name) that are among the sources."""
    out = [name]
    m = re.search(rf"\bclass\s+{re.escape(name)}(?:<[^>]*>)?\s+extends\s+(\w+)", classes[name][1]) if name in classes else None
    if m and m.group(1) in classes and m.group(1) not in seen:
        out += chain(classes, m.group(1), seen + (name,))
    return out


def scan(root):
    classes = load(root)
    findings = []
    for name, (path, text) in classes.items():
        family = chain(classes, name)
        if not any(SPRING.search(classes[c][1]) for c in family):
            continue
        mocks = {}
        for c in family:
            for m in MOCK_FIELD.finditer(classes[c][1]):
                mocks[m.group(2)] = m.group(1)
        if not mocks:
            continue
        for m in re.finditer(r"(?:\bMockito\.)?\bverify(NoInteractions|NoMoreInteractions)?\s*\(", text):
            inner, end = call_after(text, m.end() - 1)
            if inner is None:
                continue
            parts = split_args(inner)
            target = parts[0] if parts else ""
            if target not in mocks:
                continue
            line = text.count("\n", 0, m.start()) + 1
            if m.group(1):
                findings.append({"class": name, "line": line, "mock": target, "type": mocks[target], "kind": "no-interactions", "call": m.group(0).strip("( ")})
                continue
            mode = parts[1] if len(parts) > 1 else ""
            if re.search(r"atLeast|atMost|calls\(", mode):
                continue
            kind = "never" if re.search(r"never\(\)|times\(\s*0\s*\)", mode) else "exact"
            tail = re.match(r"\s*\.\s*(\w+)\s*\(", text[end:])
            if not tail:
                continue
            call_args, _ = call_after(text, end + tail.end() - 1)
            args = split_args(call_args or "")
            if all(BROAD.match(a) for a in args):
                findings.append({"class": name, "line": line, "mock": target, "type": mocks[target], "kind": kind, "call": f"{target}.{tail.group(1)}({', '.join(args)})", "mode": mode or "default times(1)"})
    return sorted(findings, key=lambda f: (f["class"], f["line"]))


def self_test():
    sources = {
        "BaseSupport": "@SpringBootTest abstract class BaseSupport { @MockitoBean protected AuditLog audit; @MockitoBean WecomGateway wecom; }",
        "FlakyTest": "class FlakyTest extends BaseSupport { void a() { verify(audit).record(entry.capture()); verify(wecom, never()).send(any()); verify(wecom, timeout(1000)).send(any(Card.class)); Mockito.verify(audit, times(1)).record(anyString()); verifyNoMoreInteractions(audit); verify(audit, times(0)).record(isNull()); } }",
        "SafeTest": "class SafeTest extends BaseSupport { void a() { verify(audit).record(argThat(e -> e.action().equals(x))); verify(wecom, never()).send(argThat(c -> c.id().equals(id))); verify(audit, atLeastOnce()).record(captor.capture()); verify(wecom).markHandled(eq(key), note); verify(wecom).resolve(\"c1\", ENTRY); verify(audit, atMost(2)).record(any()); } }",
        "PlainUnitTest": "class PlainUnitTest { @Mock AuditLog audit; void a() { verify(audit).record(any()); } }",
        "NotAMockTest": "@SpringBootTest class NotAMockTest { Helper h; void a() { verify(h).run(); } }",
        "NoArgTest": "@SpringBootTest class NoArgTest { @MockitoBean Scheduler jobs; void a() { verify(jobs).runDue(); verify(jobs, never()).stop(); } }",
        "CommentedTest": "@SpringBootTest class CommentedTest { @MockitoBean AuditLog audit; // verify(audit).record(any());\n void a() { /* verify(audit).record(any()); */ } }",
    }
    with tempfile.TemporaryDirectory() as root:
        for name, text in sources.items():
            open(os.path.join(root, name + ".java"), "w", encoding="utf-8").write(text)
        found = scan(root)
    got = {(f["class"], f["kind"], f["call"].split("(")[0]) for f in found}
    assert ("FlakyTest", "exact", "audit.record") in got, found
    assert ("FlakyTest", "never", "wecom.send") in got and ("FlakyTest", "exact", "wecom.send") in got, found
    assert ("FlakyTest", "no-interactions", "verifyNoMoreInteractions") in got and ("FlakyTest", "never", "audit.record") in got, found
    assert ("NoArgTest", "exact", "jobs.runDue") in got and ("NoArgTest", "never", "jobs.stop") in got, "a call with no argument cannot be told apart from another module's call"
    assert not [f for f in found if f["class"] in ("SafeTest", "PlainUnitTest", "NotAMockTest", "CommentedTest")], [f for f in found if f["class"] in ("SafeTest", "PlainUnitTest", "NotAMockTest", "CommentedTest")]
    print("shared_mock_verify_scan self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    findings = scan(args[0] if args else ROOT)
    if "--json" in sys.argv:
        print(json.dumps(findings, ensure_ascii=False, indent=1))
    else:
        for f in findings:
            print(f"{f['class']}:{f['line']}\t{f['kind']}\t{f['type']}\t{f['call']}\t{f.get('mode', '')}")
        print(f"shared-mock verify scan: {len(findings)} finding(s) in {len({f['class'] for f in findings})} class(es) (report only)")
