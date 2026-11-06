复审结论：通过
commit：1eb7300d6e2bbe9c5c44c0ef0734e9906ffd8ec3
复审者：sonnet-首轮-r0-1448

patch-id：5a7ee24fe8d210eba9dd3f3507c5220f2936a40c

影响：organization.workforce 的 api 新增一个只读接口 PositionLookup（暂无调用方，R2 将消费）；其余为文档与词表。已证明的调用层数：0（新增接口，git grep 无引用），Spring 装配由 PositionLookupIT 实跑证明。

## 必改
无。

## 建议
1. enterprise-aggregates-draft.md 第 519 行 AGG-62 与第 523 行 AGG-67（及生成物 planned.yaml 943、991 行）仍写"不经 platform.approval（同 AGG-65 ……）"。AGG-65 现已改经 platform.approval，"同 AGG-65"成了悬空指代；两行自身的论断（B-13 内单级批准、避免成环）仍然成立，所以只是措辞陈旧，不是错误行为。卡的"不得做"禁止其他措辞改动，且 spec 第 12 行没有列这两行，因此不阻塞；建议另记一条后续措辞项（或在 R1/R2 一并处理）。
2. command-ids.yaml 的 CMD-5ae13191「关闭招聘需求」与 spec 提到的 closeout 命令拆分（审批结果处理、撤回、关闭）本卡未动，符合卡范围（卡只要求 AGG-65 的 BC 与审批措辞）；留给 R2 即可。
3. JdbcPositionQueries.isActive 用 `list().size() == 1`，用 `.optional().isPresent()` 更直接；不影响正确性。

## 已检查且无问题
- 提交头与 PR：`gh pr view 1469` 的 headRefOid 等于 1eb7300d6e2bbe9c5c44c0ef0734e9906ffd8ec3；`git cat-file -e` 通过。
- 验收 1（查询）：isActive 同时要求 "今日生效版本 ACTIVE"（CURRENT CTE：superseded_at is null、日期区间含今天）与 "positions 行 ACTIVE"。已读 JdbcPositionQueries.java：被停用、已删除、不存在、被取代的 ACTIVE 版本均返回 false。只读本模块自己的表 organization_workforce.positions / position_versions，无跨模块读取。
- 测试：`backend/mvnw -f backend/pom.xml -Dtest=PositionLookupIT test`，surefire 报告 tests=2 errors=0 failures=0。覆盖 ACTIVE、DISABLED、版本被停用而行仍 ACTIVE、DELETED（带 ACTIVE 版本）、未知 ID、被取代版本。测试断言的是行为（对外 api 的返回值）。未改动任何已有断言或验收测试。
- 接口快照：platform-org-interfaces.signatures.txt 新增两行与 PositionLookup 的实际签名一致（isActive(PositionId)）；PositionLookupService 包私有、实现 api 接口，符合模块边界。
- 验收 2（措辞）：AGG-65 行从 B-13 表移出并在新增 B-33 招聘 表中改述为经 platform.approval（依据 ADR-0085 决定 4，spec 第 12 行已核对）；§4.3.2 第 954 行、985 行、1272 行、1353 行（spec 列出的四处）及 §7 第 12 条均已加"已取代"说明并保留原文；聚合计数文字未被改动。planned.yaml 的 AGG-65 现为 BC: B-33，其余变动只是 source_line 位移（机械生成，-1 偏移与删除一行一致），符合"生成物由生成器产出"。
- 词表：terms.yaml 的 recruitment_requisition 改 bc: organization.recruitment；bcs.yaml 新增该 bc 条目（subdomain organization、kind bc，描述写明 schema 与模块在 R1 登记）。与 contexts.yaml:107 "名称归 one-glossary" 一致。
- 不得做：未发现招聘 schema 或代码，无迁移改动，无代码文件超 1000 行，无新增密钥或行级数据；测试夹具只用随机 UUID 与虚构编码。
- 注释：PositionLookup / PositionQueries 的 Javadoc 与代码行为一致。

## 破坏验证
| 防线 | 破坏方式 | 结果 |
|---|---|---|
| 版本必须 ACTIVE（status = 'ACTIVE'） | 去掉该条件 | 未实跑（未在临时副本中执行，不得改动现有 worktree）；按用例推演 PL-2 / PL-4 会变红 |
| positions 行必须 ACTIVE（exists 子句） | 去掉该子句 | 未实跑；按用例推演 PL-3（DELETED 但带 ACTIVE 版本）会变红 |
| 被取代版本不算（CURRENT 的 superseded_at is null） | 该过滤在既有查询中，未改动 | 未实跑；aSupersededActiveVersionDoesNotCount 针对它 |
以上三行为推演而非实跑，标记为未验证；因为是只读查询、用例逐项对应，不据此升级为必改。
