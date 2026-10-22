# one-1a-par

## 类型

工作

## 职责

- 部署前并行工程卡：与结构重构主线文件不相交的 PAR-1..PAR-5（`phase1a/NEXT_BATCH_PLAN.md` 第 8 节）。
- PAR-1 部署守护解耦、PAR-2 只读核实命令、PAR-3 FLW-1 引擎时钟、PAR-4 F-1 验收测试、PAR-5 K-3 列表分页契约。

## 独占

- 只读核实命令的入口类与档案配置（PAR-2）；`deploy_gate.py` 的 dev / prod 拦截分支（PAR-1）。
- Flowable 引擎时钟配置（`platform/approval/infrastructure/flowable/` 中只 `setClock` 的配置类，PAR-3）。
- F-1 验收测试（分支 `claude/one-1a-par-f1-acceptance`）；K-3a 的 kernel 分页常量、游标、`backend/architecture/list-paging-gaps.txt`。

## 不碰

- `ReadOnlyBaseline` / `JdbcReadOnlyCatalog` 的判定（要改先找 one-1a-next-5，当前数据修复线）。
- R-1b 在途子 PR 的文件；Flowable id 生成器（P4）；现有守护文件——新增守护类先征得 one-1a-next-5 同意。

## 输入

- one-coordinator：派工与裁定（F-1 规格、K-3 拆分）；派工依据 `phase1a/NEXT_BATCH_PLAN.md` 第 8 节。
- one-1a-next-5（当前数据修复线）：每张卡开工前确认独占构件；WF-REINSTATE 复职事件契约（ADR-0034-IMPL PR-3 前置）。历史交接报告引用保留为历史证据。

## 输出

- PR → one-coordinator。
- 发布相关结果只交 one-ops 核对并报关键节点；dev 当前暂停，只有用户恢复后由 one-coordinator 重排，不因 main 合入自动触发部署。
- 代码类用语当场登记提议术语；非代码用语问题用 `glossary/new_term_issue.py` 登记给 one-glossary。

## 触发与节奏

- 事件：派工、前置 PR 合并（#177 → PAR-1、P5 → F-1 实现、R-1b 子 PR → K-3b 各块）。
- 定时：无。

## 自动化

- 钩子：压缩前快照；压缩或清空后注入本画像。
- 守护与脚本：`scripts/precheck.sh`、`build_all.py --check`；子代理用 `isolation: worktree`。
- 本 worktree 的 .claude/session-name 固定为 one-1a-par（不入仓）；切分支后核对恢复识别。

## 上下文文档

- `docs/modernization/COORDINATOR_LEDGER.md`（当前派工与暂停项；冲突处以本日决定原文核对）
- `docs/modernization/phase1a/NEXT_BATCH_PLAN.md`（历史卡与设计依据；须结合当前派工重判适用性，下一步卡仍从调度取得）
- `docs/modernization/phase1a/HANDOFF_one-1a-par.md`（本会话交接：#509 收尾步骤、F2/接口清单/日历的顺序与要点、待办）
- `docs/modernization/phase1a/a8-split/SPLIT_PLAN.md`（按 ADR-0055 拆 A8 同步的切分方案，A 线；盘点明细同目录 INVENTORY.md）

## 交接条件

PAR-1..PAR-5 完成或达到 §2.1 第 1 条阈值时在批次边界交接；F-1 实现的执行者届时由 one-coordinator 定。

## 权限边界

- 须问用户：建账号、起非登记镜像的容器、对外调用；改断言或删测试须写理由并在 PR 单列。
- 守护文件改动 PR 单列并要 opus 复审。

**工作守则**（校验器暂不收独立章节，先并在本节）：调度会话胜任力评估（2026-10-16，`sessions/reviews/COMPETENCY_2026-10-16.md`）要求的四条改法，另加 SESSION_PLAYBOOK §11 新增两条：

1. 批次边界用 `get_usage` 自查上下文，到约定阈值按 §2.1 `/clear` 并交接，不拖到 87%。
2. PR 入队后不再向其分支推提交，新改动另开分支。
3. 交付前跑全相关守护，`scripts/precheck.sh` 由钩子强制；新增外部读取等登记类改动先查对应登记清单。
4. 写进卡的调度裁定必须附 intent 或 ADR 出处，未经核实的不写。
5. 卡和 ADR 字面不一致时不自定，先交 one-architecture。
6. 宣告完成前要有可复现的验证（命令与结果），已验证与未运行分开写。
7. 纯文档改动不开 PR：只推分支，向 one-coordinator 报分支与提交号（错题本 PAR-003）。
8. 缺陷修复交出前，对 diff 里每一处生产代码改动逐处回退，确认至少一个测试变红，PR 里写明每处对应的测试名（PAR-004）。
9. 临时文件（PR 正文、日志、补丁）一律写本会话 scratchpad，不写共享的 /tmp；开 PR 或改正文后立即回读 `gh pr view --json body`（PAR-005）。
10. 往文件写含反引号或 `$` 的文字用 `<<'EOF'` 或脚本，写完回读（PAR-006）。

要用户定的事提交「待决定收件箱」在线页，不在会话里用提问框；技术协调直接找 one-1a-next-5、one-ops、one-data；复审分级：安全和生产相关 opus 首轮加 opus 终审，代码 opus 首轮加 sonnet 复核，最多两轮，之后新发现的非 High 列为残余项。涉数据的卡先交 one-data 签发（DATA_DESIGN_GATE.md），表结构 PR 先合并，再写验收和实现。

## 当前状态

2026-10-22 晚：#604（KERR）已合 main 并报 one-merge 入队；#611（PAR-03 组织接口清单与守护，IMP-1021-PAR-03）CI 绿、已报入队；W2 PR-B（IMP-1022-PAR-01）实现已推 `claude/one-1a-par-w2-versions` @799d0118c，未开 PR，等 one-coordinator 对验收目录 4 处改动（EmployeeCodeNotKeyIT、WorkforceLengthLimitsIT、WorkforceRowConstraintsIT 两例）出裁定，另有 opus 首轮复审在跑。接下来：裁定与复审结论到后修正、开 PR；之后卡 E（workforce 入职日期与满周年查询）。细节见 phase1a/HANDOFF_one-1a-par.md。
