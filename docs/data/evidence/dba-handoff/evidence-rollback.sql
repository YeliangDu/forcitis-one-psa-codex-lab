-- ADR-0037 · 取证账号回滚（以高权限账号执行）
-- 之后在新 ECS 删除 /etc/forcitis-one/evidence.env
USE [a8_db];
DROP USER IF EXISTS [one_evidence_ro];
GO
USE [ruoyi-vue-pro];
DROP USER IF EXISTS [one_evidence_ro];
GO
USE [u8cloud];
DROP USER IF EXISTS [one_evidence_ro];
GO
USE [u8cloud_QZ];
DROP USER IF EXISTS [one_evidence_ro];
GO
DROP LOGIN [one_evidence_ro];
GO
