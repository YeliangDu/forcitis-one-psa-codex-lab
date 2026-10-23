#!/usr/bin/env python3
"""Decide whether a pull request touches anything the backend or frontend builds read. Writes relevant=true|false to
$GITHUB_OUTPUT. Everything is relevant except documentation: docs/ outside the paths the tests read, and root *.md.
Also writes tier=docs|guarded|code (relevant stays what it was: anything beyond pure documentation) and, for tier guarded, what the middle tier runs:
guard_classes (container-free backend test classes that read the changed docs, comma separated), guard_frontend (the glossary copy scripts), guard_release (the release script self-tests).
tier docs: nothing the builds read changed. tier guarded: every changed path is a doc under READ_BY_BUILDS and every file that reads it is one of those three kinds; the
readers are found by scanning the sources (literal paths and path segments overlapping the doc, plus its file name), never from a hand-kept map. tier code: anything else, also a doc some
other reader (a Spring or container test, main code, another script) reads: when in doubt, run everything. A push to main and the nightly run are always tier code.
Also writes legacy_it=true|false: whether the change touches what the SQL Server integration tests (Maven profile legacy-it) exercise.
--paths-from reads one path per line from a file instead of diffing (scripts/precheck.sh uses it so both gates agree).
On a push the changed files are diffed against $BEFORE; a missing or unknown $BEFORE counts as legacy_it=true.
Usage: relevant_change.py <base-ref>  |  relevant_change.py --paths-from <file>  |  relevant_change.py --self-test"""
import importlib.util
import os
import re
import subprocess
import sys
from pathlib import Path

# Release checks invoke this script with -I; only trusted sibling modules go back on sys.path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from acceptance_reports import legacy_acceptance_paths

READ_BY_BUILDS = (
    "docs/modernization/glossary/",
    "docs/modernization/events/",
    "docs/modernization/phase1a/SEEYON_VIEWS.sql",
    "docs/modernization/phase1a/infra/one_db_roles_schemas.sql",
    "docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml",
    # R-1b P5 acceptance (EventEnvelopeContractTest, the rename ITs) read the field list and the aggregate register
    "docs/modernization/proposals/EVENT_ANALYTICS_FIELDS.md",
    "docs/modernization/architecture/ssot/i03-aggregates/built.yaml",
    # ADR37-1 guards: the credential deny list (CredentialColumnGuardTest) and the runbook that must not list a check switch (ReadOnlyCheckSwitchGuardTest)
    "docs/modernization/phase1a/infra/handoff/B-5-dba/evidence/credential_columns.csv",
    "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md",
    # read by scripts/release (deploy gate, release bundle); their self-tests run in ci
    "docs/modernization/status/blockers/",
    "docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md",
    "docs/modernization/phase1a/infra/runbooks/files/",
    "docs/modernization/phase1a/infra/runbooks/prod-approvals.txt",
)

# inside READ_BY_BUILDS directories but read by no build: the one-glossary records
NOT_READ_BY_BUILDS = (
    "docs/modernization/glossary/issues/",
    "docs/modernization/glossary/decision-sheets/",
    "docs/modernization/glossary/GLOSSARY_CONTEXT.md",
)

# files outside the builds that read READ_BY_BUILDS docs and run in a CI job whatever changed
ALWAYS_RUN_READERS = (
    "scripts/ci/db_scripts_aligned.py",   # gitleaks job, whatever changed
    "scripts/ci/baseline_ddl_scan.py",    # assertion-drift job on every pull request; report only
)
# developer tools that name docs paths but are no CI step: they are not readers (scripts/precheck.sh lists glossary/terms.yaml in a `touches` pattern; counting it would hide every unread glossary doc)
LOCAL_ONLY = ("scripts/precheck.sh", "scripts/hooks/")

# read only by steps of the gitleaks job or the assertion-drift job that run unconditionally, or by scripts/precheck.sh; docs read by scripts/release must be listed in READ_BY_BUILDS
READ_BY_ALWAYS_RUN_CHECKS = (
    "docs/modernization/architecture/proposals/j_checks.py",
    "docs/modernization/architecture/togaf/transitions.yaml",
    "docs/modernization/phase1a/infra/runbooks/sql/",
    "docs/modernization/build_all.py",
    # 错题本：build_all.py 的自测步骤（scripts/ci/ci_recurrence.py 读册、quality/defect-bank/defect_bank_check.py 检查册）在 gitleaks 作业里无条件运行
    "docs/modernization/quality/defect-bank/",
    # ruling record: read by scripts/ci/acceptance_rulings.py in the always-run acceptance step
    "docs/modernization/rulings/",
    # session profiles: checked by build_all.py --check, injected by scripts/hooks/session_context.py
    "docs/modernization/sessions/",
    # start_sheet.py 的开工单按需读列表里点名它；handoff_selftest.py 的夹具写同名路径
    "docs/modernization/SESSION_PLAYBOOK.md",
    "docs/modernization/check_legacy_name.py",
    # task register: check_tasks.py runs in build_all.py; session_daily.py reads origin/main's tasks/
    "docs/modernization/check_tasks.py",
    "docs/modernization/tasks/",
    # decision page question rules: run by scripts/precheck.sh when a decision page changes
    "docs/modernization/architecture/decision-page/check_pages.py",
    "docs/modernization/rules/decide/build_decision_page.py",
)


