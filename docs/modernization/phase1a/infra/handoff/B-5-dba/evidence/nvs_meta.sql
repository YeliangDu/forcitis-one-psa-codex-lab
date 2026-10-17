-- 老系统项目「重要性级别」（N/V/S）前置：含 importance/level/grade 的列所在表、字典里取值为 N/V/S 的字典类型（只看元数据与字典定义，不读业务数据）。只在 a8_db 上下文出结果。
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND (c.COLUMN_NAME LIKE N'%importan%' OR c.COLUMN_NAME LIKE N'%project_level%' OR c.COLUMN_NAME LIKE N'%project_grade%' OR c.COLUMN_NAME LIKE N'%review_level%' OR c.COLUMN_NAME LIKE N'%nvs%')
 ORDER BY c.TABLE_NAME, c.COLUMN_NAME;
GO
SELECT d.dict_type, d.value, d.label, d.sort, d.status FROM [ruoyi-vue-pro].dbo.system_dict_data d
 WHERE DB_NAME() = N'a8_db' AND d.deleted = 0 AND d.dict_type IN (SELECT dict_type FROM [ruoyi-vue-pro].dbo.system_dict_data WHERE deleted = 0 AND value IN (N'N', N'V', N'S') GROUP BY dict_type HAVING COUNT(DISTINCT value) >= 2)
 ORDER BY d.dict_type, d.sort;
GO
