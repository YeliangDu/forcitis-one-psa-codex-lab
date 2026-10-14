# STATUS — PSA → FORCITIS one 现代化

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
| Phase 1a 平台桥接（试点） | 进行中（2026-10-14 派工） | psa-1a-platform-bridge-reimagine | 入口条件见 brief：老系统安全止血按用户决定不再阻塞（I-1..I-9 保障）；骨架与 CI 已就绪（PR #9）；RDS 已开通（PG 18 高可用 pgm-bp13vak0sew3m003，现作测试开发用）；FORCITIS one 专用 ECS 已开通（i-bp122xlpar8w2uylmal2，杭州 K 区，g8i.xlarge 4C16G，内网 172.16.89.86，包年自动续费，安全组 sg-bp1hjbq8dkoaxa2nljzq 仅 80/443，RDS 白名单分组 forcitis_one）；待办：PSA 用数据库与角色、部署 | `/code-modernization:modernize-reimagine`（新会话 `psa-1a-…`） |
| OA 流程盘点（迁入边界与顺序） | 完成（2026-10-14）：OA_PROCESS_INVENTORY.md（125 个在用模板：迁入 80、视情况 30、留在 OA 12、已停用 3；权责事实完整性矩阵 24 行 + 主数据；36 处集成点逐处判定；6a–6f 阶段草案）；ADR-0021 **已采纳**（用户：「采纳；6a–6c 并入 Phase 4 前」）；ADR-0016 第 44 行修订提议与 brief 两条 `Proposed revision:`（H-1 不设双源期、Phase 4 出口以 6a–6c 为前置）；A8 叫法 20 项登记为待确认用语 | — （psa-oa-inventory 已收尾） | 待用户：批准 ADR-0016 修订提议与 brief 两条修订；用语确认表 A 组 20 项；财务判定 U8C 手工 / 出纳 / 固定资产凭证；「应付与付款」BC 编号由 psa-gl-scope 统一 | 6a 前新会话 `psa-6a-contract-revenue-extract` 补抽 A8 合同与收入规则（含表单触发器） |
| 总账纳入新系统与三大报表（范围评估） | 进行中（2026-10-14 派工） | psa-gl-scope | 只读盘点 PSA→U8C 凭证与 U8C 总账做法；U8C 访问方式须先经用户确认；产出 U8C_GL_PRACTICE.md、ADR-0022（提议）、brief 修订稿 | 完成后由用户审批 ADR-0022 与 brief 修订 |
| Phase 1b 报价 | 未开始 | — | Phase 1a 出口 | — |
| Phase 2 交付 + 工时 | 未开始 | — | Phase 1b 出口 | — |
| Phase 3 差旅报销 | 未开始 | — | Phase 1a 出口 | — |
| Phase 4 成本、收入与核算 | 未开始 | — | Phase 2、3 出口 | — |
| Phase 5 身份全面切换、BI、下线 | 未开始 | — | Phase 4 出口 | — |

## 阻塞与待用户决定

- 老系统安全缺陷（含 SEC-116 验证码固定 9999）按用户决定不处理；新系统按 `phase1a/SECURITY_INVARIANTS.md` I-1..I-9 保证不继承。brief Phase 1a 入口条件「老系统已止血」需按 `Proposed revision:` 追加说明（psa-coordinator）。
- 运维事项：收窄 48088 安全组与 nginx 拒绝免密端点（SEC-001 网络层止血）、删除 `/other-api`（SEC-107）、Nacos 内部令牌、凭据轮换；RDS PG 18 高可用、ARMS、SLS 已开通（2026-10-14，见 aliyun 仓库 evidence/pg-upgrade-20261014）；RDS 已开通自动续费（用户 2026-10-14）；FORCITIS one 专用 ECS 已开通（2026-10-14）。
- 业务编号新格式的各单据模板，在 Phase 1a 由管理员确认。
- OA 流程盘点（OA_PROCESS_INVENTORY.md §9 待办）：ADR-0016 修订提议与 brief Phase 4 两条 `Proposed revision:` 待批准；用语确认表 A 组 20 项；U8C 手工 / 出纳 / 固定资产凭证由财务逐类判定。
