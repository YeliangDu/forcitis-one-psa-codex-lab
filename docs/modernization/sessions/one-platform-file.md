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

2026-10-23 接手会话交接：签章第一段 api 的 PR #587 已在合并队列。文件 6b-2（上传与状态端点）已提交 c2565de91，分支 claude/one-platform-file-6b2（基于 origin/main c40f38872），任务号 IMP-1021-COO-02；`scripts/precheck.sh --run-backend` 在后台跑，未见结果前未开 PR——接手先重跑 precheck，过了再推送、开 PR、报 one-coordinator 要 opus 首轮复审（代码 PR 须 opus 首轮）。

6b-2 要点（PR 说明须写明）：①上传是原始请求体不是 multipart（幂等过滤器读一次再交还，multipart 的分段读不到），文件名走 Content-Disposition filename*，类型取 Content-Type；②IdempotencyFilter 请求体超 64 KiB 落临时文件、边读边算哈希（口径不变），新增 forcitis.platform.web.idempotency.max-body-size=128MB，G46 不许文件模块读该键，故只有测试守默认值不小于 100MB，生产配置须运维保证文件上限小于它；③服务未开时上传答不可用、状态答不存在；④Permission platform.file:file:create/read 授 employee（改 identity 基线迁移）；⑤前端 api 生成器放行二进制上传的 Content-Disposition、http.ts 按 Blob 直发（前端公共代码，须点出）；⑥会话码表无文件会话码，任务号沿用 IMP-1021-COO-02。

下一步按 one-coordinator 顺序：6b-3 两个作业（未关联已过期转 EXPIRED_UNBOUND 并补 StoredFile 根类、正式区孤儿只读核对，@Scheduled 加 @SchedulerLock 放 file/infrastructure/scheduling，unbuilt_roots 到期 2026-11-15）→ ClamdScannerTest.aSilentClamdTimesOutAndIsRetriedWithinTheLimitThenRefused 偶发失败：断言前改带上限的确定性等待（CountDownLatch 或 awaitility），不隔离、不放宽 → 5a → 单元 7（并入 WITHDRAWN 状态与 version 列、状态转换约束逐对改写，表结构先请 one-data 签发）。另待：PI/SPI 改名与 has_macros 等 one-glossary gl-10 合入与 one-data 答复。

经验：本机有 Docker，可跑 ITs 与 OpenApiExportTest（verify 阶段会重生成 frontend/packages/api/openapi.json，再 `node frontend/scripts/generate-api.mjs`）；mvnw 输出很长，重定向到文件再 grep；后台任务 description 须 `<任务号> <名> (<执行者>)`；代码提交须带任务号或 PR 号。

无未完成的用户请求。
