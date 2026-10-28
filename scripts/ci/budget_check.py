#!/usr/bin/env python3
"""budget_check：按 policy.json 核对流程体量与单一出处（定稿 1.1、1.3、第 3 节；口径见派工书第 11 节）。
模式取 budget_check.mode：warn 只列违例、退出 0；enforce 有违例退出 1；缺失或非法按 enforce（失败关闭）。
输出每行「<检查号> <路径> <说明>」，「信息」开头的行不计违例；KB 按 1024 字节算。policy 缺键或值不对时
该项检查报「检查无法完成」违例（失败关闭），其他检查照跑。各检查的格式约定写在对应函数的说明里。
用法：budget_check.py [--root DIR] | budget_check.py --self-test
"""
import argparse, datetime, fnmatch, functools, itertools, json, math, os, posixpath, re, subprocess, sys
from pathlib import Path

CLAUDE, RULES, ROLES = "CLAUDE.md", "docs/modernization/RULES.md", "docs/modernization/roles"
TEMPLATE, PROMPT = ".github/pull_request_template.md", "scripts/review/PROMPT.md"
LABEL_WRITER = "scripts/review/post_review.py"
HOOK_DIRS = ("scripts/gates/", "scripts/hooks/")
SCRIPT_EXT = (".py", ".sh")
TOOLS_LIB_ROOT = "~/.modernize/"
SEP = re.compile(r"[、,，;；]")
PATH_TOKEN = re.compile(r"[\w.*/@+~-]+", re.A)
MEASURE_FIELDS = (("指标", "metric"), ("基线", "baseline"), ("复盘日", "review_date"), ("撤销条件", "revoke_condition"))
# H8：键名 → 字面值后须紧跟的单位；≥1000 的整数不带单位也算；无单位可依的 <1000 整数不查（太通用）。
UNITS = [(r"_kb$", r"\s*(?:KB|K|千字节)"), (r"max_lines\.|_lines$", r"\s*行"), (r"_minutes$", r"\s*分钟"),
         (r"_hours$", r"\s*小时"), (r"_days$|retention_days\.", r"\s*[天日]"), (r"_seconds$", r"\s*秒"),
         (r"_ms$", r"\s*(?:ms|毫秒)"), (r"_pct$", r"\s*%"), (r"_pts$", r"\s*(?:点|%)"),
         (r"_items$|_rules_max$", r"\s*条"), (r"files_max$|_files$", r"\s*[个份]"), (r"_prs$", r"\s*个"),
         (r"resident_max$|ci_runners$", r"\s*[个台]"), (r"factor$", r"\s*倍")]
FRACTION_KEY = re.compile(r"share|rate")
LEFT = r"(?<![A-Za-z0-9_.#-])"
LABEL_WRITE = re.compile(r"--(?:add|remove)-label|/labels\b|(?:add|remove)_labels?\b")
CODE_EXT = {".py", ".sh", ".bash", ".yml", ".yaml", ".js", ".mjs", ".cjs", ".ts", ""}
PRUNE = {".git", "node_modules", "build", "dist", "target", ".gradle", ".venv", "__pycache__"}
SHELL_CALL = re.compile(r"subprocess|os\.system|os\.popen")
OPS_CMD = re.compile(r"""["'](?:ssh|scp|rsync|psql|pg_dump|pg_restore|mysql|mysqldump|sqlcmd|sqlplus|aliyun|kubectl"""
                     r"""|redis-cli|mongosh?)["'\s]""")
OPS_IMPORT = re.compile(r"^\s*(?:import|from)\s+(?:paramiko|psycopg2?|pymysql|pyodbc|oracledb|cx_Oracle|fabric"
                        r"|pymssql|sqlalchemy|alibabacloud_\w+|aliyunsdk\w*|oss2|boto3"
                        r"|urllib\.request|socket|http\.client|requests|httpx)\b", re.M)
