"""第三轮用语确认（重做）的写回规划（用户 2026-10-15 在固定确认页逐组提交，glossary/decisions-round3-2026-10-15.json，键 R3-<编号>）。
glossary/build_terms.py 调用 apply(terms) 生成正式词条；round3/writeback.py 调用 plan() 改提议文件。ACL 归类（键 ACL-）见 apply_acl.py。
数据：sheets/R3-*.json（每条的推荐选项，重做后的单子）、inventory.json（提议原文快照）、r0-items.json（R0 输入快照）。
「同意建议」即采纳该条的推荐选项（approve / rename / merge / common_word / keep），「保持原样」即按原名转正式。
规则（与第二轮一致）：
- approve / rename：转为正式词条；改了中文名，旧中文名作方言别名；改了英文名，旧英文名作 old-code 别名。
- merge：不另立词条，旧名挂到目标词条（预判为方言的作 dialect，否则作 standard-zh）。
- common_word：英文词进 backend/glossary/terms.yaml 的 common_words，界面在用的中文进 frontend/scripts/copy-allow-list.yaml，不作业务术语。
- keep（定义写法组）：不改。
- 禁用写法只加纯中文的方言叫法；旧英文名只作 old-code 别名，不加禁用（G38 按单词拦截，Act、Form 这类旧名在别处用法正当）。
- 旧名仍在代码（含近 2 天未合并分支）或界面文字中出现的，旧条目留在 proposed/round3-pending-renames.yaml，改名合入后删除；
  中文方言照常写进 forbidden：前端用语守护跳过仍是某条术语 zh 的写法，待改名条目删除后禁用自动生效。"""
import functools, glob, json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
DECISIONS = os.path.join(HERE, "..", "decisions-round3-2026-10-15.json")
CONFIRMED = "2026-10-15（用户，用语确认第三轮）"
CJK = re.compile(r"[一-鿿（）]+")
# 测试按条目读取的提议（DocumentTypeCodeTest 要求每个单据类型代码在 platform-numbering.yaml 有同名 snake）：
# 用户已定并入 document_type_code，但条目留在原文件，直到负责会话改测试后删除（改名卡）
TEST_PINNED = {f"platform-numbering.yaml#{c}": "DocumentTypeCodeTest 按 snake 读取 platform-numbering.yaml" for c in ("qt", "ts", "jv")}
FRONTEND_ROOTS = ("frontend/apps", "frontend/packages")
SKIP_DIRS = {"node_modules", "dist", "generated"}


def load(name):
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        return json.load(f)


