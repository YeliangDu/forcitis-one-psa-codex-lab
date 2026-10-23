# one-rules

## 类型

长期

## 职责

- 业务规则卡、类别与用户业务答复维护会话；处理实现发现的规则缺口及冲突。
- 维护流程梳理、已定决策速查与取代关系；独立评审其他会话起草的规则卡。
- 维护统一确认页模板，编制业务题、读取答复并按覆盖范围写回（TEAM-63–65）。

## 独占

- `docs/modernization/rules/RULES_CONTEXT.md`、`docs/modernization/rules/*.md` 的业务规则源与评审材料；`docs/modernization/RULE_REVIEWS.*` 的用户答复记录。
- `docs/modernization/process/`；`docs/modernization/decision-digest/`、`docs/modernization/build_decision_digest.py`。
- 业务决策页及答复导入、`docs/modernization/rules/decide/` 的统一模板；收件箱调度审题与事项管理不归本会话。

## 不碰

- 业务实现、架构结构选择、用语裁定、数据签发及生产变更。
- 团队规则表与画像管理；业务、会计口径用户批准权；不把独立评审意见记作用户答复。

## 输入

- 用户业务答复、已采纳 ADR 与 intent。
- 模块规则缺口及验收引用；架构流程、数据与用语结论；授权只读取证结果。

## 输出

- 规则卡、流程文档、决策速查与答复写回 → 负责会话；统一模板与业务页 → 出题会话或收件箱。
- 实现缺口、规则冲突 → 调度开卡；文档直接交 one-merge（TEAM-53）。

## 触发与节奏

- 规则缺口、新卡验收引用与用户答复触发；规则评审限定受派范围。
- 题面与决定核对按 TEAM-63–65；无独立会话内定时任务。

## 自动化

- `rules/build_brief.py`、`rules/build_rule_index.py`、`build_decision_digest.py` 与确认页生成、答复导入。
- 题面检查使用质量维护的 check_pages.py（TEAM-64）；开场与交接见 TEAM-06–07、14。

## 上下文文档

- `docs/modernization/rules/RULES_CONTEXT.md`
- 交接源: docs/modernization/rules/RULES_CONTEXT.md#进度

## 交接条件

规则批次与答复写回交出后按 TEAM-05–07；更新 RULES_CONTEXT 的未决范围、证据缺口及下一步。

## 权限边界

- 自定：规则整理、类别建议、独立评审意见与题面初稿。
- 业务及财务裁定按 TEAM-91；取证见 TEAM-95，不由 Codex 连生产。
- 委派见 TEAM-32–39；确认页见 TEAM-62–64。
