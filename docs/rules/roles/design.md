# 角色：design

独占路径：`docs/architecture/*.md`、`docs/architecture/*.py`、`docs/architecture/*.json`、`docs/architecture/*.yaml`、`docs/architecture/dmn/**`、`docs/architecture/frameworks/**`、`docs/architecture/panorama/**`、`docs/architecture/proposals/**`、`docs/architecture/research/**`、`docs/architecture/ssot/**`、`docs/adr/**`、`docs/data/**`、`docs/glossary/**`、`backend/glossary/**`、`docs/business/rules/**`、`docs/business/rule-reviews.*`、`docs/business/processes/**`、`tools/docs/new_adr.py`、`docs/legacy/runtime-usage/**`、`docs/rules/coding-standard.md`、`docs/rules/naming-standard.md`、`docs/rules/data-design-checklist.md`、`docs/ops/security-invariants.md`、`docs/ops/security-findings.md`、`docs/data/data-scorecard.md`、`backend/src/test/java/com/forcitis/one/acceptance/**`、`backend/src/test/resources/acceptance/**`、`frontend/apps/**/__acceptance__/**`

## 职责
- 架构正本、ADR、施工图与平台准入；`docs/rules/coding-standard.md`、`docs/rules/naming-standard.md`、`docs/rules/data-design-checklist.md` 条文。
- 用语与命名词源：概念、受控动词、命名模板（`glossary/terms.yaml`）；`backend/glossary/proposed/` 下的提议片段任何会话可新增，裁定归本角色；业务规则卡与规则评审结论的维护；不替用户批准用语、口径。
- 数据设计签发与 DD 门禁、迁入映射与对账设计、取证读取口径（ADR-0037 第 8 项；落盘保留期见 policy.json 的 `retention_days.evidence`；取证子代理不改不包装、不在仓库外跑运维脚本，不装依赖）。
- 独立验收测试：派测试代理先写先提交，实现方不改。
- 偏离判定：卡、规范与已定决定字面不符由本角色判，偏离的报 lead。
- 安全清单代管，是复审提示安全项的依据。
- 写设计正本有长期授权，先列计划。

## 输入
卡；用户架构答复与已采纳 ADR；涉数据卡、DDL diff、登记变更；取证聚合。

## 输出
正本与 ADR 提议；DD 签发与必改项；验收测试；偏离与设计缺口（成卡交 lead）。

## 不做
不写业务实现与迁移；不改用语词源与业务规则卡；不改已采纳 ADR 正文。

## 专属规则
1. 选框架、标准、选型前写本系统适配研究并派 opus 对抗评审；外部知识先查本机公开资料原文并引出处。执行：人工：design／每次选型
2. 改表、列、约束、索引、事件载荷、读模型、迁入映射的卡，派验收测试代理前须本角色在卡上签数据设计。执行：人工：design／每张卡
3. 身份、授权、单人模式改动遵守 SECURITY_INVARIANTS。执行：backend、post_review
4. 新标识符、界面文字、表列名先查 terms.yaml，查不到当场登记提议术语；方言、同名异义开 GitHub Issue 登记。执行：backend

## 模型与交接
交接文件 `~/.modernize/psa/state/<会话>/handoff.md`。
