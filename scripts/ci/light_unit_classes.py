#!/usr/bin/env python3
"""Print the unit test classes that start no container and no Spring context, comma separated, for `surefire:test -Dtest=` (scripts/precheck.sh --pre-push).

A class is light when its name ends in `Test`, it is not abstract, and neither its own source nor any class it extends (followed through the test sources) mentions a
marker of a heavy test: @SpringBootTest, Testcontainers, a call that starts a database (TestPostgres.container / shared / database, any TestMssql call), a container class, or an *Support / *IntegrationTest base. `*IT` classes belong to failsafe and are never listed.
The list is generated from the sources on purpose: a hand-kept list or a path-to-test map would leave out the next scan test (SeeyonSourceScanTest ran in CI only, three times).

Usage: light_unit_classes.py [test-source-root] | light_unit_classes.py --reading <text> [--heavy] [test-source-root] | light_unit_classes.py --self-test
--reading keeps the classes whose source (comments removed, strings kept) mentions the text, e.g. `architecture/` for the guards that read backend/architecture; with --heavy it lists the classes that mention it but start a container (CI only).
"""
import os
import re
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "backend", "src", "test", "java"))
HEAVY = re.compile(r"@SpringBootTest|@Testcontainers|@DataJdbcTest|@WebMvcTest|@AutoConfigureMockMvc|TestPostgres\.(?:container|shared|database)\s*\(|TestMssql\.\w+\s*\(|PostgreSQLContainer|MSSQLServerContainer|GenericContainer|Testcontainers|ApplicationModuleTest")


def strip_comments(text):
    """Comments and string literals out: a guard that only scans sources names PostgreSQLContainer in a string and starts nothing."""
    text = re.sub(r'""".*?"""', '""', text, flags=re.S)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def read_sources(root):
    """{simple class name: [source text of every file with that name]}; the same simple name can exist in several packages."""
    sources = {}
    for folder, _, files in os.walk(root):
        for name in files:
            if name.endswith(".java"):
                sources.setdefault(name[:-5], []).append(strip_comments(open(os.path.join(folder, name), encoding="utf-8").read()))
    return sources


def mentions(root, needle):
    """Simple names of the test classes whose source, comments removed and strings kept, contains the text."""
    found = set()
    for folder, _, files in os.walk(root):
        for name in files:
            if name.endswith(".java"):
                text = open(os.path.join(folder, name), encoding="utf-8").read()
                if needle in re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", text, flags=re.S)):
                    found.add(name[:-5])
    return found


def light_classes(sources):
    def heavy(name, seen=()):
        texts = sources.get(name)
        if texts is None:
            return bool(re.search(r"Support$|IntegrationTest$|IT$", name))
        for text in texts:
            if HEAVY.search(text) or re.search(r"\bclass\s+\w+(?:<[^>]*>)?\s+extends\s+\w*(?:Support|IntegrationTest)\b", text):
                return True
            m = re.search(rf"\bclass\s+{re.escape(name)}(?:<[^>]*>)?\s+extends\s+(\w+)", text)
            if m and m.group(1) not in seen and heavy(m.group(1), seen + (name,)):
                return True
        return False

    out = []
    for name, texts in sources.items():
        if not name.endswith("Test") or heavy(name):
            continue
        # a file that declares no concrete class of its own name (an interface, an enum, an abstract base) is not a test class
        if any(re.search(rf"\babstract\s+class\s+{re.escape(name)}\b", t) or not re.search(rf"\bclass\s+{re.escape(name)}\b", t) for t in texts):
            continue
        out.append(name)
    return sorted(out)


