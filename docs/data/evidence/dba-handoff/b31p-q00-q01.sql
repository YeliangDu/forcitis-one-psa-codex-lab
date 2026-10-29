-- B31P 元数据探查 Q01 + Q00 首页（ARC-1017-DAT-01）。只读表名列名，不读任何值。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (2)
       name AS database_name,
       is_read_committed_snapshot_on
FROM sys.databases
WHERE name IN (N'a8_db', N'ruoyi-vue-pro')
ORDER BY name;

SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
-- 首页 N''、N'' 均绑定为空串。
SELECT TOP (1000)
       c.TABLE_NAME AS table_name,
       c.COLUMN_NAME AS column_name
FROM INFORMATION_SCHEMA.COLUMNS AS c
WHERE c.TABLE_SCHEMA = N'dbo'
  AND (
       c.TABLE_NAME LIKE N'CAP[_]%'
    OR c.TABLE_NAME LIKE N'CTP[_]%'
    OR c.TABLE_NAME LIKE N'COL[_]%'
    OR c.TABLE_NAME LIKE N'BPM[_]%'
    OR c.TABLE_NAME LIKE N'WF[_]%'
    OR c.TABLE_NAME LIKE N'ORG[_]%'
    OR c.TABLE_NAME LIKE N'DOC[_]%'
    OR c.TABLE_NAME LIKE N'ACT[_]%'
    OR c.TABLE_NAME LIKE N'bpm[_]%'
    OR c.TABLE_NAME LIKE N'qrtz[_]%'
    OR c.TABLE_NAME LIKE N'%config%'
    OR c.TABLE_NAME LIKE N'%rule%'
    OR c.TABLE_NAME LIKE N'%schedule%'
    OR c.TABLE_NAME LIKE N'%job%'
    OR c.TABLE_NAME LIKE N'%asset%'
    OR c.TABLE_NAME LIKE N'%seal%'
    OR c.TABLE_NAME LIKE N'%anniver%'
    OR c.TABLE_NAME LIKE N'system[_]role%'
    OR c.TABLE_NAME LIKE N'system[_]post%'
    OR c.TABLE_NAME LIKE N'psa[_]quotation%'
    OR c.TABLE_NAME IN (
       N'formmain_0012', N'formmain_0642', N'formmain_0643',
       N'formmain_0875', N'formmain_0879',
       N'formmain_1051', N'formmain_1055', N'formmain_1071',
       N'formmain_1065', N'formmain_1067', N'formmain_1069',
       N'formmain_0855', N'formmain_0856',
       N'formmain_1239', N'formmain_1250', N'formmain_1377',
       N'formmain_0307', N'formmain_0515',
       N'formmain_0366', N'formmain_0471'
    )
  )
  AND (
       c.TABLE_NAME > N''
    OR (c.TABLE_NAME = N''
        AND c.COLUMN_NAME > N'')
  )
ORDER BY c.TABLE_NAME, c.COLUMN_NAME;
