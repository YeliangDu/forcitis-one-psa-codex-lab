#!/usr/bin/env python3
"""流程骨架 yaml -> 描述级 BPMN 2.0.2 协作图（含 BPMNDI）。

用法：python3 -I bpmn_gen.py <processes.<组>.yaml> --out <目录> [--only PRC-NN]
     python3 -I bpmn_gen.py --self-test
画法见 BPMN_STYLE.md，字段见 CONVENTIONS.md；输出后自动调用 bpmn_check.py 校验。
"""
import argparse
import importlib.util
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

import yaml

HERE = Path(__file__).resolve().parent
CATALOG = Path(__file__).resolve().parents[1].parent / "ssot" / "09-processes" / "catalog.yaml"   # 流程目录正本
STAKEHOLDERS = HERE.parents[1] / "ssot" / "05-value-streams" / "value-streams.yaml"
CHECKER = HERE / "bpmn_check.py"

LANE_H, HDR, BLACK_H = 130, 30, 80
TASK_W, TASK_H, SLOT_T = 100, 60, 170
EVT, GW, SLOT_E = 36, 50, 90
STORE_W, STORE_H, OBJ_W, OBJ_H, ANN_W, ANN_H = 36, 26, 26, 32, 86, 30
SYSTEM = "system"
TASK_KINDS = {"userTask", "serviceTask", "sendTask", "manualTask"}


def clip(text, n):
    text = str(text)
    return text if len(text) <= n else text[:n] + "…"


def head(text):
    return re.split(r"[；;。（(：:]", str(text))[0].strip()


def load_stakeholders():
    data = yaml.safe_load(STAKEHOLDERS.read_text(encoding="utf-8"))
    return {s["id"]: s for s in data.get("stakeholders", [])}


def role_name(sid, stake):
    s = stake.get(sid)
    if not s:
        return sid
    sub = s.get("sub")
    if sub and sub != "—":
        return sub
    return s.get("category") or clip(s.get("members") or sid, 12)


def pool_role_name(sid, stake):
    s = stake.get(sid)
    if not s:
        return sid
    parts = [x for x in (s.get("category"), s.get("sub")) if x and x != "—"]
    return "·".join(parts) or clip(s.get("members") or sid, 12)


class Node:
    def __init__(self, nid, kind, name, lane, **kw):
        self.id, self.kind, self.name, self.lane = nid, kind, name, lane
        self.doc = kw.get("doc", "")
        self.defn = kw.get("defn")
        self.attached = kw.get("attached")
        self.cancel = kw.get("cancel", True)
        self.duration = kw.get("duration")
        self.w, self.h, self.slot = self.size(kind)
        self.cx = self.cy = 0.0
        self.reads, self.writes, self.objs = [], [], []

    @staticmethod
    def size(kind):
        if kind in TASK_KINDS:
            return TASK_W, TASK_H, SLOT_T
        if kind == "exclusiveGateway":
            return GW, GW, SLOT_E
        return EVT, EVT, SLOT_E

    def box(self):
        return (self.cx - self.w / 2, self.cy - self.h / 2, self.cx + self.w / 2, self.cy + self.h / 2)


