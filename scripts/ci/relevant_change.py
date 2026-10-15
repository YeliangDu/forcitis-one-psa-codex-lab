#!/usr/bin/env python3
"""Decide whether a pull request touches anything the backend or frontend builds read. Writes relevant=true|false to
$GITHUB_OUTPUT. Everything is relevant except documentation: docs/ outside the paths the tests read, and root *.md.
--paths-from reads one path per line from a file instead of diffing (scripts/precheck.sh uses it so both gates agree).
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
    # ADR37-1 guards: the credential deny list (CredentialColumnGuardTest) and the runbook that must not list a check switch (ReadOnlyCheckSwitchGuardTest)
    "docs/modernization/phase1a/infra/handoff/B-5-dba/evidence/credential_columns.csv",
    "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md",
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


def is_relevant(path):
    if path.startswith(READ_BY_BUILDS):
        return True
    if path.startswith("docs/"):
        return False
    return not ("/" not in path and path.endswith(".md"))


def changed_files(base):
    out = subprocess.run(["git", "diff", "--name-only", f"origin/{base}...HEAD"], capture_output=True, text=True, check=True)
    return out.stdout.splitlines()


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


def any_relevant(paths):
    return any(is_relevant(path) for path in paths if path)


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    if len(sys.argv) == 3 and sys.argv[1] == "--paths-from":
        paths = Path(sys.argv[2]).read_text(encoding="utf-8").splitlines()
        print(f"relevant={'true' if any_relevant(paths) else 'false'}")
        return
    base = sys.argv[1] if len(sys.argv) > 1 else ""
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request" or not base:
        relevant = True
    else:
        files = changed_files(base)
        relevant = any(is_relevant(f) for f in files)
        print(f"{len(files)} changed files, relevant={relevant}")
    line = f"relevant={'true' if relevant else 'false'}"
    print(line)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as out:
            out.write(line + "\n")


main()
