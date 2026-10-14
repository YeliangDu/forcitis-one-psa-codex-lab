# RULE-280 修订建议：通过时不再补默认意见「同意」

日期：2026-10-14 · 起草：psa-1a-platform-bridge-reimagine · 状态：**已确认**（用户 2026-10-14：「留空并如实审计 (Recommended)」，经 psa-coordinator 转达）；代码在 Phase 1a 下一轮实现

确认前不改动代码行为。当前实现（`DecideTasks.DEFAULT_APPROVE_REASON = "同意"`）保持不变。

## 背景

- RULE-280 在第一批评审中的结论是「正确+修」，修正内容是「字段写入与任务完成在同一事务」。规则卡中「通过时意见为空就补成『同意』」这一点，当时没有单独讨论。
- 第二波 opus 复审（Nit）指出两点：
  1. 这个默认值的来源，是老代码为了绕开引擎限制而补的值。老注释原文为「bpm approveTask 意见必填，通过缺省补齐」，见 `psa/.../WorkflowApproveServiceImpl.java:73`。
  2. 新系统的审计会把这个补出来的「同意」记成审批人自己填写的意见，审计记录因此失真。
- 新系统的引擎封装并不要求意见必填，所以补默认值的技术理由已经不存在。

## 建议修订（规则卡 Specification 的变化）

| 情形 | 现行（老行为） | 建议 |
|---|---|---|
| 通过，意见为空 | 意见记为「同意」 | 意见**留空**。审计如实记录 `reason = null`；界面上显示为「（未填写意见）」，这是展示文案，不写入数据 |
| 驳回，意见为空 | 失败：WORKFLOW_APPROVE_REJECT_REASON_REQUIRED | 不变：驳回必须填写原因 |
| 退回，意见为空 | 失败（RULE-275） | 不变 |
| 系统自动通过（RULE-302 重复审批人） | 「系统自动同意：同一实例重复审批人」 | 不变。这是系统动作，审计的操作人为空（系统），原因是固定文案，不会冒充用户意见 |

修订后的 Given/When/Then 片段：

```
When a pass is submitted with reason ""
Then the decision is recorded with no reason
 And the audit entry for the decision has reason = null and actor = the approver
```

## 影响

- 代码：删除 `DecideTasks.DEFAULT_APPROVE_REASON` 和补值逻辑；调整对应的验收测试（按 RULE-280 的规则 ID 命名）；前端在任务详情和待办中对空意见显示「（未填写意见）」。
- 报表与对账：只在新系统中生效。老系统存量数据中的「同意」保持原样，不回写。
- 业务口径：如果业务方确实需要「通过必须填写意见」，属于另一条规则，届时以节点配置（是否要求意见）实现，不用补默认值替代。

## 请用户确认

1. 通过时意见为空，留空并如实审计，不补「同意」（建议）；或者
2. 保持现状，继续补「同意」；或者
3. 通过也必须填写意见（按节点配置）。
