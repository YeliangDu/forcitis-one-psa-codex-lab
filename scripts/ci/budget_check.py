#!/usr/bin/env python3
"""budget_check：按 policy.json 核对流程体量与单一出处（定稿 1.1、1.3、第 3 节 H1 H7 H8 H9 H13 H17 H21 H23；口径见派工书第 11 节）。
模式取 budget_check.mode：warn 只列违例、退出 0；enforce 有违例退出 1；缺失或非法按 enforce（失败关闭）。
输出每行「<检查号> <路径> <说明>」，「信息」开头的行不计违例；KB 按 1024 字节算。各检查的格式约定写在对应函数的注释里。
用法：budget_check.py [--root DIR] | budget_check.py --self-test"""
import argparse, datetime, functools, itertools, json, os, re, subprocess, sys
from pathlib import Path

CLAUDE, RULES, ROLES = "CLAUDE.md", "docs/modernization/RULES.md", "docs/modernization/roles"
TEMPLATE, PROMPT, LABEL_WRITER = ".github/pull_request_template.md", "scripts/review/PROMPT.md", "scripts/review/post_review.py"
HOOK_DIRS, MQ_DIRS, SCRIPT_EXT = ("scripts/gates/", "scripts/hooks/"), ("scripts/merge/", "scripts/queue/"), (".py", ".sh")
SEP, PATH_TOKEN = re.compile(r"[、,，;；]"), re.compile(r"[\w.*/@+~-]+", re.A)
MEASURE_FIELDS = (("指标", "metric"), ("基线", "baseline"), ("复盘日", "review_date"), ("撤销条件", "revoke_condition"))
# H8：键名 → 字面值后须紧跟的单位；≥1000 的整数不带单位也算；无单位可依的 <1000 整数不查（太通用）。
UNITS = [(r"_kb$", r"\s*(?:KB|K|千字节)"), (r"max_lines\.|_lines$", r"\s*行"), (r"_minutes$", r"\s*分钟"), (r"_hours$", r"\s*小时"),
         (r"_days$|retention_days\.", r"\s*[天日]"), (r"_seconds$", r"\s*秒"), (r"_ms$", r"\s*(?:ms|毫秒)"), (r"_pct$", r"\s*%"),
         (r"_pts$", r"\s*(?:点|%)"), (r"_items$|_rules_max$", r"\s*条"), (r"files_max$|_files$", r"\s*[个份]"), (r"_prs$", r"\s*个"),
         (r"resident_max$|ci_runners$", r"\s*[个台]"), (r"factor$", r"\s*倍")]
FRACTION_KEY, LEFT, SHELL_CALL = re.compile(r"share|rate"), r"(?<![A-Za-z0-9_.#-])", re.compile(r"subprocess|os\.system|os\.popen")
LABEL_WRITE = re.compile(r"--(?:add|remove)-label|/labels\b|(?:add|remove)_labels?\b")
CODE_EXT = {".py", ".sh", ".bash", ".yml", ".yaml", ".js", ".mjs", ".cjs", ".ts", ""}
PRUNE = {".git", "node_modules", "build", "dist", "target", ".gradle", ".venv", "__pycache__"}
OPS_CMD = re.compile(r"""["'](?:ssh|scp|rsync|psql|pg_dump|pg_restore|mysql|mysqldump|sqlcmd|sqlplus|aliyun|kubectl"""
                     r"""|redis-cli|mongosh?)["'\s]""")
OPS_IMPORT = re.compile(r"^\s*(?:import|from)\s+(?:paramiko|psycopg2?|pymysql|pyodbc|oracledb|cx_Oracle|fabric|pymssql|sqlalchemy"
                        r"|alibabacloud_\w+|aliyunsdk\w*|oss2|boto3|urllib\.request|socket|http\.client|requests|httpx)\b", re.M)
read = lambda path: path.read_text("utf-8", errors="replace")
count = lambda path: len(read(path).splitlines())
is_test = lambda rel: "selftest" in Path(rel).stem or Path(rel).stem.startswith("test_")
posix = lambda p, root: p.relative_to(root).as_posix()
role_files = lambda root: sorted((root / ROLES).glob("*.md")) if (root / ROLES).is_dir() else []
role_rules = lambda text: sum(len([b for b in body if re.match(r"(?:[-*]|\d+\.)\s", b)])
                              for _, title, body in headings(text) if title.startswith("专属规则"))

