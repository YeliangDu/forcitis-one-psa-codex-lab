"""Production evidence tools: every import must resolve without running anything.

#748 first review: deleting b5_legacy_run.py and two siblings left b5_evidence_scan.py crashing with ModuleNotFoundError,
and nothing in CI noticed, because the tools only run against production. This reads the imports statically (ast, no
execution, no credentials). A non-stdlib import is fine when it is a module in one of DIRS or a pinned third-party SDK.
Remove this check when the evidence tools move under tools/ with their own self-test (#742 second PR).

  evidence_imports.py              check the repository
  evidence_imports.py --self-test  a deleted sibling turns it red
"""
import ast
import os
import shutil
import sys
import tempfile

DIRS = (
    "docs/modernization/phase1a/infra/handoff/B-5-dba/tools",
    "docs/modernization/phase1a/infra/runbooks/scripts",
    "scripts/evidence",
)
THIRD_PARTY = {"alibabacloud_ecs20140526", "alibabacloud_tea_openapi"}


def problems(root):
    local = {f[:-3] for d in DIRS if os.path.isdir(os.path.join(root, d)) for f in os.listdir(os.path.join(root, d)) if f.endswith(".py")}
    found = []
    for d in DIRS:
        folder = os.path.join(root, d)
        for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else ():
            if not name.endswith(".py"):
                continue
            tree = ast.parse(open(os.path.join(folder, name), encoding="utf-8").read(), name)
            for node in ast.walk(tree):
                if isinstance(node, ast.Import):
                    mods = [a.name.split(".")[0] for a in node.names]
                elif isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
                    mods = [node.module.split(".")[0]]
                else:
                    continue
                for mod in mods:
                    if mod not in sys.stdlib_module_names and mod not in local and mod not in THIRD_PARTY:
                        found.append(f"{d}/{name}:{node.lineno} imports {mod}, which is neither in {', '.join(DIRS)} nor a pinned SDK")
    return found


def self_test(root):
    with tempfile.TemporaryDirectory() as tmp:
        for d in DIRS:
            shutil.copytree(os.path.join(root, d), os.path.join(tmp, d))
        assert problems(tmp) == [], problems(tmp)
        os.remove(os.path.join(tmp, DIRS[0], "b5_read_all_run.py"))
        assert any("imports b5_read_all_run" in p for p in problems(tmp)), "a deleted sibling module must be reported"
    print("evidence_imports self-test: ok")


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if sys.argv[1:] == ["--self-test"]:
        self_test(root)
    else:
        bad = problems(root)
        print("\n".join(bad) or f"evidence imports: all resolve ({', '.join(DIRS)})")
        sys.exit(1 if bad else 0)
