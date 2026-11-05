#!/usr/bin/env python3
"""Every tracked code file is at most 1000 lines (#810 item 5; scripts/review/PROMPT.md makes a longer code file a must-fix).
A code file has one of SUFFIXES, or no suffix at all and is a script (executable in git, or starting with #!).

Files over the limit when this check arrived are listed in OVER with their line count and reason; each may only shrink, and its entry
goes when it is back under the limit. Lines are counted as an editor shows them: newlines, plus one for a last line without one.

Each failure names the rule, the file and the fix:
  FL-1 a code file not in OVER is over the limit
  FL-2 a file in OVER grew past its registered count
  FL-3 an OVER entry is stale: the file is gone, under the limit, or shorter than registered

  file_lines.py              check the repository (run from its root)
  file_lines.py --self-test  a red case per rule
"""
import os
import subprocess
import sys
import tempfile

MAX_LINES = 1000
SUFFIXES = (".java", ".kt", ".ts", ".tsx", ".mts", ".cts", ".js", ".jsx", ".mjs", ".cjs", ".py", ".sh", ".sql")
OVER = {  # path -> (registered lines, reason); shrink only
    "backend/src/test/java/com/forcitis/one/acceptance/platform/integration/ReadOnlyVerificationCommandIT.java":
        (1424, "acceptance test: only the test engineer splits it (CLAUDE.md baseline 4)"),
    "scripts/release/remote_install.sh": (1083, "release script on a guarded path: split in its own reviewed PR"),
    "backend/src/test/java/com/forcitis/one/DataArchitectureTest.java": (1054, "guard test: split by rule family in its own PR"),
    "backend/src/test/java/com/forcitis/one/DdlNamingTest.java": (1002, "guard test: split by rule family in its own PR"),
}


def lines(path):
    with open(path, "rb") as f:
        data = f.read()
    return data.count(b"\n") + (1 if data and not data.endswith(b"\n") else 0)


def is_script(root, rel, mode):
    """No suffix, and executable in git or starting with #!."""
    if "." in os.path.basename(rel):
        return False
    if mode == "100755":
        return True
    try:
        with open(os.path.join(root, rel), "rb") as f:
            return f.read(2) == b"#!"
    except OSError:
        return False


def tracked(root):
    out = subprocess.run(["git", "-C", root, "ls-files", "-s", "-z"], capture_output=True, check=True).stdout.decode()
    files = []
    for entry in out.split("\0")[:-1]:
        meta, rel = entry.split("\t", 1)
        mode = meta.split()[0]
        if mode != "120000" and (rel.endswith(SUFFIXES) or is_script(root, rel, mode)):
            files.append(rel)
    return files


def problems(root, files=None, over=OVER, limit=MAX_LINES):
    found = []
    files = tracked(root) if files is None else files
    for rel in files:
        path = os.path.join(root, rel)
        if os.path.islink(path) or not os.path.isfile(path):
            if rel in over:
                found.append(f"FL-3 {rel}: listed in OVER but not in the working tree. fix: delete its OVER entry")
            continue
        n = lines(path)
        if rel in over:
            registered = over[rel][0]
            if n > registered:
                found.append(f"FL-2 {rel}: {n} lines, more than the {registered} registered in OVER (shrink only)."
                             f" fix: move code out (a new class or module) instead of adding to it")
            elif n <= limit:
                found.append(f"FL-3 {rel}: {n} lines, back within {limit}. fix: delete its OVER entry in scripts/guards/file_lines.py")
            elif n < registered:
                found.append(f"FL-3 {rel}: {n} lines, shorter than the {registered} registered. fix: lower its OVER entry to {n}")
        elif n > limit:
            found.append(f"FL-1 {rel}: {n} lines, over {limit}. fix: split it by responsibility into files of at most {limit} lines")
    for rel in over:
        if rel not in files:
            found.append(f"FL-3 {rel}: listed in OVER but not a tracked code file. fix: delete its OVER entry")
    return found


def tracked_like(found):
    return [p.split()[1].rstrip(":") for p in found]


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, n):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w").write("x\n" * n)

        def hit(files, over):
            return sorted({p.split()[0] for p in problems(tmp, files, over, limit=10)})

        write("a/A.java", 10)
        write("a/big.sh", 12)
        assert hit(["a/A.java"], {}) == [], "at the limit is fine"
        write("a/A.java", 11)
        assert hit(["a/A.java"], {}) == ["FL-1"], "one over"
        assert hit(["a/big.sh"], {"a/big.sh": (12, "r")}) == [], "a registered file at its count"
        assert hit(["a/big.sh"], {"a/big.sh": (11, "r")}) == ["FL-2"], "a registered file that grew"
        assert "lower its OVER entry to 12" in problems(tmp, ["a/big.sh"], {"a/big.sh": (13, "r")}, limit=10)[0], "shrank: lower the entry"
        write("a/big.sh", 9)
        assert "delete its OVER entry" in problems(tmp, ["a/big.sh"], {"a/big.sh": (12, "r")}, limit=10)[0], "back within the limit: drop the entry"
        assert hit([], {"a/gone.py": (12, "r")}) == ["FL-3"], "an entry for a file git no longer tracks"
        assert "not in the working tree" in problems(tmp, ["a/deleted.py"], {"a/deleted.py": (12, "r")}, limit=10)[0], \
            "an entry for a tracked file deleted from the working tree is reported, not skipped"
        open(os.path.join(tmp, "a/A.java"), "w").write("x\n" * 10 + "last")
        assert hit(["a/A.java"], {}) == ["FL-1"], "a last line without a newline counts"
        subprocess.run(["git", "init", "-q", tmp], check=True)
        os.makedirs(os.path.join(tmp, "bin"))
        for name, head in (("tool", "#!/bin/sh\n"), ("run", ""), ("notes", "")):
            open(os.path.join(tmp, "bin", name), "w").write(head + "x\n" * 11)
        os.chmod(os.path.join(tmp, "bin", "run"), 0o755)
        subprocess.run(["git", "-C", tmp, "add", "bin"], check=True)
        assert sorted(f for f in tracked(tmp) if f.startswith("bin/")) == ["bin/run", "bin/tool"], \
            "an extensionless script counts (#! or executable); other extensionless files do not"
        assert "fix: split it" in problems(tmp, ["a/A.java"], {}, limit=10)[0], "a failure says how to fix it"
        assert all(p.endswith(SUFFIXES) for p in OVER), "OVER lists only checked suffixes"
        for suffix in SUFFIXES:
            write("b/f" + suffix, 11)
        files = ["b/f" + suffix for suffix in SUFFIXES]
        assert sorted(tracked_like(problems(tmp, files, {}, limit=10))) == sorted(files), "every suffix is checked"
        for suffix in (".java", ".ts", ".mts", ".sql", ".jsx"):
            assert suffix in SUFFIXES, f"{suffix} is a code file"
    print("file_lines self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(os.getcwd())
    for p in found:
        print(p)
    print(f"file_lines: {len(found)} problems")
    sys.exit(1 if found else 0)
