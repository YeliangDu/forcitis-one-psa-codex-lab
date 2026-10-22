#!/usr/bin/env python3
"""登记网络 v5：按理论框架上色 + 干系人回路 + 流程→执行的 Command 约束。

v5 相对 v4 的四处修改
  1) 恢复干系人回路：15 决策与行动 ──虚线──▶ 01 干系人（行动结果反馈、关注点复核）
  2) 新增 07 业务流程 → 10 受控业务执行 的边，并在下方注明约束：
     「每个改变对象状态的流程步骤必须声明其 Command」（校验见 validate_concerns.py P-1）
  3) 节点按理论框架上色（家族色），框内右侧标框架名，读者无需知道框架也能读懂
  4) 每个节点加一行通俗解释（给不熟悉框架的人）

绘制不变量（改图勿破坏）
  A) 顺序：背景 → 标题/图例 → 约束车道 → 方框 → 约束箭头 → 连线 → 连线标签（标签最后画）
  B) 列间隙：A-B 40 / B-C 110 / C-D 110 / D-BAND 60；标签放间隙中心，水平边标签上移 10–12px
  C) 连线一律正交折线，waypoints 显式给出
  D) 标签用 paint-order="stroke" 白描边；标签不压线段中点
  E) 预览用 Chrome headless，不用 qlmanage（会补方形并裁切）
"""
import sys
from pathlib import Path

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
W, H = 1900, 1400
DY = 60          # 图例占位上移后，所有行整体下移
BW, BH = 300, 112
X = {'A': 60, 'B': 400, 'C': 820, 'D': 1240, 'BAND': 1600}
Y = {k: v + DY for k, v in
     {'R1': 110, 'R2': 280, 'R3': 450, 'R4': 620, 'R5': 790, 'R6': 960, 'R7': 1130}.items()}

C = {'bg': '#fcfcfb', 'ink': '#1f2328', 'sub': '#5f6672', 'line': '#ccd1d9', 'box': '#ffffff',
     'value': '#2f6fb0', 'exec': '#2f8f6f', 'evid': '#c07a1e', 'dim': '#8a8f98', 'con': '#7c5cd6'}

# 框架家族：key → (中文层名, 框架标签, 主色, 浅底)
FAM = {
    'biz': ('业务架构层', 'BIZBOK v11 / TOGAF G178', '#1d4ed8', '#f2f6fd'),
    'bmm': ('战略与动机层', 'BMM 1.3 + 战略选择', '#b45309', '#fdf6ec'),
    'bpm': ('流程层', 'BPMN 2.0 + APQC PCF', '#0f766e', '#eef8f6'),
    'ddd': ('领域与实现层', 'DDD + ArchiMate', '#7c3aed', '#f5f1fe'),
    'rea': ('事实与度量层', 'REA + Kimball 维度建模', '#be123c', '#fdf0f3'),
    'gov': ('约束与治理层', 'TOGAF 治理 + ADR + 内控', '#475569', '#eef0f3'),
    'loc': ('本地构造', '本项目自定（无框架出处）', '#6b7280', '#f3f4f6'),
}

