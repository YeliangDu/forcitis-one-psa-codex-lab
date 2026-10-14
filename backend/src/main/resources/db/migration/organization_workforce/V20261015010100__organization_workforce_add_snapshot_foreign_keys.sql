-- 数据打分卡 K6：同一 schema 内的引用补外键。快照按「部门、人员」的顺序写入，引用在提交时才检查（DEFERRABLE INITIALLY DEFERRED）。
ALTER TABLE organization_workforce.org_units
    ADD CONSTRAINT fk_org_units_parent_org_unit_id FOREIGN KEY (parent_org_unit_id)
        REFERENCES organization_workforce.org_units (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_org_units_head_employee_id FOREIGN KEY (head_employee_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_org_units_regional_receivables_manager_id FOREIGN KEY (regional_receivables_manager_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE organization_workforce.assignments
    ADD CONSTRAINT fk_assignments_reports_to_employee_id FOREIGN KEY (reports_to_employee_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX ix_org_units_head_employee_id ON organization_workforce.org_units (head_employee_id);
CREATE INDEX ix_assignments_reports_to_employee_id ON organization_workforce.assignments (reports_to_employee_id);
