#!/usr/bin/env python3
"""new_intent.py 的自测（二乙 #25 C7）：在临时副本里跑，不写仓库的 docs/decisions/。用法：python3 scripts/session/new_intent_selftest.py（附带的 --self-test 参数忽略）。"""
import contextlib, glob, json, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ROOT = os.path.join(HERE, "..", "..")
GOOD = ["--session=one-platform", "--category=流程", "--owner=one-platform", "--paths=scripts/ci,docs/decisions", "--supersedes=无"]
TEXT = ["用户原话", "落点说明"]


def run(tree, args):
    r = subprocess.run([sys.executable, os.path.join(tree, "scripts", "session", "new_intent.py"), *args, *TEXT], capture_output=True, text=True, cwd=tree)
    return r.returncode, r.stdout + r.stderr


def swap(args, key, value=None):
    out = [a for a in args if not a.startswith(f"--{key}=")]
    return out if value is None else out + [f"--{key}={value}"]


def fragment_owner(mod, act):
    """执行 act（返回码、输出），断言恰好新生成一个片段，返回其「负责会话」节的值。"""
    before = set(glob.glob(os.path.join(mod, "*.md")))
    code, out = act()
    assert code == 0, out
    (frag,) = set(glob.glob(os.path.join(mod, "*.md"))) - before
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
        mod, digest = os.path.join(tree, "docs", "decisions"), os.path.join(tree, "docs", "decisions", "decision-digest")
        os.makedirs(digest)
        shutil.copy(os.path.join(ROOT, "policy.json"), tree)
        shutil.copytree(os.path.join(ROOT, "docs", "rules", "roles"), os.path.join(tree, "docs", "rules", "roles"))
        shutil.copy(os.path.join(ROOT, "docs", "decisions", "decision-digest", "topics.txt"), digest)
        for name in ("relations.txt", "archived.txt", "standing.txt"):
            open(os.path.join(digest, name), "w").close()
        os.makedirs(os.path.join(tree, "scripts", "session"))
        for f in ("new_intent.py", "build_decisions.py"):
            shutil.copy(os.path.join(HERE, f), os.path.join(tree, "scripts", "session"))
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
        assert not glob.glob(os.path.join(mod, "*.md")), "拒绝时不应生成片段"
        code, out = run(tree, GOOD + ["--date=2026-01-01"])
        assert code == 0, out
        first = glob.glob(os.path.join(mod, "*.md"))
        assert len(first) == 1, first
        text = open(first[0], encoding="utf-8").read()
        assert all(f"## {s}\n" in text for s in ("原话", "落点", "类别", "负责会话", "影响路径")), text
        assert "## 类别\n\n流程\n\n## 负责会话\n\none-platform\n\n## 影响路径\n\nscripts/ci、docs/decisions\n" in text, text
        assert "## 取代" not in text, "「无」不写取代节"
        code, out = run(tree, swap(swap(swap(swap(GOOD, "category", "授权"), "paths", "无"), "supersedes", os.path.basename(first[0])), "owner", "one-lead") + ["--date=2026-01-02"])
        assert code == 0, out
        second = [f for f in glob.glob(os.path.join(mod, "*.md")) if f != first[0]][0]
        text = open(second, encoding="utf-8").read()
        assert f"## 取代\n\n- {os.path.basename(first[0])}\n" in text and "## 影响路径\n\n无\n" in text and "## 类别\n\n授权\n" in text, text
        code, out = run(tree, GOOD + ["--rules=RULE-1,ABC-RULE-22", "--date=2026-01-03"])   # #742：规则号只核格式（规则索引已删）
        assert code == 0 and "## 规则\n\nRULE-1、ABC-RULE-22\n" in open(sorted(glob.glob(os.path.join(mod, "2026-01-03-*.md")))[0], encoding="utf-8").read(), out
        code, out = run(tree, GOOD + ["--rules=rule-1"])
        assert code != 0 and "--rules" in out, "规则号格式不合应拒"
        for owner in ("one-lead", "one-design", "one-platform", "one-dev-org"):
            code, out = run(tree, swap(GOOD, "owner", owner))
            assert code == 0, f"{owner} 应认：{out}"
        for old, new in (("one-rules", "one-lead"), ("one-arch-finance", "one-design"), ("one-coordinator", "one-lead"), ("one-1a-par", "one-dev-org"), ("one-dev-par", "one-dev-org"),
                         ("one-leader", "one-lead"), ("one-glossary", "one-design"), ("one-ops", "one-platform"), ("one-merge", "one-platform"), ("one-platform-file", "one-dev-org")):   # O23b、#736、#744：旧名经别名放行，片段写现名
            assert fragment_owner(mod, lambda: run(tree, swap(GOOD, "owner", old))) == new, f"--owner={old} 应经别名写成 {new}"
        dev = os.path.join(tree, "docs", "rules", "roles", "dev.md")
        open(dev, "w", encoding="utf-8").write(open(dev, encoding="utf-8").read().replace("## 过渡映射\n", "## 过渡映射\n- one-ghost 担任\n", 1))
        for name in ("one-ghost", "one-dev-zz", "one-leadx"):   # 只在 roles/ 过渡映射里的名字、未登记名都不在 sessions.config，应拒
            code, out = run(tree, swap(GOOD, "owner", name))
            assert code != 0 and "--owner" in out, f"未登记名 {name} 应拒：{out[:200]}"
        with alias_to_nobody(tree):
            code, out = run(tree, swap(GOOD, "owner", "one-rules"))
            assert code != 0 and "--owner" in out, f"别名指向不存在的会话应拒：{out[:200]}"
    print("ok")


if __name__ == "__main__":
    main()