LEGACY_IT_PATHS = (
    "backend/src/main/java/com/forcitis/one/platform/integration/",
    "backend/src/main/java/com/forcitis/one/organization/workforce/",
    "backend/src/main/resources/application",
    "backend/src/main/resources/config/platform_integration.properties",
    "backend/src/main/resources/db/migration/platform_integration/",
    "backend/src/main/resources/db/migration/organization_workforce/",
    "backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/",
    "backend/src/test/java/com/forcitis/one/platform/integration/",
    "backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/legacy/",
    "backend/src/test/java/com/forcitis/one/acceptance/platform/integration/",
    "backend/src/test/java/com/forcitis/one/TestImages.java",
    "backend/src/test/java/com/forcitis/one/fixture/doubles/TestOrgIds.java",
    "backend/src/test/java/com/forcitis/one/TestMssql.java",
    "backend/src/main/java/com/forcitis/one/kernel/WecomUserId.java",
    "backend/src/main/java/com/forcitis/one/kernel/ExternalSourceBrokenException.java",
    "backend/src/main/java/com/forcitis/one/kernel/ExternalSystemUnavailableException.java",
    "backend/src/main/java/com/forcitis/one/platform/approval/domain/Legacy",
    "backend/architecture/test-images.txt",
    "backend/architecture/external-read-registry.yaml",
    # QUAL-CARD-H: imports and fixtures of the moved classes and their test helpers.
    "backend/src/main/java/com/forcitis/one/OneApplication.java",
    "backend/src/main/java/com/forcitis/one/kernel/AccountEmployees.java",
    "backend/src/main/java/com/forcitis/one/kernel/DocumentId.java",
    "backend/src/main/java/com/forcitis/one/kernel/DocumentReference.java",
    "backend/src/main/java/com/forcitis/one/kernel/EmployeeId.java",
    "backend/src/main/java/com/forcitis/one/kernel/UserId.java",
    "backend/src/main/java/com/forcitis/one/kernel/Uuids.java",
    "backend/src/main/java/com/forcitis/one/kernel/WorkContext.java",
    "backend/src/main/java/com/forcitis/one/platform/audit/api/AuditLog.java",
    "backend/src/main/java/com/forcitis/one/platform/identity/api/Permission.java",
    "backend/src/main/java/com/forcitis/one/platform/identity/api/SessionRoles.java",
    "backend/src/test/java/com/forcitis/one/TestDatabase.java",
    "backend/src/test/java/com/forcitis/one/TestPostgres.java",
    "backend/src/test/java/com/forcitis/one/acceptance/AcceptanceSupport.java",
    "backend/src/test/java/com/forcitis/one/acceptance/AccountsAcceptanceSupport.java",
    "backend/src/test/java/com/forcitis/one/fixture/doubles/MutableClock.java",
    "backend/src/test/java/com/forcitis/one/fixture/doubles/TestAccounts.java",
    "backend/src/test/resources/init-schemas.sql",
    "backend/pom.xml",
    "docs/modernization/phase1a/SEEYON_VIEWS.sql",
    "scripts/ci/legacy_it.sh",
    "scripts/ci/backend_verify.sh",
    "scripts/ci/acceptance_reports.py",
    "scripts/ci/ci_git.py",
    "scripts/ci/repeat_new_tests.py",
    "scripts/ci/relevant_change.py",
    ".github/workflows/nightly-legacy-it.yml",
    ".github/workflows/ci.yml",
)
HANDOFF_SQL_DIR = "docs/modernization/phase1a/infra/handoff/B-5-dba/"
NULL_SHA = "0" * 40


def is_legacy_it(path):
    return path.startswith(LEGACY_IT_PATHS) or (path.startswith(HANDOFF_SQL_DIR) and path.endswith(".sql"))


def any_legacy_it(paths):
    return any(is_legacy_it(path) for path in paths if path)


def is_relevant(path):
    if path.startswith(NOT_READ_BY_BUILDS):
        return False
    if path.startswith(READ_BY_BUILDS):
        return True
    if path.startswith("docs/"):
        return False
    return not ("/" not in path and path.endswith(".md"))


# without core.quotepath=off git quotes non-ASCII paths ("docs/...\350..."), which then look relevant
GIT = ("git", "-c", "core.quotepath=off")


def changed_files(base):
    out = subprocess.run([*GIT, "diff", "--no-renames", "--name-only", f"origin/{base}...HEAD"], capture_output=True, text=True, encoding="utf-8", check=True)
    return out.stdout.splitlines()


def files_since(before):
    out = subprocess.run([*GIT, "diff", "--no-renames", "--name-only", before, "HEAD"], capture_output=True, text=True, encoding="utf-8")
    return out.stdout.splitlines() if out.returncode == 0 else None


SCANNED = ("backend/src", "frontend", "scripts")
SKIPPED_DIRS = {"node_modules", "dist", ".pnpm", "target"}
SOURCE_SUFFIXES = {".java", ".kt", ".ts", ".tsx", ".js", ".mjs", ".cjs", ".json", ".yaml", ".yml", ".py", ".sh", ".properties", ".xml", ".sql"}
LITERAL = re.compile(r"docs/modernization(?:/[\w.*{}-]+)*")
SEGMENTS = re.compile(r"""['"]docs['"]\s*,\s*['"]modernization['"]((?:\s*,\s*['"][\w.*-]+['"])*)""")


def is_covered(path):
    if path.startswith(NOT_READ_BY_BUILDS) or (path + "/").startswith(NOT_READ_BY_BUILDS):
        return False
    listed = READ_BY_BUILDS + READ_BY_ALWAYS_RUN_CHECKS
    return path.startswith(listed) or (path + "/").startswith(listed)


def referenced_docs_paths(root):
    for scanned in SCANNED:
        for file in Path(root, scanned).rglob("*"):
            if not file.is_file() or file.suffix not in SOURCE_SUFFIXES or SKIPPED_DIRS & set(file.parts) or file.name == Path(__file__).name:
                continue
            text = file.read_text(errors="ignore")
            for match in LITERAL.finditer(text):
                yield file, match.group(0)
            for match in SEGMENTS.finditer(text):
                tail = re.findall(r"['\"]([\w.*-]+)['\"]", match.group(1))
                yield file, "/".join(["docs/modernization", *tail])


