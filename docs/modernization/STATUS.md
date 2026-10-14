# STATUS — PSA → FORCITIS one 现代化

更新：2026-10-14 · 规则见 SESSION_PLAYBOOK.md（ADR-0020）。认领一个工作单元时在「负责会话」写上会话名；收尾时释放。

## 当前位置

**评估与定栈已完成，技术栈已定稿，brief 已批准 Phase 1a 与 1b（2026-10-14）。** 分析产物已移入 `FORCITIS/forcitis-one-psa` 的 `docs/modernization/`（2026-10-14，用户确认后首次提交），此后以仓库中的文件为准。规则评审第一批（审批 28 条）已完成（2026-10-14）。下一步：Phase 1a。

| 工作单元 | 状态 | 负责会话 | 入口 / 阻塞 | 下一条命令 |
|---|---|---|---|---|
| 评估、地图、规则抽取、定栈、brief | 完成；brief 已批准（Phase 1a、1b，2026-10-14） | psa-assess-brief（本会话） | — | — |
| 规则评审（92 条 P0，按阶段分批） | 第一批完成（审批 28 条：正确 19、错误 9、待讨论 0，见 RULE_REVIEWS.md）；其余 64 条未开始 | — | 第二批：售前报价 14 条（1b 前）；其余按阶段 | Phase 1a 入口已无规则阻塞：新会话 `psa-1a-…` 运行 `/code-modernization:modernize-reimagine`；1b 前新会话 `psa-review-quotation` 运行 `/code-modernization:modernize-review psa` |
| 老系统安全止血 | 补丁 6 轮修订完成，未部署 | — | 运维：Nacos 内部令牌、部署顺序、nginx 删除 `/other-api`、凭据轮换、dev 实测 | 用户安排运维与 dev 实测 |
| 用语确认 | 完成（197 项） | — | 模板新增 4 个提议词条待下一轮确认 | — |
| Phase 1a 平台桥接（试点） | 未开始 | — | brief 审批；入口条件见 brief | `/code-modernization:modernize-reimagine`（新会话 `psa-1a-…`） |
| Phase 1b 报价 | 未开始 | — | Phase 1a 出口 | — |
| Phase 2 交付 + 工时 | 未开始 | — | Phase 1b 出口 | — |
| Phase 3 差旅报销 | 未开始 | — | Phase 1a 出口 | — |
| Phase 4 成本、收入与核算 | 未开始 | — | Phase 2、3 出口 | — |
| Phase 5 身份全面切换、BI、下线 | 未开始 | — | Phase 4 出口 | — |

## 阻塞与待用户决定

- 运维事项：收窄 48088 安全组与 nginx 拒绝免密端点（SEC-001 网络层止血）、删除 `/other-api`（SEC-107）、Nacos 内部令牌、凭据轮换；开通独立 ECS 与 RDS PG 18 高可用、ARMS/SLS。
- 业务编号新格式的各单据模板，在 Phase 1a 由管理员确认。
