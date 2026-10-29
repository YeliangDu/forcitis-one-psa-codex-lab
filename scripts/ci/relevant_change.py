#!/usr/bin/env python3
"""Decide whether a pull request touches anything the backend or frontend builds read. Writes relevant=true|false to
$GITHUB_OUTPUT. Everything is relevant except documentation: docs/ outside the paths the tests read, and root *.md.
Also writes tier=docs|guarded|code (relevant stays what it was: anything beyond pure documentation) and, for tier guarded, what the middle tier runs:
guard_classes (container-free backend test classes that read the changed docs, comma separated), guard_frontend (the glossary copy scripts), guard_release (the release script self-tests).
tier docs: nothing the builds read changed. tier guarded: every changed path is a doc under READ_BY_BUILDS and every file that reads it is one of those three kinds; the
readers are found by scanning the sources (literal paths and path segments overlapping the doc, plus its file name), never from a hand-kept map. tier code: anything else, also a doc some
other reader (a Spring or container test, main code, another script) reads: when in doubt, run everything. A push to main and the nightly run are always tier code.
Also writes legacy_it=true|false: whether the change touches what the SQL Server integration tests (Maven profile legacy-it) exercise.
--paths-from reads one path per line from a file instead of diffing.
On a push the changed files are diffed against $BEFORE; a missing or unknown $BEFORE counts as legacy_it=true.
Usage: relevant_change.py <base-ref>  |  relevant_change.py --paths-from <file>  |  relevant_change.py --self-test (relevant_change_selftest.py)"""
import importlib.util
import os
import re
import subprocess
import sys
from pathlib import Path

# Release checks invoke this script with -I; only trusted sibling modules go back on sys.path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(1, str(Path(__file__).resolve().parents[1] / "guards"))
from acceptance_reports import legacy_acceptance_paths

READ_BY_BUILDS = (
    "docs/glossary/",
    "docs/modernization/events/",
    "docs/modernization/phase1a/SEEYON_VIEWS.sql",
    "docs/modernization/phase1a/infra/one_db_roles_schemas.sql",
    "docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml",
    # R-1b P5 acceptance (EventEnvelopeContractTest, the rename ITs) read the field list and the aggregate register
    "docs/modernization/proposals/EVENT_ANALYTICS_FIELDS.md",
    "docs/modernization/architecture/ssot/i03-aggregates/built.yaml",
    # ADR37-1 guards: the credential deny list (CredentialColumnGuardTest) and the runbook that must not list a check switch (ReadOnlyCheckSwitchGuardTest)
    "docs/data/evidence/dba-handoff/credential-columns.csv",
    "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md",
    # read by scripts/release (deploy gate, release bundle); their self-tests run in ci
    "docs/ops/deploy-gate.json",
    "docs/ops/predeploy-checklist.md",
    "docs/ops/runbooks/prod-approvals.txt",
    # DataArchitectureTest runs the V11 part of the c3/c9 verify scripts against a container database (also checked by db_scripts_aligned.py)
    "docs/ops/runbooks/sql/",
)

# inside READ_BY_BUILDS directories but read by no build: the one-glossary records
NOT_READ_BY_BUILDS = (
    "docs/glossary/issues/",
    "docs/glossary/decision-sheets/",
)

# files outside the builds that read READ_BY_BUILDS docs and run in a CI job whatever changed
ALWAYS_RUN_READERS = (
    "scripts/guards/db_scripts_aligned.py",   # scripts/ci/light.sh (backend job), whatever changed
    # #742: checks and self-tests light.sh runs directly on every event (term rules, BPMN catalogue, proposals/ reads, built.yaml, rulings, decision fragments)
    "scripts/guards/term_rules.py",
    "scripts/guards/build_catalog.py",
    "scripts/guards/proposal_reads.py",
    "scripts/guards/acceptance_rulings.py",
    "scripts/guards/check_playbooks.py",   # reads the playbooks; runs on every event
    "scripts/guards/check_names.py",   # docs paths there are naming rules and the directory allowlist
    "scripts/codegen/scan_aggregates.py",
    "scripts/codegen/scan_acceptance_tests.py",   # imported by scan_aggregates.py
    "scripts/session/build_decisions.py",
    "scripts/session/new_intent.py",   # run by new_intent_selftest.py
    "scripts/session/new_intent_selftest.py",
    # light.sh sources it on every event; the docs paths there are change patterns that pick self-tests
    "scripts/ci/light_triggers.sh",
)
# developer tools that name docs paths but are no CI step: they are not readers. scripts/gates/ holds the local hooks (the gates and their installer);
# its gates_selftest.py does run in CI, but through scripts/ci/light.sh on every event, so a doc only it names needs no further tier
LOCAL_ONLY = ("scripts/hooks/", "scripts/gates/")

