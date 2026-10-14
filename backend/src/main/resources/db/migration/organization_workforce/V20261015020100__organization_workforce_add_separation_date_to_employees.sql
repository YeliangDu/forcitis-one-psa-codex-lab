ALTER TABLE organization_workforce.employees ADD COLUMN separation_date date;
COMMENT ON COLUMN organization_workforce.employees.separation_date IS '离职日期：组织源给出的最后在职日之后的离职生效日；组织源视图目前不提供，在提供前一律为空，不以同步发现时间代替（separated_at 是发现时间）';
