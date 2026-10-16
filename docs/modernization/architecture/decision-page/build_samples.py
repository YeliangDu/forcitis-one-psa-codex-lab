"""三组流程样板合成页：泳道图（SVG）＋设计点逐条意见＋待定题。读 u12.html 模板，输出 samples.html 与 SAMPLES.json。"""
import json
import re
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).parent
CW, LH, PADL, BOXW, BOXH = 132, 78, 92, 112, 52


def wrap(text, n=8):
    lines = [text[i:i + n] for i in range(0, len(text), n)] or [""]
    if len(lines) > 1 and len(lines[-1]) <= 1:
        last = lines.pop(); lines[-1] += last
    return lines


def swimlane(lanes, nodes, edges):
    cols = max(c for _, c, _, _ in nodes.values()) + 1
    w, h = PADL + cols * CW + 16, len(lanes) * LH + 8
    pos = {k: (PADL + c * CW + (CW - BOXW) / 2, lanes.index(l) * LH + (LH - BOXH) / 2 + 4) for k, (l, c, _, _) in nodes.items()}
    out = [f'<svg viewBox="0 0 {w} {h}" width="{w}" height="{h}" role="img" xmlns="http://www.w3.org/2000/svg" style="font:11px -apple-system,PingFang SC,sans-serif">',
           '<defs><marker id="ar" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="var(--muted)"/></marker></defs>']
    for i, l in enumerate(lanes):
        y = i * LH + 4
        out.append(f'<rect x="0" y="{y}" width="{w}" height="{LH}" fill="{"var(--soft)" if i % 2 else "var(--paper)"}" stroke="var(--line)"/>')
        for j, t in enumerate(wrap(l, 6)):
            out.append(f'<text x="8" y="{y + 26 + j * 14}" fill="var(--ink)" font-weight="600">{escape(t)}</text>')
    for a, b, lab in edges:
        (xa, ya), (xb, yb) = pos[a], pos[b]
        if nodes[b][1] > nodes[a][1]:
            x1, y1, x2, y2 = xa + BOXW, ya + BOXH / 2, xb, yb + BOXH / 2
            d = f"M{x1},{y1} C{x1 + 14},{y1} {x2 - 14},{y2} {x2},{y2}"
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2 - 4
        elif nodes[b][1] == nodes[a][1]:
            x1, y1 = xa + BOXW / 2, (ya + BOXH if yb > ya else ya)
            x2, y2 = xb + BOXW / 2, (yb if yb > ya else yb + BOXH)
            d = f"M{x1},{y1} L{x2},{y2}"
            mx, my = x1 + 4, (y1 + y2) / 2
        else:
            x1, y1, x2, y2 = xa + BOXW / 2, ya + BOXH, xb + BOXW / 2, yb + BOXH
            dip = max(y1, y2) + 22
            d = f"M{x1},{y1} C{x1},{dip} {x2},{dip} {x2},{y2}"
            mx, my = (x1 + x2) / 2, dip - 2
        out.append(f'<path d="{d}" fill="none" stroke="var(--muted)" stroke-width="1.2" marker-end="url(#ar)"/>')
        if lab:
            out.append(f'<text x="{mx}" y="{my}" text-anchor="middle" fill="var(--accent)" font-size="10">{escape(lab)}</text>')
    for k, (l, c, text, kind) in nodes.items():
        x, y = pos[k]
        stroke = {"approval": "var(--changed)", "decision": "var(--defer)", "system": "var(--accent)", "external": "var(--accent)", "offline": "var(--muted)"}.get(kind, "var(--ink)")
        dash = {"external": ' stroke-dasharray="6 3"', "offline": ' stroke-dasharray="2 3"'}.get(kind, "")
        out.append(f'<rect x="{x}" y="{y}" width="{BOXW}" height="{BOXH}" rx="7" fill="var(--paper)" stroke="{stroke}" stroke-width="1.6"{dash}/>')
        lines = wrap(text, 8)[:3]
        for j, t in enumerate(lines):
            out.append(f'<text x="{x + BOXW / 2}" y="{y + BOXH / 2 - (len(lines) - 1) * 7 + j * 14 + 4}" text-anchor="middle" fill="var(--ink)">{escape(t)}</text>')
    out.append("</svg>")
    return "".join(out)


