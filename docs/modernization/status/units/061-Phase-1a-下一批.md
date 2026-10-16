排序: 061
工作单元: Phase 1a 下一批（功能补做 F-1..F-12、结构性重构 R-1..R-4、整合复审后的缺陷卡）
状态: 进行中：R-1b P2（#209）、ARCH-D（#211）已合并；FLAKY-SESS #228 入队；R-1b 按 NEXT_BATCH_PLAN 7.11「甲′」：R-1b-0a（验收就绪、等 G01–G03）→ P3b（实现第二轮）→ 组织同步拆分 b → a → R-1b-0b → DA-3 → P4 → P5
负责会话: one-1a-next（至 R-1a 合并后交接）→ one-1a-next-2（R-1b 起至 P2 开工前，2026-10-15）→ one-1a-next-3（结构重构主线 P2 起）；one-1a-par 并行（部署守护解耦、只读核实命令、FLW-1、F-1 验收先写、K-3） → one-1a-next-4（P3b 第二轮起，2026-10-15 交接）
入口 / 阻塞: blockers/090（F-1）；blockers/101 已于 2026-10-16 随 C-14 完成而删除；dev 已手工装至 rc.4（2026-10-16），首批 process_administrator / super_admin 授权待 ADR-0030 单人运维模式新 rc；ADR-0029 以岗位体系重梳完成为批准前置
下一条命令: one-1a-next-4 读 phase1a/HANDOFF_one-1a-next-3.md 第 3 节，从 P3b 第二轮收尾与 R-1b-0a 实现接续