# 渲染成二级标题却不是行首「## 」：缩进 1～3 格、「##」后接制表符或空标题；setext 是段落下一行全为「-」。
H2_ODD = re.compile(r"(?: {1,3}##(?:[ \t]|$)|##(?:\t|$))")
SETEXT_H2 = re.compile(r" {0,3}-+[ \t]*$")
NOT_PARAGRAPH = re.compile(r"\s{0,3}(?:#|[-*+][ \t]|\d+[.)][ \t]|>|```|~~~|\||-+[ \t]*$|$)")
LIST_STD = re.compile(r"(?:[-*]|\d+\.)\s")
LIST_ANY = re.compile(r"\s*(?:[-*+]|\d+[.)])\s")
ROLE_HEADING = re.compile(r"[^\w\n]*专属规则| {0,3}#{1,6}(?:[ \t]|$).*专属规则")

class Report:
    def __init__(self):
        self.violations = []
        self.infos = []

    def viol(self, code, path, msg):
        self.violations.append(f"{code} {path} {msg}")

    def info(self, code, path, msg):
        self.infos.append(f"信息 {code} {path} {msg}")

    def cap(self, code, path, val, limit, msg, policy=True):
        if policy:
            check_limit(limit)
        if val > limit:
            self.viol(code, path, msg)

def check_limit(v):
    """policy 上限值须是有限正数；NaN、Infinity、超大整数、非正数、非数字都抛 ValueError（检查无法完成）。"""
    try:
        ok = not isinstance(v, bool) and isinstance(v, (int, float)) and math.isfinite(v) and v > 0
    except OverflowError:
        ok = False
    if not ok:
        raise ValueError(f"上限 {str(v)[:20]!r} 须是有限正数")

def read(path):
    return path.read_text("utf-8", errors="replace")

def is_test(rel):
    return "selftest" in Path(rel).stem or Path(rel).stem.startswith("test_")

def role_files(root):
    return sorted((root / ROLES).glob("*.md")) if (root / ROLES).is_dir() else []

@functools.lru_cache(None)
def tree(root):
    """仓库内全部文件的相对路径，剪掉 PRUNE 与 .claude/worktrees。"""
    out = []
    for dirpath, dirs, files in os.walk(root):
        here = Path(dirpath)
        dirs[:] = [d for d in dirs if d not in PRUNE and not (here.name == ".claude" and d == "worktrees")]
        out += [(here / f).relative_to(root).as_posix() for f in files]
    return sorted(out)

def headings(text):
    """行首「## 」二级标题：[(行号, 标题, 其下各行)]。"""
    items = []
    for no, line in enumerate(text.splitlines(), 1):
        if line.startswith("## "):
            items.append((no, line[3:].strip(), []))
        elif items:
            items[-1][2].append(line)
    return items

def count_rules(rel, text, rep, role):
    """RULES.md 数「## 」标题；角色文件数「## 专属规则」下行首「- 」「* 」「1. 」。非标准二级标题（缩进、制表符、
    空标题、setext）、非标准列表项（缩进、「+ 」「1) 」）与写成别的样子的「专属规则」标题报格式违例，不静默不计。"""
    lines = text.splitlines()
    for i, line in enumerate(lines):
        prev = lines[i - 1] if i else ""
        if H2_ODD.match(line) or (SETEXT_H2.match(line) and not NOT_PARAGRAPH.match(prev)):
            rep.viol("H7", f"{rel}:{i + 1}", "非标准二级标题（缩进、制表符、空标题或 setext），格式违例，改成行首「## 」")
        if role and ROLE_HEADING.match(line) and not line.startswith("## 专属规则"):
            rep.viol("H9", f"{rel}:{i + 1}", "写了「专属规则」却不是「## 专属规则」二级标题，格式不符约定")
    if not role:
        return len(headings(text))
    n = 0
    for no, title, body in headings(text):
        for line_no, line in enumerate(body if title.startswith("专属规则") else [], no + 1):
            if LIST_STD.match(line):
                n += 1
            elif LIST_ANY.match(line):
                rep.viol("H7", f"{rel}:{line_no}", "专属规则下的非标准列表项（缩进、「+ 」或「1) 」），格式违例，改成行首「- 」")
    return n

