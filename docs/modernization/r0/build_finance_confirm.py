#!/usr/bin/env python3
"""由 FINANCE_QUESTIONS.md（问题的唯一数据源）与 FINANCE_ADR_POINTS.md（涉及财务的 ADR 要点）生成《财务确认单》FINANCE_CONFIRM.html（第 2 版，取代只含总账 12 题的第 1 版）。
题目、分组、题数只取自 FINANCE_QUESTIONS.md；本文件的 HINTS 只放页面呈现（白话背景、选项、小问拆分）与总账回填位置。
校验（不通过则生成失败）：HINTS 与清单题号一一对应；清单中来源含「GL:」的题都有回填位置，且 GL_FINANCE_DECISIONS.json 的每个待财务问题恰好被一个题（或小问）覆盖。
用法：python3 build_finance_confirm.py"""
import json, pathlib, re, sys

HERE = pathlib.Path(__file__).parent
QUESTIONS = HERE / "FINANCE_QUESTIONS.md"
ADR_POINTS = HERE / "FINANCE_ADR_POINTS.md"
GL_JSON = HERE.parent / "rules" / "GL_FINANCE_DECISIONS.json"
OUT = HERE / "FINANCE_CONFIRM.html"
VERSION = 3

# gl：回填到 GL_FINANCE_DECISIONS.json 的「事项#pending_finance 序号」；parts：一题拆成几问，各自回填
HINTS = {
    "FQ-01": {"opts": ["同意", "不同意（请说明）", "需讨论"],
              "ctx": "经营分析里的收入统一按不含税金额计，收入与成本配对时也用不含税额。"},
    "FQ-02": {"gl": "GL-026#0", "opts": ["某一时段内履行", "某一时点履行", "需审计师确认"],
              "ctx": "如果属于某一时段内履行，按收入准则，已履行部分的支出应计入当期损益，不能挂在在建成本（合同履约成本）上等客户确认。建议请审计师一并确认。"},
    "FQ-03": {"gl": "D17-6#0", "opts": ["收付也按纳税义务时点", "收付只在开票时记（请说明原因）", "需与税务顾问确认"],
              "ctx": "权责账簿按纳税义务发生时点把待转销项转为销项。若收付账簿只在开票时记销项，先收款后开票时，法定账上的应交增值税会低于当月申报数。"},
    "FQ-04": {"ph": "请逐项说明：贡献毛利扣哪些；净服务收入怎么算；新签合同额是否扣转付成本"},
    "FQ-05": {"ph": "请说明采用的账簿、贡献毛利的层级和四类分摊规则"},
    "FQ-06": {"gl": "GL-026#1", "ph": "如：未签约的项目年底统一转费用，大部分能签约",
              "ctx": "新系统规定：签约前投入只有与特定合同直接相关、并预期能收回时才挂资产，否则当期费用化。请说明现行做法和大致情况。"},
    "FQ-07": {"gl": "GL-026#2", "opts": ["有（请说明哪类项目）", "没有", "不确定"],
              "ctx": "超过一年的要在资产负债表列「其他非流动资产」，一年内的列「存货」。"},
    "FQ-08": {"gl": "D17-4#0", "ph": "请说明使用场景",
              "ctx": "U8C 主营业务成本下有「差异调整」明细，新系统需要知道它的用途，才能决定保留、改名还是合并。"},
    "FQ-09": {"gl": "D17-9#0", "opts": ["没有重复", "有重复（请说明大致期间和范围）", "不确定"],
              "ctx": "新系统里预付款只冲一次，这里只需知道历史上有没有重复，用于核对期初。"},
    "FQ-10": {"opts": ["单据日期", "费用所属期间", "凭证期间", "其他（请说明）"],
              "ctx": "权责费用与收付「费用支付」之间的差额会列入口径差异清单；居间费「按比例预提、年底一次调整」的做法也请一并说明。"},
    "FQ-11": {"ph": "请给出采用的账龄分段，如：0–45 天、46–90 天……"},
    "FQ-12": {"ph": "请说明应收以哪张表为准、红冲怎么处理"},
    "FQ-13": {"ph": "请分别说明分子、天数基准、是否含商业承兑汇票与坏账"},
    "FQ-14": {"ph": "请分别说明工抵、商业承兑汇票、保理是否计入回款，以及计入的时点"},
    "FQ-15": {"ph": "请说明「减值计划」与「已计提」的关系"},
    "FQ-16": {"parts": [
        {"label": "个人账户（「个独」）与工抵卡的收付，是否计入公司现金流量？", "opts": ["计入", "不计入", "需讨论"]},
        {"label": "其他货币资金下哪些明细是受限的（不能随时支用）？", "gl": "GL-051#0", "ph": "列明细名称即可，如：保函保证金、银行承兑保证金"}],
        "ctx": "新系统的现金及现金等价物 = 库存现金 + 银行存款 + 可随时支用的其他货币资金；保证金等受限资金和理财、货币基金不算。"},
    "FQ-17": {"gl": "GL-051#1", "opts": ["需要", "不需要", "需审计师确认"],
              "ctx": "U8C 现在的收付现金流量表只取库存现金和银行存款，新口径会把可随时支用的其他货币资金也算进来。"},
    "FQ-18": {"gl": "D17-12#0", "deferred": "需要先从 U8C 查出单账簿凭证的分类清单，清单出来后另行发送，本单不用答。"},
    "FQ-19": {"parts": [
        {"label": "新系统起步前，U8C 收付账套能否结账到起步月的上一个月？", "gl": "GL-083#0", "opts": ["能", "不能（请说明最早能结到哪个月）"]},
        {"label": "期初余额表由哪个岗位签字确认？", "gl": "GL-083#1", "ph": "填岗位，如：财务负责人"}],
        "ctx": "新系统从某年 1 月起步，期初余额取 U8C 收付账套上年末余额；如果那时还没结账，先用未结余额作暂定期初，结账后再调整。"},
    "FQ-21": {"opts": ["符合，与总账结论一致", "不符合或不一致（请说明应怎么做）", "需审计师确认"],
              "ctx": "新系统按「客户确认」确认收入；收到预付款、开出首付款发票时都只记合同负债，履约并经客户确认后才转收入。老系统的预付款条款不经交付验收就确认收入，与此不同。"},
    "FQ-22": {"parts": [
        {"label": "手工录入凭证（GL）是否含业务事项？", "opts": ["含（请说明哪类业务）", "不含", "不确定"]},
        {"label": "自动转账凭证（TR，期末结转）是否含业务事项？", "opts": ["含（请说明哪类业务）", "不含", "不确定"]},
        {"label": "固定资产凭证（FA）是否含业务事项？", "opts": ["含（请说明哪类业务）", "不含", "不确定"]}],
        "ctx": "这里的「业务事项」指收入、成本调整、采购、付款等应在业务系统发起的事。含业务事项的类别，新系统会把它们改成从业务单据生成凭证。"},
    "FQ-20": {"parts": [
        {"label": "薪酬、银行流水、折旧等数据通常在次月第几个工作日到齐？", "gl": "GL-123#0", "num": "次月第几个工作日"},
        {"label": "过渡期间，U8C 收付账套能否每月按时结账？", "gl": "GL-123#1", "opts": ["能", "有条件（请说明）", "不能（请说明）"]}],
        "ctx": "新系统每月按法人关账，关账后不反结账，更正记在下个开放月；关账日取决于这些数据几号到齐。"},
    "FQ-23": {"opts": ["统一四舍五入到分", "凭证与分摊用四舍六入五成双（银行家舍入）", "个别场景另定（请列出）"],
              "ctx": "新系统所有金额在形成时舍入到分，口径集中在一处。报价已定四舍五入到分。老系统有三处向下取整（差旅补贴、年终奖、进度百分比），统一后结果会与老系统差几分。"},
    "FQ-24": {"opts": ["最大余数法", "全部记入金额最大的一行", "全部记入最后一行", "其他（请说明）"],
              "ctx": "例：100.00 元平均拆三份得 33.33 × 3 = 99.99，差 1 分。最大余数法把差额逐分给小数部分最大的几份，结果与行的顺序无关，每份偏差小于 1 分；后两种做法在行数多时，一行可能偏差好几分，记入最后一行时重跑还可能因顺序不同得出不同结果。"},
    "FQ-25": {"opts": ["过程中不舍入，只在形成金额时舍入；存库比率 10 位、费率 6 位", "比率先舍入到 4 位再参与计算", "其他（请说明）"],
              "ctx": "日薪 ÷ 7.5 小时得到的时薪除不尽。老系统比率入库 8 位、计算用 10 位，两者不一致。"},
    "FQ-26": {"opts": ["合计 = 各行舍入后之和", "合计先算再舍入，差额倒挤到某一行"],
              "ctx": "取各行之和时，凭证借贷必然平衡，页面、导出和凭证的合计也一致。老系统结转凭证借方先求和再舍入、贷方逐行舍入，凭证会差几分。"},
    "FQ-27": {"parts": [
        {"label": "不含税额与税额怎么算？", "opts": ["不含税 = 含税 ÷ (1+税率) 取到分，税额 = 含税 − 不含税", "税额 = 不含税 × 税率 取到分", "其他（请说明）"]},
        {"label": "按行算还是按整张单据算？", "opts": ["按行", "按整张单据"]},
        {"label": "新系统与数电发票的税额差多少以内视为一致？", "ph": "请给出现行开票误差容忍的数值或依据；如零容忍请写「零」"}],
        "ctx": "第一种算法与老系统一致，含税额 = 不含税额 + 税额恒成立。按行计算时，整张发票的税额与按合计计算的税额可能差几分。"},
    "FQ-28": {"parts": [
        {"label": "归集守恒、分摊守恒是否零容差？", "opts": ["零容差，差 1 分也算不平", "允许若干分（请写数值）"]},
        {"label": "部分退回、部分冲销时怎么拆？", "opts": ["按原分录比例拆出冲销额，舍入差按 FQ-24，累计不超过原额", "其他（请说明）"]}],
        "ctx": "老系统有四种容差：零容差、行数 × 0.005 + 0.005、1.00 元、前端 0.005。新系统的拆分本身保证守恒。"},
    "FQ-29": {"opts": ["不反结账，更正记在开放期间并标注原期间", "保留反结账，须财务负责人审批并留痕"],
              "ctx": "现有材料里两种说法并存。新系统的决定是已过账事实只能冲销重做，关账后的更正落在开放期间。"},
    "FQ-30": {"opts": ["不需要，自然年 12 个期间", "需要（请说明用途）"],
              "ctx": "U8C 现在没有调整期；老系统个别规则提到第 13 期，与此冲突。"},
    "FQ-31": {"parts": [
        {"label": "工时成本怎么算？", "opts": ["工时 × 时薪（时薪 = 日薪 ÷ 标准日工时）", "人天 × 日薪（人天 = 工时 ÷ max(当日总工时, 7.5)）", "其他（请说明）"]},
        {"label": "标准日 7.5 小时是否按法人区分？", "opts": ["全公司统一", "按法人区分（请说明）"]}],
        "ctx": "两种算法在加班或不足 7.5 小时的日子结果不同。"},
}


