## 表结构 PR 复核：IMP-1017-PAR-02（#476，ADR-0034-IMPL PR-1）

- 复核：one-data，2026-10-17，PR 头 `d0af174f`（`claude/one-1a-par-adr0034-schema`），依据签发 `data/reviews/ADR-0034-IMPL.md`（f1-card@f46c9bf5 的第 7.1 节）。
- 方法：把 PR 对 `V20261014130000__platform_identity_baseline.sql` 的新增行与卡 7.1 的 DDL 逐行归一化比较（去空白）。结果：PR 新增 198 行，只有 2 行不在卡的 SQL 块里，就是卡里以注释形式写的两个基准列（`base_account_version_number bigint`、`base_role_version_ids uuid[] NOT NULL DEFAULT '{}'`），PR 把它们落进 `authorization_requests` 的 CREATE TABLE，位置与我的补签一致；卡里 5 行只在卡中的都是注释行。PR 对该文件没有删除行。`ck_authorization_requests_base` 与 `VOIDED` 不在 PR 中（符合签发范围）。两个基准列的注释存在。
- 门禁 §1.4 逐项：
  1. 实际 diff 等于预期 diff：**通过**（上面的逐行比较；`pg_dump --schema-only` 两库对比我没有做——没有可用的 PostgreSQL，且 PR 自带 `AuthorizationHistorySchemaIT`、`GrantHistoryAppendOnlyIT`、`MomentVersionTablesIT`、`PermissionCodeSetIT` 四个验收类在 CI 里对真实库验证表、约束、触发器与函数；以 CI 全绿为准）。
  2. 注册表与卡一致、无新增 `gaps`：**通过**。两条 `DIMENSION_VERSIONED` / `APPEND_ONLY` / `PERMANENT` + 注释，注释含四项（口径、清理落点、负责人 one-1a-par、期限首次生产部署后 30 个月内、按依赖顺序清理）；`writers: []`（我对 D-4 的答复）。
  3. 守护全绿、无新增例外：**待 CI**（`backend verify` 在跑）。例外清单只减不增：`ddl-naming-baseline.txt` 删一行 `column platform_numbering.numbering_rules.document_type`。**注意**：这一删与本卡的表无关，是 PR 同时改了 `DdlNamingTest`（认「具名表级枚举 CHECK」为列已被检查，并增 `_ids` 类别词）后，原先该列因只有具名 CHECK 而被记的基线例外「已修好」，不删会报「已修好却没删」。我认可这个守护改动方向（NAMING_STANDARD §2.4 本来就要求具名 `ck_<表>_<列>`，更严不更松，基线只减），但它属于「改守护断言」，**须在 PR 正文「改动的断言」一栏单列理由**（CLAUDE.md 第 5 条）；请核对 PR 正文已写。
  4. 逐表打分写入 `phase1a/DATA_SCORECARD.md`：**待合并后落**（`role_versions`、`account_role_grants` 两行）；格式是 15 列的逐表表，我在合并后补，不阻塞合并。
  5. 安全与结构 opus 复审：PR 正文已记（无 Blocker / High，M1 M2 已修）。
- 其他观察：PR 分支相对 main 落后较多（对 main 末端的整体 diff 显示大量删除，是 main 前进所致；对合并基点只有 PR 自己的改动）。合并前请 `sync_with_base_branch` 更新后再让 CI 跑一次，免得合并时与 main 上其他 `ddl-naming-baseline.txt`、注册表改动冲突。
- 结论：**数据项复核通过**（以 CI 全绿为前提，CI 绿是合并门槛，不是本复核的条件）。
