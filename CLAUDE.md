# FORCITIS one 现代化——agent 须知

开工前读：
- `docs/modernization/SESSION_PLAYBOOK.md`——怎么干活（开场 §3、收尾 §4、子代理与 Codex §5、修复批 §6.1、调度 §11、变更治理 §12、流程梳理 §13、DDD 与数据架构清单 §14、§14.1）；
- `docs/modernization/CODING_STANDARD.md`——怎么写代码（编码规范唯一入口，各条注明出处与守护）；
- `docs/modernization/NAMING_STANDARD.md`——叫什么、放哪里（命名、包结构、DDL）；
- `docs/modernization/STATUS.md`（先运行 `python3 docs/modernization/build_all.py` 生成，或看 generated-docs 分支）。

最硬的规则（细则与其余规则见上面三份规范：SESSION_PLAYBOOK、CODING_STANDARD、NAMING_STANDARD）：
0. 会话语言：始终用中文与用户对话、写报告与跨会话消息；派子代理时要求其用中文回复；新开会话继承此要求（用户 2026-10-15：「始终保持中文会话，新开会话继承这个要求」）。代码、标识符、命令保持原样。
1. 老代码只作证据，不得照搬：新代码从规格、规则卡、ADR、`docs/modernization/glossary/terms.yaml` 写起；领域模块不出现外部或老系统名称。
2. 用语以 `terms.yaml` 为准，方言禁用：新标识符、界面文字、表列名先查词表，查不到当场登记提议术语，中英文名都查重；文档里的方言、同名异义等非代码用语问题用 `docs/modernization/glossary/new_term_issue.py` 登记给 one-glossary，不自行裁定；命名遵守 `NAMING_STANDARD.md`，守护测试不通过不得合并。
3. 数据设计以 SESSION_PLAYBOOK §14、§14.1 全部条目为准：老系统与外部系统的表结构、主键、编码、状态值只作参考，自有表主键 UUIDv7。
4. 复刻老行为以生产数据为证据，不凭推测；生产取证不逐次请示，明细与个人信息不进会话与仓库，每次取证登记一行（ADR-0037）。
5. 不为通过测试放宽或删除断言；改断言 / 删测试须写理由并在报告与 PR 中单列。
6. 缺失或无效输入失败并告警，不静默跳过（规则卡明确允许的除外）。
7. 代码注释能不写就不写：只在「为什么」看不出来时写一行（CODING_STANDARD CS-1.1）。
8. 密钥：代码与测试不写密钥字面量（运行时生成）；不打印、不调用返回密钥的接口；提交前钩子 `pre-commit install`，报告前用 gitleaks 8.30.1 扫全历史。
9. 建账号、起容器、对外调用前先问用户，本机临时环境也一样。例外（用户 2026-10-15「长期授权测试容器」）：测试经 Testcontainers 在本机起、测完自动回收的临时容器，镜像限 `backend/architecture/test-images.txt` 登记的，不必逐次问；其他容器、常驻容器与对外调用照旧先问。
10. 系统名称：新系统写 FORCITIS one，老系统写「老系统」（英文 the legacy system），不用「PSA」指代系统；老路径、表名、接口、代码标识、仓库名、会话名与用户原话照原样（`docs/modernization/check_legacy_name.py`）。
11. 派子代理必须显式指定模型（haiku / sonnet / opus，按任务难度）；委派 Codex 按 SESSION_PLAYBOOK §5「Codex 派工」，派工用 `docs/modernization/templates/WORK_CARD.md`。
12. 记录只改片段：`INTENT.md`、`SESSION_LOG.md`、`STATUS.md`、`adr/ADR-*.md`、`adr/README.md`、brief 由 `build_all.py` 生成，用 `new_intent.py --session=<会话名>`、`new_log.py <会话名>` 新增片段，不直接编辑生成产物（SESSION_PLAYBOOK §4 第 0 步）。
13. 交付前运行 `scripts/precheck.sh`，输出贴进 PR「precheck.sh 输出」一栏。
14. main 受分支保护：只在自己的 worktree 分支上工作并开 PR，不在主检出目录留未提交改动；CI 三项通过后由调度会话 one-coordinator 合并；工作会话结束前向 one-coordinator 报告。用户已决定的事照做，有异议停下报告，不得自行推迟或改变。
15. 会话管理按 SESSION_PLAYBOOK §2.1：新开会话先写或认领画像 `docs/modernization/sessions/<会话名>.md`；一个会话一个工作单元、超过约 8 小时或压缩两次在批次边界交接；`one-*` 命名且不重名；权限模式与调度会话一致；每个完成点向调度会话报告；用户在本会话的决定当场记 intent 并报调度会话；后台任务与持久配置登记台账。

另须遵守：CS-1.8 只实现当前阶段、CS-1.9 当期做对、CS-1.10 契约先行、CS-5.5 / 5.6 测试只断言本模块与确定性等待、CS-7.6 并行子代理同一基线、CS-2.24 通用错误码只在 kernel（编号见 `docs/modernization/CODING_STANDARD.md`）。
