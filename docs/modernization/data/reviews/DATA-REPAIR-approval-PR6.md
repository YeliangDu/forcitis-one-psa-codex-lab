## 数据设计审查
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-approval.md` 的「数据设计（PR-6：AP-01..05、AP-08）」一节（分支 `claude/one-1a-next-5-data-pr6` @ef04b3f4；基线 main 4d0b7c34）
- 核对：我抽读了 `DecideTasks` 的 `hookDecidesReason`、`ApprovalTask` 状态机的清空与关闭语义、`start_rounds` 唯一约束与写入次序，与卡的陈述一致；各 CHECK 逐条对过写入点，两个已知坑（CLOSED 保留处理人与认领时刻；系统自动通过无认领时刻、无内容摘要）均已避开
- 判定：
  - B3a：通过。状态机 `REJECTED/CANCELLED → RUNNING` 保留；`result_reason` 与状态成对；撤销类 `action_kind` 不存在（撤销走 `end()`，不写办理记录）的核实属实。
  - O3、O4、O5、O7：通过。AP-01 三表成对 CHECK、AP-02 `approval_tasks` 七条（单向，不带 `effect_kind` 条件）、AP-05 `start_rounds` 只追加触发器（行级 + 语句级，错误码 `restrict_violation`）。
  - O8、C1：通过。`<> ''` 禁空串、注释补「为空表示」；AP-08 `ck_process_archive_skips_failure_message` BETWEEN 1 AND 2000，类名链不含 `getMessage()`，日志与入库同口径，Java 侧按同值常量截断以免归档批中断。
  - O10：通过。外键目标为「仅由 uuid 列组成的唯一约束」，G-DDL1（NAMING_STANDARD:365）已放行；`approval_requests` 一侧 `DEFERRABLE INITIALLY DEFERRED`（先写请求后写首轮、首轮反向引用请求）；`ix_approval_task_records_instance_id` 随本 PR 加。
  - O12、T4：通过（基线开放期，改基线，附预期 diff，随库重建；提交 A（AP-01 Java）先于提交 B（DDL 与夹具））。
  - 用户决定吻合：审批「驳回、撤销后可原单再发起；加签人驳回也须填原因；删掉免填放行」（收件箱 2026-10-16-one-data-approval-resubmit）。
- 裁定（六个请裁点；与卡文不同处以本记录为准）：
  1. AP-03 与验收目录：**方案 A**（普通唯一索引，`current_instance_id` NOT NULL）。验收目录的两处改动（`ApprovalUniqueKeysIT:76-86` 改为「空值被 23502 拒绝」、`ApprovalOwnIdColumnsIT:179-180` 去掉谓词断言）须走 Acceptance-Ruling（先例 RULING-DR-TB01-1）；这是前提消失而非放宽断言，PR 单列理由。
  2. 投影登记：本 PR 先落对账 IT；注册表加 `projections`（列、重算来源、对账测试名）并入 DG-0 字段扩展，由 one-quality 建，不在本 PR。
  3. `ix_approval_task_records_instance_id`：随本 PR 加（NAMING_STANDARD §2.4a-2：外键列须有以它开头的索引），不判缓。
  4. 复合外键名：**沿用 `fk_approval_task_records_task_id`**（R-1b P4 卡写该名不变，避免改名牵动；首列即 `task_id`）；G-DDL3 实现时须豁免「作外键目标的唯一约束」，由 one-quality 记入守护规格。
  5. `failure_message`：**本 PR 不改列名**，列注释写明「类名链，不含异常消息」；改名并入 R-2a，届时按词表定名并同改 `ApprovalUniqueKeysIT:92`。上限取 2000。
  6. 范围：**纳入 `domain/ApprovalRequest.java`**（`completeTask`、`end` 的原因校验），否则无原因驳回在库层才失败、得 500 而非 422；卡 §3 允许改动据此补列。
- 另：AP-01 反转 `BeforeHookIT.java:203-212` 断言（`rejectionReasonCanBeLeftToTheHook` → `rejectionWithoutReasonIsRefusedEvenWhenTheNodeHasAHook`）与用户「删掉免填放行」一致，按 CLAUDE.md 第 5 条在 PR 与报告中单列理由；`IdempotentStartIT`、`AggregateConcurrencyIT` 夹具 SQL 只补原因值，断言不动。RULE-272、审批通用流程 T10 仍写钩子免填，文档同步已由 one-1a-next-5 转 one-coordinator，不在本 PR。
- 豁免与待用户决定：无。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `DATA-REPAIR-approval.md` PR-6 节 @ef04b3f4，含上述裁定）
