-- 员工登录时登记（EmployeeRegistered）是员工聚合发出的事件；同步写入的员工行没有发过员工事件，序号为 0。
ALTER TABLE organization_workforce.employees ADD COLUMN event_count bigint NOT NULL DEFAULT 0;
COMMENT ON COLUMN organization_workforce.employees.event_count IS '事件序号：员工聚合已发出的事件数（登录时登记发一个），作为事件信封的 aggregateVersion；同步写入不改变它';
