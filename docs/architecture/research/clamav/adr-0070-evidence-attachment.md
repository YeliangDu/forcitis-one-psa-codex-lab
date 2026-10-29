# ADR-0070 证据附件

状态：历史记录｜2026-10-21｜负责：one-architecture

正式档篇幅所限，证据明细放此处；ADR 正文「证据」节只留索引。

- 用户：PR #494 评论 6311040189（8 条判据与两条提醒），intent `2026-10-21-100709`。
- ADR-0060 决定 1 第 3 条、决定 2、决定 3、决定 5 第 1、2、4 条、决定 7、迁移方案第 3、4 条；ADR-0062 决定 1、2；ADR-0036；ADR-0043；ADR-0067 决定 3 第 1 条。
- 运维实施卡 OPS-1021-OPS-02 与测量（@c5ac20d5）：`phase1a/infra/handoff/B-5-dba/evidence/att_size_result.md`（老系统附件 393,483 件，P99 97.1 MB、P99.9 465 MB、最大约 2.5 GB，> 100 MB 占 0.97%）。单次扫描 P95 耗时未测。
- ClamAV 参数与返回（`MaxFileSize`、`MaxScanSize`、`MaxRecursion`、`MaxFiles`、`MaxScanTime`、`AlertExceedsMax`、`Heuristics.*`、`FILDES`）实施时以 clamd.conf 与 clamd 文档核对。
- 复审：`architecture/research/R-ADR0070复审-2026-10-21.md`（M1–M12、S1–S9）与改后复核 `R2-ADR0070改后复核-2026-10-21.md`（N1–N10），处理见 `research/clamav/ADR-0070-复审回应.md`。
- 检索记录（`git grep -n -E '检测待处理|INSTREAM|预览范围|写入临时区|临时区并检测|已隔离|单文件|含宏|quarantine' origin/main -- docs/adr/`）：ADR-0060 本篇取代 4 项、其余相容；ADR-0062 本篇取代 2 项；ADR-0063 本篇取代 1 项；ADR-0035「隔离登记」为事件隔离，无关。
- 用户批准答复：`research/clamav/ADR-0070-用户批准答复.md`（批准页 7hVc6NZeBJ3Z4Ehp7BSACy，intent `2026-10-21-105115`）。
