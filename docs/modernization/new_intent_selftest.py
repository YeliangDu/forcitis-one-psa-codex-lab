#!/usr/bin/env python3
"""new_intent.py 的自测（二乙 #25 C7）：在临时副本里跑，不写仓库的 intent/。用法：python3 docs/modernization/new_intent_selftest.py（附带的 --self-test 参数忽略）。"""
import contextlib, glob, importlib.util, json, os, shutil, subprocess, sys, tempfile

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)
_spec = importlib.util.spec_from_file_location("build_rule_index", os.path.join(BASE, "rules", "build_rule_index.py"))
build_rule_index = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_rule_index)

GOOD = ["--session=one-ops", "--category=流程", "--owner=one-ops", "--paths=scripts/ci,docs/modernization", "--supersedes=无"]
TEXT = ["用户原话", "落点说明"]


def run(tree, args):
    r = subprocess.run([sys.executable, os.path.join(tree, "docs", "modernization", "new_intent.py"), *args, *TEXT], capture_output=True, text=True, cwd=tree)
    return r.returncode, r.stdout + r.stderr


def swap(args, key, value=None):
    out = [a for a in args if not a.startswith(f"--{key}=")]
    return out if value is None else out + [f"--{key}={value}"]


HEAD = "| 单子或页面 | 用途 | 负责会话 | 起用日期 | 退役日期 | 出处 |\n|---|---|---|---|---|---|\n"
ROW = "| {} | 演练 | {} | x | 待定 | x |\n"
PAGES = HEAD + ROW.format("ZZ", "one-glossary") + ROW.format("ZU", "roles/ 未列")
ZU_ONLY = HEAD + ROW.format("ZU", "roles/ 未列")


