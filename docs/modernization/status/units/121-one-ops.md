排序: 121
工作单元: 运维线（长期）：Phase 1a 部署前运维剩余项、告警落地、只读导出、归档 bucket 方案
状态: 进行中（2026-10-15）：第二轮完成 C-3（dev 库、角色、verify-full）、C-4 余项（Temurin 25、TLS / static-pinned、SLS 与指标采集）、B-5 老系统、C-6 三类导出（角色、日历、回放样本 500）、证书检查上线、备份窗口调整、SSL Labs A+
负责会话: one-ops（2026-10-15 起）
入口 / 阻塞: 不部署（blockers/080）；回放运行待构建产物；one_admin 轮换待 R-4；RPO 待用户查控制台；PG 证书重签与 SQL Server 开 SSL 列生产前；告警规则待 A-5 群机器人
下一条命令: 见 phase1a/infra/DEV_DEPLOY_REMAINING.md
