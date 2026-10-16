#!/usr/bin/env python3
"""Print the unit test classes that start no container and no Spring context, comma separated, for `surefire:test -Dtest=` (scripts/precheck.sh --pre-push).

A class is light when its name ends in `Test`, it is not abstract, and neither its own source nor any class it extends (followed through the test sources) mentions a
marker of a heavy test: @SpringBootTest, Testcontainers, a call that starts a database (TestPostgres.container / shared / database, any TestMssql call), a container class, or an *Support / *IntegrationTest base. `*IT` classes belong to failsafe and are never listed.
The list is generated from the sources on purpose: a hand-kept list or a path-to-test map would leave out the next scan test (SeeyonSourceScanTest ran in CI only, three times).

Usage: light_unit_classes.py [test-source-root] | light_unit_classes.py --self-test
"""
import os
import re
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "backend", "src", "test", "java"))
HEAVY = re.compile(r"@SpringBootTest|@Testcontainers|@DataJdbcTest|@WebMvcTest|@AutoConfigureMockMvc|TestPostgres\.(?:container|shared|database)\s*\(|TestMssql\.\w+\s*\(|PostgreSQLContainer|MSSQLServerContainer|GenericContainer|Testcontainers|ApplicationModuleTest")


def strip_comments(text):
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
        for name, text in files.items():
            open(os.path.join(root, name + ".java"), "w", encoding="utf-8").write(text)
        os.makedirs(os.path.join(root, "a")); os.makedirs(os.path.join(root, "b"))
        open(os.path.join(root, "a", "TwinTest.java"), "w", encoding="utf-8").write("package a; class TwinTest { }")
        open(os.path.join(root, "b", "TwinTest.java"), "w", encoding="utf-8").write("package b; @SpringBootTest class TwinTest { }")
        open(os.path.join(root, "a", "OnlyLightTwinTest.java"), "w", encoding="utf-8").write("package a; class OnlyLightTwinTest { }")
        got = light_classes(read_sources(root))
    assert got == ["CommentTest", "LightChildTest", "NamesTest", "OnlyLightTwinTest", "PlainTest", "ScanTest"], got
    real = light_classes(read_sources(ROOT)) if os.path.isdir(ROOT) else []
    assert not os.path.isdir(ROOT) or ("SeeyonSourceScanTest" in real and "ArchitectureRulesTest" in real and "ApplicationIntegrationTest" not in real and "ListPagingGuardTest" not in real), "the real test sources: scan and guard tests are light, container tests are not"
    print("light_unit_classes self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    root = sys.argv[1] if len(sys.argv) > 1 else ROOT
    print(",".join(light_classes(read_sources(root))))
