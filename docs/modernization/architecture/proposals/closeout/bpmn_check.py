#!/usr/bin/env python3
"""BPMN 检查（BPMN_STYLE.md 第 5 节）：python3 -I bpmn_check.py <文件或目录>... | --self-test"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI"
FLOWABLE_HINT = "flowable.org"
CATALOG = Path(__file__).resolve().parent / "process-catalog.yaml"

FORBIDDEN = {
    "task": "通用任务 task（必须标类型）",
    "complexGateway": "复杂网关",
    "escalationEventDefinition": "升级事件",
    "conditionalEventDefinition": "条件事件",
    "linkEventDefinition": "链接事件",
    "choreography": "编排图",
    "choreographyTask": "编排图",
    "subChoreography": "编排图",
    "conversation": "会话图",
    "callConversation": "会话图",
    "subConversation": "会话图",
    "conversationNode": "会话图",
}
FLOW_NODES = {
    "userTask", "serviceTask", "sendTask", "receiveTask", "manualTask", "scriptTask",
    "businessRuleTask", "callActivity", "subProcess", "transaction", "adHocSubProcess",
    "startEvent", "endEvent", "intermediateCatchEvent", "intermediateThrowEvent",
    "boundaryEvent", "exclusiveGateway", "parallelGateway", "inclusiveGateway",
    "eventBasedGateway", "complexGateway", "task", "dataStoreReference",
    "dataObjectReference",
}
DI_REQUIRED = FLOW_NODES | {"sequenceFlow", "messageFlow", "participant", "lane"}

# Flowable 白名单（PROCESS_METHOD_RESEARCH.md 第 7 节表）
FW_STRUCT = {
    "definitions", "collaboration", "process", "documentation", "extensionElements", "laneSet", "lane",
    "flowNodeRef", "sequenceFlow", "conditionExpression", "incoming", "outgoing",
    "userTask", "scriptTask", "serviceTask", "sendTask", "receiveTask", "manualTask",
    "businessRuleTask", "callActivity", "subProcess", "transaction",
    "startEvent", "endEvent", "intermediateCatchEvent", "intermediateThrowEvent",
    "boundaryEvent", "exclusiveGateway", "parallelGateway", "inclusiveGateway",
    "eventBasedGateway", "dataObject", "script", "message", "signal", "error",
    "itemDefinition", "interface", "operation", "messageEventDefinition",
    "timerEventDefinition", "signalEventDefinition", "errorEventDefinition",
    "terminateEventDefinition", "cancelEventDefinition", "compensateEventDefinition",
    "timeDate", "timeDuration", "timeCycle", "multiInstanceLoopCharacteristics",
    "loopCardinality", "completionCondition", "textAnnotation", "text", "association",
    "potentialOwner", "resourceAssignmentExpression", "formalExpression", "resource",
    "humanPerformer", "resourceRef", "ioSpecification", "dataInput", "dataOutput",
    "dataInputAssociation", "dataOutputAssociation", "inputSet", "outputSet",
    "property", "standardLoopCharacteristics", "loopDataInputRef", "inputDataItem",
    "sourceRef", "targetRef", "assignment", "from", "to", "dataInputRefs",
    "dataOutputRefs", "inputDataRef", "outputDataRef", "terminate", "rootElement",
}
FW_DEFS = {
    "startEvent": {"messageEventDefinition", "timerEventDefinition",
                   "signalEventDefinition", "errorEventDefinition"},
    "intermediateCatchEvent": {"timerEventDefinition", "signalEventDefinition",
                               "messageEventDefinition"},
    "intermediateThrowEvent": {"signalEventDefinition", "compensateEventDefinition"},
    "endEvent": {"errorEventDefinition", "terminateEventDefinition",
                 "cancelEventDefinition"},
    "boundaryEvent": {"timerEventDefinition", "errorEventDefinition",
                      "signalEventDefinition", "messageEventDefinition",
                      "cancelEventDefinition", "compensateEventDefinition"},
}
FW_DISCOURAGED = {
    "businessRuleTask": "可执行版不用业务规则任务（Drools）",
    "scriptTask": "可执行版不用脚本任务做业务计算",
}
FW_NOT_RUNTIME = {
    "participant": "可执行版只留本系统一个参与者，外部方改消息事件或服务任务",
    "messageFlow": "泳池间消息流的运行语义文档未列出",
    "dataStoreReference": "可执行版数据对象仅作流程变量，不连数据存储",
    "dataObjectReference": "可执行版数据对象仅作流程变量声明",
}


def local(tag):
    return tag.rsplit("}", 1)[-1] if isinstance(tag, str) else ""


def ns(tag):
    return tag[1:].split("}", 1)[0] if isinstance(tag, str) and tag.startswith("{") else ""


def load_catalog():
    data = yaml.safe_load(CATALOG.read_text(encoding="utf-8"))
    return {p["id"]: p["name"] for p in data["processes"]}


def children(el, name):
    return [c for c in el if ns(c.tag) == BPMN and local(c.tag) == name]


def check_executable(root):
    errors, warnings = [], []
    parents_participants = 0
    for el in root.iter():
        n, t = ns(el.tag), local(el.tag)
        if not t or n in (BPMNDI,) or "omg.org/spec/DD" in n:
            continue
        if n != BPMN:
            if FLOWABLE_HINT in n or "w3.org" in n:
                continue
            errors.append(f"[X01] 非白名单命名空间元素 {{{n}}}{t}")
            continue
        if t in FW_NOT_RUNTIME:
            if t == "participant":
                parents_participants += 1
                continue
            code = "X03" if t == "messageFlow" else "X04"
            errors.append(f"[{code}] Flowable 白名单越界：{t}（{FW_NOT_RUNTIME[t]}）")
            continue
        if t in FW_DISCOURAGED:
            errors.append(f"[X05] {t}：{FW_DISCOURAGED[t]}")
            continue
        if t.endswith("EventDefinition"):
            continue
        if t not in FW_STRUCT:
            errors.append(f"[X01] Flowable 白名单越界：{t}")
    if parents_participants > 1:
        errors.append(f"[X02] Flowable 白名单越界：参与者 {parents_participants} 个（只留 1 个）")
    for ev, allowed in FW_DEFS.items():
        for el in root.iter(f"{{{BPMN}}}{ev}"):
            for d in el:
                dn = local(d.tag)
                if dn.endswith("EventDefinition") and dn not in allowed:
                    errors.append(f"[X01] Flowable 白名单越界：{ev} {el.get('id')} 含 {dn}")
    return errors, warnings


def check_descriptive(root, catalog):
    errors, warnings = [], []
    for el in root.iter():
        t = local(el.tag)
        if FLOWABLE_HINT in ns(el.tag) or any(FLOWABLE_HINT in ns(a) for a in el.attrib):
            errors.append(f"[R12] 描述级文件不得含 flowable 命名空间（{t} {el.get('id')}）")
            break
    for el in root.iter():
        if ns(el.tag) == BPMN and local(el.tag) in FORBIDDEN:
            errors.append(f"[R05] 不得使用 {local(el.tag)}：{FORBIDDEN[local(el.tag)]}"
                          f"（{el.get('id') or el.get('name') or ''}）")

    node_proc, node_el, flows = {}, {}, []
    procs = children(root, "process")
    if not procs:
        errors.append("[R10] 没有 process")
    for p in procs:
        pid = p.get("id") or ""
        if not re.fullmatch(r"PRC-\d{2,}", pid):
            errors.append(f"[R10] process id「{pid}」不是 PRC-NN 形式")
        elif pid not in catalog:
            errors.append(f"[R10] process {pid} 不在 process-catalog.yaml")
        elif (p.get("name") or "") != catalog[pid]:
            errors.append(f"[R11] process {pid} 名称「{p.get('name')}」与目录「{catalog[pid]}」不一致")
        starts = children(p, "startEvent")
        ends = children(p, "endEvent")
        if not starts:
            errors.append(f"[R01] process {pid} 没有开始事件")
        for e in starts:
            if not (e.get("name") or "").strip():
                errors.append(f"[R01] 开始事件 {e.get('id')} 没有 name")
        if not ends:
            errors.append(f"[R02] process {pid} 没有结束事件")
        for e in ends:
            if not (e.get("name") or "").strip():
                errors.append(f"[R03] 结束事件 {e.get('id')} 没有 name")
        for el in p.iter():
            if ns(el.tag) != BPMN:
                continue
            if el.get("id") and local(el.tag) in FLOW_NODES | {"lane"}:
                node_proc[el.get("id")] = pid
                node_el[el.get("id")] = el
            if local(el.tag) == "sequenceFlow":
                flows.append((el, pid))

    for gw in root.iter(f"{{{BPMN}}}exclusiveGateway"):
        outs = [f for f, _ in flows if f.get("sourceRef") == gw.get("id")]
        if len(outs) >= 2:
            for f in outs:
                if not (f.get("name") or "").strip():
                    errors.append(f"[R04] 排他网关 {gw.get('id')} 的出口顺序流 {f.get('id')} 没有 name")

    for f, pid in flows:
        s, t = f.get("sourceRef"), f.get("targetRef")
        for ref in (s, t):
            if ref not in node_proc:
                errors.append(f"[R07] 顺序流 {f.get('id')} 引用不存在的节点 {ref}")
        if s in node_proc and t in node_proc and node_proc[s] != node_proc[t]:
            errors.append(f"[R07] 顺序流 {f.get('id')} 跨泳池（{node_proc[s]} -> {node_proc[t]}）")

    part_of = {}
    for c in children(root, "collaboration"):
        for pt in children(c, "participant"):
            part_of[pt.get("id")] = pt.get("id")
            if pt.get("processRef"):
                part_of.setdefault("proc:" + pt.get("processRef"), pt.get("id"))
        for mf in children(c, "messageFlow"):
            ends = []
            for ref in (mf.get("sourceRef"), mf.get("targetRef")):
                if ref in part_of:
                    ends.append(part_of[ref])
                elif ref in node_proc:
                    ends.append(part_of.get("proc:" + node_proc[ref], "proc:" + node_proc[ref]))
                else:
                    errors.append(f"[R06] 消息流 {mf.get('id')} 引用不存在的 {ref}")
                    ends.append(None)
            if None not in ends and ends[0] == ends[1]:
                errors.append(f"[R06] 消息流 {mf.get('id')} 的两端属于同一参与者")

    diagrams = list(root.iter(f"{{{BPMNDI}}}BPMNDiagram"))
    if not diagrams:
        errors.append("[R08] 文件不含 BPMNDiagram")
    else:
        drawn = {e.get("bpmnElement") for e in root.iter()
                 if ns(e.tag) == BPMNDI and local(e.tag) in ("BPMNShape", "BPMNEdge")}
        for el in root.iter():
            if ns(el.tag) == BPMN and local(el.tag) in DI_REQUIRED and el.get("id"):
                if el.get("id") not in drawn:
                    errors.append(f"[R09] {local(el.tag)} {el.get('id')} 没有 BPMNShape/BPMNEdge")
    return errors, warnings


def check_file(path, catalog):
    errors, warnings = [], []
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as ex:
        return [f"XML 解析失败：{ex}"], []
    return check_root(root, str(path).endswith(".bpmn20.xml"), catalog)


def check_root(root, executable, catalog):
    if local(root.tag) != "definitions" or ns(root.tag) != BPMN:
        return ["根元素不是 BPMN 2.0 definitions"], []
    return check_executable(root) if executable else check_descriptive(root, catalog)


def collect(args):
    files = []
    for a in args:
        p = Path(a)
        if p.is_dir():
            files += sorted(x for x in p.rglob("*") if x.is_file()
                            and (x.name.endswith(".bpmn20.xml") or x.suffix == ".bpmn"))
        elif p.is_file():
            files.append(p)
        else:
            print(f"路径不存在：{a}", file=sys.stderr)
            files.append(None)
    return files


GOOD = """<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
 xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
 xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
 xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="d">
 <collaboration id="c">
  <participant id="P1" name="FORCITIS one" processRef="PRC-01"/>
  <participant id="P2" name="客户"/>
  <messageFlow id="M1" name="邀请函" sourceRef="T1" targetRef="P2"/>
 </collaboration>
 <process id="PRC-01" name="举办市场活动">
  <startEvent id="S" name="计划确定"/>
  <userTask id="T1" name="发出邀请"/>
  <exclusiveGateway id="G" name="已确认？"/>
  <endEvent id="E1" name="已举办"/>
  <endEvent id="E2" name="已取消"/>
  <sequenceFlow id="F1" sourceRef="S" targetRef="T1"/>
  <sequenceFlow id="F2" sourceRef="T1" targetRef="G"/>
  <sequenceFlow id="F3" name="是" sourceRef="G" targetRef="E1"/>
  <sequenceFlow id="F4" name="否" sourceRef="G" targetRef="E2"/>
 </process>
 <bpmndi:BPMNDiagram id="DG"><bpmndi:BPMNPlane id="PL" bpmnElement="c">
  <bpmndi:BPMNShape id="x1" bpmnElement="P1"/><bpmndi:BPMNShape id="x2" bpmnElement="P2"/>
  <bpmndi:BPMNShape id="x3" bpmnElement="S"/><bpmndi:BPMNShape id="x4" bpmnElement="T1"/>
  <bpmndi:BPMNShape id="x5" bpmnElement="G"/><bpmndi:BPMNShape id="x6" bpmnElement="E1"/>
  <bpmndi:BPMNShape id="x7" bpmnElement="E2"/>
  <bpmndi:BPMNEdge id="e1" bpmnElement="F1"/><bpmndi:BPMNEdge id="e2" bpmnElement="F2"/>
  <bpmndi:BPMNEdge id="e3" bpmnElement="F3"/><bpmndi:BPMNEdge id="e4" bpmnElement="F4"/>
  <bpmndi:BPMNEdge id="e5" bpmnElement="M1"/>
 </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>"""

BAD = """<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
 xmlns:fl="http://flowable.org/bpmn" id="d">
 <collaboration id="c">
  <participant id="P1" processRef="PRC-01"/><participant id="P2" processRef="PRC-99"/>
  <messageFlow id="M1" sourceRef="T1" targetRef="T2"/>
  <messageFlow id="M2" sourceRef="T1" targetRef="G"/>
  <conversation id="CV"/>
 </collaboration>
 <choreography id="CH"/>
 <process id="PRC-01" name="错误的名称" fl:x="1">
  <startEvent id="S1"/><startEvent id="S2"/>
  <task id="T1"/>
  <exclusiveGateway id="G"/>
  <complexGateway id="CG"/>
  <intermediateThrowEvent id="I1"><escalationEventDefinition/></intermediateThrowEvent>
  <intermediateCatchEvent id="I2"><conditionalEventDefinition/></intermediateCatchEvent>
  <intermediateThrowEvent id="I3"><linkEventDefinition/></intermediateThrowEvent>
  <endEvent id="E"/>
  <sequenceFlow id="F1" sourceRef="T1" targetRef="G"/>
  <sequenceFlow id="F2" sourceRef="G" targetRef="E"/>
  <sequenceFlow id="F3" sourceRef="G" targetRef="T2"/>
  <sequenceFlow id="F4" sourceRef="T1" targetRef="T2"/>
 </process>
 <process id="PRC-99" name="x"><userTask id="T2"/></process>
