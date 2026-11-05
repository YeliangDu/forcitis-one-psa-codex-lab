-- 请假额度取证 L0 段 A5（#1279）：自 leave-quota-l0.sql 拆出的单条只读 SELECT，供只读取证入口（#1377）逐文件运行；内容与原段相同，仅去掉 SET 与 GO。 运行库：a8_db（语句内有库名守卫，选错库返回空结果，不是无数据）。
-- A5 目标表的物理列（formmain_NNNN 与 fieldNNNN 的对应校验）和估计行数；不读行
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE, c.IS_NULLABLE,
       (SELECT SUM(p.row_count) FROM sys.dm_db_partition_stats p WHERE p.object_id = OBJECT_ID(N'a8_db.dbo.' + c.TABLE_NAME) AND p.index_id IN (0, 1)) AS est_rows
  FROM INFORMATION_SCHEMA.COLUMNS AS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME IN (N'formmain_0787', N'formmain_0788', N'formmain_0789', N'formmain_0793', N'formmain_0820', N'formmain_1227', N'formmain_0777', N'formmain_0781', N'formmain_0798', N'formmain_0786', N'formmain_1114', N'formmain_1228', N'formmain_1113', N'formmain_0800', N'formmain_0858', N'formmain_0864', N'formmain_1318', N'formmain_0863', N'formmain_0834', N'formmain_0841')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
