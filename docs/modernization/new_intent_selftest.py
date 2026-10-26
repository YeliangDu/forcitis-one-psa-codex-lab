#!/usr/bin/env python3
"""new_intent.py 的自测（二乙 #25 C7）：在临时副本里跑，不写仓库的 intent/。用法：python3 docs/modernization/new_intent_selftest.py（附带的 --self-test 参数忽略）。"""
import glob, os, shutil, subprocess, sys, tempfile

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


def main():
    with tempfile.TemporaryDirectory() as tree:
        mod = os.path.join(tree, "docs", "modernization")
        os.makedirs(os.path.join(mod, "intent"))
        shutil.copy(os.path.join(BASE, "..", "..", "policy.json"), tree)
        shutil.copytree(os.path.join(BASE, "roles"), os.path.join(mod, "roles"))
        os.makedirs(os.path.join(mod, "decision-digest"))
        shutil.copy(os.path.join(BASE, "decision-digest", "topics.txt"), os.path.join(mod, "decision-digest"))
        open(os.path.join(mod, "decision-digest", "relations.txt"), "w").close()
        for f in ("new_intent.py", "build_decision_digest.py", "build_records.py"):
            shutil.copy(os.path.join(BASE, f), mod)
        for key in ("session", "category", "owner", "paths", "supersedes"):
            code, out = run(tree, swap(GOOD, key))
            assert code != 0 and f"--{key}" in out.split("用法")[0], f"缺 --{key} 应拒并点名：{code} {out[:200]}"
        code, out = run(tree, swap(swap(swap(GOOD, "category"), "owner"), "paths"))
        assert code != 0 and all(f"--{k}" in out.split("用法")[0] for k in ("category", "owner", "paths")), "缺多项应一次列全"
        for key, value in (("category", "杂项"), ("category", ""), ("owner", "nobody"), ("owner", "One_Ops"), ("owner", "one-dev"), ("paths", "/etc"), ("paths", "a/../b"), ("paths", "a b"), ("paths", "a,a"), ("paths", "a,,b"), ("supersedes", "no-such.md")):
            code, out = run(tree, swap(GOOD, key, value))
            assert code != 0, f"--{key}={value!r} 应拒"
        assert not glob.glob(os.path.join(mod, "intent", "*.md")), "拒绝时不应生成片段"
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
    print("ok")


if __name__ == "__main__":
    main()
