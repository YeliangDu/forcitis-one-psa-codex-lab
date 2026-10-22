# 交接报告：one-1a-next-5（数据修复批）

时点：2026-10-16，批次边界：数据修复批全部 PR 已开出并报 one-coordinator 入队。接手依据 `HANDOFF_one-1a-next-4.md` 第 3 节。

**会话约定**（沿用 one-1a-next-4）：全程中文；派子代理显式指定模型并要求中文回复，一律 `isolation: worktree`；开 PR 后确认 Auto-fix 关闭；PR 由 one-coordinator 审核入队，本线不合并；数据设计先拿 one-data 签发；代码 opus 首轮 + sonnet 复核，守护、权限、凭据 opus 首轮 + opus 终审。派工单另须写明：只按 PID 结束自己启动的进程、不按名字杀（错题本 D1）；只用 Testcontainers 与 `test-images.txt` 登记镜像，不起本机数据库进程（D3）；被测代码读真实时钟时测试不写死日期（D2）。

## 1. 先读

1. 本报告；错题本 `docs/modernization/quality/defect-bank/one-1a-next-5.md`（D1–D3）与 `one-1a-next-4.md`（D1–D4），交付前逐条回测。
2. one-data 签发记录 `docs/modernization/data/reviews/DATA-REPAIR-*-PR*.md`（#343 已合入）。
3. 私有证据 `~/.modernize/psa/handoff/one-1a-next-5/notes.md`（按时间的决定与待办，不入仓）。

## 2. 本会话产出（全部已开 PR、报 one-coordinator）

建议合并顺序（已告 one-coordinator）：#349 → #354 → #369 → #370 → #377 → #383 → #366 → #387。

| 项 | PR | 叠在 | 要点 |
|---|---|---|---|
| DR-TB01-FU1 运维证明员工须存在 | #349 | main | 退出码 1、沿用错误码（one-data 裁定）；RULING-DR-TB01-FU1-1/-2 |
| PR-6 审批 AP-01..05、AP-08 | #354 | main | 删钩子免填原因；`BeforeHookIT` 断言反转；RULING-DR-PR6-1/-2 |
| PR-7 编号 AP-09、11、13–17 | #369 | #354 | AP-14 依 one-architecture 更正只收窄审批请求单据类型；RULING-DR-PR7-1/-2 |
| PR-1 审计分区 TB-07/09/05（ADR-0048） | #370 | main | 按 UTC 月分区、两个 SECURITY DEFINER 函数、维护角色；opus 首轮 + 终审；RULING-DR-PR1-1；**正文列 one-ops 须同步的 c3/c9 角色与授权语句** |
| K-3b-4 审计接入分页契约（含 TB-04） | #377 | #370 | 固定顺序不提供 sort（one-architecture 判定）；导出 `maxRows+1`；RULING-K3B4-1/-2 |
| PR-8 workforce WF-04、05、09–12 | #366 | main | 5 条延迟外键、撤 fk_exceptions；RULING-DR-PR8-1 |
| PR-3 审计 TB-06、TB-08 | #383 | #370 | 只落「方式非空⇒结果非空」 |
| PR-8b WF-11 列宽、更正原因 200 | #387 | #366 | 上限取来源视图声明列宽（one-data 取证裁定） |

## 3. 在途

- 无未开 PR 的工作。叠放的 PR（#369、#377、#383、#387）在下层合入后若与 main 冲突，按 merge main（不 rebase）解冲突、重跑同夹具测试后报 one-coordinator。
- 本机 worktree 残留的子代理分支与 `pr*-local`、`k3b4-tests`、`pr3-tests` 等本地分支可在 PR 合入后清理。

## 4. 后续小卡（各 PR 正文已列，未起卡）