def decide_drill(tree, sync_args):
    """G3 演练（临时副本）：一题作答 → decide_sync 生成片段，负责会话取自登记表；表头变、未登记、未列、重复登记即拒。"""
    mod = os.path.join(tree, "docs", "modernization")
    dec = os.path.join(mod, "rules", "decide")
    for s, n in (("ZZ", "N1"), ("ZU", "N1"), ("ZR", "N2"), ("ZF", "N3")):
        json.dump({"items": [{"no": f"{s}-01", "opts": [{"label": "甲"}]}]}, open(os.path.join(dec, "sheets", f"{s}.json"), "w", encoding="utf-8"), ensure_ascii=False)
        out = os.path.join(tree, "out" + s)
        os.makedirs(os.path.join(out, "answers"))
        os.makedirs(os.path.join(out, "answers-submit"))
        json.dump({"choice": 0, "note": ""}, open(os.path.join(out, "answers", f"{s}@{s}-01.json"), "w"))
        pick = {"choice": 0, "label": "甲", "recommended": True, "defaulted": False}
        json.dump({"number": n, "at": "2026-01-01T00:00:00", "picks": {f"{s}~{s}-01": pick}}, open(os.path.join(out, "answers-submit", f"{n}.json"), "w", encoding="utf-8"), ensure_ascii=False)

    def sync(sheet, pages):
        open(os.path.join(dec, "ONLINE_PAGES.md"), "wb").write(pages if isinstance(pages, bytes) else pages.encode("utf-8"))
        r = subprocess.run([sys.executable, os.path.join(dec, "decide_sync.py"), sheet, os.path.join(tree, "out" + sheet), *sync_args], capture_output=True, text=True)
        return r.returncode, r.stdout + r.stderr
    before = set(glob.glob(os.path.join(mod, "intent", "*.md")))
    code, out = sync("ZZ", PAGES)
    assert code == 0, out
    (frag,) = set(glob.glob(os.path.join(mod, "intent", "*.md"))) - before
    text = open(frag, encoding="utf-8").read()
    assert "## 类别\n\n流程\n\n## 负责会话\n\none-glossary\n\n## 影响路径\n\nscripts/ci、docs/modernization\n" in text, f"片段四项须齐全且负责会话取自登记：{text}"
    os.remove(os.path.join(dec, "answers", "ZZ.json"))
    real = open(os.path.join(BASE, "rules", "decide", "ONLINE_PAGES.md"), encoding="utf-8").read()
    other = ROW.format("ZZ", "one-ops")
    wl = "行不在白名单内"
    for sheet, pages, why in (("ZZ", PAGES.replace("负责会话", "负责人"), "表头"), ("ZY", PAGES, "没有在 ONLINE_PAGES.md 登记"), ("ZU", PAGES, "roles/ 未列"),
                              ("ZZ", PAGES + other, "重复登记"), ("ZZ", PAGES + ROW.format("zz", "one-ops"), "重复登记"), ("ZZ", PAGES + "|ZZ|演练|one-ops|x|待定|x|\n", "重复登记"),
                              ("ZZ", PAGES + "说明\n", "第 5 " + wl), ("ZZ", "说明\n\n" + PAGES, "第 1 " + wl), ("ZZ", PAGES + "# 表后标题须先空行\n", "第 5 " + wl),
                              ("ZZ", PAGES + "\n" + other, "登记表之外又有表格行"), ("ZZ", PAGES + "\n# 标题\n\n" + other, "登记表之外又有表格行"),
                              ("ZZ", PAGES + "  " + other, wl), ("ZZ", PAGES + "    " + other, wl), ("ZZ", PAGES + "\t" + other, wl), ("ZZ", PAGES + "\xa0" + other, wl),
                              ("ZZ", PAGES + other.rstrip("\n") + " \n", wl), ("ZZ", PAGES + "| ZZ | 演练 | one-ops | x | 待定 |\n", wl), ("ZZ", PAGES + "| ZZ | 演练 | one-ops | x | 待定 | x | y |\n", wl),
                              ("ZZ", PAGES + "ZZ | 演练 | one-ops | x | 待定 | x |\n", wl), ("ZZ", PAGES + "| ZZ | 演练 | one-ops | x | 待定 | x\n", wl),
                              ("ZZ", HEAD + "| ZZ | 演练 \\| one-ops | one-glossary | x | 待定 |\n", wl), ("ZZ", HEAD + "| ZZ | 演练 | one-glossary | x | 待定 | x\\ |\n", wl),
                              ("ZZ", PAGES.replace("\n", "\r\n"), wl), ("ZZ", PAGES + "| ZZ | 演练\r| one-ops | x | 待定 | x |\n", wl),
                              ("ZZ", "<!--\n" + HEAD + other + "-->\n\n" + PAGES, "第 1 " + wl), ("ZZ", "```\n" + HEAD + other + "```\n", "第 1 " + wl), ("ZZ", "~~~\n" + HEAD + other, "第 1 " + wl),
                              ("ZZ", "- x\n" + PAGES, "第 1 " + wl), ("ZZ", "> " + PAGES, "第 1 " + wl), ("ZZ", "   # 缩进标题\n\n" + PAGES, "第 1 " + wl), ("ZZ", "#标题无空格\n\n" + PAGES, "第 1 " + wl),
                              ("ZZ", "[x]: http://x\n\n" + PAGES, "第 1 " + wl), ("ZZ", "﻿# 带 BOM\n\n" + PAGES, "第 1 " + wl), ("ZZ", PAGES.encode("utf-8") + b"\xff\n", "不是合法 UTF-8"),
                              ("ZZ", PAGES + ROW.format("**ZZ**", "one-ops"), "首格不合约定"), ("ZZ", PAGES + ROW.format("`ZZ`", "one-ops"), "首格不合约定"), ("ZZ", PAGES + ROW.format("ZZ​", "one-ops"), "首格不合约定"),
                              ("ZZ", HEAD + ROW.format("ZZ", "one-glossary").replace("演练", "<tr><td>ZY</td><td>x</td><td>one-ops</td>"), wl),
                              ("ZZ", "# 标题 <!--\n\n" + PAGES, "第 1 " + wl), ("ZZ", PAGES + "\f" + other, wl), ("ZZ", PAGES + "　" + other, wl), ("ZZ", PAGES + ROW.format("__ZZ__", "one-ops"), "首格不合约定"),
                              ("ZZ", PAGES + ROW.format("_ZZ_", "one-ops"), "首格不合约定"), ("ZZ", PAGES + ROW.format("某页面", "one-ops"), "首格不合约定"),
                              ("ZZ", HEAD.split("\n")[0] + "\n" + ROW.format("ZZ", "one-glossary"), "不是分隔行"),
                              ("ZU", HEAD + ROW.format("ZU", ""), "负责会话是「」"), ("ZU", HEAD + ROW.format("ZU", "one-nobody"), "换名后不在 sessions.config 里"),
                              ("N1", real, "单子 N1 在 ONLINE_PAGES.md 的负责会话是「roles/ 未列」")):
        code, out = sync(sheet, pages)
        assert code != 0 and why in out, f"{why} 应拒：{out[:200]}"
        assert not os.listdir(os.path.join(dec, "answers")), f"{why}：登记表这一关拒绝时不写答复文件"
    assert len(set(glob.glob(os.path.join(mod, "intent", "*.md"))) - before) == 1, "拒绝时不应再生成片段"
    for pages, why in ((PAGES.replace("|---|---|", "|:---|---:|", 1), "分隔行带对齐冒号"), ("# 在线页登记表\n \t\n## 小标题\n" + PAGES + "\n### 表后标题\n\n", "空行、标题行在表前表后"),
                       (PAGES.rstrip("\n"), "表在文件尾不带换行")):
        code, out = sync("ZZ", pages)
        assert code == 0 and "对账通过" in out, f"{why}应通过：{out[:200]}"
        os.remove(os.path.join(dec, "answers", "ZZ.json"))
    for sheet, old, new in (("ZR", "one-rules", "one-coordinator"), ("ZF", "one-arch-finance", "one-architecture")):   # O23b：登记为旧名的单子经别名表放行，片段写现名
        assert fragment_owner(mod, lambda: sync(sheet, HEAD + ROW.format(sheet, old))) == new, f"登记为旧名 {old} 的单子应经别名写成 {new}"
        os.remove(os.path.join(dec, "answers", f"{sheet}.json"))
    with alias_to_nobody(tree):
        code, out = sync("ZR", HEAD + ROW.format("ZR", "one-rules"))
        assert code != 0 and "换名后不在 sessions.config 里" in out, f"别名指向不存在的会话，decide_sync 应拒：{out[:200]}"
        assert not os.listdir(os.path.join(dec, "answers")), "别名这一关拒绝时不写答复文件"


