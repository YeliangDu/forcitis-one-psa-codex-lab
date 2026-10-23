#!/usr/bin/env python3
"""budget_check：按 policy.json 核对流程体量与单一出处（定稿 1.1、1.3，硬约束 H1 H7 H8 H9 H13 H17 H21 H23）。
模式取 budget_check.mode：warn 只列违例、退出 0；block 有违例退出 1；缺失或非法按 block（失败关闭）。
输出每行「<检查号> <路径> <说明>」；「信息」开头的行不计违例。KB 按 1024 字节算。格式约定：
- RULES.md 每个二级标题（## ）是一条规则，其下一行「执行：名1、名2」；名取 CHECK_NAMES 或 gates.max_lines 的键，
  或「人工：<角色>／<节奏>」；多个名用 、,，;； 分隔。CLAUDE.md 编号行计底线条数；角色文件「## 专属规则」下列表项计条数。
- 角色文件前 15 行内一行「独占路径：a/, b/c.py」（、,，分隔，可写「无」）；按路径段比，一个是另一个的前缀即交叠。
- measures 每项：指标、基线、复盘日（YYYY-MM-DD）、撤销条件（也认 metric、baseline、review_date、revoke_condition）。
用法：budget_check.py [--root DIR] | budget_check.py --self-test"""
import argparse, datetime, json, os, re, subprocess, sys
from pathlib import Path

CLAUDE, RULES, ROLES = "CLAUDE.md", "docs/modernization/RULES.md", "docs/modernization/roles"
TEMPLATE, GATES, LABEL_WRITER = ".github/pull_request_template.md", "scripts/gates", "scripts/review/post_review.py"
CHECK_NAMES = {"budget_check", "mq", "post_review", "light", "backend", "frontend", "gitleaks", "assertion-drift",
               "acceptance_v2", "start_sheet", "handoff", "janitor", "board", "budget_level", "build_decisions",
               "check_pages", "build_all", "delegate"}
SEP = re.compile(r"[、,，;；]")
MEASURE_FIELDS = (("指标", "metric"), ("基线", "baseline"), ("复盘日", "review_date"), ("撤销条件", "revoke_condition"))
# H8：键名 → 字面值后须紧跟的单位；≥1000 的整数不带单位也算；无单位可依的 <1000 整数不查（太通用）。
UNITS = [(r"_kb$", r"\s*(?:KB|K|千字节)"), (r"max_lines\.|_lines$", r"\s*行"), (r"_minutes$", r"\s*分钟"),
         (r"_hours$", r"\s*小时"), (r"_days$|retention_days\.", r"\s*[天日]"), (r"_seconds$", r"\s*秒"),
         (r"_ms$", r"\s*(?:ms|毫秒)"), (r"_pct$", r"\s*%"), (r"_pts$", r"\s*(?:点|%)"),
         (r"_items$|_rules_max$", r"\s*条"), (r"files_max$|_files$", r"\s*[个份]"), (r"_prs$", r"\s*个"),
         (r"resident_max$|ci_runners$", r"\s*[个台]"), (r"factor$", r"\s*倍")]
FRACTION_KEY, LEFT = re.compile(r"share|rate"), r"(?<![A-Za-z0-9_.#-])"
LABEL_WRITE = re.compile(r"--(?:add|remove)-label|/labels\b|(?:add|remove)_labels?\b")
CODE_EXT = {".py", ".sh", ".bash", ".yml", ".yaml", ".js", ".mjs", ".cjs", ".ts", ""}
PRUNE = {".git", "node_modules", "build", "dist", "target", ".gradle", ".venv", "__pycache__"}
SHELL_CALL = re.compile(r"subprocess|os\.system|os\.popen")
OPS_CMD = re.compile(r"""["'](?:ssh|scp|rsync|psql|pg_dump|pg_restore|mysql|mysqldump|sqlcmd|sqlplus|aliyun|kubectl"""
                     r"""|redis-cli|mongosh?)["'\s]""")
DB_IMPORT = re.compile(r"^\s*(?:import|from)\s+(?:paramiko|psycopg2?|pymysql|pyodbc|oracledb|cx_Oracle|fabric)\b", re.M)

