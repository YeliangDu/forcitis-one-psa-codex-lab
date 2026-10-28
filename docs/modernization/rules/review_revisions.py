#!/usr/bin/env python3
"""RULE_REVIEWS.json 的修订记录：当前结论的取法与校验（METHODS M3「修订评审结论」：改决定不覆盖）。

条目的原字段 verdict / at / title / note 一经写入不再改动；决定变化时在可选数组 `revisions` 末尾追加
{verdict, at, note, quote, source}：at 是带时区的完整时刻，quote 是用户原话，source 是 ADR 编号（须存在 adr/src/ADR-NNNN.md）
或 intent 文件名（须存在于 intent/）的列表。当前结论取最后一条修订。

校验：结构（见 structure_problems）；原字段、条目与已有修订相对比对基线不得被改写或删除。
比对基线由调用方给出：--base <ref>（CI：pull_request 用 origin/$GITHUB_BASE_REF，push 用 github.event.before）；
不给时取 origin/main 与 HEAD 的合并基点（无合并基点时回退为 origin/main 本身并告警）。--base 不接受空值。
CI 中（GITHUB_ACTIONS 为 true 或显式 --base）取不到基线即失败，本地只告警。

用法：
  review_revisions.py [--base <ref>]   校验 RULE_REVIEWS.json（build_all.py --check 与 CI 运行）
  review_revisions.py --self-test      自测
被 build_brief.py 导入使用 current()、load_reviews() 与 structure_problems()。"""
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile

VERDICTS = ("confirmed", "wrong", "discuss")
ORIGINAL_FIELDS = ("verdict", "at", "title", "note")
REVISION_FIELDS = ("verdict", "at", "note", "quote", "source")
REVIEWS_FILE = "RULE_REVIEWS.json"
DEFAULT_BASE = "origin/main"
MAX_AHEAD = datetime.timedelta(days=1)

git_env = None


def load_reviews(base_dir):
    def no_duplicates(pairs):
        keys = [key for key, _ in pairs]
        repeated = sorted({key for key in keys if keys.count(key) > 1})
        if repeated:
            raise ValueError(f"重复的键 {repeated}")
        return dict(pairs)

    path = os.path.join(base_dir, REVIEWS_FILE)
    try:
        with open(path, encoding="utf-8") as f:
            reviews = json.load(f, object_pairs_hook=no_duplicates)["reviews"]
    except (ValueError, KeyError, TypeError) as e:
        raise ValueError(f"{REVIEWS_FILE} 无法解析：{e}") from e
    if not isinstance(reviews, dict):
        raise ValueError(f"{REVIEWS_FILE} 的 reviews 必须是对象")
    return reviews


def moment(text):
    value = datetime.datetime.fromisoformat(text)
    if value.tzinfo is None:
        value = value.replace(tzinfo=datetime.timezone.utc)
    return value


def zoned_moment(text):
    if not isinstance(text, str) or "T" not in text:
        raise ValueError("缺少时刻")
    value = datetime.datetime.fromisoformat(text)
    if value.tzinfo is None:
        raise ValueError("缺少时区")
    return value


def current(entry):
    original_at = entry.get("at", "")
    revisions = entry.get("revisions")
    if revisions:
        last = revisions[-1]
        return {"verdict": last["verdict"], "at": original_at, "note": last["note"],
                "revised": {"at": last["at"][:10], "source": last["source"]}}
    return {"verdict": entry["verdict"], "at": original_at, "note": entry.get("note", ""), "revised": None}


def source_problem(source, base_dir):
    if re.fullmatch(r"ADR-\d{4}", source):
        return None if os.path.isfile(os.path.join(base_dir, "adr", "src", f"{source}.md")) else f"{source} 在 adr/src/ 中不存在"
    if re.fullmatch(r"[^/\\]+\.md", source):
        return None if os.path.isfile(os.path.join(base_dir, "intent", source)) else f"{source} 在 intent/ 中不存在"
    return f"{source} 既不是 ADR 编号（ADR-NNNN）也不是 intent 文件名（*.md）"


