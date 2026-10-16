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