class Process:
    def __init__(self, p, name, stake, timeout_actions=None):
        self.p, self.name, self.stake = p, name, stake
        self.pid = p["id"]
        self.warns = []
        self.nodes = {}
        self.order = []
        self.flows = []
        self.msgs = []
        self.anns = []
        self.signals = {}
        self.stores = []
        self.counter = 0
        self.ends = {}
        self.pools = {}
        self.missing = {"where": 0, "permission/page": 0, "command": 0}
        self.timeout_actions = timeout_actions or []
        self.used_timeout_actions = set()

    def warn(self, text):
        if text not in self.warns:
            self.warns.append(text)

    def nid(self, prefix):
        self.counter += 1
        return f"{prefix}_{self.counter}"

    def add(self, kind, name, lane, prefix=None, place=True, **kw):
        n = Node(self.nid(prefix or kind), kind, name, lane, **kw)
        self.nodes[n.id] = n
        if place:
            self.order.append(n.id)
        return n

    def flow(self, src, tgt, name=""):
        self.flows.append({"id": f"Flow_{len(self.flows) + 1}", "src": src, "tgt": tgt, "name": name or ""})

    def signal(self, name):
        if name not in self.signals:
            self.signals[name] = f"Signal_{len(self.signals) + 1}"
        return self.signals[name]

    def pool(self, name):
        self.pools.setdefault(name, f"Pool_{len(self.pools) + 1}")
        return name

    def annotate(self, node, text):
        self.anns.append({"id": f"Ann_{len(self.anns) + 1}", "text": text, "node": node.id})

    def lane_of(self, actor):
        return SYSTEM if actor in (None, "", "system", "platform") else actor

    def triggers(self):
        p = self.p
        if p.get("triggers"):
            return list(p["triggers"])
        t = p.get("trigger") or {}
        self.warn("缺 triggers，按旧字段 trigger 推出")
        kind = t.get("type") or "human"
        if kind == "event":
            events = t.get("events") or ([{"event": t["event"]}] if t.get("event") else [])
            if events:
                return [{"type": "event", "name": e.get("event"), "aggregate": e.get("aggregate")} for e in events]
            return [{"type": "event", "name": t.get("desc")}]
        return [{"type": kind, "name": t.get("desc")}]

    def outcome_for(self, step, when, goto):
        decs_ = step.get("decision") or []
        for dd in decs_:
            if dd.get("when") == when and dd.get("outcome"):
                return dd["outcome"]
        if when is None and len(decs_) == 1 and decs_[0].get("outcome"):
            return decs_[0]["outcome"]
        if step.get("outcome"):
            return step["outcome"]
        outcomes = self.p.get("outcomes") or []
        if outcomes:
            for o in outcomes:
                if when and o in str(when):
                    return o
            if len(outcomes) == 1:
                return outcomes[0]
            self.warn("outcomes 有多个而步骤未写 outcome，结束事件名按位置猜测")
            return outcomes[0] if when in (None, "otherwise") else outcomes[-1]
        self.warn("缺 outcomes，结束事件名用缺省「已完成（顺序走完）/ 已结束（otherwise）/ 已终止（条件）」")
        return "已完成" if when is None else "已结束" if when == "otherwise" else "已终止"

    def add_exits(self, step, outs, n, info):
        """驳回与撤回出口。已由 decision 画出的不重复：出口目标相同，或 decision 的条件含「驳回/撤回」且目标相同。"""
        decs_ = step.get("decision") or []

        def end_name(when):
            for dd in decs_:
                if dd.get("when") == when and dd.get("outcome"):
                    return dd["outcome"]
            return None

        def drawn(tgt, word, name):
            for when, t in outs:
                if t != tgt:
                    continue
                if tgt != "end":
                    return True
                if word in str(when or "") or end_name(when) == name:
                    return True
            return False

        extra = []
        ap = step.get("approval") or {}
        if ap and ap.get("on_reject") is not None:
            r = ap["on_reject"]
            tgt = self.norm_goto(r, n) if isinstance(r, int) or str(r).strip().isdigit() else "end"
            name = ap.get("on_reject_outcome") or "已驳回"
            if not drawn(tgt, "驳回", name):
                extra.append(("驳回", tgt, name))
        w = step.get("withdraw")
        if w and w.get("to") is not None:
            tgt = self.norm_goto(w["to"], n)
            name = w.get("outcome") or "已撤回"
            if not drawn(tgt, "撤回", name):
                extra.append(("撤回", tgt, name))
        if not extra:
            return outs
        base = list(outs)
        if len(base) == 1 and base[0][0] is None:
            base = [("otherwise", base[0][1])]
            if ap:
                info["labels"] = {"otherwise": "通过"}
        for label, tgt, name in extra:
            base.append((label, tgt))
            info["exit_names"][label] = name
        return base

    def norm_goto(self, g, n):
        if isinstance(g, int) and 1 <= g <= n:
            return g
        if isinstance(g, str):
            g = g.strip()
            if g.isdigit() and 1 <= int(g) <= n:
                return int(g)
            if g == "end" or re.fullmatch(r"PRC-\d+", g):
                return g
        self.warn(f"goto「{g}」无法识别，按 end 处理")
        return "end"

    # ---- 第一遍：建节点 ----
    def build(self):
        steps = self.p.get("steps") or []
        n = len(steps)
        self.steps = steps
        self.plan = []
        trigs = self.triggers()
        self.starts = []
        self.entries = []
        for index, t in enumerate(trigs, 1):
            e = t.get("entry", 1)
            if isinstance(e, str) and re.fullmatch(r"[0-9]+", e.strip()):
                e = int(e)
            if not (isinstance(e, int) and not isinstance(e, bool) and 1 <= e <= n):
                raise ValueError(f"{self.pid}: triggers[{index}].entry「{e}」须为 1–{n} 的步骤序号")
            self.entries.append(e)
        entry_set = set(self.entries)
        for t, e in zip(trigs, self.entries):
            first_lane = self.lane_of(steps[e - 1].get("actor")) if steps else SYSTEM
            kind = t.get("type") or "human"
            name = t.get("name") or {"human": "人工发起", "time": "到期", "event": "事件发生", "call": "被调用"}.get(kind, "开始")
            if not t.get("name"):
                self.warn("触发缺 name，用缺省名")
            defn = {"event": "signal", "time": "timer"}.get(kind)
            if kind not in ("human", "event", "time", "call"):
                self.warn(f"触发类型「{kind}」未知，画空开始事件")
            doc = f"来自 {t['aggregate']}" if t.get("aggregate") else ""
            s = self.add("startEvent", name, first_lane, prefix="Start", defn=defn, doc=doc)
            if defn == "signal":
                self.signal(name)
            if kind == "call":
                self.annotate(s, "由其他流程调用")
            self.starts.append(s)

        bc_default = self.p.get("owner_bc")
        for i, s in enumerate(steps, 1):
            actor = s.get("actor")
            lane = self.lane_of(actor)
            where = s.get("where")
            if not where:
                self.missing["where"] += 1
                where = "automatic" if lane == SYSTEM else "in_system"
            cmd = s.get("command")
            if not cmd:
                self.missing["command"] += 1
                if s.get("read") or s.get("reads"):
                    cmd = head(s.get("desc") or "") or "读取 " + "、".join(s.get("reads") or ["数据"])
                elif s.get("on_time"):
                    cmd = "处理：" + s["on_time"]
                else:
                    cmd = head(s.get("desc") or "") or f"办理第 {i} 步"
            cb = s.get("cross_bc") or []
            apis = [c for c in cb if c.get("mode") == "api" and c.get("direction") == "out"]
            evs = []
            for c in cb:
                if c.get("mode") == "event" and c.get("direction") == "out":
                    for e in evs:
                        if e[0] == c.get("name"):
                            e[1].append(c.get("bc"))
                            break
                    else:
                        evs.append([c.get("name"), [c.get("bc")]])
            ext = where.split(":", 1)[1] if where.startswith("external:") else None
            kind = "sendTask" if ext else "serviceTask" if where == "automatic" else "manualTask" if where == "offline" else "userTask"
            if kind in ("userTask",) and not (s.get("permission") and s.get("page")):
                self.missing["permission/page"] += 1
            bc = s.get("bc") or bc_default
            agg = s.get("aggregate")
            parts = [f"{agg} {cmd}" if agg else cmd, bc, where, s.get("permission"), s.get("page")]
            if s.get("approval"):
                ap = s["approval"]
                parts.append(("审批平台：" if ap.get("engine") == "platform.approval" else f"审批（{ap.get('engine')}）：") + str(ap.get("doc_type")))
            if s.get("calls"):
                parts.append(f"调用 {s['calls']}")
            doc = "；".join(str(x) for x in parts if x)
            chain = []
            info = {"i": i, "lane": lane, "chain": chain, "exit_names": {}, "labels": {}}
            if s.get("on_time"):
                chain.append(self.add("intermediateCatchEvent", s["on_time"], lane, prefix=f"S{i}_time", defn="timer").id)
            service_like = kind in ("serviceTask", "sendTask")
            name = cmd
            if service_like and apis:
                name += " → " + "、".join(str(a.get("bc")) for a in apis)
            if kind == "manualTask":
                base = re.sub(r"^登记", "", cmd) or cmd
                main = self.add("manualTask", "线下办理" + base, lane, prefix=f"S{i}_manual", doc="线下办理" + (
                    "；证据：" + "；".join(s.get("evidence") or []) if s.get("evidence") else ""))
                reg_name = cmd if cmd.startswith("登记") else "登记" + cmd
                chain.append(main.id)
                self.timeout_for(s, main, lane, cmd, info)
                reg = self.add("userTask", reg_name, lane, prefix=f"S{i}_reg", doc=doc)
                chain.append(reg.id)
            else:
                main = self.add(kind, name, lane, prefix=f"S{i}", doc=doc)
                chain.append(main.id)
                reg = main
                self.timeout_for(s, main, lane, cmd, info)
            info["main"], info["reg"] = main.id, reg.id
            if ext:
                pool = self.pool(ext)
                self.msgs.append({"id": f"Msg_{len(self.msgs) + 1}", "name": cmd, "src": main.id, "tgt": pool})
                if s.get("evidence") or s.get("decision"):
                    rc = self.add("intermediateCatchEvent", f"{ext}返回结果", lane, prefix=f"S{i}_recv", defn="message")
                    chain.append(rc.id)
                    self.msgs.append({"id": f"Msg_{len(self.msgs) + 1}", "name": f"{ext}返回结果", "src": pool, "tgt": rc.id})
            ap = s.get("approval") or {}
            if ap.get("engine") == "external" and ap.get("approver"):
                pool = self.pool(pool_role_name(ap["approver"], self.stake) + f"（{ap['approver']}）")
                self.msgs.append({"id": f"Msg_{len(self.msgs) + 1}", "name": str(ap.get("doc_type") or "书面认可"), "src": pool, "tgt": reg.id})
            if not service_like:
                for a in apis:
                    sv = self.add("serviceTask", f"{a.get('name')} → {a.get('bc')}", SYSTEM, prefix=f"S{i}_api")
                    chain.append(sv.id)
            for ename, bcs in evs:
                th = self.add("intermediateThrowEvent", ename, lane, prefix=f"S{i}_evt", defn="signal")
                self.signal(ename)
                self.annotate(th, "对方 BC：" + "、".join(str(b) for b in bcs))
                chain.append(th.id)
            # 数据
            writes = [agg] if agg else []
            reads = [a for a in (s.get("reads") or []) if a != agg]
            both = [a for a in (s.get("reads") or []) if a == agg]
            reg_n = self.nodes[reg.id]
            for a in writes:
                reg_n.writes.append(a)
                if both:
                    reg_n.reads.append(a)
            reg_n.reads += reads
            if kind == "manualTask" and s.get("evidence"):
                reg_n.objs.append(clip(head(s["evidence"][0]), 12))
            elif ext and s.get("evidence"):
                reg_n.objs.append(clip(head(s["evidence"][0]), 12))
            # 判断
            decs = [(d.get("when"), self.norm_goto(d.get("goto"), n)) for d in (s.get("decision") or [])]
            head_of_entry = i > 1 and i in entry_set
            nxt = i + 1 if i < n and (not steps[i].get("branch") or s.get("branch") or head_of_entry) else "end"
            if not decs:
                outs = [(None, nxt)]
            else:
                outs = list(decs)
                if not any(w == "otherwise" for w, _ in outs):
                    outs.append(("otherwise", nxt))
                if len({t for _, t in outs}) == 1 and len({dd.get("outcome") for dd in (s.get("decision") or [])}) <= 1:
                    outs = [(None, outs[0][1])]
                elif not any(w == "otherwise" for w, _ in decs):
                    self.warn("decision 缺 otherwise，已补「其他」流向下一步")
            outs = self.add_exits(s, outs, n, info)
            info["outs"] = outs
            if len(outs) > 1:
                gname = (cmd if len(cmd) < 14 else head(cmd)) + "？"
                g = self.add("exclusiveGateway", gname, lane, prefix=f"S{i}_gw")
                chain.append(g.id)
            info["step"] = s
            self.plan.append(info)
        for st, e in zip(self.starts, self.entries):
            if e > 1:
                self.order.remove(st.id)
                self.order.insert(self.order.index(self.plan[e - 1]["chain"][0]), st.id)
        if len(self.used_timeout_actions) != len(self.timeout_actions):
            raise ValueError(f"{self.pid}: timeout_actions 有未匹配的步骤或期限")

    def timeout_action_for(self, s, timeout):
        matches = [(i, a) for i, a in enumerate(self.timeout_actions)
                   if a.get("step_command") == s.get("command") and a.get("after") == timeout.get("after")]
        if not matches:
            return None
        if len(matches) != 1:
            raise ValueError(f"{self.pid}: timeout_actions 的步骤与期限重复")
        i, action = matches[0]
        guard = action.get("guard") or {}
        failure = guard.get("goto")
        if (timeout.get("interrupting") is not True or timeout.get("escalate_to") != SYSTEM
                or not action.get("command") or action.get("aggregate") != s.get("aggregate")
                or not action.get("aggregate") or action.get("outcome") not in (self.p.get("outcomes") or [])
                or not guard.get("condition") or not isinstance(failure, int) or isinstance(failure, bool)
                or not 1 <= failure <= len(self.steps)):
            raise ValueError(f"{self.pid}: timeout_actions 须明确系统中断、命令、同一聚合、结局及守卫失败步骤")
        self.used_timeout_actions.add(i)
        return action

    def timeout_for(self, s, main, lane, cmd, info):
        t = s.get("timeout")
        if not t:
            return
        if "interrupting" not in t:
            self.warn("timeout 缺 interrupting，按 BPMN 缺省（中断）")
        bd = self.add("boundaryEvent", f"期限 {t.get('after')}", lane, prefix=f"{main.id}_bd", place=False,
                      attached=main.id, cancel=bool(t.get("interrupting", True)), duration=t.get("after"))
        action = self.timeout_action_for(s, t)
        if action:
            guard = action["guard"]
            gate = self.add("exclusiveGateway", guard["condition"] + "？", SYSTEM, prefix=f"{main.id}_guard")
            task = self.add("serviceTask", action["command"], SYSTEM, prefix=f"{main.id}_auto",
                            doc=f"{action['aggregate']} {action['command']}；超时系统代办；{guard['condition']}；期限 {t['after']}")
            task.writes.append(action["aggregate"])
            info["timeout_action"] = (bd.id, gate.id, task.id, action)
            return
        esc_lane = self.lane_of(t.get("escalate_to"))
        esc = self.add("userTask", f"处理超时：{cmd}", esc_lane, prefix=f"{main.id}_esc",
                       doc=f"升级给 {t.get('escalate_to')}；期限 {t.get('after')}")
        if esc_lane == lane:
            self.warn("超时升级任务与原任务同泳道，布局上主线穿过升级任务")
        info["timeout"] = (bd.id, esc.id, bool(t.get("interrupting", True)), esc_lane)

    # ---- 第二遍：顺序流 ----
    def target_node(self, tgt, step, when, src_lane, exit_names=None):
        if isinstance(tgt, int):
            return self.plan[tgt - 1]["chain"][0]
        if tgt == "end":
            name = (exit_names or {}).get(when) or self.outcome_for(step, when, tgt)
        else:
            name = f"已转 {tgt}"
        if name not in self.ends:
            e = self.add("endEvent", name, src_lane, prefix="End")
            self.ends[name] = e.id
            if tgt != "end":
                self.annotate(e, f"转 {tgt}")
        return self.ends[name]

    def wire(self):
        for st, e in zip(self.starts, self.entries):
            self.flow(st.id, self.plan[e - 1]["chain"][0] if self.plan else None)
        incoming = {st.id for st in self.starts}
        for info in self.plan:
            ch = info["chain"]
            for a, b in zip(ch, ch[1:]):
                self.flow(a, b)
            tail = ch[-1]
            outs = info["outs"]
            for when, tgt in outs:
                label = ""
                if when is not None:
                    label = info["labels"].get(when) or ("其他" if when == "otherwise" else str(when))
                node_id = self.target_node(tgt, info["step"], when, info["lane"], info["exit_names"])
                self.flow(tail, node_id, label)
            if "timeout_action" in info:
                bd, gate, task, action = info["timeout_action"]
                self.flow(bd, gate)
                self.flow(gate, task, "满足守卫")
                failure = self.plan[action["guard"]["goto"] - 1]["chain"][0]
                self.flow(gate, failure, "不满足守卫，不执行代关")
                self.flow(task, self.target_node("end", {"outcome": action["outcome"]}, None, SYSTEM))
            elif "timeout" in info:
                bd, esc, interrupting, esc_lane = info["timeout"]
                self.flow(bd, esc)
                if interrupting:
                    first = next(f for f in self.flows if f["src"] == info["main"])
                    self.flow(esc, first["tgt"])
                else:
                    end = self.add("endEvent", "已提醒", esc_lane, prefix="End")
                    self.flow(esc, end.id)
        for f in self.flows:
            incoming.add(f["tgt"])
        for info in self.plan:
            if info["chain"][0] not in incoming:
                self.warn(f"第 {info['i']} 步没有入口顺序流")

    # ---- 布局 ----
    def layout(self):
        used = []
        for nid in self.order:
            if self.nodes[nid].lane not in used:
                used.append(self.nodes[nid].lane)
        acting = [r["id"] for r in (self.p.get("roles") or []) if r.get("as") not in ("external", "informed")]
        lanes = [x for x in acting if x in used]
        lanes += [x for x in used if x not in lanes and x != SYSTEM]
        if SYSTEM in used:
            lanes.append(SYSTEM)
        self.lanes = lanes
        top = {l: i * LANE_H for i, l in enumerate(lanes)}
        self.lane_top = top
        cursor = HDR + 30
        for nid in self.order:
            n = self.nodes[nid]
            n.cx = cursor + n.slot / 2
            n.cy = top[n.lane] + LANE_H / 2
            cursor += n.slot
        for n in self.nodes.values():
            if n.attached:
                m = self.nodes[n.attached]
                n.cx, n.cy = m.cx + 25, m.cy + m.h / 2
        self.width = max(cursor + 30, 600)
        self.main_h = len(lanes) * LANE_H
        # 黑盒泳池：按消息流的 x 范围贴靠
        spans = {}
        for m in self.msgs:
            for ref in (m["src"], m["tgt"]):
                if ref in self.nodes:
                    x = self.nodes[ref].cx
                    lo, hi = spans.get(m["src"] if m["tgt"] in self.nodes else m["tgt"], (x, x))
                    key = m["src"] if m["tgt"] in self.nodes else m["tgt"]
                    spans[key] = (min(lo, x), max(hi, x))
        rows = []
        self.pool_box = {}
        for name in self.pools:
            lo, hi = spans.get(name, (HDR + 30, HDR + 230))
            lo, hi = max(HDR, lo - 70), min(self.width, max(hi + 70, lo + 140))
            for r, taken in enumerate(rows):
                if all(hi < a - 10 or lo > b + 10 for a, b in taken):
                    taken.append((lo, hi))
                    break
            else:
                rows.append([(lo, hi)])
                r = len(rows) - 1
            self.pool_box[name] = (lo, self.main_h + 60 + r * (BLACK_H + 20), hi - lo, BLACK_H)
        self.height = self.main_h + (60 + len(rows) * (BLACK_H + 20) if rows else 0)
        self.chan = {}

    def clear(self, pts, skip):
        rects = [n.box() for n in self.nodes.values() if n.id not in skip and not n.attached]
        for p, q in zip(pts, pts[1:]):
            (x1, y1), (x2, y2) = p, q
            for l, t, r, b in rects:
                l, t, r, b = l - 3, t - 3, r + 3, b + 3
                if x1 == x2 and l < x1 < r and max(min(y1, y2), t) < min(max(y1, y2), b):
                    return False
                if y1 == y2 and t < y1 < b and max(min(x1, x2), l) < min(max(x1, x2), r):
                    return False
        return True

    def channel(self, s, t):
        li = self.lanes.index(s.lane)
        k = self.chan.get(li, 0)
        self.chan[li] = k + 1
        y = (li + 1) * LANE_H - 6 - 5 * (k % 3)
        ty = t.cy - t.h / 2 if self.lanes.index(t.lane) > li else t.cy + t.h / 2
        return [(s.cx, s.cy + s.h / 2), (s.cx, y), (t.cx, y), (t.cx, ty)]

    def route(self, s, t):
        sr, tl = s.cx + s.w / 2, t.cx - t.w / 2
        skip = {s.id, t.id}
        if tl >= sr and t.cx > s.cx:
            if s.cy == t.cy:
                pts = [(sr, s.cy), (tl, t.cy)]
                if self.clear(pts, skip):
                    return pts
            else:
                if s.kind == "exclusiveGateway":
                    y0 = s.cy - GW / 2 if t.cy < s.cy else s.cy + GW / 2
                    pts = [(s.cx, y0), (s.cx, t.cy), (tl, t.cy)]
                    if self.clear(pts, skip):
                        return pts
                ex = tl - 20
                if ex < sr + 10:
                    ex = (sr + tl) / 2
                pts = [(sr, s.cy), (ex, s.cy), (ex, t.cy), (tl, t.cy)]
                if self.clear(pts, skip):
                    return pts
        return self.channel(s, t)

    def route_boundary(self, bd, esc):
        li = self.lanes.index(bd.lane)
        ei = self.lanes.index(esc.lane)
        bx, by = bd.cx, bd.cy + EVT / 2
        if ei > li:
            return [(bx, by), (bx, esc.cy), (esc.cx - esc.w / 2, esc.cy)]
        y = (li + 1) * LANE_H - 6
        return [(bx, by), (bx, y), (esc.cx, y), (esc.cx, esc.cy + esc.h / 2)]

    # ---- 输出 ----
    def prep_data(self):
        for n in self.nodes.values():
            items = []
            for k, a in enumerate(n.writes, 1):
                items.append(("store", f"SR_{n.id}_w{k}", a, True))
            for k, a in enumerate(n.reads, 1):
                items.append(("store", f"SR_{n.id}_r{k}", a, False))
            for k, o in enumerate(n.objs, 1):
                items.append(("obj", f"OR_{n.id}_{k}", o, True))
            seen, uniq = {}, []
            for it in items:
                if it[0] == "store" and it[2] in seen:
                    continue
                if it[0] == "store":
                    seen[it[2]] = it[1]
                uniq.append(it)
            n.shapes = uniq
            n.alias = {it[1]: seen[it[2]] for it in items if it[0] == "store"}

    def xml(self):
        self.prep_data()
        e = escape
        q = quoteattr
        inc, out = {}, {}
        for f in self.flows:
            out.setdefault(f["src"], []).append(f["id"])
            inc.setdefault(f["tgt"], []).append(f["id"])
        L = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" '
             'xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" '
             'xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" '
             f'id="Defs_{self.pid}" targetNamespace="https://forcitis.one/bpmn/{self.pid}" '
             'exporter="bpmn_gen" exporterVersion="1">']
        for name, sid in self.signals.items():
            L.append(f'  <signal id="{sid}" name={q(name)}/>')
        aggs = []
        for n in self.nodes.values():
            for a in n.reads + n.writes:
                if a not in aggs:
                    aggs.append(a)
        for a in aggs:
            L.append(f'  <dataStore id="DS_{a}" name="{a}"/>')
        L.append(f'  <collaboration id="Collab_{self.pid}">')
        L.append(f'    <participant id="P_main" name="FORCITIS one" processRef="{self.pid}"/>')
        for name, pid in self.pools.items():
            L.append(f'    <participant id="{pid}" name={q(name)}/>')
        for m in self.msgs:
            s = self.pools.get(m["src"], m["src"])
            t = self.pools.get(m["tgt"], m["tgt"])
            L.append(f'    <messageFlow id="{m["id"]}" name={q(m["name"])} sourceRef="{s}" targetRef="{t}"/>')
        L.append("  </collaboration>")
        L.append(f'  <process id="{self.pid}" name={q(self.name)} isExecutable="false">')
        L.append(f"    <documentation>{e('owner ' + str(self.p.get('owner_bc')) + '；' + '、'.join(self.p.get('stages') or []))}</documentation>")
        L.append('    <laneSet id="LaneSet_1">')
        for lane in self.lanes:
            nm = "系统" if lane == SYSTEM else f"{role_name(lane, self.stake)}（{lane}）"
            L.append(f'      <lane id="Lane_{lane}" name={q(nm)}>')
            for nid in self.order + [x for x, n in self.nodes.items() if n.attached]:
                if self.nodes[nid].lane == lane:
                    L.append(f"        <flowNodeRef>{nid}</flowNodeRef>")
            L.append("      </lane>")
        L.append("    </laneSet>")
        refs = []
        for n in self.nodes.values():
            tag = n.kind
            at = f' id="{n.id}" name={q(n.name)}'
            if tag == "boundaryEvent":
                at += f' cancelActivity="{"true" if n.cancel else "false"}" attachedToRef="{n.attached}"'
            if tag == "exclusiveGateway":
                at += ""
            L.append(f"    <{tag}{at}>")
            if n.doc:
                L.append(f"      <documentation>{e(n.doc)}</documentation>")
            for f in inc.get(n.id, []):
                L.append(f"      <incoming>{f}</incoming>")
            for f in out.get(n.id, []):
                L.append(f"      <outgoing>{f}</outgoing>")
            if n.kind in TASK_KINDS:
                ins = [(f"DIn_{n.id}_{k}", a) for k, a in enumerate(n.reads, 1)]
                outs = [(f"DOut_{n.id}_{k}", a) for k, a in enumerate(n.writes + n.objs, 1)]
                L.append(f'      <ioSpecification id="IO_{n.id}">')
                for i, _ in ins:
                    L.append(f'        <dataInput id="{i}"/>')
                for i, _ in outs:
                    L.append(f'        <dataOutput id="{i}"/>')
                L.append("        <inputSet>" + "".join(f"<dataInputRefs>{i}</dataInputRefs>" for i, _ in ins) + "</inputSet>")
                L.append("        <outputSet>" + "".join(f"<dataOutputRefs>{i}</dataOutputRefs>" for i, _ in outs) + "</outputSet>")
                L.append("      </ioSpecification>")
                for k, (i, a) in enumerate(ins, 1):
                    aid = f"DIA_{n.id}_{k}"
                    L.append(f'      <dataInputAssociation id="{aid}"><sourceRef>{n.alias[f'SR_{n.id}_r{k}']}</sourceRef><targetRef>{i}</targetRef></dataInputAssociation>')
                    refs.append((aid, n.alias[f"SR_{n.id}_r{k}"], n.id, "in", a))
                for k, (i, a) in enumerate(outs, 1):
                    aid = f"DOA_{n.id}_{k}"
                    is_obj = k > len(n.writes)
                    src = f"OR_{n.id}_{k - len(n.writes)}" if is_obj else n.alias[f"SR_{n.id}_w{k}"]
                    L.append(f'      <dataOutputAssociation id="{aid}"><sourceRef>{i}</sourceRef><targetRef>{src}</targetRef></dataOutputAssociation>')
                    refs.append((aid, src, n.id, "out", a))
            if n.defn == "signal":
                L.append(f'      <signalEventDefinition signalRef="{self.signals[n.name]}"/>')
            elif n.defn == "timer" and n.kind != "boundaryEvent":
                L.append("      <timerEventDefinition/>")
            elif n.defn == "message":
                L.append("      <messageEventDefinition/>")
            if n.kind == "boundaryEvent":
                L.append(f"      <timerEventDefinition><timeDuration>{e(str(n.duration))}</timeDuration></timerEventDefinition>")
            L.append(f"    </{tag}>")
        for f in self.flows:
            nm = f' name={q(f["name"])}' if f["name"] else ""
            L.append(f'    <sequenceFlow id="{f["id"]}"{nm} sourceRef="{f["src"]}" targetRef="{f["tgt"]}"/>')
        for n in self.nodes.values():
            for kind, rid, label, _ in n.shapes:
                if kind == "store":
                    L.append(f'    <dataStoreReference id="{rid}" name={q(label)} dataStoreRef="DS_{label}"/>')
                else:
                    L.append(f'    <dataObject id="DObj_{rid}"/>')
                    L.append(f'    <dataObjectReference id="{rid}" name={q(label)} dataObjectRef="DObj_{rid}"/>')
        for a in self.anns:
            L.append(f'    <textAnnotation id="{a["id"]}"><text>{e(a["text"])}</text></textAnnotation>')
            L.append(f'    <association id="Assoc_{a["id"]}" sourceRef="{a["node"]}" targetRef="{a["id"]}" associationDirection="None"/>')
        L.append("  </process>")
        L.append(self.di(refs))
        L.append("</definitions>")
        return "\n".join(L) + "\n"

    def di(self, refs):
        q = quoteattr
        r = lambda v: f"{v:.0f}"
        o = [f'  <bpmndi:BPMNDiagram id="Diagram_{self.pid}">', f'    <bpmndi:BPMNPlane id="Plane_{self.pid}" bpmnElement="Collab_{self.pid}">']

        def shape(el, x, y, w, h, extra="", label=None):
            o.append(f'      <bpmndi:BPMNShape id="Shape_{el}" bpmnElement="{el}"{extra}>')
            o.append(f'        <dc:Bounds x="{r(x)}" y="{r(y)}" width="{r(w)}" height="{r(h)}"/>')
            if label:
                o.append(f'        <bpmndi:BPMNLabel><dc:Bounds x="{r(label[0])}" y="{r(label[1])}" width="{r(label[2])}" height="{r(label[3])}"/></bpmndi:BPMNLabel>')
            o.append("      </bpmndi:BPMNShape>")

        def edge(el, pts):
            o.append(f'      <bpmndi:BPMNEdge id="Edge_{el}" bpmnElement="{el}">')
            for x, y in pts:
                o.append(f'        <di:waypoint x="{r(x)}" y="{r(y)}"/>')
            o.append("      </bpmndi:BPMNEdge>")

        shape("P_main", 0, 0, self.width, self.main_h, ' isHorizontal="true"')
        for lane in self.lanes:
            shape(f"Lane_{lane}", HDR, self.lane_top[lane], self.width - HDR, LANE_H, ' isHorizontal="true"')
        for name, pid in self.pools.items():
            x, y, w, h = self.pool_box[name]
            shape(pid, x, y, w, h, ' isHorizontal="true"')
        for n in self.nodes.values():
            l, t, rr, b = n.box()
            if n.kind == "exclusiveGateway":
                shape(n.id, l, t, n.w, n.h, ' isMarkerVisible="true"', (n.cx - 45, self.lane_top[n.lane] + 6, 90, 28))
            else:
                shape(n.id, l, t, n.w, n.h)
        pos = {}
        for n in self.nodes.values():
            shapes = getattr(n, "shapes", [])
            if not shapes:
                continue
            k = len(shapes)
            w_item = min(STORE_W, (SLOT_T - 10 - 6 * (k - 1)) / k)
            total = k * w_item + 6 * (k - 1)
            x0 = n.cx - total / 2
            for idx, (kind, rid, label, _) in enumerate(shapes):
                x = x0 + idx * (w_item + 6)
                y = self.lane_top[n.lane] + 4
                hh = STORE_H if kind == "store" else OBJ_H
                ww = w_item if kind == "store" else min(OBJ_W, w_item)
                shape(rid, x, y, ww, hh)
                pos[rid] = (x + ww / 2, y + hh)
        for a in self.anns:
            n = self.nodes[a["node"]]
            shape(a["id"], n.cx - ANN_W / 2, self.lane_top[n.lane] + 6, ANN_W, ANN_H)
        for f in self.flows:
            s, t = self.nodes[f["src"]], self.nodes[f["tgt"]]
            edge(f["id"], self.route_boundary(s, t) if s.attached else self.route(s, t))
        for m in self.msgs:
            if m["src"] in self.nodes:
                n = self.nodes[m["src"]]
                x, y, w, h = self.pool_box[m["tgt"]]
                edge(m["id"], [(n.cx, n.cy + n.h / 2), (n.cx, y)])
            else:
                n = self.nodes[m["tgt"]]
                x, y, w, h = self.pool_box[m["src"]]
                edge(m["id"], [(n.cx, y), (n.cx, n.cy + n.h / 2)])
        for a in self.anns:
            n = self.nodes[a["node"]]
            edge(f"Assoc_{a['id']}", [(n.cx, self.lane_top[n.lane] + 6 + ANN_H), (n.cx, n.cy - n.h / 2)])
        for aid, ref, nid, direction, _ in refs:
            n = self.nodes[nid]
            px, py = pos[ref]
            tx = min(max(px, n.cx - n.w / 2 + 10), n.cx + n.w / 2 - 10)
            ty = n.cy - n.h / 2
            edge(aid, [(px, py), (tx, ty)] if direction == "in" else [(tx, ty), (px, py)])
        o.append("    </bpmndi:BPMNPlane>")
        o.append("  </bpmndi:BPMNDiagram>")
        return "\n".join(o)


