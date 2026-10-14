-- 更正版本表注释（B2 复审 Nit）：同一天的再次变化不再覆盖当天版本，而是关闭它（空区间）并追加下一修订；已关闭的行不可改，任何行不可删。
COMMENT ON TABLE organization_workforce.org_unit_versions IS '组织单元版本：组织单元的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订）；粒度：每个组织单元的每个生效区间每个修订一行；与快照在同一事务写入';
COMMENT ON TABLE organization_workforce.assignment_versions IS '任职版本：员工所在部门、岗位与汇报线的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订）；粒度：每个员工的每个生效区间每个修订一行；与快照在同一事务写入';
COMMENT ON TABLE organization_workforce.legal_entity_attribution_periods IS '法人归属期间：生效区间维度，只追加（开放期间只能被关闭，已关闭的期间不改）；粒度：每个归属对象的每个归属期间一行，同一对象的期间不重叠';
COMMENT ON COLUMN organization_workforce.org_unit_versions.effective_to_date IS '失效日（不含）；为空表示至今；等于生效日表示当天被同日再次变化取代的空区间';
COMMENT ON COLUMN organization_workforce.assignment_versions.effective_to_date IS '失效日（不含）；为空表示至今；等于生效日表示当天被同日再次变化取代的空区间';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.effective_to_date IS '失效日（不含）；为空表示至今；等于生效日表示当天被同日再次变化取代的空区间';
COMMENT ON COLUMN organization_workforce.org_unit_versions.revision_number IS '修订序号：同一生效日内的序号，同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';
COMMENT ON COLUMN organization_workforce.assignment_versions.revision_number IS '修订序号：同一生效日内的序号，同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.revision_number IS '修订序号：同一生效日内的序号，同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';
