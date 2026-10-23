# Phase 1a 下一批派工草案

起草：psa-1a-platform-bridge-reimagine，2026-10-14。依据 SESSION_PLAYBOOK 附 M1 第 5–7 条（用户「采纳」）：缺陷修复、功能补做、结构性重构分开派；缺陷按卡手术式修、小 PR 直合 main，不再攒大整合分支。修复批整合复审中的 Medium / Low 与新发现的「应交付未做」逐条追加到本草案，带复审编号。

每份派工单须写明：平台限制、授权外动作、须转用户的决定；「用户已决定的事照做，有异议停下报告，不得自行推迟或改变」；改动只在自己的分支提交。

## 一、功能补做（首次部署前必须完成的排最前）

| # | 项 | 依据 | 备注 |
|---|---|---|---|
| F-1 | ADR-0026「1a 交付范围」整套数据范围机制：类型下沉、`CurrentAccess`、角色范围授予明细表、直接下属查询、注册表 SCOPED / GLOBAL、`DataAccessRegistryTest` / `onlyRegisteredPersistenceTouchesTheDatabase` / `ScopedRepositoryContract`、部门浏览与人员详情接入范围 | 用户「本批堵漏洞，整套放下一批」；status/blockers/090 | **首次部署任何环境前必须完成**；完成后删 090。一并实现（用户 2026-10-15「采纳，敏感权限授给人」）：敏感清单内的权限只能授给具体的人，禁止经岗位或组织同步自动授予（守护）；组织同步发现敏感权限持有人调岗、离岗时，生成「待调整权限」待办给组织数据管理员与安全审计员，人工确认后才改，不自动收回或授予；与 ADR-0027 离职删除授予衔接 |
| F-2 | 守护 G-T1（应用服务只写一个聚合的仓储，读 DATA_DESIGN_CHECKLIST §14 已登记例外）、G-T2（法人 / 组织 / 员工维度 ID 写死或默认值，全生产代码）、G-T4（contract.md 与本阶段 ADR 交付条款到测试的对照，豁免清单入仓） | 用户采纳 PR #57；DATA_DESIGN_CHECKLIST §14 | G-T1 已有 SingleAggregateGuardTest（核对是否按仓储判定并读 §14 例外）；G-T2 仅部分覆盖（CurrentUserGuardTest），补全到全生产代码；G-T3 已由 TestIsolationGuardTest 覆盖 |
| F-3 | 前端运行时配置：PC `login-config.ts`、`login-page.tsx`，H5 `config/index.ts` 的构建期变量改为运行时配置（一次构建、多环境部署） | PR #59 第 4 项 | |
| F-4 | 契约中前端未做的条目：D-1a-139 管理员全部待办视图、D-1a-75 转交 / D-1a-77 批量与代理界面、D-1a-47/48 管理模式 1 小时横幅（`administratorModeExpiresAt`） | 修复批 C 报告 | 后端接口已有的先做 |
| F-5 | 审批契约未做条目：D-1a-67、76、91、128、130、131、181（逐条核对契约原文是否 1a 必做） | 修复批 A 报告 | |
| F-7 | 金额内核：`Money` 构造不再隐式舍入（超过币种小数位报错；只支持 2 位小数币种，其余拒绝）；精确的中间运算类型；`Rate` / `Ratio`（0–1 小数）；按权重拆分原语（守恒、确定性、负数对称、冲销镜像，最大余数法）；舍入口径集中一处（四舍五入到分，财务答复不同时按配置从期首起改）；守护：领域代码禁止 `setScale` / `divide` / `RoundingMode`、DDL 金额精度（numeric 位数、`ALTER ADD COLUMN` 的金额与币种配对）、OpenAPI 金额为十进制字符串 + 币种、前端金额格式化 lint | 用户决定 XD-01「十进制字符串 + 币种」、XD-02「一律 0–1 小数」、XD-08「先按建议建」、XD-07 术语「尾调 / 舍入差 / 按权重拆分」（「尾差」为方言）；普查 PR #75 | 验收测试按 §6.1 第 8 条由测试代理先写。扩展（one-coordinator 2026-10-15 业财核查）：`TaxContext`——税率按版本与生效日取值（配置类，版本化、不追溯），价税分离走内核，口径待 FQ-27 |
| F-8 | 平台共用件：统一事件消费幂等、列表分页契约与上限、共用脱敏组件、共用导出控制、日期边界测试夹具（日 / 月 / 年 / 2 月 29 日）、中文排序按拼音（ICU zh）的约定与守护 G-X2 | 普查 PR #75；用户决定 XD-03「按拼音」；XD-05 / XD-06 已有决定（日历过去日期锁定、组织单元版本表，修复批已落地） | 扩展（业财核查）：通用时态生效基座——`EffectivePeriod` 值对象（含开闭区间约定）、按日期取生效版本的仓储支持、防重叠约束与只追加的统一写法与守护；组织、法人归属、编号模板版本迁到同一基座（不改行为）。掩码设计区分「职级标准费率」与「个人真实薪资 / 日薪」：项目经理可见按职级标准费率计算的个人行，真实成本只在项目合计层（用户「按职级标准费率看到个人行」），数据分级与掩码规则据此写进规则卡 |
| F-9 | 起草 ADR-0029（提议）：非敏感权限按岗位自动授予。ADR-0026 没有按岗位授予的条款，属新增能力；按 §12.1 写影响面、迁移、回滚、需同步修改的守护，经 architecture-critic 评审后交 one-coordinator 转用户批准，**批准前不实现** | 用户 2026-10-15「采纳，敏感权限授给人」 | F-1 不阻塞于此 |
| F-10 | 组织单元「利润中心」标识：组织数据管理员在新系统中**逐个部门**标记，带生效区间、只追加版本（走 F-8 时态基座）；不改 A8、不按 PATH 层级推导；标识归新系统（COLUMN_OWNERSHIP 与数据访问注册表登记，组织同步不得覆盖）；部门撤销或合并使利润中心部门失效时，生成待办提醒组织数据管理员重新指定；标记变更写审计 | 用户 2026-10-15「利润中心就是某一级部门」「不在同一层，在新系统里逐个指定 (Recommended)」 | |
| F-11 | 起草新 ADR（提议）：业务单据「原单锁定 → 红冲单 → 修正单」通用模式，与 ADR-0016 / 0022 的过账冲销衔接（跨期工时调整、合同变更单都按此）；1a 只定设计，Phase 2 首次使用时在内核实现；经 architecture-critic 评审后交 one-coordinator 转用户 | one-coordinator 2026-10-15 业财核查 | |
| F-6 | D-1a-08：同步 HELD 与每日问题汇总经企业微信发给「组织数据管理员」角色（按角色取人） | 修复批 B3 报告 | 依赖 B1 的系统角色，已具备 |

