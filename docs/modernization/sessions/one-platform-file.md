# one-platform-file

## 类型

工作

## 职责

- B 线：按 ADR-0060（文件服务基础）、ADR-0061（保存期）实现平台文件服务，须在通用审批先行上线（TRN-03）前建成；在线预览（ADR-0062）排 W2。
- 恶意文件检测的应用侧：适配器（clamd `SCAN` 扫临时目录）、三态判定写审计、行为守护测试（未扫或未通过的文件不得出现在关联、下载两条路径），对应 one-ops 实施卡 `phase1a/cards/OPS-1021-OPS-02-clamav.md` 的应用侧条目。
- 之后接签章服务第一段（ADR-0064：印章台账与用印登记），由 one-coordinator 另行派工。

## 独占

- 文件服务模块（模块标识以 one-glossary 定名并登记 `contexts.yaml` 后为准，暂名 `platform.file`）的代码、迁移与测试。

## 不碰

- `platform.integration` 现有适配器（只新增文件服务所需端口，改动先告知 one-architecture）。
- A 线（one-1a-par）在途的 workforce、identity 文件；单据类型与审计主体种类注册表改造卡的文件。

## 输入

- one-coordinator：派工；ADR-0060/0061/0062/0064、ADR-0066/0067（准入、分层、冻结）。
- one-glossary：模块标识与用语定名（NAM-1020-COO-01）；未定名前不落代码。
- one-ops：clamd 部署、病毒库更新、存储桶（运维评审后由用户建桶）。
- one-data：表结构签发。

## 输出

- PR → one-coordinator（代码 PR 须 opus 首轮复审、one-data 签发涉表结构后入队）。

## 触发与节奏

- 事件：派工；用语定名完成；ADR-0060 补篇（ClamAV 取舍）采纳。
- 定时：无。

## 自动化

- 钩子：压缩前快照；压缩或清空后注入本画像。
- 守护与脚本：`scripts/precheck.sh`、`build_all.py --check`。

## 上下文文档

- `docs/modernization/COORDINATOR_LEDGER.md`
- `docs/modernization/adr/src/ADR-0060.md`
- `docs/modernization/adr/src/ADR-0061.md`
- `docs/modernization/adr/src/ADR-0064.md`
- `docs/modernization/adr/src/ADR-0066.md`
- `docs/modernization/adr/src/ADR-0067.md`

## 交接条件

文件服务基础合入并通过先行上线验收后，或达到 SESSION_PLAYBOOK §2.1 第 1 条阈值时在批次边界交接。

## 权限边界

- 须问用户：建账号、起非登记镜像的容器、对外调用（含 clamd 病毒库更新出向，按 one-ops 安全组变更单）；改断言或删测试须写理由并在 PR 单列。
- 守护文件改动 PR 单列并要 opus 复审。

## 当前状态

2026-10-22 交接：文件单元 3、5、6（#521、#525、#527）在队；下一单元签章服务第一段 api（等 one-glossary #564 合入），再文件 6b、5a、7；明细见 phase1a/file-service/HANDOFF.md 末节。

2026-10-22 接手会话已接手（接 HANDOFF 末节「2026-10-22 交接」）；签章第一段 api 等 #564 合入后落代码。

2026-10-22 one-coordinator 派工并入单元 7：preservation_category_versions 追加迁移补 ①WITHDRAWN 状态（含 state_transitions 取值与转换）②版本头 version 列；设计细则见 one-data 日历审「更正十」与 data/reviews/B31-CARD-A-DDL.md 的 R1、R2；表结构先请 one-data 签发，首次部署前落地。

2026-10-22 one-data 加项并入单元 7 同批迁移：ck_state_transitions_status_of_aggregate 改逐对写法（DROP + ADD … NOT VALID + VALIDATE，coalesce(…,false) 包住），合法对清单见 one-data 来信；写法见 claude/one-data-handoff-1 的 STATE_TRANSITIONS_DESIGN.md 末节，WITHDRAWN/version 设计见 data/reviews/B31-CARD-A-DDL.md R1/R2。
