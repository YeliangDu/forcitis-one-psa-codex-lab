# 审批流程键移出平台（REG-4）裁定

状态：已确认｜2026-10-22｜负责：one-architecture

触发：#562（REG-2，IMP-1021-NX5-05）的 opus 复审指出，`ProcessKey` 枚举与 `ApprovalTexts` 的「工时审批」仍留在 `platform.approval`，新增业务流程还要改平台代码，ADR-0066 决定 4 第 6 条判据未全部达到（one-coordinator 2026-10-22 转来）。

## 事实（`claude/nx5-processkey-data`）

- `platform/approval/api/ProcessKey.java`：枚举，唯一常量 `COST_TIMESHEET_APPROVAL(true)`，构造参数为 `routingFlagRequired`。
- `platform/approval/application/ApprovalTexts.java:20`：`case COST_TIMESHEET_APPROVAL -> "工时审批"`。
- 平台外的使用方只有 `cost.timesheet`（`SubmitTimesheet`、`GetTimesheet`、`CancelTimesheet`、`WithdrawTimesheet`、`ApplyApprovalResult`）。

## 裁定

1. **#562 不返工**：它达到了决定 4 第 4 条（`ProcessKey` 去掉单据类型入参，类型改为流程定义数据），本身无回退，可以合入。
2. **第 6 条另开一卡 REG-4，不并进 REG-3**：REG-3 改的是审计主体种类（`platform.audit`，基础层，含表约束迁移），与审批流程键是不同模块、不同评审要点，合在一张卡里会让一份 PR 同时改两个平台模块。两张卡都在 W0 收口窗口内完成（决定 4 第 7 条）。
3. **REG-4 的做法**：
   1. `ProcessKey` 由枚举改为 api 中的值对象（字符串键，格式按 G47 `{subdomain}_{bc}_{name}` 校验），平台不列任何业务流程。
   2. 流程键常量由声明它的业务 BC 放在自己的 api 里；工时审批的常量移到 `cost.timesheet` 的 api。
   3. `routingFlagRequired` 与流程显示名改为流程定义数据，与 REG-2 的单据类型同一写法（BPMN 扩展元素与 process 的 `name`），经引擎端口读取；缺失即部署错误，不静默。
   4. `ApprovalTexts` 不再有按业务流程分支的文案。
   5. 守护：`DocumentTypeRegistryGuardTest`（或同类守护）扩到「平台与 kernel 主代码不含业务流程键字面量与业务流程名」，豁免清单为空。
   6. 判据验收：新增一个演示流程只加 BPMN 文件与业务 BC 的常量，平台模块零改动（用测试夹具证明，不建真实业务流程）。
4. **与行政事务的次序**：行政事务的审批流程（用印、受控信息技术类别）不得往平台枚举里加常量。REG-4 合入前，行政事务用到审批的施工卡只做不依赖流程键的部分；7.4 本已因签章服务准入暂停。
