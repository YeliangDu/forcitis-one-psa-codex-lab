#!/usr/bin/env python3
"""从 adr/src/ 生成 ADR-*.md、README.md 与 INDEX.md（规则：README「ADR 生命周期与变更规则」，变更治理：METHODS M3）。

每份 ADR 一个源文件 src/ADR-NNNN.md，生成的 ADR-NNNN.md 与源文件逐字相同。两种格式，按头部区是否有「档位」行判定：
  新稿：# ADR-NNNN：标题，然后按序的字段行（状态、日期、档位、起草、类别、BC、阶段、取代、被部分取代、用户原话、批准证据、理由；多值字段为下一级列表）
  旧稿：# ADR-NNNN：标题、状态、日期、可选的取代（一行，「、」分隔）；编号在冻结常量 LEGACY_FORMAT 中，不回改
  然后是二级小节：背景 / 决定 / 影响面 / 迁移方案 / 回滚方案 / 需同步修改的守护规则 / 后果 / 证据 / 备选方案（未采用）。
旧稿的类别、BC、阶段、被部分取代范围取自旁表 catalog.yaml（不改正文；缺旁表时只告警，视图列「—」）：
  adrs:
  - id: "0001"
    category: 数据                  # 七类枚举
    bc: [全局]                      # contexts.yaml 的 id 或 全局
    stage: [全阶段]                 # transitions.yaml 的 id 或 全阶段
    partially_superseded: []        # 每项「ADR-NNNN 范围」，ADR 号须与旧稿状态行括注所指一致
校验（不通过则生成失败，不写任何产物；规则逐条见 README 第 1–11 节）：状态词、字段顺序与取值、条件必填、取代对象存在与双向成立、
小节与档位、大小、旧稿旁表、编号连续（ADR-0029 空壳合入后开启）。超期提议、正式档超 20 KB 的提议只告警。
用法：build_adrs.py | build_adrs.py --self-test | build_adrs.py --catalog-skeleton（打印旁表骨架供填写）
"""
import datetime, os, re, sys

BASE = os.path.dirname(os.path.abspath(__file__))
ARCH = os.path.join(BASE, "..", "architecture")
# 本规则合入 main 时 adr/src/ 中已有的全部编号，冻结不再追加；不在其中又无「档位」字段的 ADR 生成失败
LEGACY_FORMAT = frozenset(f"{n:04d}" for n in list(range(1, 29)) + list(range(30, 52)))
LEGACY_MAX = "0023"   # ADR-0001 至 0023 早于必备小节规则，不追补
LEGACY_STATES = ("提议", "搁置", "已采纳", "已拒绝", "已撤回", "已被 ADR-", "已废弃")
NEW_STATES = ("提议", "搁置", "已采纳", "已拒绝", "已撤回", "已废弃")
SUPERSEDED = re.compile(r"已被 ADR-(\d{4}) 取代")
REQUIRED = ("影响面", "迁移方案", "回滚方案", "需同步修改的守护规则")
ORDER = ("背景", "决定", *REQUIRED, "后果", "证据", "备选方案（未采用）")
MANDATORY = ("背景", "决定", "后果", "证据", "备选方案（未采用）")
LIGHT_REQUIRED = ("决定", *REQUIRED, "证据")
CATEGORIES = ("边界与模块", "数据", "集成", "安全与权限", "技术与平台", "业务主干", "方法")
FIELDS = ("状态", "日期", "档位", "起草", "类别", "BC", "阶段", "取代", "被部分取代", "用户原话", "批准证据", "理由")
MULTI = ("BC", "阶段", "取代", "被部分取代", "用户原话", "批准证据")
MUST = ("状态", "日期", "档位", "起草", "类别", "BC", "阶段")
FORMAL_LIMIT, LIGHT_LIMIT, OVERDUE_DAYS = 20480, 6144, 7
ANNOTATIONS = {"0020": "协作规则以 METHODS 为准"}
LEGACY_HEAD = re.compile(r"# ADR-(\d{4})：([^\n]+)\n\n- \*\*状态\*\*：([^\n]+)\n- \*\*日期\*\*：(\d{4}-\d{2}-\d{2})\n(?:- \*\*取代\*\*：([^\n]+)\n)?\n")
ITEM = re.compile(r"ADR-(\d{4})(?: (.{1,30}))?")
LINE_STYLE = re.compile(r"第\s*\d+\s*行|(?<!\w)[:：]\d+|\.\w{1,5}[:：]\d+|(?<![A-Za-z0-9])L\d{2,}\b|(?<![A-Za-z0-9])L\d+-L?\d+")
INTENT_REF = re.compile(r"(?<!\d)20\d{2}-\d{2}-\d{2}-\d{3,}|intent|用户")


