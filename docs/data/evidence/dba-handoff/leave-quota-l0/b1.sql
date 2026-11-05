-- 请假额度取证 L0 段 B1（#1279）：自 leave-quota-l0.sql 拆出的单条只读 SELECT，供只读取证入口（#1377）逐文件运行；内容与原段相同，仅去掉 SET 与 GO。
-- B1 定时任务（A8 的 JK_* = Quartz）：任务名、类、cron、状态；谁往年假档案里写数据，先看有没有按月或按年触发的任务（配置，不含数据）
SELECT d.JOB_NAME, d.JOB_GROUP, d.JOB_CLASS_NAME, t.TRIGGER_NAME, t.TRIGGER_STATE, c.CRON_EXPRESSION
  FROM a8_db.dbo.JK_JOB_DETAILS AS d
  LEFT JOIN a8_db.dbo.JK_TRIGGERS AS t ON t.JOB_NAME = d.JOB_NAME AND t.JOB_GROUP = d.JOB_GROUP
  LEFT JOIN a8_db.dbo.JK_CRON_TRIGGERS AS c ON c.TRIGGER_NAME = t.TRIGGER_NAME AND c.TRIGGER_GROUP = t.TRIGGER_GROUP
 WHERE DB_NAME() = N'a8_db'
 ORDER BY d.JOB_NAME;
