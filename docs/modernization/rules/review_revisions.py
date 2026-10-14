#!/usr/bin/env python3
"""RULE_REVIEWS.json 的修订记录：当前结论的取法与校验（SESSION_PLAYBOOK §12.2：改决定不覆盖）。

条目的原字段 verdict / at / title / note 一经写入不再改动；决定变化时在可选数组 `revisions` 末尾追加
{verdict, at, note, quote, source}，其中 quote 是用户原话，source 是 ADR 编号或 intent 文件名的列表。当前结论取最后一条修订。

用法：
  review_revisions.py             校验 RULE_REVIEWS.json 的结构，并与 HEAD 及与 origin/main 的合并基点比对原字段（本地与 CI 的 build_all.py --check 运行）
  review_revisions.py --self-test 自测
被 build_brief.py 导入使用 current() 与 structure_problems()。"""
import datetime
import json
import os
import subprocess
import sys
import tempfile

VERDICTS = ("confirmed", "wrong", "discuss")
ORIGINAL_FIELDS = ("verdict", "at", "title", "note")
REVISION_FIELDS = ("verdict", "at", "note", "quote", "source")
REVIEWS_FILE = "RULE_REVIEWS.json"


def moment(text):
    value = datetime.datetime.fromisoformat(text)
    if value.tzinfo is None:
        value = value.replace(tzinfo=datetime.timezone.utc)
    return value


def current(entry):
    revisions = entry.get("revisions")
    if revisions:
        last = revisions[-1]
        return {"verdict": last["verdict"], "at": last["at"], "note": last["note"],
                "revised": {"at": last["at"][:10], "source": last["source"]}}
    return {"verdict": entry["verdict"], "at": entry.get("at", ""), "note": entry.get("note", ""), "revised": None}


def structure_problems(reviews):
    found = []
    for rule, entry in reviews.items():
        if entry.get("verdict") not in VERDICTS:
            found.append(f"{rule}: 原 verdict 不在 {VERDICTS} 内")
        if "revisions" not in entry:
            continue
        revisions = entry["revisions"]
        if not isinstance(revisions, list) or not revisions:
            found.append(f"{rule}: revisions 必须是非空数组")
            continue
        try:
            floor = moment(entry["at"]) if entry.get("at") else None
        except ValueError:
            found.append(f"{rule}: 原 at 不是 ISO 时刻")
            floor = None
        previous = None
        for number, revision in enumerate(revisions, 1):
            where = f"{rule} 修订 {number}"
            if not isinstance(revision, dict):
                found.append(f"{where}: 必须是对象")
                continue
            extra = sorted(set(revision) - set(REVISION_FIELDS))
            if extra:
                found.append(f"{where}: 未知字段 {extra}")
            for field in ("note", "quote"):
                if not isinstance(revision.get(field), str) or not revision[field].strip():
                    found.append(f"{where}: 缺少 {field}")
            source = revision.get("source")
            if not isinstance(source, list) or not source or not all(isinstance(s, str) and s.strip() for s in source):
                found.append(f"{where}: source 必须是至少一个 ADR 编号或 intent 文件名的数组")
            if revision.get("verdict") not in VERDICTS:
                found.append(f"{where}: verdict 不在 {VERDICTS} 内")
            try:
                at = moment(revision["at"])
            except (KeyError, TypeError, ValueError):
                found.append(f"{where}: at 缺失或不是 ISO 时刻")
                continue
            if previous is not None and at <= previous:
                found.append(f"{where}: at 必须严格晚于上一条修订")
            elif previous is None and floor is not None and at < floor:
                found.append(f"{where}: at 不得早于原结论的 at")
            previous = at
    return found


def rewritten_originals(reviews, baseline, label):
    found = []
    for rule, before in baseline.items():
        after = reviews.get(rule)
        if after is None:
            found.append(f"{rule}: 相对 {label} 被删除；评审结论不得删除，改决定用 revisions")
            continue
        for field in ORIGINAL_FIELDS:
            if before.get(field) != after.get(field):
                found.append(f"{rule}: 相对 {label} 改写了原字段 {field}；改决定请在 revisions 追加")
        kept = before.get("revisions", [])
        if after.get("revisions", [])[:len(kept)] != kept:
            found.append(f"{rule}: 相对 {label} 改动了已有修订；修订只能在末尾追加")
    return found


def git(base_dir, *args):
    return subprocess.run(["git", "-C", base_dir, *args], capture_output=True, text=True)


def baseline_versions(base_dir):
    try:
        probe = git(base_dir, "rev-parse", "--verify", "--quiet", "HEAD^{commit}")
    except FileNotFoundError:
        return [], "git 不可用，跳过原字段改写校验"
    if probe.returncode != 0:
        return [], "无法读取 git 的 HEAD，跳过原字段改写校验"
    refs = [("HEAD", "HEAD")]
    base = git(base_dir, "merge-base", "HEAD", "origin/main")
    if base.returncode == 0 and base.stdout.strip():
        refs.append((f"origin/main 合并基点 {base.stdout.strip()[:10]}", base.stdout.strip()))
    versions = []
    for label, ref in refs:
        shown = git(base_dir, "show", f"{ref}:./{REVIEWS_FILE}")
        if shown.returncode == 0:
            versions.append((label, json.loads(shown.stdout)["reviews"]))
    return versions, None


