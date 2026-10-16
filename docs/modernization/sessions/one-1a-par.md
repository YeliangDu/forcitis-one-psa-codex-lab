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

- `ReadOnlyBaseline` / `JdbcReadOnlyCatalog` 的判定（要改先找 one-1a-next-3）。
- R-1b 在途子 PR 的文件；Flowable id 生成器（P4）；现有守护文件——新增守护类先征得 one-1a-next-3 同意。

## 输入

- one-coordinator：派工与裁定（F-1 规格、K-3 拆分）；派工依据 `phase1a/NEXT_BATCH_PLAN.md` 第 8 节。
- one-1a-next-3：每张卡开工前确认独占构件；P5 合并信号（F-1 实现前置）。

## 输出

- PR → one-coordinator；PAR-2 合并后通知 one-ops 与 one-coordinator 出 rc.2（C-14 前提）。
- 代码类用语当场登记提议术语；非代码用语问题用 `glossary/new_term_issue.py` 登记给 one-glossary。

## 触发与节奏

- 事件：派工、前置 PR 合并（#177 → PAR-1、P5 → F-1 实现、R-1b 子 PR → K-3b 各块）。
- 定时：无。

## 自动化

- 钩子：压缩前快照；压缩或清空后注入本画像。
- 守护与脚本：`scripts/precheck.sh`、`build_all.py --check`；子代理用 `isolation: worktree`。

## 上下文文档

- 只有本画像（钩子总是注入画像本身）；派工依据见「输入」。

## 交接条件

PAR-1..PAR-5 完成或达到 §2.1 第 1 条阈值时在批次边界交接；F-1 实现的执行者届时由 one-coordinator 定。

## 权限边界

- 须问用户：建账号、起非登记镜像的容器、对外调用；改断言或删测试须写理由并在 PR 单列。
- 守护文件改动 PR 单列并要 opus 复审。

## 当前状态

已合并 PAR-1 #175、FLW-1 #183、K-3a #190、只读核实命令 #197、C-14 定位 #235、CI 兜底 #232、TestMssql 共用 #263、C-14 (f) 系统库本人用户基线含 H-1 / H-1′ / H-A / M-A / M-1 与守护单处白名单 #277（one-ops RDS 实测第 1–3、6、7 项已做：三个正式账号与样本账号逐项相同、新检查不误拒，证据 one-ops 的 handoff/B-5-dba/evidence/c14_f_msdb_effective_result.md；仅剩 H-2 探针结果后才出 rc、重跑 C-14，PR 残余风险单列：对象级 SELECT/EXECUTE 不限对象、H-2、Service Broker 与全文对象未计、只经 guest 判定不了）；模型策略（用户 2026-10-15）会话与测试代理默认 sonnet、opus 只用于首轮代码 / 守护复审与安全判定，收口规则：首轮复审后只有 High 继续在 PR 内修，Medium / Low 登记残余；后续按序：⓪词表改名小 PR（卡 docs/modernization/glossary/b2/GL_RENAME_CARDS.md，#280 合入后在 main 可见，用户已确认）：B2-B01 AccountDisabled→UserAccountDisabled、B2-B06 RoleDeleted→RoleAbolished、B2-B10 ForcedLogout→ForceLogout、B2-B09 五个事件改过去式（先 grep 类是否在代码里，不在只改词表侧并在卡上注明），同批删两份存量清单对应行，不碰 P5 动到的文件，先与 one-1a-next-4 对一下；⓪′TS-UNIQ 工时单唯一约束（排在改名小 PR 之后、F-1 之前；依据用户 2026-10-15 Q15、intent 233152、聚合草稿 §6 N-03；调度最新裁定覆盖此前两条：每人每天最多一张工时单，非工作日也算，唯一约束对所有状态生效、不做部分唯一；驳回或撤回后单子回到可修改状态、改原单再提交，每次提交是新的审批轮次，不设「已取消」终态；状态值按此流转定、名字先查词表，基线 V20261014140000 状态 CHECK 缺撤回状态而事件目录有 TimesheetCancelled（用户已定改名 TimesheetApprovalWithdrawn B2-B07，类名归 one-1a-next-4 的 P5，状态值归本卡，对齐不重复改）；最低时数拦截不在本卡，交 one-rules 等用户在决策页定；先核对现有代码驳回后能否改了重交，不能则本卡改；先写卡发 one-coordinator 看，再测试代理先写失败验收，基线开放期内直接改 cost.timesheet 基线附表结构 diff 由 opus 核对，约束名按 NAMING_STANDARD，疑问问 one-architecture）；①界面文字改名 PR（分支 claude/one-1a-par-ui-terms-r3，等 one-1a-next-4 的 P5 合入 uuid 桩 ID 后 rebase，三个测试文件冲突由我解：h5 task-detail-page.test.tsx、web app-routes.test.tsx、numbering-rules-page.test.tsx；另 P4 的「部门负责人审批」界面文字与 h5 审批测试数据看 P5 是否已覆盖）；②日志客户端 IP 缺陷卡（LoginAdmission.java:90 与 EnterAdministratorMode.java:80，测试代理先写失败测试、sonnet 实现、opus 首轮复审，本分支 claude/one-1a-par-log-ip 已从 main 切出）；③两个超长验收文件拆分（ReadOnlyVerificationCommandIT 约 1000 行、ReadOnlyAccountCheckIT 约 700 行，先与 one-1a-next-4 确认）；④F-1 实现等 R-1b P5（验收分支 claude/one-1a-par-f1-acceptance，实现前 rebase 并删 #212 转正条目，两个 Q4 错误码发 one-glossary 查重，登记 ADR-0032 用法偏离）；⑤OPS-PORT 运维命令档案不起 Web（分支 claude/one-1a-par-ops-port，先红测试 26b9b04b，先与 one-1a-next-4 确认 identity 独占构件）；⑥K-3b 各块随 R-1b 子 PR、FLW-2 在 P4 之后、对象名允许清单卡待 one-ops 答复；C-6 的 6 条 DEFECT 已记为回放方法限制未取证坐实。
