"""Validate catalog.yaml (the process registry) and generate catalog.md from it.

Usage: python3 -I build_catalog.py [--check | --self-test]
Errors exit 1 and write nothing; --check validates without writing. Warnings (business without bpmn or stages) do not fail.
Runtime BPMN (errors): every backend/src/main/resources/processes/*.bpmn20.xml is referenced by exactly one entry's runtime_bpmn,
and its <process id> equals the file name; flow_docs paths exist.
"""
import collections
import re
import sys
import tempfile
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]
HERE = REPO / "docs/architecture/ssot/processes"
PROPOSALS = HERE.parents[1] / "proposals"   # 限期读取例外，见 docs/architecture/proposal-reads.yaml
CATALOG = HERE / "catalog.yaml"
OUT = HERE / "catalog.md"
RUNTIME = "backend/src/main/resources/processes"
TYPES = ["business", "event_policy", "single_aggregate", "read_model", "integration", "merged", "retired"]
STATUSES = ["draft", "sample", "reviewed", "user_confirmed"]
GROUPS = ["revenue", "finance", "people", "platform"]
ID_RE = re.compile(r"^PRC-(\d{2,3})$")
SPECIAL_OWNERS = {"reporting"}


def load():
    return yaml.safe_load(CATALOG.read_text(encoding="utf-8"))["processes"]


def valid_stages():
    vs = yaml.safe_load((HERE.parent / "value-streams" / "value-streams.yaml").read_text(encoding="utf-8"))["value_streams"]
    return {s["id"] for v in vs for s in v["stages"]}


def valid_owners():
    text = (PROPOSALS / "enterprise-aggregates-draft.md").read_text(encoding="utf-8")
    sec = text.split("### 3.2 ", 1)[1].split("\n### ", 1)[0]
    owners = set(re.findall(r"^\|\s*(B-\d{2})\s*\|", sec, re.M))
    items = (PROPOSALS / "closeout" / "structural-items.md").read_text(encoding="utf-8")
    if "B-31" in items:
        owners.add("B-31")
    return owners


def check_runtime(rows, repo=REPO):
    """runtime_bpmn and flow_docs point at existing files; each runtime BPMN is registered exactly once under its own process id."""
    errors, refs = [], collections.defaultdict(list)
    for r in rows:
        for doc in r.get("flow_docs") or []:
            if not (repo / doc).is_file():
                errors.append(f"{r['id']}: flow_docs {doc} 不存在")
        path = r.get("runtime_bpmn")
        if path:
            refs[path].append(r["id"])
            if not (repo / path).is_file():
                errors.append(f"{r['id']}: runtime_bpmn {path} 不存在")
    for f in sorted((repo / RUNTIME).glob("*.bpmn20.xml")):
        rel = f.relative_to(repo).as_posix()
        ids = refs.get(rel, [])
        if len(ids) != 1:
            errors.append(f"{rel}: 应恰被一条目录条目的 runtime_bpmn 引用，现为 {ids or '无'}")
        key = f.name[:-len(".bpmn20.xml")]
        if re.findall(r'<process\b[^>]*\bid="([^"]+)"', f.read_text(encoding="utf-8")) != [key]:
            errors.append(f"{rel}: 应只含一个 <process id=\"{key}\">")
    return errors


def check(rows, repo=REPO):
    errors, warns = check_runtime(rows, repo), []
    stages, owners = valid_stages(), valid_owners()
    by_id = {}
    for r in rows:
        i = r.get("id", "")
        if not ID_RE.match(str(i)):
            errors.append(f"{i}: 编号格式应为 PRC-NN")
            continue
        if i in by_id:
            errors.append(f"{i}: 编号重复")
        by_id[i] = r
    nums = sorted(int(i[4:]) for i in by_id)
    if nums:
        for n in range(1, nums[-1] + 1):
            if n not in nums:
                errors.append(f"PRC-{n:02d}: 空号未登记为 retired")
    for i, r in by_id.items():
        t = r.get("type")
        if t not in TYPES:
            errors.append(f"{i}: type {t!r} 不合法")
        if r.get("status") not in STATUSES:
            errors.append(f"{i}: status {r.get('status')!r} 不合法")
        if r.get("group") not in GROUPS:
            errors.append(f"{i}: group {r.get('group')!r} 不合法")
        if not r.get("name"):
            errors.append(f"{i}: 缺 name")
        if t == "merged":
            tgt = by_id.get(r.get("merged_into"))
            if tgt is None:
                errors.append(f"{i}: merged_into {r.get('merged_into')!r} 不存在")
            elif tgt.get("type") == "merged":
                errors.append(f"{i}: merged_into {r['merged_into']} 自身也是 merged")
        elif t == "retired":
            if not r.get("note"):
                errors.append(f"{i}: retired 须写 note")
        else:
            o = r.get("owner_bc")
            if not (o in owners or o in SPECIAL_OWNERS or (isinstance(o, str) and o.startswith("platform."))):
                errors.append(f"{i}: owner_bc {o!r} 未登记")
        for s in r.get("stages") or []:
            if s not in stages:
                errors.append(f"{i}: stage {s} 不在 value-streams.yaml")
        if t == "business":
            if r.get("support") and r.get("stages"):
                errors.append(f"{i}: 标了 support 却填了 stages")
            elif not r.get("stages") and not str(r.get("support") or "").strip():
                warns.append(f"{i}: business 未填 stages（不在价值流上的写 support: <理由>）")
            if not r.get("bpmn"):
                warns.append(f"{i}: business 未登记 bpmn（铺开后改为失败）")
    return errors, warns


