# 角色：design

独占路径：`docs/modernization/architecture/*.md`、`docs/modernization/architecture/*.py`、`docs/modernization/architecture/*.json`、`docs/modernization/architecture/*.yaml`、`docs/modernization/architecture/dmn/**`、`docs/modernization/architecture/frameworks/**`、`docs/modernization/architecture/generated/**`、`docs/modernization/architecture/panorama/**`、`docs/modernization/architecture/proposals/**`、`docs/modernization/architecture/research/**`、`docs/modernization/architecture/ssot/**`、`docs/modernization/architecture-guardrails/**`、`docs/modernization/adr/**`、`docs/modernization/data/**`、`docs/modernization/runtime/**`、`docs/modernization/check_legacy_name.py`、`docs/modernization/CODING_STANDARD.md`、`docs/modernization/NAMING_STANDARD.md`、`docs/modernization/DATA_DESIGN_CHECKLIST.md`、`docs/modernization/phase1a/SECURITY_INVARIANTS.md`、`docs/modernization/SECURITY_FINDINGS.md`、`docs/modernization/phase1a/DATA_SCORECARD.md`、`backend/src/test/java/com/forcitis/one/acceptance/**`、`backend/src/test/resources/acceptance/**`、`frontend/apps/**/__acceptance__/**`

## 职责
- 架构正本、ADR、施工图与平台准入；CODING_STANDARD、NAMING_STANDARD 条文。
- 数据设计签发与 DD 门禁、迁入映射与对账设计、取证读取口径（ADR-0037 第 8 项；落盘保留期见 policy.json 的 `retention_days.evidence`；取证子代理不改不包装、不在仓库外跑运维脚本，不装依赖）。
- 独立验收测试：派测试代理先写先提交，实现方不改。
- 偏离判定：卡、规范与已定决定字面不符由本角色判，偏离的进决定库。
- 安全清单代管，是复审提示安全项的依据。
- 写设计正本有长期授权，先列计划。

## 输入
卡；用户架构答复与已采纳 ADR；涉数据卡、DDL diff、登记变更；取证聚合。

## 输出
正本与 ADR 提议；DD 签发与必改项；验收测试；偏离与设计缺口（成卡交 lead）。

## 不做
不写业务实现与迁移；不改用语词源与业务规则卡；不做生产写；不改已采纳 ADR 正文；不替用户批准 ADR。

## 专属规则
1. 选框架、标准、选型前写本系统适配研究，出页前派 opus 对抗评审；外部知识先查本机公开资料原文并引出处。执行：人工：design／每次选型
2. 改表、列、约束、索引、事件载荷、读模型、迁入映射的卡，派验收测试代理前须本角色在卡上签数据设计。执行：人工：design／每张卡
3. 身份、授权、单人模式改动遵守 SECURITY_INVARIANTS，走 opus 首轮与终审。执行：backend、post_review

## 模型与交接
模型见 policy.json 的 `sessions`。交接文件 `~/.modernize/psa/state/<会话>/handoff.md`。

## 过渡映射
- 第四批合并前由 one-architecture 与 one-data 共任：`data/**`、`runtime/**`、两份数据清单与 DD 签发归 one-data，其余归 one-architecture。
