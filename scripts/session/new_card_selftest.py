#!/usr/bin/env python3
"""new_card.py 的自测（#1163）：在临时副本里跑，不建真卡（gh 换成桩）。用法：python3 scripts/session/new_card_selftest.py（附带的 --self-test 参数忽略）。
覆盖：各拒绝情形；dry-run 的输出原样交给 issue_gate 的卡检查，生成器与门一致；模板多一节时生成器跟着要求；真建卡时 gh 收到的标题、标签、正文。"""
import json, os, shutil, stat, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
sys.path.insert(0, os.path.join(ROOT, "scripts", "merge"))
import issue_gate  # noqa: E402

SECTIONS = [n for n in issue_gate.sections(open(issue_gate.TEMPLATE, encoding="utf-8").read()) if n != "类型"]
GOOD = ["--type=ops", "--scope=session", "--summary=开卡脚本按模板生成", "--session=one-platform"] + [f"--{n}=内容{i}" for i, n in enumerate(SECTIONS)]


def swap(args, key, value=None):
    out = [a for a in args if not a.startswith(f"--{key}=")]
    return out if value is None else out + [f"--{key}={value}"]


def run(tree, args, path=None):
    env = dict(os.environ, PATH=path) if path else None
    r = subprocess.run([sys.executable, os.path.join(tree, "scripts", "session", "new_card.py"), *args], capture_output=True, text=True, cwd=tree, env=env)
    return r.returncode, r.stdout + r.stderr


def make_tree(tmp):
    tree = os.path.join(tmp, "tree")
    for d in ("scripts/session", "scripts/merge", ".github/ISSUE_TEMPLATE"):
        os.makedirs(os.path.join(tree, d))
    for rel in ("scripts/session/new_card.py", "scripts/merge/issue_gate.py", ".github/ISSUE_TEMPLATE/card.md", "policy.json"):
        shutil.copy(os.path.join(ROOT, rel), os.path.join(tree, rel))
    shutil.copytree(os.path.join(ROOT, "docs/rules/playbooks"), os.path.join(tree, "docs/rules/playbooks"))
    return tree


def gh_stub(tmp):
    """gh 桩：把参数与标准输入记进 calls.json，打印假网址。"""
    bin_dir, log = os.path.join(tmp, "bin"), os.path.join(tmp, "calls.json")
    os.makedirs(bin_dir)
    stub = os.path.join(bin_dir, "gh")
    open(stub, "w").write(f"#!{sys.executable}\nimport json, sys\njson.dump({{'argv': sys.argv[1:], 'stdin': sys.stdin.read()}}, open({log!r}, 'w'))\nprint('https://example.invalid/issues/1')\n")
    os.chmod(stub, os.stat(stub).st_mode | stat.S_IXUSR)
    return bin_dir + os.pathsep + os.environ["PATH"], log


