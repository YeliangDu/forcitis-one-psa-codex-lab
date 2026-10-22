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

2026-10-22 接手会话：签章第一段 api 的 PR #587 已批、反复与 main 的 openapi 生成物冲突，最新 head de4557aa9 等 CI 绿后由 one-merge 入队（本机无 Docker，openapi 快照为手工补入六个签章错误码，以 CI 导出为准）；文件 6b-1 的 PR #603 已合；6b 余下两块未开工：上传与状态端点（POST /api/v1/platform/files 带幂等键、GET 状态；需 Permission 码、identity 迁移、idempotent-endpoints.txt 一行、openapi 先行；无 multipart 先例，幂等过滤器会把整个请求体读入内存须先定）与两个作业（未关联已过期转 EXPIRED_UNBOUND 并补 StoredFile 根类、正式区孤儿只读核对，@Scheduled 加 @SchedulerLock 放 file/infrastructure/scheduling，unbuilt_roots 到期 2026-11-15）；其后 5a、单元 7（并入 WITHDRAWN 状态与 version 列、状态转换约束逐对改写，表结构先请 one-data 签发）；PI/SPI 改名与 has_macros 等 one-glossary gl-10 合入与 one-data 答复；经验：main 常动 openapi，合 main 取其 openapi 与生成物再重生成，无 Docker 时手补枚举；代码提交须带任务号或 PR 号。
