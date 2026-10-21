# one-platform-file 交接（文件服务 B 线）

- 时间：2026-10-21，clear 前。
- 已合或在队：单元 1 #499、单元 2 #502（已合）、单元 4 #510（集成层端口，排队）、单元 3 #521（表结构，one-data 第七轮签，已报 one-merge 入队）。
- 单元 5（写入内容·检测闸）：PR #525 已开（分支 claude/one-platform-file-u5），含 #521、#510 提交，须二者之后入队；已报 one-coordinator 请批。容器类 IT 待 CI。
- 单元 5 复审已过：opus 首轮必改 8 条已改，sonnet 两轮复核问题已改；one-architecture 裁定已落地（PDF 按空用户口令判 R2–R6、7z 按头部 AES 判、压缩头内容加密交引擎；加密拒收审计写原文件名，用户 intent 2026-10-21-170547）。
- 待联调（OPS-02，已请 one-coordinator 转 one-ops）：content-compressed.7z 须报 Heuristics.Encrypted.7Zip，不报改走 commons-compress（报 one-architecture 与 one-coordinator 开卡）；perm-*.pdf 须扫得进、open-*.pdf 须报加密。
- Docker 未运行（one-coordinator 处理）：FileServiceSchemaIT、JdbcStoredFileRepositoryIT 未在本机跑过。
- 下一单元：6（关联、授权与下载；promote 已存在且哈希一致视为幂等、对象键建行即写正式键、正式区孤儿只读核对作业 N 天配置只告警）、5a（隔离与误报，审计主体用 STORED_FILE）、7（保存期数据部分）。
