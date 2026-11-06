"""What kind of change a pull request is, from the paths it changes (#1562): the one classification that the enqueue checks (mq_records.py) and the daily mix report (pr_mix.py) share.
Pure text: no gh, no git.
Records: every path is under docs/decisions/ or docs/rules/rulings/. Rules: any path is one of the rule files. The other kinds go by the first that applies: runtime (any path is backend/src/main or frontend source that is not a test), scripts (any path is a script, tool, CI, hook or machine-read config), test (any path is a test), docs (the rest)."""
import re

RECORDS = ("docs/decisions/", "docs/rules/rulings/")
RULE_FILES = ("CLAUDE.md", "backend/CLAUDE.md", "docs/rules/rules.md")
RULE_DIRS = ("docs/rules/roles/", "docs/rules/playbooks/")
CHECK_DIRS = ("scripts/gates/", "scripts/guards/", "scripts/hooks/")
SCRIPT_PREFIXES = ("scripts/", "tools/", ".github/", ".claude/", ".design-sync/", "frontend/scripts/", "backend/architecture/", "backend/glossary/", "backend/.mvn/")
SCRIPT_FILES = ("policy.json", ".pre-commit-config.yaml", ".gitignore", ".gitattributes")
TEST_PART = re.compile(r"(/src/test/|/__tests__/|/tests?/|/e2e/|\.test\.|\.spec\.|(Test|IT)\.java$)")
SELF_TEST = re.compile(r"(selftest|_test|_cases)\.[A-Za-z0-9]+$|^test_")   # a self-test or the cases it reads: not a check file itself
KINDS = ("runtime", "test", "scripts", "docs", "records", "rules")


def is_records(paths):
    return bool(paths) and all(p.startswith(RECORDS) for p in paths)


def is_rule(paths):
    return any(p in RULE_FILES or p.startswith(RULE_DIRS) for p in paths)


def is_runtime(path):
    if path.startswith("backend/src/main/"):
        return True
    return path.startswith(("frontend/apps/", "frontend/packages/")) and not TEST_PART.search(path)


def is_check_file(path):
    """A file of a gate, guard or hook, not its self-test."""
    return path.startswith(CHECK_DIRS) and not SELF_TEST.search(path.rsplit("/", 1)[-1])


def kind(paths):
    """One of KINDS for the changed paths (renamed-from paths included by the caller); a pull request with no files is docs."""
    if is_records(paths):
        return "records"
    if is_rule(paths):
        return "rules"
    if any(is_runtime(p) for p in paths):
        return "runtime"
    if any(p.startswith(SCRIPT_PREFIXES) or p in SCRIPT_FILES for p in paths):
        return "scripts"
    if any(TEST_PART.search(p) for p in paths):
        return "test"
    return "docs"


def is_backend_runtime(paths):
    return any(p.startswith("backend/src/main/") for p in paths)
