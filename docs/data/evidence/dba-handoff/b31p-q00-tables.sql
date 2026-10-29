-- B31P Q00 的表级汇总（去掉 Flowable ACT_、Quartz qrtz_ 内部表，按表名分组计列数）；只读元数据，不读任何值。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
SELECT TOP (1000)
       c.TABLE_NAME AS table_name,
       COUNT_BIG(*) AS column_count
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
    OR c.TABLE_NAME LIKE N'bpm[_]%'
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
GROUP BY c.TABLE_NAME
ORDER BY c.TABLE_NAME;
