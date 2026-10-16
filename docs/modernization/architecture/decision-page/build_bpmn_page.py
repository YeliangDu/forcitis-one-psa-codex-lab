"""流程设计方法批准页：方法题（不预选）＋三组 BPMN 样板（bpmn-js 只读渲染）＋设计点与待定题。

用法：python3 build_bpmn_page.py <BPMN 根目录>（其下 revenue/、people/、finance/ 放各组 .bpmn）。输出 method.html、METHOD_PAGE.json。
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from html import escape
from pathlib import Path

import build_samples as bs

HERE = Path(__file__).parent
NS = {"b": "http://www.omg.org/spec/BPMN/20100524/MODEL"}
KIND = {"userTask": "本系统操作", "serviceTask": "本系统自动或调用", "sendTask": "发往外部", "receiveTask": "外部回执", "manualTask": "线下", "callActivity": "调用子流程"}
BPMN_JS = "https://cdn.jsdelivr.net/npm/bpmn-js@18.30.1/dist/bpmn-navigated-viewer.production.min.js"


def task_table(xml):
    root = ET.fromstring(xml)
    rows = []
    for proc in root.findall("b:process", NS):
        for el in proc.iter():
            tag = el.tag.split("}")[-1]
            if tag in KIND:
                doc = el.find("b:documentation", NS)
                rows.append((KIND[tag], el.get("name", ""), (doc.text or "").strip() if doc is not None else ""))
    if not rows:
        return ""
    body = "".join(f"<tr><td>{escape(k)}</td><td>{escape(n)}</td><td>{escape(d)}</td></tr>" for k, n, d in rows)
    return f'<div class="tw"><table><thead><tr><th>在哪</th><th>任务</th><th>对应的聚合、命令与说明</th></tr></thead><tbody>{body}</tbody></table></div>'


def load(root):
    out = {}
    for gid, sub in (("rev", "revenue"), ("peo", "people"), ("fin", "finance")):
        files = []
        for p in sorted((root / sub).glob("*.bpmn"), key=lambda p: int(re.sub(r"\D", "", p.stem) or 0)):
            xml = p.read_text(encoding="utf-8")
            name = re.search(r'<(?:bpmn:)?process[^>]*name="([^"]*)"', xml)
            files.append({"title": f"{p.stem} {name.group(1) if name else ''}", "xml": xml, "table": task_table(xml)})
        out[gid] = files
    return out


def method_items():
    I = bs.item
    return [
        I("M-1", "method", "批准流程设计方法（第 2 版）", "方法稿：往上承接价值流、对象与聚合；每个流程写清步骤、角色、在哪发生、判断、审批、超时、证据，以及期间、法人、数据范围、通知、审计、在途迁入等横切项；往下交付命令、契约、规则卡、验收场景、权限、页面与迁入口径，并能双向追溯。批准后先写「骨架生成流程图」的工具，再按目录铺开。",
          [("批准", "按方法铺开，约 7–9 个工作日（含评审）"), ("按我的意见改后再批（在说明里写）", "改稿后再出页")], ["proposals/closeout/PROCESS_METHOD_DRAFT.md（第 2 版）；opus 评审 PROCESS_METHOD_REVIEW.md"]),
        I("M-2", "method", "流程只在唯一目录登记，流程图由骨架自动生成", "你问「流程总数也不对……还是现在流程还没有目录」。现在目录共 146 条：业务流程 91（原 82＋新增 9）、事件策略 22、单聚合维护 19、读模型 4、已合并 10。之前的 85 无出处，93 是另一口径。同一流程的步骤只写在骨架里，图由工具生成，免得三处手写对不上。这会改你批准的 ADR-0050 中「流程登记由可执行流程文件生成」一句。",
          [("同意：目录唯一来源，图由骨架生成", "要先写生成工具 1–1.5 天"), ("不同意：图手画（在说明里写理由）", "三处手写，易漂移")], ["ADR-0050 决定 20②、J8、决定 16；方法稿第 2、4、9 节"]),
        I("M-3", "method", "全部业务流程画描述级 BPMN 图；可执行版推迟到实现时出", "BPMN 采用 OMG 现行正式版 2.0.2（2014 年发布，至今无新版；ISO/IEC 19510:2013 对应的是 2.0.1）。描述级图给人看、推导下游；Flowable 可执行版是实现物，现在出会越过当前阶段。这会改 ADR-0050「只有人工编排流程画 BPMN」。",
          [("同意：91 个业务流程画描述级图，30 个人工编排流程的可执行版随实现出", "收尾期工作量最小"), ("现在就出可执行版", "多 30 张图并要保持两份一致；越过当前阶段")], ["方法稿第 4、7、9 节；调研第 1、7 节"]),
        I("M-4", "method", "每一步标明「在哪发生」，分四类", "本系统操作；本系统自动；外部系统经集成完成（企业微信、U8C、银行、税务）；线下完成、本系统只登记结果与证据。外部和线下的步骤写明登记什么证据——它们是诉讼取证和历史迁入的关键。",
          [("认可这四类", "写进统一约定"), ("要改分法（在说明里写）", "按你的说明改")], ["你问「流程节点哪些是在本系统，设计方位是如何考虑的」"]),
        I("M-5", "method", "验收场景怎么从流程推出", "",
          [("每个判断出口、每个超时、每个结局至少一条场景，另选 1–3 条关键全程路径；跨模块的每段只验本模块", "可执行、不爆炸；与现行测试规范一致"), ("每条路径都写一条场景", "网关多时数量按乘积增长；跨模块场景与测试规范冲突")], ["方法稿第 6 节；评审 M3"]),
        I("M-6", "method", "新增三项参照写进架构框架", "Silver《BPMN Method and Style》的画法规则（结局即结束状态、网关出口写结局词、任务标类型）；事件风暴的四个词（策略、读模型、外部系统、热点）；由路径推导验收示例。原书正文未取到，规则为转载与本项目归纳。BIZBOK 仍按已定的 v11，不切换到 v15。",
          [("三项都写入", "改 FRAMEWORKS，随过渡 ADR"), ("只写入部分（在说明里写）", "按你的说明")], ["方法稿第 8 节；调研第 2–5 节；FRAMEWORKS 防漂移规则 2"]),
        I("M-7", "method", "铺开后的流程，你怎么看", "",
          [("每个价值流一页，只看业务判断与待定题", "每页十几题；图可点开看"), ("每个流程图都看", "91 张，确认时间长")], ["你要求先看样板（intent 153009）；评审 M7"]),
    ]


def main(root):
    bpmn = load(Path(root))
    items = method_items()
    groups = [{"id": "method", "title": "流程设计方法（请批）", "desc": "先定方法，再看下面三组样板是否符合你的想法。"}]
    for g in bs.G:
        if g["id"] == "conv":
            continue
        groups.append({"id": g["id"], "title": g["title"], "desc": g["desc"] + " 图可拖动、缩放；下表列出每个任务对应的聚合与命令。"})
    items += [i for i in bs.ITEMS if i["grp"] != "conv"]
    info = [{"title": "这一页怎么看", "lines": [
        "第一页是方法要你定的 7 题，都不预选。后面三页是三组样板，用标准 BPMN 2.0.2 画，bpmn-js 渲染：每个角色一条泳道，客户、税务平台、企业微信等外部方是单独的泳池，用消息流连接；用户任务＝本系统操作，服务任务＝本系统自动，手工任务＝线下。",
        "每张图下有表，列出每个任务对应的聚合、命令和所在模块，回答「流程跟聚合、业务对象对应过吗」。三组初稿的对应检查：聚合 100% 查得到，命令 98.7%（缺口铺开时补）。",
        "样板里各设计点与待定题和上一版相同，可逐条写意见。"]}]
    data = {"items": items, "groups": groups, "info": info, "side": []}
    tpl = (HERE / "u12.html").read_text()
    tpl = tpl.replace("<title>生效日期口径</title>", "<title>流程设计方法</title>").replace("<h1>员工与岗位的生效日期</h1>", "<h1>流程设计方法与 BPMN 样板</h1>")
    tpl = re.sub(r'<p class="lede">.*?</p>', '<p class="lede">方法题不预选；样板逐处给意见；按组提交。</p>', tpl, count=1)
    tpl = tpl.replace("</style>", ".bpmnc{height:460px;border:1px solid var(--line);border-radius:8px;background:#fff;margin:6px 0}.legend2{font-size:12.5px;color:var(--muted);margin:10px 0 0}\n</style>", 1)
    tpl = tpl.replace("<script>\n(() => {", f'<script src="{BPMN_JS}"></script>\n<script>\n(() => {{', 1)
    tpl = re.sub(r"const DATA = .*?;\n", lambda m: "const DATA = " + json.dumps(data, ensure_ascii=False) + ";\nconst BPMN = " + json.dumps(bpmn, ensure_ascii=False) + ";\n" + MOUNT, tpl, count=1)
    anchor = "<p>${esc(g.desc)}</p></div></section>"
    assert anchor in tpl
    tpl = tpl.replace(anchor, "<p>${esc(g.desc)}</p></div><div id=\"diaghost\" data-g=\"${g.id}\"></div></section>", 1)
    assert "\n  stats();\n}" in tpl
    tpl = tpl.replace("\n  stats();\n}", "\n  mountBpmn();\n  stats();\n}", 1)
    (HERE / "method.html").write_text(tpl)
    (HERE / "METHOD_PAGE.json").write_text(json.dumps({"sheet": "MTH", **data}, ensure_ascii=False, indent=1))
    print(len(items), "items;", {k: len(v) for k, v in bpmn.items()}, "bpmn files")


MOUNT = r"""const bpmnCache = {};
function mountBpmn(){
  const host = document.getElementById("diaghost"); if (!host) return;
  const g = host.dataset.g, files = BPMN[g] || [];
  if (!files.length) return;
  if (!bpmnCache[g]){
    const box = document.createElement("div"); bpmnCache[g] = box; host.appendChild(box);
    for (const f of files){
      const h = document.createElement("p"); h.className = "legend2"; h.textContent = f.title;
      const c = document.createElement("div"); c.className = "bpmnc";
      const t = document.createElement("div"); t.innerHTML = f.table;
      box.append(h, c, t);
      if (!window.BpmnJS){ c.textContent = "流程图组件未加载（网络受限时可只看下表）"; continue; }
      const v = new window.BpmnJS({container: c});
      v.importXML(f.xml).then(() => { try { v.get("canvas").zoom("fit-viewport"); } catch(e){} }).catch(e => { c.textContent = "流程图加载失败：" + e.message; });
    }
  } else host.appendChild(bpmnCache[g]);
}
"""

if __name__ == "__main__":
    main(sys.argv[1])
