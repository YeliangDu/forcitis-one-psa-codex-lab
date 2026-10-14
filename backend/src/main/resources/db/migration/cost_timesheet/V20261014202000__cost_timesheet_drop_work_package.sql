-- 工时单不再记工作包（D-1a-107、D-1a-127）：交付执行模块整体删除，工时不与工作包的剩余工时核对。
-- 1a 修复批前不部署，库里没有需要保留的工作包取值；若已有行，删列会丢掉它们，部署前先确认。
DROP INDEX cost_timesheet.ix_timesheets_work_package_id;
ALTER TABLE cost_timesheet.timesheets DROP COLUMN work_package_id;
