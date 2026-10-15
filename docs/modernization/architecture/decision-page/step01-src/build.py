# Build the step 0+1 confirmation page (phone) from the drafts in ../step01; content is generated, never hand-copied.
import yaml, html, json, os, sys
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "togaf")
v = yaml.safe_load(open(f"{D}/vision.yaml", encoding="utf-8"))
caps = yaml.safe_load(open(f"{D}/capabilities.yaml", encoding="utf-8"))["capabilities"]
vss = yaml.safe_load(open(f"{D}/value-streams.yaml", encoding="utf-8"))["value_streams"]
e = lambda x: html.escape(str(x or ""))

def choice(qid, opts, rec):
    out = [f'<div class="opts" role="radiogroup" aria-label="{e(qid)}">']
    for val, label, cost in opts:
        out.append(f'<label class="opt{" rec" if val == rec else ""}"><input type="radio" name="{qid}" value="{val}"><b>{e(label)}</b><span class="cost">{e(cost)}</span></label>')
    out.append("</div>")
    return "".join(out)

goals = []
for g in v["goals"]:
    kp = "".join(f"<li>{e(k['name'])}{(' → ' + e('、'.join(map(str, k['metrics'])))) if k.get('metrics') else ''}</li>" for k in g.get("kpis") or [])
    goals.append(f'<details><summary>{e(g["id"][-2:])} {e(g["name"])}</summary><p>{e(g["statement"])}</p><p class="small">KPI：</p><ul>{kp}</ul></details>')
drivers = "".join(f"<li>{e(d.get('name') or d.get('statement'))}</li>" for d in v["drivers"])

capl = []
for c in caps:
    l2 = "".join(f"<li>{e(x['name'])}</li>" for x in c.get("l2") or [])
    capl.append(f'<details><summary>{e(c["name"])} <span class="n">{len(c.get("l2") or [])}</span></summary><p class="small">{e(c.get("definition"))}</p><ul>{l2}</ul></details>')

vsl = []
for s in vss:
    st = "".join(f"<li>{e(x['name'])}<span class=\"small\"> → {e(x.get('exit'))}</span></li>" for x in s.get("stages") or [])
    vsl.append(f'<details><summary>{e(s["name"])} <span class="n">{len(s.get("stages") or [])}</span></summary><p class="small">为{e(s.get("stakeholder"))}：{e(s.get("trigger"))} → {e(s.get("outcome"))}</p><ol>{st}</ol></details>')

OQ = {  # 每个待答问题的可选答复（第一个为默认推荐）
 "OQ-01": [("later", "暂不写，系统目标先行", "先把系统要达成的状态定下；增长目标以后再加。"), ("yes", "要写", "在备注写哪几项、目标水平。")],
 "OQ-02": [("later", "以后再定", "先定 KPI 名称与口径，数字在各阶段开工前定。"), ("now", "现在给", "写在备注里。")],
 "OQ-03": [("later", "以后再列", "经营分析阶段前补齐。"), ("now", "现在列", "写在备注里。")],
 "OQ-04": [("later", "以后再说", "暂不作为目标，不建对应能力。"), ("yes", "是目标", "加入目标与能力地图。"), ("no", "不是", "从规划中去掉。")],
 "OQ-05": [("yes", "要", "下线日期在统一编排时定，先立目标。"), ("no", "不要", "")],
 "OQ-06": [("yes", "要", "例如审批时限、报销处理时长。"), ("no", "不要", "")],
}
oqs = []
for q in v["open_questions"]:
    qid = q["id"]; opts = OQ.get(qid)
    if not opts: continue
    oqs.append(f'<div class="oq"><p><b>{e(qid)}</b> {e(q.get("question"))}</p>{choice(qid, opts, opts[0][0])}</div>')

REC = {"S0-VISION": "accept", "S1-CAPS": "accept", "S1-VS": "accept", **{k: o[0][0] for k, o in OQ.items()}}
tpl = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "template.html"), encoding="utf-8").read()
out = (tpl.replace("{{DRIVERS}}", drivers).replace("{{GOALS}}", "".join(goals)).replace("{{CAPS}}", "".join(capl))
          .replace("{{VS}}", "".join(vsl)).replace("{{OQS}}", "".join(oqs))
          .replace("{{C_VISION}}", choice("S0-VISION", [("accept", "认可", "目标与 KPI 名称按此登记；数字以后定。"), ("amend", "有修改", "写在备注里。")], "accept"))
          .replace("{{C_CAPS}}", choice("S1-CAPS", [("accept", "认可这套能力地图", "下一步据此重推 BC、定核心 / 支撑 / 通用。"), ("amend", "有修改", "写在备注里，例如增删或合并。")], "accept"))
          .replace("{{C_VS}}", choice("S1-VS", [("accept", "认可这些价值流", "下一步按价值流做事件风暴。"), ("amend", "有修改", "写在备注里。")], "accept"))
          .replace("/*REC*/{}", json.dumps(REC))
          .replace("{{NG}}", str(len(v["goals"]))).replace("{{NK}}", str(sum(len(g.get("kpis") or []) for g in v["goals"])))
          .replace("{{NC1}}", str(len(caps))).replace("{{NC2}}", str(sum(len(c.get("l2") or []) for c in caps)))
          .replace("{{NV}}", str(len(vss))).replace("{{NS}}", str(sum(len(s.get("stages") or []) for s in vss))))
open(sys.argv[1], "w", encoding="utf-8").write(out)
print("ok", len(out))
