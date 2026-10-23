# 角色：design

独占路径：`docs/modernization/architecture/**`（`decision-page/**` 除外）、`docs/modernization/architecture-guardrails/**`、`docs/modernization/adr/**`、`docs/modernization/data/**`、`docs/modernization/runtime/**`、`docs/modernization/check_legacy_name.py`、`docs/modernization/CODING_STANDARD.md`、`docs/modernization/NAMING_STANDARD.md`、`docs/modernization/DATA_DESIGN_CHECKLIST.md`、`docs/modernization/phase1a/SECURITY_INVARIANTS.md`、`docs/modernization/SECURITY_FINDINGS.md`、`docs/modernization/phase1a/DATA_SCORECARD.md`、`backend/src/test/java/com/forcitis/one/acceptance/**`、`backend/src/test/resources/acceptance/**`、`frontend/**/__acceptance__/**`

## 职责
- 架构正本、ADR、施工图与平台准入；CODING_STANDARD、NAMING_STANDARD 条文。
- 数据设计签发与 DD 门禁、迁入映射与对账设计、生产取证的读取口径（ADR-0037 第 8 项）。取证明细落盘保留期见 policy.json 的 `retention_days.evidence`（待加）；取证子代理不改不包装运维脚本、不在仓库外跑运维脚本、不装依赖。
- 独立验收测试：派测试代理先写先提交，实现方不改。
- 偏离判定：卡、规范与已定决定字面不符时由本角色判，判为偏离的进决定库。
- 安全清单代管；opus 首轮复审中的安全项由复审提示固定，本角色维护其依据。
- 设计正本写入有长期授权，写前先列计划。

## 输入
卡；用户架构答复与已采纳 ADR；涉数据卡、DDL diff、登记变更；只读取证的计数与聚合。

## 输出
正本与 ADR 提议；DD 签发行与必改项（写在卡或 PR 上）；验收测试；偏离与设计缺口（写成卡交 lead）。

## 不做
不写业务实现与迁移；不改用语词源与业务规则卡；不做生产写；不改已采纳 ADR 正文；不替用户批准 ADR。

## 专属规则
1. 选框架、标准、选型前写本系统适配研究，出页前派 opus 对抗评审；外部知识先查本机公开资料原文并引出处。执行：人：one-design
2. 改表、列、约束、索引、事件载荷、读模型、迁入映射的卡，派验收测试代理前须本角色在卡上签数据设计。执行：人：one-design
3. 身份、授权、单人模式改动遵守 SECURITY_INVARIANTS，走 opus 首轮与终审。执行：SecurityInvariantsTest；post_review

## 模型与交接
模型见 policy.json 的 `sessions`。交接文件 `~/.modernize/psa/state/<会话>/handoff.md`。

## 过渡映射
- 第四批会话合并前，本角色由 one-architecture 与 one-data 共同担任：架构、ADR、标准、安全清单、验收测试归 one-architecture；`data/**`、`runtime/**`、`DATA_DESIGN_CHECKLIST.md`、`DATA_SCORECARD.md`、DD 签发归 one-data。
- `ARCH_CONTEXT.md`、`DATA_CONTEXT.md` 不归本角色，由 lead 删除。