def parse(md=None):
    """返回 [(分组名, [{fq, q, src, gl_src}])]，只取 FINANCE_QUESTIONS.md 的内容。"""
    md = md or QUESTIONS.read_text(encoding="utf-8")
    groups, cur = [], None
    for line in md.splitlines():
        m = re.match(r"^## (\d+)\.\s*(.+)$", line)
        if m:
            cur = (f"{m.group(1)}. {m.group(2).strip()}", [])
            groups.append(cur)
            continue
        m = re.match(r"^\| (FQ-\d+) \| (.+?) \| (.+?) \| (.+?) \|$", line)
        if m and cur:
            src = m.group(3).strip()
            cur[1].append({"fq": m.group(1), "q": m.group(2).strip(), "src": src, "gl_src": "GL:" in src})
    return [g for g in groups if g[1]]


def parse_adrs():
    """返回 (说明段, [{adr, title, status, one, points, watch}])；FINANCE_ADR_POINTS.md 不存在时为空。"""
    if not ADR_POINTS.exists():
        return "", []
    intro, adrs, cur = [], [], None
    for line in ADR_POINTS.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^## (ADR-\d{4}) · (.+?) · (.+)$", line)
        if m:
            cur = {"adr": m.group(1), "title": m.group(2).strip(), "status": m.group(3).strip(), "one": "", "points": [], "watch": []}
            adrs.append(cur)
            continue
        if line.startswith("## "):
            cur = "stop"
            continue
        if cur is None:
            if line.strip() and not line.startswith("#"):
                intro.append(line.strip())
        elif cur != "stop":
            if line.startswith("一句话："):
                cur["one"] = line[len("一句话："):].strip()
            elif line.startswith("留意："):
                cur["watch"].append(line[len("留意："):].strip())
            elif line.startswith("- "):
                cur["points"].append(line[2:].strip())
    for a in adrs:
        assert a["points"], f"{a['adr']} 没有要点"
    return " ".join(intro), adrs