def check(base_dir):
    reviews = json.load(open(os.path.join(base_dir, REVIEWS_FILE), encoding="utf-8"))["reviews"]
    found = structure_problems(reviews)
    versions, warning = baseline_versions(base_dir)
    if warning:
        print(f"警告：{warning}", file=sys.stderr)
    for label, baseline in versions:
        found += rewritten_originals(reviews, baseline, label)
    return found


def self_test():
    original = {"verdict": "confirmed", "at": "2026-10-14T11:00:28+02:00", "title": "T", "note": "正确（评审）：甲"}
    revision = {"verdict": "wrong", "at": "2026-10-15", "note": "错误：乙", "quote": "用户原话", "source": ["ADR-0033"]}

    def reviews_with(*revisions, **override):
        return {"RULE-1": {**original, **override, "revisions": [dict(r) for r in revisions]}}

    def has(found, text):
        return any(text in line for line in found)

    assert structure_problems({"RULE-1": dict(original)}) == []
    assert structure_problems(reviews_with(revision)) == []
    later = {**revision, "at": "2026-10-16T08:00:00+02:00", "verdict": "confirmed", "source": ["ADR-0034", "intent.md"]}
    assert structure_problems(reviews_with(revision, later)) == []
    assert structure_problems(reviews_with({**revision, "at": original["at"]})) == []

    assert has(structure_problems(reviews_with()), "非空数组")
    assert has(structure_problems(reviews_with({k: v for k, v in revision.items() if k != "quote"})), "缺少 quote")
    assert has(structure_problems(reviews_with({**revision, "quote": "  "})), "缺少 quote")
    assert has(structure_problems(reviews_with({k: v for k, v in revision.items() if k != "source"})), "source")
    assert has(structure_problems(reviews_with({**revision, "source": []})), "source")
    assert has(structure_problems(reviews_with({**revision, "source": "ADR-0033"})), "source")
    assert has(structure_problems(reviews_with({**revision, "verdict": "maybe"})), "verdict")
    assert has(structure_problems(reviews_with({**revision, "note": ""})), "缺少 note")
    assert has(structure_problems(reviews_with({**revision, "at": "昨天"})), "ISO")
    assert has(structure_problems(reviews_with({**revision, "extra": 1})), "未知字段")
    assert has(structure_problems(reviews_with({**revision, "at": "2026-10-14T08:00:00+02:00"})), "不得早于原结论")
    assert has(structure_problems(reviews_with(revision, dict(revision))), "严格晚于")
    assert has(structure_problems(reviews_with(revision, {**later, "at": "2026-10-14"})), "严格晚于")
    assert has(structure_problems(reviews_with(revision, verdict="maybe")), "原 verdict")

    assert current({"verdict": "wrong", "note": "n"}) == {"verdict": "wrong", "at": "", "note": "n", "revised": None}
    chosen = current(reviews_with(revision, later)["RULE-1"])
    assert chosen["verdict"] == "confirmed" and chosen["revised"] == {"at": "2026-10-16", "source": later["source"]}

    baseline = {"RULE-1": dict(original), "RULE-2": {**original, "revisions": [dict(revision)]}}
    assert rewritten_originals(baseline, baseline, "x") == []
    grown = {**baseline, "RULE-1": {**original, "revisions": [dict(revision)]}, "RULE-3": dict(original)}
    assert rewritten_originals(grown, baseline, "x") == []
    for field in ORIGINAL_FIELDS:
        changed = {**baseline, "RULE-1": {**original, field: "改写"}}
        assert has(rewritten_originals(changed, baseline, "x"), f"原字段 {field}")
    assert has(rewritten_originals({"RULE-2": baseline["RULE-2"]}, baseline, "x"), "被删除")
    assert has(rewritten_originals({**baseline, "RULE-2": dict(original)}, baseline, "x"), "已有修订")
    rewritten = {**baseline, "RULE-2": {**original, "revisions": [{**revision, "note": "改"}]}}
    assert has(rewritten_originals(rewritten, baseline, "x"), "已有修订")

    with tempfile.TemporaryDirectory() as root:
        versions, warning = baseline_versions(root)
        assert versions == [] and warning

        def commit(message):
            git(root, "add", "-A")
            done = git(root, "-c", "user.email=ci@example.invalid", "-c", "user.name=ci", "commit", "-q", "-m", message)
            assert done.returncode == 0, done.stderr

        def write(reviews):
            with open(os.path.join(root, REVIEWS_FILE), "w", encoding="utf-8") as f:
                json.dump({"system": "x", "version": 1, "reviews": reviews}, f, ensure_ascii=False)

        git(root, "init", "-q", "-b", "main")
        versions, warning = baseline_versions(root)
        assert versions == [] and warning
        write({"RULE-1": dict(original)})
        commit("first")
        versions, warning = baseline_versions(root)
        assert warning is None and [label for label, _ in versions] == ["HEAD"]
        assert check(root) == []
        write({"RULE-1": {**original, "revisions": [dict(revision)]}})
        assert check(root) == []
        write({"RULE-1": {**original, "note": "改写"}})
        assert has(check(root), "相对 HEAD 改写了原字段 note")
        write({"RULE-1": dict(original), "RULE-2": dict(original)})
        assert check(root) == []
    print("review_revisions self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if len(sys.argv) != 1:
        print(__doc__, file=sys.stderr)
        return 2
    found = check(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    for line in found:
        print(f"{REVIEWS_FILE}: {line}", file=sys.stderr)
    print(f"review_revisions: {len(found)} 个问题")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
