-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：部门树
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q02 部门树：拥有项目的交付部门及其上级（用于确定「区域/BU」取哪一级）
-- 依赖：psa_pm_project.dept_id；system_dept.id / name / parent_id。
-- 不确定度：中。parent_id 来自 A8 PATH 去掉最后 4 位（docs/data/column-ownership.md system_dept 行），层级深度未核实。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT sd.id AS dept_id, sd.name AS dept_name, sd.parent_id, sp.name AS parent_name,
       COUNT_BIG(p.id) AS project_cnt
FROM dbo.psa_pm_project p
LEFT JOIN dbo.system_dept sd ON sd.id = p.dept_id
LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
GROUP BY sd.id, sd.name, sd.parent_id, sp.name
ORDER BY sp.name, sd.name;
END
