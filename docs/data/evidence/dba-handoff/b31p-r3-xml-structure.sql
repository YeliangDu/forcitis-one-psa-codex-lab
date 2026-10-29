-- B31P 第三批②：用印申请与需求工单的表单字段定义（CAP_FORM_DEFINITION.FIELD_INFO）与流程定义（WF_PROCESS_TEMPLETE.WORKFLOW）的 XML 结构树。
-- 全部在 SQL Server 内用 XQuery 解析，只输出元素名、属性名与出现次数；不输出任何属性值或文本，也不输出 XML 本身。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;

-- FIELD_INFO：是否像 XML、能否解析
SELECT t.ID AS template_id, N'FIELD_INFO' AS source_name, CASE WHEN LEFT(LTRIM(CONVERT(nvarchar(max), d.FIELD_INFO)), 1) = N'<' THEN 1 ELSE 0 END AS looks_xml,
       CASE WHEN TRY_CAST(CONVERT(nvarchar(max), d.FIELD_INFO) AS xml) IS NULL THEN 0 ELSE 1 END AS parsed_xml, LEN(CONVERT(nvarchar(max), d.FIELD_INFO)) AS length_chars
  FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.CAP_FORM_DEFINITION AS d ON d.ID = t.FORM_APPID WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053);
-- FIELD_INFO：元素名、父元素名与出现次数
WITH src AS (SELECT t.ID AS template_id, TRY_CAST(CONVERT(nvarchar(max), d.FIELD_INFO) AS xml) AS x FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.CAP_FORM_DEFINITION AS d ON d.ID = t.FORM_APPID WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053)),
     e AS (SELECT s.template_id, q.n.value('local-name(.)', 'nvarchar(200)') AS element_name, ISNULL(q.n.value('local-name(..)', 'nvarchar(200)'), N'') AS parent_element
             FROM src AS s CROSS APPLY s.x.nodes('//*') AS q(n))
SELECT template_id, parent_element, element_name, COUNT_BIG(*) AS occurrences FROM e GROUP BY template_id, parent_element, element_name ORDER BY template_id, parent_element, element_name;
-- FIELD_INFO：属性名、所属元素名与出现次数
WITH src AS (SELECT t.ID AS template_id, TRY_CAST(CONVERT(nvarchar(max), d.FIELD_INFO) AS xml) AS x FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.CAP_FORM_DEFINITION AS d ON d.ID = t.FORM_APPID WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053)),
     a AS (SELECT s.template_id, q.n.value('local-name(..)', 'nvarchar(200)') AS element_name, q.n.value('local-name(.)', 'nvarchar(200)') AS attribute_name
             FROM src AS s CROSS APPLY s.x.nodes('//*/@*') AS q(n))
SELECT template_id, element_name, attribute_name, COUNT_BIG(*) AS occurrences FROM a GROUP BY template_id, element_name, attribute_name ORDER BY template_id, element_name, attribute_name;

-- WORKFLOW：是否像 XML、能否解析
SELECT t.ID AS template_id, N'WORKFLOW' AS source_name, CASE WHEN LEFT(LTRIM(CONVERT(nvarchar(max), d.WORKFLOW)), 1) = N'<' THEN 1 ELSE 0 END AS looks_xml,
       CASE WHEN TRY_CAST(CONVERT(nvarchar(max), d.WORKFLOW) AS xml) IS NULL THEN 0 ELSE 1 END AS parsed_xml, LEN(CONVERT(nvarchar(max), d.WORKFLOW)) AS length_chars
  FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.WF_PROCESS_TEMPLETE AS d ON d.ID = t.WORKFLOW_ID WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053);
-- WORKFLOW：元素名、父元素名与出现次数
WITH src AS (SELECT t.ID AS template_id, TRY_CAST(CONVERT(nvarchar(max), d.WORKFLOW) AS xml) AS x FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.WF_PROCESS_TEMPLETE AS d ON d.ID = t.WORKFLOW_ID WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053)),
     e AS (SELECT s.template_id, q.n.value('local-name(.)', 'nvarchar(200)') AS element_name, ISNULL(q.n.value('local-name(..)', 'nvarchar(200)'), N'') AS parent_element
             FROM src AS s CROSS APPLY s.x.nodes('//*') AS q(n))
SELECT template_id, parent_element, element_name, COUNT_BIG(*) AS occurrences FROM e GROUP BY template_id, parent_element, element_name ORDER BY template_id, parent_element, element_name;
-- WORKFLOW：属性名、所属元素名与出现次数
WITH src AS (SELECT t.ID AS template_id, TRY_CAST(CONVERT(nvarchar(max), d.WORKFLOW) AS xml) AS x FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.WF_PROCESS_TEMPLETE AS d ON d.ID = t.WORKFLOW_ID WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053)),
     a AS (SELECT s.template_id, q.n.value('local-name(..)', 'nvarchar(200)') AS element_name, q.n.value('local-name(.)', 'nvarchar(200)') AS attribute_name
             FROM src AS s CROSS APPLY s.x.nodes('//*/@*') AS q(n))
SELECT template_id, element_name, attribute_name, COUNT_BIG(*) AS occurrences FROM a GROUP BY template_id, element_name, attribute_name ORDER BY template_id, element_name, attribute_name;
