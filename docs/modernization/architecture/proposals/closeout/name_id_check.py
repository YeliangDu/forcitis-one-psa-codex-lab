"""架构对象命名与编号检查（只读，只出报告；PROCESS_METHOD_DRAFT §11）。

用法: python3 -I name_id_check.py [--base REF] [--self-test]
  编号：目录内重号；对照 REF（默认 origin/main，main 尚无目录时用 HEAD）版本的目录——删号（应标 retired）、已退役或已合并的号重新启用（复用）；号段空缺只列出。
  去向：merged 须 merged_into 指向现存未退役、未合并的号；status: retired 须写原因（retired_reason 或 note）；split_into 每个号须存在。
  名称：流程 ≤ 12 字、不以条件开头（当 / 若 / 如果 / 在…，或「××后 / 时」起首）、动作不超过两个（「并」至多一个）、含「、」的列出复核、
        名称宜含主对象名（主对象取 object-layer.yaml，先跑 object_layer_check.py）；阶段 ≤ 6 字；价值流、阶段以动词开头（VERBS 词表，
        不在表内的列出人工判）；能力以「管理」结尾。命令、事件、状态的「已××」格式随改名对照表另查。
"""
import re
import subprocess
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
PROPOSALS = HERE.parent
SSOT = PROPOSALS.parent / "ssot"
CATALOG = Path(__file__).resolve().parents[1].parent / "ssot" / "09-processes" / "catalog.yaml"   # 流程目录正本
VS_FILE = SSOT / "05-value-streams" / "value-streams.yaml"
CAPS = PROPOSALS / "capabilities.v2.yaml"
LAYER = HERE / "object-layer.yaml"
INFO_MAP = SSOT / "10-objects" / "ENTERPRISE_INFO_MAP.md"
REPO_PATH = "docs/modernization/architecture/ssot/09-processes/catalog.yaml"
PROC_MAX, STAGE_MAX = 12, 6
VERBS = tuple(("选定 获得 约定 支付 确认 任用 报销 达成 沉淀 签订 开具 开票 收回 回款 付款 申报 缴纳 维护 招聘 录用 入职 申请 借支 核定 编制 预测 复盘 "
         "关账 发布 起草 评审 修订 废止 出资 分配 退回 结算 发放 结清 启动 策划 检查 整改 跟踪 认可 准入 签约 履约 履行 登记 提出 建立 处置 获取 "
         "交付 完成 形成 拓展 投标 考勤 计算 考核 评估 归档 封存 设计 施工 招采 深化 确定").split())
ACTIVITY = re.compile(r"(检查|评审|确认|核定|评估|结算|申报|复盘|申请|审批|登记|核验|盘点)$")   # 活动名收尾：业务对象不用活动名
COND = re.compile(r"^(当|若|如果|如|在|待)|^[^并、]{1,8}?(后|时)(?=[一-鿿]{2})")


def num(x):
    return int(re.sub(r"\D", "", str(x)) or 0)


def load_base(ref):
    try:
        txt = subprocess.run(["git", "show", f"{ref}:{REPO_PATH}"], capture_output=True, text=True, check=True, cwd=HERE).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    return (yaml.safe_load(txt) or {}).get("processes") or []


def inactive(p):
    return p.get("type") == "merged" or p.get("status") == "retired" or bool(p.get("split_into"))


