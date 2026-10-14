#!/usr/bin/env python3
"""OA_PROCESS_INVENTORY.md §3 模板分类表的生成器（OA 流程盘点，2026-10-14）。

输入：runtime/a8_templates.tsv（生产只读采集）+ 下面的 CLASS 分类表。
输出：改写 OA_PROCESS_INVENTORY.md 中 <!-- BEGIN:templates --> 与 <!-- END:templates --> 之间的内容。
校验：近 365 天有实例或有在途实例的模板必须全部分类，否则失败；分类表里不得有数据中不存在的模板。
运行：python3 build_oa_inventory.py（在 docs/modernization 下）
"""
import csv, sys, collections

# 分类：迁入 / 视情况 / 留在 OA / 已停用（只迁历史）
# 规则（GUIDING_PRINCIPLES §9a，用户 2026-10-14：「确保权责业务事实全部在新系统」）：
#   产生、变更或审批权责事实的流程一律「迁入」；只引用这些事实、不产生事实的才可能「视情况」。
# 字段：分类, 权责事实（无则 —）, PSA 模块（BC）, 蓝图 BC（ADR-0017；未实现标注）, 阶段草案, 说明
M, S, O, H = "迁入", "视情况", "留在 OA", "已停用"
CLASS = {
    # —— 6a 合同与收入（行级强耦合，同一阶段切换）——
    "销售合同新增": (M, "合同签订", "contract.baseline", "BC-02", "6a", "合同与付款条款的唯一来源目前在 A8（formmain_0374 / formmain_0051 / formson_0296、0376），PSA 经跨库视图读取"),
    "销售合同变更": (M, "合同变更", "contract.changeorder", "BC-02", "6a", "变更不得覆盖原合同事实，按版本追加"),
    "销售合同归档": (M, "合同签订（回签）", "contract.baseline", "BC-02", "6a", "并入合同生命周期；PSA 收入确认要求「合同已回签」"),
    "项目立项申请": (M, "合同签订（项目基线）", "delivery.execution / contract.baseline", "BC-04、BC-02", "6a", "生成项目档案（formmain_0051），是所有事实的项目坐标"),
    "项目档案变更": (M, "合同变更（条款、金额、交付部门）", "contract.baseline", "BC-02", "6a", "180 天 825 单，改的是项目与条款主档"),
    "客户产值确认单（新）": (M, "交付与收入确认", "receivables.wip（收入确认）", "BC-11", "6a", "方言「产值确认单」= 收入确认单；2026 年 1,377 单；A8 凭证插件已据此生成 U8C 凭证 2,520 单"),
    "产值坏账申请": (M, "收入调整 / 合同资产减值", "receivables.wip", "BC-11", "6a", "会计含义（收入冲减还是合同资产减值）待财务确认"),
    "项目结算申请": (M, "合同最终计量（交易价格确定）", "contract.changeorder / receivables.wip", "BC-02、BC-11", "6a", "竣工结算；可能触发收入调整"),
    "项目结项申请": (M, "交付终止（关闭后不得再归集成本）", "delivery.execution", "BC-04", "6a", "改变成本可归集状态，按交付事实处理；不等于财务结清"),
    # —— 6b 开票与收款 ——
    "项目开票申请": (M, "开票与应收", "receivables.wip", "BC-10", "6b", "开票与 WIP 同一事务边界（ADR-0016 第 5 条）；A8 销项开票插件直连税务数电平台"),
    "首付款开票申请": (M, "开票与应收（预收）", "receivables.wip", "BC-10", "6b", "并入统一开票，作为开票原因 / 类型"),
    "发票退票申请": (M, "开票更正（红冲）", "receivables.wip", "BC-10", "6b", "须保留原票、红字票、重开票的冲销链"),
    "回款单新增": (M, "回款", "receivables.collection", "BC-12", "6b", "A8 凭证插件生成 U8C 凭证 2,839 单"),
    "预收款单新增": (M, "回款（预收 / 合同负债）", "receivables.collection", "BC-12", "6b", "近一年 1 单；并入回款的预收类型"),
    "退款单新增": (M, "回款冲销（退款）", "receivables.collection", "BC-12", "6b", "低频但财务关键"),
    "应收坏账申请流程": (M, "应收减值 / 核销", "receivables.collection", "BC-12", "6b", "计提、转回、核销须分开表达"),
    # —— 6c 费用、借还款、预提与付款 ——
    "日常费用报销单": (M, "费用", "expense.spend", "BC-06（SD-5b）", "6c", "A8 凭证插件生成凭证 5,648 单"),
    "差旅费用报销单": (M, "费用（差旅）", "expense.spend", "BC-06（SD-5b）", "6c", "PSA 已有差旅申请与分贝通（Phase 3），报销仍在 A8"),
    "业务招待申请表": (M, "费用（事前审批，决定可报销额度）", "expense.spend", "BC-06（SD-5b）", "6c", "事前申请决定后续费用可否列支，归入费用事实链"),
    "业务招待费用报销单": (M, "费用", "expense.spend", "BC-06（SD-5b）", "6c", ""),
    "借款申请单新增": (M, "付款（员工借款）", "expense.spend", "BC-06（SD-5b）", "6c", "员工往来；ADR-0016 过账交易类型未列，须补"),
    "还款申请单新增": (M, "回款（员工还款）", "expense.spend", "BC-06（SD-5b）", "6c", "同上"),
    "预付款核销申请": (M, "应付核销", "expense.spend / 应付（未实现 BC）", "未覆盖", "6c", ""),
    "付款申请单-公共": (M, "付款", "应付与付款（未实现 BC）", "未覆盖", "6c", "蓝图 BC-12 只覆盖应收与现金；应付须新增 BC，待用户决定"),
    "付款申请单_外部代发": (M, "付款", "应付与付款（未实现 BC）", "未覆盖", "6c", "与「付款申请单-公共」共用表单"),
    "付款申请单-人事": (M, "付款（薪酬类）", "应付与付款（未实现 BC）", "未覆盖", "6c", "PSA 视图 psa_oa_payment_detail 已读取"),
    "付款申请单-财务": (M, "付款", "应付与付款（未实现 BC）", "未覆盖", "6c", ""),
    "特殊款项付款申请单": (M, "付款", "应付与付款（未实现 BC）", "未覆盖", "6c", ""),
    "BU预提费用明细表": (M, "费用预提", "cost.costing / accounting.ledger", "BC-07、BC-14", "6c", "A8 直接抛 U8C 凭证；PSA 视图 psa_oa_accrual_detail 作人工重分类贷方来源"),
    # —— 6d 分包与采购 ——
    "项目委外采购申请": (M, "分包（承诺）", "procurement.subcontracting", "BC-07（SD-6c）", "6d", "方言「委外」= 分包，待确认"),
    "项目委外采购合同": (M, "分包合同签订", "procurement.subcontracting", "BC-07（SD-6c）", "6d", ""),
    "项目委外交付确认单": (M, "分包成本确认（应付）", "procurement.subcontracting", "BC-07（SD-6c）", "6d", "确认即形成分包成本与应付"),
    "采购合同申请": (M, "采购合同签订（应付来源）", "procurement.subcontracting（非项目采购须扩展）", "未覆盖", "6d", "非项目采购是否纳入 FORCITIS one 待用户决定"),
    "采购合同变更": (M, "采购合同变更", "procurement.subcontracting（非项目采购须扩展）", "未覆盖", "6d", "A8 分类挂在「人力资源」下，疑为误挂"),
    # —— 6e 薪酬成本 ——
    "薪资计算审核表": (M, "人工成本（职工薪酬）", "organization.payroll", "未覆盖（蓝图排除）", "6e", "工时成本单价 psa_daily_rate 与零工时薪资成本都读它（formmain_0858）；范围待用户决定"),
    "税金计算": (M, "人工成本（个税、社保代扣）", "organization.payroll", "未覆盖（蓝图排除）", "6e", "与算薪同一链条"),
    # —— 并入已有阶段 ——
    "项目预算编制": (M, "—（计划，不过账；PSA 已有 PM_BUDGET，双入口须消除）", "delivery.execution", "BC-04", "Phase 2", "PSA 预算审批 180 天 18 单，A8 仍 193 单"),
    "项目预算变更": (M, "—（同上）", "delivery.execution", "BC-04", "Phase 2", ""),
    "新增报价": (M, "—（报价承诺，不过账；PSA 已有 OPP_QUOTATION）", "presales.quoting", "BC-01", "Phase 1b", "最后一单 2026-09-23；PSA→A8 报价档案同步在运行，A8 模板应停用"),
    # —— 视情况：只引用事实、不产生事实 ——
    "商机跟进": (S, "—", "presales.customer", "BC-01", "6f", "客户经营；建议随 6f 迁入"),
    "新建商机": (S, "—", "presales.quoting", "BC-01", "6f", "商机由 A8 同步进 PSA（psa_crm_opportunity）"),
    "商机信息变更": (S, "—", "presales.quoting", "BC-01", "6f", ""),
    "商机推荐流程": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "新建线索": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "线索跟进": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "客户档案新增": (S, "—（主数据，开票购方信息来源）", "presales.customer", "BC-01", "6f", "开票（6b）需要购方信息，6b 前须定客户主档归属"),
    "客户档案变更": (S, "—（主数据）", "presales.customer", "BC-01", "6f", ""),
    "客户档案合并": (S, "—（主数据）", "presales.customer", "BC-01", "6f", ""),
    "集团档案新增": (S, "—（主数据）", "presales.customer", "BC-01", "6f", ""),
    "集团档案变更": (S, "—（主数据）", "presales.customer", "BC-01", "6f", ""),
    "集团档案合并": (S, "—（主数据）", "presales.customer", "BC-01", "6f", ""),
    "联系人档案新增": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "联系人档案变更": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "客户拜访计划新增": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "客户拜访计划变更": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "客户拜访结果反馈": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "竞争对手档案新增": (S, "—", "presales.customer", "BC-01", "6f", ""),
    "每周CommitCall承诺单": (S, "—（销售预测）", "operations.planning", "未覆盖", "6f", "在途 1,212 单，疑似僵尸"),
    "供应商档案新增": (S, "—（主数据，付款对象）", "procurement.subcontracting", "BC-07（SD-6c）", "6d", "6c / 6d 前须定供应商主档归属"),
    "供应商信息变更": (S, "—（主数据）", "procurement.subcontracting", "BC-07（SD-6c）", "6d", ""),
    "采购请购": (S, "—（请购不形成事实）", "procurement.subcontracting", "未覆盖", "6d", ""),
    "IT资产采购申请": (S, "—（事前审批；付款走付款申请单）", "—", "未覆盖", "—", "固定资产事实在 U8C 固定资产模块（FA），见矩阵"),
    "信息服务费备案申请": (S, "—（事前备案，付款另走付款申请）", "expense.spend", "BC-06（SD-5b）", "6c", "若备案决定可付金额，应改判迁入"),
    "信息服务费备案变更申请": (S, "—", "expense.spend", "BC-06（SD-5b）", "6c", ""),
    "现金流预测": (S, "—（预测）", "operations.planning", "未覆盖", "6f", ""),
    "项目组承诺目标新增": (S, "—（经营目标）", "operations.planning", "未覆盖", "6f", ""),
    "交付中心承诺目标新增": (S, "—（经营目标）", "operations.planning", "未覆盖", "6f", ""),
    "客户中心承诺目标新增": (S, "—（经营目标）", "operations.planning", "未覆盖", "6f", ""),
    "营销人员承诺目标新增": (S, "—（经营目标）", "operations.planning", "未覆盖", "6f", ""),
    "项目组预算目标新增": (S, "—（经营预算）", "operations.budgeting", "未覆盖", "6f", ""),
    "交付中心预算目标新增": (S, "—（经营预算）", "operations.budgeting", "未覆盖", "6f", ""),
    "客户中心预算目标新增": (S, "—（经营预算）", "operations.budgeting", "未覆盖", "6f", ""),
    "营销人员预算目标新增": (S, "—（经营预算）", "operations.budgeting", "未覆盖", "6f", ""),
    "项目阶段评审": (S, "—", "delivery.execution", "BC-04", "Phase 2", "PSA 已有 PM_STAGE_REVIEW（0 实例）"),
    "项目阶段评审提醒": (S, "—", "delivery.execution", "BC-04", "Phase 2", ""),
    "项目成果内审": (S, "—（质量）", "delivery.acceptance", "无直接对应", "Phase 2", ""),
    "项目成果内审提醒": (S, "—", "delivery.acceptance", "无直接对应", "Phase 2", "已停用，在途 53"),
    "项目启动会": (S, "—", "delivery.execution", "BC-04", "Phase 2", ""),
    "项目满意度调查": (S, "—", "delivery.acceptance", "无直接对应", "Phase 2", ""),
    "项目成果外发限制申请": (S, "—", "delivery.execution", "BC-04", "—", ""),
    "客诉申请单": (S, "—", "delivery.acceptance", "无直接对应", "—", "近一年 0 单，在途 1"),
    "调薪申请单": (S, "—（改变薪酬标准，不直接形成事实）", "organization.payroll", "未覆盖", "6e", "随 6e 决定"),
    "月度考勤确认表": (S, "—（算薪输入）", "organization.payroll", "未覆盖", "6e", "随 6e 决定"),
    "合伙人注资退回申请单": (S, "权益类（不在 PSA 权责事实清单内）", "—", "未覆盖", "—", "3 单；是否纳入待用户决定"),
    "月度评估表-交付人员": (S, "—（绩效，可能影响奖金计提）", "organization.incentive", "未覆盖", "—", ""),
    "月度评估表-BU支持人员": (S, "—（同上）", "organization.incentive", "未覆盖", "—", ""),
    "月度评估表-总部人员": (S, "—（同上）", "organization.incentive", "未覆盖", "—", ""),
    "需求工单": (O, "—", "—", "—", "—", "IT 需求"),
    # —— 留在 OA：纯行政、人事、假勤 ——
    "用印申请": (O, "—", "—", "—", "—", "行政；报价用印登记与 PSA 报价同步相关，用印本身不产生事实"),
    "合同借阅申请": (O, "—", "—", "—", "—", ""),
    "外勤申请表": (O, "—", "—", "—", "—", "考勤"),
    "年假申请表": (O, "—", "—", "—", "—", "假勤"),
    "日常请假申请表": (O, "—", "—", "—", "—", "假勤"),
    "特殊假别申请表": (O, "—", "—", "—", "—", "假勤"),
    "销假申请表": (O, "—", "—", "—", "—", "假勤"),
    "补卡申请表": (O, "—", "—", "—", "—", "假勤"),
    "年假延期申请": (O, "—", "—", "—", "—", "已停用，在途 2"),
    "节前提前下班考勤调整": (O, "—", "—", "—", "—", ""),
    "员工入职": (O, "—", "organization.workforce（只读快照）", "BC-P8", "—", "组织主数据仍以 A8 为源（已定，COLUMN_OWNERSHIP）"),
    "员工离职申请": (O, "—", "organization.workforce（只读快照）", "BC-P8", "—", ""),
    "员工交接表": (O, "—", "—", "—", "—", ""),
    "录用审批表": (O, "—", "—", "—", "—", ""),
    "用人申请表": (O, "—", "—", "—", "—", ""),
    "应聘登记表": (O, "—", "—", "—", "—", ""),
    "面试评估表": (O, "—", "—", "—", "—", "已停用，在途 7"),
    "转正申请表": (O, "—", "—", "—", "—", "已停用，在途 7"),
    "试用期员工转正申请": (O, "—", "—", "—", "—", ""),
    "试用期评估流程(第一个月)": (O, "—", "—", "—", "—", ""),
    "试用期评估流程(第二个月)": (O, "—", "—", "—", "—", ""),
    "人事异动表": (O, "—（改变成本归属部门，经 A8 组织同步进入）", "organization.workforce（只读快照）", "BC-P8", "—", ""),
    "劳动合同续签意向征询单": (O, "—", "—", "—", "—", ""),
    "停薪留职申请单": (O, "—", "—", "—", "—", ""),
    "成长记录维护": (O, "—", "—", "—", "—", ""),
    "周年贺卡": (O, "—", "—", "—", "—", ""),
    "感恩节活动": (O, "—", "—", "—", "—", "已停用，在途 75"),
    "创新项目发布申请": (O, "—", "—", "—", "—", ""),
    "IT资产内购申请单": (O, "—", "—", "—", "—", ""),
    "IT资产报修申请": (O, "—", "—", "—", "—", ""),
    "IT资产调拨申请": (O, "—", "—", "—", "—", ""),
    "IT资产领用申请": (O, "—", "—", "—", "—", ""),
    "IT资产信息变更": (O, "—", "—", "—", "—", ""),
    "IT资产报废回收流程": (O, "—（资产处置的会计处理在 U8C 固定资产）", "—", "—", "—", ""),
    # —— 已停用：只迁历史 ——
    "项目工时填报单": (H, "工时（历史）", "cost.timesheet", "BC-06（SD-5a）", "Phase 2", "2026-04-30 起停用，已由 PSA 工时取代；历史 386,965 单，在途 145 须排空"),
    "商机工时填报单": (H, "工时（历史）", "cost.timesheet", "BC-06（SD-5a）", "Phase 2", "2026-04-28 起停用；历史 13,274 单"),
    "项目产值确认单（老）": (H, "收入确认（历史）", "receivables.wip", "BC-11", "6a", "2022-06 起停用，仍有 71 个在途实例须判定（僵尸 / 待补结论）"),
}

