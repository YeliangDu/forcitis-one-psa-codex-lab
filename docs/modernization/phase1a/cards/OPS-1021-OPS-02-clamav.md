---
卡号: OPS-1021-OPS-02
状态: 待派
负责会话: one-ops
---
# 派工卡 OPS-1021-OPS-02：自建 ClamAV 恶意文件检测建成

- 任务名：[OPS·platform.file] 自建 ClamAV 恶意文件检测建成（W0 内，通用审批先行上线前）；主线 OPS；bc platform.file；种类 编码
- 前置：文件服务基础（ADR-0060）适配器端口落地；ADR-0060 补篇（取舍写入）

- 草稿：one-ops，2026-10-21。依据用户在 #494 的决定（评论 6311040189，intent `9a5136bf`）：选 A 自建 ClamAV；「W0 内建成并验收，不询价、不建账号、不调用任何云服务」；实施卡按 8 条建成判据逐条对应验收。
- 候选与对比：`phase1a/infra/handoff/MALWARE_SCAN_CANDIDATES.md`；ADR-0060 第 2 条（检测不可用不放行并告警）、第 1 条第 3 款（适配器在 `platform.integration`）。

## 1. 卡号与类别
- 卡号：OPS-1021-OPS-02；类别：功能补做（应交付未做：ADR-0060 入口要求的检测）。
- 分工：**运维侧（one-ops）**——clamd 与 freshclam 的安装配置、安全组出向单、健康检查指标与告警、数值测量、参数落定；**应用侧（文件服务负责会话，由调度派）**——`platform.integration` 的检测适配器与端口、三态判定与审计、行为守护测试。两侧按下表逐条对应，缺一条不算建成。

## 2. 八条判据与验收（缺一条就不算建成）

| # | 判据（用户原文要点） | 运维侧动作 | 应用侧动作 | 验收证据 |
|---|---|---|---|---|
| 1 | 部署形状：clamd 与后端同机（或同 VPC 小机）；适配器用 `SCAN` 扫本地检测暂存目录路径，**不用 `INSTREAM`**；若必须远程 TCP，超上限＝拒绝并告警，不许跳过大文件 | 同机部署 clamd（unix socket，权限限后端运行用户）；若同机放不下，另起同 VPC 小机并共享临时目录，仍用 `SCAN`；评估内存与 OSS 上传 IO 竞争（见 §5） | 适配器只发 `SCAN <临时区路径>`，路径来自本模块「写入内容」的本地检测暂存目录（上传先落此处，检测通过才进 OSS 临时区，未通过进隔离前缀；ADR-0070） | 配置文件（`clamd.conf`）与适配器测试；负向：把适配器改成 INSTREAM 被守护测试拦下 |
| 2 | 出向只加一条：`freshclam` 固定域名（或内网镜像），写进安全组变更单，其余保持 Drop | 出向变更单写一条（目的域名或镜像地址、TCP 443）；上线前我先把方案报调度、经用户批准再改安全组 | 无 | 安全组出向规则回读，仅比现状多一条 |
| 3 | 参数按业务上限调：`MaxFileSize`/`MaxScanSize` ≥ 业务单文件上限；`MaxRecursion`/`MaxFiles`/`MaxScanTime` 防压缩炸弹；`AlertExceedsMax yes` | 按 §4 两个数落定参数；`AlertExceedsMax yes` | 超限（返回超限告警）按「不可用/未通过」处理，不放行 | `clamd.conf` 参数表附测量依据；用超大文件与嵌套压缩包的测试证明被拒并告警 |
| 4 | 三态判定写审计：通过、未通过（隔离、不可关联、留 SHA-256）、不可用（拒绝＋告警）；失败即拒，不许重试到超时再放行 | 提供「不可用」信号（clamd 不可达、库过期、超时）与告警规则 | 三态入 `platform.audit`；失败即拒，重试次数与总时限为配置项且上限内仍失败则拒 | 审计条目样例；三态的集成测试 |
| 5 | 行为守护测试：未扫过或未通过的文件不能出现在「关联」「下载」两条路径上 | 无 | 机器版验收：构造「未扫」「未通过」文件走关联与下载均失败 | 守护测试（ArchUnit 或行为测试）进 CI |
| 6 | 病毒库过期也算故障：库龄进健康检查，更新失败要告警 | 健康检查读库版本日期，阈值（暂定 48 小时，测量后可调）超限即失败；`freshclam` 失败写 `ALERT` 日志，接 C-7 告警 | 健康检查端点把「库过期」当「不可用」 | 健康检查返回；人为停更新后告警触发的演练记录 |
| 7 | 已知取舍写进 ADR-0060 补篇：对宏文档另加三条——不自动执行、不内联预览、下载时提示来源 | 无 | ADR-0060 补篇由 one-architecture 起草；文件服务按三条实现（预览服务与下载响应头） | 补篇已采纳；宏类文档的下载提示与无内联预览的测试 |
| 8 | 先测两个数再定参数：业务单文件 P99 大小、单次扫描 P95 耗时 | **我来测**（见 §4）；这两个数出来之前不定 `MaxFileSize` | 无 | 测量记录（数值、样本、方法）；参数表引用它 |

