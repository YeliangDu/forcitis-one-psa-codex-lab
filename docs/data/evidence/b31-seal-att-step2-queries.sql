-- B-31 历史用印附件取证第二步（one-data，2026-10-21）：近 24 个月用印申请档案 formmain_0643 的附件计数与指纹覆盖。
-- 只读 SELECT，one_evidence_ro，SET LOCK_TIMEOUT 1000，不用 NOLOCK；输出只有计数、字节合计、长度/扩展名/取值个数分布；不选文件名、路径、ID、对象键与任何字段值。
-- 窗口：start_date >= 2024-10-01（至今）。a8_db 三表：formmain_0643 f、CTP_ATTACHMENT a（a.ATT_REFERENCE = f.ID）、CTP_FILE c（c.ID = a.FILE_URL）。
SET LOCK_TIMEOUT 1000;

-- Q1 记录与附件总量。
SELECT COUNT(DISTINCT f.ID) AS records,
       COUNT(DISTINCT CASE WHEN a.ID IS NOT NULL THEN f.ID END) AS records_with_attachment,
       COUNT(a.ID) AS attachments,
       SUM(CASE WHEN a.ID IS NOT NULL AND c.ID IS NULL THEN 1 ELSE 0 END) AS attachments_without_file_row,
       SUM(CASE WHEN c.ID IS NOT NULL THEN c.FILE_SIZE ELSE 0 END) AS total_bytes,
       SUM(CASE WHEN c.ID IS NOT NULL AND (c.FILE_SIZE IS NULL OR c.FILE_SIZE = 0) THEN 1 ELSE 0 END) AS zero_size_files,
       SUM(CASE WHEN c.ID IS NOT NULL AND a.ATTACHMENT_SIZE IS NOT NULL AND a.ATTACHMENT_SIZE <> c.FILE_SIZE THEN 1 ELSE 0 END) AS size_mismatch
  FROM a8_db.dbo.formmain_0643 f
  LEFT JOIN a8_db.dbo.CTP_ATTACHMENT a ON a.ATT_REFERENCE = f.ID
  LEFT JOIN a8_db.dbo.CTP_FILE c ON c.ID = a.FILE_URL
 WHERE f.start_date >= '2024-10-01';

-- Q2 指纹覆盖：CTP_FILE.CHECKSUM 有值/空，按长度分桶（32 位像 MD5，40 位像 SHA-1，64 位像 SHA-256）。只出长度与个数，不出值。
SELECT CASE WHEN c.CHECKSUM IS NULL OR LTRIM(RTRIM(c.CHECKSUM)) = '' THEN 'empty' ELSE CAST(LEN(LTRIM(RTRIM(c.CHECKSUM))) AS varchar(10)) END AS checksum_length,
       COUNT(*) AS files
  FROM a8_db.dbo.formmain_0643 f
  JOIN a8_db.dbo.CTP_ATTACHMENT a ON a.ATT_REFERENCE = f.ID
  JOIN a8_db.dbo.CTP_FILE c ON c.ID = a.FILE_URL
 WHERE f.start_date >= '2024-10-01'
 GROUP BY CASE WHEN c.CHECKSUM IS NULL OR LTRIM(RTRIM(c.CHECKSUM)) = '' THEN 'empty' ELSE CAST(LEN(LTRIM(RTRIM(c.CHECKSUM))) AS varchar(10)) END;

-- Q3 存放位置：OBS_OBJECT_KEY 有值/空、SYNC_OBS 取值分布。
SELECT c.SYNC_OBS, CASE WHEN c.OBS_OBJECT_KEY IS NULL OR LTRIM(RTRIM(c.OBS_OBJECT_KEY)) = '' THEN 'no_key' ELSE 'has_key' END AS obs_key, COUNT(*) AS files
  FROM a8_db.dbo.formmain_0643 f
  JOIN a8_db.dbo.CTP_ATTACHMENT a ON a.ATT_REFERENCE = f.ID
  JOIN a8_db.dbo.CTP_FILE c ON c.ID = a.FILE_URL
 WHERE f.start_date >= '2024-10-01'
 GROUP BY c.SYNC_OBS, CASE WHEN c.OBS_OBJECT_KEY IS NULL OR LTRIM(RTRIM(c.OBS_OBJECT_KEY)) = '' THEN 'no_key' ELSE 'has_key' END;

-- Q4 扩展名分布（只取最后一个点后的小写扩展名，不出文件名；含宏与旧版 Office 的个数由此得出）。
SELECT LOWER(CASE WHEN CHARINDEX('.', REVERSE(a.FILENAME)) > 0 THEN RIGHT(a.FILENAME, CHARINDEX('.', REVERSE(a.FILENAME)) - 1) ELSE '' END) AS extension, COUNT(*) AS attachments
  FROM a8_db.dbo.formmain_0643 f
  JOIN a8_db.dbo.CTP_ATTACHMENT a ON a.ATT_REFERENCE = f.ID
 WHERE f.start_date >= '2024-10-01'
 GROUP BY LOWER(CASE WHEN CHARINDEX('.', REVERSE(a.FILENAME)) > 0 THEN RIGHT(a.FILENAME, CHARINDEX('.', REVERSE(a.FILENAME)) - 1) ELSE '' END)
 ORDER BY COUNT(*) DESC;

-- Q5 版本：GENESIS_ID 相同的附件为一族；出「族大小」的分布（每个大小有多少族），看是否保留旧版本。
WITH g AS (
  SELECT a.GENESIS_ID, COUNT(*) AS n
    FROM a8_db.dbo.formmain_0643 f
    JOIN a8_db.dbo.CTP_ATTACHMENT a ON a.ATT_REFERENCE = f.ID
   WHERE f.start_date >= '2024-10-01' AND a.GENESIS_ID IS NOT NULL
   GROUP BY a.GENESIS_ID
)
SELECT n AS family_size, COUNT(*) AS families FROM g GROUP BY n ORDER BY n;

-- Q6 内容重复：窗口内 CHECKSUM 相同的文件组数、涉及文件数（有 CHECKSUM 的才算）。
WITH d AS (
  SELECT c.CHECKSUM, COUNT(*) AS n
    FROM a8_db.dbo.formmain_0643 f
    JOIN a8_db.dbo.CTP_ATTACHMENT a ON a.ATT_REFERENCE = f.ID
    JOIN a8_db.dbo.CTP_FILE c ON c.ID = a.FILE_URL
   WHERE f.start_date >= '2024-10-01' AND c.CHECKSUM IS NOT NULL AND LTRIM(RTRIM(c.CHECKSUM)) <> ''
   GROUP BY c.CHECKSUM HAVING COUNT(*) > 1
)
SELECT COUNT(*) AS duplicate_groups, SUM(n) AS files_in_duplicate_groups FROM d;

-- Q7 单号候选列：全表（不限窗口）formmain_0643 每个 nvarchar 字段的非空个数、不同取值个数、最大长度；不出任何取值。
-- 做法：对 field0029、field0032、field0034、field0036、field0041、field0042、field0064、field0076、field0077、field0048、field0065、field0066、field0073、field0046、field0063、field0062、field0044
-- 逐列执行 SELECT '<列名>' AS col, COUNT(<列>) AS non_null, COUNT(DISTINCT <列>) AS distinct_values, MAX(LEN(<列>)) AS max_len FROM a8_db.dbo.formmain_0643;（工具禁 EXEC 时逐列静态写出）
