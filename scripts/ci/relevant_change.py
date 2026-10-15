#!/usr/bin/env python3
"""Decide whether a pull request touches anything the backend or frontend builds read. Writes relevant=true|false to
$GITHUB_OUTPUT. Everything is relevant except documentation: docs/ outside the paths the tests read, and root *.md.
Also writes legacy_it=true|false: whether the change touches what the SQL Server integration tests (Maven profile legacy-it) exercise.
--paths-from reads one path per line from a file instead of diffing (scripts/precheck.sh uses it so both gates agree).
On a push the changed files are diffed against $BEFORE; a missing or unknown $BEFORE counts as legacy_it=true.
Usage: relevant_change.py <base-ref>  |  relevant_change.py --paths-from <file>  |  relevant_change.py --self-test"""
import os
import re
import subprocess
import sys
from pathlib import Path

READ_BY_BUILDS = (
    "docs/modernization/glossary/",
    "docs/modernization/events/",
    "docs/modernization/phase1a/SEEYON_VIEWS.sql",
    "docs/modernization/phase1a/infra/one_db_roles_schemas.sql",
    "docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml",
    # read by scripts/release (deploy gate, release bundle); their self-tests run in ci
    "docs/modernization/status/blockers/",
    "docs/modernization/phase1a/infra/PREDEPLOY_CHECKLIST.md",
    "docs/modernization/phase1a/infra/runbooks/files/",
    "docs/modernization/phase1a/infra/runbooks/prod-approvals.txt",
)

# read only by steps of the gitleaks job that run unconditionally, or by scripts/precheck.sh; docs read by scripts/release must be listed in READ_BY_BUILDS
READ_BY_ALWAYS_RUN_CHECKS = (
    "docs/modernization/phase1a/infra/runbooks/sql/",
    "docs/modernization/build_all.py",
    "docs/modernization/check_legacy_name.py",
)


LEGACY_IT_PATHS = (
    "backend/src/main/java/com/forcitis/one/platform/integration/",
    "backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/",
    "backend/src/test/java/com/forcitis/one/platform/integration/",
    "backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/legacy/",
    "backend/src/test/java/com/forcitis/one/acceptance/platform/integration/",
    "backend/src/test/java/com/forcitis/one/TestImages.java",
    "backend/architecture/test-images.txt",
    "backend/architecture/seeyon-view-columns.yaml",
    "backend/pom.xml",
    "docs/modernization/phase1a/SEEYON_VIEWS.sql",
)
HANDOFF_SQL_DIR = "docs/modernization/phase1a/infra/handoff/B-5-dba/"
NULL_SHA = "0" * 40


def is_legacy_it(path):
    return path.startswith(LEGACY_IT_PATHS) or (path.startswith(HANDOFF_SQL_DIR) and path.endswith(".sql"))


def any_legacy_it(paths):
    return any(is_legacy_it(path) for path in paths if path)


def is_relevant(path):
    if path.startswith(READ_BY_BUILDS):
        return True
    if path.startswith("docs/"):
        return False
    return not ("/" not in path and path.endswith(".md"))


def changed_files(base):
    out = subprocess.run(["git", "diff", "--name-only", f"origin/{base}...HEAD"], capture_output=True, text=True, check=True)
    return out.stdout.splitlines()


def files_since(before):
    out = subprocess.run(["git", "diff", "--name-only", before, "HEAD"], capture_output=True, text=True)
    return out.stdout.splitlines() if out.returncode == 0 else None


SCANNED = ("backend/src", "frontend", "scripts")
SKIPPED_DIRS = {"node_modules", "dist", ".pnpm", "target"}
SOURCE_SUFFIXES = {".java", ".kt", ".ts", ".tsx", ".js", ".mjs", ".cjs", ".json", ".yaml", ".yml", ".py", ".sh", ".properties", ".xml", ".sql"}
LITERAL = re.compile(r"docs/modernization(?:/[\w.*{}-]+)*")
SEGMENTS = re.compile(r"""['"]docs['"]\s*,\s*['"]modernization['"]((?:\s*,\s*['"][\w.*-]+['"])*)""")


def is_covered(path):
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


def self_test():
    missing = uncovered_reads(Path(__file__).resolve().parents[2])
    assert not missing, "builds read docs paths that READ_BY_BUILDS does not list (add them in scripts/ci/relevant_change.py):\n  " + "\n  ".join(missing)
    assert is_relevant("backend/pom.xml")
    assert is_relevant(".github/workflows/ci.yml")
    assert is_relevant("scripts/ci/relevant_change.py")
    assert is_relevant("docs/modernization/glossary/terms.yaml")
    assert is_relevant("docs/modernization/events/asyncapi.yaml")
    assert is_relevant("docs/modernization/phase1a/SEEYON_VIEWS.sql")
    assert is_relevant("docs/modernization/status/blockers/080-1a-no-deploy-until-fix.md")
    assert not is_relevant("docs/modernization/adr/src/ADR-0001.md")
    assert not is_relevant("docs/modernization/phase1a/INTERFACES.md")
    assert not is_relevant("README.md")
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
    assert is_legacy_it("backend/architecture/seeyon-view-columns.yaml")
    assert is_legacy_it("backend/pom.xml")
    assert is_legacy_it("docs/modernization/phase1a/SEEYON_VIEWS.sql")
    assert is_legacy_it("docs/modernization/phase1a/infra/handoff/B-5-dba/read-all/a8_apply.sql")
    assert not is_legacy_it("docs/modernization/phase1a/infra/handoff/B-5-dba/README.md")
    assert not is_legacy_it("backend/src/main/java/com/forcitis/one/platform/integrationx/Other.java")
    assert not is_legacy_it("backend/src/main/java/com/forcitis/one/organization/workforce/WorkforceService.java")
    assert not is_legacy_it("backend/src/test/java/com/forcitis/one/organization/workforce/WorkforceServiceTest.java")
    assert not is_legacy_it("frontend/package.json")
    assert not is_legacy_it(".github/workflows/ci.yml")
    assert any_legacy_it(["README.md", "backend/pom.xml", ""])
    assert not any_legacy_it(["README.md", "", "backend/src/main/java/com/forcitis/one/organization/workforce/WorkforceService.java"])
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
    if len(sys.argv) == 3 and sys.argv[1] == "--paths-from":
        paths = Path(sys.argv[2]).read_text(encoding="utf-8").splitlines()
        print(flag("relevant", any_relevant(paths)))
        print(flag("legacy_it", any_legacy_it(paths)))
        return
    base = sys.argv[1] if len(sys.argv) > 1 else ""
    event = os.environ.get("GITHUB_EVENT_NAME")
    if event == "pull_request" and base:
        files = changed_files(base)
        relevant = any(is_relevant(f) for f in files)
        legacy_it = any_legacy_it(files)
        print(f"{len(files)} changed files, relevant={relevant}, legacy_it={legacy_it}")
    else:
        relevant = True
        before = os.environ.get("BEFORE", "")
        files = files_since(before) if event == "push" and before and before != NULL_SHA else None
        legacy_it = files is None or any_legacy_it(files)
    write_outputs([flag("relevant", relevant), flag("legacy_it", legacy_it)])


main()