## 3. 两条提醒（用户原文）

- **本地检测暂存目录不能有任何预签名 URL 或外部可读路径**：扫描发生在关联之前，暂存目录可读等于「未检先可用」。目录权限只给后端运行用户；不放进 OSS 桶或静态目录；适配器不生成它的链接。**OSS 临时区只放已通过检测的文件**，未通过的进隔离前缀（不可关联、不可下载）。（ADR-0070 把检测前移，one-coordinator 2026-10-21 转 one-architecture 的口径）
- 检测器与被检文件同机时注意内存与 OSS 上传的 IO 竞争；同机放不下，宁可单独小机＋内网 `SCAN`（共享本地检测暂存目录），也别退回 `INSTREAM`。

## 4. 先测两个数（运维侧，第 8 条）

1. **业务单文件 P99 大小**：来源 A8 附件元数据——用取证账号只取附件大小的**聚合**（总数、P50/P90/P99/最大、按扩展名的分布，不取文件名与内容；登记 ADR-0037 一行）；NAS 合同大文件另计（不在库内，NAS 总量由行政/IT 给，见 #494 评论 6311104971 第 4 条）。
2. **单次扫描 P95 耗时**：在 dev 机上装好 clamd，用公开的、非敏感的样本（按得到的大小分布合成的文档、PDF、含压缩包的样本；加 EICAR 测试串验证检出）测 `SCAN` 耗时分布；同机并发上传时的耗时也测一轮。
3. 结果写进本卡「测量记录」，再据此落 `MaxFileSize`、`MaxScanSize`、`MaxScanTime`、检测总超时。

### 测量记录（one-ops 填）

