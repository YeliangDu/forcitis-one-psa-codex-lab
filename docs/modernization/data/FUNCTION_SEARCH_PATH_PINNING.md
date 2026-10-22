# 自定义函数固定 search_path：盘点、统一规则、追加迁移与守护设计

- 日期：2026-10-22；作者：one-data；起因：#597 opus 复审 M1（`organization_administration`、`organization_attendance`、`platform_file` 的 plpgsql 函数未固定 `search_path`，同名对象可顶替 `pg_trigger_depth()`、`transaction_timestamp()` 等）；范围：`backend/src/main/resources/db/migration` 下全部自定义函数。
- 状态：设计稿，**安全判定待 opus 复审**；复审通过后由 one-coordinator 派实现。

## 1. 盘点（对 origin/main 合并后 425df7627 之后的迁移文件逐个解析）

自定义函数共 **34** 个（无存储过程；`CREATE EXTENSION` 只有 `btree_gist`；视图只有 `platform_file.current_file_preservations`，已带 `security_invoker`）。

| schema | 函数数 | 已固定 search_path | 未固定 |
|---|---|---|---|
| organization_attendance | 5 | 5（#547 已做：`pg_catalog, pg_temp`） | 0 |
| platform_audit | 4 | 2（两个 `SECURITY DEFINER` 分区函数，`pg_catalog, platform_audit, pg_temp`） | 2 |
| platform_file | 11 | 0 | 11 |
| platform_numbering | 4 | 0 | 4 |
| platform_approval | 3 | 0 | 3 |
| platform_identity | 3 | 0 | 3 |
| organization_workforce | 2 | 0 | 2 |
| platform_integration | 2 | 0 | 2 |
| **合计** | **34** | **7** | **27** |

另有尚未合入的 #597（`organization_administration` 七个左右函数）同样未固定，与本设计同批处理（实现卡须覆盖，见第 4 节）。

函数性质：32 个是 `SECURITY INVOKER`（触发器函数 31 个、`platform_identity.is_normalized_code_set` 一个 `IMMUTABLE STRICT` 的 SQL 函数，用在 `role_versions` 的 CHECK 里）；`SECURITY DEFINER` 只有 `platform_audit` 的 2 个，已固定路径并 `REVOKE EXECUTE ... FROM PUBLIC`，只授给指定角色。

**函数体对 search_path 的依赖**（我逐个扫了函数体）：没有一个函数以**未限定名**引用本 schema 的表、函数或类型；未限定名只有内建函数与运算符（`lower`、`to_jsonb`、`coalesce`、`pg_has_role`、`pg_trigger_depth` 等）和一处目录表 `pg_class`（`platform_file.refuse_transition_rewrite`）、一处 `unnest`（`is_normalized_code_set`）。所以把路径固定为 `pg_catalog, pg_temp` **不会弄断任何现有函数**。

## 2. 风险评估（给 opus 复审的前提）

- 触发器函数默认 `SECURITY INVOKER`，随执行 DML 的账号运行，路径取该会话的 `search_path`。运行账号 `one_*_app` 的角色级路径是 `platform_approval, pg_catalog`（Flowable 要求），**`pg_catalog` 排在后面**，所以 `platform_approval` 里若有同名对象会先于内建被命中；运行账号对各平台 schema 已被 `REVOKE CREATE`，不能在其中建对象，因此当前不可直接利用。
- 临时 schema：未显式列入路径时，会话临时 schema 对**关系名与类型名**先于 `pg_catalog` 被搜索（对函数与运算符不会隐式搜索），`pg_class` 这类未限定关系名理论上可被临时表顶替；opus 在 #547 提出，one-1a-par 在 PG18 上未能复现（plpgsql 计划缓存），但没有理由依赖这点。运行账号目前持有数据库 `TEMP` 权限（默认来自 PUBLIC）。
- 所以结论：**纵深防御，不是已知可利用漏洞**；但把触发器放在"拒绝改写"的安全边界上，边界函数的行为不应取决于调用会话的可变状态。成本很低，应做。

## 3. 统一规则

1. **R-SP-1**：迁移里创建的每个函数（含 `CREATE OR REPLACE`）必须带 `SET search_path`，且值满足：**第一项 `pg_catalog`、最后一项 `pg_temp`**，中间只允许本函数所在 schema（且仅当函数体确需未限定引用本 schema 对象时，默认不需要）；不得含 `public`、`$user`、空值、`DEFAULT`、`FROM CURRENT`。默认值就写 `SET search_path = pg_catalog, pg_temp`。
2. **R-SP-2**：函数体里引用本 schema 或别的 schema 的对象一律**全限定**（`schema.name`）；只有 `pg_catalog` 里的内建可以未限定。`pg_temp` 列在最后，使临时 schema 最后才被搜索。
3. **R-SP-3**：`SECURITY DEFINER` 函数在 R-SP-1 之上还须 `REVOKE EXECUTE ... FROM PUBLIC`，再按角色 `GRANT`；现有两个已满足，路径写法含 `platform_audit` 的，视为满足（中间项为本 schema）。
4. **R-SP-4**：`IMMUTABLE`/`STABLE` 函数用在 CHECK 或索引表达式里时同样要固定（`is_normalized_code_set`）；固定 `search_path` 会使 SQL 函数不再内联，影响可忽略。
5. **R-SP-5（库与角色层，交 one-ops，不阻断本设计）**：①运行账号角色级路径改为 `pg_catalog, platform_approval`（pg_catalog 在前）；②`REVOKE TEMP ON DATABASE ... FROM PUBLIC`，运行账号确无需临时表再收回，c3/c9 校验脚本加 `has_database_privilege(应用账号, 'TEMP') = false`（#547 复核时已提）；③`public` schema 无 CREATE 给 PUBLIC（PG15+ 默认）。

