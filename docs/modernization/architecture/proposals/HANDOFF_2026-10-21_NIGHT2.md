# one-architecture 交接（2026-10-21 深夜二，上下文 75%）

状态：历史记录｜2026-10-21｜负责：one-architecture

接续先读本文件，再读 `HANDOFF_2026-10-21.md` 末两节（网络第一、二批与此前裁定）。

## 分支与 PR

| 分支 / PR | 内容 | 状态 | 下一步 |
|---|---|---|---|
| #549 `claude/one-architecture-adr0074` | ADR-0074 行政事务进 BC 清单与审批依赖、办理方式 | 批准页 A7475 已进收件箱（https://claude.ai/artifact/5VR57YayWQCsNZ4Pdjg4RF，pending） | 用户答后按 README 第 4 步读答复，改「已采纳」并标被取代方（ADR-0051 状态行括注；ADR-0055、0064「被部分取代」字段） |
| #552 `…-adr0075`（叠 #549） | ADR-0075 子状态机转换（采 one-data 方案） | 同上批准页 | 同上；ADR-0058「被部分取代」 |
| #557 `…-adr0076`（叠 #552） | ADR-0076 合同原件借阅先行期与 W3 整表换 schema 移交；contexts.yaml 先行登记 contract.agreement | opus 首轮后重写、sonnet 复核已改 | 出批准页（与 0077 合一张，每份一题，不预选），先交 one-coordinator 审题 |
| #558 `…-adr0077`（叠 #557） | ADR-0077 固定资产建成前以财务卡片清单引用，法人取卡片所属法人 | 同上 | 同上；one-data 须同步其 B31_PHYSICAL_MODEL_RULINGS（已发消息） |
| `claude/one-architecture-admission-file` @d48d4145 | 文件服务已准入、签章与行政事务登记、B-31 12 项裁定与补充四项、第 10 项更正、W0 收口批量审核清单、A7475 批准单、10-19 提议稿 7 份 | 文档合集 | 报 one-merge 收 |
| `claude/one-architecture-network-batch2` @85587be6 | 网络第二批（卡 7–11） | 已报 one-merge 收 | — |
| `claude/one-architecture-network-batch3` @aab45ff6 | 第三批卡 12 事实登记、卡 13 维度候选、事件目录订正 | 进行中 | 见下 |
| #530 | 网络第一批 | 调度交 one-merge | — |

## 网络第三批待做

- 卡 12：`panorama_build.py` 接入 `ssot/12-facts/facts.yaml`（节点 11）；N11-1 把「—（注释）」计未声明（先报差异）；新增 N11-2、N11-3。可派 Codex。
- 卡 13：写 `dimensions.yaml`（sonnet 起草、本会话定稿），用语按 one-glossary：「维度」只指分析维度，DIMENSION_VERSIONED 中文称「版本化主数据表」。
- 卡 14：指标三套合一；vision.yaml 16 个 KPI 保留编号并标「出口条件 / 业务指标」；口径冲突一律 needs_user。
- 卡 15：计算绑定。
- finance 分支（8db06ab5）进 main 后，`state-machines.raw` 与 `transitions.yaml` 补读 `sub_machines`（差旅订单行单独编 OTR、挂 OBJ-120）。

## 已定（本轮）

- B-31 12 项裁定见 `proposals/B31_ARCH_RULINGS_2026-10-21.md`；第 3、5 项写法已由 ADR-0077、0076 取代；第 6 项改采 one-data 方案；第 10 项更正为「W0 未冻结，常量随施工卡加，登记 `proposals/freeze/W0_CONTRACT_BATCH_REVIEW.md` 收口窗口审」。
- 文件服务准入九条闭合（`ADMISSION_PLATFORM_FILE_2026-10-21.md`），FileImports 保留、随 B-31 7.10 落地。
- RULE-472 ticket subject 归 platform.integration 外部身份；DA-3 前经来源对照是允许的过渡。

## 待用户定（已攒或在收件箱）

- A7475（ADR-0074、0075）在收件箱。
- 经营复盘、经营动作是否新立对象（OTR-876～884）；分管领导的分管范围配置由谁维护——调度攒确认页。

## 规矩（本轮新增）

- 推送单独一次调用，不与编辑、提交写在同一条命令（pre_push_gate 在命令开始前跑 precheck）。
- 改了已采纳 ADR 条款的（含部分取代、改 BC 边界）一律正式档，不用轻量档。
- 批准页题面：已答过的不再出题，放页首「本页不再请你选的事」；用语按 terms.yaml；通行做法要有出处，否则写「经验判断」。
