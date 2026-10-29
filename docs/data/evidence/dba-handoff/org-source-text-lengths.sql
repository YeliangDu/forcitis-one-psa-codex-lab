-- one-data：组织源视图文本列长度（ADR-0037 第 7 项，取证账号，只读；只出行数、非空数与长度，不出任何具体值）。
-- 视图经三段名称从 a8_db 上下文读取（与 SeeyonViewSql 读的三个视图一致；部门简称来自老系统专用视图）；scope = all 为视图全部行，
-- scope = sync 为同步范围（部门 PATH 在根 00000001 下；人员按 MEMBER_SCOPE：内部或空、工号非空、部门非空、部门路径在根下）。
-- LEN 不计尾部空格，max_bytes 是 DATALENGTH（nvarchar 每字符 2 字节），两者差即尾部空格或类型差异。
SELECT N'unit' AS entity, N'name' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(NAME) AS non_null,
       SUM(CASE WHEN LEN(NAME) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(NAME), 0)) AS min_len, MAX(LEN(NAME)) AS max_len, MAX(DATALENGTH(NAME)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_unit WHERE DB_NAME() = N'a8_db' AND 1=1
UNION ALL
SELECT N'unit' AS entity, N'name' AS col, N'sync' AS scope, COUNT(*) AS n_rows, COUNT(NAME) AS non_null,
       SUM(CASE WHEN LEN(NAME) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(NAME), 0)) AS min_len, MAX(LEN(NAME)) AS max_len, MAX(DATALENGTH(NAME)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_unit WHERE DB_NAME() = N'a8_db' AND PATH LIKE N'00000001%';
GO
SELECT N'unit' AS entity, N'code' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(CODE) AS non_null,
       SUM(CASE WHEN LEN(CODE) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(CODE), 0)) AS min_len, MAX(LEN(CODE)) AS max_len, MAX(DATALENGTH(CODE)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_unit WHERE DB_NAME() = N'a8_db' AND 1=1
UNION ALL
SELECT N'unit' AS entity, N'code' AS col, N'sync' AS scope, COUNT(*) AS n_rows, COUNT(CODE) AS non_null,
       SUM(CASE WHEN LEN(CODE) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(CODE), 0)) AS min_len, MAX(LEN(CODE)) AS max_len, MAX(DATALENGTH(CODE)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_unit WHERE DB_NAME() = N'a8_db' AND PATH LIKE N'00000001%';
GO
SELECT N'post' AS entity, N'name' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(NAME) AS non_null,
       SUM(CASE WHEN LEN(NAME) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(NAME), 0)) AS min_len, MAX(LEN(NAME)) AS max_len, MAX(DATALENGTH(NAME)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_post WHERE DB_NAME() = N'a8_db' AND 1=1;
GO
SELECT N'post' AS entity, N'code' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(CODE) AS non_null,
       SUM(CASE WHEN LEN(CODE) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(CODE), 0)) AS min_len, MAX(LEN(CODE)) AS max_len, MAX(DATALENGTH(CODE)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_post WHERE DB_NAME() = N'a8_db' AND 1=1;
GO
SELECT N'member' AS entity, N'employee_no' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(CODE) AS non_null,
       SUM(CASE WHEN LEN(CODE) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(CODE), 0)) AS min_len, MAX(LEN(CODE)) AS max_len, MAX(DATALENGTH(CODE)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_member WHERE DB_NAME() = N'a8_db' AND 1=1
UNION ALL
SELECT N'member' AS entity, N'employee_no' AS col, N'sync' AS scope, COUNT(*) AS n_rows, COUNT(CODE) AS non_null,
       SUM(CASE WHEN LEN(CODE) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(CODE), 0)) AS min_len, MAX(LEN(CODE)) AS max_len, MAX(DATALENGTH(CODE)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_member WHERE DB_NAME() = N'a8_db' AND (IS_INTERNAL = 1 OR IS_INTERNAL IS NULL) AND CODE IS NOT NULL AND CODE <> N'' AND ORG_UNIT_ID IS NOT NULL AND ORG_UNIT_PATH LIKE N'00000001%';
GO
SELECT N'member' AS entity, N'name' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(NAME) AS non_null,
       SUM(CASE WHEN LEN(NAME) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(NAME), 0)) AS min_len, MAX(LEN(NAME)) AS max_len, MAX(DATALENGTH(NAME)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_member WHERE DB_NAME() = N'a8_db' AND 1=1
UNION ALL
SELECT N'member' AS entity, N'name' AS col, N'sync' AS scope, COUNT(*) AS n_rows, COUNT(NAME) AS non_null,
       SUM(CASE WHEN LEN(NAME) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(NAME), 0)) AS min_len, MAX(LEN(NAME)) AS max_len, MAX(DATALENGTH(NAME)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_member WHERE DB_NAME() = N'a8_db' AND (IS_INTERNAL = 1 OR IS_INTERNAL IS NULL) AND CODE IS NOT NULL AND CODE <> N'' AND ORG_UNIT_ID IS NOT NULL AND ORG_UNIT_PATH LIKE N'00000001%';
GO
SELECT N'member' AS entity, N'email' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(ENTERPRISE_EMAIL) AS non_null,
       SUM(CASE WHEN LEN(ENTERPRISE_EMAIL) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(ENTERPRISE_EMAIL), 0)) AS min_len, MAX(LEN(ENTERPRISE_EMAIL)) AS max_len, MAX(DATALENGTH(ENTERPRISE_EMAIL)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_member WHERE DB_NAME() = N'a8_db' AND 1=1
UNION ALL
SELECT N'member' AS entity, N'email' AS col, N'sync' AS scope, COUNT(*) AS n_rows, COUNT(ENTERPRISE_EMAIL) AS non_null,
       SUM(CASE WHEN LEN(ENTERPRISE_EMAIL) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(ENTERPRISE_EMAIL), 0)) AS min_len, MAX(LEN(ENTERPRISE_EMAIL)) AS max_len, MAX(DATALENGTH(ENTERPRISE_EMAIL)) AS max_bytes
  FROM a8_db.forcitis_one.v_org_member WHERE DB_NAME() = N'a8_db' AND (IS_INTERNAL = 1 OR IS_INTERNAL IS NULL) AND CODE IS NOT NULL AND CODE <> N'' AND ORG_UNIT_ID IS NOT NULL AND ORG_UNIT_PATH LIKE N'00000001%';
