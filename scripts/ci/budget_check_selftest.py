#!/usr/bin/env python3
"""budget_check 自测：临时目录造最小仓库，每项检查一个违例样本必须命中，合规样本必须零违例，模式分支按定稿退出。
环境变量 BUDGET_CHECK 可指向另一份 budget_check.py（破坏验证用），默认同目录那份。"""
import copy, json, os, subprocess, sys, tempfile
DM = "docs/" "modernization"   # split on purpose: relevant_change.py would read a spelled-out fixture path as a real read of the docs
from pathlib import Path

SCRIPT = os.environ.get("BUDGET_CHECK") or str(Path(__file__).with_name("budget_check.py"))
POLICY = {
    "rules": {"claude_md_max_kb": 1.5, "rules_md_max_kb": 5, "rules_md_max_items": 12, "role_file_max_kb": 3,
              "role_files_max": 8, "role_rules_max": 3, "total_max_kb": 20, "total_max_items": 25},
    "gates": {"runtime_max_lines": 1500, "runtime_max_files": 8,
              "max_lines": {"prod_write_gate": 800, "selftest": 1000, "commit_msg_gate": 60}},
    "labels": {"review_passed": "复审通过"},
    "mq": {"enqueue_alert_minutes": 45},
    "ci": {"light_job_cancel_rate_max": 0.05},
    "budget_check": {"mode": "warn"},
    "measures": [{"指标": "轻作业 p90", "基线": "3 分钟", "复盘日": "2999-01-01", "撤销条件": "p90 回落"}],
}
GOOD = {
    "CLAUDE.md": "# 底线\n1. 用中文\n2. 生产只读\n",
    f"{DM}/RULES.md": "# 核心规则\n\n## ① 开卡与交接\n交接见 handoff；入队告警阈值见 mq.enqueue_alert_minutes。\n"
                                   "执行：budget_check、mq\n\n## ② 复审\n第 3 节；见 #1500；共 2 条。\n执行：人工：one-lead／每周\n",
    f"{DM}/roles/lead.md": f"# lead\n独占路径：{DM}/intent/, `CLAUDE.md`\n\n## 专属规则\n- 甲\n- 乙\n",
    f"{DM}/roles/platform.md": f"# platform\n独占路径：scripts/ci/、scripts/gates/**, {DM}/intents/\n",
    ".github/pull_request_template.md": "## 改动说明\n",
    "scripts/gates/prod_write_gate.py": "x = 1\n" * 20,
    "scripts/gates/gates_selftest.py": "y = 1\n" * 10,
    "scripts/review/post_review.py": 'LABEL = "复审通过"\nsh(["gh", "pr", "edit", "1", "--add-label", LABEL])\n',
    "scripts/merge/mq.py": 'def label(n, name, add):\n    sh(["gh", "pr", "edit", n, "--add-label" if add else "--remove-label", name])\n',
    f"{DM}/build_x.py": 'import subprocess\nsubprocess.run(["git", "log"])\n',
    "docs/README.md": "用 `gh pr edit --add-label 复审通过` 的只有 post_review。\n",
}
FAILS = []


def run(files, policy=POLICY):
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for rel, text in files.items():
            (root / rel).parent.mkdir(parents=True, exist_ok=True)
            (root / rel).write_text(text, "utf-8")
        if policy is not None:
            (root / "policy.json").write_text(json.dumps(policy, ensure_ascii=False), "utf-8")
        r = subprocess.run([sys.executable, SCRIPT, "--root", tmp], capture_output=True, text=True)
        viol = [ln for ln in r.stdout.splitlines() if ln and not ln.startswith(("信息", "budget_check："))]
        return r.returncode, r.stdout, viol


def check(name, ok, detail=""):
    print(("通过 " if ok else "失败 ") + name)
    if not ok:
        FAILS.append(name)
        print("    " + detail.replace("\n", "\n    "))


