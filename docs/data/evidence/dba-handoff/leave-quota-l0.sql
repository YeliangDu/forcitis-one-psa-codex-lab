-- 请假额度取证 L0（#1279）· 只查元数据：哪些表/视图/列与请假、年假、额度、余额、结转、加班、补休有关，以及各表的行数。
-- 不读任何一行业务数据：只查 INFORMATION_SCHEMA 与 sys 目录视图，输出只有 表名、列名、类型、行数；没有人员、工号、单据。
-- 用法：以取证只读账号（与 timesheet-filing-duty.sql 同一个）连 SQL Server，各在 a8_db 与 ruoyi-vue-pro 上执行一次（改下面 USE），
--       把两次的结果集（L0a 表与行数、L0b 列）各存成 CSV 发回：legacy-L0a-<库名>.csv、legacy-L0b-<库名>.csv。
-- 只含 SELECT。关键字不含中文以外的个人信息；若某库没有命中，结果为空，也请原样发回。
USE [a8_db];   -- 第二次改成 [ruoyi-vue-pro]
GO
-- L0a 命中的表/视图与行数（行数取目录视图的估计值，不扫表）
SELECT s.name AS schema_name, o.name AS object_name, o.type_desc,
       SUM(CASE WHEN p.index_id IN (0, 1) THEN p.row_count ELSE 0 END) AS row_count
  FROM sys.objects o
  JOIN sys.schemas s ON s.schema_id = o.schema_id
  LEFT JOIN sys.dm_db_partition_stats p ON p.object_id = o.object_id
 WHERE o.type IN ('U', 'V')
   AND (LOWER(o.name) LIKE '%leave%' OR LOWER(o.name) LIKE '%vacation%' OR LOWER(o.name) LIKE '%holiday%'
     OR LOWER(o.name) LIKE '%quota%' OR LOWER(o.name) LIKE '%balance%' OR LOWER(o.name) LIKE '%annual%'
     OR LOWER(o.name) LIKE '%carry%' OR LOWER(o.name) LIKE '%overtime%' OR LOWER(o.name) LIKE '%compens%'
     OR LOWER(o.name) LIKE '%attend%' OR LOWER(o.name) LIKE '%absence%' OR LOWER(o.name) LIKE '%timeoff%'
     OR o.name LIKE N'%假%' OR o.name LIKE N'%休%' OR o.name LIKE N'%额度%' OR o.name LIKE N'%加班%')
 GROUP BY s.name, o.name, o.type_desc
 ORDER BY s.name, o.name;
GO
-- L0b 命中表的列（只列元数据）；另含任何表中名字像请假/额度的列
SELECT c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE, c.IS_NULLABLE
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id = o.schema_id
                WHERE o.name = c.TABLE_NAME AND s.name = c.TABLE_SCHEMA AND o.type IN ('U', 'V')
                  AND (LOWER(o.name) LIKE '%leave%' OR LOWER(o.name) LIKE '%vacation%' OR LOWER(o.name) LIKE '%holiday%'
                    OR LOWER(o.name) LIKE '%quota%' OR LOWER(o.name) LIKE '%balance%' OR LOWER(o.name) LIKE '%annual%'
                    OR LOWER(o.name) LIKE '%carry%' OR LOWER(o.name) LIKE '%overtime%' OR LOWER(o.name) LIKE '%compens%'
                    OR LOWER(o.name) LIKE '%attend%' OR LOWER(o.name) LIKE '%absence%' OR LOWER(o.name) LIKE '%timeoff%'
                    OR o.name LIKE N'%假%' OR o.name LIKE N'%休%' OR o.name LIKE N'%额度%' OR o.name LIKE N'%加班%'))
    OR LOWER(c.COLUMN_NAME) LIKE '%leave%' OR LOWER(c.COLUMN_NAME) LIKE '%quota%' OR LOWER(c.COLUMN_NAME) LIKE '%carry%'
    OR c.COLUMN_NAME LIKE N'%年假%' OR c.COLUMN_NAME LIKE N'%额度%' OR c.COLUMN_NAME LIKE N'%结转%'
 ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.ORDINAL_POSITION;
GO