def load_checker():
    spec = importlib.util.spec_from_file_location("bpmn_check", CHECKER)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def build_xml(p, cat, stake):
    pr = Process(p, cat[p["id"]]["name"], stake, cat[p["id"]].get("timeout_actions"))
    pr.build()
    pr.wire()
    pr.layout()
    return pr, ET.fromstring(pr.xml())


def flow_edges(root):
    ns = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
    names = {e.get("id"): e.get("name") for e in root.iter() if e.get("id")}
    return [(names[f.get("sourceRef")], f.get("name"), names[f.get("targetRef")]) for f in root.iter(ns + "sequenceFlow")]


def test_trigger_entries(stake, checker, expect):
    from copy import deepcopy

    p = yaml.safe_load((HERE / "bpmn_gen_fixtures/activity_cancel.yaml").read_text(encoding="utf-8"))["processes"][0]
    cat = {p["id"]: {"name": "员工活动类申请"}}
    pr, root = build_xml(p, cat, stake)
    errors, _ = checker.check_root(root, False, {p["id"]: cat[p["id"]]["name"]})
    expect(not errors and not pr.warns, f"CON-1017-ARC-08：取消入口夹具须通过 BPMN 检查且无警告：{errors} {pr.warns}")
    targets = lambda src: {f["tgt"] for f in pr.flows if f["src"] == src}
    for start, entry in zip(pr.starts, [1, 1, 3, 3]):
        expect(targets(start.id) == {pr.plan[entry - 1]["chain"][0]}, "ARC-08：每个开始事件只能直连指定步骤")
        expect(start.lane == pr.plan[entry - 1]["lane"], "ARC-08：开始事件须在入口步骤泳道")
    cancel = pr.plan[2]["chain"][0]
    seen, pending = set(), [s.id for s in pr.starts[2:]]
    while pending:
        node = pending.pop()
        if node not in seen:
            seen.add(node)
            pending.extend(targets(node))
    expect(cancel in seen and pr.ends["已取消"] in seen, "ARC-08：取消入口可到达取消任务及已取消结局")
    expect(not seen.intersection(n for info in pr.plan[:2] for n in info["chain"]), "ARC-08：取消入口不得重做登记或完成")
    for kind in ("human", "event", "time", "call"):
        for entry, target in [("1", 0), (2, 1), ("3", 2)]:
            varied = deepcopy(p)
            varied["triggers"].append({"type": kind, "name": "指定入口", "entry": entry})
            varied["steps"][target]["on_time"] = "等待办理时点"
            variant, variant_root = build_xml(varied, cat, stake)
            errors, _ = checker.check_root(variant_root, False, {p["id"]: cat[p["id"]]["name"]})
            expect(not errors, f"ARC-08：{kind} entry={entry!r} 须通过 BPMN 检查：{errors}")
            entered = {f["tgt"] for f in variant.flows if f["src"] == variant.starts[-1].id}
            expect(variant.entries[-1] == int(entry) and entered == {variant.plan[target]["chain"][0]},
                   f"ARC-08：{kind} entry={entry!r} 须连目标步骤首节点，保留步骤内部前置办理")
    for entry in (0, -1, 4, True, False, None, "", "0", "-1", "3.0", "²", "已完成", "end", "PRC-03", 1.0, [], {}):
        invalid = deepcopy(p)
        invalid["triggers"][2]["entry"] = entry
        try:
            build_xml(invalid, cat, stake)
        except ValueError as error:
            expect("PRC-144" in str(error) and "triggers[3].entry" in str(error), "ARC-08：非法入口须明确流程及触发位置")
        else:
            expect(False, f"ARC-08：非法 entry={entry!r} 必须失败，不得回退第 1 步")


