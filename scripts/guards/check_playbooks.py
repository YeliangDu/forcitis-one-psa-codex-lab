#!/usr/bin/env python3
"""Playbooks and the card template stay short, complete and consistent (lead decision, #742 PR 3).

Each failure names the rule, the file and the fix:
  P1 docs/rules/playbooks/<type>.md is at most policy.json start.playbook_max_kb (a session reads it whole at start)
  P2 its first line is `# 剧本：<类型>` or `# Playbook: <type>`
  P3 it has a 「回报必含：」 or `Report must include:` section
  P4 every repository path it names in backticks exists
  P5 it has no wording, Chinese or English, that lets a session decide for the user
  C1 .github/ISSUE_TEMPLATE/card.md keeps its seven sections
  C2 the type list in the card's 类型 section equals the playbook files
  P6 every repository path named in backticks in a CLAUDE.md, rules.md, a role file, PROMPT.md, the PR template, or the docstring
     (leading comment for shell) of a script under scripts/ exists, relative to the repository or to the file (#856)
Runs on every event: a deleted or renamed file breaks P4 wherever it happens.

  check_playbooks.py              check the repository (run from its root)
  check_playbooks.py --self-test  a red case per rule
"""
import json
import os
import re
import sys
import tempfile

PLAYBOOKS = "docs/rules/playbooks"
CARD = ".github/ISSUE_TEMPLATE/card.md"
SECTIONS = ("类型", "目标", "完成标准", "文件范围", "依据", "消费方", "不得做")
PATH = re.compile(r"(?:\.?[\w-]+/)+[\w.-]*")  # a path with a folder; a bare file name (mq.py) says nothing about where
GRANTS = ("替用户决定", "替用户拍板", "代用户决定", "代替用户决定", "自行拍板", "无须用户确认", "无需用户确认", "不必问用户", "不用问用户", "不必请示用户")
NEGATIONS = ("不", "勿", "别", "禁止", "不得", "不要")
USER = r"(?:the\s+)?users?"
GRANTS_EN = (  # (pattern, whether a negation right before it makes it a prohibition)
    (rf"\bdecid(?:e|es|ed|ing)\s+(?:for\s+{USER}|on\s+behalf\s+of\s+{USER}|on\s+{USER}'s?\s+behalf)\b", True),   # 替用户决定
    (rf"\bmak(?:e|es|ing)\s+(?:the\s+)?(?:call|decision)s?\s+for\s+{USER}\b", True),                               # 替用户拍板
    (r"\b(?:decide|settle)\s+(?:it\s+)?(?:yourself|on\s+your\s+own)\b", True),                                   # 自行拍板
    (rf"\bwithout\s+(?:asking|consulting|checking\s+with)\s+{USER}\b", True),                                    # 不必问用户
    (rf"\bwithout\s+(?:{USER}'s?\s+|user\s+)(?:confirmation|approval|consent)\b", True),                          # 无须用户确认
    (rf"\b(?:no\s+need\s+to|need\s+not)\s+(?:ask|consult|check\s+with)\s+{USER}\b", False),                    # 不必请示用户
    (r"\bno\s+user\s+(?:confirmation|approval)\b", False),
)
MODAL = r"(?:may|can|shall|should|must|will|would)"
# Only a negation directly before the phrase on the same line makes it a prohibition ("never decide for the user", "No session may decide
# for the user"); a "not" earlier in a condition ("If lead is not available decide for the user") does not. "without ..." may follow one
# action verb ("do not merge without asking the user").
NEGATION_EN = re.compile(rf"(?:\b(?:not|never)|n't|\bnobody|\bno\s+one|\bno\s+\w+\s+{MODAL})[ \t]+(?:ever[ \t]+)?(?:{MODAL}[ \t]+)?"
                         r"(?:(?:merge|push|act|proceed|deploy|ship|approve|enqueue|release|change|continue)[ \t]+)?$", re.I)


def named_paths(text):
    """Repository paths named in backticks; placeholders (<...>), home paths and option-like words are left out."""
    for code in re.findall(r"`([^`]+)`", text):
        for word in code.split():
            if "<" in word or word.startswith(("~", "-", "$", "http")):
                continue
            word = re.sub(r"^\./", "", re.sub(r"(:\d+(-\d+)?|#\S*)$", "", word.rstrip(".,;:)）、，。；")))  # path:12, path#anchor, trailing punctuation
            if PATH.fullmatch(word):
                yield word


