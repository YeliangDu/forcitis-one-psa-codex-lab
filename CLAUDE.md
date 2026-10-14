# FORCITIS one（PSA 现代化）——agent 须知

开工前读 `docs/modernization/SESSION_PLAYBOOK.md`（开场 §3、收尾 §4、子代理与代码规则 §5、调度 §11、变更治理 §12）与 `docs/modernization/STATUS.md`。

写代码的硬规则：
- 代码注释能不写就不写：用命名、类型和结构表达意图；只在「为什么」看不出来时写一行（反直觉约束、规范条款、外部系统怪癖、并发或性能取舍）。不写复述代码的注释、变更历史、注释掉的代码、无编号 TODO。
- 老代码只作证据，不得照搬：新代码从规格、规则卡、ADR、`docs/modernization/glossary/terms.yaml` 写起；领域模块不出现外部或老系统名称。
- 用语以 `terms.yaml` 为准，方言禁用；命名遵守 `NAMING_STANDARD.md`，守护测试不通过不得合并。
- 派子代理必须显式指定模型（haiku / sonnet / opus，按任务难度）。
- 记录只改片段：`INTENT.md`、`SESSION_LOG.md`、`STATUS.md`、`adr/ADR-*.md`、`adr/README.md`、brief 由 `docs/modernization/build_all.py` 从 `intent/`、`session-log/`、`status/`、`adr/src/` 生成；用 `new_intent.py`、`new_log.py` 新增片段，改完运行 `build_all.py` 并连同产物提交，不直接编辑生成产物。
- main 受分支保护：所有改动经 PR，CI 三项通过后由 psa-coordinator 合并；工作会话结束前向 psa-coordinator 报告。
