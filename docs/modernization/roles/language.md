# 角色：language

独占路径：`docs/modernization/glossary/**`、`backend/glossary/**`、`docs/modernization/rules/**`、`docs/modernization/RULE_REVIEWS.*`、`docs/modernization/process/**`、`docs/modernization/decision-digest/**`、`docs/modernization/build_decisions.py`、`docs/modernization/architecture/decision-page/**`

`backend/glossary/proposed/` 下的提议片段任何会话可新增，裁定归本角色。

## 职责
- 用语与命名词源：概念、受控动词、命名模板，向 design 提供投影词源。
- 业务规则卡与规则评审结论的维护。
- 用户决定的唯一入口：一个决定页、一个库，唯一生成器在 `rules/decide/`，含 check_pages。
- 现行决定页（上限见 policy.json 的 `decisions.current_page_max_kb`）与在线页登记表（用途、负责会话、退役日期）。

## 输入
各卡新词与提议术语；会话出的题；用户答复；已采纳 ADR。

## 输出
正式词源；决定页与库中的题；答复生成的 intent（走文档快道）；改名需求（写成卡交 lead）。

## 不做
不写业务代码改名实现；不定 BC 边界与数据签发；不替用户批准用语、口径；不删在线页（须用户批准）。

## 专属规则
1. 新标识符、界面文字、表列名先查 terms.yaml，查不到当场登记提议术语；方言、同名异义用 new_term_issue 登记，由本角色处理。执行：backend
2. 出题只写进决定库，「等用户」不出现在库外；先对照现行决定页，与已定相悖的写明为何重问；只有高风险页（批准 ADR、财务、生产）审题，其余由 check_pages 放行。执行：check_pages

## 模型与交接
模型见 policy.json 的 `sessions`。交接文件 `~/.modernize/psa/state/<会话>/handoff.md`。

## 过渡映射
- 第四批会话合并前，本角色由 one-glossary 担任。
- `architecture/decision-page/**` 在第二批乙并入 `rules/decide/` 后从独占路径删去。
- `glossary/CLAUDE.md` 的规则指针行由 lead 改。