def words(identifier):
    spaced = re.sub(r"([A-Z])([A-Z][a-z])", r"\1 \2", re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", identifier))
    return [w.lower() for w in re.split(r"[^A-Za-z0-9]+|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])", spaced) if w and not w.isdigit()]


def snake(en):
    return "_".join(words(en))


def ui_text():
    chunks = []
    for top in FRONTEND_ROOTS:
        for directory, dirs, files in os.walk(os.path.join(ROOT, top)):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for name in files:
                if name.endswith((".ts", ".tsx")) and not re.search(r"\.test\.tsx?$|\.d\.ts$", name):
                    with open(os.path.join(directory, name), encoding="utf-8", errors="replace") as f:
                        chunks.append(f.read())
    if not chunks:
        raise SystemExit("找不到前端源码，无法判断界面是否在用旧叫法")
    return "\n".join(chunks)


@functools.lru_cache(maxsize=None)
def plan():
    """逐条换算。返回 dict：new（新正式词条）、aliases（目标 id → 别名列表）、rename_zh（已批准词条 id → 新中文名）、
    common（通用词）、removed（要从提议文件删除的 file#id）、pending（旧名仍在用、暂留的旧条目）、cards（改名卡）。"""
    decisions = json.load(open(DECISIONS, encoding="utf-8"))
    inventory = {r["key"]: r for r in load("inventory.json")}
    r0 = {r["key"]: r for r in load("r0-items.json")}
    items = [i for f in sorted(glob.glob(os.path.join(HERE, "sheets", "R3-*.json"))) for i in load(f)["items"]]
    if {f"R3-{i['code']}" for i in items} != {k for k in decisions if k.startswith("R3-")}:
        raise SystemExit("决定记录与决策单条目不一致")
    ui = ui_text()
    out = {"new": [], "aliases": {}, "rename_zh": {}, "common": [], "allow_words": [], "removed": [], "pending": [], "cards": [], "id_map": {}}

    def alias(target, name, kind, deprecated, note=""):
        entry = {"name": name, "type": kind, "deprecated": deprecated}
        if note:
            entry["source"] = note
        out["aliases"].setdefault(target, [])
        if all(a["name"] != name for a in out["aliases"][target]):
            out["aliases"][target].append(entry)

    def chosen(i):
        d = decisions[f"R3-{i['code']}"]
        if d["choice"] == "approve":
            return next(o for o in i["options"] if o["recommended"])
        if d["choice"] == "keep":
            return {"action": "approve", "zh": i["zh"], "en": i["en"], "into": ""}
        zh_en = [x.strip() for x in d["renameTo"].split("/")]
        return {"action": "rename", "zh": zh_en[0], "en": zh_en[1] if len(zh_en) > 1 else i["en"], "into": ""}

    def old_in_use(src, zh_changed, en_changed):
        why = []
        if zh_changed and src["zh"] in ui:
            why.append(f"界面文字仍用「{src['zh']}」")
        usage = src.get("usage") or {}
        if en_changed and (usage.get("code") or usage.get("branches")):
            where = "、".join(usage.get("branches") or []) or "main"
            why.append(f"代码仍用 {src['en']}（{where}）")
        return why

    # 先转正（并入目标可能是本轮新词条），再处理并入
    later = []
    for i in items:
        o = chosen(i)
        if i["code"].startswith("DF-") or o["action"] == "keep":
            continue
        if i["key"].startswith("r0#"):
            src = r0[i["key"]]
            if i["key"].startswith(("r0#A", "r0#D")) or o["action"] == "merge":
                later.append((i, o, src))
                continue
            en = o.get("en") or src["en"]
            new_id = snake(en) if en != src["en"] else src["snake"]
            out["id_map"][src["snake"]] = new_id
            entry = dict(id=new_id, zh=o.get("zh") or src["zh"], en=en, snake=snake(en), kind=src["kind"], bc=src["bc"],
                         definition=src["definition"], standard_ref=[src["standard"]], confidence=i.get("confidence", "medium"),
                         aliases=[], forbidden=[], status="approved",
                         note=f"用语确认第三轮转正式（R0 第三轮输入 {i['key'][3:]}，R3-{i['code']}）", confirmed=CONFIRMED)
            if en != src["en"]:
                entry["aliases"].append({"name": src["en"], "type": "standard-en" if src["key"] == "r0#N-credit_loss_allowance" else "old-code",
                                         "deprecated": src["key"] != "r0#N-credit_loss_allowance",
                                         **({"source": "IFRS 9 口径（loss allowance）的英文别名"} if src["key"] == "r0#N-credit_loss_allowance" else {})})
            if entry["zh"] != src["zh"]:
                entry["aliases"].append({"name": src["zh"], "type": "dialect", "deprecated": True})
                if CJK.fullmatch(src["zh"]):
                    entry["forbidden"].append(src["zh"])
            out["new"].append(entry)
            continue
        src = inventory[i["key"]]
        if i["key"] in TEST_PINNED:
            out["cards"].append({"code": i["code"], "old": f"{src['zh']} / {src['en']}", "to": f"并入 {o['into']}",
                                 "why": TEST_PINNED[i["key"]] + "，条目暂留，测试改为读 document_type_code 取值后删除"})
        else:
            out["removed"].append(i["key"])
        if o["action"] == "merge":
            later.append((i, o, src))
            continue
        if o["action"] == "common_word":
            out["common"] += [w for w in words(src["en"]) if w not in out["common"]]
            if src["zh"] in ui and CJK.fullmatch(src["zh"]):
                out["allow_words"].append(src["zh"])
            continue
        zh, en = o.get("zh") or src["zh"], o.get("en") or src["en"]
        prefix = src["id"].split("__")[0] + "__" if "__" in src["id"] else ""
        new_id = src["id"] if en == src["en"] else prefix + snake(en)
        out["id_map"][src["id"]] = new_id
        aliases = [a if isinstance(a, dict) else {"name": a, "type": "standard-zh", "deprecated": False} for a in src.get("aliases") or []]
        if i["verdict"] == "方言":
            aliases = [dict(a, type="dialect", deprecated=True) if a.get("type") == "undetermined" else a for a in aliases]
        entry = dict(id=new_id, zh=zh, en=en, snake=snake(en), kind=src["kind"], bc=src["bc"], definition=src["definition"],
                     standard_ref=list(src.get("standard_ref") or []), confidence=i.get("confidence", "medium"),
                     aliases=aliases, forbidden=list(src.get("forbidden") or []), status="approved",
                     note=f"用语确认第三轮转正式（原提议 {src['file']}#{src['id']}，R3-{i['code']}）", confirmed=CONFIRMED)
        if en != src["en"]:
            entry["aliases"].append({"name": src["en"], "type": "old-code", "deprecated": True})
        if zh != src["zh"]:
            entry["aliases"].append({"name": src["zh"], "type": "dialect", "deprecated": True})
            if CJK.fullmatch(src["zh"]):
                entry["forbidden"].append(src["zh"])
        why = old_in_use(src, zh != src["zh"], en != src["en"])
        if why:
            out["pending"].append({"src": src, "code": i["code"], "to": f"{zh} / {en}（{new_id}）", "why": why})
        out["new"].append(entry)

    for i, o, src in later:
        if i["key"].startswith("r0#D"):
            for target, names in src["dialects"].items():
                for n in names:
                    alias(out["id_map"].get(target, target), n, "dialect", True, f"R0 已裁定方言（R3-{i['code']}）")
            continue
        into = out["id_map"].get(o["into"], o["into"])
        if i["key"].startswith("r0#A"):
            if o["action"] == "rename":
                out["rename_zh"][into] = o["zh"]
                alias(into, src["alias"], "standard-zh", False, f"在用报表叫法，第三轮定为正式中文名（R3-{i['code']}）")
                continue
            name = src["alias"]
            alias(into, name, "dialect", True, f"R0 在用报表叫法（R3-{i['code']}）")
            if i["code"] == "RA-12":
                alias(into, "坏账准备计提申请", "standard-zh", False, "原「坏账申请」的正式叫法（R3-RA-12）")
                alias("ar_written_off", "坏账", "dialect", True, "单说「坏账」指核销的用法归为方言（R3-RA-12）")
            continue
        dialect = i["verdict"] == "方言"
        old_zh, old_en = src["zh"], src["en"]
        target_zh = o.get("zh") or ""
        if old_zh and old_zh != target_zh:
            alias(into, old_zh, "dialect" if dialect else "standard-zh", dialect, f"第三轮并入（R3-{i['code']}）")
        if old_en and old_en != (o.get("en") or ""):
            alias(into, old_en, "old-code", True, f"第三轮并入（R3-{i['code']}）")
        if i["code"] == "AP-26":
            out["rename_zh"][into] = "记账凭证"
            alias(into, "会计凭证", "dialect", True, "第三轮改正式中文名为「记账凭证」（R3-AP-26）")
        if not i["key"].startswith("r0#"):
            why = old_in_use(src, True, True)
            if why:
                out["pending"].append({"src": src, "code": i["code"], "to": f"并入 {into}", "why": why, "keep_zh": not dialect})
    if "journal_voucher" in out["rename_zh"] and "会计凭证" in ui:
        out["pending"].append({"src": {"id": "journal_voucher", "zh": "会计凭证", "en": "JournalVoucher", "file": "terms.yaml", "kind": "document",
                                       "bc": "accounting.voucher", "definition": "正式词条 journal_voucher 的原中文名"},
                               "code": "AP-26", "to": "记账凭证 / JournalVoucher（journal_voucher）", "why": ["界面文字仍用「会计凭证」"]})
    import yaml
    with open(os.path.join(HERE, "..", "terms.yaml"), encoding="utf-8") as f:
        final_zh = {t["zh"] for t in yaml.safe_load(f)["terms"] if t["id"] not in out["rename_zh"]}
    final_zh |= {e["zh"] for e in out["new"]} | set(out["rename_zh"].values())
    kept = []
    for x in out["pending"]:
        x["why"] = [w for w in x["why"] if not (w.startswith("界面文字") and x["src"]["zh"] in final_zh)]
        if x["why"]:
            kept.append(x)
    out["pending"] = kept
    # 预判为正式的并入（如「部门」作组织单元的标准中文别名）：界面照用，只留词汇，不开改名卡
    for x in out["pending"]:
        if x.get("keep_zh"):
            x["why"] = [w for w in x["why"] if not w.startswith("界面文字")]
            x["ui_keeps_zh"] = True
    out["cards"] += [{"code": p["code"], "old": f"{p['src']['zh']} / {p['src']['en']}", "to": p["to"], "why": "；".join(p["why"])}
                    for p in out["pending"] if p["why"]]
    return out


def confirm(t):
    old = t.get("confirmed") or ""
    if CONFIRMED not in old:
        t["confirmed"] = f"{old}；{CONFIRMED}" if old else CONFIRMED


def apply(terms):
    """build_terms.py 调用：把第三轮结果写进正式词条列表（就地修改），返回新增条数。"""
    p = plan()
    byid = {t["id"]: t for t in terms}
    for e in p["new"]:
        if e["id"] in byid:
            raise SystemExit(f"第三轮新词条 {e['id']} 与已有正式词条重名")
        byid[e["id"]] = e
        terms.append(e)
    for target, als in p["aliases"].items():
        if target not in byid:
            raise SystemExit(f"第三轮并入目标 {target} 不存在")
        t = byid[target]
        t.setdefault("aliases", [])
        for a in als:
            if a["name"] == t["zh"]:
                continue
            same = [x for x in t["aliases"] if x["name"] == a["name"]]
            if same and same[0].get("type") in ("undetermined", "zh-internal"):
                same[0].update(type=a["type"], deprecated=a["deprecated"], decision=a.get("source", f"用语确认第三轮"))
            elif same:
                continue
            else:
                t["aliases"].append(a)
            if a["type"] == "dialect" and CJK.fullmatch(a["name"]) and a["name"] not in (t.get("forbidden") or []):
                t.setdefault("forbidden", []).append(a["name"])
        confirm(t)
    for target, zh in p["rename_zh"].items():
        t = byid[target]
        old = t["zh"]
        t["zh"] = zh
        t["aliases"] = [a for a in t.get("aliases") or [] if a["name"] != zh]
        if all(a["name"] != old for a in t["aliases"]):
            t["aliases"].append({"name": old, "type": "dialect", "deprecated": True})
        if CJK.fullmatch(old) and old not in (t.get("forbidden") or []):
            t.setdefault("forbidden", []).append(old)
        confirm(t)
    return len(p["new"])