def check(catalog, base, vs_doc, caps_doc, primary, onames, objects=None):
    I = {"dup": [], "deleted": [], "reused": [], "gaps": [], "dest": [], "proc_len": [], "proc_cond": [], "proc_acts": [], "proc_list": [],
         "proc_obj": [], "stage_len": [], "verb": [], "cap": [], "obj_activity": [], "base_note": ""}
    ids = [p["id"] for p in catalog]
    seen = set()
    for i in ids:
        if i in seen:
            I["dup"].append(i)
        seen.add(i)
    by = {p["id"]: p for p in catalog}
    if base is None:
        I["base_note"] = "对照版本读不到目录，删号与复用未查"
    else:
        for b in base:
            now = by.get(b["id"])
            if not now:
                I["deleted"].append(f"{b['id']} {b.get('name', '')}")
            elif inactive(b) and not inactive(now):
                I["reused"].append(f"{b['id']}：原 {b.get('name', '')}（已合并或退役），现 {now.get('name', '')}")
    nums = sorted(num(i) for i in seen)
    if nums:
        I["gaps"] = [f"PRC-{n:02d}" for n in range(1, nums[-1]) if n not in set(nums)]
    for p in catalog:
        if p.get("type") == "merged":
            t = by.get(p.get("merged_into"))
            if not t:
                I["dest"].append(f"{p['id']} 合并去向缺或不存在：{p.get('merged_into')}")
            elif inactive(t):
                I["dest"].append(f"{p['id']} 合并到的 {t['id']} 本身已合并或退役")
        if p.get("status") == "retired" and not (p.get("retired_reason") or p.get("note")):
            I["dest"].append(f"{p['id']} 已退役未写原因")
        for x in p.get("split_into") or []:
            if x not in by:
                I["dest"].append(f"{p['id']} 拆分去向 {x} 不存在")
        if inactive(p):
            continue
        n = str(p.get("name", ""))
        if len(n) > PROC_MAX:
            I["proc_len"].append(f"{p['id']} {n}（{len(n)} 字）")
        if COND.search(n):
            I["proc_cond"].append(f"{p['id']} {n}")
        if n.count("并") > 1:
            I["proc_acts"].append(f"{p['id']} {n}")
        if "、" in n:
            I["proc_list"].append(f"{p['id']} {n}")
        o = primary.get(p["id"])
        if o and onames.get(o) and re.sub(r"[（(].*", "", onames[o]) not in n:
            I["proc_obj"].append(f"{p['id']} {n}：主对象 {o} {re.sub(r'[（(].*', '', onames[o])}")
    for vs in vs_doc.get("value_streams") or []:
        if not str(vs.get("name", "")).startswith(VERBS):
            I["verb"].append(f"{vs['id']} {vs.get('name')}")
        for s in vs.get("stages") or []:
            sn = str(s.get("name", ""))
            if len(sn) > STAGE_MAX:
                I["stage_len"].append(f"{s['id']} {sn}（{len(sn)} 字）")
            if not sn.startswith(VERBS):
                I["verb"].append(f"{s['id']} {sn}")
    for o, nm in sorted((objects or {}).items(), key=lambda x: num(x[0])):
        if ACTIVITY.search(re.sub(r"[（(].*", "", nm).strip()):
            I["obj_activity"].append(f"{o} {re.sub(r'[（(].*', '', nm).strip()}")
    for c in caps_doc.get("capabilities") or []:
        for x in [c] + list(c.get("l2") or []):
            if not str(x.get("name", "")).endswith("管理"):
                I["cap"].append(f"{x['id']} {x.get('name')}")
    return I


SECTIONS = [("dup", "编号 重号"), ("deleted", "编号 删号（应标 retired）"), ("reused", "编号 复用"), ("dest", "去向 不齐"),
            ("proc_len", f"流程名超过 {PROC_MAX} 字"), ("proc_cond", "流程名以条件开头"), ("proc_acts", "流程名动作超过两个"),
            ("proc_list", "流程名含「、」（多动作或多对象，复核）"), ("proc_obj", "流程名不含主对象名"),
            ("stage_len", f"阶段名超过 {STAGE_MAX} 字"), ("verb", "价值流、阶段名不以词表动词开头（人工判动宾）"), ("cap", "能力名不以「管理」结尾"), ("obj_activity", "业务对象名是活动名（应为结果或记录名）")]


def render(I):
    out = ["# 命名与编号检查（name_id_check.py 生成，勿手改）", "", "规则见 PROCESS_METHOD_DRAFT.md §11；收尾期只出报告。" + (f" 注：{I['base_note']}。" if I["base_note"] else ""), "",
           "| 项 | 数 |", "|---|---|"] + [f"| {t} | {len(I[k])} |" for k, t in SECTIONS] + [f"| 号段空缺（只列出） | {len(I['gaps'])} |", ""]
    for k, t in SECTIONS:
        if I[k]:
            out += [f"## {t}（{len(I[k])}）", ""] + [f"- {x}" for x in I[k]] + [""]
    if I["gaps"]:
        out += ["## 号段空缺（只列出，可能是预留或未入目录）", "", "、".join(I["gaps"]), ""]
    return "\n".join(out) + "\n"