def check_rule_volume(root, pol, rep):
    """H7：条数 = RULES.md 二级标题数 + 角色「## 专属规则」列表项数；CLAUDE.md 底线摘要只计 KB（第 11 节）。"""
    lim = pol["rules"]
    total_kb = 0.0
    total_items = 0
    roles = role_files(root)
    if not roles:
        rep.info("H7", ROLES, f"尚无 {ROLES}/*.md")
    rep.cap("H7", ROLES, len(roles), lim["role_files_max"], f"角色文件 {len(roles)} 份超过 rules.role_files_max")
    files = [(CLAUDE, "claude_md_max_kb", None), (RULES, "rules_md_max_kb", "rules_md_max_items")]
    files += [(p.relative_to(root).as_posix(), "role_file_max_kb", "role_rules_max") for p in roles]
    for rel, kb_key, n_key in files:
        if not (root / rel).is_file():
            rep.info("H7", rel, f"尚无 {rel}")
            continue
        size = (root / rel).stat().st_size / 1024
        n = count_rules(rel, read(root / rel), rep, role=n_key == "role_rules_max") if n_key else 0
        total_kb += size
        total_items += n
        rep.cap("H7", rel, size, lim[kb_key], f"{size:.2f} KB 超过 rules.{kb_key}")
        if n_key:
            label = "专属规则 " if n_key == "role_rules_max" else ""
            rep.cap("H7", rel, n, lim[n_key], f"{label}{n} 条超过 rules.{n_key}")
    total_kb = round(total_kb, 2)
    rep.cap("H7", "(合计)", total_kb, lim["total_max_kb"], f"{total_kb} KB 超过 rules.total_max_kb")
    rep.cap("H7", "(合计)", total_items, lim["total_max_items"], f"{total_items} 条超过 rules.total_max_items")

def check_gate_volume(root, pol, rep):
    """H1：scripts/gates/ 与 scripts/hooks/ 的运行代码文件数（文件名含 selftest 或以 test_ 开头算自测，不计）。
    行数不再查：卡 #736 去掉 gates.max_lines、runtime_max_lines、mq／tools_lib 的 max_lines_total 与「自测行数不得多于运行代码」。"""
    where = "scripts/gates+hooks"
    files = [r for r in tree(root) if r.startswith(HOOK_DIRS) and r.endswith(SCRIPT_EXT)]
    if not files:
        rep.info("H1", where, "尚无 scripts/gates/、scripts/hooks/ 下的脚本")
        return
    run_files = [r for r in files if not is_test(r)]
    rep.cap("H1", where, len(run_files), pol["gates"]["runtime_max_files"],
            f"运行代码 {len(run_files)} 个文件超过 gates.runtime_max_files")

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
    """H8：逐行查规则、角色、模板、CLAUDE.md、复审提示与 Issue 模板，无任何豁免（机读头全是字符串）。"""
    numbers = leaves({k: v for k, v in pol.items() if k != "schema_version"})
    pats = [(path, literal_pattern(path, v)) for path, v in numbers]
    docs = [root / t for t in (CLAUDE, RULES, TEMPLATE, PROMPT)] + role_files(root)
    for p in [d for d in docs if d.is_file()] + sorted(root.glob(".github/ISSUE_TEMPLATE/**/*.*")):
        for no, line in enumerate(read(p).splitlines(), 1):
            hits = {}
            for path, pat in pats:
                m = pat.search(line) if pat else None
                if m:
                    hits.setdefault(m.group().strip(), []).append(path)
            for lit, keys in hits.items():
                where = f"{p.relative_to(root).as_posix()}:{no}"
                rep.viol("H8", where, f"字面值「{lit}」与 policy.json {'、'.join(keys)} 相同，改引用键名")

