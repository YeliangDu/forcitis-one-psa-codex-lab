-- 数据打分卡 K8、K9：表注释写明粒度，补全缺失的列注释。
COMMENT ON TABLE organization_attendance.work_calendar_years IS '工作日历年度：某个日历的一个已维护年份；没有这一行的年份视为未维护，依赖它的计算会失败；粒度：每个日历的每个年份一行';
COMMENT ON TABLE organization_attendance.work_calendar_days IS '工作日历日期：周一至周五上班、周末休息之外的例外日期；粒度：每个年份内每个例外日期一行';
COMMENT ON COLUMN organization_attendance.work_calendar_years.id IS '工作日历年度 ID';
COMMENT ON COLUMN organization_attendance.work_calendar_years.version IS '乐观锁版本';
COMMENT ON COLUMN organization_attendance.work_calendar_years.created_at IS '创建时间';
COMMENT ON COLUMN organization_attendance.work_calendar_years.updated_at IS '更新时间';
COMMENT ON COLUMN organization_attendance.work_calendar_days.id IS '工作日历日期 ID';
COMMENT ON COLUMN organization_attendance.work_calendar_days.created_at IS '创建时间';
COMMENT ON COLUMN organization_attendance.work_calendar_days.updated_at IS '更新时间';
