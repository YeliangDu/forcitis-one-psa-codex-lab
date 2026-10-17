-- C-5 首任管理员：按姓名查平台负责人（用户本人）在 A8 的登录名，供 find-accounts-by-external-subject 使用（用户 2026-10-16 经 one-coordinator 批准按姓名「都业梁」查）。只出匹配个数、登录名与启用状态。只在 a8_db 上下文出结果。
SELECT M.ID AS member_id, P.LOGIN_NAME AS login_name, M.IS_ENABLE AS enabled, M.STATE AS state, M.IS_DELETED AS deleted
  FROM a8_db.dbo.ORG_MEMBER M LEFT JOIN a8_db.dbo.ORG_PRINCIPAL P ON P.MEMBER_ID = M.ID
 WHERE DB_NAME() = N'a8_db' AND M.NAME = N'都业梁';
GO