class Report:
    def __init__(self): self.violations, self.infos = [], []
    def viol(self, code, path, msg): self.violations.append(f"{code} {path} {msg}")
    def info(self, code, path, msg): self.infos.append(f"信息 {code} {path} {msg}")
    def cap(self, code, path, val, cap, msg): val > cap and self.viol(code, path, msg)

@functools.lru_cache(None)
def tree(root):  # 仓库内全部文件的相对路径，剪掉 PRUNE 与 .claude/worktrees
    out = []
    for dirpath, dirs, files in os.walk(root):
        here = Path(dirpath)
        dirs[:] = [d for d in dirs if d not in PRUNE and not (here.name == ".claude" and d == "worktrees")]
        out += [posix(here / f, root) for f in files]
    return sorted(out)
def headings(text):  # 每个「## 」二级标题一条：(行号, 标题, 其下各行)
    lines = text.splitlines()
    at = [i for i, line in enumerate(lines) if line.startswith("## ")]
    return [(i + 1, lines[i][3:].strip(), lines[i + 1:j]) for i, j in zip(at, at[1:] + [len(lines)])]

def check_rule_volume(root, pol, rep):  # H7；条数 = RULES.md 二级标题数 + 角色「## 专属规则」列表项数，CLAUDE.md 底线摘要只计 KB
    lim, total_kb, total_items = pol["rules"], 0.0, 0
    for rel, key in ((CLAUDE, "claude_md_max_kb"), (RULES, "rules_md_max_kb")):
        if not (root / rel).is_file():
            rep.info("H7", rel, f"尚无 {rel}")
            continue
        size, n = (root / rel).stat().st_size / 1024, len(headings(read(root / rel))) if rel == RULES else 0
        total_kb, total_items = total_kb + size, total_items + n
        rep.cap("H7", rel, size, lim[key], f"{size:.2f} KB 超过 rules.{key}")
        rep.cap("H7", rel, n, lim["rules_md_max_items"], f"{n} 条超过 rules.rules_md_max_items")
    if not (roles := role_files(root)):
        rep.info("H7", ROLES, f"尚无 {ROLES}/*.md")
    rep.cap("H7", ROLES, len(roles), lim["role_files_max"], f"角色文件 {len(roles)} 份超过 rules.role_files_max")
    for p in roles:
        rel, size, n = posix(p, root), p.stat().st_size / 1024, role_rules(read(p))
        total_kb, total_items = total_kb + size, total_items + n
        rep.cap("H7", rel, size, lim["role_file_max_kb"], f"{size:.2f} KB 超过 rules.role_file_max_kb")
        rep.cap("H7", rel, n, lim["role_rules_max"], f"专属规则 {n} 条超过 rules.role_rules_max")
    rep.cap("H7", "(合计)", round(total_kb, 2), lim["total_max_kb"], f"{round(total_kb, 2)} KB 超过 rules.total_max_kb")
    rep.cap("H7", "(合计)", total_items, lim["total_max_items"], f"{total_items} 条超过 rules.total_max_items")

def check_gate_volume(root, pol, rep):  # H1：scripts/gates/ 与 scripts/hooks/ 的 .py/.sh 合计；名含 selftest 或以 test_ 开头算自测
    g, where, lines, run_files = pol["gates"], "scripts/gates+hooks", [0, 0], 0  # lines：[运行代码, 自测]
    if not (files := [r for r in tree(root) if r.startswith(HOOK_DIRS) and r.endswith(SCRIPT_EXT)]):
        return rep.info("H1", where, "尚无 scripts/gates/、scripts/hooks/ 下的脚本")
    for rel in files:
        lines[is_test(rel)], run_files = lines[is_test(rel)] + count(root / rel), run_files + (not is_test(rel))
    rep.cap("H1", where, lines[0], g["runtime_max_lines"], f"运行代码 {lines[0]} 行超过 gates.runtime_max_lines")
    rep.cap("H1", where, run_files, g["runtime_max_files"], f"运行代码 {run_files} 个文件超过 gates.runtime_max_files")
    rep.cap("H1", where, lines[1], lines[0], f"自测 {lines[1]} 行多于运行代码 {lines[0]} 行")

