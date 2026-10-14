# FORCITIS one 现代化——agent 须知

开工前读 `docs/modernization/SESSION_PLAYBOOK.md`（开场 §3、收尾 §4、子代理与代码规则 §5、调度 §11、变更治理 §12、流程梳理 §13）与 `docs/modernization/STATUS.md`（先运行 `python3 docs/modernization/build_all.py` 生成，或看 generated-docs 分支）。

写代码的硬规则：
- 代码注释能不写就不写：用命名、类型和结构表达意图；只在「为什么」看不出来时写一行（反直觉约束、规范条款、外部系统怪癖、并发或性能取舍）。不写复述代码的注释、变更历史、注释掉的代码、无编号 TODO。
- 老代码只作证据，不得照搬：新代码从规格、规则卡、ADR、`docs/modernization/glossary/terms.yaml` 写起；领域模块不出现外部或老系统名称。
- 用语以 `terms.yaml` 为准，方言禁用；命名遵守 `NAMING_STANDARD.md`，守护测试不通过不得合并。
- 复刻老行为以生产数据为证据，不凭推测。
- 不为通过测试放宽或删除断言；改断言 / 删测试须写理由并在报告与 PR 中单列。
- 缺失或无效输入失败并告警，不静默跳过（规则卡明确允许的除外）。
- 只实现当前阶段规格所需，未来能力留在规则卡。
- 新标识符、界面文字、表列名先查 `terms.yaml`，查不到当场登记提议术语，不自造、不撞已有词。
- 测试不写密钥字面量（运行时生成）；报告前用 gitleaks 8.30.1 扫全历史。提交前钩子：`pre-commit install`（`.pre-commit-config.yaml`，与 CI 同版本）。
- 测试只断言本模块效果；异步用确定性等待，不写宽泛的 `never()`。
- 契约先行：接口路径、必填、表名变化时 OpenAPI 与架构文档同一提交更新。
- 并行子代理从同一基线出发，整合前同步最新 main，整合提交重跑全部守护。
- 建账号、起容器、对外调用前先问用户，本机临时环境也一样。
- 能一开始做对的不留到后续阶段；推迟的工作须在规则卡或文档写明「为何后做更有价值」，否则当期做对。
- 系统名称：新系统写 FORCITIS one，老系统写「老系统」（英文 the legacy system），不用「PSA」指代系统；老路径、表名、接口、代码标识、仓库名、会话名与用户原话照原样（`docs/modernization/check_legacy_name.py`）。
- 派子代理必须显式指定模型（haiku / sonnet / opus，按任务难度）。
- 委派 Codex 编码按 SESSION_PLAYBOOK §5「Codex 派工」：只交规格写死、可客观验证的机械编码，显式指定模型，独立 worktree，照常测试代理先写验收、opus 复审。
- 登记术语时中英文名都查重；通用错误码只在 `kernel` 定义；生产只读访问写明对象，超出先请示。
- 记录只改片段：`INTENT.md`、`SESSION_LOG.md`、`STATUS.md`、`adr/ADR-*.md`、`adr/README.md`、brief 由 `docs/modernization/build_all.py` 从 `intent/`、`session-log/`、`status/`、`adr/src/` 生成；用 `new_intent.py --session=<会话名>`、`new_log.py <会话名>` 新增片段（文件名带时刻与会话名，不手工起名），改完运行 `build_all.py`；ADR 与 brief 连同产物提交，INTENT / SESSION_LOG / STATUS 不入仓（只提交片段）；不直接编辑生成产物。
- main 受分支保护：所有改动经 PR，CI 三项通过后由调度会话 one-coordinator 合并；工作会话结束前向 one-coordinator 报告。
- 只在自己的 worktree 分支上工作并开 PR，不在主检出目录留未提交改动；用户已决定的事照做，有异议停下报告，不得自行推迟或改变。
- 会话管理按 SESSION_PLAYBOOK §2.1：一个会话一个工作单元、超过约 8 小时或压缩两次在批次边界交接；`one-*` 命名且不重名；权限模式与调度会话一致；每个完成点向调度会话报告；用户在本会话的决定当场记 intent 并报调度会话；后台任务与持久配置登记台账；不打印、不调用返回密钥的接口。
