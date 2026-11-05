-- 请假额度取证 L0 段 B2（#1279）：自 leave-quota-l0.sql 拆出的单条只读 SELECT，供只读取证入口（#1377）逐文件运行；内容与原段相同，仅去掉 SET 与 GO。 运行库：a8_db（语句内有库名守卫，选错库返回空结果，不是无数据）。
-- B2 A8 里与表单触发、公式、数据交换、插件相关的配置表有哪些（只列表名与估计行数，之后按表名再定下一步，不读内容）
SELECT o.name AS table_name, SUM(CASE WHEN p.index_id IN (0, 1) THEN p.row_count ELSE 0 END) AS est_rows
  FROM sys.objects AS o LEFT JOIN sys.dm_db_partition_stats AS p ON p.object_id = o.object_id
 WHERE DB_NAME() = N'a8_db' AND o.type = 'U'
   AND (o.name LIKE N'%TRIGGER%' OR o.name LIKE N'%FORMULA%' OR o.name LIKE N'%DEE%' OR o.name LIKE N'%PLUGIN%'
     OR o.name LIKE N'%BIND%' OR o.name LIKE N'%SCHEDULE%' OR o.name LIKE N'%TIMER%')
 GROUP BY o.name ORDER BY o.name;
