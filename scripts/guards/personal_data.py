#!/usr/bin/env python3
"""No personal data in the legacy inventories and the evidence folders (#1301; CLAUDE.md baseline 2: row-level and personal data stay out of the repo).

Scans every tracked file under docs/legacy/runtime-usage/ and docs/data/evidence/. A failure names the rule, the file and the line, never the value.
  PD-0 a file that is not UTF-8 text or cannot be read: it cannot be scanned, so it fails (never skipped); convert it to UTF-8
  PD-1 a cell of a .tsv or .csv row names a person's attribute: gender (男/女) or an age (N岁); a template or form name is not a person
  PD-2 a mobile phone number (11 digits, 1[3-9]..., also written with spaces or hyphens)
  PD-3 a mainland identity number (18 characters with a valid birth date)
Prose and SQL may say 男/女 or 岁 in an aggregate ("2 values: 男、女"); only a .tsv or .csv cell is row-level. Comment lines (#) count only at the top of a file.
This check finds patterns, not people: a name without a gender or age word passes, so it does not replace reading what goes into these folders. Redact the cell (replace the identifying part with a neutral
label such as 应聘登记表（个人模板，已去标识） and keep every count), or leave the row out of the inventory.

  personal_data.py              check the repository (run from its root)
  personal_data.py --self-test  a red case per rule
"""
import contextlib
import io
import os
import re
import subprocess
import sys
import tempfile

FOLDERS = ("docs/legacy/runtime-usage/", "docs/data/evidence/")
ATTRIBUTE = re.compile(r"男|女|\d+\s*岁")
PHONE = re.compile(r"(?<!\d)1[3-9]\d[ -]?\d{4}[ -]?\d{4}(?!\d)")
ID_NUMBER = re.compile(r"(?<!\d)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\d)")
FIX = "redact it in place (a neutral label, counts and other columns unchanged) or drop the row"


def tracked(root):
    out = subprocess.run(["git", "-C", root, "ls-files", "-z", "--", *FOLDERS], capture_output=True, check=True).stdout.decode()
    return [p for p in out.split("\0") if p]