def max_bytes(root):
    """policy.json start.playbook_max_kb in bytes (1 KB = 1024)."""
    return int(json.load(open(os.path.join(root, "policy.json"), encoding="utf-8"))["start"]["playbook_max_kb"] * 1024)


def playbook_problems(root, name):
    path = os.path.join(root, PLAYBOOKS, name)
    rel = f"{PLAYBOOKS}/{name}"
    raw = open(path, "rb").read()
    text = raw.decode("utf-8")
    found = []
    if len(raw) > max_bytes(root):
        found.append(f"P1 {rel}: {len(raw)} bytes, over start.playbook_max_kb ({max_bytes(root)}); cut it to the steps a session needs")
    first = text.split("\n", 1)[0]
    if not re.fullmatch(r"# 剧本：\S.*|# Playbook: \S.*", first):
        found.append(f"P2 {rel}:1: first line is {first!r}; make it `# Playbook: <type>` (or `# 剧本：<类型>`)")
    if not re.search(r"^(?:回报必含[：:]|Report must include:)", text, re.M):
        found.append(f"P3 {rel}: no `Report must include:` (or 「回报必含：」) section; add one listing what the report must contain")
    for word in named_paths(text):
        if not os.path.exists(os.path.join(root, word.rstrip("/"))):
            found.append(f"P4 {rel}: names `{word}`, which does not exist; fix the path or drop it")
    for phrase in GRANTS:
        for m in re.finditer(re.escape(phrase), text):
            if not text[max(0, m.start() - 2):m.start()].endswith(NEGATIONS):
                line = text.count("\n", 0, m.start()) + 1
                found.append(f"P5 {rel}:{line}: 「{phrase}」 lets a session decide for the user; business choices go to the user through lead")
    english = text.replace("\u2019", "'")
    for pattern, negatable in GRANTS_EN:
        for m in re.finditer(pattern, english, re.I):
            before = english[english.rfind("\n", 0, m.start()) + 1:m.start()]
            if not (negatable and NEGATION_EN.search(before)):
                line = english.count("\n", 0, m.start()) + 1
                found.append(f"P5 {rel}:{line}: \"{m.group(0)}\" lets a session decide for the user; business choices go to the user through lead")
    return found