# read only by checks that run unconditionally (scripts/ci/light.sh in the backend job on every event, the gitleaks job); docs read by scripts/release must be listed in READ_BY_BUILDS
READ_BY_ALWAYS_RUN_CHECKS = (
    "docs/architecture/ssot/transitions/transitions.yaml",
    # rule and role files: 工具库 start_sheet 的开工单（仓库外，O23a）点名 RULES.md 与角色文件
    "docs/rules/rules.md",
    "docs/rules/roles/",
    # ruling record: read by scripts/guards/acceptance_rulings.py in the always-run acceptance step
    "docs/rules/rulings/",
    # scripts/ci/evidence_imports.py (light.sh) parses the production evidence tools
    "docs/ops/dba-scripts/tools/",
    # deleted by #742; scripts/merge/mq.py still names it: its conflict auto-resolve finds no build_all.py and settles nothing (that code goes in #742's cleanup PR)
    "docs/build_all.py",
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
    "scripts/guards/acceptance_reports.py",
    "scripts/guards/ci_git.py",
    "scripts/ci/relevant_change.py",
    ".github/workflows/nightly-legacy-it.yml",
    ".github/workflows/ci.yml",
)
HANDOFF_SQL_DIR = "docs/ops/dba-scripts/"
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
    if path.startswith(("docs/", "tools/docs/")):  # tools/docs/ was under docs/ before #742; tools/ops and tools/evidence stay relevant (release-job shellcheck, evidence self-test)
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
# a path into one of the docs/ folders (#755 layout; modernization/ holds what acceptance tests read until PR B), written whole or as
# separate string arguments ("docs", "glossary", "terms.yaml"); docs/<anything else> in a test string is a fixture or a label, not a read
DOCS_TOP = "modernization|adr|architecture|business|data|decisions|glossary|legacy|ops|rules"
LITERAL = re.compile(rf"(?<![\w-])(?<![\w-]/)docs/(?:{DOCS_TOP})(?![\w.-])(?:/[\w.*{{}}-]+)*")
SEGMENTS = re.compile(rf"""['"]docs['"]((?:\s*,\s*['"](?:{DOCS_TOP})['"])(?:\s*,\s*['"][\w.*-]+['"])*)""")
# this script and its self-test name docs paths as data and fixtures: never readers
OWN_FILES = {Path(__file__).name, "relevant_change_selftest.py"}


def source_files(root):
    """(file, path relative to root) of every scanned source."""
    for scanned in SCANNED:
        for file in Path(root, scanned).rglob("*"):
            if file.is_file() and file.suffix in SOURCE_SUFFIXES and not SKIPPED_DIRS & set(file.parts) and file.name not in OWN_FILES:
                yield file, file.relative_to(root).as_posix()


def segments_path(match):
    """The docs path a SEGMENTS match spells as separate string arguments."""
    return "/".join(["docs", *re.findall(r"['\"]([\w.*-]+)['\"]", match.group(1))])


def is_covered(path):
    if path.startswith(NOT_READ_BY_BUILDS) or (path + "/").startswith(NOT_READ_BY_BUILDS):
        return False
    listed = READ_BY_BUILDS + READ_BY_ALWAYS_RUN_CHECKS
    return path.startswith(listed) or (path + "/").startswith(listed)


def referenced_docs_paths(root):
    for file, rel in source_files(root):
        text = file.read_text(errors="ignore")
        for match in LITERAL.finditer(text):
            yield rel, match.group(0)
        for match in SEGMENTS.finditer(text):
            yield rel, segments_path(match)


def uncovered_reads(root):
    """Docs paths named by a source that may not run when they change; ALWAYS_RUN_READERS run on every event, so what they read needs no tier."""
    return sorted({f"{rel}: {path}" for rel, path in referenced_docs_paths(root) if rel not in ALWAYS_RUN_READERS and not is_covered(path)})


GUARDED_READER_DIRS = {"scripts/release/": "release"}
WILDCARD = re.compile(r"[*{]")
CODE_SUFFIXES = {".java", ".kt", ".ts", ".tsx", ".js", ".mjs", ".cjs"}
TEST_ROOT = "backend/src/test/java/"
WIDE = re.compile(r"""[(,]\s*(["'`](?:docs|modernization)["'`])|(?<=\.\./)(docs\b)""")
# sources that match WIDE without reading docs at all (a pull request label named "docs" in a test string); every entry must still match, the self-test fails on a stale one
WIDE_NOT_READERS = {
    "scripts/ci/heavy_gate.py": "the test strings use \"docs\" as a label name",
    "scripts/ci/light_unit_classes.py": "docs/ is only a changed-path prefix and a fixture path in its self-test (a literal \"../docs/...\" in a temporary test source); it reads no document of this repository",
    "scripts/release/ci_passed.py": "its self-test builds a temporary git repository with a docs directory; it reads no document of this repository",
    "scripts/merge/mq.py": "\"docs\" is the name of a merge lane; the only path it builds is relevant_change.py's, to ask it about a pull request's file list",
    "scripts/merge/mq_selftest.py": "lane names, and a stub relevant_change.py in a temporary directory; it reads no document of this repository",
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
        for file, rel in source_files(root):
            if rel.startswith(LOCAL_ONLY):
                continue
            text = file.read_text(errors="ignore")
            text = self.texts[rel] = strip_code_comments(text) if file.suffix in CODE_SUFFIXES else text
            covered = []
            for match in LITERAL.finditer(text):
                self.refs.append((rel, WILDCARD.split(match.group(0))[0]))
                covered.append(match.span())
            for match in SEGMENTS.finditer(text):
                self.refs.append((rel, segments_path(match)))
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
        import relevant_change_selftest
        from types import SimpleNamespace   # this module's names; not sys.modules: a loader such as mq_selftest's need not register it
        relevant_change_selftest.self_test(SimpleNamespace(**globals()))
        return
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
