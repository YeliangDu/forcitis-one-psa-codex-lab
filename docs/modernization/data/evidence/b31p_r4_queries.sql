-- B31P 第四批查询草稿（one-data，2026-10-17，依据 one-ops 第三批结构探查 b31p_r3_result.md）
-- 只读 SELECT；SET LOCK_TIMEOUT 1000；语句超时 2s（驱动设置）；不用 NOLOCK / READ UNCOMMITTED；执行账号 one_evidence_ro。
-- 11 个模板 id（Q02 已确认）：
--   -803037765431705053 需求工单; 16074125293700 与 5779151688961574431 用印申请; 16486247748910、16620851075370（已删除）、8339262726610963687 新增报价;
--   2916490644621970991 感恩节活动; 5324409057402766767 IT资产领用申请; 6389177723207763537 创新项目发布申请; 7187395059800230659 周年贺卡; 9068384877204753833 IT资产采购申请
-- 输出只落取证通道专用目录；会话只回结论、计数与「键是否存在」。字段定义与受控值是空白配置，不含实例值。

-- ===== Q5-A 表单字段定义（JSON，键路径取自 r3 结果；定义不含业务值）=====
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
 WHERE t.ID IN (-803037765431705053, 16074125293700, 5779151688961574431, 16486247748910, 16620851075370, 8339262726610963687,
                2916490644621970991, 5324409057402766767, 6389177723207763537, 7187395059800230659, 9068384877204753833)
   AND ISJSON(CAST(f.FIELD_INFO AS nvarchar(max))) = 1
 ORDER BY t.ID, f.ID;   -- 预计 11 个模板 × 数十字段，远小于 1000 行；超过即截断并报

-- ===== Q5-B 受控值（枚举项）：先确认 enum_ref 与 CTP_ENUM_ITEM.REF_ENUMID 的对应关系（假设，待确认），再取 =====
-- 假设：enumInfo 保存 CTP_ENUM.ID（数字）。若不成立，只回「对应关系不成立」。
SELECT TOP (500) e.ID AS enum_id, e.DESCRIPTION AS enum_description, i.SORTNUMBER AS item_order, i.SHOWVALUE AS item_label,
       i.ENUMVALUE AS item_value, i.STATE AS item_state
  FROM a8_db.dbo.CTP_ENUM AS e
  JOIN a8_db.dbo.CTP_ENUM_ITEM AS i ON i.REF_ENUMID = e.ID
 WHERE e.ID IN (/* Q5-A 得到并确认的 enum_ref 数字集合，由执行器绑定 */)
 ORDER BY e.ID, i.SORTNUMBER;

-- ===== Q6-A 流程定义 XML：属性值画像（只出低基数取值与计数；高基数属性只标注，不出值）=====
-- 元素与属性均为缩写（r3 结果）；含 8 位以上连续数字的取值视为标识符，不输出。
WITH wf AS (
  SELECT t.ID AS template_id, TRY_CAST(CAST(w.WORKFLOW AS nvarchar(max)) AS xml) AS x
    FROM a8_db.dbo.CTP_TEMPLATE AS t
    JOIN a8_db.dbo.WF_PROCESS_TEMPLETE AS w ON w.ID = t.WORKFLOW_ID
   WHERE t.ID IN (-803037765431705053, 5779151688961574431, 7187395059800230659, 9068384877204753833, 5324409057402766767,
                  6389177723207763537, 8339262726610963687, 2916490644621970991, 16074125293700, 16486247748910)
), attrs AS (
  SELECT wf.template_id,
         a.value('local-name(..)', 'nvarchar(20)') AS element_name,
         a.value('local-name(.)',  'nvarchar(40)') AS attribute_name,
         a.value('.', 'nvarchar(200)') AS attribute_value
    FROM wf CROSS APPLY wf.x.nodes('//@*') AS n(a)
   WHERE wf.x IS NOT NULL
), prof AS (
  SELECT template_id, element_name, attribute_name, COUNT_BIG(*) AS occurrences,
         COUNT(DISTINCT attribute_value) AS distinct_values
    FROM attrs GROUP BY template_id, element_name, attribute_name
)
SELECT p.template_id, p.element_name, p.attribute_name, p.occurrences, p.distinct_values,
       CASE WHEN p.distinct_values <= 15 THEN a.attribute_value END AS low_cardinality_value,
       CASE WHEN p.distinct_values <= 15 THEN COUNT_BIG(a.attribute_value) END AS value_count
  FROM prof AS p
  JOIN attrs AS a ON a.template_id = p.template_id AND a.element_name = p.element_name AND a.attribute_name = p.attribute_name
 WHERE p.distinct_values > 15
    OR (LEN(a.attribute_value) <= 40 AND a.attribute_value NOT LIKE N'%[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%')
 GROUP BY p.template_id, p.element_name, p.attribute_name, p.occurrences, p.distinct_values,
          CASE WHEN p.distinct_values <= 15 THEN a.attribute_value END
 ORDER BY p.template_id, p.element_name, p.attribute_name;   -- 行数上限 2000；超过即截断并报

-- ===== Q6-B 节点权限定义（节点策略名称与操作，无成员）=====
SELECT TOP (500) np.ID AS permission_id, np.CATEGORY, np.NAME AS permission_name, np.TYPE, np.IS_DELETE, np.SORT
  FROM a8_db.dbo.CTP_NODE_PERMISSION AS np
 ORDER BY np.CATEGORY, np.SORT;   -- NODE_POLICY 与 VALUE 为容器列，不原样输出
