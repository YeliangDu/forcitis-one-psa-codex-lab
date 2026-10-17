SELECT c.COLUMN_NAME, c.DATA_TYPE, c.CHARACTER_MAXIMUM_LENGTH FROM a8_db.INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME = N'formmain_0307' AND c.COLUMN_NAME IN (N'field0217', N'field0294', N'field0600', N'field0534', N'field0640', N'field0641', N'field0233', N'field0242', N'field0413', N'field0241', N'field0486') ORDER BY c.COLUMN_NAME;
GO
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME IN (N'system_post', N'system_user_post') AND c.COLUMN_NAME IN (N'deleted', N'status', N'user_id', N'post_id', N'code', N'name', N'id') ORDER BY c.TABLE_NAME, c.COLUMN_NAME;
GO