def gl_targets(h):
    """一个题的全部总账回填位置。"""
    return ([h["gl"]] if h.get("gl") else []) + [p["gl"] for p in h.get("parts", []) if p.get("gl")]


def validate(groups):
    errs = []
    qs = [q for _, g in groups for q in g]
    fqs = [q["fq"] for q in qs]
    if len(set(fqs)) != len(fqs):
        errs.append("FINANCE_QUESTIONS.md 中题号重复")
    for f in set(fqs) ^ set(HINTS):
        errs.append(f"{f}：清单与 HINTS 不一致（只在一边出现）")
    for q in qs:
        h = HINTS.get(q["fq"], {})
        if q["gl_src"] != bool(gl_targets(h)):
            errs.append(f"{q['fq']}：来源「{q['src']}」与总账回填位置不一致")
    gl = json.loads(GL_JSON.read_text(encoding="utf-8"))["decisions"]
    pending = {f"{k}#{i}" for k, x in gl.items() for i, _ in enumerate(x.get("pending_finance", []))}
    mapped = [t for h in HINTS.values() for t in gl_targets(h)]
    for t in set(mapped) - pending:
        errs.append(f"回填位置 {t} 在 GL_FINANCE_DECISIONS.json 中不存在")
    for t in pending - set(mapped):
        errs.append(f"GL 待财务问题 {t} 没有被任何题覆盖")
    for t in {t for t in mapped if mapped.count(t) > 1}:
        errs.append(f"GL 待财务问题 {t} 被多个题覆盖")
    if errs:
        sys.exit("财务确认单校验失败：\n" + "\n".join(errs))