NODES = {
    'SH':   ('A', 'R1', 'biz', '01 干系人',        '谁在乎这件事、谁承担后果', 'SH-ID · Owner'),
    'CON':  ('B', 'R1', 'biz', '02 关注点',        '他们在乎什么、担心什么', 'CON-ID · 提出者'),
    'VP':   ('C', 'R1', 'biz', '03 价值主张',      '我们承诺交付什么价值', 'VP-ID · 取舍决策'),
    'REJ':  ('B', 'R2', 'bmm', '04 取舍决定',      '接还是不接；不接也要留痕', 'DEC-R-ID · 理由 · 复审'),
    'CAP':  ('C', 'R2', 'biz', '05 业务能力',      '靠什么本事兑现承诺', 'CAP-ID ↔ STG-ID'),
    'VS':   ('C', 'R3', 'biz', '06 价值流与阶段',  '从触发到交付分几步', 'VS/STG-ID · 门条件'),
    'PRC':  ('C', 'R4', 'bpm', '07 业务流程',      '每步谁做、做什么、何时算完', 'PRC-ID · 仅 L2/L3'),
    'OBJ':  ('D', 'R4', 'ddd', '09 业务对象',      '业务里有身份、有状态的东西', 'OBJ-ID + states/transitions'),
    'ACT':  ('C', 'R5', 'ddd', '10 受控业务执行',  '一次被授权的动作', 'ACT-ID · 命令/事件 · owner BC'),
    'FACT': ('D', 'R5', 'rea', '11 业务事实',      '发生过的、不可改的记录', 'FACT-ID · 业务/记录时间'),
    'BIND': ('D', 'R6', 'loc', '14 计算绑定',      '这次指标用哪版公式与口径', 'BIND-ID · 时间基准'),
    'DIM':  ('D', 'R7', 'rea', '12 维度',          '按人/项目/客户/组织切开看', 'DIM-ID · SCD 版本'),
    'KPI':  ('C', 'R6', 'rea', '13 指标与评价',    '目标多少、实际多少、差多少', 'KPI-ID · 可切分'),
    'DEC':  ('A', 'R7', 'gov', '15 决策与行动',    '谁负责改、什么时候改完', 'DEC-ID · 复核'),
}

EDGES = [
    # 价值追溯（蓝）
    ('SH', 'r', 'CON', 'l', [], '提出', 'value', (380, 156)),
    ('CON', 'r', 'VP', 'l', [], '取舍承接', 'value', (760, 156)),
    ('CON', 'b', 'REJ', 't', [], '取舍弃权', 'value', (562, 252)),
    ('REJ', 'r', 'CAP', 'l', [], '不新增投资', 'value', (760, 326)),
    ('VP', 'b', 'CAP', 't', [], '决定能力配置', 'value', (982, 312, 'start')),
    ('CAP', 'b', 'VS', 't', [], '支撑阶段', 'value', (982, 484, 'start')),
    ('OBJ', 't', 'VS', 'r', [(1390, 600), (1120, 600)], '门条件引用状态', 'ref', (1250, 590)),
    ('VP', 'b', 'VS', 'l', [(780, 222), (780, 506)], '由价值流兑现', 'value', (768, 600, 'end')),
    ('VS', 'b', 'PRC', 't', [], '拆解为 L2/L3 流程', 'value', (982, 594, 'start')),
    ('PRC', 'r', 'OBJ', 'l', [], '声明编排的对象', 'value', (1180, 706)),
    # 流程 → 执行的 Command 约束边
    ('PRC', 'b', 'ACT', 't', [], '每步声明 Command', 'exec', (982, 764, 'start')),
    ('OBJ', 'b', 'FACT', 't', [], '认定并记录', 'value', (1402, 764, 'start')),
    ('ACT', 'r', 'OBJ', 'l', [(1160, 906), (1160, 736)], 'Command 改变状态', 'exec', (1128, 790, 'start')),
    ('ACT', 'r', 'FACT', 'l', [], '产生新事实', 'exec', (1180, 944)),
    ('FACT', 'b', 'BIND', 't', [], '绑定口径', 'value', (1402, 934, 'start')),
    ('BIND', 'l', 'KPI', 'r', [], '版本化计算', 'value', (1180, 1006)),
    # 控制回路（绿）
    ('DEC', 't', 'ACT', 'b', [(210, 920), (970, 920)], '执行行动', 'exec', (400, 912)),
    ('KPI', 'l', 'DEC', 'r', [(620, 1016), (620, 1186)], '偏差触发', 'value', (700, 1006)),
    # 干系人回路（绿虚线，v5 恢复）
    ('DEC', 'l', 'SH', 'l', [(30, 1186), (30, 166)], '行动结果反馈 · 关注点复核', 'exec', (40, 700, 'start')),
    # 证据回答（橙虚线）
    ('KPI', 'r', 'CON', 'b', [(1210, 1016), (1210, 240), (550, 240)], '证据回答关注点', 'evid', (1220, 300, 'start')),
    # 维度（灰）
    ('FACT', 'r', 'DIM', 'r', [(1580, 846), (1580, 1186)], '维度切分', 'dim', (1592, 1020, 'start')),
    ('DIM', 'l', 'KPI', 'b', [(970, 1186)], '按维度聚合', 'dim', (1100, 1176)),
]


