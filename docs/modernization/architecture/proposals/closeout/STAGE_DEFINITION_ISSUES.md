# 阶段对齐：须改阶段定义或须用户判断的事项（交 one-coordinator）

用户 2026-10-16 决定按 BIZBOK v11 §3.4 逐层对齐（阶段 → 能力 → 信息 → 干系人）；本表只收要改价值流阶段定义、或须用户判断的事项，不自定。

| # | 来源 | 价值流 / 阶段 | 问题 | 组的建议 | 状态 |
|---|---|---|---|---|---|
| SD-01 | revenue STAGE_ALIGN.md（8a2e7291） | VS-02.1 概念 | 入口条件是否也要求发包人资料「已提供」 | 见 revenue/STAGE_ALIGN.md | 待交 |
| SD-02 | revenue STAGE_ALIGN.md（8a2e7291） | VS-04 合同签订 → 最终价款确认 | 两段之间没有「履约」阶段，索赔、变更、任务订单都发生在这一段 | 见 revenue/STAGE_ALIGN.md | 待交 |
| SD-03 | finance STAGES.md ST-1（06e6c36b） | VS-08 报销垫付费用 | 缺「事前申请」阶段（PRC-170 费用事前额度、172 出差申请、173 超标订单审批暂挂 VS-08.2 / VS-05.2） | 见 finance/STAGES.md | 待交 |
| SD-04 | finance STAGES.md ST-2（06e6c36b） | VS-05 / VS-08 | 公司直付的差旅订单（PRC-171 月账单）归获得外部资源还是报销垫付费用 | 见 finance/STAGES.md | 待交 |
| SD-05 | finance STAGES.md 末节 | VS-10.2 | 退出条件改为「三大报表已出具、对账已完成」，关账与记账两项移到进入条件 | finance 提议 | 待交 |
| SD-06 | finance STAGES.md 末节 | VS-06.1 | 进入条件改为「法定申报期已到」 | finance 提议 | 待交 |
| SD-07 | finance STAGES.md 末节 | VS-06.2（流程为 revenue PRC-78） | 进入条件改为「年检或核查通知已到、或有效期临近」 | finance 提议；须 revenue 复核 | 待交 |
| SD-08 | revenue STAGE_ALIGN.md 第 4 节（1974c2ed） | VS-04.2 收入确认 | 退出条件「会计分录已记账」由 finance 的过账流程 PRC-92 产生，收入线只提交过账候选；可选：给 PRC-92 加标 VS-04.2，或把退出条件改为「收入确认事实已过账」 | 架构倾向改退出条件（过账服务全部价值流，逐段加标会让对齐图失真） | 待交 |
| SD-09 | revenue STAGE_ALIGN.md 第 4 节 | VS-02.7 结算 | 进入条件「项目收尾中」现无流程产生，随 Q09 定 | 随 Q09 | 待交 |