class Report:
    def __init__(self):
        self.violations, self.infos = [], []
        self.viol = lambda code, path, msg: self.violations.append(f"{code} {path} {msg}")
        self.info = lambda code, path, msg: self.infos.append(f"信息 {code} {path} {msg}")

def read(path):
    return path.read_text("utf-8", errors="replace")

def role_files(root):
    return sorted((root / ROLES).glob("*.md")) if (root / ROLES).is_dir() else []

def headings(text):
    items = []
    for no, line in enumerate(text.splitlines(), 1):
        if line.startswith("## "):
            items.append((no, line[3:].strip(), []))
        elif items:
            items[-1][2].append(line)
    return items

def role_rules(text):
    return sum(len([b for b in body if re.match(r"(?:[-*]|\d+\.)\s", b)])
               for _, title, body in headings(text) if title.startswith("专属规则"))

def check_rule_volume(root, pol, rep):
    lim, total_kb, total_items = pol["rules"], 0.0, 0
    for rel, key in ((CLAUDE, "claude_md_max_kb"), (RULES, "rules_md_max_kb")):
        if not (root / rel).is_file():
            rep.info("H7", rel, f"尚无 {rel}")
            continue
        size, text = (root / rel).stat().st_size / 1024, read(root / rel)
        n = len(re.findall(r"^\d+\.\s", text, re.M)) if rel == CLAUDE else len(headings(text))
        total_kb, total_items = total_kb + size, total_items + n
        if size > lim[key]:
            rep.viol("H7", rel, f"{size:.2f} KB 超过 rules.{key}")
        if rel == RULES and n > lim["rules_md_max_items"]:
            rep.viol("H7", rel, f"{n} 条超过 rules.rules_md_max_items")
    roles = role_files(root)
    if not roles:
        rep.info("H7", ROLES, f"尚无 {ROLES}/*.md")
    if len(roles) > lim["role_files_max"]:
        rep.viol("H7", ROLES, f"角色文件 {len(roles)} 份超过 rules.role_files_max")
    for p in roles:
        rel, size, n = p.relative_to(root).as_posix(), p.stat().st_size / 1024, role_rules(read(p))
        total_kb, total_items = total_kb + size, total_items + n
        if size > lim["role_file_max_kb"]:
            rep.viol("H7", rel, f"{size:.2f} KB 超过 rules.role_file_max_kb")
        if n > lim["role_rules_max"]:
            rep.viol("H7", rel, f"专属规则 {n} 条超过 rules.role_rules_max")
    for val, key, unit in ((round(total_kb, 2), "total_max_kb", "KB"), (total_items, "total_max_items", "条")):
        if val > lim[key]:
            rep.viol("H7", "(合计)", f"{val} {unit} 超过 rules.{key}")

def check_gate_volume(root, pol, rep):
    g, d = pol["gates"], root / GATES
    if not d.is_dir():
        return rep.info("H1", GATES, f"尚无 {GATES}/")
    lines, run_files = [0, 0], 0  # [运行代码, 自测]
    for p in sorted(d.rglob("*")):
        if not p.is_file() or p.suffix not in (".py", ".sh") or "__pycache__" in p.parts:
            continue
        rel, n = p.relative_to(root).as_posix(), len(read(p).splitlines())
        is_test = "selftest" in p.stem or p.stem.startswith("test_")
        lines[is_test], run_files = lines[is_test] + n, run_files + (not is_test)
        key = p.stem if p.stem in g["max_lines"] else ("selftest" if is_test else None)
        if key and n > g["max_lines"][key]:
            rep.viol("H1", rel, f"{n} 行超过 gates.max_lines.{key}")
    for bad, msg in ((lines[0] > g["runtime_max_lines"], f"运行代码 {lines[0]} 行超过 gates.runtime_max_lines"),
                     (run_files > g["runtime_max_files"], f"运行代码 {run_files} 个文件超过 gates.runtime_max_files"),
                     (lines[1] > lines[0], f"自测 {lines[1]} 行多于运行代码 {lines[0]} 行")):
        if bad:
            rep.viol("H1", GATES, msg)

