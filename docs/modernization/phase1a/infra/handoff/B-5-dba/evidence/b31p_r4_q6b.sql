-- B31P 第四批（one-data 草稿 @4638ceb9 的 Q5-A、Q6-A、Q6-B，one-ops 审后调整）。只读；one_evidence_ro（ADR-0037 第 7 项）。
-- 调整：DB_NAME() 守卫（两库各跑一次，只在 a8_db 出数据）；Q6-A 的取值输出加属性级防线——某属性只要有任何取值是 2–3 个汉字（可能是人名）或全为 8 位以上数字，整属性不出值，只报个数；
--   取值本身仍须不含 8 位以上连续数字、长度 ≤ 40。结果只落本机会话临时目录；回给 one-data 的内容先人工剔除人名样式。
SET LOCK_TIMEOUT 1000;
SELECT TOP (500) np.ID AS permission_id, np.CATEGORY, np.NAME AS permission_name, np.TYPE, np.IS_DELETE, np.SORT
  FROM a8_db.dbo.CTP_NODE_PERMISSION AS np
 WHERE DB_NAME() = N'a8_db'
 ORDER BY np.CATEGORY, np.SORT;