def fragment_owner(mod, act):
    """执行 act（返回码、输出），断言恰好新生成一个片段，返回其「负责会话」节的值。"""
    before = set(glob.glob(os.path.join(mod, "intent", "*.md")))
    code, out = act()
    assert code == 0, out
    (frag,) = set(glob.glob(os.path.join(mod, "intent", "*.md"))) - before
    return open(frag, encoding="utf-8").read().split("## 负责会话\n\n", 1)[1].split("\n", 1)[0]


@contextlib.contextmanager
def alias_to_nobody(tree):
    """临时把副本 policy.json 的别名 one-rules 指向不存在的会话 one-nobody，用完复原。"""
    path = os.path.join(tree, "policy.json")
    orig = open(path, encoding="utf-8").read()
    policy = json.loads(orig)
    policy["sessions"]["aliases"]["one-rules"] = "one-nobody"
    open(path, "w", encoding="utf-8").write(json.dumps(policy, ensure_ascii=False))
    try:
        yield
    finally:
        open(path, "w", encoding="utf-8").write(orig)


def main():
    with tempfile.TemporaryDirectory() as tree:
        mod = os.path.join(tree, "docs", "modernization")
        os.makedirs(os.path.join(mod, "intent"))
        shutil.copy(os.path.join(BASE, "..", "..", "policy.json"), tree)
        shutil.copytree(os.path.join(BASE, "roles"), os.path.join(mod, "roles"))
        os.makedirs(os.path.join(mod, "decision-digest"))
        shutil.copy(os.path.join(BASE, "decision-digest", "topics.txt"), os.path.join(mod, "decision-digest"))
        open(os.path.join(mod, "decision-digest", "relations.txt"), "w").close()
        for f in ("new_intent.py", "build_decisions.py"):
            shutil.copy(os.path.join(BASE, f), mod)
        for key in ("session", "category", "owner", "paths", "supersedes"):
            code, out = run(tree, swap(GOOD, key))
            assert code != 0 and f"--{key}" in out.split("用法")[0], f"缺 --{key} 应拒并点名：{code} {out[:200]}"
        code, out = run(tree, swap(swap(swap(GOOD, "category"), "owner"), "paths"))
        assert code != 0 and all(f"--{k}" in out.split("用法")[0] for k in ("category", "owner", "paths")), "缺多项应一次列全"
        for key, value in (("category", "杂项"), ("category", ""), ("owner", "nobody"), ("owner", "One_Ops"), ("owner", "one-dev"), ("paths", "/etc"), ("paths", "a/../b"), ("paths", "a b"), ("paths", "a,a"), ("paths", "a,,b"), ("paths", "a,无"), ("paths", "~/x"), ("paths", "./a"), ("paths", "a|b"), ("paths", "a\\b"),("supersedes", "no-such.md")):
            code, out = run(tree, swap(GOOD, key, value))
            assert code != 0, f"--{key}={value!r} 应拒"
        code, out = run(tree, swap(GOOD, "category", "杂项"))
        assert "--category" in out and "--owner" not in out and "--paths" not in out,f"只打印出错的项：{out[:200]}"
        assert not glob.glob(os.path.join(mod, "intent", "*.md")), "拒绝时不应生成片段"
        dec = os.path.join(mod, "rules", "decide")
        os.makedirs(os.path.join(dec, "sheets"))
        for f in ("decide_sync.py", "export_answers.py", "ONLINE_PAGES.md"):
            shutil.copy(os.path.join(BASE, "rules", "decide", f), dec)
        script = os.path.join(dec, "decide_sync.py")
        sync = [sys.executable, script, "GL6", tree]
        sync_args = [a for a in GOOD[1:] if not a.startswith("--owner=")]
        for key in ("category", "paths", "supersedes"):
            r = subprocess.run(sync + swap(sync_args, key), capture_output=True, text=True)
            msg = r.stdout + r.stderr
            assert r.returncode != 0 and f"缺必填项：--{key}（" in msg, f"decide_sync 不带 --{key} 应拒并只列它：{msg[:200]}"
        r = subprocess.run(sync + sync_args, capture_output=True, text=True)
        assert "缺必填项" not in r.stdout + r.stderr and "登记" not in r.stdout + r.stderr, "登记单子不给 --owner 应取登记值越过检查（之后因无答复数据另行失败）"
        r = subprocess.run(sync + sync_args + ["--owner=one-ops"], capture_output=True, text=True)
        assert r.returncode != 0 and "不一致" in r.stdout + r.stderr, "显式 --owner 与登记不一致应拒"
        r = subprocess.run(sync + sync_args + ["--owner=one-glossary"], capture_output=True, text=True)
        assert "不一致" not in r.stdout + r.stderr and "登记" not in r.stdout + r.stderr, "真实登记表 GL6 应取 one-glossary"
        r = subprocess.run([sys.executable, script, "X1", tree, *sync_args], capture_output=True, text=True)
        assert r.returncode != 0 and "单子 X1 没有在 ONLINE_PAGES.md 登记" in r.stdout + r.stderr, "未登记单子应拒并报单子名"
        r = subprocess.run(sync + ["--no-intent"], capture_output=True, text=True)
        assert "缺必填项" not in r.stdout + r.stderr, "--no-intent 不要求四项"
        code, out = run(tree, GOOD + ["--date=2026-01-01"])
        assert code == 0, out
        first = glob.glob(os.path.join(mod, "intent", "*.md"))
        assert len(first) == 1, first
        text = open(first[0], encoding="utf-8").read()
        assert all(f"## {s}\n" in text for s in ("原话", "落点", "类别", "负责会话", "影响路径")), text
        assert "## 类别\n\n流程\n\n## 负责会话\n\none-ops\n\n## 影响路径\n\nscripts/ci、docs/modernization\n" in text, text
        assert "## 取代" not in text, "「无」不写取代节"
        assert build_rule_index.parse_intent("p", text)[2] == {"类别": "流程", "负责会话": "one-ops", "影响路径": "scripts/ci、docs/modernization"}
        code, out = run(tree, swap(swap(swap(swap(GOOD, "category", "授权"), "paths", "无"), "supersedes", os.path.basename(first[0])), "owner", "one-coordinator") + ["--date=2026-01-02"])
        assert code == 0, out
        second = [f for f in glob.glob(os.path.join(mod, "intent", "*.md")) if f != first[0]][0]
        text = open(second, encoding="utf-8").read()
        assert f"## 取代\n\n- {os.path.basename(first[0])}\n" in text and "## 影响路径\n\n无\n" in text and "## 类别\n\n授权\n" in text, text
        for owner in ("one-coordinator", "one-1a-par", "one-merge", "one-glossary"):
            code, out = run(tree, swap(GOOD, "owner", owner))
            assert code == 0, f"{owner} 应认：{out}"
        for old, new in (("one-rules", "one-coordinator"), ("one-arch-finance", "one-architecture")):   # O23b：旧名经别名放行，片段写现名
            assert fragment_owner(mod, lambda: run(tree, swap(GOOD, "owner", old))) == new, f"--owner={old} 应经别名写成 {new}"
        dev = os.path.join(mod, "roles", "dev.md")
        open(dev, "w", encoding="utf-8").write(open(dev, encoding="utf-8").read().replace("## 过渡映射\n", "## 过渡映射\n- one-ghost 担任\n", 1))
        for name in ("one-ghost", "one-dev-par", "one-lead"):   # 只在 roles/ 过渡映射里的名字、第四批新名都不在 sessions.config，应拒
            code, out = run(tree, swap(GOOD, "owner", name))
            assert code != 0 and "--owner" in out, f"未登记名 {name} 应拒：{out[:200]}"
        with alias_to_nobody(tree):
            code, out = run(tree, swap(GOOD, "owner", "one-rules"))
            assert code != 0 and "--owner" in out, f"别名指向不存在的会话应拒：{out[:200]}"
        decide_drill(tree, [a for a in GOOD[1:] if not a.startswith("--owner=")])
    print("ok")


if __name__ == "__main__":
    main()
