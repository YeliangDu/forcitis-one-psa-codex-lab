# 触发器命名条款提议（交 NAMING_STANDARD §2.4）

> 冻结（2026-11-04，#1263）：提议稿，未写入规则；触发器命名以基线迁移与 `DdlNamingTest` 为准。

- 起草：one-data，2026-10-16；来源：PR-3 签发时的承诺（`reviews/DATA-REPAIR-timesheet-audit-PR3.md` 裁定 4：触发器统一命名转卡）。**提议稿，不是已采纳规则**：条款落进 NAMING_STANDARD、改全库的小卡由 one-coordinator 排，条款用语交 one-glossary 查重。
- 现状（`origin/main` 基线迁移 `CREATE TRIGGER` 共 27 个）：
  - `tg_<表名>_<用途>`：19 个（`tg_org_unit_versions_append_only`、`tg_..._append_only_truncate`、`tg_source_references_guard`、`tg_source_references_never_deleted` 等），占多数；
  - `<表名>_<动作>`（无 `tg_` 前缀）：8 个（`audit_entries_reject_update_delete`、`audit_entries_reject_truncate`、`approval_task_records_reject_*`、`legal_entity_attribution_corrections_reject_*`、`numbering_rule_versions_guard` 等）。
  - 两种风格并存；`DdlNamingTest` 不检查触发器名，`ddl-naming-baseline.txt` 无触发器行，NAMING_STANDARD 无触发器条款。
- 提议条款：
  1. 触发器名 `tg_<表名>_<用途>`；语句级 `BEFORE TRUNCATE` 的触发器名以 `_truncate` 结尾；
  2. 用途取小集合：`append_only`（拒 UPDATE、DELETE）、`never_deleted`（只拒 DELETE）、`guard`（状态流转或值校验）；新增用途须在条款里登记；
  3. 触发器函数名 `<schema>.<动词>_<对象>`（现有 `reject_start_round_change` 等）不在本条款内；
  4. 分区父表上的触发器随分区克隆，分区子表上手工建的语句级触发器同一前缀（`tg_<父表名>_<用途>_truncate`，加分区后缀不要求）。
- 守护（待 one-quality）：`DdlNamingTest` 增一项，触发器名须匹配 `^tg_<表名>_(append_only|never_deleted|guard)(_truncate)?$`；改名前不得出现新的不合规触发器（基线例外清单只减不增，ADR-0033 同口径）。
- 改动面（改全库的小卡，排在 R-2a 之后）：8 个不合规触发器改名；测试里按旧名禁用与启用触发器的写法同改（已知：`AuditArchiveJobIT.java:70,71,168,169`，`LegalEntityAttributionIntegrationTest` 的关触发器删更正，`VersionTables.truncate`）；基线开放期直接改基线，附预期表结构 diff；改名不改行为。
- 待决：用途集合是否足够（`guard` 含义偏宽）；是否要求触发器名带 `trg_` 而非 `tg_`（现状多数为 `tg_`，不改）。