def check_line_caps(root, pol, rep):  # gates.max_lines.<名> 按文件名或「名_后缀」对 scripts/**、docs/**/*.py；门与钩子报 H1，其余报 H23
    """外围工具总行数（H23 后半）：mq.max_lines_total 管 scripts/merge/ 与 queue/；tools_lib 在仓库外，由工具库自测或 janitor 查。"""
    caps = pol["gates"]["max_lines"]
    for rel in tree(root):
        p, hook = Path(rel), rel.startswith(HOOK_DIRS)
        if rel.startswith("scripts/") and p.suffix in SCRIPT_EXT or rel.startswith("docs/") and p.suffix == ".py":
            key = next((k for k in (p.stem, f"{p.stem}_{p.suffix[1:]}") if k in caps), "selftest" if hook and is_test(rel) else None)
            if key and (n := count(root / rel)) > caps[key]:
                rep.viol("H1" if hook else "H23", rel, f"{n} 行超过 gates.max_lines.{key}")
    n = sum(count(root / r) for r in tree(root) if r.startswith(MQ_DIRS) and r.endswith(SCRIPT_EXT))
    rep.cap("H23", "scripts/merge+queue", n, pol["mq"]["max_lines_total"], f"{n} 行超过 mq.max_lines_total")
    rep.info("H23", "tools_lib", f"工具库在仓库外，tools_lib.max_lines_total（{pol['tools_lib']['max_lines_total']}）由工具库自测或 janitor 查")

def leaves(node, path=""):
    if isinstance(node, (dict, list)):
        for k, v in (node.items() if isinstance(node, dict) else enumerate(node)):
            yield from leaves(v, f"{path}[{k}]" if isinstance(node, list) else f"{path}.{k}".lstrip("."))
    elif isinstance(node, (int, float)) and not isinstance(node, bool) and node not in (0, 1):
        yield path, node
def literal_pattern(path, v):  # policy 数值 → 文档里算「写了字面值」的正则；不值得查时返回 None
    if FRACTION_KEY.search(path) and 0 < v < 1:
        return re.compile(rf"{LEFT}(?:{re.escape(f'{v * 100:g}')}\s*%|{re.escape(f'{v:g}')}(?![\d.]))")
    big = isinstance(v, int) and v >= 1000
    alts = [str(v) if isinstance(v, int) else f"{v:g}"] + ([f"{v:,}"] if big else [])
    num = f"{LEFT}(?:{'|'.join(map(re.escape, alts))})"
    unit = next((u for key, u in UNITS if re.search(key, path)), None)
    forms = ([num + r"(?![\d.,])"] if big else []) + ([num + unit] if unit else [])
    return re.compile("|".join(f"(?:{f})" for f in forms)) if forms else None

def check_single_source(root, pol, rep):  # 逐行查，无任何豁免（PROMPT.md 的机读头全是字符串，H8 只查数值）
    pats = [(path, pat) for path, v in leaves({k: v for k, v in pol.items() if k != "schema_version"})
            if (pat := literal_pattern(path, v))]
    docs = [root / t for t in (CLAUDE, RULES, TEMPLATE, PROMPT)] + role_files(root) + sorted(root.glob(".github/ISSUE_TEMPLATE/**/*"))
    for p in (d for d in docs if d.is_file()):
        for no, line in enumerate(read(p).splitlines(), 1):
            hits = {}
            for path, m in ((path, m) for path, pat in pats if (m := pat.search(line))):
                hits.setdefault(m.group().strip(), []).append(path)
            for lit, keys in hits.items():
                rep.viol("H8", f"{posix(p, root)}:{no}", f"字面值「{lit}」与 policy.json {'、'.join(keys)} 相同，改引用键名")

def check_names(root, pol):  # scripts/、docs/ 下的 .py/.sh，backend/src/test 下的测试类，workflow 文件名与 job 名，gates.max_lines 的键
    names = set(pol["gates"]["max_lines"])
    for rel in tree(root):
        if ((p := Path(rel)).suffix in SCRIPT_EXT and rel.startswith(("scripts/", "docs/"))
                or rel.startswith("backend/src/test/") and p.suffix == ".java"):
            names.add(p.stem)
        elif rel.startswith(".github/workflows/") and p.suffix in (".yml", ".yaml"):
            names |= {p.stem, *re.findall(r"^  ([\w-]+):", read(root / rel).partition("\njobs:")[2], re.M)}
    return names
