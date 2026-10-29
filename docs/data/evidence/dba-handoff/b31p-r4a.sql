-- B31P 第四批（one-data 草稿 @4638ceb9 的 Q5-A、Q6-A、Q6-B，one-ops 审后调整）。只读；one_evidence_ro（ADR-0037 第 7 项）。
-- 调整：DB_NAME() 守卫（两库各跑一次，只在 a8_db 出数据）；Q6-A 的取值输出加属性级防线——某属性只要有任何取值是 2–3 个汉字（可能是人名）或全为 8 位以上数字，整属性不出值，只报个数；
--   取值本身仍须不含 8 位以上连续数字、长度 ≤ 40。结果只落本机会话临时目录；回给 one-data 的内容先人工剔除人名样式。
SET LOCK_TIMEOUT 1000;
SELECT t.ID AS template_id, t.SUBJECT AS template_name, f.ID AS form_id, f.NAME AS form_name,
       j.[display] AS field_label, j.[name] AS field_key, j.fieldType AS field_type, j.[type] AS field_kind,
       j.fieldLength AS field_length, j.isNull AS is_nullable_flag, j.enumInfo AS enum_ref
  FROM a8_db.dbo.CTP_TEMPLATE AS t
  JOIN a8_db.dbo.CAP_FORM_DEFINITION AS f ON f.ID = t.FORM_APPID
 CROSS APPLY OPENJSON(CAST(f.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo')
       WITH ([display] nvarchar(255) '$.display', [name] nvarchar(255) '$.name', fieldType nvarchar(100) '$.fieldType',
             [type] nvarchar(100) '$.type', fieldLength nvarchar(50) '$.fieldLength', isNull nvarchar(20) '$.isNull',
             enumInfo nvarchar(100) '$.enumInfo') AS j
 WHERE DB_NAME() = N'a8_db'
   AND t.ID IN (-803037765431705053, 16074125293700, 5779151688961574431, 16486247748910, 16620851075370, 8339262726610963687,
                2916490644621970991, 5324409057402766767, 6389177723207763537, 7187395059800230659, 9068384877204753833)
   AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1
 ORDER BY t.ID, f.ID;
WITH wf AS (
  SELECT t.ID AS template_id, TRY_CAST(CAST(w.WORKFLOW AS nvarchar(max)) AS xml) AS x
    FROM a8_db.dbo.CTP_TEMPLATE AS t
    JOIN a8_db.dbo.WF_PROCESS_TEMPLETE AS w ON w.ID = t.WORKFLOW_ID
   WHERE DB_NAME() = N'a8_db'
     AND t.ID IN (-803037765431705053, 5779151688961574431, 7187395059800230659, 9068384877204753833, 5324409057402766767,
                  6389177723207763537, 8339262726610963687, 2916490644621970991, 16074125293700, 16486247748910)
), attrs AS (
  SELECT wf.template_id,
         a.value('local-name(..)', 'nvarchar(20)') AS element_name,
         a.value('local-name(.)',  'nvarchar(40)') AS attribute_name,
         a.value('.', 'nvarchar(200)') AS attribute_value
    FROM wf CROSS APPLY wf.x.nodes('//@*') AS n(a)
   WHERE wf.x IS NOT NULL
), prof AS (
  SELECT template_id, element_name, attribute_name, COUNT_BIG(*) AS occurrences, COUNT(DISTINCT attribute_value) AS distinct_values,
         MAX(CASE WHEN attribute_value LIKE N'%[^ -~]%' AND LEN(attribute_value) <= 3 THEN 1 ELSE 0 END) AS has_short_cjk,
         MAX(CASE WHEN attribute_value LIKE N'%[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%' THEN 1 ELSE 0 END) AS has_long_digits
    FROM attrs GROUP BY template_id, element_name, attribute_name
)
SELECT p.template_id, p.element_name, p.attribute_name, p.occurrences, p.distinct_values, p.has_short_cjk, p.has_long_digits,
       CASE WHEN p.distinct_values <= 15 AND p.has_short_cjk = 0 AND LEN(a.attribute_value) <= 40
                 AND a.attribute_value NOT LIKE N'%[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%' THEN a.attribute_value END AS low_cardinality_value,
       CASE WHEN p.distinct_values <= 15 AND p.has_short_cjk = 0 AND LEN(a.attribute_value) <= 40
                 AND a.attribute_value NOT LIKE N'%[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%' THEN COUNT_BIG(*) END AS value_count
  FROM prof AS p
  JOIN attrs AS a ON a.template_id = p.template_id AND a.element_name = p.element_name AND a.attribute_name = p.attribute_name
 GROUP BY p.template_id, p.element_name, p.attribute_name, p.occurrences, p.distinct_values, p.has_short_cjk, p.has_long_digits,
          CASE WHEN p.distinct_values <= 15 AND p.has_short_cjk = 0 AND LEN(a.attribute_value) <= 40
                    AND a.attribute_value NOT LIKE N'%[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%' THEN a.attribute_value END
 ORDER BY p.template_id, p.element_name, p.attribute_name;
SELECT TOP (500) np.ID AS permission_id, np.CATEGORY, np.NAME AS permission_name, np.TYPE, np.IS_DELETE, np.SORT
  FROM a8_db.dbo.CTP_NODE_PERMISSION AS np
 WHERE DB_NAME() = N'a8_db'
 ORDER BY np.CATEGORY, np.SORT;
