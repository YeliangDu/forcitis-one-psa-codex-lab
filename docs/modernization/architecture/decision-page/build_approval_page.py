"""审批口径与加审规则确认页：读 closeout/APPROVAL_RULES.yaml，出 approval.html、APPROVAL_PAGE.json。业务口径页，推荐项预选（用户 #362 裁定 B）。"""
import json
import re
from pathlib import Path

import yaml

HERE = Path(__file__).parent
RULES = HERE.parent / "proposals" / "closeout" / "APPROVAL_RULES.yaml"
LEVEL = {"abnormal": 0, "special": 1, "none": 2}


def item(no, grp, title, ctx, opts, basis, rec=None):
    o = [{"label": l, "effect": e, "rec": i == rec, "sug": i == rec} for i, (l, e) in enumerate(opts)]
    o.append({"label": "暂不定（我先不选）", "effect": "不预设，留待以后", "rec": rec is None, "sug": False})
    return {"no": no, "grp": grp, "title": title, "src": "", "ctx": ctx, "opts": o, "basis": basis, "practice": [], "now": "", "check": "", "key": "APR~" + no}


def main():
    data = yaml.safe_load(RULES.read_text(encoding="utf-8"))
    items = [
        item("A-0", "base", "正常情况由谁审批", "按你的原则：正常只一人审批，审批人是费用或业务归属对象的责任人——项目费用由项目经理批，部门费用由部门负责人批，其他成本对象由其登记的责任人批；系统按归属对象自动找人，不按单据类型或提交人所在部门写死。付款的制单、复核、出纳分离属于不相容职务，另行控制，不算审批节点。",
             [("按此执行", "三组所有流程的审批都按「一级审批 → 条件加审（最多两级）」画")], ["你的原话「看谁对费用负责，谁负责谁批」「正常情况上单人审批，异常情况加一个审批节点，特殊情况两个加审节点」"], rec=0),
        item("A-1", "base", "金额阈值怎么定", "「金额超过阈值」分两档：超第一档算异常（加一级），超第二档算特殊（加两级）。阈值作为配置数据，可随时调整、按生效日期生效。",
             [("按单据类型分别给两档（在说明里写，例如报销、付款、采购、开票各几万）", "最贴合实际；你要给一张小表"), ("先统一两档，运行一段时间后再按单据类型细分（在说明里写两个数）", "上线简单；个别单据类型可能偏松或偏紧")], ["APPROVAL_RULES.yaml AR-01、AR-02"]),
    ]
    for r in data["rules"]:
        if r["id"] in ("AR-01", "AR-02"):
            continue
        if r["level"] == "tbd":
            if r["id"] == "AR-11":
                opts = [("由其汇报线上级代批", "仍是一人审批，只是换人"), ("按异常处理，加一级审批", "多一个节点"), ("不加审，只事后抽查", "最快；靠抽查发现问题")]
            else:
                opts = [("由归属对象的上级组织负责人代批", "不卡单"), ("由事先指定的代理人批", "要维护代理人"), ("退回，先补登责任人再提交", "最严；会卡单")]
            items.append(item(r["id"], "rules", r["name"], "条件：" + r["condition"] + "。", opts, ["APPROVAL_RULES.yaml " + r["id"]], rec=0))
            continue
        esc = r["escalate_to"] if isinstance(r["escalate_to"], list) else [r["escalate_to"]]
        opts = [(f"异常：加一级审批（{esc[0]}）", "多一个审批节点"),
                (f"特殊：加两级审批（{'、'.join(esc) if len(esc) > 1 else esc[0] + '及总经理'}）", "多两个审批节点"),
                ("不加审，只预警并事后抽查", "最快；靠预警与抽查补偿")]
        ctx = "条件：" + r["condition"] + "。" + (" 注意：" + r["note"] if r.get("note") else "")
        items.append(item(r["id"], "rules", r["name"], ctx, opts, ["APPROVAL_RULES.yaml " + r["id"]], rec=LEVEL[r["level"]]))
    items.append(item("R-Q1b", "rq1", "交付物内部校审，要不要也按「少审批」原则精简",
                      "上次你选了「按项目类别配置校审级数（例如方案两级、施工图三级）」。说明一下：这是设计成果的技术质量把关（校对、审核、审定），由技术专家和注册执业人员把关，不是费用审批，也不经过审批平台。调度请我按新原则再问一次。",
                      [("不按审批原则精简，维持按项目类别配置级数", "技术把关不受影响；校审节点照旧"), ("也按原则：正常只一级审核，按规则（如超高层、大型项目、专项幕墙）加一或两级", "更快；技术把关力度随项目降低")],
                      ["样板页 R-Q1（intent 162821）；收入线样板 PRC-28 第 3 步"], rec=0))
    groups = [{"id": "base", "title": "审批口径", "desc": "正常一人审批，由归属对象的责任人批；阈值。"},
              {"id": "rules", "title": "异常与特殊情形", "desc": "每条规则命中时加几级。推荐级别为草拟，可改。"},
              {"id": "rq1", "title": "交付物校审（重问）", "desc": "技术校审是否也按少审批原则。"}]
    info = [{"title": "这一页怎么来的", "lines": [
        "你提出权责发生制下流程要少、要快：正常单人审批，异常加一个节点，特殊加两个；审批人看谁对费用负责。",
        "三组铺开流程时，审批一律画成「一级审批 → 条件加审（最多两级）」，加审规则作为配置数据（本页确认后生效），由系统按客观条件判定，事后抽查与预警补偿。",
        "推荐项为我按常见内控做法草拟，已预选并带标签，同意就不用动；阈值请在说明里写。"]}]
    page = {"items": items, "groups": groups, "info": info, "side": []}
    tpl = (HERE / "u12.html").read_text()
    tpl = tpl.replace("<title>生效日期口径</title>", "<title>审批口径</title>").replace("<h1>员工与岗位的生效日期</h1>", "<h1>审批口径与加审规则</h1>")
    tpl = re.sub(r'<p class="lede">.*?</p>', '<p class="lede">业务口径页：推荐项已预选并带标签，同意就不用动；按组提交。</p>', tpl, count=1)
    tpl = re.sub(r"const DATA = .*?;\n", lambda m: "const DATA = " + json.dumps(page, ensure_ascii=False) + ";\n", tpl, count=1)
    (HERE / "approval.html").write_text(tpl)
    (HERE / "APPROVAL_PAGE.json").write_text(json.dumps({"sheet": "APR", **page}, ensure_ascii=False, indent=1))
    print(len(items), "items")


if __name__ == "__main__":
    main()