def leaves(node, path=""):
    if isinstance(node, (dict, list)):
        for k, v in (node.items() if isinstance(node, dict) else enumerate(node)):
            yield from leaves(v, f"{path}[{k}]" if isinstance(node, list) else f"{path}.{k}".lstrip("."))
    elif isinstance(node, (int, float)) and not isinstance(node, bool) and node not in (0, 1):
        yield path, node

def literal_pattern(path, v):
    """policy 数值 → 文档里算「写了字面值」的正则；不值得查时返回 None。"""
    if FRACTION_KEY.search(path) and 0 < v < 1:
        return re.compile(rf"{LEFT}(?:{re.escape(f'{v * 100:g}')}\s*%|{re.escape(f'{v:g}')}(?![\d.]))")
    big = isinstance(v, int) and v >= 1000
    alts = [str(v) if isinstance(v, int) else f"{v:g}"] + ([f"{v:,}"] if big else [])
    num = f"{LEFT}(?:{'|'.join(map(re.escape, alts))})"
    unit = next((u for key, u in UNITS if re.search(key, path)), None)
    forms = ([num + r"(?![\d.,])"] if big else []) + ([num + unit] if unit else [])
    return re.compile("|".join(f"(?:{f})" for f in forms)) if forms else None

def check_single_source(root, pol, rep):
    pats = [(path, pat) for path, v in leaves({k: v for k, v in pol.items() if k != "schema_version"})
            if (pat := literal_pattern(path, v))]
    for p in [t for t in (root / RULES, root / TEMPLATE) if t.is_file()] + role_files(root):
        rel = p.relative_to(root).as_posix()
        for no, line in enumerate(read(p).splitlines(), 1):
            hits = {}
            for path, pat in pats:
                if m := pat.search(line):
                    hits.setdefault(m.group().strip(), []).append(path)
            for lit, keys in hits.items():
                rep.viol("H8", f"{rel}:{no}", f"字面值「{lit}」与 policy.json {'、'.join(keys)} 相同，改引用键名")

def check_exec_column(root, pol, rep):
    if not (root / RULES).is_file():
        return
    names = CHECK_NAMES | set(pol["gates"]["max_lines"])
    for no, title, body in headings(read(root / RULES)):
        where = f"{RULES}:{no}"
        cols = [m.group(1).strip() for m in (re.match(r"\s*[-*]?\s*执行[：:]\s*(.*)$", b) for b in body) if m]
        if not cols or not cols[0]:
            rep.viol("H9", where, f"「{title}」缺执行栏或执行栏为空")
            continue
        for tok in filter(None, (t.strip().strip("`").strip() for t in SEP.split(cols[0]))):
            if tok.startswith("人工"):
                if not tok[2:].strip("：: "):
                    rep.viol("H9", where, f"「{title}」人工执行须写角色／节奏")
            elif not (name := re.match(r"[A-Za-z_][\w.-]*", tok)) or name.group() not in names:
                rep.viol("H9", where, f"「{title}」执行栏引用的检查名「{tok}」不存在")

def check_measures(root, pol, rep):
    if not isinstance(ms := pol.get("measures"), list):
        return rep.viol("H13", "policy.json", "measures 缺失或不是数组")
    for i, m in enumerate(ms):
        where, m = f"policy.json:measures[{i}]", m if isinstance(m, dict) else {}
        for cn, en in MEASURE_FIELDS:
            val = m.get(cn, m.get(en))
            if val in (None, "", [], {}):
                rep.viol("H13", where, f"缺「{cn}」或为空")
            elif cn == "复盘日":
                due = re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(val)) and datetime.date.fromisoformat(str(val))
                if not due:
                    rep.viol("H13", where, f"复盘日「{val}」不是 YYYY-MM-DD")
                elif due <= datetime.date.today():
                    rep.info("H13", where, f"复盘日 {due} 已到期")

def norm_owned(s):
    s = re.sub(r"/?\*\*$", "", re.sub(r"^\./", "", s.strip().strip("`").strip())).rstrip("/")
    return tuple(x for x in s.split("/") if x)

