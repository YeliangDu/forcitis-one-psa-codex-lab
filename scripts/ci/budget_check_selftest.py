#!/usr/bin/env python3
"""budget_check 自测：临时目录造最小仓库，每项检查一个违例样本必须命中，合规样本必须零违例，模式分支按定稿退出。
环境变量 BUDGET_CHECK 可指向另一份 budget_check.py（破坏验证用），默认同目录那份。"""
import copy, json, os, subprocess, sys, tempfile
DM = "docs/" "modernization"   # 拆开写：relevant_change.py 会把写全的夹具路径当成对 docs 的真实读取
from pathlib import Path

SCRIPT = os.environ.get("BUDGET_CHECK") or str(Path(__file__).with_name("budget_check.py"))
POLICY = {
    "rules": {"claude_md_max_kb": 1.5, "rules_md_max_kb": 5, "rules_md_max_items": 12, "role_file_max_kb": 3,
              "role_files_max": 8, "role_rules_max": 3, "total_max_kb": 20, "total_max_items": 25},
    "gates": {"runtime_max_lines": 1500, "runtime_max_files": 8,
              "max_lines": {"prod_write_gate": 800, "selftest": 1000, "commit_msg_gate": 60, "stop_check": 120,
                            "post_review": 200, "budget_check": 450, "light_sh": 120, "build_x": 90}},
    "labels": {"review_passed": "复审通过"},
    "mq": {"enqueue_alert_minutes": 45, "max_lines_total": 2500},
    "tools_lib": {"max_lines_total": 1800},
    "ci": {"light_job_cancel_rate_max": 0.05},
    "budget": {"review_days_after_enable": [7, 14]},
    "budget_check": {"mode": "warn"},
    "measures": [{"指标": "轻作业 p90", "基线": "3 分钟", "复盘日": "2999-01-01", "撤销条件": "p90 回落"}],
}
GOOD = {
    "CLAUDE.md": "# 底线\n1. 用中文\n2. 生产只读\n",
    f"{DM}/RULES.md": "# 核心规则\n\n## ① 开卡与交接\n交接见 handoff；入队告警阈值见 mq.enqueue_alert_minutes。\n"
                      "执行：budget_check、mq\n\n## ② 复审\n第 3 节；见 #1500；共 2 条。\n执行：人工：one-lead／每周\n",
    f"{DM}/roles/lead.md": f"# lead\n独占路径：{DM}/intent/, `CLAUDE.md`\n\n## 专属规则\n- 甲\n- 乙\n",
    f"{DM}/roles/platform.md": f"# platform\n独占路径：scripts/ci/、scripts/gates/**, {DM}/intents/\n本角色无专属规则。\n",
    ".github/pull_request_template.md": "## 改动说明\n",
    ".github/workflows/ci.yml": "on:\n  push:\n    branches: [main]\njobs:\n  light:\n    runs-on: x\n  backend:\n    runs-on: x\n",
    "scripts/gates/prod_write_gate.py": "x = 1\n" * 20,
    "scripts/gates/gates_selftest.py": "y = 1\n" * 10,
    "scripts/hooks/stop_check.py": "z = 1\n" * 5,
    "scripts/ci/acceptance_guard.py": "a = 1\n",
    "backend/src/test/java/x/LegacyShapeTest.java": "class LegacyShapeTest {}\n",
    "scripts/review/post_review.py": 'LABEL = "复审通过"\nsh(["gh", "pr", "edit", "1", "--add-label", LABEL])\n',
    "scripts/merge/mq.py": 'def label(n, name, add):\n    sh(["gh", "pr", "edit", n, "--add-label" if add else "--remove-label", name])\n',
    f"{DM}/build_x.py": 'import subprocess\nsubprocess.run(["git", "log"])\n',
    f"{DM}/gen/helper.py": "def f():\n    return 1\n",
    f"{DM}/gen/make.py": "#!/usr/bin/env python3\nimport helper\n",
    "docs/guard/Example.java": "class Example { void run() {} }\n",
    "docs/README.md": "用 `gh pr edit --add-label 复审通过` 的只有 post_review。\n",
}
FAILS = []