## 二、结构性重构（单独派，不与缺陷混）

| # | 项 | 依据 | 备注 |
|---|---|---|---|
| R-1 | UUIDv7 主键：全部聚合 ID 类型、迁移、事件载荷、OpenAPI；`IdGenerator` 与「单列主键必须是 uuid」守护已在修复批落地 | 用户「UUIDv7 (Recommended)」；B3 擅自推迟已如实登记 | 在迁移基线之前完成；可与 R-2 同批 |
| R-2 | 79 项命名改名（ddl-naming-baseline 实有条目；原写 84，one-1a-next 2026-10-15 清点更正）与「web/api 出参不得暴露 SENSITIVE / RESTRICTED 列」守护（若修复批 B2b 未完成） | #44 | |
| R-4 | 数据库时钟默认值（102 处、31 个文件，其中 `DEFAULT` 写法 99 处，另 3 处 DML `now()` 即 INT-L23；原写约 91 处、28 个）改为不依赖数据库时钟：在开放基线中去掉默认值（SESSION_PLAYBOOK 附 M3「迁移基线」），各 INSERT 显式传入注入 Clock 的时刻；并入时间守护 | 修复批时区项范围外发现（`TimeSourceGuardTest` 已禁止 SQL 字符串读数据库时钟） | 宜并入 R-3 迁移基线一并处理 |
| R-3 | 迁移基线 PR：已合入迁移合并为一份基线，opus 用两个空库对比 `pg_dump --schema-only`，diff 为空的证据写进 PR 正文；同 PR 实现「迁移只增不删不改名」守护（基线开放标记移除后生效，破坏性变更走 expand / contract 并登记例外） | §12.2；one-coordinator 2026-10-14 | 顺序与写法以 `NEXT_BATCH_PLAN.md` 为准：先 R-3a 纯合并基线（diff 为空），再 R-1、R-2、R-4 直接改基线（用户「开放期内直接改基线」）；B2 011000 / 011100 已为无条件，剩建库脚本 §4b 的 `to_regclass`；`application.properties` 中退役的 `delivery_execution`（INT-L24）一并移除 |