LEGEND = "图例：黑框＝用户在本系统操作；橙框＝审批；黄框＝判断；绿实框＝本系统自动；绿长虚线框＝外部系统经集成完成；灰点线框＝线下完成、本系统只登记结果与证据。横向可滑动。"

REVENUE = swimlane(
    ["设计师", "技术专家", "注册执业人员", "项目经理", "客户", "收入侧（系统）"],
    {
        "a": ("设计师", 0, "编制交付物（设计软件）", "offline"),
        "b": ("技术专家", 1, "校对审核审定", "approval"),
        "c": ("注册执业人员", 2, "签发（查执业证书）", "decision"),
        "d": ("项目经理", 3, "送达并登记回执为证据", "offline"),
        "e": ("客户", 4, "书面认可／退回／不回复", "offline"),
        "f": ("项目经理", 5, "登记客户认可", "offline"),
        "g": ("项目经理", 4, "登记退回，轮次加1", "offline"),
        "h": ("项目经理", 6, "期满后登记无异议声明", "task"),
        "i": ("收入侧（系统）", 7, "确认收入（认可或视为同意）", "system"),
    },
    [("a", "b", ""), ("b", "a", "不通过"), ("b", "c", "通过"), ("c", "d", ""), ("d", "e", "送达"),
     ("e", "f", "认可"), ("e", "g", "退回"), ("g", "a", "改后重报"), ("e", "h", "审查期满无异议"),
     ("f", "i", "事件"), ("h", "i", "期满认定后")],
)

PEOPLE = swimlane(
    ["人力", "人力（薪酬社保）", "身份平台", "企业微信", "系统"],
    {
        "a": ("人力", 0, "登记入职（填入职日）", "task"),
        "b": ("身份平台", 1, "建待开通登录身份", "system"),
        "c": ("企业微信", 2, "开通成员（T-1）", "external"),
        "d": ("身份平台", 3, "按工号绑定，待生效", "system"),
        "x": ("身份平台", 4, "失败或逾期告警", "decision"),
        "e": ("系统", 4, "入职日到：入职生效", "system"),
        "f": ("身份平台", 5, "放行登录", "system"),
        "g": ("人力", 1, "建立任职（职位职级）", "task"),
        "h": ("人力", 2, "提交并批准劳动合同", "approval"),
        "k": ("人力", 3, "签订劳动合同（线下签）", "offline"),
        "m": ("人力（薪酬社保）", 5, "参保增员（网办）", "offline"),
        "n": ("人力（薪酬社保）", 6, "建立薪酬档案", "task"),
    },
    [("a", "b", "入职已登记"), ("b", "c", ""), ("c", "d", "成功"), ("c", "x", "失败"), ("a", "g", ""), ("g", "h", ""), ("h", "k", "批准"),
     ("d", "f", "待生效"), ("e", "f", "入职生效"), ("e", "m", "入职生效"), ("m", "n", "")],
)

FINANCE = swimlane(
    ["员工", "部门负责人", "财务", "系统"],
    {
        "a": ("员工", 0, "发票查验（税务）并占用", "external"),
        "b": ("员工", 1, "占用预算（INV-102）", "decision"),
        "c": ("员工", 2, "提交报销", "task"),
        "d": ("部门负责人", 3, "审批（F-01）", "approval"),
        "e": ("财务", 4, "核定报销与期间", "task"),
        "f": ("系统", 5, "冲抵员工借款", "system"),
        "g": ("系统", 6, "确认应付净额", "system"),
        "h": ("财务", 5, "提交过账候选", "system"),
        "r": ("系统", 4, "释放预算与发票", "system"),
        "z": ("系统", 7, "净额为零：标记报销已付", "system"),
    },
    [("a", "b", ""), ("b", "c", "预算够"), ("b", "r", "超额且拦截"), ("c", "d", ""), ("d", "e", "通过"), ("d", "r", "驳回"),
     ("e", "h", "核定后"), ("e", "f", "有借款"), ("e", "g", "无借款"), ("f", "g", "净额大于零"), ("f", "z", "净额为零")],
)


