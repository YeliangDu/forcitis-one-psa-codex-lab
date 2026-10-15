#!/usr/bin/env python3
"""第三轮用语确认的写回（一次性执行，结果入仓）：按 apply_round3.plan()
1. 从 backend/glossary/proposed/*.yaml 删去已转正、并入或归为通用词的条目（按行删除条目块，保留文件头注释与其余内容）；
2. 旧名仍在代码或界面中使用的，写 backend/glossary/proposed/round3-pending-renames.yaml 暂留旧写法（改名合入后由 one-glossary 删除对应条目）；
3. 通用词追加到 backend/glossary/terms.yaml 的 common_words，界面在用的中文通用词追加到 frontend/scripts/copy-allow-list.yaml；
4. 写 round3/RENAME_CARDS.md（改名卡清单，交 one-coordinator 派工）；
5. ACL 归类（apply_acl.plan()）：已答复的结果写进 acl/acl-classification.json（class、decision，组织同步 7 条另写 bc）；
   仍留在提议文件的条目写 scope（ACL 再写 acl_class），组织同步 7 条 bc 改为集成层，「领域·定义去外部名」的按答复改写定义。
正式词条由 glossary/build_terms.py 的第三轮应用段生成，不在这里写。
用法：python3 docs/modernization/glossary/round3/writeback.py"""
import json, os, re, sys
import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apply_round3 as a
import apply_acl

ROOT = a.ROOT
PROPOSED = os.path.join(ROOT, "backend", "glossary", "proposed")
PENDING = os.path.join(PROPOSED, "round3-pending-renames.yaml")
BACKEND_TERMS = os.path.join(ROOT, "backend", "glossary", "terms.yaml")
ALLOW = os.path.join(ROOT, "frontend", "scripts", "copy-allow-list.yaml")
CARDS = os.path.join(a.HERE, "RENAME_CARDS.md")


def remove_terms(path, ids):
    lines = open(path, encoding="utf-8").read().splitlines(keepends=True)
    out, skipping, removed = [], False, set()
    for line in lines:
        m = re.match(r"^- id: (\S+)\s*$", line)
        if m:
            skipping = m.group(1) in ids
            if skipping:
                removed.add(m.group(1))
        elif skipping and (re.match(r"^\S", line) or line.startswith("#")):
            skipping = False
        if not skipping:
            out.append(line)
    text = "".join(out)
    if re.search(r"^terms:\s*\n(?!- )", text, re.M):
        text = re.sub(r"^terms:\s*\n(?!- )", "terms: []\n", text, flags=re.M)
    open(path, "w", encoding="utf-8").write(text)
    return removed


def append_list(path, key, words, comment):
    text = open(path, encoding="utf-8").read()
    data = yaml.safe_load(text) or {}
    have = {str(w) for w in data.get(key) or []}
    new = [w for w in words if w not in have]
    if not new:
        return []
    m = re.search(rf"^{key}:\s*\n((?:[ ]*- .*\n|[ ]*#.*\n)*)", text, re.M)
    if not m:
        raise SystemExit(f"{path}: 找不到 {key} 列表")
    first = re.search(r"^([ ]*)- ", m.group(1), re.M)
    indent = first.group(1) if first else "  "
    block = f"{indent}# {comment}\n" + "".join(f"{indent}- {w}\n" for w in new)
    text = text[:m.end()] + block + text[m.end():]
    open(path, "w", encoding="utf-8").write(text)
    return new


def yaml_scalar(value):
    return json.dumps(value, ensure_ascii=False)


def mark_terms(path, rows):
    """在提议文件的条目块里写 scope / acl_class，按需改 bc 与 definition（只改这几行，其余原样）。返回已处理的 id。"""
    lines = open(path, encoding="utf-8").read().splitlines(keepends=True)
    out, current, done = [], None, set()
    for line in lines:
        m = re.match(r"^- id: (\S+)\s*$", line)
        if m:
            current = m.group(1) if m.group(1) in rows else None
        elif current and (re.match(r"^\S", line) or line.startswith("#")):
            current = None
        if current:
            row = rows[current]
            key = re.match(r"^  (scope|acl_class|bc|definition):", line)
            if key and key.group(1) in ("scope", "acl_class"):
                continue
            if key and key.group(1) == "definition" and row.get("definition"):
                line = f"  definition: {yaml_scalar(row['definition'])}\n"
            if key and key.group(1) == "bc":
                line = f"  bc: {row.get('bc') or line.split(':', 1)[1].strip()}\n"
                out.append(line)
                for k, v in apply_acl.scope_of(row["class"]).items():
                    out.append(f"  {k}: {yaml_scalar(v) if not v.isascii() else v}\n")
                done.add(current)
                continue
        out.append(line)
    open(path, "w", encoding="utf-8").write("".join(out))
    return done