## 4. 追加迁移设计

- **做法**：对已合入 main 的 27 个函数，每个 schema 一个**追加迁移**（放各自的迁移目录，命名按 NAMING_STANDARD：`V{yyyyMMddHHmmss}__{schema}_pin_function_search_path.sql`），只含 `ALTER FUNCTION <schema>.<name>(<参数类型>) SET search_path = pg_catalog, pg_temp;`，不重写函数体，不改行为；`ALTER FUNCTION ... SET` 需函数属主，Flyway 迁移账号即属主。八个 schema 里 `organization_attendance` 已满足，不用迁移；其余七个 schema 各一份，共 7 份、27 条语句（清单见附录）。
- **#597 与以后新基线**：基线尚未合入 main 的（`organization_administration`），直接在 `CREATE FUNCTION` 上加 `SET`，不另做追加迁移；#597 的实现卡须在合入前改（opus M1 已列）。
- **验证**：迁移后 `SELECT p.oid::regprocedure, p.proconfig FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = ANY(<本库自有 schema>)` 的每一行 `proconfig` 含 `search_path=pg_catalog, pg_temp`；各 schema 既有 IT 全绿（触发器行为不变）。
- **回滚**：`ALTER FUNCTION ... RESET search_path`；不涉及数据。
- **迁移不可变守护**：追加迁移是新文件，不触 `migrations_immutable`。

## 5. 守护设计（新函数未固定 search_path 即失败）

1. **主守护（行为型，随 `DataArchitectureTest`）**：在测试用的迁移后数据库里查 `pg_proc`（本库自有 schema 集合，排除扩展函数）：每个函数必须 `proconfig` 里有 `search_path=...` 且满足 R-SP-1（第一项 `pg_catalog`、最后 `pg_temp`、无 `public`/`$user`）；`SECURITY DEFINER` 函数另核 `PUBLIC` 无 `EXECUTE`（`has_function_privilege('public', oid, 'EXECUTE') = false`）。失败信息列出函数的全限定签名。这是行为判断，不依赖解析 SQL 文本，不会被 `$$` 引号、`CREATE OR REPLACE` 或 `ALTER FUNCTION` 的写法绕过。
2. **辅守护（快速反馈，随 `scripts/ci/baseline_ddl_scan.py` 或 `measurable_ddl_scan.py` 的新项 M7）**：静态扫迁移文件的 `CREATE FUNCTION` 头部有 `SET search_path`（只报告 + 基线只减不增，同 M1–M6），让不跑容器的 precheck 也能早发现。
3. **部署前检查**：c3/c9 `*_verify.sql` 加 V11：对已迁移的库跑同一条 `pg_proc` 查询，期望 0 行违规，ON_ERROR_STOP。
4. **不放宽已有断言**：不改任何现有测试断言；守护是新增。

## 6. 范围外与残余

- 事件触发器、行级安全、排序规则/`COLLATE "C"` 不涉及。
- Flowable/Spring 自带对象不是自定义函数，不动。
- 仍需运行账号最小权限（R-SP-5）才算纵深完整；本设计不改角色脚本，交 one-ops。

## 附录 A：27 条 ALTER（按 schema 分文件）

-- organization_workforce（2）
ALTER FUNCTION organization_workforce.reject_attribution_correction_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION organization_workforce.refuse_version_rewrite() SET search_path = pg_catalog, pg_temp;
-- platform_approval（3）
ALTER FUNCTION platform_approval.reject_start_round_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_approval.reject_task_record_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_approval.reject_process_archive_skip_change() SET search_path = pg_catalog, pg_temp;
-- platform_audit（2）
ALTER FUNCTION platform_audit.reject_audit_entry_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_audit.reject_archive_batch_change() SET search_path = pg_catalog, pg_temp;
-- platform_file（11）
ALTER FUNCTION platform_file.refuse_transition_rewrite() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_stored_file_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_stored_file_name_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.refuse_append_only_rewrite() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_preservation_category_version_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_preservation_category_version_insert() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_file_preservation_insert() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_false_positive_judgment_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_judgment_insert() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_quarantine_read_request_insert() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_file.guard_quarantine_read_request_change() SET search_path = pg_catalog, pg_temp;
-- platform_identity（3）
ALTER FUNCTION platform_identity.is_normalized_code_set(text[]) SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_identity.refuse_history_rewrite() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_identity.require_open_role_version() SET search_path = pg_catalog, pg_temp;
-- platform_integration（2）
ALTER FUNCTION platform_integration.guard_source_reference_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_integration.reject_source_reference_deletion() SET search_path = pg_catalog, pg_temp;
-- platform_numbering（4）
ALTER FUNCTION platform_numbering.guard_document_type_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_numbering.reject_legacy_number_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_numbering.guard_rule_version_change() SET search_path = pg_catalog, pg_temp;
ALTER FUNCTION platform_numbering.guard_rule_version_source() SET search_path = pg_catalog, pg_temp;