def box(key):
    lane, row, fam, title, plain, ids = NODES[key]
    zh_fam, ref, color, tint = FAM[fam]
    x, y = X[lane], Y[row]
    s = [f'<g><rect x="{x}" y="{y}" width="{BW}" height="{BH}" rx="10" fill="{tint}" '
         f'stroke="{color}" stroke-width="1.3" stroke-opacity="0.55"/>',
         f'<rect x="{x}" y="{y}" width="5" height="{BH}" rx="2.5" fill="{color}"/>',
         f'<text x="{x+16}" y="{y+30}" font-size="16.5" font-weight="600" fill="{C["ink"]}">{title}</text>',
         f'<text x="{x+16}" y="{y+54}" font-size="12" fill="{C["sub"]}">{plain}</text>',
         f'<text x="{x+16}" y="{y+76}" font-size="11.5" fill="{color}" opacity="0.9">{ids}</text>',
         f'<text x="{x+16}" y="{y+96}" font-size="10.5" fill="{color}" opacity="0.75">{ref}</text>',
         '</g>']
    return '\n'.join(s)


def anchor(key, side):
    lane, row = NODES[key][0], NODES[key][1]
    x, y = X[lane], Y[row]
    return {'l': (x, y + BH / 2), 'r': (x + BW, y + BH / 2),
            't': (x + BW / 2, y), 'b': (x + BW / 2, y + BH)}[side]


def path_of(f, fs, t, ts, way):
    pts = [anchor(f, fs)] + [(x, y + DY) for x, y in way] + [anchor(t, ts)]
    return 'M ' + ' L '.join(f'{x} {y}' for x, y in pts)