def check_exec_column(root, pol, rep):  # H9：每条规则下单独一行「执行：名1、名2」或「人工：<角色>／<节奏>」
    rules = headings(text := read(root / RULES) if (root / RULES).is_file() else "")
    if text.strip() and not rules:
        rep.viol("H9", RULES, "未找到二级标题规则，格式不符约定")
    for p in role_files(root):
        if re.search(r"^[^\w\n]*专属规则", t := read(p), re.M) and not any(h.startswith("专属规则") for _, h, _ in headings(t)):
            rep.viol("H9", posix(p, root), "写了「专属规则」却不是「## 专属规则」二级标题，格式不符约定")
    names = check_names(root, pol) if rules else set()
    for no, title, body in rules:
        where = f"{RULES}:{no}"
        cols = [m.group(1).strip() for m in (re.match(r"\s*[-*]?\s*执行[：:]\s*(.*)$", b) for b in body) if m]
        if not cols or not cols[0]:
            rep.viol("H9", where, f"「{title}」缺执行栏或执行栏为空")
            continue
        for tok in filter(None, (t.strip().strip("`").strip() for t in SEP.split(cols[0]))):
            if tok.startswith("人工") and not tok[2:].strip("：: "):
                rep.viol("H9", where, f"「{title}」人工执行须写角色／节奏")
            elif not tok.startswith("人工") and not (name := re.match(r"[A-Za-z_][\w-]*", tok)) or name.group() not in names:
                rep.viol("H9", where, f"「{title}」执行栏引用的检查名「{tok}」不存在")

def check_measures(root, pol, rep):  # H13：指标、基线、复盘日（YYYY-MM-DD 或解析到整数／整数数组的 policy 键路径）、撤销条件；也认英文键
    if not isinstance(ms := pol.get("measures"), list):
        return rep.viol("H13", "policy.json", "measures 缺失或不是数组")
    for i, m in enumerate(ms):
        where, m = f"policy.json:measures[{i}]", m if isinstance(m, dict) else {}
        for cn, en in MEASURE_FIELDS:
            if (val := m.get(cn, m.get(en))) in (None, "", [], {}):
                rep.viol("H13", where, f"缺「{cn}」或为空")
            elif cn == "复盘日":
                ref = functools.reduce(lambda n, k: n.get(k) if isinstance(n, dict) else None, str(val).split("."), pol)
                if (refs := ref if isinstance(ref, list) else [ref]) and all(type(x) is int for x in refs):
                    rep.info("H13", where, f"复盘日引用 {val}（相对天数），到期判断跳过")
                    continue
                try: due = re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(val)) and datetime.date.fromisoformat(str(val))
                except ValueError: due = None  # 如 2026-13-45：逐项报违例，不中断后面的 measures
                if not due:
                    rep.viol("H13", where, f"复盘日「{val}」既不是 YYYY-MM-DD，也不是解析到整数的 policy 键")
                elif due <= datetime.date.today():
                    rep.info("H13", where, f"复盘日 {due} 已到期")

def check_ownership(root, pol, rep):  # H17：前 15 行内「独占路径：a/, b/c.py」只写路径或「无」，不支持排除写法；按路径段比，前缀即交叠
    owned = []
    for p, rel in ((p, posix(p, root)) for p in role_files(root)):
        if not (decl := [m.group(1) for m in (re.match(r"\s*独占路径[：:]\s*(.*)$", ln) for ln in read(p).splitlines()[:15]) if m]):
            rep.viol("H17", rel, "头部 15 行内缺「独占路径：」声明")
            continue
        for tok in filter(None, (t.strip().strip("`").strip() for t in SEP.split(decl[0]) if decl[0].strip() != "无")):
            if not PATH_TOKEN.fullmatch(tok):
                rep.viol("H17", rel, f"「独占路径：」只写路径，「{tok}」不是路径（不支持排除写法，说明另起一行）")
            elif t := tuple(x for x in re.sub(r"/?\*\*$", "", re.sub(r"^\./", "", tok)).rstrip("/").split("/") if x):
                owned.append((rel, tok, t))
    for (fa, ra, ta), (fb, rb, tb) in itertools.combinations(owned, 2):
        if fa != fb and (ta[:len(tb)] == tb or tb[:len(ta)] == ta):
            rep.viol("H17", fa, f"独占路径 {ra} 与 {fb} 的 {rb} 交叠")

