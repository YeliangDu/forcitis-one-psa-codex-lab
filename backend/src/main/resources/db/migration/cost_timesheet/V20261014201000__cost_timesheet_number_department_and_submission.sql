-- 工时单带单据编号与事实发生时的部门（数据打分卡 K1、K2）、提交日、查询索引（K13）；审批由事件异步发起，工时单不再记审批实例。
-- 新增的必填列没有默认值：库里若已有行则迁移失败，而不是给旧行补一个猜测的值（1a 修复批前不部署）。
ALTER TABLE cost_timesheet.timesheets ADD COLUMN timesheet_number text NOT NULL;
ALTER TABLE cost_timesheet.timesheets ADD CONSTRAINT uk_timesheets_timesheet_number UNIQUE (timesheet_number);
ALTER TABLE cost_timesheet.timesheets ADD COLUMN org_unit_id bigint NOT NULL;
ALTER TABLE cost_timesheet.timesheets ADD COLUMN submitted_date date;
ALTER TABLE cost_timesheet.timesheets DROP COLUMN approval_instance_id;

COMMENT ON TABLE cost_timesheet.timesheets IS '工时单：聚合根；粒度：每张工时单一行；状态机在聚合内，审批结果经审批完成事件回写';
COMMENT ON COLUMN cost_timesheet.timesheets.timesheet_number IS '单据编号：创建时由编号服务取号（TIMESHEET），全局唯一；事件信封的单据编号落在业务行上';
COMMENT ON COLUMN cost_timesheet.timesheets.org_unit_id IS '组织单元（组织单元 ID）：创建时填报人所在的部门；业务事实归属于事实发生时的部门，调岗后不漂移';
COMMENT ON COLUMN cost_timesheet.timesheets.submitted_date IS '提交日：员工提交审批的业务日期（Asia/Shanghai）；重新提交时更新；与工作日期是两件事，空表示从未提交';
COMMENT ON COLUMN cost_timesheet.timesheets.work_date IS '工作日期：员工填写的、工时发生的那一天；创建时校验不得晚于今天，之后不再改写；事件的业务日期取它';
COMMENT ON COLUMN cost_timesheet.timesheets.event_count IS '事件序号：该聚合已记录的领域事件数，每个事件加一，下一个事件取其加一作信封的聚合版本；与乐观锁版本 version（每次保存加一）是两个计数';

CREATE INDEX ix_timesheets_employee_id ON cost_timesheet.timesheets (employee_id, work_date);
CREATE INDEX ix_timesheets_org_unit_id ON cost_timesheet.timesheets (org_unit_id, work_date);
CREATE INDEX ix_timesheets_work_package_id ON cost_timesheet.timesheets (work_package_id) WHERE status = 'SUBMITTED';
CREATE INDEX ix_timesheets_status ON cost_timesheet.timesheets (status, updated_at) WHERE status IN ('SUBMITTED', 'RETURNED');
