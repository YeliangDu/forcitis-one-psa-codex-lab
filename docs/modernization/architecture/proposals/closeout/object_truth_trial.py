"""业务对象真条件试跑（只读）。

用法：python3 object_truth_trial.py [--detail]
输入：信息图总表、terms.yaml、聚合稿，及 one-data 两份表清单（经 git show 读取）。
"""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
MOD = HERE.parents[2]
REPO = MOD.parents[1]
INFO_MAP = MOD / "architecture/ssot/10-objects/ENTERPRISE_INFO_MAP.md"
AGG = MOD / "architecture/proposals/ENTERPRISE_AGGREGATES_DRAFT.md"
TERMS = MOD / "glossary/terms.yaml"
TABLE_LISTS = [
    "3680451f:docs/modernization/data/TABLE_OBJECT_LIST_PROPOSED.md",
    "af6c5a7e:docs/modernization/data/TABLE_OBJECT_LIST.md",
]
EMPTY = {"", "—", "-", "无", "待定"}
OBJ_RE = re.compile(r"OBJ-(\d+)")


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def load_objects():
    text = INFO_MAP.read_text(encoding="utf-8")
    section = text.split("\n## 1.", 1)[1].split("\n### 1.1", 1)[0]
    objs = {}
    for line in section.splitlines():
        if not re.match(r"^\| OBJ-\d+ \|", line):
            continue
        c = cells(line)
        if len(c) < 12:
            sys.exit(f"总表行列数不足：{c[0]}")
        oid = c[0]
        if oid in objs:
            sys.exit(f"总表编号重复：{oid}")
        objs[oid] = dict(
            name=re.sub(r"[*【（(].*", "", c[1]).strip(), definition=c[2], dep=c[3],
            type=c[4], states=c[5], relations=c[6], rules=c[7], facts=c[8],
            term_col=c[10],
        )
    return objs


def is_object(o):
    return "非对象" not in o["dep"] and "已非对象" not in o["definition"] and "降为" not in o["dep"]


def load_term_names():
    names = set()
    for line in TERMS.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\s+zh:\s*'?([^']+?)'?\s*$", line)
        if m:
            names.add(m.group(1))
    if not names:
        sys.exit("terms.yaml 未读到 zh 词条")
    return names


def agg_objects():
    found = set()
    for line in AGG.read_text(encoding="utf-8").splitlines():
        if re.match(r"^\| AGG-\d+ \|", line):
            c = cells(line)
            found |= {f"OBJ-{n}" for n in OBJ_RE.findall(c[1] + c[2])}
    return found


def table_objects():
    found = set()
    for spec in TABLE_LISTS:
        out = subprocess.run(["git", "-C", str(REPO), "show", spec],
                             capture_output=True, text=True)
        if out.returncode != 0:
            sys.exit(f"取不到表清单 {spec}：{out.stderr.strip()}")
        for line in out.stdout.splitlines():
            if not line.startswith("| ") or line.startswith("|---"):
                continue
            c = cells(line)
            if len(c) < 5:
                continue
            obj_col = " ".join(c[3:5])
            if "无（" in obj_col or "已去掉" in obj_col:
                continue
            found |= {f"OBJ-{n}" for n in OBJ_RE.findall(obj_col)}
    return found


def finite_states(s):
    if s.strip() in EMPTY:
        return False
    parts = [p for p in re.split(r"→|/|／|、|；", re.sub(r"（[^）]*）", "", s)) if p.strip()]
    return len(parts) >= 2


IDENTITY_HINT = re.compile(r"编号|代码|号码|唯一|查重|证件|业务键|单号")


def conditions(terms, aggs, tables):
    return [
        ("T1 名词有词条", "BIZBOK 信息概念 / G190 名词 / ArchiMate 名词",
         lambda oid, o: o["name"] in terms),
        ("T2 有定义", "BIZBOK 定义 / TOGAF Business Information 语义",
         lambda oid, o: o["definition"] not in EMPTY),
        ("T3 依附已判", "BIZBOK 一级 / 次级",
         lambda oid, o: o["dep"].lstrip("*").startswith(("一级", "次级"))),
        ("T4 有类型", "BIZBOK 类型",
         lambda oid, o: o["type"] not in EMPTY),
        ("T5 有限状态（≥2）", "BIZBOK 状态 / DDD Entity 生命周期",
         lambda oid, o: finite_states(o["states"])),
        ("T6 有关系", "BIZBOK 关系 / ArchiMate 关系",
         lambda oid, o: o["relations"] not in EMPTY),
        ("T7 归入聚合", "DDD Aggregate",
         lambda oid, o: oid in aggs),
        ("T8 有表（现有或提议）", "TOGAF Data Entity / ArchiMate Data Object / DAMA 实体",
         lambda oid, o: oid in tables),
        ("（参考）身份线索", "DDD Entity / DAMA 业务键（产物无字段，只作线索）",
         lambda oid, o: bool(IDENTITY_HINT.search(o["rules"] + o["definition"]))),
        ("（标注）产生事实", "Kimball 事实（标注，不作条件）",
         lambda oid, o: o["facts"] not in EMPTY and not o["facts"].startswith("—")),
    ]


def main():
    detail = "--detail" in sys.argv
    objs = {k: v for k, v in load_objects().items() if is_object(v)}
    conds = conditions(load_term_names(), agg_objects(), table_objects())
    print(f"有效对象 {len(objs)}")
    for name, src, fn in conds:
        miss = [f"{k} {v['name']}" for k, v in objs.items() if not fn(k, v)]
        print(f"{name}｜{src}｜命中 {len(objs) - len(miss)}｜未命中 {len(miss)}")
        if detail and miss:
            print("    " + "；".join(miss))


if __name__ == "__main__":
    main()
