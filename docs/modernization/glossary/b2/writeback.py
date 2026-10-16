#!/usr/bin/env python3
"""B2 一次性改写 backend/glossary/proposed/*.yaml（结果入仓）：类别按 apply_b2 的规则合并或按 kind-judgement.json 判定；
移出词条的条目（bcs、common_word、attribute_of、bc_description）从提议文件删去，说明写进 bcs.yaml 由 one-glossary 手工完成。
只改 kind 行与整块删除，其余原样。用法：python3 docs/modernization/glossary/b2/writeback.py"""
import glob, os, re, sys
import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apply_b2

ROOT = os.path.abspath(os.path.join(apply_b2.HERE, "..", "..", "..", ".."))
PROPOSED = os.path.join(ROOT, "backend", "glossary", "proposed")


def main():
    judged = {(i["source"], i["id"]): i for i in apply_b2.judgement() if i["source"] != "terms"}
    seen, changed, removed = set(), 0, []
    for path in sorted(glob.glob(os.path.join(PROPOSED, "*.yaml"))):
        name = os.path.basename(path)
        doc = yaml.safe_load(open(path, encoding="utf-8")) or {}
        plan = {}
        for e in doc.get("terms") or []:
            j = judged.get((name, e["id"]))
            if j:
                seen.add((name, e["id"]))
            plan[e["id"]] = apply_b2.new_kind(e, j) if e["kind"] != "error_code" else "error_code"
        lines, out, cur, skipping = open(path, encoding="utf-8").read().splitlines(keepends=True), [], None, False
        for line in lines:
            m = re.match(r"^- id: (\S+)\s*$", line)
            if m:
                cur = m.group(1)
                skipping = plan.get(cur) in apply_b2.MOVED
                if skipping:
                    removed.append(f"{name}#{cur}")
            elif cur and (re.match(r"^\S", line) or line.startswith("#")):
                cur, skipping = None, False
            if skipping:
                continue
            k = re.match(r"^  kind: (\S+)\s*$", line)
            if cur and k and plan.get(cur) != k.group(1):
                line = f"  kind: {plan[cur]}\n"
                changed += 1
            out.append(line)
        text = "".join(out)
        if re.search(r"^terms:\s*\n(?!- )", text, re.M):
            text = re.sub(r"^terms:\s*\n(?!- )", "terms: []\n", text, flags=re.M)
        open(path, "w", encoding="utf-8").write(text)
    missing = sorted(set(judged) - seen)
    if missing:
        raise SystemExit("判定表里的提议条目没找到：" + "、".join(f"{a}#{b}" for a, b in missing))
    print(f"提议条目改类别 {changed} 条，移出 {len(removed)} 条：{'、'.join(removed)}")


if __name__ == "__main__":
    main()
