## 数据设计审查
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-workforce.md` 的「数据设计补（PR-8b：WF-11 长度上限）」一节（分支 `claude/one-1a-next-5-data-pr8b` @b32c7f66；叠在 PR-8 之上）。长度上限依据见 `DATA-REPAIR-workforce-PR8.md` 末段（one-ops 来源列长度取证，取来源视图声明列宽）
- 判定：
  - O8：通过。十一条 CHECK 就地改写为「长度 AND 非空白」，名不变（名称与编码类 ≤ 500、邮箱 ≤ 100、部门简称 ≤ 255、`correction_reason` ≤ 200）；可空列写成 `col IS NULL OR (…)`。`char_length` 按码点计、来源按 UTF-16 单元计，库内只会更松，同步不会因此被拒。
  - O12、T4：通过（基线开放期，改基线；预期 diff 只是十一条 CHECK 的定义文本变化与一条列注释，无新增、删除、重命名约束，无列与索引变化）。
- 裁定：
  1. 不新增约束、就地改写同列非空白 CHECK：同意。
  2. `correction_reason` 入口 `CorrectLegalEntityAttribution.execute` 复用 `ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED`（422），长度量原值、不先 `strip`：同意——更正表存的是原值，入口与库内 CHECK 同一把尺，不会「入口放行、库内拒绝」得 500；与 `ReleaseSyncRun` 先 `strip` 的写法不同，因其存入值不同，各自入口与库内一致即可。前后空白计入长度；入口超长按先例 `ALERT organization.workforce` 记录。
- 豁免与待用户决定：无。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `DATA-REPAIR-workforce.md` PR-8b 节 @b32c7f66，含上述裁定）