def test_timeout_actions(stake, checker, expect):
    from copy import deepcopy

    fixture = yaml.safe_load((HERE / "bpmn_gen_fixtures/timeout.yaml").read_text(encoding="utf-8"))
    p = fixture["processes"][0]
    cat = {c["id"]: c for c in fixture["catalog"]}
    cmap = {pid: c["name"] for pid, c in cat.items()}
    pr, root = build_xml(p, cat, stake)
    errors, _ = checker.check_root(root, False, cmap)
    expect(not errors, f"ARCH-VS14 A8：超时夹具有错误：{errors}")
    bd, gate, task, action = pr.plan[4]["timeout_action"]
    targets = lambda src: {f["tgt"] for f in pr.flows if f["src"] == src}
    expect(pr.nodes[task].kind == "serviceTask" and pr.nodes[task].lane == SYSTEM, "A8：代关必须是系统服务任务")
    expect(pr.nodes[bd].cancel and pr.nodes[bd].duration == "P5D", "A8：P5D 中断原确认任务")
    expect(targets(bd) == {gate} and task in targets(gate), "A8：到期通过守卫后执行代关")
    expect(targets(task) == {pr.ends["已关闭"]}, "A8：代关只能到已关闭，不能回人工确认网关")
    expect(pr.plan[3]["chain"][0] in targets(gate), "A8：缺处理结论须回解决步骤，不得关闭")
    edges = flow_edges(root)
    expect(("关闭 IT 需求？", "提出人认为未解决", "受理 IT 需求") in edges, "A8：人工未解决分支仍可重开")
    bd2, esc2, interrupting, _ = pr.plan[1]["timeout"]
    expect(not interrupting and targets(esc2) and all(pr.nodes[t].name == "已提醒" for t in targets(esc2)),
           "A8：P2D 非中断提醒单独结束，不关闭主工单")
    for field, value in [("after", "P6D"), ("aggregate", "AGG-115"), ("outcome", "未登记结局"),
                         ("guard", {"condition": "有结论", "goto": 99})]:
        bad = deepcopy(cat)
        bad[p["id"]]["timeout_actions"][0][field] = value
        try:
            build_xml(p, bad, stake)
        except ValueError:
            continue
        expect(False, f"A8：无效 timeout_actions.{field} 必须失败")
    bad_p = deepcopy(p)
    bad_p["steps"][4]["timeout"]["interrupting"] = False
    try:
        build_xml(bad_p, cat, stake)
    except ValueError:
        return
    expect(False, "A8：非中断超时不得套用中断代关配置")


