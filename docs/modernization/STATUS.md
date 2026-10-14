# STATUS — FORCITIS one 现代化

更新：2026-10-14 · 规则见 SESSION_PLAYBOOK.md（ADR-0020）。认领一个工作单元时在「负责会话」写上会话名；收尾时释放。

## 当前位置

**评估与定栈已完成，技术栈已定稿，brief 已批准 Phase 1a 与 1b（2026-10-14）。** 分析产物已移入 `FORCITIS/forcitis-one-psa` 的 `docs/modernization/`（2026-10-14，用户确认后首次提交），此后以仓库中的文件为准。规则评审第一批（审批 28 条）、第二批（售前报价 14 条）、第三批（身份与组织 45 条）已完成（2026-10-14）。下一步：Phase 1a。

| 工作单元 | 状态 | 负责会话 | 入口 / 阻塞 | 下一条命令 |
|---|---|---|---|---|
| 评估、地图、规则抽取、定栈、brief | 完成；brief 已批准（Phase 1a、1b，2026-10-14） | — （评估会话已收尾） | — | — |
| 总调度 | 进行中：派工、核对报告、汇总用户决定（SESSION_PLAYBOOK §11） | psa-coordinator | — | 工作会话结束前向 psa-coordinator 报告（§4 第 7 步） |
| 规则评审（92 条 P0，按阶段分批） | 第一批完成（审批 28 条：正确 19、错误 9）；第二批完成（售前报价 14 条：正确 11、错误 3，另修订已定 RULE-160；见 RULE_REVIEWS.md）；待讨论 0；第三批完成（identity/org 45 条：正确 16、错误 29，Phase 1a 入口条件已勾选）；其余 50 条 P0 未开始 | — | Phase 1a、1b 入口的规则评审均已完成；其余按阶段（下一批：交付、工时、收入确认，Phase 2 前；RULE-144 毛利口径须与 RULE-182/239 对齐） | Phase 2 前新会话 `psa-review-delivery` 运行 `/code-modernization:modernize-review psa` |
| 老系统安全止血 | **搁置**（用户 2026-10-14：「老系统先不管，确保新系统没有这些缺陷」）。补丁 7 轮与止血方案保留备查，不执行 | — | 无（不再阻塞新系统：新系统不信任老系统身份，见 phase1a/SECURITY_INVARIANTS.md） | — |
| 用语确认 | 完成：第一轮 197 项；第二轮 80 项（2026-10-14：提议术语 58 条转正式、A8 叫法 20 个归方言、新 BC 命名 2 项）；terms.yaml 348 条全部已批准 | — （psa-terms-review 已收尾） | 待 1a 会话改名 4 处代码旧写法后删除 backend/glossary/proposed/pending-renames.yaml（见 round2-proposals.yaml code_renamed） | 后续阶段的新术语按 ADR-0018 登记后再开下一轮 |
| Phase 1a 平台桥接（试点） | 进行中（2026-10-14 派工） | psa-1a-platform-bridge-reimagine | 入口条件见 brief：老系统安全止血按用户决定不再阻塞（I-1..I-9 保障）；骨架与 CI 已就绪（PR #9）；RDS 已开通（PG 18 高可用 pgm-bp13vak0sew3m003，现作测试开发用）；FORCITIS one 专用 ECS 已开通（i-bp122xlpar8w2uylmal2，杭州 K 区，g8i.xlarge 4C16G，内网 172.16.89.86，包年自动续费，安全组 sg-bp1hjbq8dkoaxa2nljzq 仅 80/443，RDS 白名单分组 forcitis_one）；待办：FORCITIS one 用数据库与角色、部署 | `/code-modernization:modernize-reimagine`（新会话 `psa-1a-…`） |
| Phase 1a 流程梳理（现状流程、目标流程、差异清单，差异逐条由用户决定） | 完成（2026-10-14）：7 个流程（登录与会话、组织与人员同步、审批通用流程、TIMESHEET 审批、合并待办与通知、业务编号、审计），差异 D-1a-01..181 全部有用户决定（intent 102–159），contract.md 178 条写入 brief §5；brief Phase 1a 入口条件「流程梳理已完成」已勾选 | — （psa-1a-process-review 已收尾） | 需改代码清单已报 psa-coordinator 转 1a 会话；ADR-0026 第 5 项第 2、6 条与 D-11 按用户决定需修订（intent 156–158，报 psa-coordinator）；ADR-0009 §6 第 21 行与 I-2 矛盾待处理 | Phase 1b 前新会话 `psa-1b-process-review` 做 1b 流程梳理（Phase 2 待决清单见 process/1a/TIMESHEET审批.md D-1a-127） |
| OA 流程盘点（迁入边界与顺序） | 完成（2026-10-14）：OA_PROCESS_INVENTORY.md（125 个在用模板：迁入 80、视情况 30、留在 OA 12、已停用 3；权责事实完整性矩阵 24 行 + 主数据；36 处集成点逐处判定；6a–6f 阶段草案）；ADR-0021 **已采纳**（用户：「采纳；6a–6c 并入 Phase 4 前」）；ADR-0016 第 44 行修订提议与 brief 两条 `Proposed revision:`（H-1 不设双源期、Phase 4 出口以 6a–6c 为前置）；A8 叫法 20 项登记为待确认用语 | — （psa-oa-inventory 已收尾） | 待用户：批准 ADR-0016 修订提议与 brief 两条修订；用语确认表 A 组 20 项；财务判定 U8C 手工 / 出纳 / 固定资产凭证；「应付与付款」BC 编号由 psa-gl-scope 统一 | 6a 前新会话 `psa-6a-contract-revenue-extract` 补抽 A8 合同与收入规则（含表单触发器） |
| 总账纳入新系统与三大报表（范围评估） | 完成，待审批（2026-10-14）：U8C_GL_PRACTICE.md（两套账蒸馏 ①–⑦ + 黄金基线清单）、u8c/（6 张报表 376 条公式、二开与插件清单、使用面统计、零发生科目、只读查询）、U8C_CAPABILITY_MAP.md、GL_COMPLIANCE_CHECKLIST.md（财会〔2024〕12 号 47 条）、rules/GL_RULES.md（125 张）与 GL_FINANCE_REVIEW.md（105 项，★22）、ADR-0022（提议）、proposals/GL_SCOPE_REVISIONS.md（ADR-0016 第 6 条与 ADR-0017 第 4 条修订稿、阶段草案 G1 / G2、16 项待决问题）；黄金基线只在本机 `~/.modernize/psa/u8c-baseline/` | — （psa-gl-scope 已收尾） | 待用户审批 ADR-0022、两条 ADR 修订与 G1 / G2 草案；待财务答复 GL_FINANCE_REVIEW ★22 项与「哪一套是法定账」 | 财务评审另开会话（psa-coordinator 安排）；阶段编号 G1 / G2 由 psa-coordinator 与 6a–6f 统一 |
| 经营分析范围评估（经营子域 4 个 BC、reporting 与 KPI 语义层、BI 重构规划） | 完成，**ADR-0023 已采纳**（用户 2026-10-14：「批准 ADR-0023」）：BI_SCOPE.md；**§6 Q1–Q12 已全部有用户决定**（Q1/Q2 经 psa-coordinator，Q3–Q12 经 psa-bi-decisions 逐项确认，见 BI_SCOPE §6.1） | — （psa-bi-decisions 已收尾） | 待财务 F1–F5、待业务 B1–B5（BI_SCOPE §6.1）；R 编号与 6a–6c 入口修订（镜像就绪）由 psa-coordinator 统一；Q11 生产只读计数在 R0 执行前须先报访问方式 | 新会话 `psa-r0-metric-baseline` 做 R0（OAT 入库 glossary/oat/、指标目录与 OAT 映射、T_FST 逐个核对、api_audit_logs 计数、工具对比、Q12 问题清单模板、词表登记） |
| 总账财务评审（G1 入口前：口径转换规则 D17、科目表与编码 D3、★ 项） | 完成，待财务（2026-10-14）：28 项全部由用户作答——已定 23、已定但有待财务问题 5；12 个问题待财务（关账日、受限货币资金、履约义务时段 / 时点须审计师确认、期初签字人、单账簿凭证分类等）；D3 用户选「两账各一张科目表、权责新编一套」，ADR-0022 第 2、5、9 条修订提议见 GL_SCOPE_REVISIONS §E | — （psa-gl-finance-review 已收尾） | 待用户转财务答复 GL_FINANCE_DECISIONS.md「待财务清单」；D17-12 需 U8C 只读分类查询（只取计数，须 psa-coordinator 安排）；ADR-0022 已补齐（财务评审结论与 §A、§B、§E 并入正文，声明取代 ADR-0016 第 6 条与 ADR-0017 第 4 条，四个必备小节齐全），仍为提议，待用户审批；GL_FINANCE_REVIEW 第二至四节 83 项按阶段另排 | 用户把 `r0/FINANCE_CONFIRM.html`（财务确认单第 2 版：7 份 ADR 整体确认 + FQ-01..FQ-22）转财务；交回后运行 `python3 r0/import_finance_answers.py <答复文件>` |
| 变更治理（ADR 生命周期、规则调整三类、已过账事实只能冲销） | 完成（2026-10-14）：SESSION_PLAYBOOK §12；adr/README.md「ADR 生命周期与变更规则」与「历史偏差」；build_adrs.py 校验；CONFIG_CHANGELOG.md；ADR-0024（提议） | — （psa-change-governance 已收尾） | 待用户审批 ADR-0024（采纳时同一提交删除 ADR-0016 追加段并标注部分取代） | — |
| 公共记录片段化（INTENT / SESSION_LOG / STATUS / ADR 一条记录一个文件） | 完成（2026-10-14）：intent/、session-log/、status/、adr/src/ 片段与生成器；build_all.py；CI 生成一致性检查；CLAUDE.md 与 SESSION_PLAYBOOK 改为只改片段；迁移后产物除空白外逐字节一致 | — （psa-repo-fragments 已收尾） | — | 各会话改用 new_intent.py / new_log.py 与 status/ 片段，改完运行 build_all.py 并连同产物提交 |
| 授权模型 ADR-0026（数据范围、法人维度、审批访问、职责分离、super_admin 边界、授权治理、敏感字段、老角色迁移） | 完成：ADR-0026 已采纳（用户 2026-10-14 批准，D-1..D-13 已决定） | — （psa-authz-model 已收尾） | —（1b 前须登记的新规则见 STATUS 阻塞） | 批准后由 Phase 1a 会话按 ADR-0026「1a 交付范围」实现并同步守护规则 |
| 写代码规则第二批（11 条）与「流程梳理」步骤写入 | 完成（2026-10-14）：SESSION_PLAYBOOK §5 第二批 11 条、§13 流程梳理；CLAUDE.md；.pre-commit-config.yaml；PR 模板；brief 各阶段入口条件与 §5「流程梳理结论」 | — （psa-repo-fragments 已收尾） | — | 调度会话派 `psa-1a-process-review` 补做 Phase 1a 流程梳理 |
| 文档去除旧名称「PSA」（新系统称 FORCITIS one，老系统称「老系统」，字面标识保留） | 完成（2026-10-14）：B1–B6 分批替换；check_legacy_name.py 守护接入 CI；backend 断言消息交 1a 会话 | — （one-docs-naming 已收尾） | — | 新会话名用 one-* 前缀；legacy_system 术语转正待下一轮用语确认 |
| R0 指标口径基线 | 完成（2026-10-14）：在用报表盘点与指标基线草稿（r0/）；B6–B11、Q1（C 为主，D 后期可选，AG Grid 企业版）、Q5（OAT 入 glossary/oat/）、Q11（BI 使用量与 T_FST 元数据，生产只读）、Q12（问题模板）已完成；待财务 FQ-01..20（r0/FINANCE_QUESTIONS.md）；第三轮用语输入（r0/TERMS_ROUND3_INPUT.md）；新登记生产 SQL Agent 通道 A8_ACCESS_REGISTER #28–#32 | — （psa-r0-metric-baseline 已收尾） | 待用户：B1–B5、问题清单（~/.modernize/psa/bi-inputs/questions/）；待财务：FQ-01..20；第三轮用语确认 | R1 入口条件草稿见 r0/TOOL_COMPARISON.md §7；财务答复回填基线（r0/FINANCE_ANSWERS.json） |
| Phase 1b 报价 | 未开始 | — | Phase 1a 出口 | — |
| Phase 2 交付 + 工时 | 未开始 | — | Phase 1b 出口 | — |
| Phase 3 差旅报销 | 未开始 | — | Phase 1a 出口 | — |
| Phase 4 成本、收入与核算 | 未开始 | — | Phase 2、3 出口 | — |
| Phase 5 身份全面切换、BI、下线 | 未开始 | — | Phase 4 出口 | — |

