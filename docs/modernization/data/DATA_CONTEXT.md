# one-data 速查

只记现行状态，一行一项（SESSION_PLAYBOOK §2.1 第 13 条）。2026-10-16 批次边界更新（上下文 76%，交接 /clear 续）。

| 项 | 值 |
|---|---|
| 状态 | 在岗；独立会话（已从调度解绑）；工作规则：签发记录要先于被签发 PR 合入，文档类产出不单独入队、报 one-coordinator 打包，合并过的分支不再推、开新分支新 PR，每个会话同时打开 PR ≤ 3 |
| 门禁 | `data/DATA_DESIGN_GATE.md` 已生效（用户「生效，阈值按 10%」）；新涉数据的卡先签发、表结构 PR 先合并（只含约束、注释、登记且无业务代码的修复卡：签发 → 验收（红）→ DDL（绿）同一 PR 即可，TA-A 裁定 1）；§4a 分级参照表；附录 C 自测表 |
| 签发记录 | `data/reviews/<卡号>.md`，已签：R-1b P5、TS-UNIQ、TB-01/02、TB-03、PR-1、PR-3、PR-5（identity）、PR-6、PR-7（按 AP-14 更正后重签）、PR-8、PR-8b、TA-A（A1/A2/A3）、SINGLE-OP；PR #400 的 SINGLE-OP 记录待合（先于 #398） |
| 已建表审查 | `data/BASELINE_TABLE_REVIEW.md`（统一分级：阻断 0、高 20、中 91、低 70）、`review-parts/`、`repair-cards/`；保留期与匿名化 `data/RETENTION_PROPOSAL.md`（用户已定按建议）；一次投产更正见 BASELINE_TABLE_REVIEW §9 |
| 错题本 | `quality/defect-bank/one-data.md`（DATA-001…017）；工作守则：「缺失 / 不存在」类结论自己 grep 原文复核后才对外发 |
| 岗位体系重梳（Q-02） | ① 取证完成（`data/positions/EVIDENCE.md`，含补充聚合）；③ 映射候选稿 `data/positions/JOB_MAPPING.md`（57 个在用岗位，候选待人工核）已合；**待**：J-1/J-2 用户答复后出职级对照；职业通道 4 类（技术 / 职能 / 管理 / 营销）与方案 B 三通道对不上、历史工时引用 73 个无人岗位（约 43% 工时行）已告 one-architecture |
| 一次投产 | 生产不再同步 A8，全量历史一次性迁入；sync_* 取消方向、已建表等过渡 ADR 退役，不删不新增；**迁入映射与对账方案（U-4 全量历史明细、U-10 投产前两个已结期间重算比对）由 one-data 负责，等施工方案定稿再向 one-coordinator 要排期** |
| 未完成 | ① 填报义务实现的数据设计：等 one-rules 规则卡（预审前置 P1–P11 已发；请假 = OBJ-68、标准工时 = AGG-108，one-architecture 已答）；② 触发器命名条款提议 `data/TRIGGER_NAMING_PROPOSAL.md`（已写，待 one-coordinator 排改全库小卡与 NAMING_STANDARD 条款、one-glossary 查重）；③ DG-0 注册表字段扩展（business_key、数据类别、DECLARED / OBSERVED、查询路径、分区键、projections、failed_policy 等）与基线 DDL 扫描脚本（#322）由 one-quality；④ E3：迁入单的 `round_number` / `submitted_date` 口径回看（TA-A 决定点 6）；⑤ one-1a-next-5 后续：运维证明员工 ID 须存在小卡（M-4）、DATA-REPAIR-workforce 其余条目 |
| 接 one-architecture | OBJ-105–109 等新增对象，为 METHOD 第 11 步准备（未开始，等一次投产过渡 ADR 后再排） |