def write_acl():
    decided = apply_acl.plan()
    rows = json.load(open(apply_acl.CLASSIFICATION, encoding="utf-8"))
    keys = {r["key"] for r in rows}
    for r in rows:
        d = decided.get(r["key"])
        if d:
            r["class"], r["decision"] = d["class"], d["decision"]
            if d.get("bc"):
                r["bc"], r["ruling"] = d["bc"], d["ruling"]
    for key, d in decided.items():
        if key not in keys:
            rows.append({"key": key, "class": d["class"], "reason": "用户决定", "decision": d["decision"]})
    with open(apply_acl.CLASSIFICATION, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)
        f.write("\n")
    by_file = {}
    for key, d in decided.items():
        file, term_id = key.split("#", 1)
        if file.endswith(".yaml"):
            by_file.setdefault(file, {})[term_id] = d
    marked, absent = 0, []
    for file, ids in sorted(by_file.items()):
        path = os.path.join(PROPOSED, file)
        done = mark_terms(path, ids) if os.path.exists(path) else set()
        marked += len(done)
        absent += [f"{file}#{x}" for x in sorted(set(ids) - done)]
    return marked, absent


def main():
    p = a.plan()
    by_file = {}
    for key in p["removed"]:
        file, term_id = key.split("#", 1)
        by_file.setdefault(file, set()).add(term_id)
    skipped = []
    for file, ids in sorted(by_file.items()):
        path = os.path.join(PROPOSED, file)
        done = remove_terms(path, ids) if os.path.exists(path) else set()
        skipped += sorted(f"{file}#{x}" for x in ids - done)
    pending = []
    for x in p["pending"]:
        src = x["src"]
        en = src["en"]
        pending.append({
            "id": f"round3_pending__{src['id']}", "zh": src["zh"], "en": en, "snake": a.snake(en), "kind": src.get("kind") or "value",
            "bc": src.get("bc") or "kernel",
            "definition": f"第三轮用语确认（R3-{x['code']}）已定为：{x['to']}。" + ("界面照用这个叫法，作正式词条的标准中文别名。" if x.get("ui_keeps_zh") and not x["why"] else "代码与界面改名合入前保留旧写法。"),
            "standard_ref": [f"glossary/decisions-round3-2026-10-15.json R3-{x['code']}"], "confidence": "high", "aliases": [], "forbidden": [],
            "status": "proposed", "note": "；".join(x["why"]) or "界面叫法，保留",
        })
    header = ("# 第三轮用语确认的待改名登记（one-glossary 生成，round3/writeback.py）：用户已定新名，旧名仍在代码或界面中使用。\n"
              "# 负责会话按 glossary/round3/RENAME_CARDS.md 改名并合入后，one-glossary 删除对应条目，方言禁用随之生效。\n")
    with open(PENDING, "w", encoding="utf-8") as f:
        f.write(header)
        yaml.safe_dump({"terms": pending}, f, allow_unicode=True, sort_keys=False, width=200)
    common = append_list(BACKEND_TERMS, "common_words", p["common"], "用语确认第三轮归为通用词（2026-10-15，R3）")
    allow = append_list(ALLOW, "words", p["allow_words"], "用语确认第三轮归为通用词、界面在用（2026-10-15，R3）")
    with open(CARDS, "w", encoding="utf-8") as f:
        f.write("# 第三轮用语确认改名卡\n\n用户 2026-10-15 已定新名；下列旧名仍在代码或界面中使用。负责会话改名合入后告诉 one-glossary，"
                "由其删除 `backend/glossary/proposed/round3-pending-renames.yaml` 中对应条目（方言禁用随之生效）。\n\n"
                "| 编号 | 旧名 | 新名 | 在用位置 |\n|---|---|---|---|\n")
        for c in p["cards"]:
            f.write(f"| R3-{c['code']} | {c['old']} | {c['to']} | {c['why']} |\n")
    print(f"删除提议条目 {sum(len(v) for v in by_file.values()) - len(skipped)} 条（在途分支条目不在 main 上，跳过 {len(skipped)} 条）；"
          f"待改名登记 {len(pending)} 条；改名卡 {len(p['cards'])} 张；通用词 +{len(common)}；界面允许词 +{len(allow)}")
    if skipped:
        print("跳过：" + "、".join(skipped))
    marked, absent = write_acl()
    print(f"ACL 归类：提议条目写使用范围 {marked} 条；不在提议文件中（已转正式或在途分支）{len(absent)} 条")


if __name__ == "__main__":
    main()