## 阻塞与待用户决定

- 已知缺口（Phase 1a 会话登记，1b 前必须修复，详见 `phase1a/TIMESHEET_REPLAY.md`「已知缺口」）：G-1 审批节点「取不到人」与「字段缺失 / 值无效」无法区分，缺失或无效时应失败并告警，不能静默跳过（RULE-300）；G-2 会签（RULE-248/273）未实现。
- 老系统安全缺陷（含 SEC-116 验证码固定 9999）按用户决定不处理；新系统按 `phase1a/SECURITY_INVARIANTS.md` I-1..I-9 保证不继承。brief Phase 1a 入口条件「老系统已止血」需按 `Proposed revision:` 追加说明（psa-coordinator）。
- 运维事项：收窄 48088 安全组与 nginx 拒绝免密端点（SEC-001 网络层止血）、删除 `/other-api`（SEC-107）、Nacos 内部令牌、凭据轮换；RDS PG 18 高可用、ARMS、SLS 已开通（2026-10-14，见 aliyun 仓库 evidence/pg-upgrade-20261014）；RDS 已开通自动续费（用户 2026-10-14）；FORCITIS one 专用 ECS 已开通（2026-10-14）。
- 业务编号新格式的各单据模板，在 Phase 1a 由管理员确认。
- OA 流程盘点（OA_PROCESS_INVENTORY.md §9 待办）：ADR-0016 修订提议与 brief Phase 4 两条 `Proposed revision:` 待批准；用语确认表 A 组 20 项；U8C 手工 / 出纳 / 固定资产凭证由财务逐类判定。
- ADR-0026（已采纳）：1b 实现前须按 SESSION_PLAYBOOK §12.2 先在 RULE_REVIEWS 登记并经用户确认的新规则——① 法人限定 `legal_entity_scope`（ANY / OWN / LISTED，只作用于有法人列的表，默认 ANY）；② 审批参与只读访问（参与人范围、办结后保留与收回，D-4）；③ 授予时职责分离的组合规则（随第一条组合规则）。另：凭证制单 / 审核 / 记账的操作时规则（原 D-8）交总账 ADR 与财务决定。