- 证明对象只能经校验工厂产生（FU1 复审 M-1）。
- identity `JdbcAccountRepository` 地址判定偏宽，与审计侧（已收紧）对齐 ID-12。
- `DataArchitectureTest` 校验注册表 `failed_policy` 格式（并入 DG-0，one-quality）；`NumberingRule.propose` 校验 `startValue <= capacity`；`DdlNamingTest` 认表级 CHECK；`DdlNamingTest` 兜底不覆盖无 schema / TEMP / 引号标识符。
- 调度器默认处理器与 `ProcessArchiveJob` 探测失败日志可能带原始异常消息（one-quality 统一）。
- 归档批次两个 uk 触发时只有调度器 ERROR、无 ALERT 与计数（PR-3 设计节第 3 条）。
- 来源视图启动检查只核列名不核列宽（PR-8b 复审 L3）。
- 非空白 CHECK 用 `btrim` 只去半角空格，统一写法交 one-data；同列多外键命名补 NAMING_STANDARD §2.4。
- 触发器命名统一（one-data 起草条款，交 one-coordinator 排全库小卡）。
- AP-12、AP-18（legacy_numbers）待用户「生产不从老系统接任何东西」的口径。
- 用语：岗位 `DELETED` 与组织单元 `REVOKED` 同义异名已登记给 one-glossary（随 #366）。

## 5. 2026-10-21/22 单据类型登记线（接续，覆盖本报告第 3 节"在途"）

时点：2026-10-22 06:50，上下文约 75%。

| 项 | PR / 分支 | 状态 |
|---|---|---|
| REG-1 单据类型登记表与 kernel `DocumentTypeCode` | #507 | 已合入 |
| 编号版本来源类别（SEC-1021-COO-01，任务号 IMP-1021-NX5-03） | #524 | 已合入；开放项：迁入角色 `history_loader` 写 MIGRATION 行被触发器拒，迁入规格评审前二选一，改触发器回 one-data |
| 错误码两项（IMP-1021-NX5-04） | #528 | 已合入 |
| P0 验收测试（RULE-340/337/308/304 重投，13 例） | #546 `claude/nx5-p0-acceptance` | 已批准入队，待 CI；红的 RULE-376 三例留 `claude/nx5-p0-acceptance-gaps`（缺口卡 IMP-1021-COO-11）；RULE-304 已按 D-1a-66 改回口径，非缺口 |
| REG-2 ProcessKey 去入参（IMP-1021-NX5-05） | #562 `claude/nx5-processkey-data` | 补修 D1（启动校验、分类错误码、命名空间、单测）本地 verify 已全绿待提交推送，sonnet 复核两轮提出的 EngineWiring 漏捕获 DomainException 已改；one-architecture 裁定可合入不返工 |
| 任务片段状态补登 | `claude/nx5-task-status` | NX5-01/03/04 补为已合并，待 one-merge 收 |

**下一步（按序）**：①#562 提交推送并再请 sonnet 复核（只看 D1 修复）、CI 绿后报 one-coordinator；②RULE-412 架构守护（时间表上下文不读加班审批/补休余额，自己写，ArchUnit）；③REG-3 审计对象类别登记表（ADR-0069 第 14 条；`audit_subject_kinds` 表、`audit_entries.subject_kind` 改外键、SYNC_RUN 以 ACTIVE 入表归 one-1a-par 退休、`STORED_FILE` 一并种入——one-platform-file 单元 5 会改枚举与 CHECK，谁先合谁对齐；表结构交 one-data、opus 首轮复审）；④REG-4（one-architecture 裁定 `REG4_PROCESS_KEY_RULING_2026-10-22.md` @dbd25294：ProcessKey 改值对象按 G47、流程键常量移 cost.timesheet api、routingFlagRequired 与显示名改流程定义数据、ApprovalTexts 去业务分支、守护扩到业务流程键/名、夹具证明新增流程零改动；已向调度要任务号与排序）；⑤行政事务单据类型登记卡（清单见 `claude/one-1a-admin-docs` @5ff4ca55d 的 IMP-1021-ADM-02，排在 REG-2/3 之后）。