def check_ownership(root, pol, rep):
    owned = []
    for p in role_files(root):
        rel = p.relative_to(root).as_posix()
        decl = [m.group(1) for m in (re.match(r"\s*独占路径[：:]\s*(.*)$", ln) for ln in read(p).splitlines()[:15]) if m]
        if not decl:
            rep.viol("H17", rel, "头部 15 行内缺「独占路径：」声明")
        elif decl[0].strip() != "无":
            owned += [(rel, raw.strip(), t) for raw in SEP.split(decl[0]) if (t := norm_owned(raw))]
    for i, (fa, ra, ta) in enumerate(owned):
        for fb, rb, tb in owned[i + 1:]:
            if fa != fb and (ta[:len(tb)] == tb or tb[:len(ta)] == ta):
                rep.viol("H17", fa, f"独占路径 {ra} 与 {fb} 的 {rb} 交叠")

def walk(root, top, skip_docs):
    for dirpath, dirs, files in os.walk(top):
        here = Path(dirpath)
        dirs[:] = [d for d in dirs if d not in PRUNE and not (skip_docs and here == root and d == "docs")
                   and not (here.name == ".claude" and d == "worktrees")]
        yield from (here / f for f in files)

def check_label_writer(root, pol, rep):
    label = pol["labels"]["review_passed"]
    for p in walk(root, root, skip_docs=True):
        rel = p.relative_to(root).as_posix()
        if p.suffix not in CODE_EXT or rel == LABEL_WRITER or rel.startswith("scripts/ci/budget_check"):
            continue
        lines = read(p).splitlines() if p.is_file() and p.stat().st_size <= 1_000_000 else []
        for i, line in enumerate(lines):
            near = "\n".join(lines[max(0, i - 3):i + 4])
            if LABEL_WRITE.search(line) and (label in near or "review_passed" in near):
                rep.viol("H21", f"{rel}:{i + 1}", f"增删「{label}」标签，只许 {LABEL_WRITER} 写")

def check_docs_scripts(root, pol, rep):
    for p in walk(root, root / "docs", skip_docs=False):
        rel, t = p.relative_to(root).as_posix(), read(p) if p.is_file() and p.suffix == ".py" else ""
        if p.suffix in (".sh", ".bash"):
            rep.viol("H23", rel, "docs/ 下的 shell 脚本")
        elif (SHELL_CALL.search(t) and OPS_CMD.search(t)) or DB_IMPORT.search(t):
            rep.viol("H23", rel, "docs/ 下的 Python 调远程或数据库命令（运维／取证脚本）")
        elif p.suffix != ".py" and p.is_file() and p.open("rb").read(2) == b"#!":
            rep.viol("H23", rel, "docs/ 下带 shebang 的可执行文件")

CHECKS = [("H7", check_rule_volume), ("H1", check_gate_volume), ("H8", check_single_source),
          ("H9", check_exec_column), ("H13", check_measures), ("H17", check_ownership),
          ("H21", check_label_writer), ("H23", check_docs_scripts)]

def main(argv=None):
    ap = argparse.ArgumentParser(description="按 policy.json 核对规则与钩子体量、单一出处等")
    ap.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args(argv)
    if args.self_test:
        return subprocess.run([sys.executable, str(Path(__file__).with_name("budget_check_selftest.py"))]).returncode
    root = Path(args.root).resolve()
    try:
        pol = json.loads((root / "policy.json").read_text("utf-8"))
    except (OSError, ValueError) as e:
        print(f"POLICY policy.json 无法读取或解析（{e}），失败关闭")
        return 1
    rep, mode = Report(), (pol.get("budget_check") or {}).get("mode") if isinstance(pol, dict) else None
    if mode not in ("warn", "block"):
        rep.info("MODE", "policy.json", f"budget_check.mode={mode!r} 缺失或非法，按 block 处理（失败关闭）")
        mode = "block"
    for code, fn in CHECKS:
        try:
            fn(root, pol, rep)
        except (KeyError, TypeError, ValueError, AttributeError) as e:
            rep.viol(code, "policy.json", f"检查无法完成（缺键或类型不对：{e!r}），失败关闭")
    print("\n".join(rep.infos + rep.violations + [f"budget_check：违例 {len(rep.violations)} 条，模式 {mode}"]))
    return 1 if mode == "block" and rep.violations else 0

if __name__ == "__main__":
    sys.exit(main())
