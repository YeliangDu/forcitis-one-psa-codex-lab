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

已合并 PAR-1 #175、FLW-1 #183、K-3a #190、只读核实命令 #197、C-14 定位 #235、CI 兜底 #232；模型策略（用户 2026-10-15）会话与测试代理默认 sonnet、opus 只用于首轮代码 / 守护复审与安全判定；在途队列按序：①#251 TestMssql 共用已并进代码合集 #263（分支 claude/one-1a-par-testmssql，不再推送）；②C-14 (f) 系统库本人用户纳入基线：opus 实现已完成（分支 claude/one-1a-par-f-baseline-impl 在 worktree agent-ab9626289e3b2a092，三个提交 2b552007、6e34d3c0、d961c857，验收 134 / 134、连接包单测 92 / 92 通过，基于旧 #251 未 rebase）；opus 首轮安全复审结论修后可合并，two High 调度裁定如下——H-1 补检查（msdb 本人用户直接授权按类别允许清单：库级只许 G CONNECT、对象级只许 G SELECT 与 G EXECUTE 和任意 D、其他 class 与带转授权一律违规，再以 HAS_PERMS_BY_NAME 查 CONTROL / ALTER / ALTER ANY ROLE / ALTER ANY USER / ALTER ANY SCHEMA / CREATE PROCEDURE 任一为真即违规以兜住角色嵌套，不得误判 one-ops 实测的 34 条平台默认授权，容器实测、RDS 实测交 one-ops 只读核实，补完 opus 复审），H-1 验收已完成（分支 claude/one-1a-par-f-h1 @25fdf755，基于 #251 分支，含 7b22fe4e 的 cherry-pick 6522f32d，八个反例对 (f) 实现全红、基线与 one-ops 样本形状全绿，全局性反例 finally 撤销、同 fork 顺序跑；新原因码暂定 SYSTEM_DATABASE_OWN_USER_PRIVILEGE，定位行字段只 reason 与 database，其余待实现阶段定），下一步等代码合集 #263 合入 main 后把验收与实现 rebase，再派 opus 实现 H-1 补检查（类别允许清单加 HAS_PERMS_BY_NAME 六项，注意 ③ IMPERSONATE 与 ⑧ WITH GRANT OPTION 的有效权限不会变真，只能靠直接授权类别清单拦）并 opus 复审；H-2 用户选 A（intent 234252），one-ops 在 C-14 探针阶段试把账号移出 SQLAgentOperatorRole，结果前 (f) 先按现写法、PR 单列待探针结果，能移出则基线收紧为不允许该角色，移不出则保留并写明残余风险；#251 已并进代码合集 #263，不再往 #251 分支推提交；H-2（SQLAgentOperatorRole 可启停所有本地作业，作业数为 0 只能事后发现）调度已转用户知情确认，PR 先单列为待用户确认的偏离，one-ops 在查 RDS 能否把账号移出 SQLAgentOperatorRole，用户确认后由 one-architecture 写入 ADR-0037 第 4 项 (f)；M-1（rdscore 授权读不到时走 .orElse(true) 改显式分支）与 L-3（rdscore 角色叫不出名称的单测）顺手改；#251 合入后实现与验收先 rebase 到新 main，PR 单列放宽、H-1 / H-2、四处断言改动（ReadOnlyBaselineTest 两处、ReadOnlyBaselineLocatorTest 三处、ReadOnlyVerificationCommandTest 一处，理由是用户纳入基线口径变化）、新原因码 OWNS_AGENT_JOB；G37 缺词 agent / job / own 若报缺补进 backend/glossary/terms.yaml 的 common_words；③日志客户端 IP 缺陷卡（LoginAdmission.java:90 与 EnterAdministratorMode.java:80，P3b 与 0a 无交集，测试代理先写失败测试、sonnet 实现、opus 首轮复审）；④两个超长验收文件拆分（ReadOnlyVerificationCommandIT 约 980 行、ReadOnlyAccountCheckIT 约 575 行）排在 P3b 含 H1 之后；⑤F-1 实现等 R-1b P5（验收分支 claude/one-1a-par-f1-acceptance @01793d22，52 条，ADR-0044 的 Q1 / Q3 / Q4 定稿后收紧，业务负责人存员工 ID businessOwnerEmployeeId，两个错误码待 Q4 后发 one-glossary 查重，实现前 rebase 并删 #212 转正条目）；⑥OPS-PORT 运维命令档案不起 Web 等 R-1b P2（分支 claude/one-1a-par-ops-port，先红测试 26b9b04b）；⑦K-3b 各块随 R-1b 子 PR、FLW-2 在 P4 之后；C-6 的 6 条 DEFECT 已记为回放方法限制未取证坐实。
