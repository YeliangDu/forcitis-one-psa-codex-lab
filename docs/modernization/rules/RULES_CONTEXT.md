# one-rules 速查（2026-10-16 下午）

一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

设计原则（用户 2026-10-15，经 one-coordinator 转达）：以 AEC 行业项目制专业服务的通行做法为主（合同范本、收入准则、成熟产品），老系统生产数据只作参考与核对；确需复刻的老行为仍以生产数据为证。业务决策页做法见 `rules/decide/README.md`；页面顶部是分组标签，选择自动存为草稿，每组点「提交本组」才算答复（用户 2026-10-15 首次只提交了各页第一组）。

## 在途

| 项 | 状态 | 出处 |
|---|---|---|
| 顾问答题单（用户与调度共同答财务、审计、法务类题；用户 2026-10-16 改派，不转发） | 第 1 批已出（W-7、P-001～005）；W-7、P-001、P-002 先请用户答；P-003～005 要按新路线重写（一次投产 2028-01-01、全部历史迁入、U8C 继续做法定账，intent 133348 U-2、U-4、U-6、U-8），先对照 U-1～U-18；第 2 批含 W-1～W-6、W-8 | 私有：`~/.modernize/psa/finance/ADVISOR_BATCH_1.md`、`external-drafts/`；#360 已关 |
| 决定链路（确认页整页「检查答案」后一次提交、显示编号、自动把收件箱对应项改为已定；新单子统一 `sheets/*.json`，旧页不迁；规则卡索引由 one-quality 做工具我维护） | 进行中，排在顾问第 2 批之前 | 调度派工；`rules/decide/`；收件箱 https://claude.ai/artifact/RVU6nNNSsygF5sVRWLhQ15 |
| 填报义务规则 RULE-397～405 | 已写；等 B-27 请假对象与标准工时版本（AGG-108）落地才能实现；#286 待入队 | `rules/TIMESHEET_FILING_RULES.md` |
| 财务口径决策单 | 46 题与 9 条事实已写回；F-05、F-10、F-11 与 ADR-0047 八项（F-12～F-19）暂缓不外发；A1、A10 待审计师 | `r0/FINANCE_DECISIONS.md` |
| 驳回与撤销原因、再发起 | #346（RULE-272、273）、#347（撤销一律须原因、再发起状态机）待合并 | intent 085331、123736 |
| 手册 §5 转述前核对原文 | #323 待合并 | |
| 规则卡补「类别」 | ADR-0040 第 2 步之后 | ADR-0040 |
| 规则文档瘦身对照表 | 待用户 | COORDINATOR_LEDGER「待用户」 |
| F-1 数据范围；RULE-387 | F1-01、02 已定（ADR-0044 修订在 one-architecture）；RULE-387 维持条目的业务负责人与有效期待 ADR-0044 Q1；区域维度并入 one-architecture 的组织梳理 | `rules/decide/answers/F1.json` |
| D-16 事件改名 | 已合入 | |

## 工作守则要点（画像「权限边界」下有全文）

- 发布决策页固定 `contract: "0.2.80"`；发布后核对真实渲染，没登录就报「未实看」。
- 要别人定的事直连对方，抄送 one-data 或 one-coordinator；用户待答事项只由调度汇总到收件箱。
- 写回只写用户原话覆盖的范围；转述的结论先核对原文并标已核实 / 已抽查 / 未核实。
- 每个 PR 开后立即关 Auto-fix；子代理显式指定模型（haiku 取数，sonnet 调研与草稿，opus 极少）。
- 错题本 `quality/defect-bank/one-rules.md`，决策页发布前回测。

## 决策页工具

`rules/decide/`：生成器 `build_decision_page.py`、导出 `export_answers.py`、收件箱 `inbox/`；已发布的页面与链接见 `rules/decide/README.md`。
