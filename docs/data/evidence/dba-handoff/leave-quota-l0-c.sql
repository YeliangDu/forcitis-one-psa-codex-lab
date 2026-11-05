-- 请假额度取证 L0 段 C（#1279）：自 leave-quota-l0.sql 拆出的单条只读 SELECT，供只读取证入口（#1377）逐文件运行；内容与原段相同，仅去掉 SET 与 GO。 运行库：ruoyi-vue-pro（语句内有库名守卫，选错库返回空结果，不是无数据）。
-- C ruoyi-vue-pro 一侧：对象名与估计行数（只查元数据）；库名用 DB_NAME() 守卫，只在 ruoyi-vue-pro 出结果
SELECT s.name AS schema_name, o.name AS object_name, o.type_desc,
       SUM(CASE WHEN p.index_id IN (0, 1) THEN p.row_count ELSE 0 END) AS est_rows
  FROM sys.objects AS o JOIN sys.schemas AS s ON s.schema_id = o.schema_id
  LEFT JOIN sys.dm_db_partition_stats AS p ON p.object_id = o.object_id
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND o.type IN ('U', 'V')
   AND (LOWER(o.name) LIKE '%leave%' OR LOWER(o.name) LIKE '%vacation%' OR LOWER(o.name) LIKE '%quota%' OR LOWER(o.name) LIKE '%annual%'
     OR LOWER(o.name) LIKE '%overtime%' OR LOWER(o.name) LIKE '%compens%' OR LOWER(o.name) LIKE '%attend%' OR LOWER(o.name) LIKE '%absence%')
 GROUP BY s.name, o.name, o.type_desc ORDER BY s.name, o.name;
