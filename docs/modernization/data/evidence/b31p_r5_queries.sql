-- B31P 第五批查询草稿（one-data，2026-10-17）：补第四批 Q6-A 因「短汉字可能是人名」整属性不出值而漏掉的节点名与角色名。
-- 原则：只放行三类短汉字取值——①等于 ORG_ROLE / ORG_POST 的名称（职责定义是配置）；②含节点动词（审批、审核、确认、办理、受理、处理、开始、结束、会签、加签）；
-- ③含职责词（负责人、经理、总监、主任、助理、部门）。其余短汉字取值只计数、不出值。含 8 位以上数字的取值仍不出。
-- 只读 SELECT；SET LOCK_TIMEOUT 1000；语句超时 2s；不用 NOLOCK；one_evidence_ro。
SET LOCK_TIMEOUT 1000;
WITH wf AS (
  SELECT t.ID AS template_id, TRY_CAST(CAST(w.WORKFLOW AS nvarchar(max)) AS xml) AS x
    FROM a8_db.dbo.CTP_TEMPLATE AS t
    JOIN a8_db.dbo.WF_PROCESS_TEMPLETE AS w ON w.ID = t.WORKFLOW_ID
   WHERE t.ID IN (-803037765431705053, 5779151688961574431, 16074125293700, 7187395059800230659, 9068384877204753833,
                  5324409057402766767, 6389177723207763537, 2916490644621970991)
), attrs AS (
  SELECT wf.template_id, a.value('local-name(..)', 'nvarchar(20)') AS element_name,
         a.value('local-name(.)', 'nvarchar(40)') AS attribute_name, a.value('.', 'nvarchar(200)') AS attribute_value
    FROM wf CROSS APPLY wf.x.nodes('//@*') AS n(a) WHERE wf.x IS NOT NULL
), shortcn AS (
  SELECT * FROM attrs
   WHERE LEN(attribute_value) BETWEEN 2 AND 60
     AND attribute_value COLLATE Latin1_General_BIN2 LIKE N'%[一-龥]%'  -- 默认排序规则下这个范围几乎匹配不到，须用二进制排序规则（one-ops 首跑发现）
     AND attribute_value NOT LIKE N'%[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%'
), allowed AS (
  SELECT * FROM shortcn
   WHERE attribute_value IN (SELECT NAME FROM a8_db.dbo.ORG_ROLE UNION SELECT NAME FROM a8_db.dbo.ORG_POST)
      OR attribute_value LIKE N'%审批%' OR attribute_value LIKE N'%审核%' OR attribute_value LIKE N'%确认%' OR attribute_value LIKE N'%办理%'
      OR attribute_value LIKE N'%受理%' OR attribute_value LIKE N'%处理%' OR attribute_value LIKE N'%开始%' OR attribute_value LIKE N'%结束%'
      OR attribute_value LIKE N'%会签%' OR attribute_value LIKE N'%加签%' OR attribute_value LIKE N'%负责人%' OR attribute_value LIKE N'%经理%'
      OR attribute_value LIKE N'%总监%' OR attribute_value LIKE N'%主任%' OR attribute_value LIKE N'%助理%' OR attribute_value LIKE N'%部门%'
)
SELECT template_id, element_name, attribute_name, attribute_value, COUNT_BIG(*) AS occurrences, CAST(1 AS bit) AS value_shown
  FROM allowed GROUP BY template_id, element_name, attribute_name, attribute_value
UNION ALL
SELECT s.template_id, s.element_name, s.attribute_name, NULL, COUNT_BIG(*), CAST(0 AS bit)
  FROM shortcn AS s
 WHERE NOT EXISTS (SELECT 1 FROM allowed AS a WHERE a.template_id = s.template_id AND a.element_name = s.element_name
                   AND a.attribute_name = s.attribute_name AND a.attribute_value = s.attribute_value)
 GROUP BY s.template_id, s.element_name, s.attribute_name
ORDER BY template_id, element_name, attribute_name;   -- 行数上限 2000
