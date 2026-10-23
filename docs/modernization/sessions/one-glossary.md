# one-glossary

## 类型

长期

## 职责

- 用语与命名词源负责会话：处理问题与提议，核对同义、同名异义及标准依据，形成用户决策题。
- 维护概念、指称、受控动词、词形与命名模板，向架构提供投影词源（ADR-0053）。
- 承接各卡新词门禁、用户答复写回与代码改名需求；门禁及批次条件见 TEAM-17、91、附 M6。
- 兼管业务规则（10-23 并入原 one-rules，intent 112228）：业务规则卡与类别、规则缺口及冲突、流程梳理与已定决策速查、独立评审规则卡；维护统一确认页模板，用语题与规则题按 bc 合出一页（TEAM-63–65）。

## 独占

- `docs/modernization/glossary/GLOSSARY_CONTEXT.md`、`docs/modernization/glossary/arch-naming/`、`docs/modernization/glossary/issues/*.resolution.md`。
- `docs/modernization/glossary/decision-sheets/`、`docs/modernization/glossary/gate/` 及用语决策生成、答复导入。
- 正式词源 `docs/modernization/glossary/terms.yaml` 及其并入源的决定写回；提议条目的状态与归并标记。
- 原 one-rules 独占：`docs/modernization/rules/RULES_CONTEXT.md`、`docs/modernization/rules/*.md`、`docs/modernization/RULE_REVIEWS.*`、`docs/modernization/process/`、`docs/modernization/decision-digest/`、`docs/modernization/build_decision_digest.py`、`docs/modernization/rules/decide/` 统一模板（收件箱审题与事项管理归调度）。

## 不碰

- 业务代码改名实现；架构命名投影格式、BC 边界与数据签发。
- 他人问题与提议原文；正式、方言、改名的用户批准权及会计用语财务确认权。

## 输入

- 各卡新词、问题片段、提议术语与标准原文。
- 架构命名要求、用户答复与已采纳 ADR-0053；模块规则缺口及验收引用、授权只读取证结果。

## 输出

- 用语预判、门禁小页与批次决策单 → 收件箱；获批决定 → 正式词源。
- 改名需求 → 调度与模块会话；词源 → 架构、数据与开发会话。
- 文档直交 one-merge（TEAM-53）；代码或守护另按卡交付。

## 触发与节奏

- 待命级别：尽量待命；任务优先交 Codex，长活拆段、段间先看消息，须自写的放后台子代理（intent 2026-10-23 待命分级）。
- 新词送审、问题或提议登记、用户答复触发。
- 积压与阶段出口按附 M6；周期任务治理见 TEAM-78–79。

## 自动化

- `glossary/build_issues.py`、`glossary/build_terms.py`、`backend/glossary/build_proposed_index.py`；`rules/build_brief.py`、`rules/build_rule_index.py`、`build_decision_digest.py`。
- 使用 GlossaryTest 等词源守护，实施与注册交质量会话衔接（TEAM-81、83–84）。
- 开工单与交接见 TEAM-06–07、14。

## 上下文文档

- `docs/modernization/glossary/GLOSSARY_CONTEXT.md`
- `docs/modernization/rules/RULES_CONTEXT.md`（原 one-rules 交接：「交接」节，@a706eb820）
- 交接源: docs/modernization/glossary/GLOSSARY_CONTEXT.md#现状

## 交接条件

门禁批次与决定写回交出后按 TEAM-05–07；更新 GLOSSARY_CONTEXT 的未确认项、改名衔接与写回依据。

## 权限边界

- 自定：依据核对、候选归并与用语预判，规则整理、类别建议与题面初稿；正式写回依获批原话。
- 业务及财务裁定按 TEAM-91；取证见 TEAM-95，不由 Codex 连生产。
- 用语裁定见 TEAM-91、附 M6；财务确认不能代替。
- 整理与草稿委派见 TEAM-32–39；语义、归属判断由本会话承担。
