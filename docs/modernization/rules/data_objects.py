#!/usr/bin/env python3
"""DATA_OBJECTS.md: core records (DO classes with @TableName) that business rules reference."""
import os, re, glob, collections
ROOT = os.path.realpath("legacy/psa")
rules = open("analysis/psa/BUSINESS_RULES.md", encoding="utf-8").read()
cards = re.findall(r"### (RULE-\d+): .+\n((?:(?!### RULE-).*\n)*)", rules)
dos = []
for f in glob.glob(os.path.join(ROOT, "psa-backend/**/src/main/java/**/*.java"), recursive=True):
    t = open(f, encoding="utf-8", errors="ignore").read()
    m = re.search(r'@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"', t)
    if not m: continue
    cls = os.path.basename(f)[:-5]
    fields = re.findall(r"^\s*private\s+([\w<>, ]+?)\s+(\w+)\s*;", t, re.M)
    dos.append((m.group(1), cls, os.path.relpath(f, ROOT), fields))
# domain aggregates / entities (new DDD layer) — no @TableName, keyed by class name
seen = {(t, c) for t, c, _, _ in dos}
for f in glob.glob(os.path.join(ROOT, "psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/**/*.java"), recursive=True):
    t = open(f, encoding="utf-8", errors="ignore").read()
    cls = os.path.basename(f)[:-5]
    if not re.search(rf"\b(class|record)\s+{cls}\b", t) or re.search(r"\binterface\s+" + cls, t): continue
    fields = re.findall(r"^\s*private\s+(?:final\s+)?([\w<>, ?]+?)\s+(\w+)\s*[;=]", t, re.M)
    if len(fields) < 3: continue
    dos.append(("(domain) " + cls, cls, os.path.relpath(f, ROOT), fields))
uniq = {}
for d in dos: uniq.setdefault((d[0], d[1]), d)
dos = list(uniq.values())
rows = []
for table, cls, path, fields in dos:
    tname = table.replace("(domain) ", "")
    used = [rid for rid, body in cards if re.search(rf"\b{re.escape(tname)}\b|\b{re.escape(cls)}\b", body)]
    if used: rows.append((len(used), table, cls, path, fields, used))
rows.sort(reverse=True)
L = ["# DATA_OBJECTS — psa", "", "业务规则卡（`BUSINESS_RULES.md`）中引用到的核心记录：按表名或 DO 类名在规则正文中的出现次数排序。字段取自 DO 类的 `private` 声明（Java 类型）。", "",
     f"共 {len(rows)} 个对象被规则引用（DO 总数 {len(dos)}）。", "",
     "| 表 | DO 类 | 被引用规则数 | 位置 |", "|---|---|---|---|"]
L += [f"| `{t}` | {c} | {n} | `{p}` |" for n, t, c, p, f, u in rows]
for n, t, c, p, f, u in rows:
    L += ["", f"## `{t}` — {c}", f"**位置：** `{p}`  ", f"**使用它的规则：** {', '.join(u)}", "",
          "| 字段 | 类型 |", "|---|---|"] + [f"| {name} | `{typ.strip()}` |" for typ, name in f[:60]]
    if len(f) > 60: L.append(f"| …（另 {len(f)-60} 个字段） | |")
open("analysis/psa/DATA_OBJECTS.md", "w", encoding="utf-8").write("\n".join(L) + "\n")
print(len(rows), "objects;", [(t, n) for n, t, *_ in rows[:15]])
