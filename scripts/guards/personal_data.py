#!/usr/bin/env python3
"""No personal data in the legacy inventories and the evidence folders (#1301; CLAUDE.md baseline 2: row-level and personal data stay out of the repo).

Scans every tracked text file under docs/legacy/runtime-usage/ and docs/data/evidence/. A failure names the rule, the file and the line, never the value.
  PD-1 a cell of a .tsv row names a person's attribute: gender (男/女) or an age (N岁); a template or form name is not a person
  PD-2 a mobile phone number (11 digits, 1[3-9]...)
  PD-3 a mainland identity number (18 characters with a valid birth date)
Prose and SQL may say 男/女 or 岁 in an aggregate ("2 values: 男、女"); only a .tsv cell is row-level. Redact the cell (replace the identifying part with a neutral
label such as 应聘登记表（个人模板，已去标识） and keep every count), or leave the row out of the inventory.

  personal_data.py              check the repository (run from its root)
  personal_data.py --self-test  a red case per rule
"""
import os
import re
import subprocess
import sys
import tempfile

FOLDERS = ("docs/legacy/runtime-usage/", "docs/data/evidence/")
ATTRIBUTE = re.compile(r"男|女|\d+\s*岁")
PHONE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
ID_NUMBER = re.compile(r"(?<!\d)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\d)")
FIX = "redact it in place (a neutral label, counts and other columns unchanged) or drop the row"


def tracked(root):
    out = subprocess.run(["git", "-C", root, "ls-files", "-z", "--", *FOLDERS], capture_output=True, check=True).stdout.decode()
    return [p for p in out.split("\0") if p]


def problems(root, files=None):
    found = []
    for rel in tracked(root) if files is None else files:
        path = os.path.join(root, rel)
        try:
            with open(path, encoding="utf-8") as f:
                lines = f.read().split("\n")
        except (OSError, UnicodeDecodeError):
            continue   # not a text file; nothing to read
        for number, line in enumerate(lines, 1):
            if rel.endswith(".tsv") and not line.startswith("#"):
                for column, cell in enumerate(line.split("\t"), 1):
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
        assert hit(d + "a.tsv", "# 说明：男、女 各一条的汇总\nid\n1\n") == [], "a comment line is prose"
        assert hit(FOLDERS[1] + "a.md", "性别 | 2 | 男、女 | 29 岁以下 17 人\n") == [], "an aggregate in prose is not row-level"
        assert hit(FOLDERS[1] + "a.md", "call 1" + "380000000" + "\n") == [], "ten digits is not a phone number"
        assert hit(FOLDERS[1] + "a.md", "call 1" + "38" + "00000000\n") == ["PD-2"], "an 11-digit mobile number"
        assert hit(FOLDERS[1] + "a.sql", "x = 1" + "380000000000\n") == [], "twelve digits is not a phone number"
        assert hit(FOLDERS[1] + "a.sql", "id " + "110101" + "1990" + "0101" + "001" + "X" + "\n") == ["PD-3"], "an identity number"
        assert hit(FOLDERS[1] + "a.sql", "id " + "110101" + "1990" + "1301" + "001" + "X" + "\n") == [], "an 18-digit id without a valid birth date (a system id)"
        message = problems(tmp, [d + "a.tsv"])[0] if hit(d + "a.tsv", "1\t模板甲（男）\n") else ""
        assert "模板甲" not in message and "fix:" in message, "a failure names file and line, never the value"
    print("personal_data self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    root = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
    found = problems(root)
    for message in found:
        print(message)
    print(f"personal_data: {len(found)} problem(s)")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