def structure_problems(reviews, base_dir=None, now=None):
    now = now or datetime.datetime.now(datetime.timezone.utc)
    found = []
    for rule, entry in reviews.items():
        if not isinstance(entry, dict):
            found.append(f"{rule}: 条目必须是对象")
            continue
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
            elif base_dir is not None:
                found += [f"{where}: source {problem}" for problem in (source_problem(s, base_dir) for s in source) if problem]
            if revision.get("verdict") not in VERDICTS:
                found.append(f"{where}: verdict 不在 {VERDICTS} 内")
            try:
                at = zoned_moment(revision.get("at"))
            except ValueError as e:
                found.append(f"{where}: at 须是带时区的完整时刻（如 2026-10-15T09:30:00+02:00）：{e}")
                continue
            if at > now + MAX_AHEAD:
                found.append(f"{where}: at 晚于当前时刻超过 1 天")
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
        if not isinstance(after, dict) or not isinstance(before, dict):
            continue
        for field in ORIGINAL_FIELDS:
            if before.get(field) != after.get(field):
                found.append(f"{rule}: 相对 {label} 改写了原字段 {field}；改决定请在 revisions 追加")
        kept = before.get("revisions", [])
        if after.get("revisions", [])[:len(kept)] != kept:
            found.append(f"{rule}: 相对 {label} 改动了已有修订；修订只能在末尾追加")
    return found


def git(base_dir, *args):
    return subprocess.run(["git", "-C", base_dir, *args], capture_output=True, text=True, env=git_env)


class NoBaseline(Exception):
    pass


def comparison_point(base_dir, base):
    try:
        resolved = git(base_dir, "rev-parse", "--verify", "--quiet", f"{base}^{{commit}}")
    except FileNotFoundError:
        raise NoBaseline("git 不可用") from None
    if resolved.returncode != 0:
        raise NoBaseline(f"基线 {base} 不是本仓库中的提交")
    sha = resolved.stdout.strip()
    parents = git(base_dir, "rev-list", "--parents", "-n", "1", "HEAD")
    if parents.returncode != 0:
        raise NoBaseline("无法读取 HEAD")
    if len(parents.stdout.split()) > 2:
        return sha, None
    merged = git(base_dir, "merge-base", sha, "HEAD")
    if merged.returncode == 0 and merged.stdout.strip():
        return merged.stdout.strip(), None
    return sha, f"{base} 与 HEAD 无合并基点（浅克隆？），回退为 {base} 本身作基线，可能误报"


def check(base_dir, base=None, strict=False, now=None):
    problems, warnings, info = [], [], []
    try:
        reviews = load_reviews(base_dir)
    except ValueError as e:
        return [str(e)], warnings, info
    problems += structure_problems(reviews, base_dir, now)
    try:
        sha, fallback = comparison_point(base_dir, base or DEFAULT_BASE)
        if fallback:
            warnings.append(fallback)
        shown = git(base_dir, "show", f"{sha}:./{REVIEWS_FILE}")
        label = f"{base or DEFAULT_BASE}（比对提交 {sha[:10]}）"
        if shown.returncode != 0:
            info.append(f"比对基线 {label} 中没有 {REVIEWS_FILE}，无原结论可比对")
        else:
            info.append(f"比对基线：{label}")
            try:
                baseline = json.loads(shown.stdout)["reviews"]
            except (ValueError, KeyError, TypeError) as e:
                raise NoBaseline(f"基线中的 {REVIEWS_FILE} 无法解析：{e}") from e
            problems += rewritten_originals(reviews, baseline, label)
    except NoBaseline as e:
        (problems if strict else warnings).append(f"取不到比对基线，未校验原结论是否被改写：{e}")
    return problems, warnings, info


