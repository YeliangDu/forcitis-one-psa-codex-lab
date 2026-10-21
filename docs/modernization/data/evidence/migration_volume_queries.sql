-- 全历史迁入数据量估算：取各库各表的行数与占用空间（只读元数据，不读任何行值；one-data 2026-10-21，用户要求排在最前）
-- 两个库各执行一次：a8_db（A8）与 [ruoyi-vue-pro]（老系统）。账号 one_evidence_ro；SET LOCK_TIMEOUT 1000；语句超时 5 秒；不用 NOLOCK。
-- 输出：表名、行数（分区统计的近似值，不 COUNT(*)）、数据页 KB、索引及 LOB KB、平均行宽 = 数据 KB×1024 ÷ 行数。表名与数字不含个人信息。
-- 输出落取证通道专用目录；会话只回：①两库总行数与总 GB；②按下面「迁入范围」归并后各域的行数与 GB；③行数最大的 40 张表的表名、行数、平均行宽。
SET LOCK_TIMEOUT 1000;
SELECT TOP (400)
       s.name AS schema_name, t.name AS table_name,
       SUM(CASE WHEN i.index_id IN (0, 1) THEN p.rows ELSE 0 END)                         AS row_count,
       SUM(CASE WHEN i.index_id IN (0, 1) THEN a.used_pages ELSE 0 END) * 8                AS data_kb,
       SUM(CASE WHEN i.index_id NOT IN (0, 1) THEN a.used_pages ELSE 0 END) * 8            AS index_kb,
       SUM(a.used_pages) * 8                                                               AS used_kb
  FROM sys.tables AS t
  JOIN sys.schemas AS s ON s.schema_id = t.schema_id
  JOIN sys.indexes AS i ON i.object_id = t.object_id
  JOIN sys.partitions AS p ON p.object_id = i.object_id AND p.index_id = i.index_id
  JOIN sys.allocation_units AS a ON a.container_id = p.partition_id
 WHERE t.is_ms_shipped = 0
 GROUP BY s.name, t.name
 HAVING SUM(CASE WHEN i.index_id IN (0, 1) THEN p.rows ELSE 0 END) > 0
 ORDER BY SUM(a.used_pages) DESC;
-- 迁入范围（归并口径，由 one-data 在拿到结果后做，不在 SQL 里）：
--   A8：组织/人员/岗位（ORG_*）、审批与协同历史（COL_SUMMARY、CTP_AFFAIR、CTP_CONTENT_ALL、WF_*、CTP_COMMENT_ALL）、已迁入通用审批的表单主表与明细（formmain_*、formson_*）、附件元数据（CTP_FILE、CTP_ATTACHMENT；文件本体在 NAS，由业务方提供）
--   老系统：合同与项目（psa_contract*、psa_pm_*、psa_wbs_*、psa_task_*、psa_opportunity*）、工时（psa_time_sheet*）、成本与在制（psa_cost_*、psa_wip_*、psa_pl_*、psa_oh_*）、审批引擎历史（ACT_*、bpm_*，按 ADR-0054/0055 只读归档不迁入，单列）

-- ===== 补充查询（2026-10-21，用于迁入范围归并）：按表名分组汇总，只读元数据 =====
-- 在 a8_db 执行；老系统库的分组已能由前 20 与 ##04 行数归并，不必再跑。输出每组：表数、行数、总占用 GB、数据 GB、索引与 LOB GB。
SET LOCK_TIMEOUT 1000;
WITH sizes AS (
  SELECT t.name AS table_name,
         SUM(CASE WHEN i.index_id IN (0, 1) THEN p.rows ELSE 0 END)                  AS row_count,
         SUM(CASE WHEN i.index_id IN (0, 1) THEN a.used_pages ELSE 0 END) * 8.0       AS data_kb,
         SUM(a.used_pages) * 8.0                                                      AS used_kb
    FROM sys.tables t JOIN sys.indexes i ON i.object_id = t.object_id
    JOIN sys.partitions p ON p.object_id = i.object_id AND p.index_id = i.index_id
    JOIN sys.allocation_units a ON a.container_id = p.partition_id
   WHERE t.is_ms_shipped = 0 GROUP BY t.name
), grouped AS (
  SELECT CASE
           WHEN table_name LIKE 'ORG[_]%'                                   THEN '01 组织人员岗位(ORG_*)'
           WHEN table_name IN ('COL_SUMMARY','CTP_AFFAIR','CTP_CONTENT_ALL','CTP_COMMENT_ALL','CTP_PROCESS_LOG','WF_WORKITEM_HISTORY') THEN '02 审批协同业务历史'
           WHEN table_name IN ('WF_PROCESS_RUNNING','WF_CASE_HISTORY','WF_CASE_RUN','WF_PROCESS_RUNNING_HIS','WF_CASE_HISTORY_HIS','WF_WORKITEM_RUN','WF_WORKITEM_RUN_HIS') THEN '03 流程引擎状态'
           WHEN table_name LIKE 'FORM[_]TRIGGER[_]RECORD[_]%' OR table_name LIKE 'CAP[_]FORM[_]%RECORD%' OR table_name LIKE 'caprunninglog[_]%' OR table_name LIKE 'CAP[_]FORM[_]TRIGGER%' THEN '04 表单触发与运行日志'
           WHEN table_name LIKE 'formmain[_]%' OR table_name LIKE 'formson[_]%' OR table_name LIKE 'formson%' THEN '05 表单主表与明细'
           WHEN table_name IN ('CTP_FILE','CTP_ATTACHMENT','CTP_FILE_FINPRINT')                                              THEN '06 附件元数据'
           WHEN table_name LIKE 'LEXMISCIF[_]%'                                                                               THEN '07 U8C 凭证'
           WHEN table_name IN ('CTP_APP_LOG','LOGON_LOG') OR table_name LIKE 'M3[_]%'                                         THEN '08 日志与行为'
           ELSE '09 其他' END AS grp, row_count, data_kb, used_kb
    FROM sizes
)
SELECT grp, COUNT(*) AS table_count, SUM(row_count) AS row_count,
       CAST(SUM(used_kb) / 1048576.0 AS decimal(9,2)) AS used_gb,
       CAST(SUM(data_kb) / 1048576.0 AS decimal(9,2)) AS data_gb,
       CAST((SUM(used_kb) - SUM(data_kb)) / 1048576.0 AS decimal(9,2)) AS index_lob_gb
  FROM grouped WHERE row_count > 0 GROUP BY grp ORDER BY grp;