def item(no, grp, title, ctx, opts, basis):
    o = [{"label": l, "effect": e, "rec": False, "sug": False} for l, e in opts]
    o.append({"label": "暂不定（我先不选）", "effect": "不预设，留待以后", "rec": True, "sug": False})
    return {"no": no, "grp": grp, "title": title, "src": "", "ctx": ctx, "opts": o, "basis": basis, "practice": [], "now": "", "check": "", "key": "SMP~" + no}


def point(no, grp, text, basis):
    return item(no, grp, text, "", [("符合我的想法", ""), ("要改（在说明里写怎么改）", "按你的说明改样板与约定")], ["依据：" + basis])


def overall(no, grp, name):
    return item(no, grp, f"「{name}」整体是否符合你的想法", "看完泳道图与下面的设计点后，先给整体结论；具体意见写在各设计点上。",
                [("符合，可以按这个样子铺开", "该组按此铺开其余流程"), ("大体符合，按我写的意见改后铺开", "改约定后铺开"), ("不符合，要重做（在说明里写）", "该组重做样板")], [])


ITEMS = []
G = []
REV_POINTS = [
    ("送达、认可、退回、无异议声明都把回执与书面材料登记为证据，挂在本轮提交上", "你答「诉讼主要卡在缺客户签认或签收」（intent 144613）；ADR-0047 第 5 条第 2 点"),
    ("证据只追加、用服务端时间、带内容哈希，可整包封存，可选第三方时间戳", "你要求证据能作诉讼证据（intent 141725）；诉讼证据研究：法律不强制第三方存证"),
    ("交付侧不判断视为同意：只记送达、退回和项目经理的无异议声明；期满计算与认定在收入侧", "ADR-0049 决定 1、2"),
    ("客户认可不是系统内审批：客户的书面材料是证据，签认人须是合同登记的发包人代表或授权人", "ADR-0047 第 2 条"),
    ("认可日由证据得出，收入期间取认可日所在月，不接受人工填写期间", "ADR-0047 第 3 条"),
    ("退回后重新报送，审查期从新的送达日重算", "ADR-0047 第 5 条第 5 点；设计合同示范文本 GF-2015-0209 第 8.1 条"),
    ("内部校审按设计院通行的校对、审核、审定逐级登记，记在交付物里，不走审批平台", "AEC 行业通行做法（CLAUDE.md 第 4 条）"),
    ("签发人须持有效执业证书，专业与等级符合；有阻断性不符合项时不能登记认可", "聚合稿 INV-47、INV-48"),
    ("已签发版本不可改，只出新版本；文件版本带哈希", "聚合稿 INV-46"),
    ("无异议声明由项目经理作出，财务在收入确认审批中复核", "ADR-0047 U-2 (a)（你 10-16 按预选批准）"),
    ("历史交付物、提交轮次与认可记录全部迁入，记来源与原始时间", "你定的全部历史明细迁入（intent 133348）"),
]
PEO_POINTS = [
    ("FORCITIS one 是人事记录系统：入职、任职、劳动合同在本系统登记，不再从 A8 同步", "你定的自成体系、A8 停用（intent 133348、144943）"),
    ("入职日、任职生效日用人事业务生效日；可提前登记、到日生效，补登落回真实日期", "你定的 U-12（intent 134434）"),
    ("「入职已登记」和「入职生效」分开：登记时就准备账号，入职日才算正式在职、参保计薪", "由上一条推出"),
    ("账号在登记后自动准备，但入职日才放行登录", "你在 Q-03 写「通过入职后开通企业微信账号，解决」（intent 134134）"),
    ("企业微信账号号码由本系统定（取工号），按号码精确绑定，不按姓名或手机号模糊匹配；匹配不到就告警", "不静默跳过（CLAUDE.md 第 6 条）"),
    ("不沿用老系统「手机号或工号＋统一默认口令」的兜底登录", "安全规则；老系统只作证据"),
    ("任职＝组织单元＋职位＋职级＋汇报线＋主兼，按生效区间版本化，主任职区间不重叠", "你在 Q-08 答「岗位只要目录」（intent 134134）"),
    ("任职变了，系统自动重算默认角色与数据范围", "数据范围随任职（P-05）；按岗位授权规则另行起草（ADR-0029）"),
    ("劳动合同由人力负责人单级批准，在人事模块内完成，不走通用审批", "你批准的 F-3（intent 114200）"),
    ("试用期超法定上限直接拒绝；用工满一个月未签书面合同只提醒", "劳动合同法第 19、10、82 条"),
    ("员工的三个法人口径（劳动合同用人单位、数据范围法人、个税扣缴义务人）分别记，不一致只预警", "你在 Q-09 的答复（intent 134134）"),
    ("参保增员和薪酬档案从真实入职日起算，薪酬档案按敏感个人信息管", "社会保险法第 58 条；ADR-0043"),
    ("人员、任职、劳动合同、参保、薪酬档案历史全部迁入，按生效日回放，迁入不触发本流程", "全部历史明细迁入（intent 133348）；ADR-0036"),
]
FIN_POINTS = [
    ("报销一次只改报销本身，发票、预算、应付、借款都通过对方模块处理", "一个事务只改一个聚合（SESSION_PLAYBOOK §14）"),
    ("先占发票、再占预算、再提交；失败时反过来先释放预算、再释放发票", "⑥a 评审 H5"),
    ("一张发票只在报销上占用一次，报销产生的应付不再占用发票", "符合性审查 G-17；INV-74"),
    ("不合规发票、要素不全、项目已结项都当场拒收并告警", "INV-75、84、39；CLAUDE.md 第 6 条"),
    ("审批走通用审批平台", "你批准的 F-2（intent 114200）"),
    ("报销人、审批人、核定人不能是同一人", "会计基础工作规范的不相容职务分离"),
    ("财务核定时定所属期间；超过事前预批额度只预警", "聚合稿 AGG-59"),
    ("有未结员工借款时自动冲抵，冲抵后的净额才进应付", "你在 Q-10 定的员工借款自动冲抵（intent 134134）"),
    ("报销不认识会计科目，只把业务事实交给统一过账", "ADR-0016 决定 1、3"),
    ("历史报销、应付全部迁入，迁入不触发审批、不发事件", "全部历史明细迁入（intent 133348）；ADR-0036"),
]

