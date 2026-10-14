-- 表注释写明粒度（数据架构守护 D3）。
COMMENT ON TABLE platform_numbering.numbering_rules IS '编号规则：每种单据一条，是版本的聚合根；模板内容在 numbering_rule_versions，由组织数据管理员维护、业务管理员确认（凭证号另需财务确认）。粒度：每种单据一行';
COMMENT ON TABLE platform_numbering.numbering_counters IS '编号计数器：按单据类型 × 法人 × 周期计数，与业务写入同事务递增（不跳号）。粒度：每个单据类型、法人、周期一行';
COMMENT ON TABLE platform_numbering.legacy_numbers IS '原编号：新系统单据与其迁移前编号的对照，只读检索；各单据在其切换阶段导入。粒度：每个单据类型的每个原编号一行';
COMMENT ON TABLE platform_numbering.numbering_rule_versions IS '编号规则版本：模板按版本保存，保存后内容不可变（触发器拒绝修改内容与删除），只补确认；每种单据至多一个待确认版本，已确认的最高版本发号。粒度：每种单据的每个模板版本一行';
