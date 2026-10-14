-- 交付执行模块整体删除（D-1a-127）：1a 不做工作包，也不据此核对工时；以后重做时从规格重新建表。
DROP TABLE delivery_execution.work_packages;
DROP SCHEMA delivery_execution;
