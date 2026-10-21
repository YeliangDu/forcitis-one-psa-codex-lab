"""Tiny BPMN 2.0.2 collaboration writer with grid DI (column 160, lane rows 120)."""
from xml.sax.saxutils import escape as esc

COL, ROW, X0, LANE_LABEL = 160, 120, 160, 30
SIZE = {'task': (110, 70), 'event': (36, 36), 'gw': (50, 50), 'ds': (50, 50), 'do': (36, 50), 'ann': (130, 46)}
KIND = {'userTask': 'task', 'serviceTask': 'task', 'manualTask': 'task', 'sendTask': 'task', 'callActivity': 'task',
        'startEvent': 'event', 'endEvent': 'event', 'intermediateThrowEvent': 'event',
        'intermediateCatchEvent': 'event', 'boundaryEvent': 'event',
        'exclusiveGateway': 'gw', 'parallelGateway': 'gw'}


def q(s):
    return esc(s, {'"': '&quot;'})


def build(pid, name, lanes, nodes, flows, msgs=(), pools_top=(), pools_bottom=(), stores=(), objects=(),
          annotations=(), signals=(), messages=(), doc=''):
    """lanes: [(id, name, rows)]; nodes: dict id -> {type, name, lane, col, row, doc, ev, attached, cancel, called}."""
    lane_y, y = {}, 0
    top_h = 60 * len(pools_top) + (40 if pools_top else 0)
    pool_y = top_h
    for lid, _, rows in lanes:
        lane_y[lid] = (pool_y + y, rows * ROW)
        y += rows * ROW
    pool_h = y
    maxcol = max(n['col'] for n in nodes.values()) + 2
    pool_w = X0 + maxcol * COL
    pos = {}
    for nid, n in nodes.items():
        k = KIND[n['type']]
        w, h = SIZE[k]
        if n['type'] == 'boundaryEvent':
            px, py, pw, ph = pos[n['attached']]
            pos[nid] = (px + pw - 25, py + ph - 18, w, h)
            continue
        ly, _ = lane_y[n['lane']]
        cx = X0 + n['col'] * COL + COL / 2
        cy = ly + n.get('row', 0) * ROW + ROW / 2
        pos[nid] = (cx - w / 2, cy - h / 2, w, h)
    bottom = pool_y + pool_h + 40
    for i, (sid, _, col, _) in enumerate(stores):
        w, h = SIZE['ds']
        pos[sid] = (X0 + col * COL + COL / 2 - w / 2, bottom, w, h)
    for oid, _, col, _ in objects:
        w, h = SIZE['do']
        pos[oid] = (X0 + col * COL + COL / 2 - w / 2, bottom + 70, w, h)
    for aid, _, col, row_y, _ in annotations:
        w, h = SIZE['ann']
        pos[aid] = (X0 + col * COL + 10, bottom + 140 + row_y, w, h)
    bp_y = bottom + 220 + 60 * (max([a[3] for a in annotations], default=0) // 60)
    poolpos = {}
    for i, (bid, _) in enumerate(pools_top):
        poolpos[bid] = (0, i * 60, pool_w, 50)
    for i, (bid, _) in enumerate(pools_bottom):
        poolpos[bid] = (0, bp_y + i * 60, pool_w, 50)

    def mid(p, side):
        x, y_, w, h = p
        return {'r': (x + w, y_ + h / 2), 'l': (x, y_ + h / 2), 't': (x + w / 2, y_), 'b': (x + w / 2, y_ + h)}[side]

    def route(s, t, i=0):
        ps, pt = pos[s], pos[t]
        if pt[0] > ps[0] + ps[2] / 2:
            a, b = mid(ps, 'r'), mid(pt, 'l')
            if abs(a[1] - b[1]) < 1:
                return [a, b]
            xm = (a[0] + b[0]) / 2
            return [a, (xm, a[1]), (xm, b[1]), b]
        a, b = mid(ps, 'b'), mid(pt, 'b')
        yl = max(a[1], b[1]) + 22 + 10 * i
        return [a, (a[0], yl), (b[0], yl), b]

    X = []
    w = X.append
    w('<?xml version="1.0" encoding="UTF-8"?>')
    w('<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" '
      'xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" '
      'xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
      f'id="Defs_{pid.replace("-", "_")}" targetNamespace="https://forcitis.one/bpmn/{pid}" '
      'exporter="one-arch-people bpmngen" exporterVersion="1">')
    for sid, sname in signals:
        w(f'  <signal id="{sid}" name="{q(sname)}"/>')
    for mid_, mname in messages:
        w(f'  <message id="{mid_}" name="{q(mname)}"/>')
    w(f'  <collaboration id="Collab_{pid.replace("-", "_")}">')
    w(f'    <participant id="P_main" name="FORCITIS one" processRef="{pid}"/>')
    for bid, bname in list(pools_top) + list(pools_bottom):
        w(f'    <participant id="{bid}" name="{q(bname)}"/>')
    for i, (mfid, s, t, mname) in enumerate(msgs):
        w(f'    <messageFlow id="{mfid}" name="{q(mname)}" sourceRef="{s}" targetRef="{t}"/>')
    w('  </collaboration>')
    w(f'  <process id="{pid}" name="{q(name)}" isExecutable="false">')
    if doc:
        w(f'    <documentation>{esc(doc)}</documentation>')
    w('    <laneSet id="LaneSet_1">')
    for lid, lname, _ in lanes:
        w(f'      <lane id="{lid}" name="{q(lname)}">')
        for nid, n in nodes.items():
            if n.get('lane') == lid or (n['type'] == 'boundaryEvent' and nodes[n['attached']]['lane'] == lid):
                w(f'        <flowNodeRef>{nid}</flowNodeRef>')
        w('      </lane>')
    w('    </laneSet>')
    inc = {k: [] for k in nodes}
    out = {k: [] for k in nodes}
    for fid, s, t, _ in flows:
        out[s].append(fid)
        inc[t].append(fid)
    for nid, n in nodes.items():
        attrs = f'id="{nid}" name="{q(n["name"])}"'
        if n['type'] == 'boundaryEvent':
            attrs += f' attachedToRef="{n["attached"]}" cancelActivity="{"true" if n.get("cancel") else "false"}"'
        if n['type'] == 'callActivity':
            attrs += f' calledElement="{n["called"]}"'
        w(f'    <{n["type"]} {attrs}>')
        if n.get('doc'):
            w(f'      <documentation>{esc(n["doc"])}</documentation>')
        for f in inc[nid]:
            w(f'      <incoming>{f}</incoming>')
        for f in out[nid]:
            w(f'      <outgoing>{f}</outgoing>')
        for sid, _, _, targets in stores:
            if nid in targets:
                w(f'      <dataOutputAssociation id="DA_{nid}_{sid}"><targetRef>{sid}</targetRef></dataOutputAssociation>')
        for oid, _, _, targets in objects:
            if nid in targets:
                w(f'      <dataOutputAssociation id="DA_{nid}_{oid}"><targetRef>{oid}</targetRef></dataOutputAssociation>')
        ev = n.get('ev')
        if ev:
            kind, ref = ev
            if kind == 'signal':
                w(f'      <signalEventDefinition id="ED_{nid}" signalRef="{ref}"/>')
            elif kind == 'message':
                w(f'      <messageEventDefinition id="ED_{nid}" messageRef="{ref}"/>')
            elif kind == 'timer':
                w(f'      <timerEventDefinition id="ED_{nid}"><timeDate xsi:type="tFormalExpression">{esc(ref)}</timeDate></timerEventDefinition>')
        w(f'    </{n["type"]}>')
    for fid, s, t, fname in flows:
        nm = f' name="{q(fname)}"' if fname else ''
        w(f'    <sequenceFlow id="{fid}"{nm} sourceRef="{s}" targetRef="{t}"/>')
    for sid, sname, _, _ in stores:
        w(f'    <dataStoreReference id="{sid}" name="{q(sname)}"/>')
    for oid, oname, _, _ in objects:
        w(f'    <dataObjectReference id="{oid}" name="{q(oname)}" dataObjectRef="{oid}_obj"/>')
        w(f'    <dataObject id="{oid}_obj"/>')
    for aid, text, _, _, _ in annotations:
        w(f'    <textAnnotation id="{aid}"><text>{esc(text)}</text></textAnnotation>')
    for aid, _, _, _, src in annotations:
        w(f'    <association id="As_{aid}" sourceRef="{src}" targetRef="{aid}"/>')
    w('  </process>')
    # DI
    w(f'  <bpmndi:BPMNDiagram id="Diagram_{pid.replace("-", "_")}">')
    w(f'    <bpmndi:BPMNPlane id="Plane_1" bpmnElement="Collab_{pid.replace("-", "_")}">')

    def shape(eid, p, extra=''):
        x, y_, ww, hh = p
        w(f'      <bpmndi:BPMNShape id="{eid}_di" bpmnElement="{eid}"{extra}>'
          f'<dc:Bounds x="{x:.0f}" y="{y_:.0f}" width="{ww:.0f}" height="{hh:.0f}"/></bpmndi:BPMNShape>')

    shape('P_main', (0, pool_y, pool_w, pool_h), ' isHorizontal="true"')
    for lid, _, _ in lanes:
        ly, lh = lane_y[lid]
        shape(lid, (LANE_LABEL, ly, pool_w - LANE_LABEL, lh), ' isHorizontal="true"')
    for bid, p in poolpos.items():
        shape(bid, p, ' isHorizontal="true"')
    for nid in nodes:
        shape(nid, pos[nid])
    for sid, *_ in stores:
        shape(sid, pos[sid])
    for oid, *_ in objects:
        shape(oid, pos[oid])
    for aid, *_ in annotations:
        shape(aid, pos[aid])

    def edge(eid, pts):
        w(f'      <bpmndi:BPMNEdge id="{eid}_di" bpmnElement="{eid}">' +
          ''.join(f'<di:waypoint x="{x:.0f}" y="{y_:.0f}"/>' for x, y_ in pts) + '</bpmndi:BPMNEdge>')

    for i, (fid, s, t, _) in enumerate(flows):
        edge(fid, route(s, t, i % 3))
    for i, (mfid, s, t, _) in enumerate(msgs):
        sp = pos.get(s) or poolpos[s]
        tp = pos.get(t) or poolpos[t]
        off = (i % 2) * 16 - 8
        if s in poolpos:
            x = tp[0] + tp[2] / 2 + off
            a = (x, sp[1] + sp[3]) if sp[1] < tp[1] else (x, sp[1])
            b = (x, tp[1]) if sp[1] < tp[1] else (x, tp[1] + tp[3])
        else:
            x = sp[0] + sp[2] / 2 + off
            a = (x, sp[1]) if tp[1] < sp[1] else (x, sp[1] + sp[3])
            b = (x, tp[1] + tp[3]) if tp[1] < sp[1] else (x, tp[1])
        edge(mfid, [a, b])
    for sid, _, _, targets in stores:
        for t in targets:
            edge(f'DA_{t}_{sid}', [mid(pos[t], 'b'), mid(pos[sid], 't')])
    for oid, _, _, targets in objects:
        for t in targets:
            edge(f'DA_{t}_{oid}', [mid(pos[t], 'b'), mid(pos[oid], 't')])
    for aid, _, _, _, src in annotations:
        edge(f'As_{aid}', [mid(pos[src], 'b'), mid(pos[aid], 't')])
    w('    </bpmndi:BPMNPlane>')
    w('  </bpmndi:BPMNDiagram>')
    w('</definitions>')
    return '\n'.join(X) + '\n'
