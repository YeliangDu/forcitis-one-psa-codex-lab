-- B-5 全库只读 · 以 one_a8_views_ro 登录：基表、元数据与原有视图都能读（只出计数）
-- R1 人员基表
SELECT COUNT(*) AS n FROM dbo.ORG_MEMBER;
GO
-- R2 单元基表
SELECT COUNT(*) AS n FROM dbo.ORG_UNIT;
GO
-- R3 原有视图
SELECT COUNT(*) AS n FROM forcitis_one.v_org_member;
GO
-- R4 元数据
SELECT COUNT(*) AS n FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo');
GO