def self_test():
    global git_env
    now = datetime.datetime(2026, 10, 15, 12, 0, tzinfo=datetime.timezone.utc)
    original = {"verdict": "confirmed", "at": "2026-10-14T11:00:28+02:00", "title": "T", "note": "正确（评审）：甲"}
    revision = {"verdict": "wrong", "at": "2026-10-15T09:30:00+02:00", "note": "错误：乙", "quote": "用户原话", "source": ["ADR-0033"]}

    def reviews_with(*revisions, **override):
        return {"RULE-1": {**original, **override, "revisions": [dict(r) for r in revisions]}}

    def problems_of(*revisions, **override):
        return structure_problems(reviews_with(*revisions, **override), now=now)

    def has(found, text):
        return any(text in line for line in found)

    clean_env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    clean_env.update(GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_NOSYSTEM="1")
    git_env = clean_env
    try:
        structure_cases(original, revision, now, reviews_with, problems_of, has)
        presentation_cases(original, revision, has)
        baseline_cases(original, revision, now, has, clean_env)
    finally:
        git_env = None
    print("review_revisions self-test: ok")
    return 0


def structure_cases(original, revision, now, reviews_with, problems_of, has):
    assert structure_problems({"RULE-1": dict(original)}, now=now) == []
    assert problems_of(revision) == []
    later = {**revision, "at": "2026-10-15T10:00:00+02:00", "verdict": "confirmed", "source": ["ADR-0034", "x.md"]}
    assert structure_problems(reviews_with(revision, later), now=now) == [], "later revision with other verdict"
    assert problems_of({**revision, "at": original["at"]}) == [], "equal to the original at is allowed"
    assert problems_of({**revision, "at": "2026-10-15T07:30:00Z"}) == [], "UTC offset"

    assert has(problems_of(), "非空数组")
    assert has(problems_of({k: v for k, v in revision.items() if k != "quote"}), "缺少 quote")
    assert has(problems_of({**revision, "quote": "  "}), "缺少 quote")
    assert has(problems_of({k: v for k, v in revision.items() if k != "source"}), "source")
    assert has(problems_of({**revision, "source": []}), "source")
    assert has(problems_of({**revision, "source": "ADR-0033"}), "source")
    assert has(problems_of({**revision, "verdict": "maybe"}), "verdict")
    assert has(problems_of({**revision, "note": ""}), "缺少 note")
    assert has(problems_of({**revision, "extra": 1}), "未知字段")
    for bad in ("昨天", "2026-10-15", "2026-10-15T09:30:00", "2026-10-15T09:30:00+25:00"):
        assert has(problems_of({**revision, "at": bad}), "带时区的完整时刻"), bad
    assert has(problems_of({**revision, "at": 5}), "带时区的完整时刻")
    assert has(problems_of({**revision, "at": "2026-10-14T08:00:00+02:00"}), "不得早于原结论"), "before the original"
    assert has(problems_of({**revision, "at": "2026-10-14T08:30:00Z"}), "不得早于原结论"), "08:30Z is 10:30+02:00, before the original"
    assert problems_of({**revision, "at": "2026-10-14T09:30:00Z"}) == [], "09:30Z is 11:30+02:00, after the original"
    assert has(problems_of({**revision, "at": "2026-10-16T12:00:01+00:00"}), "晚于当前时刻"), "more than a day ahead"
    assert problems_of({**revision, "at": "2026-10-16T12:00:00+00:00"}) == [], "exactly one day ahead is allowed"
    assert has(structure_problems(reviews_with(revision, dict(revision)), now=now), "严格晚于")
    same_day_earlier = {**later, "at": "2026-10-15T09:00:00+02:00"}
    assert has(structure_problems(reviews_with(revision, same_day_earlier), now=now), "严格晚于"), "same day, earlier"
    one_second = {**later, "at": "2026-10-15T09:30:01+02:00"}
    assert structure_problems(reviews_with(revision, one_second), now=now) == [], "same day, one second later"
    assert has(problems_of(revision, verdict="maybe"), "原 verdict")
    assert has(structure_problems({"RULE-9": "text"}, now=now), "RULE-9: 条目必须是对象")

    with tempfile.TemporaryDirectory() as base:
        os.makedirs(os.path.join(base, "adr", "src"))
        os.makedirs(os.path.join(base, "intent"))
        open(os.path.join(base, "adr", "src", "ADR-0033.md"), "w").close()
        open(os.path.join(base, "intent", "a-b.md"), "w").close()

        def sourced(*source):
            return structure_problems(reviews_with({**revision, "source": list(source)}), base, now)

        assert sourced("ADR-0033", "a-b.md") == []
        assert has(sourced("ADR-0099"), "ADR-0099 在 adr/src/ 中不存在")
        assert has(sourced("ADR-0033（提议）"), "既不是 ADR 编号")
        assert has(sourced("missing.md"), "missing.md 在 intent/ 中不存在")
        assert has(sourced("../ADR-0033.md"), "既不是 ADR 编号")

        def loaded(text):
            with open(os.path.join(base, REVIEWS_FILE), "w", encoding="utf-8") as f:
                f.write(text)
            return load_reviews(base)

        assert loaded('{"reviews": {"RULE-1": {"verdict": "wrong"}}}') == {"RULE-1": {"verdict": "wrong"}}
        for text, expect in (('{"reviews": {"RULE-1": {}, "RULE-1": {}}}', "重复的键"),
                             ('{"reviews": {"RULE-1": {"verdict": "wrong", "verdict": "confirmed"}}}', "重复的键"),
                             ('{"reviews": []}', "必须是对象"), ('{"no": 1}', "无法解析"), ("{", "无法解析")):
            try:
                loaded(text)
            except ValueError as e:
                assert expect in str(e), (text, str(e))
            else:
                raise AssertionError(text)

    assert current({"verdict": "wrong", "at": "A", "note": "n"}) == {"verdict": "wrong", "at": "A", "note": "n", "revised": None}
    chosen = current(reviews_with(revision, later)["RULE-1"])
    assert chosen["verdict"] == "confirmed" and chosen["at"] == original["at"]
    assert chosen["revised"] == {"at": "2026-10-15", "source": later["source"]}

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