def tools_lib_names(pol, rep):
    """H9 白名单只取 policy tools_lib.checks（工具名列表）。tools_lib.dir 只用来核对名单里的工具在本机存在
    （含入口文件 <名>/<名>.py、<名>.sh 或 __main__.py 的一级子目录），有差异只出信息行，不改违例。
    dir 须是 ~/.modernize/ 下的具体目录，过宽或含 .. 报格式违例。"""
    conf = pol.get("tools_lib")
    raw = conf.get("dir") if isinstance(conf, dict) else None
    checks = conf.get("checks") if isinstance(conf, dict) else None
    if checks is not None and not (isinstance(checks, list) and all(isinstance(c, str) for c in checks)):
        rep.viol("H9", "policy.json", "tools_lib.checks 须是工具名字符串列表，格式违例")
        return set()
    names = set(checks or [])
    if checks is None:
        rep.info("H9", "policy.tools_lib.checks", "未配置，工具库检查名未纳入")
    if raw is not None:
        deep = isinstance(raw, str) and raw.startswith(TOOLS_LIB_ROOT) and raw.rstrip("/") != TOOLS_LIB_ROOT[:-1]
        if not deep or ".." in raw.split("/"):
            rep.viol("H9", "policy.json", f"tools_lib.dir「{raw}」须写成 {TOOLS_LIB_ROOT} 下的具体目录，格式违例")
            return names
    lib = Path(os.path.expanduser(raw)) if raw else None
    if lib is None or not lib.is_dir():
        rep.info("H9", "policy.tools_lib.dir", "未配置或不存在，本机未核对 tools_lib.checks")
        return names
    local = {d.name for d in lib.iterdir() if d.is_dir() and not d.name.startswith((".", "_"))
             and any(e.is_file() for e in (d / f"{d.name}.py", d / f"{d.name}.sh", d / "__main__.py"))}
    if names != local:
        rep.info("H9", "policy.tools_lib.dir", f"本机核对：名单有而本机无「{'、'.join(sorted(names - local)) or '无'}」，"
                 f"本机有而名单无「{'、'.join(sorted(local - names)) or '无'}」")
    return names

def check_names(root, pol, rep):
    """执行栏可引用的检查名：scripts/、docs/ 下的 .py/.sh，backend/src/test 下的测试类，workflow 文件名与 job 名，
    工具库里的检查（见 tools_lib_names）。"""
    names = tools_lib_names(pol, rep)
    for rel in tree(root):
        p = Path(rel)
        if p.suffix in SCRIPT_EXT and rel.startswith(("scripts/", "docs/")):
            names.add(p.stem)
        elif rel.startswith("backend/src/test/") and p.suffix == ".java":
            names.add(p.stem)
        elif rel.startswith(".github/workflows/") and p.suffix in (".yml", ".yaml"):
            jobs = read(root / rel).partition("\njobs:")[2]
            names |= {p.stem, *re.findall(r"^  ([\w-]+):", jobs, re.M)}
    return names

def check_exec_column(root, pol, rep):
    """H9：每条规则下单独一行「执行：名1、名2」或「人工：<角色>／<节奏>」；检查名见 check_names。"""
    if not (root / RULES).is_file():
        return
    rules = headings(read(root / RULES))
    if not rules:
        rep.viol("H9", RULES, "无规则：文件为空或没有行首「## 」二级标题，格式不符约定")
        return
    names = check_names(root, pol, rep)
    for no, title, body in rules:
        where = f"{RULES}:{no}"
        cols = [m.group(1).strip() for m in (re.match(r"\s*[-*]?\s*执行[：:]\s*(.*)$", b) for b in body) if m]
        if not cols or not cols[0]:
            rep.viol("H9", where, f"「{title}」缺执行栏或执行栏为空")
            continue
        for tok in filter(None, (t.strip().strip("`").strip() for t in SEP.split(cols[0]))):
            if tok.startswith("人工"):
                if not tok[2:].strip("：: "):
                    rep.viol("H9", where, f"「{title}」人工执行须写角色／节奏")
                continue
            name = re.match(r"[A-Za-z_][\w-]*", tok)
            if not name or name.group() not in names:
                rep.viol("H9", where, f"「{title}」执行栏引用的检查名「{tok}」不存在")