**本期教训（待入错题本）**：①zsh 里 `$P:refs/...` 的 `:r` 会被当修饰符，推送一律 `"${P}:refs/heads/<分支>"`；②`git add -A` 会把误改的验收文件扫进文档提交，先 `git reset <验收目录>`；③全局 perl 替换前先限定文件（`effectiveTo` 误伤过 workforce）；④改验收目录须带 `Acceptance-Ruling` 或 `Acceptance-Test-Author` 尾注，裁定记录须先在 main 上才被认；⑤PR 回复里写「预期表结构 diff」，未用 pg_dump 实测要如实写；⑥生成物（如 `FREEZE_FACTS.md`）会随 main 输入过期，CI 的 build_all --check 会红，重新生成作单独提交。

## 6. 2026-10-22 下午增补（上下文约 85%，到批次边界请接手）

| 项 | PR / 分支 | 状态 |
|---|---|---|
| REG-3 审计对象类别登记表（IMP-1022-NX5-06） | #599 `claude/nx5-audit-subject-kinds` | opus 首轮「需修后再审」，已改：B1 退休类别可保留常量（启动校验不再要求删常量）、写入路径拒绝退休类别（`refuseWhenRetired`）；B2 守护读全部审计迁移且含 RETIRED；M2 校验挪到 `SmartInitializingSingleton`；M3 核生效日。**未采纳 M1**（has_object 由生成列+复合外键驱动，新增无对象类别仍要改 CHECK）——请 one-data 签发时一并裁。验收补丁在 scratchpad `reg3-acceptance.patch`（`AuditSubjectColumnsIT` + `P5Names`；按 RULING-COORD-20261022-03：无 ID 未知类别仍 23514 加注释、断言旧 CHECK 不存在、登记表集合与 KINDS_* 一致、has_object 与 ck_subject_id 一致、DisplayName 同步），待裁定进 main 后 `git apply` 并带 `Acceptance-Ruling` 尾注提交。 |
| RULE-412 架构守护（IMP-1022-NX5-07） | `claude/nx5-rule412` @710d6c4b | 已推未开 PR：`TimesheetOvertimeSeamGuardTest`（不依赖 leave 包、module-dependencies 无到 leave 的边、工时代码与迁移不含加班/补休词汇，含反例自测，破坏验证过）。 |
| REG-4 ProcessKey 值对象（IMP-1022-COO-01） | 未开工 | one-architecture 裁定 `REG4_PROCESS_KEY_RULING_2026-10-22.md` @dbd25294；W0 收口窗口内、行政事务第一个审批流程之前完成。避开 `PLATFORM_APPROVAL_TRANSITION_ILLEGAL`、`CONCURRENT_CHANGE` 两个码（one-1a-par 的 KERR 卡要迁成 kernel 通用码）。 |
| 行政事务单据类型登记（卡 N） | one-1a-admin | 改由 one-1a-admin 写登记行，本会话只复核。 |
| #546 / #562 | 已批或待批 | #546 已按 0cf44d73 重新入队；#562 sonnet 两轮复核通过，待 CI 与调度批。 |

**接手顺序**：①看 #599 的 one-data 签发评论与 opus 复核，等裁定编号进 main 后应用验收补丁并重跑 verify；②开 RULE-412 的 PR；③REG-4；④复核 one-1a-admin 的登记行。platform.numbering 的 owner 现为本会话（one-architecture 7.8）。

**本期补充教训**：⑦CHECK 先于外键被评估，同一行违反两者时报 CHECK 错误码；⑧REG 类改动先 grep 验收目录里对旧 CHECK 名的引用（`P5Names` 等）并提前要裁定；⑨`git stash` 是共享栈，别用，换分支前把补丁存成文件；⑩推送命令不要和 `cd 临时目录` 放同一次调用（推送前钩子会按命令里的目录出错）。