def sections(path, body):
    chunks = re.split(r"(?m)^## ([^\n]+)\n\n", body)
    if chunks[0]:
        raise ValueError(f"{path}：头部之后须直接是二级标题")
    names = chunks[1::2]
    secs = {n: b[:-2] if b.endswith("\n\n") else b[:-1] for n, b in zip(names, chunks[2::2])}
    if len(secs) != len(names) or any(n not in ORDER for n in names) or names != [n for n in ORDER if n in secs]:
        raise ValueError(f"{path}：小节须取自 {'、'.join(ORDER)}，不重复且按此顺序：{names}")
    return secs


def parse_new_header(path, lines):
    """头部行 -> {字段: 值或列表}；未知、重复、乱序字段，多值写成单行、单值写成列表，一律失败。"""
    fields, order, current = {}, [], None
    for line in lines:
        m = re.fullmatch(r"- \*\*([^*]+)\*\*：(.*)", line)
        if m:
            name, value = m.groups()
            if name not in FIELDS:
                raise ValueError(f"{path}：未知头部字段「{name}」")
            if name in fields:
                raise ValueError(f"{path}：头部字段「{name}」重复")
            order.append(name)
            if name in MULTI:
                if value.strip():
                    raise ValueError(f"{path}：多值字段「{name}」须写成下一级列表，每项一行")
                fields[name] = []
            else:
                if not value.strip():
                    raise ValueError(f"{path}：字段「{name}」不能为空")
                fields[name] = value.strip()
            current = name
            continue
        m = re.fullmatch(r"  - (\S.*)", line)
        if m and current in MULTI:
            fields[current].append(m.group(1).strip())
            continue
        raise ValueError(f"{path}：头部行不合规（字段行「- **名**：值」，多值字段的项缩进两格写「  - 项」）：{line[:40]}")
    if order != [n for n in FIELDS if n in fields]:
        raise ValueError(f"{path}：头部字段须按 {'、'.join(FIELDS)} 的顺序：{order}")
    missing = [n for n in MUST if n not in fields]
    empty = [n for n in MULTI if n in fields and not fields[n]]
    if missing or empty:
        raise ValueError(f"{path}：缺少或为空的头部字段 {'、'.join(missing + empty)}")
    return fields


def parse_text(path, text, size=None):
    m = re.match(r"# ADR-(\d{4})：([^\n]+)\n\n", text)
    if not m:
        raise ValueError(f"{path}：头部须以「# ADR-NNNN：标题」开始")
    no, title = m.groups()
    if os.path.basename(path) != f"ADR-{no}.md":
        raise ValueError(f"{path}：文件名与编号 ADR-{no} 不一致")
    cut = re.search(r"(?m)^## ", text)
    if not cut:
        raise ValueError(f"{path}：没有二级标题")
    header_text = text[m.end():cut.start()]
    header_lines = header_text.rstrip("\n").split("\n")
    if not header_text.endswith("\n\n") or header_text.endswith("\n\n\n") or header_text.startswith("\n"):
        raise ValueError(f"{path}：头部与第一个二级标题之间须恰有一个空行")
    is_new = any(re.match(r"- \*\*档位\*\*：", l) for l in header_lines)
    if not is_new and no not in LEGACY_FORMAT:
        raise ValueError(f"{path}：ADR-{no} 不在 LEGACY_FORMAT 中，须有「档位」字段（新格式）")
    size = len(text.encode("utf-8")) if size is None else size
    # 「被部分取代」由后来的 ADR 追加，不是本篇内容，不计入大小上限
    partly = re.search(r"(?m)^- \*\*被部分取代\*\*：\n(?:  - .*\n)*", header_text) if is_new else None
    if partly:
        size -= len(partly.group(0).encode("utf-8"))
    if is_new:
        f = parse_new_header(path, header_lines)
        secs = sections(path, text[cut.start():])
        return dict(no=no, title=title, new=True, status=f["状态"], date=f["日期"], tier=f["档位"], drafter=f["起草"], category=f["类别"],
                    bc=f["BC"], stage=f["阶段"], supersedes=f.get("取代", []), partly=f.get("被部分取代", []), quotes=f.get("用户原话", []),
                    evidence=f.get("批准证据", []), reason=f.get("理由", ""), secs=secs, header=text[m.start():cut.start() - 1], size=size)
    h = LEGACY_HEAD.match(text)
    if not h:
        raise ValueError(f"{path}：旧稿头部须为「# ADR-NNNN：标题」、状态、日期（及可选的取代）行")
    _, _, status, date, sup = h.groups()
    secs = sections(path, text[h.end():])
    missing = [n for n in MANDATORY if n not in secs]
    if missing:
        raise ValueError(f"{path}：缺少小节 {'、'.join(missing)}")
    return dict(no=no, title=title, new=False, status=status, date=date, tier="正式", category=None, bc=None, stage=None,
                supersedes=sup.split("、") if sup else [], partly=[], secs=secs, header=text[m.start():h.end() - 1], size=size)