ORDER = {M: 0, S: 1, O: 2, H: 3}

def main():
    rows = []
    with open("runtime/a8_templates.tsv", encoding="utf-8") as f:
        lines = [l for l in f if not l.startswith("#")]
    for r in csv.DictReader(lines, delimiter="\t"):
        for k in ("total", "last_180d", "last_365d", "year_2026", "running"):
            r[k] = int(r[k])
        rows.append(r)
    active = [r for r in rows if r["last_365d"] > 0 or r["running"] > 0]
    names = {r["template_name"] for r in rows}
    missing = sorted({r["template_name"] for r in active} - CLASS.keys())
    unknown = sorted(CLASS.keys() - names)
    if missing or unknown:
        sys.exit(f"未分类的活跃模板：{missing}\n分类表中数据里不存在的模板：{unknown}")
    agg = collections.OrderedDict()   # 同名模板（同一表单多个模板）合并计数
    for r in active:
        a = agg.setdefault(r["template_name"], dict(r, ids=[]))
        if a is not r and a["ids"]:
            for k in ("total", "last_180d", "last_365d", "year_2026", "running"):
                a[k] += r[k]
            a["first_use"] = min(a["first_use"], r["first_use"])
            a["last_use"] = max(a["last_use"], r["last_use"])
        a["ids"].append(r["template_id"])
    items = sorted(agg.values(), key=lambda r: (ORDER[CLASS[r["template_name"]][0]], CLASS[r["template_name"]][4], -r["last_180d"]))
    cnt = collections.Counter(CLASS[r["template_name"]][0] for r in items)
    n180 = collections.Counter()
    for r in items:
        n180[CLASS[r["template_name"]][0]] += r["last_180d"]
    out = [f"近 365 天有实例或有在途实例的模板共 **{len(items)}** 个（数据：`runtime/a8_templates.tsv`，生成：`build_oa_inventory.py`）。",
           "", "| 分类 | 模板数 | 近 180 天实例 |", "|---|---|---|"]
    for c in (M, S, O, H):
        out.append(f"| {c} | {cnt[c]} | {n180[c]:,} |")
    out += ["", "| # | A8 模板 | A8 分类 | 分类 | 权责事实 | PSA 模块（BC） | 蓝图 BC | 阶段 | 近 180 天 | 2026 年 | 累计 | 在途 | 最近使用 | 说明 |",
            "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for i, r in enumerate(items, 1):
        c, fact, bc, bp, ph, note = CLASS[r["template_name"]]
        out.append(f"| {i} | {r['template_name']} | {r['category']} | **{c}** | {fact} | `{bc}` | {bp} | {ph} | {r['last_180d']:,} | {r['year_2026']:,} | {r['total']:,} | {r['running']:,} | {r['last_use']} | {note} |".replace("`—`", "—"))
    md = open("OA_PROCESS_INVENTORY.md", encoding="utf-8").read()
    b, e = "<!-- BEGIN:templates -->", "<!-- END:templates -->"
    i, j = md.index(b) + len(b), md.index(e)
    open("OA_PROCESS_INVENTORY.md", "w", encoding="utf-8").write(md[:i] + "\n" + "\n".join(out) + "\n" + md[j:])
    print("templates:", len(items), dict(cnt), "last_180d:", dict(n180))

if __name__ == "__main__":
    main()