G.append({"id": "rev", "title": "收入线：交付物从编制到客户认可（PRC-28）",
          "desc": "设计师编制，技术专家逐级校审，注册执业人员签发，项目经理送达并登记客户的认可、退回或期满无异议；收入侧据此确认收入。这是收入证据链的起点。",
          "diagram": REVENUE, "legend": LEGEND})
ITEMS.append(overall("R-0", "rev", "交付物从编制到客户认可"))
ITEMS.append(item("R-Q1", "rev", "交付物的校审要几级", "设计院通常有校对、审核、审定三级。",
                  [("按项目类别配置（例如方案两级、施工图三级）", "灵活；要维护一张配置表"), ("所有交付物统一三级", "简单；方案类交付会慢")], ["收入线样板第 4 节"]))
ITEMS.append(item("R-Q2", "rev", "合同没约定审查期、客户又迟迟不签认，系统怎么办", "",
                  [("按送达日提醒项目经理催签，超期列入阶段预警，不自动认可", "不会凭空确认收入；要人跟进"), ("另定（在说明里写）", "按你的说明")], ["收入线样板第 4 节"]))
for n, (t, b) in enumerate(REV_POINTS, 1):
    ITEMS.append(point(f"R-{n:02d}", "rev", t, b))

G.append({"id": "peo", "title": "人员线：入职到账号开通（PRC-83、137、84）",
          "desc": "人力登记入职后，身份平台准备企业微信账号；入职日到了才算在职、放行登录，并办理参保、建薪酬档案；同时建立任职、签劳动合同。",
          "diagram": PEOPLE, "legend": LEGEND})
