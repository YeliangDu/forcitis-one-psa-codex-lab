"""Production evidence tools: every import and every literal sibling-file read must resolve without running anything.

#748 first review: deleting b5_legacy_run.py and two siblings left b5_evidence_scan.py crashing with ModuleNotFoundError,
and nothing in CI noticed, because the tools only run against production. This reads the imports statically (ast, no
execution, no credentials). A non-stdlib import is fine when it is a module in one of DIRS or a pinned third-party SDK.
#748 final review: the restored modules also read set_env.py as a file (TOOLS / 'set_env.py') and would have crashed
after changing production. So a path built from BASES and string literals (TOOLS / 'x', B5 / 'read-all' / 'y') must exist.
#742: the executed B-5 write runners are deleted; their shared channel code lives in b5_remote.py for b5_evidence_scan.py.
Keep this check while the tools only run against production.

  evidence_imports.py              check the repository
  evidence_imports.py --self-test  a deleted sibling module or read file turns it red
"""
import ast
import os
import shutil
import sys
import tempfile

DIRS = (
    "tools/ops/dba",
    "tools/ops/ecs/scripts",
    "tools/evidence",
)
THIRD_PARTY = {"alibabacloud_ecs20140526", "alibabacloud_tea_openapi"}
BASES = {"TOOLS": ".", "SCRIPTS": ".", "B5": ".."}  # module-level Path names in the tools, relative to the file's folder


def literal_path(node):
    """BASES name / 'a' / 'b' -> ['..', 'a', 'b'] style parts; None for anything not fully literal."""
    if isinstance(node, ast.Name) and node.id in BASES:
        return [BASES[node.id]]
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Div) and isinstance(node.right, ast.Constant) and isinstance(node.right.value, str):
        left = literal_path(node.left)
        return None if left is None else left + [node.right.value]
    return None


def top_level_names(path):
    """Names a module defines at top level (functions, classes, assignments, imports)."""
    names = set()
    for node in ast.parse(open(path, encoding="utf-8").read(), path).body:
        if isinstance(node, (ast.FunctionDef, ast.ClassDef)):
            names.add(node.name)
        elif isinstance(node, ast.Assign):
            names.update(t.id for t in node.targets if isinstance(t, ast.Name))
        elif isinstance(node, (ast.Import, ast.ImportFrom)):
            names.update((a.asname or a.name).split(".")[0] for a in node.names)
    return names


def problems(root):
    paths = {f[:-3]: os.path.join(root, d, f) for d in DIRS if os.path.isdir(os.path.join(root, d)) for f in os.listdir(os.path.join(root, d)) if f.endswith(".py")}
    local = set(paths)
    found = []
    for d in DIRS:
        folder = os.path.join(root, d)
        for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else ():
            if not name.endswith(".py"):
                continue
            tree = ast.parse(open(os.path.join(folder, name), encoding="utf-8").read(), name)
            for node in ast.walk(tree):
                parts = literal_path(node) if isinstance(node, ast.BinOp) else None
                if parts and len(parts) > 1 and not os.path.exists(os.path.join(folder, *parts)):
                    found.append(f"{d}/{name}:{node.lineno} reads {'/'.join(parts[1:])}, which does not exist next to it")
                    continue
                if isinstance(node, ast.Import):
                    mods = [a.name.split(".")[0] for a in node.names]
                elif isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
                    mods = [node.module.split(".")[0]]
                    if node.module in local:  # a helper moved or renamed in a sibling: the name must still be there
                        missing = sorted({a.name for a in node.names} - top_level_names(paths[node.module]))
                        found += [f"{d}/{name}:{node.lineno} imports {m} from {node.module}, which does not define it" for m in missing]
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
        remote = os.path.join(tmp, DIRS[0], "b5_remote.py")
        open(remote, "w", encoding="utf-8").write(open(remote, encoding="utf-8").read().replace("def key_pair(", "def key_pair_renamed("))
        assert any("imports key_pair from b5_remote" in p for p in problems(tmp)), "a name no longer defined by a sibling module must be reported"
        os.remove(os.path.join(tmp, DIRS[0], "b5_remote.py"))
        assert any("imports b5_remote" in p for p in problems(tmp)), "a deleted sibling module must be reported"
        os.remove(os.path.join(tmp, DIRS[0], "OneSql.java"))
        assert any("reads OneSql.java" in p for p in problems(tmp)), "a deleted sibling file read by TOOLS / '...' must be reported"
    print("evidence_imports self-test: ok")


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if sys.argv[1:] == ["--self-test"]:
        self_test(root)
    else:
        bad = problems(root)
        print("\n".join(bad) or f"evidence imports: all resolve ({', '.join(DIRS)})")
        sys.exit(1 if bad else 0)
