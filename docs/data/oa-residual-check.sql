-- 权责事实无遗留检查（ADR-0021 第 4 条）—— 只读查询模板，SQL Server 2016，在生产 RDS 上以只读连接执行
-- 用法：每个阶段出口前，按 OA_PROCESS_INVENTORY.md §6.3 填入本阶段的 A8 模板 ID、表单 ID 与切换日，逐段执行；
--      每段期望结果写在注释里，任何一段不满足即出口条件不满足。只取计数，不读单据内容。
-- 参数（示例为 6a；模板 ID、表单 ID 见 runtime/a8_templates.tsv 第 1 列与表单定义）：
--   @cutover      切换日（会计期间首日）
--   @templates    本阶段 A8 模板 ID（CTP_TEMPLATE.ID）
--   @forms        本阶段 A8 表单 ID（CAP_FORM_DEFINITION.ID，即 LEXMISCIF_VOUCHER.V_FORMID）

DECLARE @cutover date = '2027-01-01';
DECLARE @templates TABLE (id bigint PRIMARY KEY);
DECLARE @forms TABLE (id bigint PRIMARY KEY);
-- INSERT INTO @templates VALUES (...), (...);
-- INSERT INTO @forms VALUES (...), (...);

-- 1) 源头关停：本阶段模板已停用（期望：0 行）
SELECT t.ID, t.SUBJECT, t.STATE
FROM a8_db.dbo.CTP_TEMPLATE t WITH (NOLOCK)
WHERE t.ID IN (SELECT id FROM @templates) AND t.STATE = 0 AND ISNULL(t.IS_DELETE, 0) = 0;

-- 2) 源头关停：切换日后无新实例（期望：0）；在途实例为 0（期望：0，或逐单有关闭结论）
SELECT SUM(CASE WHEN s.CREATE_DATE >= @cutover THEN 1 ELSE 0 END) AS new_after_cutover,
       SUM(CASE WHEN s.STATE = 0 THEN 1 ELSE 0 END)               AS still_running
FROM a8_db.dbo.COL_SUMMARY s WITH (NOLOCK)
WHERE s.TEMPLETE_ID IN (SELECT id FROM @templates);

-- 3) 旁路关停：A8 凭证插件对本阶段表单在切换日后无新凭证（期望：0）
SELECT v.V_FORMID, COUNT(*) AS vouchers_after_cutover
FROM a8_db.dbo.LEXMISCIF_VOUCHER v WITH (NOLOCK)
WHERE v.V_FORMID IN (SELECT id FROM @forms) AND TRY_CONVERT(date, v.V_MAKEDATE) >= @cutover
GROUP BY v.V_FORMID;

-- 4) 旁路关停（仅 6b）：A8 销项开票插件在切换日后无新发票（期望：0）
SELECT COUNT(*) AS invoices_after_cutover
FROM a8_db.dbo.LEXMISCIVOUT_OPENMAIN o WITH (NOLOCK)
WHERE TRY_CONVERT(date, o.OM_CREATEDATE) >= @cutover;

-- 5) U8C 来源分布：切换日后 XX（外部接口）凭证总数，须等于新系统过账推送数 + 仍未迁入表单的 A8 凭证数；
--    GL（手工）与 TR（出纳）按财务判定的类别逐类复核（期望：业务类权责事实为 0）
SELECT 'u8cloud' AS book, pk_system, COUNT(*) AS vouchers
FROM u8cloud.dbo.gl_voucher WITH (NOLOCK)
WHERE ISNULL(dr, 0) = 0 AND ISNULL(discardflag, 'N') <> 'Y' AND TRY_CONVERT(date, LEFT(prepareddate, 10)) >= @cutover
GROUP BY pk_system
UNION ALL
SELECT 'u8cloud_QZ', pk_system, COUNT(*)
FROM u8cloud_QZ.dbo.gl_voucher WITH (NOLOCK)
WHERE ISNULL(dr, 0) = 0 AND ISNULL(discardflag, 'N') <> 'Y' AND TRY_CONVERT(date, LEFT(prepareddate, 10)) >= @cutover
GROUP BY pk_system;

-- 6) 跨库读取清零：PSA 库内仍引用 a8_db 的视图（期望：只剩组织主数据类，见 OA_PROCESS_INVENTORY §5.3「权责 = 否」）
SELECT o.name AS view_name
FROM [ruoyi-vue-pro].sys.views o
JOIN [ruoyi-vue-pro].sys.sql_modules m ON m.object_id = o.object_id
WHERE m.definition LIKE '%a8[_]db%';

-- 7) 存量迁入对账在新系统（PostgreSQL）侧执行：按 (期间, 法人, 来源单号) 比对 A8 档案与 operations.facts 的
--    行数与金额，迁入行带来源标记（source_system = 'A8'、原单号）；差异为 0 或经财务签字（见 ADR-0021 第 4 条）。
--    新系统代码侧守护：除 platform.integration 外，任何模块不得持有 a8_db 连接或引用 A8 表名（ArchUnit / 词表禁用写法）。
