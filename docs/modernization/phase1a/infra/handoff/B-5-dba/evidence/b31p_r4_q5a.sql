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