def parse(path):
    return parse_text(path, open(path, encoding="utf-8").read())


def render(a):
    return f"{a['header']}\n" + "\n\n".join(f"## {n}\n\n{a['secs'][n]}" for n in ORDER if n in a["secs"]) + "\n"


def load_ids(path, key):
    import yaml
    with open(path, encoding="utf-8") as f:
        return {str(x["id"]) for x in yaml.safe_load(f)[key]}


def load_context():
    ctx = dict(bcs=None, stages=None, catalog=None, today=datetime.date.today())
    try:
        import yaml
    except ImportError:
        print("警告：缺少 PyYAML，未校验 BC、阶段取值与旁表（build_all.py --check 会因此失败）", file=sys.stderr)
        return ctx
    ctx["bcs"] = load_ids(os.path.join(ARCH, "ssot", "d-domain", "contexts.yaml"), "contexts")
    ctx["stages"] = load_ids(os.path.join(ARCH, "ssot", "t-transitions", "transitions.yaml"), "transitions")
    cat = os.path.join(BASE, "catalog.yaml")
    if os.path.exists(cat):
        with open(cat, encoding="utf-8") as f:
            ctx["catalog"] = yaml.safe_load(f)["adrs"]
    return ctx


def check_values(label, values, allowed, sole, proposal):
    errors = []
    if allowed is None:
        return errors
    for v in values:
        if v == "待定":
            if not proposal:
                errors.append(f"{label}：非提议状态不得为「待定」")
        elif v not in allowed and v != sole:
            errors.append(f"{label}：取值「{v}」不在登记中")
    if len(values) > 1 and any(v in (sole, "待定") for v in values):
        errors.append(f"{label}：「{sole}」「待定」须单独成项")
    return errors


def check_item(label, item):
    m = ITEM.fullmatch(item)
    if not m:
        return [f"{label}：「{item}」须为「ADR-NNNN」或「ADR-NNNN 范围」（范围 1–30 字）"]
    if m.group(2) and LINE_STYLE.search(m.group(2)):
        return [f"{label}：范围不得写行号：{item}"]
    return []