def presentation_cases(original, revision, has):
    brief_script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build_brief.py")
    rows = [("RULE-1", "Low", "blocked"), ("RULE-2", "High", "settled"), ("RULE-3", "Low", "blocked"), ("RULE-4", "Low", "blocked"),
            ("RULE-5", "High", "settled"), ("RULE-6", "High", "settled")]
    table = "".join(f"| {rid} | 售前与报价 | Title {rid} | calc | P0 | `X.java:1` | {conf} |\n" for rid, conf, _ in rows)
    bodies = "".join(f"### {rid}: Title {rid}\n\nbody\n\n" for rid, _, _ in rows)

    def revised(verdict, at, note):
        return {**revision, "verdict": verdict, "at": at, "note": note}

    reviews = {
        "RULE-1": {**original, "revisions": [revised("wrong", "2026-10-15T09:30:00+02:00", "错误（修订）：新做法一")]},
        "RULE-2": {**original, "revisions": [revised("confirmed", "2026-10-15T09:30:00+02:00", "正确+修（修订）：新做法二")]},
        "RULE-3": {**original, "verdict": "wrong", "note": "错误（评审）：旧做法三"},
        "RULE-4": {**original, "revisions": [revised("discuss", "2026-10-15T09:30:00+02:00", "待定四")]},
        "RULE-5": {**original, "revisions": [revised("wrong", "2026-10-15T09:30:00+02:00", "错误（修订）：新做法五")]},
        "RULE-6": {**original, "revisions": [revised("discuss", "2026-10-15T09:30:00+02:00", "待定六")]},
    }
    with tempfile.TemporaryDirectory() as base:
        os.makedirs(os.path.join(base, "adr", "src"))
        open(os.path.join(base, "adr", "src", "ADR-0033.md"), "w").close()
        with open(os.path.join(base, "BUSINESS_RULES.md"), "w", encoding="utf-8") as f:
            f.write(table + "\n" + bodies)
        with open(os.path.join(base, "MODERNIZATION_BRIEF.template.md"), "w", encoding="utf-8") as f:
            f.write("{{BEHAVIOR_CONTRACT}}\n{{PROCESS_CONTRACT}}\n")

        def build(data):
            with open(os.path.join(base, REVIEWS_FILE), "w", encoding="utf-8") as f:
                json.dump({"system": "x", "version": 1, "reviews": data}, f, ensure_ascii=False)
            return subprocess.run([sys.executable, "-B", brief_script, base], capture_output=True, text=True, env=git_env)

        done = build(reviews)
        assert done.returncode == 0, done.stdout + done.stderr
        text = open(os.path.join(base, "MODERNIZATION_BRIEF.md"), encoding="utf-8").read()
        lines = text.splitlines()

        def blocked_line(rid):
            return next(l for l in lines if l.startswith("- [") and f"{rid} Title" in l)

        mark = "已修订 2026-10-15，出处 ADR-0033"
        one = blocked_line("RULE-1")
        assert "**不复刻**" in one and "新做法一" in one and mark in one and "评审 2026-10-14" in one, one
        three = blocked_line("RULE-3")
        assert "旧做法三" in three and "已修订" not in three, three
        four = blocked_line("RULE-4")
        assert "**待讨论**" in four and "待定四" in four and mark in four, four
        settled = next(l for l in lines if l.startswith("**已定："))
        for fragment in ("RULE-2 Title RULE-2（修订：新做法二）", "RULE-5 Title RULE-5（**不复刻**，新系统做法：新做法五）",
                         "RULE-6 Title RULE-6（待讨论：待定六）"):
            assert fragment in settled, (fragment, settled)
        assert settled.count(mark) == 3, settled

        broken = {**reviews, "RULE-1": {**reviews["RULE-1"], "revisions": [{k: v for k, v in revised("wrong", "2026-10-15T09:30:00+02:00", "x").items() if k != "quote"}]}}
        failed = build(broken)
        assert failed.returncode != 0 and "缺少 quote" in failed.stdout + failed.stderr
        failed = build({**reviews, "RULE-1": "text"})
        assert failed.returncode != 0 and "条目必须是对象" in failed.stdout + failed.stderr