def run(files, policy=POLICY, env=None):
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for rel, text in files.items():
            (root / rel).parent.mkdir(parents=True, exist_ok=True)
            (root / rel).write_text(text, "utf-8")
        if policy is not None:
            (root / "policy.json").write_text(json.dumps(policy, ensure_ascii=False), "utf-8")
        r = subprocess.run([sys.executable, SCRIPT, "--root", tmp], capture_output=True, text=True, env={**os.environ, **(env or {})})
        viol = [ln for ln in r.stdout.splitlines() if ln and not ln.startswith(("信息", "budget_check："))]
        return r.returncode, r.stdout + r.stderr, viol


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
PROMPT = "scripts/review/PROMPT.md"
HEADER = "复审结论：通过｜必改\ncommit：<40 位 sha>\n复审者：<模型>-<首轮|终审>-<标识>\n"


def main():
    expect_clean("合规样本零违例", {})
    rc, out, viol = run({"CLAUDE.md": "x\n"})
    check("规则文件与 gates、hooks 尚无：只报信息不计违例", not viol and f"尚无 {DM}/RULES.md" in out
          and "尚无 scripts/gates/" in out, out)
    # H7
    expect_hit("H7 CLAUDE.md 超 KB", {"CLAUDE.md": "字" * 600}, "H7", "CLAUDE.md")
    expect_hit("H7 RULES 超 KB", {RULES: "## 甲\n执行：mq\n" + "字" * 1800}, "H7", RULES)
    expect_hit("H7 RULES 超条数", {RULES: "".join(f"## {i}\n执行：mq\n" for i in range(13))}, "H7", RULES)
    expect_hit("H7 角色文件超份数", {}, "H7", f"{DM}/roles", with_policy(["rules", "role_files_max"], 1))
    expect_hit("H7 角色文件超 KB", {ROLE + "lead.md": "独占路径：x/\n" + "字" * 1100}, "H7", ROLE + "lead.md")
    expect_hit("H7 角色专属规则超条数", {ROLE + "lead.md": "独占路径：x/\n## 专属规则\n- a\n- b\n- c\n- d\n"},
               "H7", ROLE + "lead.md")
    for name, text, no in (("缩进", "## 甲\n执行：mq\n ## 乙\n执行：mq\n", 3), ("制表符", "## 甲\n执行：mq\n##\t乙\n", 3),
                           ("空标题", "## 甲\n执行：mq\n##\n", 3), ("setext", "## 甲\n执行：mq\n\n乙\n---\n", 5)):
        expect_hit(f"H7 非标准二级标题（{name}）报格式违例", {RULES: text}, "H7", f"{RULES}:{no}")
    expect_clean("H7 分隔线、表格不算 setext", {RULES: "## 甲\n执行：mq\n\n---\n\n| a |\n|---|\n- x\n---\n"})
    for name, item in (("「+ 」", "+ c"), ("「1) 」", "1) c"), ("缩进", "  - c")):
        expect_hit(f"H7 专属规则下非标准列表项（{name}）报格式违例",
                   {ROLE + "lead.md": f"独占路径：x/\n## 专属规则\n- a\n{item}\n"}, "H7", ROLE + "lead.md:4")
    expect_hit("H7 合计超条数", {}, "H7", "(合计)", with_policy(["rules", "total_max_items"], 3))
    expect_hit("H7 合计超 KB", {}, "H7", "(合计)", with_policy(["rules", "total_max_kb"], 0.3))
    expect_clean("H7 CLAUDE.md 底线摘要不计条数（第 11 节）", {"CLAUDE.md": "# 底线\n" + "".join(f"{i}. 条\n" for i in range(1, 7))},
                 with_policy(["rules", "total_max_items"], 4))
    # H1：scripts/gates 与 scripts/hooks 合计（运行代码 gates 20 + hooks 5 = 25 行，2 个文件）
    expect_hit("H1 单文件超 max_lines", {"scripts/gates/commit_msg_gate.py": "z = 1\n" * 61}, "H1",
               "scripts/gates/commit_msg_gate.py")
    expect_hit("H1 hooks 下单文件超 max_lines", {"scripts/hooks/stop_check.py": "z = 1\n" * 121}, "H1",
               "scripts/hooks/stop_check.py")
    expect_hit("H1 自测多于运行代码", {"scripts/gates/gates_selftest.py": "y = 1\n" * 26}, "H1", "scripts/gates+hooks")
    expect_hit("H1 文件数含 hooks", {}, "H1", "scripts/gates+hooks", with_policy(["gates", "runtime_max_files"], 1))
    expect_hit("H1 行数含 hooks", {}, "H1", "scripts/gates+hooks", with_policy(["gates", "runtime_max_lines"], 24))
    expect_hit("H1 行数含 .sh", {"scripts/gates/x.sh": "echo\n" * 10}, "H1", "scripts/gates+hooks",
               with_policy(["gates", "runtime_max_lines"], 30))
    files = {k: v for k, v in GOOD.items() if not k.startswith("scripts/gates/")}
    rc, out, viol = run({**files, "scripts/hooks/stop_check.py": "z = 1\n" * 30}, with_policy(["gates", "runtime_max_lines"], 24))
    check("H1 只有 hooks 也照样超限报违例（不降成信息）", any(v.startswith("H1 scripts/gates+hooks") for v in viol), out)
    # H23 后半：外围工具单文件与总行数
    expect_hit("H23 门外工具超 max_lines", {"scripts/review/post_review.py": "p = 1\n" * 201}, "H23", "scripts/review/post_review.py")
    expect_hit("H23 docs 下 .py 超 max_lines", {f"{DM}/build_x.py": "b = 1\n" * 91}, "H23", f"{DM}/build_x.py")
    expect_hit("H23 名_后缀 键（light_sh）", {"scripts/ci/light.sh": "echo\n" * 121}, "H23", "scripts/ci/light.sh")
    expect_hit("H23 mq 总行数（merge+queue）", {"scripts/queue/merge_train.py": "q = 1\n" * 30}, "H23", "scripts/merge+queue",
               with_policy(["mq", "max_lines_total"], 30))
    own = Path(SCRIPT).read_text("utf-8")
    expect_clean("H23 budget_check 自身在 gates.max_lines.budget_check 内", {"scripts/ci/budget_check.py": own})
    expect_hit("H23 budget_check 自身计入行数上限", {"scripts/ci/budget_check.py": own}, "H23", "scripts/ci/budget_check.py",
               with_policy(["gates", "max_lines", "budget_check"], 100))
    rc, out, viol = run(GOOD)
    check("H23 tools_lib 在仓库外：输出一行信息", "信息 H23 tools_lib" in out and "tools_lib.max_lines_total" in out, out)
    rc, out, viol = run(GOOD, with_policy(["mq"], {"enqueue_alert_minutes": 45}))
    check("H23 缺 mq.max_lines_total 失败关闭（报违例）", any(v.startswith("H23 policy.json") for v in viol), out)
    # H8
    expect_hit("H8 RULES 写 1.5 KB", {RULES: "## 体量\nCLAUDE.md 不超过 1.5 KB。\n执行：budget_check\n"}, "H8", RULES + ":2")
    expect_hit("H8 角色写 45 分钟", {ROLE + "lead.md": "独占路径：x/\n入队超 45 分钟告警\n"}, "H8", ROLE + "lead.md:2")
    expect_hit("H8 模板写 1,500 行", {".github/pull_request_template.md": "门不超过 1,500 行\n"}, "H8",
               ".github/pull_request_template.md:1")
    expect_hit("H8 不带单位的 ≥1000 整数", {".github/pull_request_template.md": "门不超过 1500\n"}, "H8",
               ".github/pull_request_template.md:1")
    expect_hit("H8 比例写成 5%", {RULES: "## 取消率\n不超过 5%\n执行：light\n"}, "H8", RULES + ":2")
    expect_hit("H8 CLAUDE.md 写 45 分钟", {"CLAUDE.md": "# 底线\n1. 入队超 45 分钟告警\n"}, "H8", "CLAUDE.md:2")
    expect_hit("H8 Issue 模板写 45 分钟", {".github/ISSUE_TEMPLATE/bug.md": "超 45 分钟\n"}, "H8", ".github/ISSUE_TEMPLATE/bug.md:1")
    expect_hit("H8 复审提示写 45 分钟", {PROMPT: "复审超 45 分钟无果就查\n"}, "H8", PROMPT + ":1")
    expect_clean("H8 复审提示的机读头（含围栏）不报", {PROMPT: "## 输出\n```\n" + HEADER + "```\n"})
    expect_hit("H8 复审提示围栏里的数值照报", {PROMPT: "```\n" + HEADER + "超 45 分钟\n```\n"}, "H8", PROMPT + ":5")
    expect_hit("H8 复审提示机读头后的正文照报", {PROMPT: "```\n" + HEADER + "```\n正文：超 45 分钟\n"}, "H8", PROMPT + ":6")
    expect_hit("H8 复审提示未闭合围栏后照报", {PROMPT: "```\n" + HEADER + "正文：超 45 分钟\n"}, "H8", PROMPT + ":5")
    expect_hit("H8 复审提示单行围栏后照报", {PROMPT: "```复审结论：通过```\n超 45 分钟\n"}, "H8", PROMPT + ":2")
    expect_hit("H8 RULES 里的围栏不豁免", {RULES: "## 甲\n```\n超 45 分钟\n```\n执行：mq\n"}, "H8", RULES + ":3")
    # H9
    expect_hit("H9 缺执行栏", {RULES: "## 甲\n正文\n"}, "H9", RULES + ":1")
    expect_hit("H9 执行栏为空", {RULES: "## 甲\n执行：\n"}, "H9", RULES + ":1")
    expect_hit("H9 检查名不存在", {RULES: "## 甲\n执行：budget_check、no_such_check\n"}, "H9", RULES + ":1")
    expect_hit("H9 尚不存在的检查名不放行", {RULES: "## 甲\n执行：janitor\n"}, "H9", RULES + ":1")
    expect_hit("H9 workflow 的 on 段不算检查名", {RULES: "## 甲\n执行：push\n"}, "H9", RULES + ":1")
    expect_hit("H9 人工未写角色", {RULES: "## 甲\n执行：人工\n"}, "H9", RULES + ":1")
    expect_clean("H9 实存检查名从仓库生成", {RULES: "## 甲\n执行：acceptance_guard.py、LegacyShapeTest、light、ci、prod_write_gate --check\n"})
    expect_clean("H9 gates.max_lines 的键可作检查名", {RULES: "## 甲\n执行：commit_msg_gate\n"})
    with tempfile.TemporaryDirectory() as home:   # 工具库：只认含入口文件的一级子目录；dir 须在 ~/.modernize/ 下
        lib = Path(home) / ".modernize" / "lib"
        for d, entry in (("janitor", "janitor.py"), ("budget_level", "__main__.py"), ("stop_sh", "stop_sh.sh"),
                         ("legacy", "README.md"), (".hidden", ".hidden.py"), ("_priv", "_priv.py")):
            (lib / d).mkdir(parents=True)
            (lib / d / entry).write_text("x\n", "utf-8")
        (lib / "board").write_text("x\n", "utf-8")
        lib_pol, env = with_policy(["tools_lib", "dir"], "~/.modernize/lib"), {"HOME": home}
        rc, out, viol = run({**GOOD, RULES: "## 甲\n执行：janitor、budget_level、stop_sh\n"}, lib_pol, env)
        check("H9 认含入口文件的工具库子目录（~ 展开），出本机口径信息", not viol and "本机口径" in out
              and "未配置或不存在" not in out, out)
        for bad in ("board", "handoff", "legacy", ".hidden", "_priv"):
            rc, out, viol = run({**GOOD, RULES: f"## 甲\n执行：{bad}\n"}, lib_pol, env)
            check(f"H9 工具库里不算检查的「{bad}」照报", any(v.startswith(f"H9 {RULES}:1") for v in viol), out)
        for wide in ("~", "~/", "~/.modernize", "~/.modernize/", "~/.modernize/../x", "/abs/lib", "lib", 3):
            rc, out, viol = run({**GOOD, RULES: "## 甲\n执行：janitor\n"}, with_policy(["tools_lib", "dir"], wide), env)
            check(f"H9 tools_lib.dir={wide!r} 过宽或越界报格式违例", any(v.startswith("H9 policy.json") for v in viol), out)
    missing = with_policy(["tools_lib", "dir"], "~/.modernize/no-such-lib")
    for name, pol in (("键缺失", POLICY), ("目录不存在", missing)):
        rc, out, viol = run({**GOOD, RULES: "## 甲\n执行：janitor\n"}, pol, {"HOME": "/nonexistent-home"})
        check(f"H9 tools_lib.dir {name}：白名单外照报，另出一行信息", any(v.startswith(f"H9 {RULES}:1") for v in viol)
              and "信息 H9 policy.tools_lib.dir 未配置或不存在，工具库检查名未纳入" in out, out)
    expect_clean("H9 「人工」写在执行栏第一个不崩溃", {RULES: "## 甲\n执行：人工：one-lead／每周\n"})
    rc, out, viol = run({**GOOD, RULES: "## 甲\n执行：no_such、人工：one-lead／每周\n"})
    check("H9 「不存在的名、人工」只报一次", [v for v in viol if v.startswith("H9")] == [
        f"H9 {RULES}:1 「甲」执行栏引用的检查名「no_such」不存在"] and "Traceback" not in out, out)
    expect_hit("H9 RULES 为空报违例", {RULES: ""}, "H9", RULES)
    expect_hit("H9 RULES 全空白报违例", {RULES: "\n  \n\t\n"}, "H9", RULES)
    expect_hit("H9 RULES 无二级标题不静默", {RULES: "# 规则\n1. 写测试\n2. 不绕过\n"}, "H9", RULES)
    expect_hit("H9 「## 本角色专属规则」不静默", {ROLE + "lead.md": "独占路径：x/\n## 本角色专属规则\n- a\n"}, "H9",
               ROLE + "lead.md:2")
    expect_hit("H9 加粗「专属规则」不静默", {ROLE + "lead.md": "独占路径：x/\n**专属规则**\n- a\n"}, "H9", ROLE + "lead.md:2")
    expect_hit("H9 「### 专属规则」不静默", {ROLE + "lead.md": "独占路径：x/\n### 专属规则\n- a\n"}, "H9", ROLE + "lead.md")
    # H13
    m = POLICY["measures"][0]
    expect_hit("H13 缺撤销条件", {}, "H13", "policy.json:measures[0]",
               with_policy(["measures"], [{k: v for k, v in m.items() if k != "撤销条件"}]))
    expect_hit("H13 字段为空", {}, "H13", "policy.json:measures[0]", with_policy(["measures"], [{**m, "基线": ""}]))
    expect_hit("H13 基线为 null", {}, "H13", "policy.json:measures[0]", with_policy(["measures"], [{**m, "基线": None}]))
    expect_hit("H13 复盘日格式错", {}, "H13", "policy.json:measures[0]",
               with_policy(["measures"], [{**m, "复盘日": "下周一"}]))
    expect_hit("H13 复盘日引用的键不是整数", {}, "H13", "policy.json:measures[0]",
               with_policy(["measures"], [{**m, "复盘日": "labels.review_passed"}]))
    rc, out, viol = run(GOOD, with_policy(["measures"], [{**m, "复盘日": "2026-13-45"}, {**m, "撤销条件": None}]))
    check("H13 日期非法逐项报，不中断后面的项", any(v.startswith("H13 policy.json:measures[0]") for v in viol)
          and any(v.startswith("H13 policy.json:measures[1]") for v in viol), out)
    rc, out, viol = run(GOOD, with_policy(["measures"], [{**m, "复盘日": "budget.review_days_after_enable"}]))
    check("H13 复盘日写键引用合规，只报信息", not viol and "复盘日引用" in out, out)
    expect_hit("H13 measures 缺失", {}, "H13", "policy.json", {k: v for k, v in POLICY.items() if k != "measures"})
    rc, out, viol = run(GOOD, with_policy(["measures"], [{"metric": "a", "baseline": "b", "review_date": "2000-01-01",
                                                           "revoke_condition": "c"}]))
    check("H13 英文键可用；到期只报信息", not viol and "已到期" in out, out)
    # H17
    expect_hit("H17 前缀交叠", {ROLE + "dev.md": "独占路径：scripts/ci/budget_check.py\n"}, "H17", ROLE)
    expect_hit("H17 相同路径交叠", {ROLE + "dev.md": "独占路径：./CLAUDE.md\n"}, "H17", ROLE)
    expect_hit("H17 缺声明", {ROLE + "dev.md": "# dev\n"}, "H17", ROLE + "dev.md")
    expect_clean("H17 段名相近不算交叠", {ROLE + "dev.md": f"独占路径：scripts/cifoo/, {DM}/intent2\n"})
    expect_hit("H17 路径行混进说明文字", {ROLE + "dev.md": "独占路径：backend/；每个开发会话只改本卡列明的文件\n"},
               "H17", ROLE + "dev.md")
    expect_hit("H17 不支持「除外」写法", {ROLE + "dev.md": "独占路径：backend/（backend/glossary/ 除外）\n"}, "H17", ROLE + "dev.md")
    expect_hit("H17 嵌套归属算交叠（不支持排除）", {ROLE + "dev.md": "独占路径：backend/\n",
                                                  ROLE + "language.md": "独占路径：backend/glossary/\n"}, "H17", ROLE)
    expect_clean("H17 列更细前缀不交叠", {ROLE + "dev.md": "独占路径：backend/src/、frontend/\n",
                                          ROLE + "language.md": "独占路径：backend/glossary/\n"})
    expect_clean("H17 本机路径（~/ 开头）算路径", {ROLE + "platform.md": "独占路径：scripts/ci/、`~/.modernize/psa/tools/**`\n"})
    expect_hit("H17 中间通配符按其前的前缀比（宁报不漏）", {ROLE + "design.md": "独占路径：frontend/**/__acceptance__/**\n",
                                                        ROLE + "dev.md": "独占路径：frontend/scripts/\n"}, "H17", ROLE)
    expect_clean("H17 末段通配符不截断", {ROLE + "design.md": "独占路径：docs/arch/*.md\n",
                                          ROLE + "language.md": "独占路径：docs/arch/decision-page/**\n"})
    for name, a, b in (("末段 *", "scripts/*", "scripts/ci/budget_check.py"), ("末段 *.md", "docs/*.md", "docs/a.md"),
                       ("中段 *", "docs/*/x.md", "docs/arch/x.md"), ("折叠 ..", "docs/../x/", "x/y/"),
                       ("两边通配", "docs/*.md", "docs/a*")):
        expect_hit(f"H17 {name} 交叠", {ROLE + "design.md": f"独占路径：{a}\n", ROLE + "dev.md": f"独占路径：{b}\n"},
                   "H17", ROLE)
    expect_clean("H17 末段 *.md 不匹配 *.py 之外的同级名", {ROLE + "design.md": "独占路径：docs/arch/*.md\n",
                                                          ROLE + "dev.md": "独占路径：docs/arch/a.py、docs/arch/b/\n"})
    expect_hit("H17 越出仓库的 .. 报格式违例", {ROLE + "dev.md": "独占路径：../x/\n"}, "H17", ROLE + "dev.md")
    expect_hit("H17 头部第二行声明也读", {ROLE + "dev.md": "# dev\n独占路径：无\n独占路径：scripts/\n"}, "H17", ROLE)
    # H21
    expect_hit("H21 别处加标签", {"scripts/merge/mq.py": 'sh(["gh", "pr", "edit", "1", "--add-label", "复审通过"])\n'},
               "H21", "scripts/merge/mq.py:1")
    expect_hit("H21 gh api 经键名删标签", {"scripts/x.sh": 'L=$(jq -r .labels.review_passed policy.json)\n'
                                                           'gh api -X DELETE repos/o/r/issues/1/labels/$L\n'},
               "H21", "scripts/x.sh:2")
    # H23 前半：docs 下不放运维、取证脚本
    expect_hit("H23 docs 下 shell", {"docs/ops/probe.sh": "echo hi\n"}, "H23", "docs/ops/probe.sh")
    expect_hit("H23 docs 下 Python 调 psql", {"docs/ops/q.py": 'import subprocess\nsubprocess.run(["psql", "-c", "x"])\n'},
               "H23", "docs/ops/q.py")
    expect_hit("H23 docs 下 Python 连库", {"docs/ops/q.py": "import psycopg2\n"}, "H23", "docs/ops/q.py")
    expect_hit("H23 docs 下无后缀 shebang", {"docs/ops/run": "#!/bin/sh\necho\n"}, "H23", "docs/ops/run")
    expect_hit("H23 docs 下 Python 用阿里云 SDK", {"docs/ops/r.py": "from alibabacloud_ecs20140526 import models\n"},
               "H23", "docs/ops/r.py")
    expect_hit("H23 docs 下 Python 直调外部接口", {"docs/ops/a.py": "import urllib.request\nimport socket\n"}, "H23", "docs/ops/a.py")
    ops = {"docs/ops/b5_run.py": "import psycopg2\nput('set_env.py')\n"}
    expect_hit("H23 import 同目录运维模块", {**ops, "docs/ops/scan.py": "#!/usr/bin/env python3\nfrom b5_run import put\n"},
               "H23", "docs/ops/scan.py")
    expect_hit("H23 被同目录运维脚本点名调用", {**ops, "docs/ops/set_env.py": "import os, sys\n"}, "H23", "docs/ops/set_env.py")
    rc, out, viol = run({**GOOD, **ops, "docs/other/scan.py": "from b5_run import put\n", "docs/other/set_env.py": "x = 1\n"})
    check("H23 只报运维脚本本身，别的目录同名不受牵连", [v.split()[1] for v in viol if v.startswith("H23")] == ["docs/ops/b5_run.py"], out)
    expect_hit("H23 docs 下带 main 的 Java", {"docs/ops/Probe.java": "class Probe { public static void main(String[] a) {} }\n"},
               "H23", "docs/ops/Probe.java")
    # 读文件出错、policy 缺键：该项失败关闭报违例，其他检查照跑
    rc, out, viol = run({**GOOD, ROLE + "broken.md/x": "目录冒充角色文件\n", "docs/ops/probe.sh": "echo\n"})
    check("读文件出错转违例行，不出 traceback，其他检查照跑", rc == 0 and "Traceback" not in out
          and any("检查无法完成" in v and "读不了文件" in v and "IsADirectoryError" in v for v in viol)
          and any(v.startswith("H23 docs/ops/probe.sh") for v in viol), out)
    pol = with_policy(["mq"], {"enqueue_alert_minutes": 45})
    rc, out, viol = run({**GOOD, "scripts/review/post_review.py": "p = 1\n" * 201}, pol)
    check("缺 mq.max_lines_total：报违例，单文件上限与 tools_lib 信息照出", rc == 0 and "Traceback" not in out
          and any(v.startswith("H23 policy.json 检查无法完成") and "max_lines_total" in v for v in viol)
          and any(v.startswith("H23 scripts/review/post_review.py") for v in viol) and "信息 H23 tools_lib" in out, out)
    rc, out, viol = run({**GOOD, "scripts/queue/merge_train.py": "q = 1\n" * 30}, with_policy(["mq", "max_lines_total"], 30)
                        | {"tools_lib": {}})
    check("缺 tools_lib.max_lines_total：报违例，mq 总行数照查", rc == 0 and "Traceback" not in out
          and any(v.startswith("H23 policy.json 检查无法完成") and "max_lines_total" in v for v in viol)
          and any(v.startswith("H23 scripts/merge+queue") for v in viol), out)
    # 模式：policy 的枚举 warn｜enforce
    bad = {**GOOD, "docs/ops/probe.sh": "echo\n"}
    check("warn 有违例退出 0", run(bad)[0] == 0)
    rc, out, _ = run(bad, with_policy(["budget_check", "mode"], "enforce"))
    check("enforce 有违例退出 1，且不报模式非法", rc == 1 and "缺失或非法" not in out, out)
    check("enforce 无违例退出 0", run(GOOD, with_policy(["budget_check", "mode"], "enforce"))[0] == 0)
    rc, out, _ = run(bad, with_policy(["budget_check", "mode"], "block"))
    check("mode 非法（含旧值 block）按 enforce", rc == 1 and "按 enforce" in out, out)
    rc, out, _ = run(bad, {k: v for k, v in POLICY.items() if k != "budget_check"})
    check("mode 缺失按 enforce", rc == 1 and "按 enforce" in out, out)
    check("policy.json 缺失退出 1", run(GOOD, None)[0] == 1)
    rc, out, _ = run(GOOD, {k: v for k, v in POLICY.items() if k != "labels"})
    check("policy 缺键失败关闭（报违例）", "H21 policy.json" in out, out)
    print(f"budget_check 自测：{'全部通过' if not FAILS else f'{len(FAILS)} 项失败'}")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