</definitions>"""

EXEC_BAD = """<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
 <collaboration id="c">
  <participant id="P1" processRef="PRC-01"/><participant id="P2"/>
  <messageFlow id="M" sourceRef="P1" targetRef="P2"/>
 </collaboration>
 <process id="PRC-01"><startEvent id="s"/><complexGateway id="g"/>
  <businessRuleTask id="b"/><scriptTask id="sc"/><dataStoreReference id="d"/>
  <endEvent id="e"><escalationEventDefinition/></endEvent>
 </process></definitions>"""


TWO_STARTS = GOOD.replace(
    '<userTask id="T1"',
    '<startEvent id="S3" name="另一触发"/>\n  <userTask id="T1"',
).replace(
    '<sequenceFlow id="F1"',
    '<sequenceFlow id="F5" sourceRef="S3" targetRef="T1"/>\n  <sequenceFlow id="F1"',
).replace(
    '<bpmndi:BPMNEdge id="e1"',
    '<bpmndi:BPMNShape id="x8" bpmnElement="S3"/><bpmndi:BPMNEdge id="e6" bpmnElement="F5"/>'
    '<bpmndi:BPMNEdge id="e1"',
)


def codes(msgs):
    out = []
    for m in msgs:
        mt = re.match(r"\[([RX]\d\d)\]", m)
        out.append(mt.group(1) if mt else "??")
    return sorted(out)


def self_test():
    catalog = {"PRC-01": "举办市场活动"}
    cases = [
        ("合规", GOOD, False, []),
        ("R09 缺 BPMNShape", GOOD.replace('<bpmndi:BPMNShape id="x5" bpmnElement="G"/>', ""), False, ["R09"]),
        ("违规（描述级）", BAD, False,
         ["R01"] * 3 + ["R02"] + ["R03"] + ["R04"] * 2 + ["R05"] * 7 + ["R06"]
         + ["R07"] * 2 + ["R08"] + ["R10"] + ["R11"] + ["R12"]),
        ("两个已命名开始事件", TWO_STARTS, False, []),
        ("两个开始事件其一无 name", TWO_STARTS.replace(' name="另一触发"', ""), False, ["R01"]),
        ("违规（可执行）", EXEC_BAD, True, ["X01"] * 2 + ["X02", "X03", "X04", "X05", "X05"]),
    ]
    ok = True
    for name, xml, executable, expect in cases:
        errors, warnings = check_root(ET.fromstring(xml), executable, catalog)
        got = codes(errors)
        good = got == sorted(expect)
        ok &= good
        print(f"{name}: {'通过' if good else '失败'} 期望 {sorted(expect)} 实得 {got}")
        if not good:
            for m in errors:
                print("   ", m)
    _, w = check_root(ET.fromstring(EXEC_BAD), True, catalog)
    ok &= not w
    print(f"可执行无警告: {'通过' if not w else '失败'}")
    print("自测", "通过" if ok else "失败")
    return 0 if ok else 1


def main(argv):
    if "--self-test" in argv:
        return self_test()
    if not argv:
        print(__doc__)
        return 2
    catalog = load_catalog()
    bad = False
    for f in collect(argv):
        if f is None:
            bad = True
            continue
        errors, warnings = check_file(f, catalog)
        print(f"{f}: 错误 {len(errors)}，警告 {len(warnings)}")
        for m in errors:
            print(f"  错误：{m}")
        for m in warnings:
            print(f"  警告：{m}")
        bad |= bool(errors)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
