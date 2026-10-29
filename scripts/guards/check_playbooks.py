#!/usr/bin/env python3
"""Playbooks and the card template stay short, complete and consistent (lead decision, #742 PR 3).

Each failure names the rule, the file and the fix:
  P1 docs/modernization/playbooks/<type>.md is at most 2048 bytes (a session reads it whole at start)
  P2 its first line is `# 剧本：<类型>`
  P3 it has a 「回报必含」 section
  P4 every repository path it names in backticks exists
  P5 it has no wording that lets a session decide for the user
  C1 .github/ISSUE_TEMPLATE/card.md keeps its seven sections
  C2 the type list in the card's 类型 section equals the playbook files
Runs on every event: a deleted or renamed file breaks P4 wherever it happens.

  check_playbooks.py              check the repository (run from its root)
  check_playbooks.py --self-test  a red case per rule
"""
import os
import re
import sys
import tempfile

PLAYBOOKS = "docs/modernization/playbooks"
CARD = ".github/ISSUE_TEMPLATE/card.md"
MAX_BYTES = 2048
SECTIONS = ("类型", "目标", "完成标准", "文件范围", "依据", "消费方", "不得做")
PATH = re.compile(r"(?:\.?[\w-]+/)+[\w.-]*")  # a path with a folder; a bare file name (mq.py) says nothing about where
GRANTS = ("替用户决定", "替用户拍板", "代用户决定", "代替用户决定", "自行拍板", "无须用户确认", "无需用户确认", "不必问用户", "不用问用户", "不必请示用户")
NEGATIONS = ("不", "勿", "别", "禁止", "不得", "不要")


def named_paths(text):
    """Repository paths named in backticks; placeholders (<...>), home paths and option-like words are left out."""
    for code in re.findall(r"`([^`]+)`", text):
        for word in code.split():
            if "<" in word or word.startswith(("~", "-", "$", "http")):
                continue
            word = re.sub(r"^\./", "", re.sub(r"(:\d+(-\d+)?|#\S*)$", "", word.rstrip(".,;:)）、，。；")))  # path:12, path#anchor, trailing punctuation
            if PATH.fullmatch(word):
                yield word


def playbook_problems(root, name):
    path = os.path.join(root, PLAYBOOKS, name)
    rel = f"{PLAYBOOKS}/{name}"
    raw = open(path, "rb").read()
    text = raw.decode("utf-8")
    found = []
    if len(raw) > MAX_BYTES:
        found.append(f"P1 {rel}: {len(raw)} bytes, over {MAX_BYTES}; cut it to the steps a session needs")
    first = text.split("\n", 1)[0]
    if not re.fullmatch(r"# 剧本：\S.*", first):
        found.append(f"P2 {rel}:1: first line is {first!r}; make it `# 剧本：<类型>`")
    if not re.search(r"^回报必含[：:]", text, re.M):
        found.append(f"P3 {rel}: no 「回报必含：」 section; add one listing what the report must contain")
    for word in named_paths(text):
        if not os.path.exists(os.path.join(root, word.rstrip("/"))):
            found.append(f"P4 {rel}: names `{word}`, which does not exist; fix the path or drop it")
    for phrase in GRANTS:
        for m in re.finditer(re.escape(phrase), text):
            if not text[max(0, m.start() - 2):m.start()].endswith(NEGATIONS):
                line = text.count("\n", 0, m.start()) + 1
                found.append(f"P5 {rel}:{line}: 「{phrase}」 lets a session decide for the user; business choices go to the user through lead")
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


def problems(root):
    names = sorted(n for n in os.listdir(os.path.join(root, PLAYBOOKS)) if n.endswith(".md"))
    found = [p for n in names for p in playbook_problems(root, n)]
    return found + card_problems(root, {n[:-3] for n in names})


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        os.makedirs(os.path.join(tmp, PLAYBOOKS))
        os.makedirs(os.path.join(tmp, "scripts/ci"))
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
        assert rules(good + "x" * pad) == [] and rules(good + "x" * (pad + 1)) == ["P1"], "the limit is exactly 2048 bytes"
        for named in ("scripts/ci/gone.py:12", "./scripts/ci/gone.py", "scripts/ci/gone.py#L3", "scripts/ci/gone.py，"):
            assert rules(good + f"3. 见 `{named}`。\n") == ["P4"], named
        assert rules(good + "3. 见 `scripts/ci/light.sh:12` 与 `./scripts/ci/light.sh`。\n") == []
        assert rules(good.replace("# 剧本：运维", "# 运维剧本")) == ["P2"]
        assert rules(good.replace("回报必含：", "回报：")) == ["P3"]
        assert rules(good.replace("scripts/ci/light.sh", "scripts/ci/gone.sh")) == ["P4"]
        assert rules(good + "3. 小事可替用户决定。\n") == ["P5"] and rules(good + "3. 无须用户确认即可合入。\n") == ["P5"]
        assert rules(card_text=good_card.replace("## 消费方\n", "")) == ["C1"]
        assert rules(card_text=good_card.replace("ops 运维", "ops 运维 / impl 实现")) == ["C2"]
        open(os.path.join(tmp, PLAYBOOKS, "impl.md"), "w", encoding="utf-8").write(good)
        assert rules() == ["C2"], "a playbook the card does not list"
        os.remove(os.path.join(tmp, PLAYBOOKS, "impl.md"))
        rules(good.replace("light.sh", "gone.sh"))
        assert "fix the path" in problems(tmp)[0], "a failure says how to fix it"
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