def uncovered_reads(root):
    return sorted({f"{file.relative_to(root)}: {path}" for file, path in referenced_docs_paths(root) if not is_covered(path)})


GUARDED_READER_DIRS = {"scripts/release/": "release"}
WILDCARD = re.compile(r"[*{]")
CODE_SUFFIXES = {".java", ".kt", ".ts", ".tsx", ".js", ".mjs", ".cjs"}
TEST_ROOT = "backend/src/test/java/"
WIDE = re.compile(r"""[(,]\s*(["'`](?:docs|modernization)["'`])|(?<=\.\./)(docs\b(?!/modernization))""")
# sources that match WIDE without reading docs at all (a pull request label named "docs" in a test string); every entry must still match, the self-test fails on a stale one
WIDE_NOT_READERS = {
    "scripts/ci/heavy_gate.py": "the test strings use \"docs\" as a label name",
    "scripts/ci/light_unit_classes.py": "docs/ is only a changed-path prefix and a fixture path in its self-test (a literal \"../docs/...\" in a temporary test source); it reads no document of this repository",
    "scripts/release/ci_passed.py": "its self-test builds a temporary git repository with a docs directory; it reads no document of this repository",
    "scripts/merge/mq.py": "\"docs\" is the name of a merge lane (and of the train subcommand); the only path it builds is relevant_change.py's, to ask it about a pull request's file list",
    "scripts/merge/mq_selftest.py": "lane names, and a stub relevant_change.py in a temporary directory; it reads no document of this repository",
    "scripts/queue/merge_train.py": "the `docs` subcommand collects documentation commits from session branches (git diff against paths under docs/); its self-test builds temporary repositories with a docs directory; it reads no document of the working tree",
}
# frontend scripts that `pnpm run check:copy` and `pnpm run test:scripts` run (checked against package.json by the self-test); any other frontend reader needs the full verify
GUARDED_FRONTEND = ("frontend/scripts/check-copy-glossary.mjs", "frontend/scripts/copy-glossary.mjs", "frontend/scripts/copy-glossary.test.mjs")
# Heavy (container or Spring) test classes that read a guarded doc and may be left out of the middle tier, with the reason. Hand-kept on purpose and checked by the self-test (a stale entry fails):
# every entry is a decision to run less, so it needs the coordinator's approval. Key: (doc path prefix, simple class name).
EXEMPT_HEAVY_READERS = {}   # none approved: the coordinator decided that every heavy reader of a doc makes it tier code (2026-10-16)


CODE_TOKEN = re.compile(r'''"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`|/\*(?:.|\n)*?\*/|//[^\n]*''')


def strip_code_comments(text):
    """Comments out, string literals kept (a glob such as "proposed/*.yaml" must not open a comment)."""
    return CODE_TOKEN.sub(lambda m: m.group(0) if m.group(0)[0] in "\"'`" else " ", text)


class ReaderIndex:
    """What reads what: literal docs paths (and path segments) per source file, and the text of each source (comments out) for file-name and helper-class matches."""

    def __init__(self, root):
        self.root, self.refs, self.texts, self.wide, self.wide_ignored = Path(root), [], {}, [], []
        for scanned in SCANNED:
            for file in Path(root, scanned).rglob("*"):
                if not file.is_file() or file.suffix not in SOURCE_SUFFIXES or SKIPPED_DIRS & set(file.parts) or file.name == Path(__file__).name:
                    continue
                rel = file.relative_to(root).as_posix()
                if rel.startswith(LOCAL_ONLY):
                    continue
                text = file.read_text(errors="ignore")
                text = self.texts[rel] = strip_code_comments(text) if file.suffix in CODE_SUFFIXES else text
                covered = []
                for match in LITERAL.finditer(text):
                    self.refs.append((rel, WILDCARD.split(match.group(0))[0]))
                    covered.append(match.span())
                for match in SEGMENTS.finditer(text):
                    tail = re.findall(r"['\"]([\w.*-]+)['\"]", match.group(1))
                    self.refs.append((rel, "/".join(["docs/modernization", *tail])))
                    covered.append(match.span())
                # a docs path built in a way the two patterns do not capture (Path.of("..", "docs").resolve(...), Files.walk over docs): the file may read any doc
                if any(not any(a <= m.start(m.lastindex) < b for a, b in covered) for m in WIDE.finditer(text)):
                    (self.wide_ignored if rel in WIDE_NOT_READERS else self.wide).append(rel)

    def readers(self, path):
        """Source files that name `path` or something under it, name a directory it lies in, or mention its file name."""
        found = set()
        for rel, literal in self.refs:
            lit = literal.rstrip("/")
            if lit and (path == lit or path.startswith(lit + "/") or lit.startswith(path.rstrip("/") + "/") or literal.startswith(path)):
                found.add(rel)
        name = path.rstrip("/").rsplit("/", 1)[-1]
        if "." in name and len(name) >= 5:
            word = re.compile(rf"(?<![\w.-]){re.escape(name)}(?![\w])")
            found.update(rel for rel, text in self.texts.items() if name in text and word.search(text))
        return found

    def users_of_helper(self, rel):
        """Test sources that mention the simple name of a test helper class."""
        name = rel.rsplit("/", 1)[-1][:-5]
        word = re.compile(rf"\b{re.escape(name)}\b")
        return {other for other, text in self.texts.items() if other.startswith(TEST_ROOT) and other != rel and word.search(text)}