ITEMS.append(overall("P-0", "peo", "入职到账号开通"))
ITEMS.append(item("P-T1", "peo", "企业微信成员怎么开通", "",
                  [("本系统在登记入职后自动调企业微信接口开通，信息部门只处理失败", "时差最小、号码由系统定、可追溯"), ("信息部门在企业微信后台手工开通，再在本系统确认号码", "不用接口权限；多一步人工")], ["人员线样板 T-1；IDENTITY_LIFECYCLE.md"]))
ITEMS.append(item("P-T2", "peo", "入职前多久开通，失败或入职日还没绑定时谁多久处理", "以下数字为草拟。",
                  [("入职前 3 个工作日开通；失败由信息部门 1 个工作日内处理", "新人第一天就能登录"), ("入职当天开通、当天处理", "准备时间紧"), ("另定（在说明里写）", "按你的说明")], ["人员线样板 T-2"]))
ITEMS.append(item("P-T3", "peo", "是否同意「提前开通、入职日才放行登录」", "",
                  [("同意", "按上图"), ("不同意，入职当天才开通", "第一天可能登录不了")], ["人员线样板 T-3"]))
ITEMS.append(item("P-T4", "peo", "哪些用工类型不参保", "",
                  [("退休返聘、实习不参保，其余都参保", "按常见口径"), ("另定清单（在说明里写）", "按你的说明")], ["人员线样板 T-4"]))
ITEMS.append(item("P-T5", "peo", "招聘阶段没入职的候选人，面试和录用记录要不要迁入", "全部历史迁入的范围问题；含个人信息，按最小化原则倾向不迁。",
                  [("不迁，只迁已入职人员", "个人信息最少"), ("迁入", "可查历史招聘；须加个人信息保护措施")], ["人员线样板 T-5"]))
for n, (t, b) in enumerate(PEO_POINTS, 1):
    ITEMS.append(point(f"P-{n:02d}", "peo", t, b))

G.append({"id": "fin", "title": "财务线：报销从提交到应付（PRC-87）",
          "desc": "员工选项目与发票、占用预算后提交，部门负责人审批，财务核定；有员工借款先自动冲抵，净额确认为应付，再交统一过账。驳回或预算超额时自动释放已占用的预算与发票。",
          "diagram": FINANCE, "legend": LEGEND})
ITEMS.append(overall("F-0", "fin", "报销从提交到应付"))
ITEMS.append(item("F-Q1", "fin", "报销审批要几级", "",
                  [("部门负责人一级", "最快"), ("部门负责人；项目费用加项目经理；超过金额阈值加财务负责人（阈值在说明里写）", "管控更细；流程更长")], ["财务线样板 F-01"]))
ITEMS.append(item("F-Q2", "fin", "报销超过预算时，拦截还是只预警", "",
                  [("拦截：超预算不能提交", "控制最严；要先调预算"), ("只预警：能提交，审批人看到超额提示", "不卡业务；超支靠审批把关")], ["财务线样板 INV-102"]))
for n, (t, b) in enumerate(FIN_POINTS, 1):
    ITEMS.append(point(f"F-{n:02d}", "fin", t, b))

ITEMS.append(item("W-1", "conv", "每一步标明「在哪发生」，分四类，是否认可", "四类：用户在本系统操作；本系统自动；外部系统经集成完成（企业微信、U8C、银行、税务）；线下完成、本系统只登记结果与证据。外部和线下的步骤必须写明登记什么证据——它们是诉讼取证和历史迁入的关键。三组样板已按此标注，泳道图里用不同框线区分。",
                  [("认可这四类，铺开时每一步都标", "写进统一约定"), ("要改分法（在说明里写）", "按你的说明改约定")], ["用户问「流程节点哪些是在本系统，设计方位是如何考虑的」；closeout/CONVENTIONS.md where、evidence"]))
