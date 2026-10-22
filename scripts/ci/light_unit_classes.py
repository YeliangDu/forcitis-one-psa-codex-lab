#!/usr/bin/env python3
"""Print the unit test classes that start no container and no Spring context, comma separated, for `surefire:test -Dtest=` (scripts/precheck.sh --pre-push).

A class is light when its name ends in `Test`, it is not abstract, and neither its own source nor any class it extends (followed through the test sources) mentions a
marker of a heavy test: @SpringBootTest, Testcontainers, a call that starts a database (TestPostgres.container / shared / database, any TestMssql call), a container class, or an *Support / *IntegrationTest base. `*IT` classes belong to failsafe and are never listed.
The list is generated from the sources on purpose: a hand-kept list or a path-to-test map would leave out the next scan test (SeeyonSourceScanTest ran in CI only, three times).

--changed <file> narrows the list to what the changed paths (one per line) can affect (scripts/precheck.sh --pre-push): the tests outside the module directories always run, a module's own tests only when that module changed, and any
change in backend/ that is not module source (kernel, resources, pom, test fixtures, a module that no longer exists) falls back to the whole list; with --explain it prints the scope instead.

Usage: light_unit_classes.py [test-source-root] | light_unit_classes.py --reading <text> [--heavy] [test-source-root] | light_unit_classes.py --changed <file> [--explain] | light_unit_classes.py --self-test
--reading keeps the classes that read the thing named by the text (it occurs inside a string literal, or the class uses a helper class that reads it), e.g. `architecture` for the guards that read backend/architecture; with --heavy it lists the classes that mention it but start a container (CI only).
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


def mentions(root, needle, main_root=None):
    """Simple names of the classes that read the thing named by the text: the text occurs inside a string literal of the class (comments removed), or the class uses a helper
    class (a class not named *Test, in the tests or in src/main, e.g. PrimaryKeyExemptions, ExternalReadRegister, IdempotencyScope) that does. One level of helper only: a helper of a helper is not followed."""
    if main_root is None:
        guess = os.path.normpath(os.path.join(root, "..", "..", "main", "java"))
        main_root = guess if os.path.isdir(guess) else None

    def read(folder_root):
        texts = {}
        for folder, _, files in os.walk(folder_root):
            for name in files:
                if name.endswith(".java"):
                    text = open(os.path.join(folder, name), encoding="utf-8").read()
                    texts.setdefault(name[:-5], []).append(re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", text, flags=re.S)))
        return texts

    reads = lambda ts: any(needle in lit for t in ts for lit in re.findall(r'"(?:\\.|[^"\\\n])*"', t))
    texts, main_texts = read(root), read(main_root) if main_root else {}
    literal = {n for n, ts in texts.items() if reads(ts)}
    helpers = {n for n in literal if not n.endswith("Test")} | {n for n, ts in main_texts.items() if reads(ts)}
    uses = {n for n, ts in texts.items() if n not in literal and any(re.search(rf"\b{re.escape(h)}\b", t) for t in ts for h in helpers)}
    return literal | uses


MODULE_SOURCE = re.compile(r"^backend/src/(?:main|test)/java/com/forcitis/one/([a-z]+)/([a-z]+)/")


def changed_modules(paths, main_root):
    """({"bc.module"} of the changed module sources, whole) — whole is True when a backend/ path is not source of a module that exists in src/main (then every module's tests may be affected)."""
    modules, whole = set(), False
    for path in paths:
        m = MODULE_SOURCE.match(path)
        if m and os.path.isdir(os.path.join(main_root, m.group(1), m.group(2))):
            modules.add(f"{m.group(1)}.{m.group(2)}")
        elif path.startswith("backend/"):
            whole = True
    return modules, whole


def read_directories(root, main_root):
    """{simple class name: {module "bc.module" of each file, or None when the file is outside the module directories}}."""
    where = {}
    for folder, _, files in os.walk(root):
        parts = os.path.relpath(folder, root).split(os.sep)
        module = f"{parts[3]}.{parts[4]}" if len(parts) >= 5 and parts[:3] == ["com", "forcitis", "one"] and os.path.isdir(os.path.join(main_root, parts[3], parts[4])) else None
        for name in files:
            if name.endswith(".java"):
                where.setdefault(name[:-5], set()).add(module)
    return where


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
    with tempfile.TemporaryDirectory() as base:
        root = os.path.join(base, "src", "test", "java")
        os.makedirs(root)
        os.makedirs(os.path.join(base, "src", "main", "java"))
        open(os.path.join(base, "src", "main", "java", "MainScope.java"), "w", encoding="utf-8").write('class MainScope { static Object read() { return "/architecture/idempotent-endpoints.txt"; } }')
        for name, text in {"ReadsLightTest": 'class ReadsLightTest { Path p = Path.of("architecture/x.txt"); }', "ReadsJoinedTest": 'class ReadsJoinedTest { Path p = Path.of("architecture").resolve("y.txt"); }',
                           "ReadsViaHelperTest": "class ReadsViaHelperTest { Object o = RegistryReader.load(); }", "RegistryReader": 'class RegistryReader { static Object load() { return Path.of("architecture/z.txt"); } }',
                           "ReadsHeavyTest": '@SpringBootTest class ReadsHeavyTest { String s = "architecture/y.txt"; }', "HelperOfHelperTest": "class HelperOfHelperTest { Object o = ViaReader.go(); }",
                           "ViaReader": "class ViaReader { static Object go() { return RegistryReader.load(); } }", "OtherTest": "class OtherTest { }",
                           "CommentOnlyTest": 'class CommentOnlyTest { /* "architecture/z.txt" */ }', "IdentifierTest": "class IdentifierTest { Object architecture = null; Object o = new ArchitectureRules(); }",
                           "ViaMainTest": "class ViaMainTest { Object o = MainScope.read(); }"}.items():
            open(os.path.join(root, name + ".java"), "w", encoding="utf-8").write(text)
        light = cli(["--reading", "architecture", root]).split(",")
        heavy = cli(["--reading", "architecture", "--heavy", root]).split(",")
        everything = cli([root]).split(",")
    assert light == ["ReadsJoinedTest", "ReadsLightTest", "ReadsViaHelperTest", "ViaMainTest"], f"--reading: a string literal or a helper that reads, not comments, identifiers or a helper of a helper: {light}"
    assert heavy == ["ReadsHeavyTest"], f"--heavy: the readers that start a container: {heavy}"
    assert "OtherTest" in everything and "ReadsHeavyTest" not in everything, "without --reading the whole light list"
    with tempfile.TemporaryDirectory() as base:
        root = os.path.join(base, "src", "test", "java")
        one = os.path.join(root, "com", "forcitis", "one")
        for rel in ("a/b", "a/c", "a/b/domain", "fixture/doubles", "kernel"):
            os.makedirs(os.path.join(one, *rel.split("/")))
        for rel in ("a/b", "a/c"):
            os.makedirs(os.path.join(base, "src", "main", "java", "com", "forcitis", "one", *rel.split("/")))
        for rel, name in (("", "TopTest"), ("kernel", "KernelTest"), ("a/b", "BTest"), ("a/b/domain", "BDomainTest"), ("a/c", "CTest"), ("fixture/doubles", "DoublesTest")):
            open(os.path.join(one, *filter(None, rel.split("/")), name + ".java"), "w", encoding="utf-8").write(f"class {name} {{ }}")
        listing = os.path.join(base, "changed")

        def narrowed(*paths):
            open(listing, "w", encoding="utf-8").write("\n".join(paths) + "\n")
            return cli(["--changed", listing], root).split(","), cli(["--changed", listing, "--explain"], root)
        got, _ = narrowed("backend/src/main/java/com/forcitis/one/a/b/domain/X.java")
        assert got == ["BDomainTest", "BTest", "DoublesTest", "KernelTest", "TopTest"], f"one module changed: its tests and the tests outside the modules: {got}"
        got, why = narrowed("frontend/src/x.ts", "backend/src/test/java/com/forcitis/one/a/c/CTest.java")
        assert got == ["CTest", "DoublesTest", "KernelTest", "TopTest"] and "a.c" in why, (got, why)
        assert narrowed("frontend/src/x.ts")[0] == ["DoublesTest", "KernelTest", "TopTest"], "nothing in backend/: the tests outside the modules"
        for whole in ("backend/pom.xml", "backend/src/main/resources/db/x.sql", "backend/src/main/java/com/forcitis/one/kernel/K.java", "backend/src/test/java/com/forcitis/one/fixture/doubles/D.java",
                      "backend/src/main/java/com/forcitis/one/a/gone/G.java", "backend/glossary/x.yaml"):
            got, why = narrowed("backend/src/main/java/com/forcitis/one/a/b/X.java", whole)
            assert got == ["BDomainTest", "BTest", "CTest", "DoublesTest", "KernelTest", "TopTest"] and "every light test" in why, (whole, got)
    if os.path.isdir(ROOT):
        real = set(cli(["--reading", "architecture"]).split(","))
        assert {"PrimaryKeyTypeGuardTest", "RepositoryWriteGuardTest", "SystemActorCatalogueTest", "CredentialColumnGuardTest", "PrimaryKeyExemptionsTest", "SeeyonSourceScanTest", "QualifiedBaseTableGuardTest", "IdempotencyScopeTest", "SystemActorsTest"} <= real, sorted(real)
    print("light_unit_classes self-test: ok")


def cli(args, root=ROOT):
    needle = args[args.index("--reading") + 1] if "--reading" in args else None
    listing = args[args.index("--changed") + 1] if "--changed" in args else None
    rest = [a for i, a in enumerate(args) if not a.startswith("--") and not (i > 0 and args[i - 1] in ("--reading", "--changed"))]
    root = rest[0] if rest else root
    names = light_classes(read_sources(root))
    if listing:
        main_root = os.path.normpath(os.path.join(root, "..", "..", "main", "java", "com", "forcitis", "one"))
        modules, whole = changed_modules([line.strip() for line in open(listing, encoding="utf-8") if line.strip()], main_root)
        if "--explain" in args:
            return "every light test (a backend path outside module sources changed)" if whole else f"tests outside the modules + modules: {' '.join(sorted(modules)) or '(none)'}"
        if not whole:
            where = read_directories(root, main_root)
            names = [n for n in names if None in where[n] or where[n] & modules]
    if needle:
        read = mentions(root, needle)
        names = sorted(n for n in read if n.endswith("Test") and n not in names) if "--heavy" in args else [n for n in names if n in read]
    return ",".join(names)


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    print(cli(sys.argv[1:]))