def self_test():
    catalog = yaml.safe_load(CATALOG.read_text(encoding="utf-8"))["processes"]
    cat = {c["id"]: c for c in catalog}
    stake = load_stakeholders()
    checker = load_checker()
    cmap = checker.load_catalog()
    fx = HERE / "bpmn_gen_fixtures"
    ok = True

    def expect(cond, text):
        nonlocal ok
        if not cond:
            ok = False
            print(f"失败：{text}")

    def run(name):
        p = yaml.safe_load((fx / name).read_text(encoding="utf-8"))["processes"][0]
        pr, root = build_xml(p, cat, stake)
        errors, _ = checker.check_root(root, False, cmap)
        expect(not errors, f"{name} 检查有错误：{errors}")
        return pr, flow_edges(root)

    pr, edges = run("mini.yaml")
    expect(len(pr.starts) == 4, "mini 应有 4 个开始事件")

    pr, edges = run("entry.yaml")
    expect(("发起乙", None, "办乙前") in edges, "entry: 3 的开始事件应连第 3 步")
    expect(("发起甲", None, "办甲") in edges, "无 entry 的开始事件应连第 1 步")
    expect(("办乙前", None, "办乙") in edges, "入口步骤的下一步带 branch 时仍应连接")

    pr, edges = run("exits.yaml")
    got = {(a, b, c) for a, b, c in edges if b in ("驳回", "撤回")}
    expect(("申请甲？", "驳回", "被拒") in got, "on_reject: end 取 on_reject_outcome 作结束事件名")
    expect(("申请甲？", "撤回", "已撤回") in got, "withdraw.to=end 接结束事件")
    expect(("申请乙？", "驳回", "已驳回") in got, "说明文字的 on_reject 当作 end，缺省名「已驳回」")
    expect(not any(a.startswith("申请丙") and b in ("驳回", "撤回") for a, b, _ in got), "decision 已画出的驳回、撤回不重复")
    expect(("申请丁？", "驳回", "申请甲") in got, "on_reject 为序号时连回该步骤")
    expect(("申请丁？", "撤回", "申请乙") in got, "withdraw.to 为序号时连回该步骤")
    test_timeout_actions(stake, checker, expect)
    test_trigger_entries(stake, checker, expect)
    print("bpmn_gen 自测：" + ("全部通过" if ok else "有失败"))
    return 0 if ok else 1


