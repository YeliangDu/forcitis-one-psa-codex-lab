-- E-B6 前置：老系统里报销、付款、采购、开票、分包相关表与金额列的元数据（只看表名、列名、类型与行数，不读数据）。只在 a8_db 上下文出结果。
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db'
   AND (c.TABLE_NAME LIKE N'%reimb%' OR c.TABLE_NAME LIKE N'%expense%' OR c.TABLE_NAME LIKE N'%payment%' OR c.TABLE_NAME LIKE N'%purchase%'
        OR c.TABLE_NAME LIKE N'%invoice%' OR c.TABLE_NAME LIKE N'%subcontract%' OR c.TABLE_NAME LIKE N'%sub_contract%' OR c.TABLE_NAME LIKE N'%outsourc%')
   AND (c.COLUMN_NAME LIKE N'%amount%' OR c.COLUMN_NAME LIKE N'%money%' OR c.COLUMN_NAME LIKE N'%total%' OR c.COLUMN_NAME LIKE N'%price%'
        OR c.COLUMN_NAME LIKE N'%create_time%' OR c.COLUMN_NAME LIKE N'%deleted%' OR c.COLUMN_NAME LIKE N'%status%')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