def main():
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
         f'font-family="-apple-system, PingFang SC, Helvetica Neue, Arial, sans-serif">',
         f'<rect width="{W}" height="{H}" fill="{C["bg"]}"/>']
    C['ref'] = '#9aa0a8'
    for k in ('value', 'exec', 'evid', 'dim', 'con', 'ref'):
        s.append(f'<defs><marker id="ar-{k}" markerWidth="9" markerHeight="9" refX="8.4" refY="4.5" '
                 f'orient="auto"><path d="M0,0 L9,4.5 L0,9 z" fill="{C[k]}"/></marker></defs>')

    s.append(f'<text x="60" y="50" font-size="25" font-weight="700" fill="{C["ink"]}">'
             f'价值 · 约束 · 控制：登记网络 v5（15 节点）</text>')
    s.append(f'<text x="60" y="74" font-size="12.5" fill="{C["sub"]}">'
             f'节点颜色 = 所属理论框架家族；框内第三行是标识，第四行是框架出处。不熟悉框架的人先读每格第二行的白话说明</text>')

    # 框架图例（两行）
    order = ['biz', 'bmm', 'bpm', 'ddd', 'rea', 'gov', 'loc']
    for i, k in enumerate(order):
        zh, ref, color, tint = FAM[k]
        col, row = i % 4, i // 4
        x = 60 + col * 460
        y = 100 + row * 26
        s.append(f'<rect x="{x}" y="{y}" width="14" height="14" rx="3" fill="{tint}" stroke="{color}" stroke-opacity="0.7"/>')
        s.append(f'<text x="{x+22}" y="{y+12}" font-size="12" fill="{C["sub"]}">{zh} · <tspan fill="{color}">{ref}</tspan></text>')

    # 线型图例
    for i, (lab, k, dash) in enumerate((('价值追溯', 'value', ''), ('执行反馈', 'exec', ''), ('证据回答', 'evid', '7 5'), ('维度', 'dim', ''))):
        x = 1240 + i * 150
        s.append(f'<line x1="{x}" y1="150" x2="{x+24}" y2="150" stroke="{C[k]}" stroke-width="2" stroke-dasharray="{dash}"/>')
        s.append(f'<text x="{x+30}" y="154" font-size="11.5" fill="{C["sub"]}">{lab}</text>')

    # 约束车道（08）
    bx, by, bw, bh = X['BAND'], 190 + DY, 250, 700
    zh, ref, color, tint = FAM['gov']
    s.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="{bh}" rx="12" fill="{tint}" '
             f'stroke="{color}" stroke-dasharray="8 6" stroke-width="1.3"/>')
    s.append(f'<rect x="{bx}" y="{by}" width="5" height="{bh}" rx="2.5" fill="{color}"/>')
    s.append(f'<text x="{bx+16}" y="{by+28}" font-size="15" font-weight="600" fill="{C["ink"]}">08 约束与认定规则</text>')
    s.append(f'<text x="{bx+16}" y="{by+50}" font-size="11.5" fill="{C["sub"]}">什么情况下必须拒绝</text>')
    s.append(f'<text x="{bx+16}" y="{by+72}" font-size="11" fill="{color}">INV-ID · 横切 03/06/07/09/10/11</text>')
    for i, t in enumerate(['作用范围 · 执行点', '失败证据 · 可否豁免', 'EXC：审批/到期/补偿',
                           'OWNER-BC · ADR 引用', '例外必须显式登记', '无执行点 = 未求解', '07/10 由 P-1 与 T6 检查']):
        s.append(f'<text x="{bx+16}" y="{by+100+i*22}" font-size="11.5" fill="{C["sub"]}">{t}</text>')
    s.append(f'<text x="{bx+16}" y="{by+250}" font-size="11" fill="{color}">TOGAF 治理 + ADR + 内控</text>')
    s.append(f'<text x="{bx+16}" y="{by+272}" font-size="11.5" fill="{C["sub"]}">约束不满射 ⇒ 架构未定</text>')

    for key, y in (('VP', 166 + DY), ('VS', 506 + DY), ('OBJ', 676 + DY), ('FACT', 846 + DY)):
        ax, ay = anchor(key, 'r')
        s.append(f'<path d="M {bx} {y} L {ax} {y}" fill="none" stroke="{color}" stroke-width="1.5" '
                 f'stroke-dasharray="5 4" marker-end="url(#ar-con)"/>')

    s += [box(k) for k in NODES]
    for f, fs, t, ts, way, _l, kind, _p in EDGES:
        dash = {'evid': ' stroke-dasharray="7 5"', 'ref': ' stroke-dasharray="2 4"',
                'exec': ' stroke-dasharray="6 4"' if (f, t) == ('DEC', 'SH') else ''}.get(kind, '')
        s.append(f'<path d="{path_of(f, fs, t, ts, way)}" fill="none" stroke="{C[kind]}" '
                 f'stroke-width="1.7"{dash} marker-end="url(#ar-{kind})"/>')
    for f, fs, t, ts, way, label, kind, pos in EDGES:
        if not label:
            continue
        lx, ly = pos[0], pos[1] + DY
        anc = pos[2] if len(pos) > 2 else 'middle'
        s.append(f'<text x="{lx}" y="{ly}" font-size="12" fill="{C["sub"]}" text-anchor="{anc}" '
                 f'stroke="{C["bg"]}" stroke-width="5" paint-order="stroke" stroke-linejoin="round">{label}</text>')

    rules = ['09 是锚：门 / 流程 / 事实 / 约束都指向它',
             '07 步骤改状态必须声明 Command（P-1）',
             '11 事实 → 12 维度 + 14 绑定 → 13 指标',
             '每个聚合 ≥1 张表（不是每个对象）']
    for i, r in enumerate(rules):
        s.append(f'<text x="{60+i*450}" y="{H-22}" font-size="11.5" fill="{C["sub"]}">• {r}</text>')
    s.append('</svg>')
    (OUT / 'registration-network.svg').write_text('\n'.join(s), encoding='utf8')
    print(f'已生成 {(OUT / "registration-network.svg")}')


if __name__ == '__main__':
    main()