## 三、缺陷修复（按卡手术式，小 PR 直合 main）

整合复审中定级为 Medium / Low 的条目，复审结束后逐条追加，格式：`复审编号 | 文件:行 | 一句话 | 回归测试名`。

| 复审编号 | 文件:行 | 一句话 | 回归测试名 |
|---|---|---|---|
| B1-scope-M1 | `platform/identity/.../OrgUnitScopes`、`OrgUnitScopeIntegrationTest` | 调用人在组织中找不到部门时，显式处理（按本部门类授予跳过或明确 403，写入规则卡），不再靠 `AccessGrant` 的空指针兜底；改正测试显示名 | `B1-scope 无部门调用人…` |
| B1-scope-M2 | `backend/architecture/module-dependencies.txt`、`REIMAGINED_ARCHITECTURE.md` §2.2 | 登记 kernel 端口 `OrgUnitAccess`；F-1 下沉 `OrgUnitId` 时端口改为类型化 id，去掉 `NOT_USED = new LegalEntityId(1)` | 随 F-1 |
| B1-scope-N1 | `LegalEntityController.java:31-32`、`data-access-registry.yaml` | 法人列表登记为 GLOBAL；部门响应的 `parentOrgUnitId` 对范围外上级裁剪 | 随 F-1 |

| B1-scope-N2 | `OrgUnitScopeIntegrationTest` | 越权与不存在两种响应改为比较整个响应体，而不只比 errorCode | `B1-scope 越权与不存在响应完全相同` |
| B1-scope-N3 | `GetLegalEntityAttribution`、`GetOrgUnit`、`OwnAttributionGuard` | 审计主体键统一写法（`org_unit:` / `org-unit:` / `legal-entity-attribution:org_unit:` 三种并存） | 审计检索测试 |

| B3-R2 | `RepositoryWritePolicy`、`RepositoryWriteGuardTest` | 仓储写入守护堵口：例外理由必填；版本条件须在 WHERE 中而非整条 SQL 匹配；检查 `INSERT … ON CONFLICT DO UPDATE`；识别拼接 SQL | `B3-R2 …` |
| B3-R3 | `IdempotencyFilter.java:94-96`；接管逻辑 | 抢占失败且查询为空时不执行业务（重新抢占或 409）；超时接管加防护令牌，旧请求不得写入或删除新占有者的行 | `B3-R3 …` |
| B3-R4 | `GrantRoleByOperations`、应急管理员停用与重置凭据 | 吊销会话的三条运维路径补 `session.revoked` 审计 | `B3-R4 …` |
| B3-R5 | `MustAuditActionsTest` | 必记动作守护从「动作字符串出现过」加强为「改状态的用例必写审计」 | `B3-R5 …` |
| B3-R6 | `WorkCalendarController`、RUNBOOK、OpenAPI | `lockedDaysConfirmed=true` 无 locked-day 权限返回 403 的 HTTP 测试；文档与 OpenAPI 写明该标志需要的权限 | `B3-R6 …` |
| B3-R7 | `scripts/ci/` | CI 加迁移版本单调性检查：新增迁移的版本号不得低于 main 上已有的最高版本 | 脚本 self-test |
| B3-R8 | B3 的 Medium 测试 | M2、M3、M5–M7、M9–M13 对应测试的 DisplayName 补复审编号 | — |

| INT-G1 | `AuditArchiveJob.java:32-36`、`platform_audit.properties:5-6` | 审计在线行到期移出（见 DEFERRED，生产部署前完成） | `INT-G1 …` |

| INT-M1 … INT-M16、INT-L6 … INT-L32 | 见 `phase1a/review/REVIEW_integration.md` §7 | 整合复审的 Medium / Low 全量清单（含文件:行与回归测试名）；派工时逐条拆进缺陷修复卡，标「已登记」的与上表合并 | 见报告 |