G.insert(0, {"id": "conv", "title": "共通：每一步在哪发生", "desc": "三个样板共用的一条写法：区分本系统操作、本系统自动、外部系统、线下四类。下面列出三个样板里的外部和线下步骤及登记的证据。", "diagram": "", "legend": ""})

INFO = [{"title": "三个样板里的外部与线下步骤", "lines": [
    "收入线（PRC-28）：编制交付物在设计软件里完成，本系统登记文件版本与哈希；送达、客户书面认可、退回都在线下，本系统登记回执、签认单或退回意见作为证据，并记签认日。",
    "人员线（PRC-83、137、84）：劳动合同线下签署，登记已签合同扫描件与签署日（以后接电子签可改为外部系统）；参保增员在人社、公积金网办（无接口，待核），登记增员回执与办理日；企业微信账号经接口开通，登记返回的账号号码与开通时间。",
    "财务线（PRC-87）：发票查验经税务平台，登记发票文件、查验结果与查验时刻；纸质原始凭证线下交财务，财务核定时在本系统登记签收。付款经银行、凭证推送 U8C 在其他流程里。"]},
    {"title": "怎么看这一页", "lines": [
    "三个架构组各出一个流程样板，看设计是否符合你的想法。每组一个标签页：上面是泳道图（每个角色一条泳道），下面先问整体结论，再问几道待定题，最后逐条列关键设计点和依据。",
    "每条设计点可选「符合」或「要改」，要改的在说明里写；都不预选。",
    "你的意见回来前，三组不铺开其余流程；意见回来后统一改约定再铺开。",
    "已由我裁定、不用你看的格式和接缝问题，记在 closeout/STRUCTURAL_ITEMS.md。"]}]

if __name__ == "__main__":
    data = {"items": ITEMS, "groups": [{k: g[k] for k in ("id", "title", "desc")} for g in G], "info": INFO, "side": []}
    diagrams = {g["id"]: {"svg": g["diagram"], "legend": g["legend"]} for g in G if g["diagram"]}
    tpl = (HERE / "u12.html").read_text()
    tpl = tpl.replace("<title>生效日期口径</title>", "<title>流程样板</title>").replace("<h1>员工与岗位的生效日期</h1>", "<h1>流程样板：三组各一（看设计是否符合你的想法）</h1>")
    tpl = re.sub(r'<p class="lede">.*?</p>', '<p class="lede">每题默认「暂不定」，不预选；看完泳道图后逐处给意见，按组提交。</p>', tpl, count=1)
    tpl = tpl.replace("</style>", ".diag{overflow-x:auto;background:var(--paper);border:1px solid var(--line);border-radius:8px;padding:8px;-webkit-overflow-scrolling:touch}.diag svg{display:block}.legend2{font-size:12px;color:var(--muted);margin:6px 0 0}\n</style>", 1)
    tpl = re.sub(r"const DATA = .*?;\n", lambda m: "const DATA = " + json.dumps(data, ensure_ascii=False) + ";\nconst DIAGRAMS = " + json.dumps(diagrams, ensure_ascii=False) + ";\n", tpl, count=1)
    anchor = "<p>${esc(g.desc)}</p></div></section>"
    assert anchor in tpl, "模板结构变了"
    tpl = tpl.replace(anchor, "<p>${esc(g.desc)}</p></div>${DIAGRAMS[g.id] ? `<div class=\"diag\">${DIAGRAMS[g.id].svg}</div><p class=\"legend2\">${esc(DIAGRAMS[g.id].legend)}</p>` : \"\"}</section>", 1)
    (HERE / "samples.html").write_text(tpl)
    (HERE / "archive" / "SAMPLES.json").write_text(json.dumps({"sheet": "SMP", **data}, ensure_ascii=False, indent=1))
    print(len(ITEMS), "items")
