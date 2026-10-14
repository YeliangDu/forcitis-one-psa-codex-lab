-- 数据打分卡 K8、K9：表注释写明粒度，补全缺失的列注释。
COMMENT ON TABLE organization_workforce.positions IS '岗位：组织源岗位的快照，停用与删除同步（RULE-367）；粒度：每个岗位一行';
COMMENT ON TABLE organization_workforce.org_units IS '组织单元：组织源组织单元的快照；停用（DISABLED）与撤销（REVOKED，组织源不再返回）分开（RULE-355/357）；粒度：每个组织单元一行，变化历史在 org_unit_versions';
COMMENT ON TABLE organization_workforce.employees IS '员工：组织源人员的快照；没有登录名即离职（RULE-363），不含生日等敏感信息；粒度：每个员工一行';
COMMENT ON TABLE organization_workforce.assignments IS '任职：员工所在组织单元、岗位与直属领导（汇报线），组织源为源；粒度：每个员工当前一行，变化历史在 assignment_versions';
COMMENT ON TABLE organization_workforce.sync_runs IS '同步记录：组织快照每次同步的记录与缩水保护结果；粒度：每次同步运行一行';
COMMENT ON TABLE organization_workforce.sync_diffs IS '同步差异：每次同步改动的对象与字段，以及数据例外（ISSUE）；粒度：每次同步中每个被改动或有例外的对象一行';
COMMENT ON TABLE organization_workforce.legal_entities IS '法人主体：由管理员在新系统维护的法人小表（约 5 家），不从外部系统同步；粒度：每个法人一行，变化历史在 legal_entity_versions';
COMMENT ON COLUMN organization_workforce.sync_runs.version IS '乐观锁版本';
COMMENT ON COLUMN organization_workforce.legal_entities.version IS '乐观锁版本';
