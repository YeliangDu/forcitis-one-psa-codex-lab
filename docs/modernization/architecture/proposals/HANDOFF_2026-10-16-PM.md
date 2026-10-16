# one-architecture 交接（2026-10-16 下午）

接续先读本文件，再读 `ARCH_CONTEXT.md`。上午交接 `HANDOFF_2026-10-16.md` 只作历史。

## 分支与 PR

- PR #350 `claude/one-architecture-adr-0050-0051`：ADR-0050、0051 采纳（intent 130636）＋施工方案第 2 版等，待入队。
- 分支 `claude/one-architecture-adr-governance`（已推送，未开 PR）：删 3 份旧草稿、ADR 实践研究、待决盘点、ADR 规则修订稿第 3 版与批准页、施工方案定稿（进行中）、U-12 重问页、职位目录草稿（进行中）、Q-04 研究计划（进行中）。规则批准后与 one-quality 同一 PR 提交（readme＋生成器＋new_adr.py＋评审模板＋SESSION_PLAYBOOK）。

## 待用户（收件箱）

- ADR 规则修订 8 题 https://claude.ai/artifact/9W7cPxpAGJLLot67gJdmqQ
- 员工岗位生效日期 U-12 重问 https://claude.ai/artifact/2ZXNdbKNsnGtPhe5U5mJwQ
- 待决页 Q-01 未答；Q-02 随岗位体系重梳包定；Q-04 待研究。

## 已定（今日 intent）

090004 第③步答复；112800 对照标准 L-1–L-12；114200 F-1–F-5、ADR-0049；120544 售前回溯 P-1–P-5；124649 A-1（认可的规划进 SSOT）；130636 ADR-0050/0051 批准；133348 施工方案（自成体系、老系统加 A8 不接、一次投产、全量历史进交易库、全模块）；134134 待决 14 题；134135 投产 2028-01-01。

## 在途

1. 施工方案定稿（含须修订 ADR 清单、2028-01-01 倒推日历）→ 报 one-coordinator，须修订的已采纳 ADR 另立过渡 ADR。
2. 岗位体系重梳：② 职位目录草稿（给 one-data 映射）、④ 按职位授权规则、⑤ 确认页（含 ADR-0029 去向）。
3. Q-04 收入确认对象研究计划 → 报 one-coordinator。
4. ADR-0050/0051 采纳后的 SSOT 落地 PR：`architecture/business/` 三份定稿、`contexts.yaml` 28 个规划 BC 与 7 个退役、规划依赖边与聚合（Q-11 一次认可）、生成器新校验。
5. one-glossary 四个 BC 中文名题（含建议 organization.leave 改「考勤与休假」）定后同步 ADR-0051 与 contexts.yaml。