def light_class_names(root):
    spec = importlib.util.spec_from_file_location("light_unit_classes", Path(__file__).with_name("light_unit_classes.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return set(module.light_classes(module.read_sources(Path(root, "backend/src/test/java"))))


def classify_reader(rel, doc, index, light, seen=()):
    """Kinds of work that cover this reader: [('class', name) | ('frontend',) | ('release',) | ('exempt', name) | ('none',)], or None when the middle tier cannot cover it
    (a Spring or container test, main code, any other script). A test helper is covered by every test that uses it, transitively."""
    if rel in ALWAYS_RUN_READERS:
        return [("none",)]
    if rel.startswith(TEST_ROOT) and rel.endswith(".java"):
        name = rel.rsplit("/", 1)[-1][:-5]
        if name in light:
            return [("class", name)]
        if any(doc.startswith(prefix) and name == cls for prefix, cls in EXEMPT_HEAVY_READERS):
            return [("exempt", name)]
        if name.endswith(("Test", "IT")) or rel in seen:
            return None
        users = index.users_of_helper(rel)
        if not users:
            return None
        kinds = []
        for user in sorted(users):
            sub = classify_reader(user, doc, index, light, seen + (rel,))
            if sub is None:
                return None
            kinds += sub
        return kinds
    if rel in GUARDED_FRONTEND:
        return [("frontend",)]
    for prefix, kind in GUARDED_READER_DIRS.items():
        if rel.startswith(prefix):
            return [(kind,)]
    return None


def plan_for(paths, index, light):
    """Middle-tier plan for paths that are all docs under READ_BY_BUILDS: {'classes': set, 'frontend': bool, 'release': bool, 'exempt': set}, or None when anything must run in full."""
    plan = {"classes": set(), "frontend": False, "release": False, "exempt": set()}
    if index.wide:
        return None
    for path in paths:
        readers = index.readers(path)
        if not readers:
            return None
        for rel in readers:
            kinds = classify_reader(rel, path, index, light)
            if kinds is None:
                return None
            for kind in kinds:
                if kind[0] == "class":
                    plan["classes"].add(kind[1])
                elif kind[0] == "exempt":
                    plan["exempt"].add(kind[1])
                elif kind[0] in ("frontend", "release"):
                    plan[kind[0]] = True
    return plan


def is_guarded_doc(path):
    return path.startswith(READ_BY_BUILDS) and not path.startswith(NOT_READ_BY_BUILDS)


def tier_of(paths, root):
    """('docs'|'guarded'|'code', plan or None). Anything the builds read that is not a doc under READ_BY_BUILDS makes the tier code."""
    paths = [p for p in paths if p]
    if any(is_relevant(p) and not is_guarded_doc(p) for p in paths):
        return "code", None
    docs = [p for p in paths if is_guarded_doc(p)]
    if not docs:
        return "docs", None
    plan = plan_for(docs, ReaderIndex(root), light_class_names(root))
    if plan is None:
        return "code", None
    if not (plan["classes"] or plan["frontend"] or plan["release"]):
        return "docs", None
    return "guarded", plan


def tier_lines(tier, plan):
    plan = plan or {"classes": set(), "frontend": False, "release": False, "exempt": set()}
    return [f"tier={tier}", f"guard_classes={','.join(sorted(plan['classes']))}", flag("guard_frontend", plan["frontend"]), flag("guard_release", plan["release"])]


SOURCE_ROOTS = ("backend/src/main/java", "backend/src/test/java")
JAVA_IMPORT = re.compile(r"^import (?:static )?(com\.forcitis\.one\.[\w.]+);", re.M)
RELATIVE_PATH = re.compile(r'Path\.of\("((?:\.\./|architecture/)[^"]+)"')


def mssql_it_inputs(root):
    test_root = Path(root, TEST_ROOT)
    pending = list(test_root.rglob("*MssqlIT.java")) + [test_root / p for p in legacy_acceptance_paths(root)]
    seen = set()
    while pending:
        test = pending.pop()
        if test in seen:
            continue
        seen.add(test)
        text = strip_code_comments(test.read_text(encoding="utf-8"))
        yield test.relative_to(root).as_posix()
        for match in JAVA_IMPORT.finditer(text):
            # Static member / nested type imports resolve to their containing source file.
            parts = match.group(1).split(".")
            while parts:
                candidates = [Path(root, source_root, "/".join(parts) + ".java") for source_root in SOURCE_ROOTS]
                found = next((p for p in candidates if p.is_file()), None)
                if found is not None:
                    yield found.relative_to(root).as_posix()
                    if found.is_relative_to(test_root):
                        pending.append(found)
                    break
                parts.pop()
        # Same-package helpers (not imported), including OrgSourceWorldSupport and ReadOnlyBaselineFixture.
        for sibling in test.parent.glob("*.java"):
            if sibling != test and re.search(rf"\b{re.escape(sibling.stem)}\b", text):
                pending.append(sibling)
        for match in RELATIVE_PATH.finditer(text):
            yield os.path.normpath(os.path.join("backend", match.group(1)))
        # Classpath fixtures and relative Paths are build inputs too, not only ../docs paths.
        for resource in re.findall(r'withInitScript\("([^"\n]+)"\)', text):
            yield "backend/src/test/resources/" + resource


def uncovered_mssql_inputs(root):
    return sorted({path for path in mssql_it_inputs(root) if not is_legacy_it(path)})


def mssql_inputs_self_test():
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        base = TEST_ROOT + "com/forcitis/one/acceptance/platform/integration/"
        moved = base + "MovedIT.java"
        files = {
            "backend/pom.xml": '<project xmlns="http://maven.apache.org/POM/4.0.0"><properties><legacy.it.acceptance>'
                               + moved[len(TEST_ROOT):] + '</legacy.it.acceptance></properties></project>',
            moved: 'class MovedIT extends WorldSupport {}',
            base + "WorldSupport.java": 'import static com.forcitis.one.fixture.Fixture.value;\nclass WorldSupport { Object x = OtherHelper.x; }',
            base + "OtherHelper.java": 'class OtherHelper { Object x = Path.of("../docs/modernization/phase1a/SEEYON_VIEWS.sql"); Object r = Path.of("architecture/external-read-registry.yaml"); }',
            TEST_ROOT + "com/forcitis/one/fixture/Fixture.java": 'import com.forcitis.one.kernel.EmployeeId;\nclass Fixture { Object x = db.withInitScript("init-schemas.sql"); }',
            "backend/src/main/java/com/forcitis/one/kernel/EmployeeId.java": "record EmployeeId() {}",
            TEST_ROOT + "com/forcitis/one/platform/integration/ExistingMssqlIT.java": "class ExistingMssqlIT {}",
        }
        for path, content in files.items():
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        found = set(mssql_it_inputs(root))
        expected = set(files) - {"backend/pom.xml"}
        expected |= {"backend/src/test/resources/init-schemas.sql", "docs/modernization/phase1a/SEEYON_VIEWS.sql", "backend/architecture/external-read-registry.yaml"}
        assert found == expected, f"SQL Server input scan lost tests, inherited/local/imported helpers, static imports or fixtures: {expected - found}; extra={found - expected}"


def tiers_self_test(root):
    import tempfile

    def write(base, rel, text):
        target = Path(base, rel)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    with tempfile.TemporaryDirectory() as tmp:
        t = "backend/src/test/java/com/forcitis/one/"
        write(tmp, t + "LightReadTest.java", 'class LightReadTest { Path p = Path.of("docs/modernization/events/light.yaml"); }')
        write(tmp, t + "HeavyReadIT.java", 'class HeavyReadIT { String p = "docs/modernization/events/heavy.yaml"; }')
        write(tmp, t + "SpringReadTest.java", '@SpringBootTest class SpringReadTest { String p = "docs/modernization/events/spring.yaml"; }')
        write(tmp, t + "Helper.java", 'class Helper { static String p = "docs/modernization/events/helper.yaml"; }')
        write(tmp, t + "UserOneTest.java", "class UserOneTest { Object h = Helper.p; }")
        write(tmp, t + "UserTwoTest.java", "class UserTwoTest { Object h = Helper.p; }")
        write(tmp, t + "HeavyHelper.java", 'class HeavyHelper { static String p = "docs/modernization/events/heavyhelper.yaml"; }')
        write(tmp, t + "HeavyUserIT.java", "class HeavyUserIT { Object h = HeavyHelper.p; }")
        write(tmp, t + "NoUserHelper.java", 'class NoUserHelper { static String p = "docs/modernization/events/nouser.yaml"; }')
        write(tmp, "backend/src/main/java/com/forcitis/one/Main.java", 'class Main { String p = "docs/modernization/events/main.yaml"; }')
        write(tmp, t + "CommentOnlyTest.java", 'class CommentOnlyTest {\n // reads docs/modernization/events/comment.yaml in theory\n /* and comment.yaml again */ }')
        write(tmp, t + "ByNameIT.java", 'class ByNameIT { String dir = base(); String f = dir + "/byname.yaml"; }')
        write(tmp, t + "ByNameTest.java", 'class ByNameTest { String f = "docs/modernization/events/byname.yaml"; }')
        write(tmp, "frontend/scripts/copy-glossary.mjs", 'const p = "docs/modernization/glossary/front.yaml";')
        write(tmp, "frontend/src/app.ts", 'const p = "docs/modernization/glossary/app.yaml";')
        write(tmp, "scripts/release/gate.py", 'P = "docs/modernization/status/blockers/"')
        write(tmp, "scripts/other/tool.py", 'P = "docs/modernization/events/tool.yaml"')
        write(tmp, "scripts/precheck.sh", "cat docs/modernization/events/only-precheck.yaml; cat docs/modernization/glossary/")
        write(tmp, "scripts/hooks/h.py", 'P = "docs/modernization/events/only-hook.yaml"')
        write(tmp, "scripts/ci/db_scripts_aligned.py", 'P = "docs/modernization/events/always.yaml"')
        write(tmp, t + "StarGlobIT.java", 'class StarGlobIT { String g = "glob:**/*.java"; String p = "docs/modernization/events/after-star.yaml"; } /** */')
        write(tmp, "frontend/scripts/generate-api.mjs", 'const p = "docs/modernization/glossary/gen.yaml";')
        write(tmp, t + "GlossaryRelatedTest.java", 'class GlossaryRelatedTest { String p = "docs/modernization/glossary/terms.yaml"; }')
        write(tmp, t + "ApplicationIntegrationTest.java", '@SpringBootTest class ApplicationIntegrationTest { String p = "docs/modernization/glossary/terms.yaml"; }')
        ev = "docs/modernization/events/"
        gl = "docs/modernization/glossary/"

        def tier(*paths):
            return tier_of(list(paths), tmp)

        def classes(*paths):
            kind, plan = tier(*paths)
            assert kind == "guarded", (paths, kind)
            return sorted(plan["classes"])

        assert classes(ev + "light.yaml") == ["LightReadTest"]
        assert tier(ev + "heavy.yaml") == ("code", None), "an IT reads it"
        assert tier(ev + "spring.yaml") == ("code", None), "a Spring test reads it"
        assert classes(ev + "helper.yaml") == ["UserOneTest", "UserTwoTest"], "a helper is covered by the tests that use it"
        assert tier(ev + "heavyhelper.yaml") == ("code", None), "a helper used by an IT"
        assert tier(ev + "nouser.yaml") == ("code", None), "a helper nobody uses: unknown, so everything runs"
        assert tier(ev + "main.yaml") == ("code", None), "main code reads it"
        assert tier(ev + "comment.yaml") == ("code", None), "no reader found: everything runs"
        assert tier(ev + "byname.yaml") == ("code", None), "another reader names the file without the full path"
        assert tier(ev + "tool.yaml") == ("code", None), "a script outside the covered ones reads it"
        assert tier(ev + "only-precheck.yaml") == ("code", None), "precheck.sh is a developer tool, no reader: nothing reads it, so everything runs"
        assert tier(ev + "only-hook.yaml") == ("code", None)
        assert tier(ev + "always.yaml") == ("docs", None), "read only by a script that runs in every job"
        assert tier(ev + "after-star.yaml") == ("code", None), "a /* inside a string literal must not hide what follows"
        assert tier(gl + "gen.yaml") == ("code", None), "a frontend script that no guarded pnpm script runs"
        assert strip_code_comments('a "x/*y" // c\nb /* c */ "d" \'e//f\'') == 'a "x/*y"  \nb   "d" \'e//f\''
        write(tmp, t + "WalkIT.java", 'class WalkIT { Object w = Files.walk(Path.of("..", "docs")); }')
        assert tier(ev + "light.yaml") == ("code", None), "a reader that walks docs: any doc may be read"
        Path(tmp, t + "WalkIT.java").unlink()
        with tempfile.TemporaryDirectory() as tmp2:
            write(tmp2, t + "SegDirTest.java", 'class SegDirTest { Path d = Path.of("docs", "modernization", "status", "blockers"); }')
            assert tier_of(["docs/modernization/status/blockers/zz.md"], tmp2)[1]["classes"] == {"SegDirTest"}, "a directory named by path segments"
        write(tmp, t + "EventsApplicationIntegrationTest.java", "x")
        write(tmp, t + "ApplicationIntegrationTest.java", '@SpringBootTest class ApplicationIntegrationTest { String p = "docs/modernization/glossary/terms.yaml"; String q = "docs/modernization/events/other.yaml"; }')
        EXEMPT_HEAVY_READERS[(gl, "ApplicationIntegrationTest")] = "test only"
        try:
            assert tier(ev + "other.yaml") == ("code", None), "the exemption is limited to its doc prefix"
        finally:
            EXEMPT_HEAVY_READERS.clear()
        kind, plan = tier(gl + "front.yaml")
        assert kind == "guarded" and plan["frontend"] and not plan["classes"]
        assert tier(gl + "app.yaml") == ("code", None), "a frontend source outside frontend/scripts reads it"
        kind, plan = tier("docs/modernization/status/blockers/x.md")
        assert kind == "guarded" and plan["release"]
        assert tier(gl + "terms.yaml") == ("code", None), "a heavy test reads it and no exemption is approved"
        EXEMPT_HEAVY_READERS[(gl, "ApplicationIntegrationTest")] = "test only"
        try:
            kind, plan = tier(gl + "terms.yaml")
            assert kind == "guarded" and plan["classes"] == {"GlossaryRelatedTest"} and plan["exempt"] == {"ApplicationIntegrationTest"}, (kind, plan)
        finally:
            EXEMPT_HEAVY_READERS.clear()
        # mixed changes
        assert tier(ev + "light.yaml", "backend/pom.xml") == ("code", None)
        assert tier(ev + "light.yaml", "docs/modernization/adr/src/ADR-0001.md")[0] == "guarded"
        assert tier(ev + "light.yaml", ev + "heavy.yaml") == ("code", None)
        assert tier("docs/modernization/adr/src/ADR-0001.md", "README.md") == ("docs", None)
        assert tier(gl + "issues/x.md") == ("docs", None)
        assert tier() == ("docs", None)
        lines = tier_lines(*tier(ev + "helper.yaml"))
        assert lines[0] == "tier=guarded" and lines[1] == "guard_classes=UserOneTest,UserTwoTest" and lines[2:] == ["guard_frontend=false", "guard_release=false"], lines
        assert tier_lines("code", None) == ["tier=code", "guard_classes=", "guard_frontend=false", "guard_release=false"]
        # the exemption list: a stale entry (class gone, or no longer a reader) must be noticed
        index, light = ReaderIndex(root), light_class_names(root)
        for (prefix, cls), why in EXEMPT_HEAVY_READERS.items():
            assert why.strip(), f"exemption {cls} needs a reason"
            files = [rel for rel in index.texts if rel.endswith(f"/{cls}.java")]
            assert files, f"exemption {prefix} {cls}: no such class any more"
            assert cls not in light, f"{cls} is a light test now: drop the exemption"
            reached = {k for r in index.readers(prefix + "terms.yaml") for k in (classify_reader(r, prefix + "terms.yaml", index, light) or [])}
            assert ("exempt", cls) in reached, f"exemption {prefix} {cls} is never reached: remove it"


def rename_self_test():
    """A doc moved out from under a read path must still list the path it left (git diff rename detection shows only the new path)."""
    import tempfile
    env = {**os.environ, "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}
    with tempfile.TemporaryDirectory() as tmp:
        def git(*args):
            return subprocess.run(["git", *args], cwd=tmp, capture_output=True, text=True, check=True, env=env).stdout
        git("init", "-q", "-b", "main")
        old = Path(tmp, "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md")
        old.parent.mkdir(parents=True)
        old.write_text("# runbook\n" * 20, encoding="utf-8")
        git("add", "-A"); git("-c", "commit.gpgsign=false", "commit", "-q", "-m", "base")
        git("update-ref", "refs/remotes/origin/main", "HEAD")
        base = git("rev-parse", "HEAD").strip()
        new = Path(tmp, "docs/modernization/other/INFRA_RUNBOOK_OLD.md")
        new.parent.mkdir(parents=True)
        git("mv", str(old), str(new)); git("-c", "commit.gpgsign=false", "commit", "-q", "-m", "move")
        cwd = os.getcwd()
        try:
            os.chdir(tmp)
            assert sorted(changed_files("main")) == ["docs/modernization/other/INFRA_RUNBOOK_OLD.md", "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md"], changed_files("main")
            assert sorted(files_since(base)) == sorted(changed_files("main"))
        finally:
            os.chdir(cwd)


def real_tiers_self_test(root):
    """Every doc the builds read: a tier that covers every reader found, or the full run. Nothing the scan cannot place is left to the cheap tier."""
    index, light = ReaderIndex(root), light_class_names(root)
    tracked = subprocess.run([*GIT, "ls-files", "docs"], cwd=root, capture_output=True, text=True, encoding="utf-8", check=True).stdout.splitlines()
    docs = [p for p in tracked if is_guarded_doc(p)]
    assert docs, "no tracked doc under READ_BY_BUILDS"
    assert sorted(index.wide_ignored) == sorted(WIDE_NOT_READERS), f"WIDE_NOT_READERS lists files that no longer match: {sorted(set(WIDE_NOT_READERS) - set(index.wide_ignored))}"
    assert not index.wide, "sources that build docs paths in a way the scan cannot read (they may read any doc): " + ", ".join(index.wide)
    package = Path(root, "frontend/package.json").read_text(encoding="utf-8")
    assert "scripts/check-copy-glossary.mjs" in package and "scripts/*.test.mjs" in package, "frontend/package.json no longer runs the scripts listed in GUARDED_FRONTEND"
    for rel in GUARDED_FRONTEND:
        assert rel in index.texts, f"{rel} is listed in GUARDED_FRONTEND but does not exist"
    assert "copy-glossary" in index.texts["frontend/scripts/check-copy-glossary.mjs"] and "copy-glossary" in index.texts["frontend/scripts/copy-glossary.test.mjs"]
    for entry in READ_BY_BUILDS:
        assert index.readers(entry), f"READ_BY_BUILDS lists {entry}, but no source reads it (stale entry?)"
    guarded = 0
    for path in docs:
        plan = plan_for([path], index, light)
        if plan is None:
            continue
        guarded += 1
        for rel in index.readers(path):
            assert classify_reader(rel, path, index, light) is not None
            assert not rel.startswith("backend/src/main/"), f"{path} is read by main code {rel}"
            assert rel in ALWAYS_RUN_READERS or rel in GUARDED_FRONTEND or rel.startswith((TEST_ROOT, "scripts/release/")), f"{path}: reader {rel} is not one the middle tier runs"
    assert guarded, "every doc went to the full run: the middle tier is empty"
    expected = {
        "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md": {"ReadOnlyCheckSwitchGuardTest"},
        "docs/modernization/phase1a/infra/handoff/B-5-dba/evidence/credential_columns.csv": {"CredentialColumnGuardTest"},
    }
    for path, classes in expected.items():
        plan = plan_for([path], index, light)
        assert plan is not None and classes <= plan["classes"], f"{path} must trigger {sorted(classes)}, plans {plan}"
    assert plan_for(["docs/modernization/events/asyncapi.yaml"], index, light) is None, "ITs read the event catalogue: full run"
    for path in ("docs/modernization/glossary/terms.yaml", "docs/modernization/glossary/bcs.yaml"):
        assert plan_for([path], index, light) is None, f"{path}: a heavy test (ApplicationIntegrationTest) reads it and no exemption is approved: full run"
    assert plan_for(["docs/modernization/status/blockers/080-1a-no-deploy-until-fix.md"], index, light)["release"]


def self_test():
    root = Path(__file__).resolve().parents[2]
    for path in (
        "backend/src/main/resources/application.properties",
        "backend/src/main/resources/application-read-only-verification.properties",
        "backend/src/main/resources/application-read-only-verification.yml",
        "backend/src/main/resources/application-read-only-verification.yaml",
        "backend/src/main/resources/application-prod.properties",
        "backend/src/main/resources/config/platform_integration.properties",
        "backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql",
        "backend/src/main/resources/db/migration/organization_workforce/V20261014000000__organization_workforce_baseline.sql",
        "backend/src/main/java/com/forcitis/one/organization/workforce/application/ApplySourceSnapshot.java",
        "scripts/ci/ci_git.py",
        "scripts/ci/repeat_new_tests.py",
    ):
        assert is_legacy_it(path), f"QUAL-CARD-H-R2-3: direct SQL Server input must trigger legacy-it: {path}"
    scheduled = subprocess.run([sys.executable, str(Path(__file__).resolve()), "main"],
                               env={**os.environ, "GITHUB_EVENT_NAME": "schedule", "GITHUB_OUTPUT": os.devnull},
                               capture_output=True, text=True, check=True)
    assert "legacy_it=true" in scheduled.stdout.splitlines(), "QUAL-CARD-H-R2-7: schedule must always run SQL Server"
    mssql_inputs_self_test()
    unlisted = uncovered_mssql_inputs(root)
    assert not unlisted, "files the SQL Server tests or their helpers import or read are not in LEGACY_IT_PATHS (add them in scripts/ci/relevant_change.py):\n  " + "\n  ".join(unlisted)
    for path in legacy_acceptance_paths(root):
        assert is_legacy_it(TEST_ROOT + path), f"legacy.it.acceptance class does not trigger legacy-it: {path}"
    tiers_self_test(root)
    rename_self_test()
    real_tiers_self_test(root)
    missing = uncovered_reads(root)
    assert not missing, "builds read docs paths that READ_BY_BUILDS does not list or NOT_READ_BY_BUILDS excludes (fix the lists in scripts/ci/relevant_change.py):\n  " + "\n  ".join(missing)
    assert is_relevant("backend/pom.xml")
    assert is_relevant(".github/workflows/ci.yml")
    assert is_relevant("scripts/ci/relevant_change.py")
    assert is_relevant("docs/modernization/glossary/terms.yaml")
    assert is_relevant("docs/modernization/events/asyncapi.yaml")
    assert not is_relevant("docs/modernization/glossary/issues/2026-10-15-120000-one-ops-x.md")
    assert not is_relevant("docs/modernization/glossary/decision-sheets/2026-10-20.md")
    assert not is_relevant("docs/modernization/glossary/GLOSSARY_CONTEXT.md")
    assert is_covered("docs/modernization/glossary/terms.yaml")
    assert not is_covered("docs/modernization/glossary/issues/x.md"), "a build reading NOT_READ_BY_BUILDS must fail the scan"
    assert not is_covered("docs/modernization/glossary/issues")
    assert is_covered("docs/modernization/sessions/one-ops.md")
    assert is_relevant("docs/modernization/phase1a/SEEYON_VIEWS.sql")
    assert is_relevant("docs/modernization/status/blockers/080-1a-no-deploy-until-fix.md")
    assert not is_relevant("docs/modernization/adr/src/ADR-0001.md")
    assert not is_relevant("docs/modernization/phase1a/INTERFACES.md")
    assert not is_relevant("README.md")
    assert not is_relevant("docs/modernization/intent/2026-10-15-120000-one-ops-中文.md")
    assert is_relevant('"docs/q\\"x.md"'), "a path git still quotes must count as relevant"
    assert not is_relevant("docs/modernization/phase1a/infra/runbooks/sql/c3_dev_db.sql")
    assert is_relevant("notes.txt")
    assert not is_relevant("docs/modernization/build_all.py")
    assert not is_relevant("docs/modernization/check_legacy_name.py")
    assert is_relevant("docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml")
    assert is_relevant("docs/modernization/phase1a/infra/one_db_roles_schemas.sql")
    assert any_relevant(["docs/modernization/CODING_STANDARD.md", "docs/modernization/glossary/terms.yaml"])
    assert not any_relevant(["docs/modernization/CODING_STANDARD.md", "CLAUDE.md", ""])
    assert not any_relevant([])
    assert is_legacy_it("backend/src/main/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViews.java")
    assert is_legacy_it("backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/LegacyInboxConfiguration.java")
    assert is_legacy_it("backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java")
    assert is_legacy_it("backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/legacy/LegacyInboxAccessIT.java")
    assert is_legacy_it("backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java")
    assert is_legacy_it("backend/architecture/test-images.txt")
    assert is_legacy_it("backend/src/test/java/com/forcitis/one/TestMssql.java")
    assert is_legacy_it("backend/architecture/external-read-registry.yaml")
    assert is_legacy_it("backend/pom.xml")
    assert is_legacy_it("docs/modernization/phase1a/SEEYON_VIEWS.sql")
    assert is_legacy_it("docs/modernization/phase1a/infra/handoff/B-5-dba/read-all/a8_apply.sql")
    assert not is_legacy_it("docs/modernization/phase1a/infra/handoff/B-5-dba/README.md")
    assert not is_legacy_it("backend/src/main/java/com/forcitis/one/platform/integrationx/Other.java")
    assert is_legacy_it("backend/src/main/java/com/forcitis/one/organization/workforce/WorkforceService.java")
    assert not is_legacy_it("backend/src/test/java/com/forcitis/one/organization/workforce/WorkforceServiceTest.java")
    assert not is_legacy_it("frontend/package.json")
    assert is_legacy_it(".github/workflows/ci.yml")
    assert is_legacy_it(".github/workflows/nightly-legacy-it.yml")
    assert is_legacy_it("scripts/ci/legacy_it.sh")
    assert not is_legacy_it(".github/workflows/deploy.yml")
    assert is_legacy_it("backend/src/main/java/com/forcitis/one/kernel/WecomUserId.java")
    assert not is_legacy_it("backend/src/main/java/com/forcitis/one/kernel/Money.java")
    assert any_legacy_it(["README.md", "backend/pom.xml", ""])
    assert not any_legacy_it(["README.md", "", "backend/src/main/java/com/forcitis/one/cost/timesheet/Timesheet.java"])
    assert not any_legacy_it([])


def any_relevant(paths):
    return any(is_relevant(path) for path in paths if path)


def flag(name, value):
    return f"{name}={'true' if value else 'false'}"


def write_outputs(lines):
    for line in lines:
        print(line)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as out:
            out.write("".join(line + "\n" for line in lines))


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    if len(sys.argv) == 3 and sys.argv[1] == "--reads-docs":   # precheck.sh: exit 0 when a listed path is a file the builds read (READ_BY_BUILDS only: the always-run checks are CI's)
        sys.exit(0 if any(not p.startswith(NOT_READ_BY_BUILDS) and p.startswith(READ_BY_BUILDS) for p in Path(sys.argv[2]).read_text(encoding="utf-8").splitlines() if p) else 1)
    if len(sys.argv) == 3 and sys.argv[1] == "--paths-from":
        paths = Path(sys.argv[2]).read_text(encoding="utf-8").splitlines()
        print(flag("relevant", any_relevant(paths)))
        print(flag("legacy_it", any_legacy_it(paths)))
        print(*tier_lines(*tier_of(paths, Path(__file__).resolve().parents[2])), sep="\n")
        return
    base = sys.argv[1] if len(sys.argv) > 1 else ""
    event = os.environ.get("GITHUB_EVENT_NAME")
    tier = tier_lines("code", None)   # push, schedule and anything else: the full run
    if event == "pull_request" and base:
        files = changed_files(base)
        relevant = any(is_relevant(f) for f in files)
        legacy_it = any_legacy_it(files)
        tier = tier_lines(*tier_of(files, Path(__file__).resolve().parents[2]))
        print(f"{len(files)} changed files, relevant={relevant}, legacy_it={legacy_it}, {', '.join(tier)}")
    elif event == "schedule":
        relevant, legacy_it = True, True   # full nightly run, including the SQL Server tier
    else:
        relevant = True
        before = os.environ.get("BEFORE", "")
        files = files_since(before) if event == "push" and before and before != NULL_SHA else None
        legacy_it = files is None or any_legacy_it(files)
    write_outputs([flag("relevant", relevant), flag("legacy_it", legacy_it), *tier])


main()