def self_test():
    cat = [{"id": "PRC-01", "name": "确认收入"}, {"id": "PRC-02", "name": "x", "type": "merged", "merged_into": "PRC-09"},
           {"id": "PRC-04", "name": "入职后参保增员与建立薪酬档案"}, {"id": "PRC-05", "name": "申请并审批并支付费用"},
           {"id": "PRC-06", "name": "退役", "status": "retired"}, {"id": "PRC-07", "name": "再用"}, {"id": "PRC-01", "name": "重号"}]
    base = [{"id": "PRC-03", "name": "已删"}, {"id": "PRC-07", "name": "旧", "type": "merged", "merged_into": "PRC-01"}]
    vs = {"value_streams": [{"id": "VS-X", "name": "获得交付成果", "stages": [{"id": "VS-X.1", "name": "市场与线索"}, {"id": "VS-X.2", "name": "签订合同并归档文件"}]}]}
    caps = {"capabilities": [{"id": "CAP-1", "name": "协议管理", "l2": [{"id": "CAP-1.2", "name": "协议"}]}]}
    I = check(cat, base, vs, caps, {"PRC-01": "OBJ-81"}, {"OBJ-81": "收入确认（说明）"}, {"OBJ-81": "收入确认（说明）", "OBJ-01": "往来单位"})
    ok = lambda n, c: print(f"自测 {n}: {'通过' if c else '失败'}") or c
    res = [ok("重号", I["dup"] == ["PRC-01"]), ok("删号", I["deleted"] == ["PRC-03 已删"]), ok("复用", len(I["reused"]) == 1),
           ok("去向", any("PRC-02" in x for x in I["dest"]) and any("PRC-06" in x for x in I["dest"])),
           ok("条件开头", I["proc_cond"] == ["PRC-04 入职后参保增员与建立薪酬档案"]), ok("超长", any("PRC-04" in x for x in I["proc_len"])),
           ok("动作超过两个", I["proc_acts"] == ["PRC-05 申请并审批并支付费用"]), ok("主对象名（括注去掉后比）", I["proc_obj"][0] == "PRC-01 确认收入：主对象 OBJ-81 收入确认"),
           ok("阶段名", any("VS-X.1" in x for x in I["verb"]) and any("VS-X.2" in x for x in I["stage_len"]) and not any("VS-X " in x for x in I["verb"])),
           ok("能力名", I["cap"] == ["CAP-1.2 协议"]), ok("对象活动名", I["obj_activity"] == ["OBJ-81 收入确认"]), ok("空缺", I["gaps"] == ["PRC-03"])]
    if not all(res):
        sys.exit(1)
    print("自测 通过")


def main(argv):
    if "--self-test" in argv:
        return self_test()
    ref = argv[argv.index("--base") + 1] if "--base" in argv else "origin/main"
    catalog = yaml.safe_load(CATALOG.read_text(encoding="utf-8"))["processes"]
    primary = {}
    if LAYER.exists():
        primary = {p["id"]: p["primary_object"] for p in yaml.safe_load(LAYER.read_text(encoding="utf-8")).get("processes") or [] if p.get("primary_object")}
    onames = {}
    for line in INFO_MAP.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\|\s*(OBJ-\d+)\s*\|\s*([^|]+?)\s*\|", line)   # 只认对象总表行（OBJ 在首列）；「| M-16 | OBJ-35 |」类对照行不是名称
        if m:
            onames.setdefault(m.group(1), m.group(2))
    base = load_base(ref)
    if base is None and "--base" not in argv:
        ref, base = "HEAD", load_base("HEAD")   # 目录未进 main 前对照本分支上次提交
    nonobj = {m.group(1) for m in (re.match(r"^\|\s*(OBJ-\d+)\s*\|[^|]*\|\s*([^|]*)\|", l) for l in INFO_MAP.read_text(encoding="utf-8").splitlines())
              if m and re.search(r"非对象|行号保留", m.group(2))}
    I = check(catalog, base, yaml.safe_load(VS_FILE.read_text(encoding="utf-8")), yaml.safe_load(CAPS.read_text(encoding="utf-8")), primary, onames,
              {o: n for o, n in onames.items() if o not in nonobj})
    md = render(I)
    if "--no-write" not in argv:
        (HERE / "NAME_ID_REPORT.md").write_text(md, encoding="utf-8")
    print("\n".join(l for l in md.splitlines() if l.startswith("| ") and not l.startswith("| 项")))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
