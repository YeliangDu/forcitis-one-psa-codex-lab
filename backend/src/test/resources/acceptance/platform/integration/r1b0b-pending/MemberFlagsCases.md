# R-1b-0b 待重写的验收用例（人员标志与两项不一致判定）

按 RULING-R1B0-7 从部门类型直读验收类（R-1b-0a，`*SourceFlagsIT`）移出，不参与本卡。R-1b-0b 开卡后由测试代理按新形态重写：两项不一致判定记在集成层组织来源同步的同步差异里（不进 workforce `IssueKind`），主体键按 uuid（卡第 14 节）。原代码在提交 f42b561d（分支 r1b0-acceptance-2）中该验收类的源文件，可作参考。

## 共用夹具（原样可复用，仍在该验收类）

- 真实 SQL Server（Testcontainers，`TestImages`），视图为 `docs/data/legacy-views/` 下 DBA 视图脚本的 schema、views 两段原文，基表为老系统形状：`dbo.ORG_MEMBER` 的 `STATE`、`IS_DELETED`、`IS_ENABLE` 为 smallint 可空，`ORG_PRINCIPAL.LOGIN_NAME` nvarchar(50)（one-ops 取证 2026-10-15）。
- 只读登录按 `ReadOnlyBaselineFixture` 建并与 RDS 基线比对；应用用 `SpringApplicationBuilder` 真实启动，组织来源连接指向夹具库。
- 人员（10 位 ID，名字 `Kestrel <标签>`，工号 `E-<标签>`，岗位 100；范围 = 内部且在根路径 00000001 下）：

| 标签 | ID | 部门 | STATE / IS_DELETED / IS_ENABLE | 登录名 | 说明 |
|---|---|---|---|---|---|
| P1 | 4100000001 | 2 | 1 / 0 / 1 | 13977770001 | 生产组合 1|0|1|有（304 人）；部门 2 负责人、区域 AR、计费人员 |
| P1B | 4100000002 | 3 | 1 / 0 / 1 | 13977770002 | 同上组合；上级 P1；非法值用例的受害者 |
| P2 | 4100000003 | 2 | 1 / 1 / 0 | 无 | 生产组合 1|1|0|无（6 人） |
| P3 | 4100000004 | 2 | 2 / 0 / 0 | 无 | 生产组合 2|0|0|无（943 人） |
| P4 | 4100000005 | 3 | 2 / 1 / 0 | 无 | 生产组合 2|1|0|无（1 人） |
| C1 | 4100000011 | 2 | 2 / 0 / 1 | 13977770011 | 构造：有登录名、STATE=2 |
| C2 | 4100000012 | 2 | 1 / 1 / 1 | 13977770012 | 构造：有登录名、IS_DELETED=1 |
| C3 | 4100000013 | 3 | 1 / 0 / 0 | 13977770013 | 构造：有登录名、IS_ENABLE=0 |
| C4 | 4100000014 | 3 | 1 / 0 / 1 | 无 | 构造：无登录名、STATE=1 且有效 |
| C5 | 4100000015 | 2 | 2 / 0 / 0 | 13977770015 | 构造：有登录名、2|0|0（两项判定都触发） |
| OUT | 4100000090 | 4（根外） | 1 / 0 / 1 | 13977770090 | 范围外 |
| EXT | 4100000091 | 2 | 1 / 0 / 1 | 13977770091 | IS_INTERNAL=0，范围外 |
| L0 | 4100000020 | 2 | 1 / 0 / 1 | 13977770020 | 夹具外，登录对照时临时插入 |
| L1 | 4100000021 | 2 | 3 / 0 / 1 | 13977770021 | 夹具外，登录用例时临时插入（STATE 非法） |

- 期望判定（每人每轮恰好这些，不多不少）：在职状态不一致 = (STATE=1) 与（有登录名）不同；记录已删除或停用而仍有登录名 = (IS_DELETED=1 或 IS_ENABLE=0) 且有登录名。结果：P2、C1、C4 各一条不一致；C2、C3 各一条「已删除或停用仍有登录名」；C5 两类各一条；P1、P1B、P3、P4 无。负责会话 one-1a-next-3 已确认 P2 这类（生产 6 人）每轮各一条、C5 两类各一条（2026-10-15）。

## 用例