def main():
    assert len(SECTIONS) >= 6, SECTIONS
    with tempfile.TemporaryDirectory() as tmp:
        tree, path_with_stub, log = make_tree(tmp), *gh_stub(tmp)
        # 拒绝情形：每条都不建卡（桩没被调用）、点名出错的项
        refusals = [("type", "运维"), ("type", "nope"), ("type", ""), ("scope", "a b"), ("scope", ""), ("summary", "fix the thing"), ("summary", "中" * 31),
                    ("summary", "第一行\n第二行"), ("summary", ""), ("session", "nobody"), ("session", "one-ops"), ("session", "")]
        for key, value in refusals:
            code, out = run(tree, swap(GOOD, key, value) + ["--dry-run"], path_with_stub)
            assert code != 0 and f"--{key}" in out, f"--{key}={value!r} 应拒并点名：{code} {out[:200]}"
        for key in ("type", "scope", "summary", "session"):
            code, out = run(tree, swap(GOOD, key) + ["--dry-run"], path_with_stub)
            assert code != 0 and f"--{key}" in out, f"缺 --{key} 应拒并点名：{out[:200]}"
        for name in SECTIONS:
            for value in (None, "", "   ", "<!-- 只有注释 -->"):
                code, out = run(tree, swap(GOOD, name, value) + ["--dry-run"], path_with_stub)
                assert code != 0 and name in out, f"小节 {name}={value!r} 应拒并点名：{out[:200]}"
        code, out = run(tree, swap(swap(swap(GOOD, SECTIONS[0]), SECTIONS[1]), "scope") + ["--dry-run"], path_with_stub)
        assert code != 0 and SECTIONS[0] in out and SECTIONS[1] in out and "--scope" in out, f"多项问题应一次列全：{out[:300]}"
        # 模板里有的类型，剧本被删则拒
        os.remove(os.path.join(tree, "docs/rules/playbooks/investigation.md"))
        code, out = run(tree, swap(GOOD, "type", "investigation") + ["--dry-run"], path_with_stub)
        assert code != 0 and "剧本" in out, f"没有剧本的类型应拒：{out[:200]}"
        shutil.copy(os.path.join(ROOT, "docs", "rules", "playbooks", "investigation.md"), os.path.join(tree, "docs/rules/playbooks"))
        assert not os.path.exists(log), "拒绝时不应调用 gh"

        # dry-run 与门一致：每个类型的输出原样交给 issue_gate；标题前缀按 CS-7.8
        for kind, prefix in (pair.split(">") for pair in "impl>feat bugfix>fix docs>docs design>docs ops>ci acceptance>test investigation>spike".split()):
            code, out = run(tree, swap(GOOD, "type", kind) + ["--dry-run"], path_with_stub)
            assert code == 0, out
            title, _, body = out.partition("\n\n")
            assert title == f"{prefix}(session): 开卡脚本按模板生成", title
            assert issue_gate.issue_title_problem(title) is None, title
            assert issue_gate.issue_body_problem(body) is None, issue_gate.issue_body_problem(body)
            assert issue_gate.sections(body)["类型"] == kind and issue_gate.sections(body)[SECTIONS[0]] == "内容0", body
        assert not os.path.exists(log), "dry-run 不应调用 gh"

        # 范围可含 . / - ；--file 给小节、命令行覆盖文件
        md = os.path.join(tmp, "card.md")
        open(md, "w", encoding="utf-8").write("".join(f"## {n}\n<!-- 注释 -->\n来自文件{i}\n\n" for i, n in enumerate(SECTIONS)))
        base = [a for a in GOOD if not a.startswith(tuple(f"--{n}=" for n in SECTIONS))]
        code, out = run(tree, swap(base, "scope", "a/b-c.d") + [f"--file={md}", f"--{SECTIONS[1]}=命令行优先", "--dry-run"], path_with_stub)
        assert code == 0 and out.startswith("ci(a/b-c.d): "), out
        got = issue_gate.sections(out.partition("\n\n")[2])
        assert got[SECTIONS[0]] == "来自文件0" and got[SECTIONS[1]] == "命令行优先" and got[SECTIONS[2]] == "来自文件2", got
        code, out = run(tree, base + ["--file=" + os.path.join(tmp, "nope.md"), "--dry-run"], path_with_stub)
        assert code != 0, "读不到 --file 应拒"
        open(md, "w", encoding="utf-8").write(f"## {SECTIONS[0]}\n只有一节\n")
        code, out = run(tree, base + [f"--file={md}", "--dry-run"], path_with_stub)
        assert code != 0 and SECTIONS[1] in out and SECTIONS[0] not in out.split("缺小节")[1], f"文件缺节应只点缺的：{out[:300]}"

        # 小节表读自模板：模板多一节，不给就拒，给了就进正文
        tpl = os.path.join(tree, ".github", "ISSUE_TEMPLATE", "card.md")
        open(tpl, "a", encoding="utf-8").write("\n## 新节\n<!-- 测试用 -->\n")
        code, out = run(tree, GOOD + ["--dry-run"], path_with_stub)
        assert code != 0 and "新节" in out, f"模板多一节应要求：{out[:200]}"
        code, out = run(tree, GOOD + ["--新节=有内容", "--dry-run"], path_with_stub)
        assert code == 0 and issue_gate.sections(out.partition("\n\n")[2])["新节"] == "有内容", out
        shutil.copy(os.path.join(ROOT, ".github/ISSUE_TEMPLATE/card.md"), tpl)

        # 真建卡：gh 收到标题、标签、正文（经标准输入），网址原样打印
        code, out = run(tree, GOOD, path_with_stub)
        assert code == 0 and "https://example.invalid/issues/1" in out, out
        call = json.load(open(log))
        argv = call["argv"]
        assert argv[:2] == ["issue", "create"] and argv[argv.index("--title") + 1] == "ci(session): 开卡脚本按模板生成", argv
        assert argv[argv.index("--label") + 1] == "会话:one-platform", argv
        assert issue_gate.issue_body_problem(call["stdin"]) is None and issue_gate.sections(call["stdin"])["类型"] == "ops", call["stdin"]
        # gh 失败：脚本也失败
        open(os.path.join(tmp, "bin", "gh"), "w").write("#!/bin/sh\necho boom >&2\nexit 4\n")
        code, out = run(tree, GOOD, path_with_stub)
        assert code != 0 and "boom" in out, out

        # 生成器与门同口径：门的拒绝形状，生成器也拒（标题坏、正文缺节各一）
        assert issue_gate.issue_title_problem("fix the thing") and issue_gate.issue_body_problem("## 目标\n x\n")
    print("new_card_selftest: ok")


if __name__ == "__main__":
    main()
