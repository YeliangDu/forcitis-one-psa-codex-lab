## 2026-10-14 · psa-repo-fragments · 命名标准借鉴 DAMA
- 目标：按用户「全部在 1a 做」，在命名标准中补 DAMA / ISO 11179 的列名类别词、约束与索引命名、列级敏感分级、1a 表保留期，更正 GUIDING_PRINCIPLES §6 过时内容，并给出 1a 改名清单（psa-coordinator 派工）。
- 输入：origin/main 190c79f（含 1a 全部迁移脚本 15 个）；NAMING_STANDARD、GUIDING_PRINCIPLES §6、backend DdlNamingTest 与 architecture/ 下其他守护清单的格式。
- 用户决定（原话见 INTENT.md）：问「1a 还有哪些设计需要现在考虑？所有表命名是否有标准？需要借鉴 dama 的标准吗」，选「全部在 1a 做 (Recommended)」。
- 产出：NAMING_STANDARD §2.4（类别词与类型表、pk_/fk_/uk_/ck_/ix_ 命名、框架表豁免）、§2.5（四级分级与处理）、守护表 D2；DdlNamingTest 新增按迁移推算最终表结构（跟踪 ADD / RENAME COLUMN、RENAME CONSTRAINT、ALTER INDEX RENAME、DROP），检查类别词与类型、约束与索引命名、敏感列登记，附注入与改名跟踪测试；architecture/ddl-naming-baseline.txt（83 项：列 26、约束 41、索引 16，每行附改名建议）；architecture/data-classification.txt（10 列）；GUIDING_PRINCIPLES §6 主数据改为全部进新系统、数据安全引用分级、1a 表保留期建议值（待用户决定）、ID 策略标待定。
- 验证：DdlNamingTest 5 项通过（注入：错类别词、错类型、未命名或错名的约束与索引、未登记的敏感列均失败；后续迁移改名后违规消失；基线删行则该项重新报错，多余的基线行报「已修好请删除」；删掉敏感登记则报错）；NamingSurfaceTest、GlossaryTest、LegacyShapeTest、ArchitectureRulesTest 通过；完整 mvnw verify 见报告。未使用子代理。
- 未完成与阻塞：保留期待用户决定；ID 策略以 1a 方案为准；web / api 出参不暴露敏感列的守护由 1a 实现；类别词表比派工单多出若干项（见报告），请用户确认。
- 下一步命令：1a 按 ddl-naming-baseline.txt 用 Flyway 追加迁移改名并删行。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-fragment-naming）