def validate_new(a, status_of, by_no, ctx):
    no, status, tier, errors = a["no"], a["status"], a["tier"], []
    tag = f"ADR-{no}"
    proposal = status == "提议"
    if not (status in NEW_STATES or re.fullmatch(r"已被 ADR-\d{4} 取代", status)):
        errors.append(f"{tag}: 状态须精确为 {'、'.join(NEW_STATES)} 或「已被 ADR-NNNN 取代」：{status[:30]}")
    try:
        datetime.date.fromisoformat(a["date"]); assert re.fullmatch(r"\d{4}-\d{2}-\d{2}", a["date"])
    except (ValueError, AssertionError):
        errors.append(f"{tag}: 日期须为有效的 YYYY-MM-DD：{a['date']}")
    if tier not in ("正式", "轻量"):
        errors.append(f"{tag}: 档位须为「正式」或「轻量」：{tier}")
    if not re.fullmatch(r"[A-Za-z0-9._-]+", a["drafter"]):
        errors.append(f"{tag}: 起草须为会话名：{a['drafter']}")
    if a["category"] == "待定":
        if not proposal:
            errors.append(f"{tag}: 非提议状态类别不得为「待定」")
    elif a["category"] not in CATEGORIES:
        errors.append(f"{tag}: 类别须取自 {'、'.join(CATEGORIES)}：{a['category']}")
    errors += [f"{tag}: {e}" for e in check_values("BC", a["bc"], ctx["bcs"], "全局", proposal)]
    errors += [f"{tag}: {e}" for e in check_values("阶段", a["stage"], ctx["stages"], "全阶段", proposal)]
    # 条件必填
    def need(name, ok):
        if not ok:
            errors.append(f"{tag}: 状态「{status}」须有{name}")
    background = a["secs"].get("背景", "")
    if status == "已采纳":
        need("用户原话", a["quotes"]); need("批准证据", a["evidence"])
    elif status == "搁置":
        need("理由（含「重启条件：…」）", re.search(r"(?<!无)(?<!没有)重启条件[：:]\s*(?!无\b|待定)\S", a["reason"])); need("用户原话", a["quotes"])
    elif status in ("已拒绝", "已废弃"):
        need("理由", a["reason"]); need("用户原话", a["quotes"])
    elif status == "已撤回":
        need("理由", a["reason"])
        if INTENT_REF.search(background):
            need("用户原话（背景所列触发出处含 intent，属用户指派）", a["quotes"])
    # 取代
    for field, items in (("取代", a["supersedes"]), ("被部分取代", a["partly"])):
        for item in items:
            errors += [f"{tag}: {e}" for e in check_item(field, item)]
    if tier == "轻量" and a["supersedes"]:
        errors.append(f"{tag}: 轻量档不得取代或部分取代已采纳 ADR（取代须为空）")
    for item in a["supersedes"]:
        m = ITEM.fullmatch(item)
        if m and m.group(1) not in status_of:
            errors.append(f"{tag}: 取代对象不存在：{item}")
    for item in a["partly"]:
        m = ITEM.fullmatch(item)
        if not m:
            continue
        other = by_no.get(m.group(1))
        if not other:
            errors.append(f"{tag}: 被部分取代所指 ADR 不存在：{item}")
        elif not any(i.startswith(f"ADR-{no}") for i in other["supersedes"]) or other["status"] != "已采纳":
            errors.append(f"{tag}: 被部分取代 {item}，但 ADR-{m.group(1)} 须为已采纳且「取代」列有 ADR-{no}")
    sup = SUPERSEDED.fullmatch(status)
    if sup:
        other = by_no.get(sup.group(1))
        if not other or other["status"] != "已采纳" or not any(re.fullmatch(f"ADR-{no}", i) for i in other["supersedes"]):
            errors.append(f"{tag}: 状态为已被 ADR-{sup.group(1)} 取代，但其须为已采纳且「取代」整份列有 ADR-{no}")
    # 小节
    names = a["secs"]
    if status in ("已采纳", "已废弃") or SUPERSEDED.fullmatch(status):
        need_secs = ORDER if tier == "正式" else LIGHT_REQUIRED
        errors += [f"{tag}: 缺少小节「{n}」" for n in need_secs if not names.get(n, "").strip()]
    # 大小
    if tier == "轻量" and a["size"] > LIGHT_LIMIT:
        errors.append(f"{tag}: 轻量档源文件 {a['size']} 字节超过 {LIGHT_LIMIT}，改用正式档")
    if tier == "正式" and a["size"] > FORMAL_LIMIT and not proposal:
        errors.append(f"{tag}: 正式档源文件 {a['size']} 字节超过 {FORMAL_LIMIT}，须拆分")
    return errors


def validate_legacy(a, status_of):
    no, status, errors = a["no"], a["status"], []
    if not status.startswith(LEGACY_STATES):
        errors.append(f"ADR-{no}: 状态须以 {'/'.join(LEGACY_STATES)} 开头：{status[:30]}")
    if no > LEGACY_MAX:
        errors += [f"ADR-{no}: 缺少必备小节「{n}」" for n in REQUIRED if not a["secs"].get(n, "").strip()]
    return errors


