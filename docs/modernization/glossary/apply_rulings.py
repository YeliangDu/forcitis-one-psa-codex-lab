"""用户裁定的写回（glossary/rulings.yaml）：build_terms.py 在各轮答复写回之后调用 apply(terms)。
规则见 rulings.yaml 文件头；缺 source、目标词条不存在、改名后新 id 重名、promote 的提议不存在或已是正式词条，都失败而不跳过。"""
import glob, os, re
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
PROPOSED = os.path.join(HERE, "..", "..", "..", "backend", "glossary", "proposed")
CJK = re.compile(r"[一-鿿（）]+")
TERM_REF_FIELDS = ("homonyms", "used_by", "upstream_term")


def snake(en):
    spaced = re.sub(r"([A-Z])([A-Z][a-z])", r"\1 \2", re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", en))
    return "_".join(w.lower() for w in re.split(r"[^A-Za-z0-9]+", spaced) if w and not w.isdigit())


def confirm(t, text):
    old = t.get("confirmed") or ""
    if text not in old:
        t["confirmed"] = f"{old}；{text}" if old else text


def add_alias(t, name, kind, source):
    if name != t["zh"] and name != t["en"] and all(a["name"] != name for a in t.setdefault("aliases", [])):
        t["aliases"].append({"name": name, "type": kind, "deprecated": True, "source": source})


def retarget(terms, old_id, new_id):
    for t in terms:
        for f in TERM_REF_FIELDS:
            v = t.get(f)
            if isinstance(v, list):
                t[f] = [new_id if x == old_id else x for x in v]
            elif v == old_id:
                t[f] = new_id


def apply(terms, late=False):
    doc = [r for r in yaml.safe_load(open(os.path.join(HERE, "rulings.yaml"), encoding="utf-8"))["rulings"] if bool(r.get("late")) == late]
    byid = {t["id"]: t for t in terms}
    done = 0
    for r in doc:
        if not r.get("source"):
            raise SystemExit(f"rulings.yaml {r.get('id')}：缺 source")
        op, rid = r["op"], r.get("id")
        confirmed = r.get("confirmed") or f"{r['source']}（用户裁定，rulings.yaml）"
        if op == "move_bc":
            frm, to = set(r["from"]), r["to"]
            moved = [t for t in terms if t.get("bc") in frm]
            for t in moved:
                t["bc"] = to
                confirm(t, confirmed)
            if not moved:
                raise SystemExit(f"rulings.yaml move_bc {sorted(frm)}：没有词条在这些 bc 里")
            done += 1
            continue
        if op == "promote_file":
            path = os.path.join(HERE, r["file"])
            entries = yaml.safe_load(open(path, encoding="utf-8"))["terms"]
            proposed_ids = set()
            for pf in sorted(glob.glob(os.path.join(PROPOSED, "*.yaml"))):
                proposed_ids |= {e["id"] for e in (yaml.safe_load(open(pf, encoding="utf-8")) or {}).get("terms") or []}
            for e in entries:
                if e["id"] in byid:
                    raise SystemExit(f"rulings.yaml promote_file {r['file']}：{e['id']} 已是正式词条")
                if e["id"] in proposed_ids:
                    raise SystemExit(f"rulings.yaml promote_file {r['file']}：{e['id']} 在提议文件里还有同名条目，转正须同批删除")
                entry = dict(e, status="approved", confirmed=confirmed)
                terms.append(entry)
                byid[e["id"]] = entry
                done += 1
            continue
        if op == "promote":
            if rid in byid:
                raise SystemExit(f"rulings.yaml promote {rid}：已是正式词条")
            if r["entry"]["id"] != rid:
                raise SystemExit(f"rulings.yaml promote {rid}：entry.id 不一致")
            for path in sorted(glob.glob(os.path.join(PROPOSED, "*.yaml"))):
                if any(e["id"] == rid for e in (yaml.safe_load(open(path, encoding="utf-8")) or {}).get("terms") or []):
                    raise SystemExit(f"rulings.yaml promote {rid}：{os.path.basename(path)} 里还有同名提议条目，转正须同批删除")
            entry = dict(r["entry"], status="approved", confirmed=confirmed, note=f"用户裁定转正式：{r['source']}")
            terms.append(entry)
            byid[rid] = entry
            done += 1
            continue
        if op == "move_to_bc":
            for tid in r["ids"]:
                if tid not in byid:
                    raise SystemExit(f"rulings.yaml move_to_bc：{tid} 不是正式词条")
                byid[tid]["bc"] = r["to"]
                confirm(byid[tid], confirmed)
            done += 1
            continue
        if rid not in byid:
            raise SystemExit(f"rulings.yaml {rid}：词表里没有这条正式词条")
        t = byid[rid]
        if op == "deprecate":
            for k in ("replaced_by", "until", "owner"):
                if not r.get(k):
                    raise SystemExit(f"rulings.yaml deprecate {rid}：缺 {k}")
            t.update(status="deprecated", replaced_by=r["replaced_by"], until=r["until"], owner=r["owner"])
            confirm(t, confirmed)
        elif op == "redefine":
            if r.get("kind"):
                t["kind"] = r["kind"]
            t["definition"] = r["definition"]
            for a in r.get("add_aliases") or []:
                add_alias(t, a["name"], a["type"], r["source"])
            for w in r.get("add_forbidden") or []:
                if not CJK.fullmatch(w):
                    raise SystemExit(f"rulings.yaml redefine {rid}：add_forbidden「{w}」须为纯中文")
                if w not in (t.get("forbidden") or []):
                    t.setdefault("forbidden", []).append(w)
            confirm(t, confirmed)
        elif op == "rename":
            old_zh, old_en = t["zh"], t["en"]
            new_id = r.get("to_id") or rid
            if new_id != rid and new_id in byid:
                raise SystemExit(f"rulings.yaml {rid}：改名后的 id {new_id} 与已有词条重名")
            t["zh"], t["en"], t["snake"] = r["zh"], r["en"], snake(r["en"])
            if r.get("definition"):
                t["definition"] = r["definition"]
            if r.get("kind"):
                t["kind"] = r["kind"]
            add_alias(t, old_zh, "dialect" if r.get("forbid_old", True) else "standard-zh", r["source"])
            if r.get("forbid_old", True) and CJK.fullmatch(old_zh) and old_zh not in (t.get("forbidden") or []):
                t.setdefault("forbidden", []).append(old_zh)
            if old_en != r["en"]:
                add_alias(t, old_en, "old-code", r["source"])
            if new_id != rid:
                t["id"] = new_id
                byid[new_id] = byid.pop(rid)
                retarget(terms, rid, new_id)
            confirm(t, confirmed)
        else:
            raise SystemExit(f"rulings.yaml {rid}：未知操作 {op}")
        done += 1
    return done
