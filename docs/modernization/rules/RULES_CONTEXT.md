# one-rules 速查（2026-10-15）

一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

设计原则（用户 2026-10-15，经 one-coordinator 转达）：以 AEC 行业项目制专业服务的通行做法为主（合同范本、收入准则、成熟产品），老系统生产数据只作参考与核对；确需复刻的老行为仍以生产数据为证。业务决策页做法见 `rules/decide/README.md`。

## 在途

| 项 | 状态 | 出处 |
|---|---|---|
| 财务口径决策单 46 题 + 9 条问财务的事实 | 在线页已交用户待提交（46 题全部待定，FD-02/03/08 已按推荐由 XD-08/09 先行实施）；A1、A10 待审计师书面意见 | [在线页](https://claude.ai/artifact/UfWUw5PuwVe6HzC5QRn8nB)，`rules/decide/README.md`，数据源 `r0/FINANCE_DECISIONS.md`，#176 |
| ARCH-D5 工时行类型 → 审批旗标映射、区域负责人来源 | Phase 2 前置项：方案页 6 题已交用户待提交（推荐：项目行归项目经理、售前归售前对象归属部门负责人、间接归直属上级、不保留混合、不加整单确认、负责人取组织架构）；用户定后写规则卡并请 one-glossary 改「区域负责人」 | [方案页](https://claude.ai/artifact/GGz4AuMS63ooyL85itojya)，`rules/decide/sheets/D5.json`，NEXT_BATCH_PLAN「Phase 2 前置项」，D-1a-127 |
| 规则卡补「类别」 | ADR-0040 第 2 步之后 | ADR-0040 |
| 规则文档瘦身对照表 | 待用户 | COORDINATOR_LEDGER「待用户」 |
| F-1 数据范围待定 7 题；「部门撤销或合并」规则卡 RULE-387 | 决策页已交用户待提交（F1-01、F1-02 须在 P5 合并前定）；规则卡已写，测试随 F-1 由测试代理写 | [决策页](https://claude.ai/artifact/4NMzvH2vxpJUYfckwrfcSJ)，`rules/decide/sheets/F1.json`，`rules/DATA_SCOPE_RULES.md`，`phase1a/cards/F-1-aec-check.md` |