- **业务单文件大小（第 8 条前半，2026-10-21）**：A8 附件登记值，P50 0.15 MB、P90 7.4 MB、P95 22 MB、**P99 97 MB**、P99.9 465 MB、最大约 2.5 GB；> 25 MB 占 4.7%、> 100 MB 占 0.97%；明细见 `phase1a/infra/handoff/B-5-dba/evidence/att_size_result.md`。含义：`MaxFileSize`/`MaxScanSize` 要远高于 clamd 默认；ClamAV 单文件 2 GB 以上不能扫，文件服务大小上限须定在 2 GB 以内（业务决定，待用户）；压缩包与 octet-stream 是大文件主体。
- **dev 安装（2026-10-21，用户选方案 A）**：官方 RPM clamav 1.4.6（LTS，GitHub 发布页；sha256 746feb3d…4bdd4ae 与发布页一致，签名经 Talos 公钥 5BAD CA26 65EF 59DC F8A2 3D8B 707F 0DB4 8083 6771 验证为好，公钥与官方文档仓库 cisco-talos.gpg 一致）；装在 /usr/local，单元 forcitis-one-clamd、forcitis-one-freshclam（开机自启），配置 /etc/forcitis-one/clamd.conf、freshclam.conf（root:root 644）；clamd 仅 unix socket /run/clamav/clamd.sock（clamav:forcitis 660，无 TCP/UDP 监听）；病毒库 /var/lib/clamav；出向沿用现有 443 规则，无安全组改动；程序升级需手动换 RPM，freshclam 只更库。暂定参数：MaxFileSize 100M、MaxScanSize 400M、MaxRecursion 10、MaxFiles 10000、MaxScanTime 120000、AlertExceedsMax/AlertEncryptedArchive/AlertEncryptedDoc yes。
- **部署配置核对（2026-10-21，应用侧要求）**：clamd 进程带附加组 forcitis（回读 /proc 组 992 993；单元 SupplementaryGroups=forcitis 且 clamav 用户在 forcitis 组）；/data/forcitis-one 为 root:forcitis 750、/data/forcitis-one/tmp 为 forcitis:forcitis 750，clamav 可进入；暂存子目录 750、文件 640、属组 forcitis 由应用建，clamd 经组读取。clamd 侧 MaxScanTime 120 s ≥ 检测总超时 60 s。注意：后端服务单元 ReadWritePaths 仅 logs 与 tmp，staging-root 须在 /data/forcitis-one/tmp 之内，否则应用单元要加路径。
- **健康检查与告警（第 6 条运维侧，2026-10-21 已装 dev）**：`one-clamav-check.timer` 每 5 分钟，检查 clamd 应答、库龄（> 48 h）、freshclam 运行；P1=clamd 不应答，P2=库过期或 freshclam 未运行；经 `one_alert_send` 推群并去重；演练（停 clamd、阈值压 0、停 freshclam）三类告警均触发与恢复，详见 runbooks/C-7-alerts.md「ClamAV 健康检查」。
- **加密项必验（one-architecture 裁定，样本 claude/one-platform-file-u5）**：content-compressed.7z、content.7z、header.7z 均报 Heuristics.Encrypted.7Zip（plain.7z OK）；open-*.pdf 五个均报 Heuristics.Encrypted.PDF，perm-*.pdf 五个均扫得进（OK），plain.pdf OK。结论：clamd 路线成立，无须改走 commons-compress。EICAR 报 Eicar-Test-Signature；101 MB 文件报 Heuristics.Limits.Exceeded.MaxFileSize（AlertExceedsMax 生效）。
- **单次扫描耗时（第 8 条后半，dev 4C16G，clamdscan SCAN 本地路径，随机内容、每次换文件避开缓存）**：1 MB p50 0.07 s / p95 0.08 s；20 MB p50 1.17 s / p95 1.45 s（n=20）；97 MB（≈P99）p50 3.39 s / p95 7.16 s（n=12）；97 MB 四并发 p50 3.94 s / p95 7.64 s（n=6）。样本为随机字节，非真实文档与压缩包混合，嵌套压缩炸弹未测；总超时建议 ≥ 60 s，待应用侧联调与生产规格再复核 MaxScanTime。

## 5. 实施步骤（运维侧）

1. dev 机装 clamd 与 freshclam（发行版包），unix socket；不开 TCP 监听。
2. 安全组出向单（freshclam 域名或镜像，TCP 443）→ 报调度 → 用户批准 → 改。当前 ECS 出向仅放行 5432/1433（内网）、80、443 与元数据，其余 Drop；若 443 已放行全网，仍写明目的并在变更单里记录，不依赖全网放行。
3. 测量（§4），落参数；写 `clamd.conf`、`freshclam.conf`（`/etc/forcitis-one/` 下，root 管理，纳入发布包的「服务器文件一致」检查）。
4. 健康检查与告警：库龄、clamd 可用性、`freshclam` 失败；接 C-7。
5. 与应用侧联调：EICAR 样本走「写入→检测→隔离」；停 clamd 走「不可用→拒绝＋告警」；过期库走「故障」。
6. 逐条对 §2 八条出验收记录；全部过才报「建成」。
7. 生产（P2 独立实例的生产 ECS）上线时同步复制配置；不另起云服务。

## 6. 禁止事项
- 不用 `INSTREAM`；不跳过超限文件；不在重试耗尽后放行；不让本地检测暂存目录外部可读，不把未检文件放进 OSS 临时区。
- 不询价、不建云账号、不调用任何云检测服务（用户决定）；商业引擎（D）仅在「安全评审明确要求更高检出率」或「出现首例漏检/绕过」时由 one-ops 带询价再报，询价项：国内许可方式、是否支持离线/内网更新、是否提供 socket 或 HTTP 接口、单节点吞吐与并发上限。
- 不打印、不写入密钥；不改卡外文件；不放宽任何断言。
- 改安全组、改生产配置先报调度、经用户批准（CLAUDE.md 规则 9）。

## 7. 报告去向
- 完成、阻塞或需要用户决定，向 one-coordinator；附 §2 八条验收记录、测量记录、返工轮次。