def validate_legacy_supersession(a, status_of, by_no):
    """已采纳的旧稿声明取代 ADR-x：整份 → x 状态以「已被 ADR-本号 取代」开头；部分 → x 为旧稿时状态含该句，为新稿时其「被部分取代」列有本号。"""
    errors, no = [], a["no"]
    for ref in a["supersedes"]:
        m = re.match(r"ADR-(\d{4})(.*)", ref)
        if not m or m.group(1) not in status_of:
            errors.append(f"ADR-{no}: 取代对象不存在：{ref}"); continue
        other, partial = by_no[m.group(1)], m.group(2).strip()
        mark = f"已被 ADR-{no} 取代"
        if partial and other["new"]:
            ok = any(i.startswith(f"ADR-{no}") for i in other["partly"])
        elif partial:
            ok = mark in other["status"]
        else:
            ok = other["status"].startswith(mark)
        if not ok:
            errors.append(f"ADR-{no}: 取代 {ref}，但 ADR-{m.group(1)} 未标「{mark}」")
    return errors


def validate_catalog(adrs, ctx):
    """旧稿旁表；返回 (errors, 旧稿号 -> 旁表项)。缺旁表只由调用方告警。"""
    cat, errors = ctx["catalog"], []
    if cat is None:
        return errors, {}
    entries = {}
    for e in cat:
        no = str(e.get("id"))
        if no in entries:
            errors.append(f"catalog.yaml: ADR-{no} 重复")
        entries[no] = e
    legacy = {a["no"]: a for a in adrs if not a["new"]}
    for no, e in entries.items():
        if no not in LEGACY_FORMAT or no not in legacy:
            errors.append(f"catalog.yaml: ADR-{no} 不是旧稿（新稿不得出现，编号须属 LEGACY_FORMAT）"); continue
        if e.get("category") not in CATEGORIES:
            errors.append(f"catalog.yaml: ADR-{no} 类别须取自 {'、'.join(CATEGORIES)}：{e.get('category')}")
        errors += [f"catalog.yaml: ADR-{no} {x}" for x in check_values("BC", list(e.get("bc") or []), ctx["bcs"], "全局", False)]
        errors += [f"catalog.yaml: ADR-{no} {x}" for x in check_values("阶段", list(e.get("stage") or []), ctx["stages"], "全阶段", False)]
        if not e.get("bc") or not e.get("stage"):
            errors.append(f"catalog.yaml: ADR-{no} 的 bc、stage 不能为空")
        ranges = list(e.get("partially_superseded") or [])
        for r in ranges:
            errors += [f"catalog.yaml: ADR-{no} {x}" for x in check_item("被部分取代", str(r))]
        in_catalog = {m.group(1) for r in ranges if (m := ITEM.fullmatch(str(r)))}
        in_status = set(SUPERSEDED.findall(legacy[no]["status"])) if "已被 ADR-" in legacy[no]["status"] and not legacy[no]["status"].startswith("已被") else set()
        if in_catalog != in_status:
            errors.append(f"catalog.yaml: ADR-{no} 被部分取代所指 ADR {sorted(in_catalog)} 与状态行括注 {sorted(in_status)} 不一致")
    errors += [f"catalog.yaml: 缺少旧稿 ADR-{no}" for no in sorted(legacy) if no not in entries]
    return errors, entries


def validate(adrs, ctx):
    status_of = {a["no"]: a["status"] for a in adrs}
    by_no = {a["no"]: a for a in adrs}
    errors = []
    for a in adrs:
        errors += validate_new(a, status_of, by_no, ctx) if a["new"] else validate_legacy(a, status_of)
        if a["status"].startswith("已采纳") and not a["new"]:
            errors += validate_legacy_supersession(a, status_of, by_no)
        elif a["status"] == "已采纳":
            for ref in a["supersedes"]:
                m = ITEM.fullmatch(ref)
                if not m:
                    continue
                other = by_no.get(m.group(1))
                if not other:
                    continue
                mark = f"已被 ADR-{a['no']} 取代"
                if m.group(2):
                    ok = any(i.startswith(f"ADR-{a['no']}") for i in other["partly"]) if other["new"] else mark in other["status"]
                else:
                    ok = other["status"].startswith(mark)
                if not ok:
                    errors.append(f"ADR-{a['no']}: 取代 {ref}，但 ADR-{m.group(1)} 未标「{mark}」")
    for a in adrs:
        if a["new"]:
            continue
        for n in set(SUPERSEDED.findall(a["status"])):
            other = by_no.get(n)
            if not other or other["status"] != "已采纳" and not other["status"].startswith("已采纳") or not any(i.startswith(f"ADR-{a['no']}") for i in other["supersedes"]):
                errors.append(f"ADR-{a['no']}: 状态写已被 ADR-{n} 取代，但 ADR-{n} 须存在、已采纳且「取代」列有 ADR-{a['no']}")
    cat_errors, _ = validate_catalog(adrs, ctx)
    return errors + cat_errors