def review_kind(pol, val):
    """复盘日：键引用到整数或整数数组 → "ref"；合法 YYYY-MM-DD → 日期；都不是 → None。"""
    ref = pol
    for key in str(val).split("."):
        ref = ref.get(key) if isinstance(ref, dict) else None
    if all(type(x) is int for x in (ref if isinstance(ref, list) and ref else [ref])):
        return "ref"
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(val)):
        return None
    try:
        return datetime.date.fromisoformat(str(val))
    except ValueError:  # 如 2026-13-45：逐项报违例，不中断后面的 measures
        return None

def check_measures(root, pol, rep):
    """H13：每项须有指标、基线、复盘日（YYYY-MM-DD 或解析到整数／整数数组的 policy 键路径）、撤销条件；也认英文键。"""
    ms = pol.get("measures")
    if not isinstance(ms, list):
        rep.viol("H13", "policy.json", "measures 缺失或不是数组")
        return
    for i, m in enumerate(ms):
        where = f"policy.json:measures[{i}]"
        m = m if isinstance(m, dict) else {}
        for cn, en in MEASURE_FIELDS:
            val = m.get(cn, m.get(en))
            if val in (None, "", [], {}):
                rep.viol("H13", where, f"缺「{cn}」或为空")
                continue
            kind = review_kind(pol, val) if cn == "复盘日" else "n/a"
            if kind == "ref":
                rep.info("H13", where, f"复盘日引用 {val}（相对天数），到期判断跳过")
            elif kind is None:
                rep.viol("H13", where, f"复盘日「{val}」既不是 YYYY-MM-DD，也不是解析到整数的 policy 键")
            elif kind != "n/a" and kind <= datetime.date.today():
                rep.info("H13", where, f"复盘日 {kind} 已到期")

def segs_overlap(ta, tb):
    """逐段比：通配段（*、*.md）匹配任意同级名，两边都是通配段或遇「**」即算交叠（宁报不漏）；较短一方比完即前缀，算交叠。"""
    for a, b in zip(ta, tb):
        if a == "**" or b == "**":
            return True
        if a == b or fnmatch.fnmatchcase(b, a) or fnmatch.fnmatchcase(a, b):
            continue
        if "*" not in a or "*" not in b:
            return False
    return True

def check_ownership(root, pol, rep):
    """H17：前 15 行内每一行「独占路径：a/, b/c.py」都读；只写仓库内路径或「无」，不支持排除写法；先折叠 . 与 ..。"""
    owned = []
    for p in role_files(root):
        rel = p.relative_to(root).as_posix()
        head = read(p).splitlines()[:15]
        decls = [m.group(1) for m in (re.match(r"\s*独占路径[：:]\s*(.*)$", ln) for ln in head) if m]
        if not decls:
            rep.viol("H17", rel, "头部 15 行内缺「独占路径：」声明")
        toks = [t.strip().strip("`").strip() for d in decls if d.strip() != "无" for t in SEP.split(d)]
        for tok in filter(None, toks):
            path = posixpath.normpath(tok) if PATH_TOKEN.fullmatch(tok) else ".."
            if path in (".", "..") or path.startswith(("/", "../")):
                rep.viol("H17", rel, f"「独占路径：」只写仓库内路径，「{tok}」不是（不支持排除写法，说明另起一行）")
            else:
                owned.append((rel, tok, tuple(path.split("/"))))
    for (fa, ra, ta), (fb, rb, tb) in itertools.combinations(owned, 2):
        if fa != fb and segs_overlap(ta, tb):
            rep.viol("H17", fa, f"独占路径 {ra} 与 {fb} 的 {rb} 交叠")

