# STATUS — PSA → FORCITIS one 现代化

更新：2026-10-14 · 规则见 SESSION_PLAYBOOK.md（ADR-0020）。认领一个工作单元时在「负责会话」写上会话名；收尾时释放。

## 当前位置

**评估与定栈已完成，技术栈已定稿，brief 已批准 Phase 1a 与 1b（2026-10-14）。** 分析产物已移入 `FORCITIS/forcitis-one-psa` 的 `docs/modernization/`（2026-10-14，用户确认后首次提交），此后以仓库中的文件为准。规则评审第一批（审批 28 条）、第二批（售前报价 14 条）、第三批（身份与组织 45 条）已完成（2026-10-14）。下一步：Phase 1a。

| 工作单元 | 状态 | 负责会话 | 入口 / 阻塞 | 下一条命令 |
|---|---|---|---|---|
| 评估、地图、规则抽取、定栈、brief | 完成；brief 已批准（Phase 1a、1b，2026-10-14） | — （评估会话已收尾） | — | — |
| 总调度 | 进行中：派工、核对报告、汇总用户决定（SESSION_PLAYBOOK §11） | psa-coordinator | — | 工作会话结束前向 psa-coordinator 报告（§4 第 7 步） |
| 规则评审（92 条 P0，按阶段分批） | 第一批完成（审批 28 条：正确 19、错误 9）；第二批完成（售前报价 14 条：正确 11、错误 3，另修订已定 RULE-160；见 RULE_REVIEWS.md）；待讨论 0；第三批完成（identity/org 45 条：正确 16、错误 29，Phase 1a 入口条件已勾选）；其余 50 条 P0 未开始 | — | Phase 1a、1b 入口的规则评审均已完成；其余按阶段（下一批：交付、工时、收入确认，Phase 2 前；RULE-144 毛利口径须与 RULE-182/239 对齐） | Phase 2 前新会话 `psa-review-delivery` 运行 `/code-modernization:modernize-review psa` |
| 老系统安全止血 | 补丁 6 轮修订完成，未部署 | — | 运维：Nacos 内部令牌、部署顺序、nginx 删除 `/other-api`、凭据轮换、dev 实测 | 用户安排运维与 dev 实测 |
| 用语确认 | 完成（197 项） | — | 模板新增 4 个提议词条待下一轮确认 | — |
| Phase 1a 平台桥接（试点） | 进行中（2026-10-14 派工） | psa-1a-platform-bridge-reimagine | 入口条件见 brief：未满足项含安全止血与凭据轮换、主机与 RDS、骨架与 CI、老表所有权矩阵 | `/code-modernization:modernize-reimagine`（新会话 `psa-1a-…`） |
| OA 流程盘点（迁入边界与顺序） | 进行中（2026-10-14 派工） | psa-oa-inventory | 只读盘点 A8 OA 流程，产出 OA_PROCESS_INVENTORY.md 与 ADR-0021（提议）；生产只读查询已获用户同意 | 完成后由用户决定 ADR-0021 |
| 总账纳入新系统与三大报表（范围评估） | 进行中（2026-10-14 派工） | psa-gl-scope | 只读盘点 PSA→U8C 凭证与 U8C 总账做法；U8C 访问方式须先经用户确认；产出 U8C_GL_PRACTICE.md、ADR-0022（提议）、brief 修订稿 | 完成后由用户审批 ADR-0022 与 brief 修订 |
| Phase 1b 报价 | 未开始 | — | Phase 1a 出口 | — |
| Phase 2 交付 + 工时 | 未开始 | — | Phase 1b 出口 | — |
| Phase 3 差旅报销 | 未开始 | — | Phase 1a 出口 | — |
| Phase 4 成本、收入与核算 | 未开始 | — | Phase 2、3 出口 | — |
| Phase 5 身份全面切换、BI、下线 | 未开始 | — | Phase 4 出口 | — |

## 阻塞与待用户决定

- 运维事项：收窄 48088 安全组与 nginx 拒绝免密端点（SEC-001 网络层止血）、删除 `/other-api`（SEC-107）、Nacos 内部令牌、凭据轮换；开通独立 ECS 与 RDS PG 18 高可用、ARMS/SLS。
- 业务编号新格式的各单据模板，在 Phase 1a 由管理员确认。