1. **四种生产组合与判定**（原第 1 条，RULING-R1B0-6）
   - Given 夹具含四种生产组合（用 SQL 核对 1|0|1|有、1|1|0|无、2|0|0|无、2|1|0|无）与 C1–C5。
   - When 读组织快照并同步。
   - Then 范围内人员集合与登录名和改动前纯视图人员语句（测试内冻结的纯视图人员语句，来自提交 d3a9f07f）完全相同；每人在同步报告中恰好带有上表期望的判定，不多不少，且不出现第三种新类别。
   - 要点：判定改从集成层同步差异读取（0b 定接口），不再用 workforce `IssueKind`；不按来源枚举名断言。

2. **人员标志非法整轮失败**（原第 2 条人员部分，C-3、RULING-R1B0-2）
   - Given 先同步一轮成功；把 P1B 的 STATE 改为 3、0、NULL，或 IS_DELETED 改为 2、NULL，或 IS_ENABLE 改为 2、NULL（参数化七例），同时把 NAME 改为随机标记值。
   - When 组织同步。
   - Then 本轮 FAILED，日志有 `ALERT … organization.workforce … sync`，快照不写（P1B 的姓名仍为原值），失败原因含列名（`\bSTATE\b` 等），失败原因与本轮日志不含标记姓名、P1B 的 ID、登录名、`SQLServerException`、`Conversion failed`；`finally` 中还原。

3. **单人查找遇非法 STATE**（原第 3 条）
   - Given P1B 的 STATE=3（先证明干净时按企业微信 userid 能查到 1 人）。
   - When `OrgSource.findStaffByWecomUserId` 与 `findStaffByLoginName`。
   - Then 都抛 `ExternalSourceBrokenException`，异常链不含 ID、姓名、登录名、企业微信 userid、`SQLServerException`；`finally` 还原。

4. **有登录名但来源判为离职 / 已删除 / 停用**（原第 4 条，RULE-363）
   - When 同步。Then C1、C2、C3、C5 仍判为在职（`OrgDirectory.findEmployee(...).employmentStatus() == EMPLOYED`）；C1 一条不一致，C2、C3 各一条「已删除或停用仍有登录名」，C5 两条各一；缩水保护分子 `incomingEmployedCount` 等于纯视图语句中有登录名的人数（= 6：P1、P1B、C1、C2、C3、C5）；问题 detail 不含任何人员姓名与登录名。

5. **无登录名但 STATE=1 且有效**（原第 5 条）
   - When 同步。Then C4 判为离职并带一条不一致；P2 带一条不一致（STATE=1、无登录名、已删除）；P1、P1B、P3、P4 无此类问题。

6. **已删除或停用的人仍进快照**（原第 6 条，MEMBER_SCOPE 不变）
   - When 同步。Then P2、P3、P4、C2、C3、C5 都在快照中（`OrgDirectory.findEmployee` 姓名正确），在职状态只按登录名；`OrgSource.readSnapshot().staff()` 含这六人。当前实现下即为绿，作回归守护。

7. **人员基表诱饵**（原第 8 条人员部分）
   - Given 应用启动后在 `forcitis_one` 下建同名诱饵 `ORG_MEMBER`（所有人 STATE=2、IS_DELETED=1、IS_ENABLE=0），并用只读登录证明不带 schema 的 `ORG_MEMBER` 解析到诱饵。
   - When 同步。Then 判定取自 `dbo.ORG_MEMBER`：P1 无问题（读到诱饵会两类都有），C4 仍有不一致（读到诱饵则无）；其余人按上表。`finally` 删除诱饵。

8. **启动时缺人员标志列**（原第 9 条人员部分）
   - Given 夹具库 `dbo.ORG_MEMBER` 缺 STATE。When 启动。Then 拒绝启动，异常链同一行内含 `ORG_MEMBER` 与 `\bSTATE\b`。前提：0b 在读取列登记中登记这三列（运行时用途）。

9. **登录查人遇来源损坏**（RULING-R1B0-5）
   - 对照：先同步一轮（登录未命中登记只进快照已有的部门与岗位）；临时插入 L0，`WorkContext` 内调 `OrgDirectory.findEmployeeByWecomUserId("wx-l0")` → 返回在职员工。
   - 用例：同样插入 L1（STATE=3）→ 不返回员工（空或异常皆可，登录据此失败关闭），不登记（`findEmployee` 为空），日志有 ALERT（不是「来源不可用」的 WARN），日志与异常不含 L1 的姓名、登录名、ID、`SQLServerException`。`finally` 删除插入的行。
   - 完整 HTTP 登录需身份模块的角色与账号数据（CS-5.5），如需在 HTTP 层断言「无会话」放 `scenario/`。
