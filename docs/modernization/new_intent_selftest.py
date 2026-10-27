#!/usr/bin/env python3
"""new_intent.py 的自测（二乙 #25 C7）：在临时副本里跑，不写仓库的 intent/。用法：python3 docs/modernization/new_intent_selftest.py（附带的 --self-test 参数忽略）。"""
import glob, json, os, shutil, subprocess, sys, tempfile

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)
import build_records  # noqa: E402

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
    for s in ("ZZ", "ZU"):
        json.dump({"items": [{"no": f"{s}-01", "opts": [{"label": "甲"}]}]}, open(os.path.join(dec, "sheets", f"{s}.json"), "w", encoding="utf-8"), ensure_ascii=False)
        out = os.path.join(tree, "out" + s)
        os.makedirs(os.path.join(out, "answers"))
        os.makedirs(os.path.join(out, "answers-submit"))
        json.dump({"choice": 0, "note": ""}, open(os.path.join(out, "answers", f"{s}@{s}-01.json"), "w"))
        pick = {"choice": 0, "label": "甲", "recommended": True, "defaulted": False}
        json.dump({"number": "N1", "at": "2026-01-01T00:00:00", "picks": {f"{s}~{s}-01": pick}}, open(os.path.join(out, "answers-submit", "N1.json"), "w", encoding="utf-8"), ensure_ascii=False)

    def sync(sheet, pages):
        open(os.path.join(dec, "ONLINE_PAGES.md"), "w", encoding="utf-8").write(pages)
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
    dup = "|ZZ|演练|one-ops|x|待定|x|\n"
    for sheet, pages, why in (("ZZ", PAGES.replace("负责会话", "负责人"), "表头"), ("ZY", PAGES, "没有在 ONLINE_PAGES.md 登记"), ("ZU", PAGES, "roles/ 未列"), ("ZZ", PAGES + ROW.format("ZZ", "one-ops"), "重复登记"),
                              ("ZZ", PAGES + dup, "重复登记"), ("ZZ", PAGES + "  " + ROW.format("ZZ", "one-ops"), "重复登记"), ("ZZ", PAGES + "| ZZ | 演练 | one-ops | x | 待定\n", "格式不规范"),
                              ("ZZ", PAGES + "ZZ | 演练 | one-ops | x | 待定 | x |\n", "格式不规范"), ("ZZ", PAGES + "| ZZ | 演练 | one-ops | x | 待定 | x\n", "格式不规范"),
                              ("ZZ", PAGES + ROW.format("**ZZ**", "one-ops"), "首格不合约定"), ("ZZ", PAGES + ROW.format("`ZZ`", "one-ops"), "首格不合约定"), ("ZZ", PAGES + ROW.format("ZZ​", "one-ops"), "首格不合约定"),
                              ("ZZ", PAGES + ROW.format("zz", "one-ops"), "重复登记"),
                              ("ZZ", PAGES + ROW.format("_ZZ_", "one-ops"), "首格不合约定"), ("ZZ", PAGES + ROW.format("__ZZ__", "one-ops"), "首格不合约定"), ("ZZ", PAGES + ROW.format("某页面", "one-ops"), "首格不合约定"),
                              ("ZZ", HEAD + "| ZZ | 演练 \\| one-ops | one-glossary | x | 待定 |\n", "格式不规范"), ("ZZ", ZU_ONLY + "\n" + ROW.format("ZZ", "one-ops"), "块之后"),
                              ("ZZ", ZU_ONLY + "\n说明\n" + ROW.format("ZZ", "one-ops"), "块之后"), ("ZZ", ZU_ONLY + "说明\n" + ROW.format("ZZ", "one-ops"), "紧接着一行非表格行"),
                              ("ZZ", ZU_ONLY + "\n<!--\n" + ROW.format("ZZ", "one-ops") + "-->\n", "HTML、注释或代码块"), ("ZZ", ZU_ONLY + "\n```\n" + ROW.format("ZZ", "one-ops") + "```\n", "HTML、注释或代码块"),
                              ("ZZ", "<!--\n" + HEAD + ROW.format("ZZ", "one-ops") + "\n-->\n<table><tr><td>ZZ</td><td>one-glossary</td></tr></table>\n", "HTML、注释或代码块"),
                              ("ZZ", "~~~\n" + HEAD + ROW.format("ZZ", "one-ops") + "\n~~~\n", "HTML、注释或代码块"), ("ZZ", "   <!--\n" + HEAD + ROW.format("ZZ", "one-ops") + "\n-->\n", "HTML、注释或代码块"),
                              ("ZZ", "- <!--\n  " + HEAD.replace("\n|", "\n  |") + "  " + ROW.format("ZZ", "one-ops") + "\n  -->\n- <table><tr><td>ZZ</td><td>one-glossary</td></tr></table>\n", "HTML、注释或代码块"),
                              ("ZZ", "- ```\n  " + HEAD.replace("\n|", "\n  |") + "  " + ROW.format("ZZ", "one-ops"), "HTML、注释或代码块"), ("ZZ", "> 1. ~~~\n" + HEAD + ROW.format("ZZ", "one-ops") + "\n", "HTML、注释或代码块"),
                              ("ZZ", ZU_ONLY + "    " + ROW.format("ZZ", "one-ops"), "缩进"), ("ZZ", ZU_ONLY + "\t" + ROW.format("ZZ", "one-ops"), "缩进"), ("ZZ", ZU_ONLY + "  \t" + ROW.format("ZZ", "one-ops"), "缩进"),
                              ("ZZ", ZU_ONLY + "\f" + ROW.format("ZZ", "one-ops"), "缩进"), ("ZZ", ZU_ONLY + "\xa0" + ROW.format("ZZ", "one-ops"), "缩进"), ("ZZ", ZU_ONLY + "　" + ROW.format("ZZ", "one-ops"), "缩进"),
                              ("ZZ", ZU_ONLY + ROW.format("ZZ", "one-ops").rstrip("\n") + "\xa0\n", "缩进"),
                              ("ZZ", HEAD.split("\n")[0] + "\n" + ROW.format("ZZ", "one-glossary"), "不是分隔行"),
                              ("ZU", HEAD + "| ZU | 演练 | one-glossary | x | 待定 |\n", "格式不规范"), ("ZU", HEAD + ROW.format("ZU", ""), "负责会话是「」"),
                              ("ZU", HEAD + ROW.format("ZU", "one-nobody"), "不在 roles/ 过渡映射"), ("N1", real, "单子 N1 在 ONLINE_PAGES.md 的负责会话是「roles/ 未列」")):
        code, out = sync(sheet, pages)
        assert code != 0 and why in out, f"{why} 应拒：{out[:200]}"
        assert not os.listdir(os.path.join(dec, "answers")), f"{why}：登记表这一关拒绝时不写答复文件"
    assert len(set(glob.glob(os.path.join(mod, "intent", "*.md"))) - before) == 1, "拒绝时不应再生成片段"
    code, out = sync("ZZ", PAGES.replace("|---|---|", "|:---|---:|", 1))
    assert code == 0 and "对账通过" in out, f"分隔行带对齐冒号应通过：{out[:200]}"