def page(groups):
    adr_intro, adrs = parse_adrs()
    data = [[name, [dict(q, **{k: v for k, v in HINTS[q["fq"]].items()}) for q in g]] for name, g in groups]
    n = sum(1 for _, g in data for q in g if not q.get("deferred"))
    style = (HERE / "finance_confirm.css").read_text(encoding="utf-8")
    js = (HERE / "finance_confirm.js").read_text(encoding="utf-8")
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>财务确认单</title>
<style>
{style}
</style>
</head>
<body>
<div class="wrap">
<header>
  <p class="ver">第 {VERSION} 版 · 取代第 2 版：新增 FQ-23..FQ-31（金额舍入、拆分、精度、容差与期间），FQ-01..FQ-22 与第 2 版相同。第 2 版已填的答复请照抄到本版，或把第 2 版导出的文件与本版一并发回。</p>
  <h1>财务确认单</h1>
  <p class="lede">FORCITIS one 新系统将接管总账和经营分析，同时出权责口径和收付口径两套账（收付口径仍是法定账）。口径规则已经由公司定下来了，{"请先逐份确认涉及财务的 " + str(len(adrs)) + " 份决定，再答复下面 " if adrs else "下面 "}{n} 个问题需要财务按实际情况答复，新系统据此定关账日、期初、记账规则和经营分析指标的算法。</p>
  <div class="howto">
    <div><b>怎么填</b>：用浏览器打开本文件即可，不需要登录，也不联网。能选的先选，再在说明栏补充；不确定的选「需确认」或写下原因。填到一半关掉，下次在同一台电脑、同一个浏览器打开会接着填。</div>
    <div><b>怎么交回</b>：填完点页面底部「导出答复」，把下载的文件发回给项目负责人。浏览器不让下载时，点「复制答复」，把文字粘贴到邮件或微信里发回。</div>
    <div><b>不需要</b>写金额、凭证号或员工姓名，写岗位和情况即可。</div>
  </div>
  <div class="meta">
    <label class="f" for="who">答复人岗位<input id="who" type="text" placeholder="如：财务经理"></label>
    <label class="f" for="when">答复日期<input id="when" type="date"></label>
  </div>
</header>
<main id="qs"></main>
</div>
<div class="bar"><div class="in">
  <span class="prog" id="prog">已答 0 / {n + len(adrs)}</span>
  <button type="button" id="copy">复制答复</button>
  <button type="button" class="primary" id="exp">导出答复</button>
  <span id="msg" role="status"></span>
  <textarea id="out" hidden readonly aria-label="答复文本"></textarea>
</div></div>
<script>
const VERSION = {VERSION};
const GROUPS = {json.dumps(data, ensure_ascii=False)};
const ADRS = {json.dumps(adrs, ensure_ascii=False)};
const ADR_INTRO = {json.dumps(adr_intro, ensure_ascii=False)};
{js}
</script>
</body>
</html>
"""


if __name__ == "__main__":
    groups = parse()
    validate(groups)
    OUT.write_text(page(groups), encoding="utf-8")
    print(f"{OUT.name}：{sum(len(g) for _, g in groups)} 题，第 {VERSION} 版")
