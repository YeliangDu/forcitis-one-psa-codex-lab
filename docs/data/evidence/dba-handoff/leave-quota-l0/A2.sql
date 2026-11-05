-- 请假额度取证 L0 段 A2（#1279）：自 leave-quota-l0.sql 拆出的单条只读 SELECT，供只读取证入口（#1377）逐文件运行；内容与原段相同，仅去掉 SET 与 GO。
-- A2 字段表：显示名 → fieldNNNN、类型、枚举引用（定义，不是数据）
SELECT f.ID AS form_id, f.NAME AS form_name, j.[display] AS field_label, j.[name] AS field_key, j.fieldType AS field_type,
       j.[type] AS field_kind, j.fieldLength AS field_length, j.enumInfo AS enum_ref
  FROM a8_db.dbo.CAP_FORM_DEFINITION AS f
 CROSS APPLY OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo')
       WITH ([display] nvarchar(255) '$.display', [name] nvarchar(255) '$.name', fieldType nvarchar(100) '$.fieldType',
             [type] nvarchar(100) '$.type', fieldLength nvarchar(50) '$.fieldLength', enumInfo nvarchar(100) '$.enumInfo') AS j
 WHERE DB_NAME() = N'a8_db' AND f.ID IN (796171863600219842, 5859649593037638153, 9052538345220490521, -7749553928318729525, -7022180195473537202, 9063501945476501361, 6280910657268830205, 1302286494557294279, 802812893430891959, 4653374441028142836, 7535998766052625231, 2928784593663115810, -1386513811900251097, -2826989769200875060, -364855800485097884, 8372459854493797226, 8604312368632513736, 4066963956786548045, -3286220380529473854, 724697480203878861) AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1
 ORDER BY f.ID, j.[name];