GO
SELECT N'unit' AS entity, N'short_name (legacy view)' AS col, N'all' AS scope, COUNT(*) AS n_rows, COUNT(short_name) AS non_null,
       SUM(CASE WHEN LEN(short_name) > 0 THEN 1 ELSE 0 END) AS non_empty, MIN(NULLIF(LEN(short_name), 0)) AS min_len, MAX(LEN(short_name)) AS max_len, MAX(DATALENGTH(short_name)) AS max_bytes
  FROM [ruoyi-vue-pro].dbo.v_one_legacy_org_short_name WHERE DB_NAME() = N'a8_db' AND 1=1;
GO
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM a8_db.INFORMATION_SCHEMA.COLUMNS
 WHERE DB_NAME() = N'a8_db' AND TABLE_SCHEMA = N'forcitis_one' AND ((TABLE_NAME IN (N'v_org_unit', N'v_org_post') AND COLUMN_NAME IN (N'NAME', N'CODE'))
    OR (TABLE_NAME = N'v_org_member' AND COLUMN_NAME IN (N'NAME', N'CODE', N'ENTERPRISE_EMAIL'))) ORDER BY TABLE_NAME, COLUMN_NAME;
GO
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS
 WHERE DB_NAME() = N'a8_db' AND TABLE_NAME = N'v_one_legacy_org_short_name' ORDER BY COLUMN_NAME;
GO
