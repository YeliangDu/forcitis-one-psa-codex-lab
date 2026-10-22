# one-1a-par 交接（A 线人事与身份）

2026-10-22 交接（用户要求清空；上一次 10-21 的交接已全部执行完，本文件整篇替换）。清空后按下面顺序接续，细节以各 PR/分支为准。

## 在途 PR
1. **#604 KERR（IMP-1022-COO-02，通用错误码进 kernel）**：分支 `claude/one-1a-par-kernel-error-codes`，头 `446fcdd2f`（本地工作区 `elated-mclaren-be9a20` 现停在 `claude/one-1a-par-handoff-1022-2` 之前是此分支）。one-coordinator 已批（opus 首轮已过、-04 验收裁定已进 main、:99 显示名已按 -04 精神改）。**剩余**：等 CI 绿（头 446fcdd2f 的 run 在排队，上一头 c714d47be 已全绿），绿后直接报 one-merge 入队；入队后不再往该分支推。
2. 已合入 main 的本会话 PR（无需再动）：#509（删老待办，F）、#519（Seeyon 超时 10 秒）、#547（日历版本化迁移 PR-A，IMP-1021-PAR-02）、#595（F2 老待办残留清理，IMP-1021-PAR-04）。

## 下一步（顺序已定）
**IMP-1021-PAR-03 组织接口清单**（排在 KERR 之后，卡 E 之前）：`backend/architecture/platform-org-interfaces.txt`（格式 one-architecture 已定：每行 `<平台模块> -> <组织模块>::<NamedInterface 名>  # 用途 + 出处`，表头注释照 `module-dependencies.txt`；守护三项：ModuleDependencyTest 依赖只能落在清单内、清单内每个接口存在且有 @NamedInterface 且清单外组织 api 不得被平台引用、签名快照 `platform-org-interfaces.signatures.txt` 一变 CI 要求 PR 正文写“已通知平台 owner”；现有边先全量登记并逐个核实实际引用的类型：approval→workforce/attendance、identity→workforce；先登记 `WorkCalendar`、`OrgDirectory` 与 ADR-0068 人事事件）。守护代码自己写（不交 Codex），opus 复审；验收测试先交测试代理。先取任务登记片段 `tasks/IMP-1021-PAR-03.md`（若无则新建，任务号 `IMP-1021-PAR-03`）。

**卡 E（行政事务员工活动）**：`organization.workforce` 补入职日期 + 「满周年」查询，需求见 `claude/one-1a-admin-docs @5ff4ca55d` 的 `B31_ADM02_BLUEPRINT.md` §4 与卡 E；one-data 口径意见 `claude/one-data-handoff-1 @02ede0e11 / @fa035eab7` 的 `data/EMPLOYMENT_START_DATE_OPINION.md`（入职日放 W2 `employee_versions` 的 `employment_start_date`、迁入依据列 `employment_start_basis_code`、批量只返回前三类依据、闰日规则）。你现在是 `organization.workforce` 的 owner，卡 E 的设计经 one-arch-people；表结构先请 one-data 签发；在 PAR-03 之后，前置（W2 employee_versions、one-data 签表）未齐前只做对齐、不写代码。

## 待办池（不插队，记忆里各有一条）
- W2：INV-62 与 `ex_assignment_versions_overlap` 不一致（兼职任职存不进去）→ 先请 one-data 签；RULE-387（自定义范围里部门撤销/合并告警并生成范围复核项）、RULE-394（权限调整逾期只升级不自动收回）在 platform.identity 未实现，W2 组织命令之后、投产前做；`RegisteredEmployee` 对应 AGG-62、`SyncRun` 去留，判定后回 one-architecture。
- 日历后续（#547 已合）：PR-B（四眼流程 + Web/OpenAPI/前端，前提是 one-architecture 起轻量 ADR 取代 ADR-0030 第 13 行）、PR-C（使用方记版本，approval 记 `work_calendar_version_id`）；旧表 `work_calendar_years/days` 等 PR-B 接上后下线；注册表三张新表的 `writers` 随写表的用例 PR 加。设计依据 `data/reviews/WORK-CALENDAR-DDL-2026-10-21.md`（含更正十，在分支 `claude/one-data-handoff-1`）。
- F2 遗留 Low（未做，另开小卡）：`LegacyAdapterPlacementTest` 的 `moved` 无断言、测试里“two views/both databases/three adapters”措辞（含 `everyExternalSessionCarriesALockTimeout` 的显示名）、`MergedInbox` 命名、前端“新系统”页签等于“全部”、`StalledExternalSourceIT.ordinarySession` 无调用方。
- one-ops 侧：`PREDEPLOY_CHECKLIST.md` C-20 已过时（交 one-ops 关闭/改写，并清 one.env 残留口令）；运行账号 TEMP 权限核验。
- KERR 之后：G-7 其余同义码（“必须填写原因”“已过撤回截止点”）未收，行政事务 7.8 要用时再加 `KERNEL_REASON_REQUIRED`（工时与 approval 里各一条同名要一并迁）；快照 `ssot/i09-acceptance/tests.yaml`、`panorama.yaml` 的旧显示名随下次扫描刷新。
- 错题本 `quality/defect-bank/one-1a-par.md` 待补 PAR-008（rebase 用错 `--onto` 范围）、PAR-009（定向选测漏掉引用被删字段的测试类）、PAR-010（“已推送”未核对远端就陈述），并把 PAR-003..006 改为已落实。
- 文档分支 `claude/one-1a-par-calendar-plan`（日历方案与盘点，docs 只读）未进 main，报“分支+提交号”由 one-merge 收。
- 备份：`~/.modernize/psa/reboot-backup-20261021/codex-wt-f.tgz`（已核对内容已体现在 #509，工作区已删，可留可弃）。

## 工作守则（本会话新增）
①本机负载：只跑受影响模块的测试，全量 backend verify 与 Testcontainers 全套交 CI；同一时间只留一个后台构建或 Codex 任务；`precheck.sh` 每次推送前只跑一次（推送钩子 `pre_push_gate.py` 会自动跑，提交/改文件与 `git push` 分两次 Bash 调用）。②验收目录改动先发清单给 one-coordinator 出裁定、裁定进 main 后才开 PR，提交说明写 `Acceptance-Ruling:`（与 `Co-Authored-By` 之间不留空行）；改断言/删用例在 PR「改断言」栏单列。③卡与 ADR/手册字面不一致不自定，交 one-architecture（本次 F2 核实命令入口即如此）。④守护文件改动（`DataArchitectureTest`、`NamingSurfaceTest` 等）PR 单列并 opus 复审，且与同处并行 PR 先对齐写法。⑤陈述“已推送”前 `git fetch && git status -sb` 核对；git 写操作用绝对路径，工作区放 `.claude/worktrees` 下；生成产物不入库（本地 `build_all.py` 或 generated-docs 分支）。⑥身份、授权、守护、触发器类代码自己写不交 Codex；Codex 用 `run_in_background` + `< /dev/null`，报告写 scratchpad。⑦回复结尾只写状态，不向用户提问；技术问题报 one-coordinator，要用户定的进收件箱。
