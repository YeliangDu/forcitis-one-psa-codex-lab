# one-data 速查

只记现行状态，一行一项（SESSION_PLAYBOOK §2.1 第 13 条）。

| 项 | 值 |
|---|---|
| 状态 | 已认领（2026-10-16） |
| 门禁 | `data/DATA_DESIGN_GATE.md` 已生效（用户 2026-10-16「生效，阈值按 10%」）；新涉数据卡先签发，表结构 PR 先合并 |
| 签发记录 | `data/reviews/<卡号>.md`；已签发 R-1b P5 |
| 在途补审 | TS-UNIQ（one-1a-par 写卡中，等卡送审）；填报义务（等 one-rules 规则卡与 one-architecture 的请假 / 标准工时对象）；P5 已签发 |
| 已建表审查 | `data/BASELINE_TABLE_REVIEW.md` + `data/review-parts/` + `data/repair-cards/`（四张修复卡草稿），待 one-coordinator 排期；待用户决定事项登记收件箱 |
| 错题本 | `quality/defect-bank/one-data.md` |
| 接 one-architecture | OBJ-105–109 等新增对象，为 METHOD 第 11 步准备（未开始） |
| 岗位体系重梳（Q-02，2026-10-16） | one-data 牵头 ① 岗位与职级取证（one-ops 取，只出计数）与 ③ 旧岗位 → 新职位映射表；② 新职位目录由 one-architecture 出草稿 `proposals/JOB_CATALOG_DRAFT.md`，出来后做 ③ |
| 一次投产（intent 2026-10-16-133348） | 生产不再同步 A8，全量历史一次性迁入；sync_* 取消方向、已建表等过渡 ADR 退役、不删不新增；one-data 负责迁入映射与对账（U-4 全量历史明细、U-10 投产前两个已结期间重算比对），方案待施工方案定稿 |

