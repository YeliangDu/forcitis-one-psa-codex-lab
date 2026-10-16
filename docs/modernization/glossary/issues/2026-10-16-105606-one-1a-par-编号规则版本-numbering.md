## 用语问题

- 词或现象：编号规则版本（numbering_rule_version）既指版本号值对象 RuleVersion（从 1 起的序号），又指版本行 UUID（列 numbering_rule_version_id、类型 NumberingRuleVersionId，供使用方引用）；terms.yaml 词条定义只写了前者，新列注释与类名用的是后者
- 出处：backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql 列 numbering_rule_version_id；phase1a/cards/TS-UNIQ.md §11；backend/glossary/terms.yaml 词条 numbering_rule_version
- 使用范围：domain
- bc：platform.numbering
- 类型：conflict
- 登记会话：one-1a-par
- 状态：待处理
