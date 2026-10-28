# 老系统与 A8 生产数据读取登记

ADR-0037 第 7 项：生产取证不再逐次请示，每次读取登记一行（一个片段）：用 `python3 docs/modernization/phase1a/infra/new_legacy_read.py --session=<会话名> …` 新增，本表由 `build_all.py` 生成、不入仓，不要直接编辑（）。明细与个人信息不进会话记录与仓库，只出计数 / 聚合，或落 ECS 数据盘；凭据值任何情况下不输出。2026-10-15 之前的读取按当时的逐次批准补登。

执行账号：`u8c` 是老系统主数据源（RDS SQL Server 的高权限账号，经老系统应用服务器的只读探针 `LegacyReadOnlyProbe` 或 `OneSql ro` 读取）；ADR-0037 起取证改用 `one_evidence_ro`，高权限账号只用于授权变更本身。