def table(title, counter, keys, label):
    lines = [f"### {title}", "", f"| {label} | 数量 |", "|---|---|"]
    for k in keys:
        if counter.get(k):
            lines.append(f"| {k} | {counter[k]} |")
    lines.append(f"| 合计 | {sum(counter.values())} |")
    return lines + [""]


def render(rows):
    by_type = collections.Counter(r["type"] for r in rows)
    by_group = collections.Counter(r["group"] for r in rows)
    biz_group = collections.Counter(r["group"] for r in rows if r["type"] == "business")
    by_vs = collections.Counter()
    for r in rows:
        if r["type"] != "business":
            continue
        for v in sorted({s.split(".")[0] for s in r.get("stages") or []}):
            by_vs[v] += 1
    no_stage = sum(1 for r in rows if r["type"] == "business" and not r.get("stages") and not r.get("support"))
    by_vs["支撑（不在价值流上）"] = sum(1 for r in rows if r["type"] == "business" and r.get("support"))
    L = ["<!-- 由 build_catalog.py 生成，不手改 -->", "# 流程目录（生成）", "",
         "由 build_catalog.py 生成，不手改；来源 `catalog.yaml`（业务流程唯一登记）。", "",
         f"共 {len(rows)} 条，编号 PRC-01 至 PRC-{max(int(r['id'][4:]) for r in rows)}。", ""]
    L += table("按类型", by_type, TYPES, "类型")
    L += table("按组（全部类型）", by_group, GROUPS, "组")
    L += table("按组（仅业务流程）", biz_group, GROUPS, "组")
    L += table("按价值流（仅业务流程；跨价值流的流程各计一次，故合计大于业务流程数）", by_vs, sorted(by_vs), "价值流")
    L.append(f"业务流程中尚未填 stages：{no_stage} 条。\n")
    L += ["## 总表", "", "| 编号 | 名称 | 类型 | 并入 | 阶段 | owner_bc | 组 | 状态 | 人工编排 | bpmn | 可执行 BPMN |",
          "|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in rows:
        ho = {True: "是", False: "否", None: "待定"}[r.get("human_orchestration")]
        L.append("| " + " | ".join([r["id"], str(r["name"]).replace("|", "/"), r["type"], r.get("merged_into") or "",
                                    ", ".join(r.get("stages") or []), r.get("owner_bc") or "", r["group"], r["status"],
                                    ho, r.get("bpmn") or "", Path(r.get("runtime_bpmn") or "").name]) + " |")
    return "\n".join(L) + "\n"


def self_test():
    with tempfile.TemporaryDirectory() as d:
        repo = Path(d)
        (repo / RUNTIME).mkdir(parents=True)
        (repo / "doc.md").write_text("x", encoding="utf-8")
        bpmn = repo / RUNTIME / "a_flow.bpmn20.xml"
        bpmn.write_text('<definitions><process id="a_flow"/></definitions>', encoding="utf-8")
        row = {"id": "PRC-01", "runtime_bpmn": f"{RUNTIME}/a_flow.bpmn20.xml", "flow_docs": ["doc.md"]}
        assert check_runtime([row], repo) == []
        assert any("恰被一条" in e for e in check_runtime([], repo)), "未登记的可执行 BPMN 须失败"
        assert any("恰被一条" in e for e in check_runtime([row, dict(row, id="PRC-02")], repo)), "重复登记须失败"
        assert any("flow_docs" in e for e in check_runtime([dict(row, flow_docs=["gone.md"])], repo))
        assert any("runtime_bpmn" in e for e in check_runtime([row, {"id": "PRC-02", "runtime_bpmn": f"{RUNTIME}/gone.bpmn20.xml"}], repo))
        bpmn.write_text('<definitions><process id="other"/></definitions>', encoding="utf-8")
        assert any("<process id" in e for e in check_runtime([row], repo)), "process id 与文件名不符须失败"
        assert any("<process id" in e for e in check([], repo)[0]), "可执行 BPMN 的问题须算错误，不算警告"
    print("build_catalog self-test: ok")


def main():
    if "--self-test" in sys.argv:
        return self_test()
    rows = load()
    rows.sort(key=lambda r: int(str(r["id"])[4:]) if ID_RE.match(str(r["id"])) else 10**6)
    errors, warns = check(rows)
    for w in warns[:10]:
        print("警告", w)
    if len(warns) > 10:
        print(f"警告 另有 {len(warns) - 10} 条同类")
    for e in errors:
        print("错误", e)
    if errors:
        sys.exit(f"错误 {len(errors)}，警告 {len(warns)}")
    if "--check" not in sys.argv:
        OUT.write_text(render(rows), encoding="utf-8")
        print(f"已生成 {OUT.name}（警告 {len(warns)}）")


if __name__ == "__main__":
    main()