def warnings(adrs, ctx):
    out, today = [], ctx["today"]
    nums = sorted(int(a["no"]) for a in adrs)
    gaps = [f"{n:04d}" for n in range(1, nums[-1] + 1) if n not in set(nums)] if nums else []
    if ctx["catalog"] is None:
        out.append("缺少 adr/catalog.yaml：旧稿的类别、BC、阶段在视图中列「—」（骨架：build_adrs.py --catalog-skeleton）")
    if gaps and "0029" not in {a["no"] for a in adrs}:
        out.append(f"编号缺号 {'、'.join(gaps)}（ADR-0029 空壳合入后改为失败）")
    for a in adrs:
        if a["new"] and a["status"] == "提议":
            days = (today - datetime.date.fromisoformat(a["date"])).days
            if days > OVERDUE_DAYS:
                out.append(f"超期提议 ADR-{a['no']}（{a['title']}，起草 {a['drafter']}，占号 {a['date']}，已 {days} 天）")
            if a["tier"] == "正式" and a["size"] > FORMAL_LIMIT:
                out.append(f"ADR-{a['no']} 正式档源文件 {a['size']} 字节超过 {FORMAL_LIMIT}，采纳前须拆分")
    return out


def status_section(adrs, ctx):
    """STATUS.md 的「超期 ADR 提议」段（R-10）：超期提议与搁置分列，搁置不计超期。"""
    today = ctx["today"]
    over = [f"- ADR-{a['no']} {a['title']}（起草 {a['drafter']}，占号 {a['date']}，已 {(today - datetime.date.fromisoformat(a['date'])).days} 天）"
            for a in adrs if a["new"] and a["status"] == "提议" and (today - datetime.date.fromisoformat(a["date"])).days > OVERDUE_DAYS]
    held = [f"- ADR-{a['no']} {a['title']}：{a['reason']}" for a in adrs if a["new"] and a["status"] == "搁置"]
    return ("\n## 超期 ADR 提议\n\n" + ("\n".join(over) if over else "无") + "\n\n### 搁置（不计超期）\n\n" + ("\n".join(held) if held else "无") + "\n")


def numbering_errors(adrs):
    """ADR-0029 空壳合入后（src/ADR-0029.md 存在）编号须从 0001 起连续；之前只告警。"""
    have = {int(a["no"]) for a in adrs}
    if 29 not in have or not have:
        return []
    return [f"编号缺号 ADR-{n:04d}（号一经占用不复用，含已撤回、已拒绝）" for n in range(1, max(have) + 1) if n not in have]


def meta_of(a, entries):
    if a["new"]:
        return a["category"], a["bc"], a["stage"], [p for p in a["partly"]]
    e = entries.get(a["no"])
    if not e:
        return None, None, None, None
    return e["category"], list(e["bc"]), list(e["stage"]), list(e.get("partially_superseded") or [])


def line(a):
    return f"- [ADR-{a['no']}](ADR-{a['no']}.md) {a['title']}"


