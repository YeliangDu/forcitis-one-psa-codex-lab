# one-platform-file 交接（文件服务 B 线）

- 时间：2026-10-21（恢复后更新）。
- 已合或在队：单元 1 #499、单元 2 #502（已合）、单元 4 #510（集成层端口，排队）、单元 3 #521（表结构，one-data 第七轮签，已报 one-merge 入队）。
- 单元 5（写入内容·检测闸）：PR #525 已开（分支 claude/one-platform-file-u5），含 #521、#510 提交，须二者之后入队；已报 one-coordinator 请批。容器类 IT 待 CI。
- 单元 5 复审已过：opus 首轮必改 8 条已改，sonnet 两轮复核问题已改；one-architecture 裁定已落地（PDF 按空用户口令判 R2–R6、7z 按头部 AES 判、压缩头内容加密交引擎；加密拒收审计写原文件名，用户 intent 2026-10-21-170547）。
- 待联调（OPS-02，已请 one-coordinator 转 one-ops）：content-compressed.7z 须报 Heuristics.Encrypted.7Zip，不报改走 commons-compress（报 one-architecture 与 one-coordinator 开卡）；perm-*.pdf 须扫得进、open-*.pdf 须报加密。
- #525 恢复后已合 main、修列名 transition_sequence_number（e77663a5）、补数据访问登记（0806e43e），本机容器 IT 已跑通。
- 单元 6：PR #527（after=525），opus 首轮必改已改（关联仅上传人或平台模块，加守护），sonnet 复核过；首次关联写保存期首行从单元 7 提前。
- 下一单元：6b（前端上传与状态端点、OpenAPI、identity/idempotency/scheduling 出边、正式区孤儿只读核对作业、「未关联已过期」作业；复审延后项：临时区已过期时重试关联先认正式键并映射为不可用（改集成层适配器先告知 one-architecture）、两处链接时效上限启动交叉校验、读取内容区分 sink 与存储异常、时效非正数改 4xx）、5a（隔离与误报）、7（保存期数据部分其余）。
- OPS-02 实测（one-ops-r56@3570f6ef）：clamd 1.4.6 判定与应用侧映射一致，不改 commons-compress；部署时 staging-root 定为 /data/forcitis-one/tmp/scan-staging（后端单元 ReadWritePaths 只含 logs、tmp），检测总超时 ≥60 s（clamd MaxScanTime 120 s）；EICAR、停 clamd 演练在单元 9 与 one-ops 约。
