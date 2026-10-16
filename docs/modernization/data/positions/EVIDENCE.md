# 岗位与职级只读取证（聚合）

- 取证：one-ops，2026-10-16，账号 one_evidence_ro，ADR-0037，只出聚合、小格（< 3 人）写「< 3」；原始结果文件在 one-ops 本机（0600，随后删除），本文件是聚合的抄录，不含任何个人信息；SQL 在 `phase1a/infra/handoff/B-5-dba/evidence/post_level_*.sql`、`post_list.sql`。
- 在职 = 同步范围内（部门 PATH 在根 00000001 下、内部或空、工号非空）、未删除、启用、STATE = 1，共 304 人。

## 第 1 步：元数据——员工的职级值在哪

- **A8 岗位表** `ORG_POST`（208 行）：ID、NAME、CODE、IS_ENABLE、TYPE、SORT_ID、IS_DELETED、STATUS、EXTERNAL_TYPE 等；`zfORG_POST`（139 行）是备份类副本。人与岗位：`ORG_MEMBER.ORG_POST_ID`；管理职务：`ORG_MEMBER.ORG_LEVEL_ID` → `ORG_LEVEL`（47 行，NAME、CODE、LEVEL_ID、GROUP_LEVEL_ID）。
- **岗位关系** `ORG_RELATIONSHIP.TYPE`：`Member_Post` 1100 行（OBJECTIVE0_ID = 部门、OBJECTIVE1_ID = 岗位）、`Department_Post` 785、`Member_Role` 1502 等；岗位与角色的绑定没有独立表（`ORG_ROLE` 2653 行是角色本身）。
- **员工的职级值**：人事表 `formmain_0307`（表单「员工档案表」）里 **`field0294`「能力等级」**（nvarchar(100)，文本）：304 位在职人员全部非空，20 个不同的值，其中 **196 人的值形如 T+数字**，与岗位名称里的职级代码一致。另有 `field0641`「薪等」（枚举，9 个值）、`field0233`「职称」（枚举，4 个值）、`field0242`「职业通道」（枚举，4 个值）、`field0413`「职务级别」（取自 ORG_LEVEL，全部相同）、`field0241`「岗位」（与 ORG_MEMBER.ORG_POST_ID 完全一致，304 / 304）。表单定义里还有「现能力等级」「现薪等」「现岗位」等字段，但物理表 `formmain_0307` 里没有这几列。表单定义里没有叫「职级」的字段。
- **老系统**：`system_post`（id、code、name、status、sort、deleted…）、`system_user_post`（user_id、post_id、deleted…）；含 post、level、grade、job 字样的其他列有 `psa_time_sheet.post_id`、`mdm_bill_rate_detail.job_level_code` / `salary_grade_no`、`psa_employee_transfer_record.job_level_code` 等。

## 第 2 步：计数

**① A8 岗位**
- `ORG_POST` 共 208 行；STATUS 全为 1；IS_DELETED = 0 的 137 行，= 1 的 71 行。
- 有在职人员的岗位 57 个（全是未删除的）；无人岗位：全部行 151 个，其中未删除的 80 个。
- 有人岗位按在职人数分桶的岗位个数：1 人 27；2–4 人 18；5–9 人 7；10–19 人 1；≥ 20 人 4。
- 在职人员无岗位：**0**。
- 岗位名称以「T + 数字」开头：61 个；其他 147 个。不同的职级代码 7 个（T1–T7）；各代码下在职人数：T1 40、T2 79、T3 70、T4 4、T5 < 3、T6 < 3、T7 < 3。去掉职级代码后不同的「职能」名称 58 个。

**② 老系统岗位**
- `system_post` 共 211 行：status 0 且 deleted 0 的 135 行；status 0 且 deleted 1 的 74 行；status 1 且 deleted 0 的 2 行（未删除共 137 行）。
- 有用户的（未删除）岗位 18 个；有岗位的用户 32 人；有 ≥ 2 个岗位的用户 < 3 人。

**③ 对照（A8 岗位 208 行 × 老系统未删除岗位 137 行）**
- 编码精确相同：A8 的 208 个全部在老系统有相同编码；老系统 137 个全部在 A8 有相同编码；两边各自对不上的编码个数均为 0。
- 名称精确相同：A8 有 138 个；A8 对不上的 70 个；老系统 137 个全部在 A8 找得到同名。

**④ 兼任（`Member_Post`，部门 = OBJECTIVE0_ID、岗位 = OBJECTIVE1_ID）**
- 在职人员 304 人，每人都有一行与其主部门、主岗位相同的关系；另有 **6 人各有 1 条不同的行**（兼任或副岗），无人有 2 条及以上；这 6 条里和主岗位同部门的 < 3 条。Member_Post 全表 1100 行（含已删除、不在同步范围的人）。

**⑤ 管理职务（ORG_LEVEL）**
- 在职人员 ORG_LEVEL 非空 **304** 人，全部是同一个管理职务「普通员工」，没有区分度；所以「岗位名称前缀与管理职务口径不一致」无法机械判断，只给分布：普通员工 304。

## 补充聚合（one-ops，2026-10-16，云助手任务 t-hz0707nkpd3591c；SQL `post_level_enum_counts.sql`；在职 304 人，小格 < 3）

**① 员工档案（枚举取显示名）**
- 职业通道：技术 242、职能 33、管理 17、营销 12。
- 职称：无 168、助理工程师 68、工程师 58、副高级工程师 10。
- 薪等：1=50、2=48、3=45、4=37、5=44、6=40、7=15、8=16、9=9。
- 能力等级（20 值）：T1 40、T2 79、T3 70、T4 4、T5 / T6 / T7 各 < 3；M3 10、M4 31、M5 15、M6 10、M7 4、M9 < 3；A1 9、A2 11、A3 3；S1 4、S2 < 3、S3 5、S4 < 3。

**② 老系统（不同取值数 / 各值行数；行数不是人数）**
- `mdm_bill_rate_detail.job_level_code` 21 个值：A1 395、A2 505、A3 450、M3 450、M4 505、M5 505、M6 478、M7 340、M8 340、M9 248、S1 291、S2 291、S3 320、S4 375、T1 340、T2 505、T3 505、T4 432、T5 478、T6 340、T7 340。
- `mdm_bill_rate_detail.salary_grade_no` 10 个值，是长整型编号、不是 1–9：0 为 210 行，其余 9 个编号的行数 396–1155。
- `psa_employee_transfer_record.job_level_code` 22 个值：A1 204、A2 123、A3 43、A4 < 3、M3 111、M4 391、M5 149、M6 110、M7 13、M8 11、M9 6、S1 75、S2 48、S3 76、S4 30、T1 891、T2 1145、T3 783、T4 16、T5 < 3、T6 13、T7 7。

**③ `psa_time_sheet` 引用的岗位**：共 111 个 post_id，全部能经 `system_post` 编码在 A8 岗位表找到（无空岗位、无找不到的）。岗位个数 / 工时行数全部 / 未删除：A8 已删除岗位 18 / 28853 / 26930；A8 未删除但无在职人员 55 / 197773 / 176014；A8 未删除且有在职人员 38 / 304007 / 270535。即全量历史迁入时岗位来源对照要覆盖 73 个无人岗位（其中 18 个 A8 已删除），约占 43% 的工时行。
