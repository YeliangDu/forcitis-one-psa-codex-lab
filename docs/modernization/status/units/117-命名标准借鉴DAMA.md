排序: 117
工作单元: 命名标准借鉴 DAMA（列名类别词、约束与索引命名、敏感分级、保留期、§6 更正）
状态: 完成（2026-10-14）：NAMING_STANDARD §2.4、§2.5；DdlNamingTest 守护与注入测试；基线 83 项（列 26、约束 41、索引 16）附改名建议；敏感分级 10 列；GUIDING_PRINCIPLES §6 更正与保留期建议值
负责会话: — （psa-repo-fragments 已收尾）
入口 / 阻塞: 保留期待用户决定；ID 策略待 1a 方案确认
下一条命令: 1a 按 backend/architecture/ddl-naming-baseline.txt 用 Flyway 追加迁移改名，改完删行；1a 实现 web / api 出参不暴露敏感列的守护