def index_md(adrs, entries):
    cur = [a for a in adrs if a["status"].startswith("已采纳")]
    parts = ["# ADR 索引\n\n由 `build_adrs.py` 生成，不手改。类别、BC、阶段：新稿取自头部字段，旧稿取自 `catalog.yaml`。\n"]
    rows = []
    for a in cur:
        category, _, _, partly = meta_of(a, entries)
        notes = []
        if partly:
            notes.append("范围已被取代：" + "；".join(partly))
        elif not a["new"] and "已被 ADR-" in a["status"] and category is None:
            notes.append(a["status"])
        if a["no"] in ANNOTATIONS:
            notes.append(ANNOTATIONS[a["no"]])
        rows.append(line(a) + (f"（{'；'.join(notes)}）" if notes else ""))
    parts.append("## 现行有效（已采纳；已被取代、已废弃、已拒绝、已撤回、搁置、提议的不列）\n\n" + "\n".join(rows) + "\n")
    pending = [a for a in adrs if a["status"] in ("提议", "搁置")]
    if pending:
        parts.append("## 提议与搁置\n\n" + "\n".join(f"{line(a)} — {a['status']}" + (f"：{a['reason']}" if a.get("reason") else "") for a in pending) + "\n")
    groups = {}
    for a in adrs:
        category, bcs, _, _ = meta_of(a, entries)
        groups.setdefault(category or "—", []).append(a)
    parts.append("## 按类别\n\n" + "\n\n".join(f"### {c}\n\n" + "\n".join(f"{line(a)} — {a['status'][:12]}" for a in v) for c, v in sorted(groups.items(), key=lambda kv: (kv[0] == "—", kv[0]))) + "\n")
    groups = {}
    for a in adrs:
        for b in (meta_of(a, entries)[1] or ["—"]):
            groups.setdefault(b, []).append(a)
    parts.append("## 按 BC\n\n" + "\n\n".join(f"### {b}\n\n" + "\n".join(f"{line(a)} — {a['status'][:12]}" for a in v) for b, v in sorted(groups.items(), key=lambda kv: (kv[0] == "—", kv[0]))) + "\n")
    return "\n".join(parts)


def readme_rows(adrs, entries):
    rows = ""
    for a in adrs:
        category, bcs, _, _ = meta_of(a, entries)
        rows += f"| [ADR-{a['no']}](ADR-{a['no']}.md) | {a['title']} | {category or '—'} | {'、'.join(bcs) if bcs else '—'} | {a['status']} |\n"
    return rows


def catalog_skeleton(adrs):
    out = ["adrs:"]
    for a in adrs:
        if a["new"]:
            continue
        partly = [f"ADR-{n}" for n in SUPERSEDED.findall(a["status"])] if not a["status"].startswith("已被") else []
        out.append(f'- id: "{a["no"]}"   # `{a["title"]}`\n  category: ""\n  bc: []\n  stage: []\n  partially_superseded: {partly if partly else "[]"}'.replace("'", '"'))
    return "\n".join(out) + "\n"


def load_adrs(src):
    return [parse_text(f"src/{f}", open(os.path.join(src, f), encoding="utf-8").read())
            for f in sorted(os.listdir(src)) if re.fullmatch(r"ADR-\d{4}\.md", f)]


def main(argv):
    if argv not in ([], ["--catalog-skeleton"]):
        sys.exit(__doc__)
    src = os.path.join(BASE, "src")
    try:
        adrs = load_adrs(src)
    except ValueError as e:
        sys.exit(f"ADR 源文件校验失败：{e}")
    if argv == ["--catalog-skeleton"]:
        print(catalog_skeleton(adrs), end="")
        return
    ctx = load_context()
    errors = validate(adrs, ctx) + numbering_errors(adrs)
    if errors:
        sys.exit("ADR 校验失败：\n" + "\n".join(errors))
    entries = validate_catalog(adrs, ctx)[1]
    for w in warnings(adrs, ctx):
        print("警告：" + w, file=sys.stderr)
    for a in adrs:
        open(os.path.join(BASE, f"ADR-{a['no']}.md"), "w", encoding="utf-8").write(render(a))
    rd = lambda n: open(os.path.join(src, n), encoding="utf-8").read()
    readme = rd("_readme_intro.md") + "\n| ADR | 标题 | 类别 | BC | 状态 |\n|---|---|---|---|---|\n" + readme_rows(adrs, entries) + "\n" + rd("_readme_lifecycle.md") + "\n" + rd("_readme_deviations.md")
    open(os.path.join(BASE, "README.md"), "w", encoding="utf-8").write(readme)
    open(os.path.join(BASE, "INDEX.md"), "w", encoding="utf-8").write(index_md(adrs, entries))
    print(len(adrs), "ADRs written")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        import importlib.util
        spec = importlib.util.spec_from_file_location("selftest_build_adrs", os.path.join(BASE, "selftest_build_adrs.py"))
        mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
        mod.run(sys.modules[__name__])
    else:
        main(sys.argv[1:])