def check_label_writer(root, pol, rep):
    """H21：「复审通过」标签只许 post_review 写。"""
    label = pol["labels"]["review_passed"]
    for rel in tree(root):
        p = root / rel
        if rel.startswith(("docs/", "scripts/ci/budget_check")) or p.suffix not in CODE_EXT or rel == LABEL_WRITER:
            continue
        lines = read(p).splitlines() if p.is_file() and p.stat().st_size <= 1_000_000 else []
        for i, line in enumerate(lines):
            near = "\n".join(lines[max(0, i - 3):i + 4])
            if LABEL_WRITE.search(line) and (label in near or "review_passed" in near):
                rep.viol("H21", f"{rel}:{i + 1}", f"增删「{label}」标签，只许 {LABEL_WRITER} 写")

def leans_on(rel, text, ops_rel, ops_text):
    """rel 借同目录运维脚本 ops_rel 干活：import 了它，或被它点名调用。"""
    if os.path.dirname(rel) != os.path.dirname(ops_rel):
        return False
    imports = re.search(rf"^\s*(?:from|import)\s+{re.escape(Path(ops_rel).stem)}\b", text, re.M)
    return bool(imports or re.search(rf"(?<![\w.-]){re.escape(Path(rel).name)}", ops_text))

def check_docs_scripts(root, pol, rep):
    """H23 前半：docs/ 下的 shell、带 shebang 的非 .py、带 main 的 .java、调远程／数据库／云 SDK／外部接口的 .py，
    以及 import 了或被点名调用的同目录运维 .py。"""
    found = {}
    py = {}
    for rel in (r for r in tree(root) if r.startswith("docs/") and (root / r).is_file()):
        p = root / rel
        if p.suffix in (".sh", ".bash"):
            found[rel] = "docs/ 下的 shell 脚本"
        elif p.suffix == ".py":
            text = py[rel] = read(p)
            if (SHELL_CALL.search(text) and OPS_CMD.search(text)) or OPS_IMPORT.search(text):
                found[rel] = "docs/ 下的 Python 调远程或数据库命令（运维／取证脚本）"
        elif p.suffix == ".java":
            if re.search(r"\bvoid\s+main\s*\(", read(p)):
                found[rel] = "docs/ 下带 main 的 Java 程序（探针／运维工具）"
        else:
            with p.open("rb") as f:
                if f.read(2) == b"#!":
                    found[rel] = "docs/ 下带 shebang 的可执行文件"
    grown = True
    while grown:
        grown = [r for r in py if r not in found and any(o in py and leans_on(r, py[r], o, py[o]) for o in found)]
        for r in grown:
            found[r] = "docs/ 下的 Python 借同目录运维脚本干活（import 它或被它调用）"
    for rel, msg in found.items():
        rep.viol("H23", rel, msg)

CHECKS = [("H7", check_rule_volume), ("H1", check_gate_volume), ("H8", check_single_source), ("H9", check_exec_column),
          ("H13", check_measures), ("H17", check_ownership), ("H21", check_label_writer), ("H23", check_docs_scripts)]

def main(argv=None):
    ap = argparse.ArgumentParser(description="按 policy.json 核对规则与钩子体量、单一出处等")
    ap.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args(argv)
    if args.self_test:
        selftest = Path(__file__).with_name("budget_check_selftest.py")
        return subprocess.run([sys.executable, str(selftest)]).returncode
    root = Path(args.root).resolve()
    try:
        pol = json.loads((root / "policy.json").read_text("utf-8"))
    except (OSError, ValueError) as e:
        print(f"POLICY policy.json 无法读取或解析（{e}），失败关闭")
        return 1
    rep = Report()
    conf = pol.get("budget_check") if isinstance(pol, dict) else None
    mode = conf.get("mode") if isinstance(conf, dict) else None
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