def card_problems(root, types):
    path = os.path.join(root, CARD)
    if not os.path.exists(path):
        return [f"C1 {CARD}: missing; restore the card template"]
    text = open(path, encoding="utf-8").read()
    headings = re.findall(r"^## (.+?)\s*$", text, re.M)
    found = [f"C1 {CARD}: section 「## {s}」 is missing; restore it" for s in SECTIONS if s not in headings]
    block = re.search(r"^## 类型\s*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    choice = re.search(r"选一：(.*?)。", block.group(1)) if block else None
    listed = set(re.findall(r"\b([a-z]+) [一-鿿]", choice.group(1))) if choice else set()
    if listed != types:
        found.append(f"C2 {CARD}: 类型 lists {sorted(listed)}, the playbooks are {sorted(types)}; make the list match {PLAYBOOKS}/")
    return found


DOC_FILES = ("docs/rules/rules.md", "scripts/review/PROMPT.md", ".github/pull_request_template.md")


def tracked(root):
    """Tracked files (git ls-files); without git (the self-test's folder), every file under root."""
    if os.path.isdir(os.path.join(root, ".git")) or os.path.isfile(os.path.join(root, ".git")):
        import subprocess
        return subprocess.run(["git", "-C", root, "ls-files"], capture_output=True, text=True, check=True).stdout.split("\n")[:-1]
    return [os.path.relpath(os.path.join(d, f), root) for d, _, fs in os.walk(root) for f in fs]


def leading_text(rel, text):
    """The part of a script that documents it: a Python module docstring, or a shell script's leading # comment block."""
    if rel.endswith(".py"):
        import ast
        try:
            return ast.get_docstring(ast.parse(text)) or ""
        except SyntaxError:
            return ""
    lines = text.split("\n")
    block = []
    for line in lines[1:] if lines and lines[0].startswith("#!") else lines:
        if not line.startswith("#"):
            break
        block.append(line)
    return "\n".join(block)


def doc_path_problems(root):
    found = []
    for rel in tracked(root):
        doc = (os.path.basename(rel) == "CLAUDE.md" or rel in DOC_FILES
               or (rel.startswith("docs/rules/roles/") and rel.endswith(".md")))
        script = rel.startswith("scripts/") and rel.endswith((".py", ".sh"))
        if not (doc or script):
            continue
        path = os.path.join(root, rel)
        if not os.path.isfile(path):
            continue
        text = open(path, encoding="utf-8").read()
        text = text if doc else leading_text(rel, text)
        here = os.path.dirname(path)
        for word in named_paths(text):
            word = word.rstrip("/")
            if not (os.path.exists(os.path.join(root, word)) or os.path.exists(os.path.join(here, word))):
                found.append(f"P6 {rel}: names `{word}`, which does not exist (from the repository root or from the file); fix the path or drop it")
    return found


def problems(root):
    names = sorted(n for n in os.listdir(os.path.join(root, PLAYBOOKS)) if n.endswith(".md"))
    found = [p for n in names for p in playbook_problems(root, n)]
    return found + card_problems(root, {n[:-3] for n in names}) + doc_path_problems(root)


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        os.makedirs(os.path.join(tmp, PLAYBOOKS))
        os.makedirs(os.path.join(tmp, "scripts/ci"))
        json.dump({"start": {"playbook_max_kb": 2}}, open(os.path.join(tmp, "policy.json"), "w"))
        MAX_BYTES = max_bytes(tmp)
        open(os.path.join(tmp, "scripts/ci/light.sh"), "w").close()
        book = os.path.join(tmp, PLAYBOOKS, "ops.md")
        good = "# 剧本：运维\n1. 跑 `scripts/ci/light.sh` 与 `git worktree list`；`~/.x/y.md`、`docs/<卡>.md` 不核。\n2. 不替用户拍板。\n\n回报必含：\n- 卡号\n"
        card = os.path.join(tmp, CARD)
        os.makedirs(os.path.dirname(card))
        good_card = "---\nname: 卡\n---\n## 类型\n<!-- 选一：ops 运维。按 playbooks/<类型>.md 做，docs 先读。 -->\n\n" + "".join(f"## {s}\n\n" for s in SECTIONS[1:])

        def rules(text=good, card_text=good_card):
            open(book, "w", encoding="utf-8").write(text)
            open(card, "w", encoding="utf-8").write(card_text)
            return sorted({p.split()[0] for p in problems(tmp)})

        assert rules() == [], problems(tmp)
        assert rules(good + "x" * MAX_BYTES) == ["P1"]
        pad = MAX_BYTES - len(good.encode("utf-8"))
        assert rules(good + "x" * pad) == [] and rules(good + "x" * (pad + 1)) == ["P1"], "the limit is exactly start.playbook_max_kb"
        json.dump({"start": {"playbook_max_kb": 1}}, open(os.path.join(tmp, "policy.json"), "w"))
        assert rules(good + "x" * (1024 - len(good.encode("utf-8")) + 1)) == ["P1"], "the limit follows policy.json"
        json.dump({"start": {"playbook_max_kb": 2}}, open(os.path.join(tmp, "policy.json"), "w"))
        for named in ("scripts/ci/gone.py:12", "./scripts/ci/gone.py", "scripts/ci/gone.py#L3", "scripts/ci/gone.py，"):
            assert rules(good + f"3. 见 `{named}`。\n") == ["P4"], named
        assert rules(good + "3. 见 `scripts/ci/light.sh:12` 与 `./scripts/ci/light.sh`。\n") == []
        assert rules(good.replace("# 剧本：运维", "# 运维剧本")) == ["P2"]
        assert rules(good.replace("回报必含：", "回报：")) == ["P3"]
        assert rules(good.replace("scripts/ci/light.sh", "scripts/ci/gone.sh")) == ["P4"]
        assert rules(good + "3. 小事可替用户决定。\n") == ["P5"] and rules(good + "3. 无须用户确认即可合入。\n") == ["P5"]
        english = "# Playbook: ops\n1. Run `scripts/ci/light.sh`.\n2. Never decide for the user; do not merge without user confirmation.\n\nReport must include:\n- card\n"
        assert rules(english) == [], problems(tmp)
        assert rules(english.replace("# Playbook: ops", "# Ops playbook")) == ["P2"]
        assert rules(english.replace("Report must include:", "Report:")) == ["P3"]
        for grant in ("3. Small calls: decide for the user.\n", "3. Merge without asking the user.\n", "3. No need to ask the user.\n",
                      "3. You may Decide On The User's Behalf.\n", "3. If lead is not available decide for the user.\n",
                      "3. When lead does not answer decide for the user.\n", "3. If there is no reply decide for the user.\n",
                      "3. Lead says no\ndecide for the user.\n", "3. Wait or not\ndecide for the user.\n", "3. Do not wait; you may decide for the user.\n", "3. Never mind, decide for the user.\n",
                      "3. Keep deciding for the user.\n", "3. Decide on behalf of the user.\n", "3. Decide for users.\n",
                      "3. Merge without the user's confirmation.\n", "3. Decide on the user\u2019s behalf.\n", "3. Make the call for the user.\n",
                      "3. Settle it yourself.\n", "3. The user need not be asked; need not ask the user.\n", "3. No user confirmation needed.\n",
                      "3. Do not stop to think, merge without asking the user.\n"):
            assert rules(english + grant) == ["P5"], grant
        for prohibition in ("3. Never decide for the user.\n", "3. Do not decide for the user.\n", "3. You must not ever decide for the user.\n",
                            "3. No session may decide for the user.\n", "3. Nobody decides for the user.\n", "3. Don't merge without asking the user.\n",
                            "3. Never make the call for the user.\n"):
            assert rules(english + prohibition) == [], prohibition
        assert rules(english + "3. You must not, ever, decide for the user.\n") == ["P5"], "a negation far back does not cover the phrase"
        assert rules(english.replace("Report must include:", "回报必含：")) == [] and rules(good.replace("# 剧本：运维", "# Playbook: ops")) == [], "the two languages mix"

        assert rules(card_text=good_card.replace("## 消费方\n", "")) == ["C1"]
        assert rules(card_text=good_card.replace("ops 运维", "ops 运维 / impl 实现")) == ["C2"]
        open(os.path.join(tmp, PLAYBOOKS, "impl.md"), "w", encoding="utf-8").write(good)
        assert rules() == ["C2"], "a playbook the card does not list"
        os.remove(os.path.join(tmp, PLAYBOOKS, "impl.md"))
        rules(good.replace("light.sh", "gone.sh"))
        assert "fix the path" in problems(tmp)[0], "a failure says how to fix it"
        docs = {"CLAUDE.md": "See `scripts/ci/light.sh`, `~/.x`, `docs/<card>.md`.\n",
                "backend/CLAUDE.md": "Migrations in `src/db`.\n", "backend/src/db/V1.sql": "",
                "docs/rules/roles/dev.md": "Use `scripts/ci/light.sh`.\n",
                "scripts/x/tool.py": '"""Reads `scripts/ci/light.sh`."""\nX = "`scripts/gone.py` in code is no docstring"\n',
                "scripts/x/run.sh": "#!/bin/sh\n# Calls `scripts/ci/light.sh`.\necho '`scripts/gone.sh` after the header'\n"}
        for rel, text in docs.items():
            os.makedirs(os.path.dirname(os.path.join(tmp, rel)) or tmp, exist_ok=True)
            open(os.path.join(tmp, rel), "w", encoding="utf-8").write(text)
        assert rules() == [], "paths from the root or from the file; placeholders, home paths and script bodies are not checked"
        for rel, stale in (("CLAUDE.md", "See `scripts/ci/gone.sh`.\n"), ("backend/CLAUDE.md", "See `src/gone`.\n"),
                           ("docs/rules/roles/dev.md", "Use `docs/gone.md`.\n"), ("scripts/x/tool.py", '"""Reads `scripts/gone.py`."""\n'),
                           ("scripts/x/run.sh", "#!/bin/sh\n# Calls `scripts/gone.sh`.\n")):
            open(os.path.join(tmp, rel), "w", encoding="utf-8").write(stale)
            assert rules() == ["P6"], f"a stale path in {rel}"
            open(os.path.join(tmp, rel), "w", encoding="utf-8").write(docs[rel])
    print("check_playbooks self-test: ok")


if __name__ == "__main__":
    root = os.getcwd()
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(root)
    for p in found:
        print(p)
    print(f"check_playbooks: {len(found)} problems")
    sys.exit(1 if found else 0)