def baseline_cases(original, revision, now, has, clean_env):
    global git_env

    def check_in(root, base=None, strict=False):
        return check(root, base, strict, now)

    with tempfile.TemporaryDirectory() as root:
        def run(*args):
            done = git(root, "-c", "user.email=ci@example.invalid", "-c", "user.name=ci", *args)
            assert done.returncode == 0, done.stderr
            return done.stdout.strip()

        def write(reviews):
            with open(os.path.join(root, REVIEWS_FILE), "w", encoding="utf-8") as f:
                json.dump({"system": "x", "version": 1, "reviews": reviews}, f, ensure_ascii=False)

        def commit(message):
            run("add", "-A")
            run("commit", "-q", "-m", message)

        run("init", "-q", "-b", "main")
        os.makedirs(os.path.join(root, "adr", "src"))
        open(os.path.join(root, "adr", "src", "ADR-0033.md"), "w").close()
        open(os.path.join(root, "adr", "src", ".keep"), "w").close()

        write({"RULE-1": dict(original)})
        problems, warnings, info = check_in(root)
        assert problems == [] and has(warnings, "取不到比对基线"), "an unborn HEAD only warns locally"
        problems, warnings, info = check_in(root, strict=True)
        assert has(problems, "取不到比对基线") and warnings == [], "an unborn HEAD fails in CI"

        commit("first")
        problems, warnings, info = check_in(root)
        assert problems == [] and has(warnings, "取不到比对基线"), "no origin/main locally warns"
        problems, warnings, info = check_in(root, strict=True)
        assert has(problems, "取不到比对基线"), "no origin/main fails in CI"
        problems, warnings, info = check_in(root, "no-such-ref", strict=True)
        assert has(problems, "no-such-ref"), "an explicit missing base fails"

        run("checkout", "-q", "--orphan", "unrelated")
        commit("unrelated root")
        run("update-ref", "refs/remotes/origin/main", run("rev-parse", "HEAD"))
        run("checkout", "-q", "main")
        problems, warnings, info = check_in(root)
        assert problems == [] and has(warnings, "无合并基点"), "a base without a common ancestor falls back and says so"

        empty = subprocess.run([sys.executable, "-B", os.path.abspath(__file__), "--base", ""], capture_output=True, text=True,
                               env={**clean_env, "GITHUB_ACTIONS": "false"})
        assert empty.returncode == 2, "an empty --base is refused, not read as no base"

        main_tip = run("rev-parse", "HEAD")
        run("update-ref", "refs/remotes/origin/main", main_tip)
        run("checkout", "-q", "-b", "feature")
        problems, warnings, info = check_in(root)
        assert problems == [] and warnings == [] and has(info, f"比对提交 {main_tip[:10]}"), info

        write({"RULE-1": {**original, "revisions": [dict(revision)]}, "RULE-2": dict(original)})
        assert check_in(root)[0] == [], "an uncommitted new entry and a revision are not flagged"
        commit("revise")
        assert check_in(root)[0] == []
        assert check_in(root, "origin/main", True)[0] == []

        run("checkout", "-q", "-b", "rewrite")
        write({"RULE-1": {**original, "note": "改写"}, "RULE-2": dict(original)})
        assert has(check_in(root)[0], "改写了原字段 note"), "uncommitted rewrite against the merge base"
        commit("rewrite")
        assert has(check_in(root)[0], "改写了原字段 note"), "committed rewrite against the merge base"
        write({"RULE-2": dict(original)})
        commit("delete")
        assert has(check_in(root)[0], "RULE-1: 相对") and has(check_in(root)[0], "被删除")
        run("checkout", "-q", "feature")

        run("checkout", "-q", "-b", "main-moves", "main")
        write({"RULE-1": dict(original), "RULE-9": dict(original)})
        commit("main moves on")
        run("update-ref", "refs/remotes/origin/main", run("rev-parse", "HEAD"))
        run("checkout", "-q", "feature")
        problems, warnings, info = check_in(root)
        assert problems == [] and has(info, f"比对提交 {main_tip[:10]}"), "the merge base, not the moved main tip, is the baseline"

        write({"RULE-1": dict(original)})
        git_env = {**clean_env, "PATH": os.path.join(root, "no-git-here")}
        problems, warnings, info = check_in(root)
        assert problems == [] and has(warnings, "git 不可用"), "without git a local run only warns"
        problems, warnings, info = check_in(root, strict=True)
        assert has(problems, "git 不可用"), "without git CI fails"
        git_env = clean_env

    with tempfile.TemporaryDirectory() as root:
        git(root, "init", "-q", "-b", "main")
        with open(os.path.join(root, "x"), "w") as f:
            f.write("x")
        git(root, "add", "-A")
        git(root, "-c", "user.email=ci@example.invalid", "-c", "user.name=ci", "commit", "-q", "-m", "no reviews file yet")
        sha = git(root, "rev-parse", "HEAD").stdout.strip()
        git(root, "update-ref", "refs/remotes/origin/main", sha)
        with open(os.path.join(root, REVIEWS_FILE), "w", encoding="utf-8") as f:
            json.dump({"reviews": {"RULE-1": dict(original)}}, f)
        problems, warnings, info = check_in(root, strict=True)
        assert problems == [] and has(info, "没有 RULE_REVIEWS.json"), "a file new on this branch has nothing to compare"


def main():
    args = sys.argv[1:]
    if args == ["--self-test"]:
        return self_test()
    base = None
    if len(args) == 2 and args[0] == "--base" and args[1].strip():
        base = args[1]
    elif args:
        print(__doc__, file=sys.stderr)
        return 2
    strict = bool(base) or os.environ.get("GITHUB_ACTIONS") == "true"
    problems, warnings, info = check(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), base, strict)
    for line in warnings:
        print(f"警告：{line}", file=sys.stderr)
    for line in info:
        print(line)
    for line in problems:
        print(f"{REVIEWS_FILE}: {line}", file=sys.stderr)
    print(f"review_revisions: {len(problems)} 个问题")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