**15:50 增补**：#599 head 88827c3a 已含 opus 首轮修复；main 的 GOV-1022-QUA-01 把 `FREEZE_FACTS.md` 移出仓库——#546 分支（claude/nx5-p0-acceptance）里我曾重新生成过它，合 main 时 modify/delete 冲突，处理为 `git rm`。验收补丁路径 `…/scratchpad/reg3-acceptance.patch`（会话临时目录，若丢失按第 6 节要点重写：两文件 `AuditSubjectColumnsIT`、`P5Names`）。

## 7. 2026-10-22 22:50 增补（上下文 30%，批次边界）

| 项 | PR / 分支 | 状态 |
|---|---|---|
| RULE-412 守护（IMP-1022-NX5-07） | #607 | 已合入 |
| 函数固定 search_path（IMP-1022-COO-03） | #622 | 批入队，条件 CI 全绿；opus 首轮已改、sonnet 复核通过；设计稿偏离三处 one-coordinator 已转 one-data（已写入设计稿第 7 节） |
| REG-3 审计对象类别（IMP-1022-NX5-06） | #599 | 等 one-data 签发与 M1 裁定；验收补丁已带 `Acceptance-Ruling: RULING-COORD-20261022-03` |
| KERR-2 `KERNEL_REASON_REQUIRED`（IMP-1022-COO-04） | #629 `claude/nx5-kerr2` | opus 首轮通过；与 main 在 `kernel.yaml` 冲突已解（取 main 版词条，中文名「原因不能为空」），head 817ea2c32→57cc0dab3，CI 绿后须回调度再批；worktree `.claude/worktrees/nx5-kerr2` |
| 迁入写入通道·编号部分（IMP-1022-NX5-08） | #634 `claude/nx5-migration-writer-numbering` | opus 首轮「需修后再审」：高危 H1（迁入角色可直接插已确认行）已改为迁入行只能待确认插入；M1 补守护、L1 默认拒绝、L3 负例；**sonnet 复核中**；本机 Docker 停了，`MigrationWriterRoleIT` 等 IT 本地未实测，靠 CI；M2「c3/c9 建库脚本未建迁入角色」已报 one-coordinator 转 one-ops |
| 用语议题 | `glossary/issues/` 两条 | 「原因/理由」「属主角色、迁入角色」已登记给 one-glossary |

**接手顺序**：①等 #634 sonnet 复核结果，改后看 CI（Docker 恢复后可本地跑 `MigrationWriterRoleIT`、`RuleVersionSourceIT`、`NumberingRowRulesIT`），绿后贴结论、报调度批；②#629 CI 绿后回 one-coordinator 说明 head 已变请再批；③#599 看签发；④REG-4（one-architecture 裁定 `REG4_PROCESS_KEY_RULING_2026-10-22.md`，任务号 IMP-1022-COO-01）。

**本期教训**：⑪提交与推送分两次调用（推送前钩子按当时 HEAD 跑 precheck）；⑫另开 worktree（`git worktree add ../nx5-kerr2 -b <分支> origin/main`）可在 opus 复审读着当前目录时并行做下一张卡，不用切分支；⑬`build_terms` 报「基线已过期」要同时删 `term-rules-baseline.json` 的键并调低对应规则 `count`；⑭合 main 时 `kernel.yaml` 这类提议词条文件最易冲突，先看 main 上是否已有同 id 词条；⑮前端生成物手改后用 `pnpm install --frozen-lockfile --offline` 再跑 precheck，不要靠本机缺依赖放过；⑯本机 Docker 停了不要自行起，写明未实测交 CI。

**22:55 补记**：#634 的 sonnet 复核通过、结论已贴 PR、CI 已绿，等调度批；#629 CI 因 runner 的 docker keychain 报错失败（非代码问题），已重跑失败任务。本轮交接以第 7 节为准。
