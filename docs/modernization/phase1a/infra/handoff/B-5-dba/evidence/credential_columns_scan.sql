-- ADR-0037 第 3 项第 4 条 · 凭据列候选（只取库、表、列名，不取任何值）。以取证账号执行；外部列名大小写不敏感匹配，
-- 驼峰写法（accessToken 等）在本机转小写蛇形后复核。passwd / pwd 为补充词
SELECT DB_NAME() AS database_name, c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE LOWER(c.COLUMN_NAME) LIKE '%password%' OR LOWER(c.COLUMN_NAME) LIKE '%passwd%' OR LOWER(c.COLUMN_NAME) LIKE '%pwd%'
    OR LOWER(c.COLUMN_NAME) LIKE '%secret%' OR LOWER(c.COLUMN_NAME) LIKE '%totp%' OR LOWER(c.COLUMN_NAME) LIKE '%token%'
    OR LOWER(c.COLUMN_NAME) LIKE '%salt%' OR LOWER(c.COLUMN_NAME) LIKE '%hash%' OR LOWER(c.COLUMN_NAME) LIKE '%key%'
    OR LOWER(c.COLUMN_NAME) LIKE '%credential%'
 ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME;
GO
