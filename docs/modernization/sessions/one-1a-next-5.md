# one-1a-next-5

## 类型

工作

## 职责

- 数据修复批（生产前闸门，不挡 dev）：PR-1 审计分区（TB-07、TB-09、TB-05，ADR-0048，已可开）→ PR-3（TB-04、06、08）→ PR-6 审批 → PR-7 编号 → PR-8 workforce（不含 sync_*）；切法见交接报告第 3 节。
- 承接 one-1a-next-4 留下的小卡：DR-TB01-FU1（运维证明员工 ID 存在性校验）。

## 独占

- 仅独占当前已派工作卡列明的迁移文件、测试与 backend/architecture 登记条目；data-access-registry.yaml、data-classification.txt、unversioned-table-writes.txt 按卡逐项登记并串行整合，不独占整个目录；one-data 负责签发结论。

## 不碰

- 并入他卡的条目（拆分 a、DA-10、W2、日历版本化、DA-7、TA-01），以及 one-1a-par 的 F-1。
- 部署与 ECS 配置（one-ops）。

## 输入

- `phase1a/HANDOFF_one-1a-next-4.md`、数据修复卡与 one-data 签发记录、私有证据 `~/.modernize/psa/handoff/one-1a-next-4/`。

## 输出

- 每个 PR（附预期表结构 diff 与 one-data 签发行）→ one-coordinator。

## 触发与节奏

- 事件：收到已通过数据设计与 ADR 对齐检查的卡后开工；在制代码 PR 不超过一个，下一卡只准备不另开 PR，调度明确例外除外。旧修复批按 ADR-0054/0055 重判适用性，不凭旧队列继续。
- 定时：无。

## 自动化

- 钩子：压缩前快照；压缩或清空后注入本画像。
- 守护与脚本：`./mvnw -B verify`、`scripts/precheck.sh`、`build_all.py --check`、错题本回测。

## 上下文文档

- `docs/modernization/phase1a/HANDOFF_one-1a-next-4.md`（接手依据）
- `docs/modernization/phase1a/HANDOFF_one-1a-next-5.md`（本会话产出与后续）

## 交接条件

数据修复批完成后的批次边界，或达到 §2.1 第 1 条阈值时交接。

## 权限边界

- 须问用户（经待决定收件箱）：建账号、起非登记镜像的容器、对外调用、ADR 变更。
- 改断言或删测试须写理由并在 PR 单列；验收测试只由测试代理按 `Acceptance-Ruling` 改。
- 工作守则：卡与已采纳 ADR 字面不一致时，起草阶段交 one-architecture 判定；交付前用错题本回测。

## 当前状态

2026-10-22 22:50+02:00 上下文 30%，在批次边界交接，详见 `phase1a/HANDOFF_one-1a-next-5.md` 第 7 节。已合入：#507、#524、#528、#607（RULE-412）、#622（search_path，批入队时 CI 全绿为条件，以 gh 为准）。在途：#599（REG-3，等 one-data 签发与 M1 裁定）、#629（KERR-2，head 57cc0dab3，等 CI 绿后调度再批）、#634（NX5-08 迁入写入通道·编号部分，opus 首轮 H1 等已修、sonnet 复核中，本机 Docker 停了，本地 IT 未实测）。下一步：REG-4（IMP-1022-COO-01，ProcessKey 值对象）。开场在 worktree 根写 `.claude/session-name`（一行 one-1a-next-5，不入仓）。