def expect_hit(name, changes, code, path, policy=POLICY):
    rc, out, viol = run({**GOOD, **changes}, policy)
    check(name, any(v.startswith(f"{code} {path}") for v in viol) and rc == 0, out)


def expect_clean(name, changes, policy=POLICY):
    rc, out, viol = run({**GOOD, **changes}, policy)
    check(name, not viol and rc == 0, out)


def with_policy(path, value):
    pol = copy.deepcopy(POLICY)
    node = pol
    for k in path[:-1]:
        node = node[k]
    node[path[-1]] = value
    return pol


RULES = f"{DM}/RULES.md"
ROLE = f"{DM}/roles/"


def main():
    expect_clean("合规样本零违例", {})
    rc, out, viol = run({"CLAUDE.md": "x\n"})
    check("规则文件与 gates 尚无：只报信息不计违例", not viol and f"尚无 {DM}/RULES.md" in out
          and "尚无 scripts/gates/" in out, out)
    # H7
    expect_hit("H7 CLAUDE.md 超 KB", {"CLAUDE.md": "字" * 600}, "H7", "CLAUDE.md")
    expect_hit("H7 RULES 超条数", {RULES: "".join(f"## {i}\n执行：mq\n" for i in range(13))}, "H7", RULES)
    expect_hit("H7 角色文件超份数", {}, "H7", f"{DM}/roles", with_policy(["rules", "role_files_max"], 1))
    expect_hit("H7 角色专属规则超条数", {ROLE + "lead.md": "独占路径：x/\n## 专属规则\n- a\n- b\n- c\n- d\n"},
               "H7", ROLE + "lead.md")
    expect_hit("H7 合计超条数", {}, "H7", "(合计)", with_policy(["rules", "total_max_items"], 3))
    # H1
    expect_hit("H1 单文件超 max_lines", {"scripts/gates/commit_msg_gate.py": "z = 1\n" * 61}, "H1",
               "scripts/gates/commit_msg_gate.py")
    expect_hit("H1 自测多于运行代码", {"scripts/gates/gates_selftest.py": "y = 1\n" * 21}, "H1", "scripts/gates")
    expect_hit("H1 文件数超限", {}, "H1", "scripts/gates", with_policy(["gates", "runtime_max_files"], 0))
    # H8
    expect_hit("H8 RULES 写 1.5 KB", {RULES: "## 体量\nCLAUDE.md 不超过 1.5 KB。\n执行：budget_check\n"}, "H8", RULES + ":2")
    expect_hit("H8 角色写 45 分钟", {ROLE + "lead.md": "独占路径：x/\n入队超 45 分钟告警\n"}, "H8", ROLE + "lead.md:2")
    expect_hit("H8 模板写 1,500 行", {".github/pull_request_template.md": "门不超过 1,500 行\n"}, "H8",
               ".github/pull_request_template.md:1")
    expect_hit("H8 比例写成 5%", {RULES: "## 取消率\n不超过 5%\n执行：light\n"}, "H8", RULES + ":2")
    PROMPT = "scripts/review/PROMPT.md"
    expect_hit("H8 复审提示写 45 分钟", {PROMPT: "复审超 45 分钟无果就查\n"}, "H8", PROMPT + ":1")
    expect_clean("H8 复审提示围栏内的机读头样例豁免", {PROMPT: "```\n复审结论：通过｜必改\n超 45 分钟\n```\n"})
    # H9
    expect_hit("H9 缺执行栏", {RULES: "## 甲\n正文\n"}, "H9", RULES + ":1")
    expect_hit("H9 执行栏为空", {RULES: "## 甲\n执行：\n"}, "H9", RULES + ":1")
    expect_hit("H9 检查名不存在", {RULES: "## 甲\n执行：budget_check、no_such_check\n"}, "H9", RULES + ":1")
    expect_hit("H9 人工未写角色", {RULES: "## 甲\n执行：人工\n"}, "H9", RULES + ":1")
    # H13
    m = POLICY["measures"][0]
    expect_hit("H13 缺撤销条件", {}, "H13", "policy.json:measures[0]",
               with_policy(["measures"], [{k: v for k, v in m.items() if k != "撤销条件"}]))
    expect_hit("H13 字段为空", {}, "H13", "policy.json:measures[0]", with_policy(["measures"], [{**m, "基线": ""}]))
    expect_hit("H13 复盘日格式错", {}, "H13", "policy.json:measures[0]",
               with_policy(["measures"], [{**m, "复盘日": "下周一"}]))
    expect_hit("H13 measures 缺失", {}, "H13", "policy.json", {k: v for k, v in POLICY.items() if k != "measures"})
    rc, out, viol = run(GOOD, with_policy(["measures"], [{"metric": "a", "baseline": "b", "review_date": "2000-01-01",
                                                           "revoke_condition": "c"}]))
    check("H13 英文键可用；到期只报信息", not viol and "已到期" in out, out)
    # H17
    expect_hit("H17 前缀交叠", {ROLE + "dev.md": "独占路径：scripts/ci/budget_check.py\n"}, "H17", ROLE)
    expect_hit("H17 相同路径交叠", {ROLE + "dev.md": "独占路径：./CLAUDE.md\n"}, "H17", ROLE)
    expect_hit("H17 缺声明", {ROLE + "dev.md": "# dev\n"}, "H17", ROLE + "dev.md")
    expect_clean("H17 段名相近不算交叠", {ROLE + "dev.md": f"独占路径：scripts/cifoo/, {DM}/intent2\n"})
    # H21
    expect_hit("H21 别处加标签", {"scripts/merge/mq.py": 'sh(["gh", "pr", "edit", "1", "--add-label", "复审通过"])\n'},
               "H21", "scripts/merge/mq.py:1")
    expect_hit("H21 gh api 经键名删标签", {"scripts/x.sh": 'L=$(jq -r .labels.review_passed policy.json)\n'
                                                           'gh api -X DELETE repos/o/r/issues/1/labels/$L\n'},
               "H21", "scripts/x.sh:2")
    # H23
    expect_hit("H23 docs 下 shell", {"docs/ops/probe.sh": "echo hi\n"}, "H23", "docs/ops/probe.sh")
    expect_hit("H23 docs 下 Python 调 psql", {"docs/ops/q.py": 'import subprocess\nsubprocess.run(["psql", "-c", "x"])\n'},
               "H23", "docs/ops/q.py")
    expect_hit("H23 docs 下 Python 连库", {"docs/ops/q.py": "import psycopg2\n"}, "H23", "docs/ops/q.py")
    expect_hit("H23 docs 下无后缀 shebang", {"docs/ops/run": "#!/bin/sh\necho\n"}, "H23", "docs/ops/run")
    # 模式
    bad = {**GOOD, "docs/ops/probe.sh": "echo\n"}
    check("warn 有违例退出 0", run(bad)[0] == 0)
    check("block 有违例退出 1", run(bad, with_policy(["budget_check", "mode"], "block"))[0] == 1)
    check("block 无违例退出 0", run(GOOD, with_policy(["budget_check", "mode"], "block"))[0] == 0)
    rc, out, _ = run(bad, with_policy(["budget_check", "mode"], "warning"))
    check("mode 非法按 block", rc == 1 and "按 block" in out, out)
    rc, out, _ = run(bad, {k: v for k, v in POLICY.items() if k != "budget_check"})
    check("mode 缺失按 block", rc == 1 and "按 block" in out, out)
    check("policy.json 缺失退出 1", run(GOOD, None)[0] == 1)
    rc, out, _ = run(GOOD, {k: v for k, v in POLICY.items() if k != "labels"})
    check("policy 缺键失败关闭（报违例）", "H21 policy.json" in out, out)
    print(f"budget_check 自测：{'全部通过' if not FAILS else f'{len(FAILS)} 项失败'}")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