def main():
    with tempfile.TemporaryDirectory() as tree:
        mod = os.path.join(tree, "docs", "modernization")
        os.makedirs(os.path.join(mod, "intent"))
        shutil.copy(os.path.join(BASE, "..", "..", "policy.json"), tree)
        shutil.copytree(os.path.join(BASE, "roles"), os.path.join(mod, "roles"))
        os.makedirs(os.path.join(mod, "decision-digest"))
        shutil.copy(os.path.join(BASE, "decision-digest", "topics.txt"), os.path.join(mod, "decision-digest"))
        open(os.path.join(mod, "decision-digest", "relations.txt"), "w").close()
        for f in ("new_intent.py", "build_decisions.py", "build_records.py"):
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
        assert build_records.parse_intent("p", text)[2] == {"类别": "流程", "负责会话": "one-ops", "影响路径": "scripts/ci、docs/modernization"}
        code, out = run(tree, swap(swap(swap(swap(GOOD, "category", "授权"), "paths", "无"), "supersedes", os.path.basename(first[0])), "owner", "one-coordinator") + ["--date=2026-01-02"])
        assert code == 0, out
        second = [f for f in glob.glob(os.path.join(mod, "intent", "*.md")) if f != first[0]][0]
        text = open(second, encoding="utf-8").read()
        assert f"## 取代\n\n- {os.path.basename(first[0])}\n" in text and "## 影响路径\n\n无\n" in text and "## 类别\n\n授权\n" in text, text
        for owner in ("one-coordinator", "one-1a-par", "one-merge", "one-glossary"):
            code, out = run(tree, swap(GOOD, "owner", owner))
            assert code == 0, f"{owner} 应认：{out}"
        decide_drill(tree, [a for a in GOOD[1:] if not a.startswith("--owner=")])
    print("ok")


if __name__ == "__main__":
    main()