def main(argv):
    if "--self-test" in argv:
        return self_test()
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("skeleton")
    ap.add_argument("--out", required=True)
    ap.add_argument("--only")
    a = ap.parse_args(argv)
    catalog = yaml.safe_load(CATALOG.read_text(encoding="utf-8"))["processes"]
    cat = {c["id"]: c for c in catalog}
    stake = load_stakeholders()
    data = yaml.safe_load(Path(a.skeleton).read_text(encoding="utf-8"))
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    checker = load_checker()
    cmap = checker.load_catalog()
    bad = 0
    for p in data["processes"]:
        pid = p["id"]
        if a.only and pid != a.only:
            continue
        c = cat.get(pid)
        if not c:
            print(f"跳过 {pid}：不在流程目录 ssot/09-processes/catalog.yaml")
            continue
        if c.get("type") != "business":
            continue
        pr = Process(p, c["name"], stake, c.get("timeout_actions"))
        pr.build()
        pr.wire()
        pr.layout()
        xml = pr.xml()
        (out / f"{pid}.bpmn").write_text(xml, encoding="utf-8")
        errors, _ = checker.check_root(ET.fromstring(xml), False, cmap)
        for k, v in pr.missing.items():
            if v:
                pr.warn(f"{v} 个步骤缺 {k}，用缺省")
        nodes = pr.nodes.values()
        print(f"{pid} {c['name']}：任务 {sum(n.kind in TASK_KINDS for n in nodes)}，网关 {sum(n.kind == 'exclusiveGateway' for n in nodes)}，"
              f"开始 {sum(n.kind == 'startEvent' for n in nodes)}，结束 {sum(n.kind == 'endEvent' for n in nodes)}；检查：{'通过' if not errors else f'{len(errors)} 个错误'}")
        for w in pr.warns:
            print(f"  警告 {pid}：{w}")
        for e in errors:
            print(f"  错误：{e}")
        bad += bool(errors)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
