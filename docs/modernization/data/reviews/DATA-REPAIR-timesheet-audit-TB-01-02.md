## 数据设计审查
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-timesheet-audit.md` 的 TB-01（第 2、3 步）与 TB-02「数据设计」节（one-1a-next-4 分支 `claude/one-1a-next-4-data-pr2` @1279b69c）；TB-01 第 1 步用户已定、TB-03 已另签
- 核对：直接标识写入点我自己读了代码（`LoginWithSso` 的 `no employee for login name`、`WecomIdentification` 的 `no employee for WeCom user id`、`RegisterEmergencyAdmin` 的 `loginName=`），属实；19 行允许写入清单覆盖 41 处写入点的归类，抽查属实
- 判定与裁定（卡与实现以本裁定为准，与卡文不同处以本记录为准）：
  - TB-01 ①（已更正，U-7b 用户答「改写员工 ID」）：**不再新建** `audit-detail-exceptions.txt`，不需要带到期日的例外行与到期日守护；`OperationAttestation` 的操作人、见证人改写员工 ID。（原「建议到期日 2026-12-31」作废。）
  - TB-01 ②：19 行清单通过。第 12、13、14 行（登录名、企业微信 ID、`loginName=` 写进 `detail`）**并入 TB-01**，同属 ADR-0043 第 4 项第 1 条的直接标识写入：改为 `login name unknown`、`wecom user not bound`，`RegisterEmergencyAdmin` 删 `loginName=`；第 14、15 行（操作人、见证人）随 U-7b 改为员工 ID，按「代理键」处理；第 11 行（哈希前 8 位）允许（截断后无法还原、不能单独指向某人）；第 17 行 `ReleaseSyncRun` 入口限原因 1 至 200 字，通过。
  - TB-01 ② 四列分级登记（`INTERNAL + PI`，理由以「用途：」开头）通过，四段化随 ADR-0043 实施 PR，之前第二维按卡内分级表视为已登记。
  - TB-01 守护（`AuditDetailGuardTest`——只留拼接点禁字（含 `operator=`、`witness=` 姓名拼接）与格式检查，无到期日判断；`AuditWritePointsGuardTest`、`LoginAuditDetailIT`）通过。
  - TB-02 ①：N = 32768 通过（估算最大约 1.4K，`IdempotentResponseSizeTest` 落地后以实测为准，若实测接近 N 的一半须复核）。**超限处理改裁**：不存、**不释放幂等键**（保持「处理中」，重试得到处理中冲突直到 24 小时到期，避免已提交的写操作被重试重复执行）、`ALERT`、应答照常返回；不采用「释放键」。
  - TB-02 ②：白名单 `idempotent-endpoints.txt`（20 行）与「不在清单即不保存」通过；移出 5 个接口（`:reconcile`、`:release`、`source-references` 三个）通过，带键请求不再重放属预期，写进 PR 说明。
  - TB-02 ③：核对办法（`IdempotentEndpointGuardTest` 递归展开应答类型比对分级列与直接标识名单）通过；`source_references.source_id` 对 `EMPLOYEE` 类按个人信息处理，须在 `data-classification.txt` 登记（`INTERNAL + PI`），随 ADR-0043 实施 PR 或本卡顺带。
  - `response_text` 登记 `INTERNAL + PI` 通过。
- 豁免与待用户决定：无（U-7b 用户已答「改写员工 ID，阅读时再解析姓名」）。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `DATA-REPAIR-timesheet-audit.md` TB-01 第 2、3 步与 TB-02，分支提交 1279b69c，含上述裁定）

## 更正（2026-10-16，U-7b 用户已答）
用户答「改写员工 ID，阅读时再解析姓名」（收件箱 2026-10-16-one-data-u7b-attestation-names，intent `2026-10-16-…-one-data-操作登记的见证人`）。据此修改上面的 TB-01 裁定：
- `OperationAttestation` 的操作人、见证人改写员工 ID（运维命令入参改员工 ID），`detail` 不再含姓名；
- **不再需要** `audit-detail-exceptions.txt` 的 U-7b 例外行，也不需要该文件及到期日守护（c）；`AuditDetailGuardTest` 只保留拼接点禁字与格式检查，禁字表加 `operator=`、`witness=` 姓名拼接；允许写入清单第 14、15 行改为「代理键」，不再是 PI；
- 第 14 行的 `loginName=` 仍删；
- 签发行有效，范围按本更正缩小；其余裁定不变。


## 追加裁定（2026-10-16，PR-2 @3fd2b280）
- 超限计数器名按 NamingSurfaceTest G46 以声明模块开头，实现为 `forcitis.platform.web.idempotency.response.oversize`，认可，卡文少 `web` 段以实现为准。
- 复审 M-4「运维证明的员工 ID 须为存在员工」：准许移出 PR-2，作后续小卡（one-1a-next-5），条件：小卡有编号并报 one-coordinator；PR-2 说明里写明「运维证明的员工 ID 暂未校验存在」。该校验不做，则 U-7b 的「阅读时再解析姓名」会遇到解析不到的 ID。