def self_test():
    with tempfile.TemporaryDirectory() as root:
        files = {
            "PlainTest": "class PlainTest { @Test void a() {} }",
            "ScanTest": "class ScanTest { Path p = Path.of(\"src/main\"); }",
            "SpringTest": "@SpringBootTest class SpringTest {}",
            "PgTest": "class PgTest { PostgreSQLContainer c = TestPostgres.container(); }",
            "BaseHeavy": "abstract class BaseHeavy { static final Object X = TestPostgres.container(); }",
            "ChildTest": "class ChildTest extends BaseHeavy {}",
            "GrandChildTest": "class GrandChildTest extends ChildTest {}",
            "NeedsSupportTest": "class NeedsSupportTest extends WorkforceIntegrationSupport {}",
            "BaseLight": "abstract class BaseLight { }",
            "LightChildTest": "class LightChildTest extends BaseLight {}",
            "AbstractishTest": "abstract class AbstractishTest {}",
            "SomethingIT": "class SomethingIT {}",
            "MssqlTest": "class MssqlTest { Object c = TestMssql.shared(); }",
            "NamesTest": "class NamesTest { String n = TestPostgres.databaseName(\"m\", \"a.B\"); }",
            "StartsTest": "class StartsTest { Object d = TestPostgres.database(StartsTest.class, \"m\"); }",
        }
        files["CommentTest"] = "/** a note about class Foo */ class CommentTest { }"
        files["EnumTest"] = "enum EnumTest { A }"
        files["StringMentionTest"] = 'class StringMentionTest { String x = "new PostgreSQLContainer( and TestPostgres.container()"; }'
        for name, text in files.items():
            open(os.path.join(root, name + ".java"), "w", encoding="utf-8").write(text)
        os.makedirs(os.path.join(root, "a")); os.makedirs(os.path.join(root, "b"))
        open(os.path.join(root, "a", "TwinTest.java"), "w", encoding="utf-8").write("package a; class TwinTest { }")
        open(os.path.join(root, "b", "TwinTest.java"), "w", encoding="utf-8").write("package b; @SpringBootTest class TwinTest { }")
        open(os.path.join(root, "a", "OnlyLightTwinTest.java"), "w", encoding="utf-8").write("package a; class OnlyLightTwinTest { }")
        got = light_classes(read_sources(root))
    assert got == ["CommentTest", "LightChildTest", "NamesTest", "OnlyLightTwinTest", "PlainTest", "ScanTest", "StringMentionTest"], got
    real = light_classes(read_sources(ROOT)) if os.path.isdir(ROOT) else []
    assert not os.path.isdir(ROOT) or ("SeeyonSourceScanTest" in real and "ArchitectureRulesTest" in real and "TestContainerGuardTest" in real and "ApplicationIntegrationTest" not in real and "ListPagingGuardTest" not in real), "the real test sources: scan and guard tests are light, container tests are not"
    with tempfile.TemporaryDirectory() as root:
        for name, text in {"ReadsLightTest": 'class ReadsLightTest { Path p = Path.of("architecture/x.txt"); }', "ReadsHeavyTest": '@SpringBootTest class ReadsHeavyTest { String s = "architecture/y.txt"; }',
                           "OtherTest": "class OtherTest { }", "CommentOnlyTest": "class CommentOnlyTest { /* architecture/z.txt */ }", "ReadsHelper": 'class ReadsHelper { String s = "architecture/h.txt"; }'}.items():
            open(os.path.join(root, name + ".java"), "w", encoding="utf-8").write(text)
        light = set(light_classes(read_sources(root)))
        read = mentions(root, "architecture/")
    assert sorted(light & read) == ["ReadsLightTest"], "--reading: light classes that mention the text, not comments, not helpers without Test"
    assert sorted(n for n in read - light if n.endswith("Test")) == ["ReadsHeavyTest"], "--heavy: container classes that mention the text"
    real_root = ROOT
    if os.path.isdir(real_root):
        real = set(light_classes(read_sources(real_root))) & mentions(real_root, "architecture/")
        assert {"PrimaryKeyTypeGuardTest", "RepositoryWriteGuardTest", "SystemActorCatalogueTest"} <= real, sorted(real)
    print("light_unit_classes self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    args = sys.argv[1:]
    needle = args[args.index("--reading") + 1] if "--reading" in args else None
    rest = [a for i, a in enumerate(args) if not a.startswith("--") and not (i > 0 and args[i - 1] == "--reading")]
    root = rest[0] if rest else ROOT
    names = light_classes(read_sources(root))
    if needle:
        read = mentions(root, needle)
        names = sorted(n for n in read if n.endswith("Test") and n not in names) if "--heavy" in args else [n for n in names if n in read]
    print(",".join(names))