def check_label_writer(root, pol, rep):
    label = pol["labels"]["review_passed"]
    for rel in tree(root):
        if rel.startswith("docs/") or (p := root / rel).suffix not in CODE_EXT or rel == LABEL_WRITER or rel.startswith("scripts/ci/budget_check"):
            continue
        lines = read(p).splitlines() if p.is_file() and p.stat().st_size <= 1_000_000 else []
        for i, line in enumerate(lines):
            near = "\n".join(lines[max(0, i - 3):i + 4])
            if LABEL_WRITE.search(line) and (label in near or "review_passed" in near):
                rep.viol("H21", f"{rel}:{i + 1}", f"增删「{label}」标签，只许 {LABEL_WRITER} 写")

def check_docs_scripts(root, pol, rep):  # docs/ 下的 shell、带 shebang 的非 .py、带 main 的 .java、运维 .py 及借它干活的同目录 .py
    found, py = {}, {}
    for rel, p in ((r, root / r) for r in tree(root) if r.startswith("docs/") and (root / r).is_file()):
        if p.suffix in (".sh", ".bash"):
            found[rel] = "docs/ 下的 shell 脚本"
        elif p.suffix == ".py":
            if (SHELL_CALL.search(t := read(p)) and OPS_CMD.search(t)) or OPS_IMPORT.search(t):
                found[rel] = "docs/ 下的 Python 调远程或数据库命令（运维／取证脚本）"
            py[rel] = t
        elif p.suffix == ".java" and re.search(r"\bvoid\s+main\s*\(", read(p)):
            found[rel] = "docs/ 下带 main 的 Java 程序（探针／运维工具）"
        elif p.suffix != ".py" and p.open("rb").read(2) == b"#!":
            found[rel] = "docs/ 下带 shebang 的可执行文件"
    uses = lambda r, o: (re.search(rf"^\s*(?:from|import)\s+{Path(o).stem}\b", py[r], re.M)
                         or re.search(rf"(?<![\w.-]){re.escape(Path(r).name)}", py[o]))
    while grown := [r for r in py if r not in found and any(o in py and os.path.dirname(o) == os.path.dirname(r)
                                                             and uses(r, o) for o in found)]:
        found.update((r, "docs/ 下的 Python 借同目录运维脚本干活（import 它或被它调用）") for r in grown)
    for rel, msg in found.items():
        rep.viol("H23", rel, msg)

CHECKS = [("H7", check_rule_volume), ("H1", check_gate_volume), ("H23", check_line_caps), ("H8", check_single_source), ("H9", check_exec_column),
          ("H13", check_measures), ("H17", check_ownership), ("H21", check_label_writer), ("H23", check_docs_scripts)]

def main(argv=None):
    ap = argparse.ArgumentParser(description="按 policy.json 核对规则与钩子体量、单一出处等")
    ap.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args(argv)
    if args.self_test:
        return subprocess.run([sys.executable, str(Path(__file__).with_name("budget_check_selftest.py"))]).returncode
    try:
        pol = json.loads(((root := Path(args.root).resolve()) / "policy.json").read_text("utf-8"))
    except (OSError, ValueError) as e:
        print(f"POLICY policy.json 无法读取或解析（{e}），失败关闭")
        return 1
    rep, mode = Report(), (pol.get("budget_check") or {}).get("mode") if isinstance(pol, dict) else None
    if mode not in ("warn", "enforce"):
        rep.info("MODE", "policy.json", f"budget_check.mode={mode!r} 缺失或非法，按 enforce 处理（失败关闭）")
        mode = "enforce"
    for code, fn in CHECKS:
        try:
            fn(root, pol, rep)
        except (KeyError, TypeError, ValueError, AttributeError, OSError) as e:
            rep.viol(code, "policy.json", f"检查无法完成（缺键、类型不对或读不了文件：{e!r}），失败关闭")
    print("\n".join(rep.infos + rep.violations + [f"budget_check：违例 {len(rep.violations)} 条，模式 {mode}"]))
    return 1 if mode == "enforce" and rep.violations else 0

if __name__ == "__main__":
    sys.exit(main())
