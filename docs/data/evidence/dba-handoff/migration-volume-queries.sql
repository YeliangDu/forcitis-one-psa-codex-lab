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