def problems(root, files=None):
    found = []
    for rel in tracked(root) if files is None else files:
        try:
            with open(os.path.join(root, rel), "rb") as f:
                data = f.read()
            if b"\0" in data:
                continue   # binary (an image, an archive): no text to read
            lines = data.decode("utf-8").split("\n")
        except (OSError, UnicodeDecodeError) as error:
            found.append(f"PD-0 {rel}: cannot be scanned ({type(error).__name__}). fix: make it readable UTF-8 text, or take it out of the folder")
            continue
        row_level = rel.endswith((".tsv", ".csv"))
        header = True   # leading comment lines are free; a "#" later in the file is a data row
        for number, line in enumerate(lines, 1):
            header = header and line.startswith("#")
            if row_level and not header:
                for column, cell in enumerate(re.split("\t" if rel.endswith(".tsv") else ",", line), 1):
                    if ATTRIBUTE.search(cell):
                        found.append(f"PD-1 {rel}:{number}: column {column} carries a gender or age. fix: {FIX}")
            if PHONE.search(line):
                found.append(f"PD-2 {rel}:{number}: a mobile phone number. fix: {FIX}")
            if ID_NUMBER.search(line):
                found.append(f"PD-3 {rel}:{number}: an identity number. fix: {FIX}")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def hit(name, text):
            path = os.path.join(tmp, name)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            return sorted({p.split()[0] for p in problems(tmp, [name])})

        d = FOLDERS[0]
        assert hit(d + "a.tsv", "id\tname\n1\t应聘登记表\n") == [], "a neutral row"
        assert hit(d + "a.tsv", "id\tname\n1\t表（个人模板，已去标识）\n") == [], "a redacted row"
        assert hit(d + "a.tsv", "id\tname\n1\t模板甲（男）\n") == ["PD-1"], "gender in a cell"
        assert hit(d + "a.tsv", "id\tname\n1\t模板甲 30岁\n") == ["PD-1"], "age in a cell"
        assert hit(d + "a.tsv", "id\tname\n1\t模板乙（女）\n") == ["PD-1"], "the other gender word"
        assert hit(d + "a.csv", "id,name\n1,模板丙（男）30岁\n") == ["PD-1"], "a csv row is row-level too"
        assert hit(d + "a.tsv", "# 说明：男、女 各一条的汇总\nid\n1\n") == [], "a comment line at the top is prose"
        assert hit(d + "a.tsv", "# 说明\nid\t名\n# 1\t模板丁（男）\n") == ["PD-1"], "a # row after the data started is a data row"
        assert hit(FOLDERS[1] + "a.md", "性别 | 2 | 男、女 | 29 岁以下 17 人\n") == [], "an aggregate in prose is not row-level"
        assert hit(FOLDERS[1] + "a.md", "call 1" + "380000000" + "\n") == [], "ten digits is not a phone number"
        assert hit(FOLDERS[1] + "a.md", "call 1" + "38" + "00000000\n") == ["PD-2"], "an 11-digit mobile number"
        assert hit(FOLDERS[1] + "a.md", "call 1" + "38-" + "0000-0000\n") == ["PD-2"], "a number written with hyphens"
        assert hit(FOLDERS[1] + "a.sql", "x = 1" + "380000000000\n") == [], "twelve digits is not a phone number"
        assert hit(FOLDERS[1] + "a.sql", "id " + "110101" + "1990" + "0101" + "001" + "X" + "\n") == ["PD-3"], "an identity number"
        assert hit(FOLDERS[1] + "a.sql", "id " + "110101" + "1990" + "1301" + "001" + "X" + "\n") == [], "an 18-digit id without a valid birth date (a system id)"
        path = os.path.join(tmp, d, "gbk.tsv")
        with open(path, "wb") as f:
            f.write("id\t名\n1\t模板（女）\n".encode("gb18030"))
        assert [p.split()[0] for p in problems(tmp, [d + "gbk.tsv"])] == ["PD-0"], "a file that is not UTF-8 fails instead of being skipped"
        assert [p.split()[0] for p in problems(tmp, [d + "missing.tsv"])] == ["PD-0"], "a tracked file that cannot be read fails"
        with open(os.path.join(tmp, d, "bin.dat"), "wb") as f:
            f.write(b"\x89PNG\0\xff\xfe")
        assert problems(tmp, [d + "bin.dat"]) == [], "binary data has no text to read"
        message = problems(tmp, [d + "a.tsv"])[0] if hit(d + "a.tsv", "1\t模板甲（男）\n") else ""
        assert "模板甲" not in message and "fix:" in message, "a failure names file and line, never the value"
    with tempfile.TemporaryDirectory() as repo:   # the command path: git ls-files over both folders, the exit code
        subprocess.run(["git", "init", "-q", repo], check=True)
        bad = "id\tname\n1\t模板甲（男）\n"
        for rel in (FOLDERS[0] + "x.tsv", FOLDERS[1] + "y.tsv", "docs/other/z.tsv"):
            os.makedirs(os.path.join(repo, os.path.dirname(rel)), exist_ok=True)
            with open(os.path.join(repo, rel), "w", encoding="utf-8") as f:
                f.write(bad)
        subprocess.run(["git", "-C", repo, "add", "-A"], check=True)
        assert sorted(tracked(repo)) == sorted([FOLDERS[0] + "x.tsv", FOLDERS[1] + "y.tsv"]), "both folders are listed, a file outside them is not"
        assert sorted(p.split()[1].split(":")[0] for p in problems(repo)) == sorted([FOLDERS[0] + "x.tsv", FOLDERS[1] + "y.tsv"]), "both folders are scanned"
        with contextlib.redirect_stdout(io.StringIO()):
            assert main(repo) == 1, "a violation makes the exit code 1"
        for rel in (FOLDERS[0] + "x.tsv", FOLDERS[1] + "y.tsv"):
            with open(os.path.join(repo, rel), "w", encoding="utf-8") as f:
                f.write("id\tname\n1\t模板甲\n")
        with contextlib.redirect_stdout(io.StringIO()):
            assert main(repo) == 0, "clean folders exit 0"
    print("personal_data self-test: ok")
    return 0


def main(root=None):
    if root is None:
        root = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
    found = problems(root)
    for message in found:
        print(message)
    print(f"personal_data: {len(found)} problem(s)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else main())
