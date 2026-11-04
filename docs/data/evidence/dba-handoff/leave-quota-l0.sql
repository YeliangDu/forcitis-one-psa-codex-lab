-- 请假额度取证 L0（#1279）· 只查配置与元数据，不读任何业务行：A8 里请假相关表单的定义、枚举项、定时任务；ruoyi-vue-pro 的请假相关对象名与列。
-- 依据（仓库证据，已核对）：docs/legacy/runtime-usage/a8-forms.tsv（form_id 与 formmain_NNNN 的对应）；a8_db.dbo.CAP_FORM_DEFINITION.FIELD_INFO 是 JSON，
--   字段在 $.front_formmain.fieldInfo[]，键 display（字段显示名）、name（fieldNNNN）、fieldType、type、fieldLength、isNull、enumInfo（docs/data/evidence/b31p-r4-queries.sql Q5-A；r3 结果 b31p-r3-result.md §⑤）；
--   枚举项 a8_db.dbo.CTP_ENUM / CTP_ENUM_ITEM（REF_ENUMID、SHOWVALUE、ENUMVALUE，同上 Q5-B，对应关系在该文件里标为假设，本文件 A4 顺带验证）；定时任务 a8_db 的 JK_* 表（b31p-r2-result.md §②）。
-- 另含薪酬接缝所需的 7 张表单（薪资计算 0800、薪资计算档案 0858、工资条 0864、工资补录 1318、停薪留职申请单 0863、调薪申请单 0834、调薪档案表 0841；form_id 同 a8-forms.tsv）：只读定义与物理列，绝不读任何工资数值。
-- 不输出人员、工号、单据；A1–A5 读的是表单定义而不是表单数据。用只读取证账号执行（与 timesheet-filing-duty.sql 同一个）。只含 SELECT。
-- 未核实：JK_* 的列名按 Quartz 标准写（仓库证据只有表名）；若 B1 报列名不存在，只回报错信息，我改后重发。
-- 用法：A 段在 a8_db 上执行一次；C 段在 a8_db 与 ruoyi-vue-pro 各执行一次（改 USE）；每个结果集存成 CSV：legacy-L0-<段号>.csv 发回。
SET LOCK_TIMEOUT 1000;
USE [a8_db];
GO
-- A1 目标表单的定义是否是 JSON、各有多少字段（先确认读得到）
SELECT f.ID AS form_id, f.NAME AS form_name, ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) AS is_json,
       (SELECT COUNT_BIG(*) FROM OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo')) AS field_count
  FROM a8_db.dbo.CAP_FORM_DEFINITION AS f
 WHERE DB_NAME() = N'a8_db' AND f.ID IN (796171863600219842, 5859649593037638153, 9052538345220490521, -7749553928318729525, -7022180195473537202, 9063501945476501361, 6280910657268830205, 1302286494557294279, 802812893430891959, 4653374441028142836, 7535998766052625231, 2928784593663115810, -1386513811900251097, -2826989769200875060, -364855800485097884, 8372459854493797226, 8604312368632513736, 4066963956786548045, -3286220380529473854, 724697480203878861);
GO
-- A2 字段表：显示名 → fieldNNNN、类型、枚举引用（定义，不是数据）
SELECT f.ID AS form_id, f.NAME AS form_name, j.[display] AS field_label, j.[name] AS field_key, j.fieldType AS field_type,
       j.[type] AS field_kind, j.fieldLength AS field_length, j.enumInfo AS enum_ref
  FROM a8_db.dbo.CAP_FORM_DEFINITION AS f
 CROSS APPLY OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo')
       WITH ([display] nvarchar(255) '$.display', [name] nvarchar(255) '$.name', fieldType nvarchar(100) '$.fieldType',
             [type] nvarchar(100) '$.type', fieldLength nvarchar(50) '$.fieldLength', enumInfo nvarchar(100) '$.enumInfo') AS j
 WHERE DB_NAME() = N'a8_db' AND f.ID IN (796171863600219842, 5859649593037638153, 9052538345220490521, -7749553928318729525, -7022180195473537202, 9063501945476501361, 6280910657268830205, 1302286494557294279, 802812893430891959, 4653374441028142836, 7535998766052625231, 2928784593663115810, -1386513811900251097, -2826989769200875060, -364855800485097884, 8372459854493797226, 8604312368632513736, 4066963956786548045, -3286220380529473854, 724697480203878861) AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1
 ORDER BY f.ID, j.[name];
GO
-- A3 字段定义里出现过哪些键（只出键名、值类型、出现次数；用来找公式、默认值、数据关联放在哪个键里）
SELECT f.ID AS form_id, k.[key] AS definition_key, k.[type] AS json_type_code, COUNT_BIG(*) AS occurrences
  FROM a8_db.dbo.CAP_FORM_DEFINITION AS f
 CROSS APPLY OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo') AS fi
 CROSS APPLY OPENJSON(fi.[value]) AS k
 WHERE DB_NAME() = N'a8_db' AND f.ID IN (796171863600219842, 5859649593037638153, 9052538345220490521, -7749553928318729525, -7022180195473537202, 9063501945476501361, 6280910657268830205, 1302286494557294279, 802812893430891959, 4653374441028142836, 7535998766052625231, 2928784593663115810, -1386513811900251097, -2826989769200875060, -364855800485097884, 8372459854493797226, 8604312368632513736, 4066963956786548045, -3286220380529473854, 724697480203878861) AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1 AND fi.[type] = 5
 GROUP BY f.ID, k.[key], k.[type]
 ORDER BY f.ID, k.[key];
GO
-- A3b 表单定义顶层键（公式、触发、计算可能挂在表单级而不是字段级）
SELECT f.ID AS form_id, k.[key] AS top_key, k.[type] AS json_type_code, LEN(CAST(k.[value] AS nvarchar(max))) AS value_length
  FROM a8_db.dbo.CAP_FORM_DEFINITION AS f
 CROSS APPLY OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain') AS k
 WHERE DB_NAME() = N'a8_db' AND f.ID IN (796171863600219842, 5859649593037638153, 9052538345220490521, -7749553928318729525, -7022180195473537202, 9063501945476501361, 6280910657268830205, 1302286494557294279, 802812893430891959, 4653374441028142836, 7535998766052625231, 2928784593663115810, -1386513811900251097, -2826989769200875060, -364855800485097884, 8372459854493797226, 8604312368632513736, 4066963956786548045, -3286220380529473854, 724697480203878861) AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1 AND k.[key] <> N'fieldInfo'
 ORDER BY f.ID, k.[key];
GO
-- A3c 字段级定义中名字像公式、计算、默认值、关联、触发的键的内容（定义文本，不是数据）
SELECT f.ID AS form_id, JSON_VALUE(fi.[value], '$.name') AS field_key, JSON_VALUE(fi.[value], '$.display') AS field_label,
       k.[key] AS definition_key, CAST(k.[value] AS nvarchar(2000)) AS definition_text
  FROM a8_db.dbo.CAP_FORM_DEFINITION AS f
 CROSS APPLY OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo') AS fi
 CROSS APPLY OPENJSON(fi.[value]) AS k
 WHERE DB_NAME() = N'a8_db' AND f.ID IN (796171863600219842, 5859649593037638153, 9052538345220490521, -7749553928318729525, -7022180195473537202, 9063501945476501361, 6280910657268830205, 1302286494557294279, 802812893430891959, 4653374441028142836, 7535998766052625231, 2928784593663115810, -1386513811900251097, -2826989769200875060, -364855800485097884, 8372459854493797226, 8604312368632513736, 4066963956786548045, -3286220380529473854, 724697480203878861) AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1 AND fi.[type] = 5
   AND (LOWER(k.[key]) LIKE N'%formula%' OR LOWER(k.[key]) LIKE N'%calc%' OR LOWER(k.[key]) LIKE N'%default%'
     OR LOWER(k.[key]) LIKE N'%relation%' OR LOWER(k.[key]) LIKE N'%trigger%' OR LOWER(k.[key]) LIKE N'%expression%')
   AND LEN(CAST(k.[value] AS nvarchar(max))) > 0 AND CAST(k.[value] AS nvarchar(max)) NOT IN (N'', N'null', N'[]', N'{}')
 ORDER BY f.ID, field_key;
GO
-- A4 枚举项（假别、状态等标签；配置，不是数据）：目标表单字段引用的枚举；同时验证 enumInfo 与 CTP_ENUM.ID 的对应
SELECT DISTINCT e.ID AS enum_id, e.DESCRIPTION AS enum_description, i.SORTNUMBER AS item_order, i.SHOWVALUE AS item_label,
       i.ENUMVALUE AS item_value, i.STATE AS item_state
  FROM a8_db.dbo.CAP_FORM_DEFINITION AS f
 CROSS APPLY OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo') WITH (enumInfo nvarchar(100) '$.enumInfo') AS j
  JOIN a8_db.dbo.CTP_ENUM AS e ON CAST(e.ID AS nvarchar(100)) = j.enumInfo
  JOIN a8_db.dbo.CTP_ENUM_ITEM AS i ON i.REF_ENUMID = e.ID
 WHERE DB_NAME() = N'a8_db' AND f.ID IN (796171863600219842, 5859649593037638153, 9052538345220490521, -7749553928318729525, -7022180195473537202, 9063501945476501361, 6280910657268830205, 1302286494557294279, 802812893430891959, 4653374441028142836, 7535998766052625231, 2928784593663115810, -1386513811900251097, -2826989769200875060, -364855800485097884, 8372459854493797226, 8604312368632513736, 4066963956786548045, -3286220380529473854, 724697480203878861) AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1 AND ISNUMERIC(j.enumInfo) = 1
 ORDER BY e.ID, i.SORTNUMBER;
GO
-- A5 目标表的物理列（formmain_NNNN 与 fieldNNNN 的对应校验）和估计行数；不读行
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE, c.IS_NULLABLE,
       (SELECT SUM(p.row_count) FROM sys.dm_db_partition_stats p WHERE p.object_id = OBJECT_ID(N'a8_db.dbo.' + c.TABLE_NAME) AND p.index_id IN (0, 1)) AS est_rows
  FROM INFORMATION_SCHEMA.COLUMNS AS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME IN (N'formmain_0787', N'formmain_0788', N'formmain_0789', N'formmain_0793', N'formmain_0820', N'formmain_1227', N'formmain_0777', N'formmain_0781', N'formmain_0798', N'formmain_0786', N'formmain_1114', N'formmain_1228', N'formmain_1113', N'formmain_0800', N'formmain_0858', N'formmain_0864', N'formmain_1318', N'formmain_0863', N'formmain_0834', N'formmain_0841')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
-- B1 定时任务（A8 的 JK_* = Quartz）：任务名、类、cron、状态；谁往年假档案里写数据，先看有没有按月或按年触发的任务（配置，不含数据）
SELECT d.JOB_NAME, d.JOB_GROUP, d.JOB_CLASS_NAME, t.TRIGGER_NAME, t.TRIGGER_STATE, c.CRON_EXPRESSION
  FROM a8_db.dbo.JK_JOB_DETAILS AS d
  LEFT JOIN a8_db.dbo.JK_TRIGGERS AS t ON t.JOB_NAME = d.JOB_NAME AND t.JOB_GROUP = d.JOB_GROUP
  LEFT JOIN a8_db.dbo.JK_CRON_TRIGGERS AS c ON c.TRIGGER_NAME = t.TRIGGER_NAME AND c.TRIGGER_GROUP = t.TRIGGER_GROUP
 WHERE DB_NAME() = N'a8_db'
 ORDER BY d.JOB_NAME;
GO
-- B2 A8 里与表单触发、公式、数据交换、插件相关的配置表有哪些（只列表名与估计行数，之后按表名再定下一步，不读内容）
SELECT o.name AS table_name, SUM(CASE WHEN p.index_id IN (0, 1) THEN p.row_count ELSE 0 END) AS est_rows
  FROM sys.objects AS o LEFT JOIN sys.dm_db_partition_stats AS p ON p.object_id = o.object_id
 WHERE DB_NAME() = N'a8_db' AND o.type = 'U'
   AND (o.name LIKE N'%TRIGGER%' OR o.name LIKE N'%FORMULA%' OR o.name LIKE N'%DEE%' OR o.name LIKE N'%PLUGIN%'
     OR o.name LIKE N'%BIND%' OR o.name LIKE N'%SCHEDULE%' OR o.name LIKE N'%TIMER%')
 GROUP BY o.name ORDER BY o.name;
GO
-- C ruoyi-vue-pro 一侧：对象名与列（只查元数据）。在 ruoyi-vue-pro 上执行（改 USE）；a8_db 上也可执行，a8_db 侧可能为空
-- USE [ruoyi-vue-pro];
SELECT s.name AS schema_name, o.name AS object_name, o.type_desc,
       SUM(CASE WHEN p.index_id IN (0, 1) THEN p.row_count ELSE 0 END) AS est_rows
  FROM sys.objects AS o JOIN sys.schemas AS s ON s.schema_id = o.schema_id
  LEFT JOIN sys.dm_db_partition_stats AS p ON p.object_id = o.object_id
 WHERE o.type IN ('U', 'V')
   AND (LOWER(o.name) LIKE '%leave%' OR LOWER(o.name) LIKE '%vacation%' OR LOWER(o.name) LIKE '%quota%' OR LOWER(o.name) LIKE '%annual%'
     OR LOWER(o.name) LIKE '%overtime%' OR LOWER(o.name) LIKE '%compens%' OR LOWER(o.name) LIKE '%attend%' OR LOWER(o.name) LIKE '%absence%')
 GROUP BY s.name, o.name, o.type_desc ORDER BY s.name, o.name;
GO
