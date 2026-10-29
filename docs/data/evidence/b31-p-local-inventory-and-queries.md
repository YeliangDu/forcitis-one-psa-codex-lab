# B31P 取证准备：本地证据盘点与只读查询包（草稿）

- 任务：ARC-1017-DAT-01（one-data；输入 `claude/one-architecture-adr-governance@84ba2706` 的 `B31P_EVIDENCE_SPEC.md`）
- 产出方式：Codex（gpt-6.1-sol，xhigh，read-only）起草，one-data 抽查：本地证据的路径与行号抽查 8 处，与原文一致（`docs/legacy/runtime-usage/a8-templates.tsv` 的用印申请、需求工单、IT 资产采购申请、IT 资产领用申请，`docs/legacy/runtime-usage/a8-forms.tsv`，`OA_PROCESS_INVENTORY.md:173`）。**其余行号未逐条核**。
- 状态：**草稿，SQL 全部未执行、未编译**。含 `<…>` 的表与列是假设，必须先跑 Q00 元数据探查确认，禁止直接执行。生产取证只经 one-ops 的取证通道，每次登记一行（ADR-0037）；结果只留配置与计数，明细与个人信息不进会话和仓库。
- 建议执行顺序：先 Q00、Q01、Q03、Q10（只读元数据与索引，不读任何值），再据此把 Q02、Q04 以后的查询实例化；Q05–Q09、Q11、Q12 的表与列在元数据确认前不得执行。

---

=====PART1=====

本次只读取了指定规格和本仓库，未联网、未连接数据库、未修改文件。实际检出 HEAD 为 `760fef9d44466584bc837a0277a6497195111d71`，与规格标注的基线 `2ed40a2f…` 不同。

**29 项均未达到整项“已核实”。** 本地主要有模板／表单元数据快照、历史聚合、代码摘录和设计材料，缺少覆盖这些参数的完整原始配置及正式制度依据。

以下路径均相对于 `docs/`，行号按本次检出。判断针对完整填写项；“部分证据”不表示缺失参数可以预填。

共同边界：

- `docs/legacy/runtime-usage/a8-templates.tsv:1–2`：2026-10-14 模板快照，包含模板标识、实际名称、表单主表、删除标志、近 365 天计数等；**没有模板版本、节点、授权规则**。
- `docs/legacy/runtime-usage/a8-forms.tsv:1–2`：表单名称、类型、主表及行数；**没有字段标签、字段规则和受控值定义**。主表行数也不能替代流程实例数。
- `OA_PROCESS_INVENTORY.md:19–22`：上述历史取证使用 `READ UNCOMMITTED`；本次后续取证必须按 ADR-0037 重取，不能沿用其隔离方式。
- `OA_PROCESS_INVENTORY.md:46`、`docs/business/finance-adr-points.md:38` 的“留在 OA”属于历史规划；`docs/architecture/proposals/closeout/prc-allocations.md:8–13` 后来登记了迁入 B-31 的目标。两者都不能证明生产参数配置。
- `docs/business/processes/general-approval-flow.md:4–5` 主要盘点老系统 Flowable；不能直接当作 A8 用印、IT、资产及活动模板的节点配置。

1. **B31P-01 用印责任制度版本与生效信息——取证待补。**  
   `docs/legacy/runtime-usage/a8-templates.tsv:7`、`docs/legacy/runtime-usage/a8-forms.tsv:177`：实际存在「用印申请」，主表 `formmain_0642`。`OA_PROCESS_INVENTORY.md:173` 记录其使用量及报价同步关系。未见责任制度正式名称、版本、批准引用、有效期和维护授权。

2. **B31P-02 用印用途、法人、归属对象与批准责任——部分证据。**  
   `docs/legacy/runtime-usage/a8-forms.tsv:92`：存在「用印申请档案」`formmain_0643`。`docs/business/rules/raw/presales-validation-lifecycle.md:82–94`：老报价用印登记检查文件名、用印公司、印章类型、原件／复印件及份数。后者是报价代码规则摘录，未证明 A8 用途字典、用途×法人×归属对象责任映射和批准角色。

3. **B31P-03 用印授权层级与适用边界——部分证据。**  
   `docs/legacy/runtime-usage/a8-templates.tsv:7`、`OA_PROCESS_INVENTORY.md:173`：可定位用印模板。尚无该模板的各级节点、串并行、越级／代办／缺位分支及正式授权表；不能从通用审批能力推定其配置。

4. **B31P-04 用印加审制度版本与生效信息——取证待补。**  
   `docs/legacy/runtime-usage/a8-templates.tsv:7` 提供流程入口；未找到加审制度版本、条款、批准及生效证据。

5. **B31P-05 需加审的用途、条件与层级——部分证据。**  
   `docs/legacy/runtime-usage/prod-bpm2.tsv:16`：老系统 `OPP_QUOTATION` 存在读取 `needSeal` 的分支表达式。`docs/business/rules/raw/presales-validation-lifecycle.md:44` 也记录该变量。它们仅证明报价审批中的用印标志分路，不能证明 A8 用印加审用途、授权层级或正式依据。

6. **B31P-06 加审条件与来源审批直通的对应——部分证据。**  
   `docs/business/rules/raw/presales-validation-lifecycle.md:93`：报价登记要求份数至少为 1；`map/packets/fst_domain_presales.txt:98–99`：报价聚合持有用印登记集合。未见来源审批状态映射、来源实例核准份数、累计已办理口径和直通分支。最少份数校验不等于来源份数上限。

7. **B31P-07 IT 类别制度版本与生效信息——取证待补。**  
   `docs/legacy/runtime-usage/a8-templates.tsv:24`：实际模板名「需求工单」，表单名「IT需求工单」，主表 `formmain_0875`。未见类别制度及正式批准、生效信息。

8. **B31P-08 受控类别、人审条件及批准责任——部分证据。**  
   `docs/legacy/runtime-usage/a8-forms.tsv:22`、`docs/legacy/runtime-usage/a8-templates.tsv:24`：可定位 IT 工单。尚无类别／子类字典、受控标志、人审触发条件、权限需求批准与实际授权节点的配置。

9. **B31P-09 类别表提供方与维护授权——取证待补。**  
   `docs/legacy/runtime-usage/a8-forms.tsv:207`：存在「IT需求工单档案」`formmain_0879`，只证明档案定义存在。未见类别提供、维护、批准、发布角色及字段级授权。

10. **B31P-10 默认受理路由与采购交接——部分证据。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:24`：需求工单；`:63`：另有「IT资产采购申请」`formmain_1065`。两者并存不能证明交接关系，仍缺类别→受理角色、采购交接条件、字段映射及接收回执配置。

11. **B31P-11 IT 金额制度版本与生效信息——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:63`、`docs/legacy/runtime-usage/a8-forms.tsv:231`：可定位 IT 采购申请。没有 IT 金额制度、授权表版本、批准和有效范围证据。

12. **B31P-12 IT 单据金额的承载范围——部分证据。**  
    `docs/legacy/runtime-usage/a8-forms.tsv:22`、`:135`、`:194`、`:231`：分别有 IT 工单、IT 资产采购档案、标为停用的 IT 资产采购付款申请、IT 资产采购申请。尚无金额字段及其可填／必填／展示规则，不能断言金额由 IT 工单承载，也不能据表名认定交接成立。

13. **B31P-13 IT 金额两档阈值与币种——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:24`、`:63` 仅提供定位入口。未找到两档阈值、币种、含税口径、比较运算和区间边界；不得补零或推定不适用。

14. **B31P-14 领用配置制度版本与生效信息——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:144`：实际模板「IT资产领用申请」，表单「IT资产领用单」，主表 `formmain_1055`。未见岗位设备标准制度、批准版本及生效信息。

15. **B31P-15 岗位与资产类别标准——取证待补。**  
    `docs/legacy/runtime-usage/a8-forms.tsv:132`、`:192`、`:195`：存在 IT 信息资产汇总表、领用单和资产状态列表。`docs/business/processes/org-and-personnel-sync.md:28` 提及 `ORG_POST` 岗位同步。均未提供岗位×资产类别标准、数量单位、多岗位选择规则或唯一使用人校验。

16. **B31P-16 超配规则、清单提供方与维护责任——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:144` 只有领用模板线索。未找到“超配”对应的原始条件、允许／拒绝结果、重叠处理及清单维护授权。

17. **B31P-17 活动制度版本与生效信息——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:47`、`:112`、`:127`：实际有「周年贺卡」「感恩节活动」「创新项目发布申请」。`docs/architecture/proposals/closeout/prc-allocations.md:13` 是新系统流程归属，不能替代现行活动制度。

18. **B31P-18 活动类型、适用员工与清单维护——部分证据。**  
    `docs/legacy/runtime-usage/a8-forms.tsv:116`、`:198–199`：上述活动各有表单，主表分别为 `formmain_1377`、`formmain_1239`、`formmain_0856`。这些模板名称不能直接当作受控活动类型字典；适用员工条件及清单维护角色未见。

19. **B31P-19 活动预算的可执行条件——取证待补。**  
    `docs/legacy/runtime-usage/a8-forms.tsv:110`、`:199`：存在感恩节活动及其档案。未见预算来源、期间、单次／人均／累计口径、币种、校验条件及费用交接规则。

20. **B31P-20 活动组织安排的可执行条件——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:112`、`:127` 提供活动入口。未见次数、人数上限、组织职责、场地类别或允许／拒绝条件；不能把历史活动存在当作安排规则存在。

21. **B31P-21 周年规则版本、生效与维护责任——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:47`、`docs/legacy/runtime-usage/a8-forms.tsv:116`：可定位周年贺卡。未见周年制度版本、批准引用、维护角色及可维护字段范围。

22. **B31P-22 周年贺卡适用员工与人员事实条件——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:47` 只证明模板存在。`docs/business/processes/org-and-personnel-sync.md:4` 列出一般人员来源，不能证明周年任务采用相同人员范围；周年纳入／排除条件、判断时点和自动生成入口均待补。

23. **B31P-23 入职日期来源与采用口径——部分证据。**  
    `docs/ops/legacy-read/2026-10-15-000015-存量迁移.md:3–4`：历史取证登记将 `formmain_0307.field0229` 标为入职字段，用于身份生命周期研究。它没有证明周年功能使用此字段，也未证明首次／本次任职选择、类型、时区和异常处理。

24. **B31P-24 周年日期与闰日计算——取证待补。**  
    `docs/legacy/runtime-usage/a8-templates.tsv:47`、`docs/legacy/runtime-usage/a8-forms.tsv:116` 仅有周年模板／表单。未找到周年公式、首周年年数、闰日和跨年规则；不能从历史日期反推。

25. **B31P-25 重新入职的周年计算口径——取证待补。**  
    `docs/business/processes/org-and-personnel-sync.md:113` 提及复职恢复，属于身份同步设计，并非周年口径。没有重新入职后的基准日期、历史任职累计、离职间隔和去重关联配置。

26. **B31P-26 周年漏跑后的补跑与补发范围——取证待补。**  
    `docs/legacy/runtime-usage/prod-usage.tsv:1195–1196`、`:1415–1425`：只证明一般作业／Quartz 表有历史计数。`docs/business/processes/org-and-personnel-sync.md:15–18` 记录组织同步作业且注明 cron 不在仓库；不能据此认定存在周年作业。补跑窗口、首次覆盖起点、去重键、补发授权均无证据。

27. **B31P-27 报价用印制度版本与正式条款——取证待补。**  
    `docs/business/rules/raw/presales-validation-lifecycle.md:82–94` 仅描述提交报价时的登记完整性校验。未见投标／非投标发出前必须实际用印的正式条款、批准版本和适用范围。

28. **B31P-28 用印登记样表与实际凭据要求——部分证据。**  
    `docs/business/rules/raw/presales-validation-lifecycle.md:86–94` 给出报价用印登记所需字段；`map/packets/fst_domain_presales.txt:98–99` 有登记集合；`docs/legacy/runtime-usage/a8-forms.tsv:92` 有用印档案。缺空白样表版本、实际办理状态／时间、凭据类型、有效性及关闭后查询规则。**申请完整不能证明已实际用印。**

29. **B31P-29 报价版本、法人、文件与凭据的核验对应——部分证据。**  
    `docs/architecture/proposals/presales-objects-evidence.md:65` 记录报价与用印子表，`:72–73` 记载当时未见报价版本链及相关缺口。`map/packets/fst_domain_presales.txt:83` 的 `version` 明确是乐观锁版本，不能当作报价业务版本。仍缺报价版本→法人→拟发文件版本→实际用印→凭据映射，以及投标／非投标发出校验点。

另有可复用的取证方法线索：

- `docs/ops/legacy-read/2026-10-15-000022-存量迁移.md:3–5`：曾从 `CAP_FORM_DEFINITION`、`CTP_ENUM`、`CTP_ENUM_ITEM` 读取字段与枚举配置；没有证明本次五块配置已经取得。
- `docs/ops/legacy-read/2026-10-15-000021-存量迁移.md:3–5`：售前历史聚合登记，不是用印授权或实际用印凭据的原始配置。

=====PART2=====

**本包为分阶段 SQL 草稿，全部未执行。** 已参考 `docs/ops/runbooks/sql/legacy-readonly-export.sql:1–12`、`c6_step0_counts.sql:1–32`：来源为 SQL Server，业务库为 `a8_db` 和 `[ruoyi-vue-pro]`，沿用 `dbo.` 限定、`TOP`、`COUNT_BIG`、`DATEADD` 等写法。

仅沿用其库名和方言。C-6 文件中的旧账号、人员角色持有关系导出及本机输出方式不适用于本任务。

**统一执行约定**

- 除按要求设置会话级 `SET LOCK_TIMEOUT` 外，查询正文只有 `SELECT`；不建临时表，不调用存储过程，不写入、不改隔离配置。
- 执行器设置：`READ COMMITTED`、自动提交、建议语句超时 **2 秒**、套接字超时 **5 秒**；锁等待 **1000 毫秒**。SQL Server 的语句超时由驱动设置，不能用 PostgreSQL 的 `statement_timeout` 代替。
- `@参数` 表示执行器绑定参数，不在脚本中 `DECLARE`。标识符由核实后的白名单替换，不能把任意文本拼入 SQL。
- 所有含 `<…>` 的表、列、谓词均为**“假设，待元数据探查确认”**，不是实际结构声明，**禁止直接执行**。确认列存在之后，还须确认业务含义、关联关系、版本及敏感性。
- 不返回完整 XML／JSON／BPMN、脚本、表达式容器、任务参数、附件或制度正文。配置若存于这些容器，只能在服务器经核实的路径投影允许的标量；本地没有依据提供 XPath／JSONPath。
- 表达式若含固定人员标识、账号或凭据，不原样输出；安全转换无法完成时，只计此类配置项数量，并保留缺口。
- `TOP` 只限制输出，不限制扫描成本。达到上限不能称为完整；须按核实的配置主键分页。大历史表低峰按主键分批，批次结果只在服务器合并。

**Q00：首先探查元数据**

对应 B31P-01…29；输出为表名／列名配置行，**每页 TOP 1000**。在两库分别以连接数据库上下文执行。只读名称，不读任何字段值；名称模式仅用于发现对象，不代表已确认用途。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
-- 首页 @after_table、@after_column 均绑定为空串。
SELECT TOP (1000)
       c.TABLE_NAME AS table_name,
       c.COLUMN_NAME AS column_name
FROM INFORMATION_SCHEMA.COLUMNS AS c
WHERE c.TABLE_SCHEMA = N'dbo'
  AND (
       c.TABLE_NAME LIKE N'CAP[_]%'
    OR c.TABLE_NAME LIKE N'CTP[_]%'
    OR c.TABLE_NAME LIKE N'COL[_]%'
    OR c.TABLE_NAME LIKE N'BPM[_]%'
    OR c.TABLE_NAME LIKE N'WF[_]%'
    OR c.TABLE_NAME LIKE N'ORG[_]%'
    OR c.TABLE_NAME LIKE N'DOC[_]%'
    OR c.TABLE_NAME LIKE N'ACT[_]%'
    OR c.TABLE_NAME LIKE N'bpm[_]%'
    OR c.TABLE_NAME LIKE N'qrtz[_]%'
    OR c.TABLE_NAME LIKE N'%config%'
    OR c.TABLE_NAME LIKE N'%rule%'
    OR c.TABLE_NAME LIKE N'%schedule%'
    OR c.TABLE_NAME LIKE N'%job%'
    OR c.TABLE_NAME LIKE N'%asset%'
    OR c.TABLE_NAME LIKE N'%seal%'
    OR c.TABLE_NAME LIKE N'%anniver%'
    OR c.TABLE_NAME LIKE N'system[_]role%'
    OR c.TABLE_NAME LIKE N'system[_]post%'
    OR c.TABLE_NAME LIKE N'psa[_]quotation%'
    OR c.TABLE_NAME IN (
       N'formmain_0012', N'formmain_0642', N'formmain_0643',
       N'formmain_0875', N'formmain_0879',
       N'formmain_1051', N'formmain_1055', N'formmain_1071',
       N'formmain_1065', N'formmain_1067', N'formmain_1069',
       N'formmain_0855', N'formmain_0856',
       N'formmain_1239', N'formmain_1250', N'formmain_1377',
       N'formmain_0307', N'formmain_0515',
       N'formmain_0366', N'formmain_0471'
    )
  )
  AND (
       c.TABLE_NAME > @after_table
    OR (c.TABLE_NAME = @after_table
        AND c.COLUMN_NAME > @after_column)
  )
ORDER BY c.TABLE_NAME, c.COLUMN_NAME;
```

本查询不会发现所有未知命名的外围配置。若命中不足，先扩大**元数据名称检索**，不能直接扫描业务值猜用途。

**Q01：记录来源库快照设置**

对应全部编号；输出配置行，**TOP 2**。仅数据库名称与隔离设置，无个人信息；不修改数据库。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (2)
       name AS database_name,
       is_read_committed_snapshot_on
FROM sys.databases
WHERE name IN (N'a8_db', N'ruoyi-vue-pro')
ORDER BY name;
```

**Q02：复核已知实际模板**

对应全部编号的流程定位；输出配置行，**TOP 100**。只输出模板定义，名称限制为本地已出现的非个人化名称。

`CTP_TEMPLATE.ID／SUBJECT／STATE／IS_DELETE` 见 `oa_residual_check.sql:16–18`；仍须 Q00 确认现存。状态码暂按原码记录，不能自行解释为“已批准／有效”。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (100)
       t.ID AS template_id,
       t.SUBJECT AS template_name,
       t.STATE AS state_code,
       t.IS_DELETE AS delete_flag
FROM a8_db.dbo.CTP_TEMPLATE AS t
WHERE t.SUBJECT IN (
    N'用印申请', N'需求工单', N'周年贺卡',
    N'IT资产领用申请', N'IT资产采购申请',
    N'感恩节活动', N'创新项目发布申请', N'新增报价'
)
ORDER BY t.ID;
```

未命中的规格检索词，先只返回候选模板**数量**；实际名称经服务器核实不含个人信息后，加入允许名称集合重查。不能把检索词直接写入“实际流程名称”。

**Q03：字段物理类型**

对应全部编号的字段定位；输出配置行，**每表 TOP 500**。只读取列定义，无任何人员、日期或金额值。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (500)
       TABLE_NAME AS table_name,
       COLUMN_NAME AS column_name,
       DATA_TYPE AS data_type,
       CHARACTER_MAXIMUM_LENGTH AS maximum_length,
       NUMERIC_PRECISION AS numeric_precision,
       NUMERIC_SCALE AS numeric_scale,
       IS_NULLABLE AS is_nullable
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND TABLE_NAME = @confirmed_table_name
ORDER BY ORDINAL_POSITION;
```

分别在来源库执行；表名取 Q00 确认清单。数据库可空性不等于表单业务必填条件；字段标签和业务校验由 Q05 取证。

**Q04-A：A8 近一年流程实例聚合**

对应五块及其关联采购、报价流程，即全部编号的使用量旁证；输出计数，**每批 TOP 1**。不返回实例标识、单号、标题、发起人或办理明细。

`TEMPLETE_ID／CREATE_DATE` 见 `oa_residual_check.sql:21–24`。主键列及可用索引必须探查确认。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
-- 假设，待元数据探查确认：分页主键列及其类型。
SELECT TOP (1)
       @template_id AS template_id,
       COUNT_BIG(*) AS instance_count_in_batch
FROM a8_db.dbo.COL_SUMMARY AS s
WHERE s.TEMPLETE_ID = @template_id
  AND s.CREATE_DATE >= @window_start
  AND s.CREATE_DATE < @as_of
  AND s.[<已确认主键列>] > @lower_key
  AND s.[<已确认主键列>] <= @upper_key;
```

统一窗口为 `[采集时刻往前365天, 采集时刻)`，时区及数据库时间解释写入清单。每个模板覆盖全部主键批次后在 ECS 合并。这里只能得到**模板标识层面**计数；没有历史版本关联时不能标成“按执行时配置版本统计”。

**Q04-B：老系统报价实例聚合**

对应 B31P-05、06、27–29；输出按定义版本聚合，**TOP 100**。仅流程键、定义版本和计数，无业务实例明细。已见列来源为 `c6_step0_counts.sql:13–32`；发起时间列仍待确认。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
-- 假设，待元数据探查确认：发起时间列。
SELECT TOP (100)
       p.KEY_ AS process_key,
       p.VERSION_ AS definition_version,
       COUNT_BIG(*) AS instance_count_in_batch
FROM [ruoyi-vue-pro].dbo.ACT_RE_PROCDEF AS p
JOIN [ruoyi-vue-pro].dbo.ACT_HI_PROCINST AS h
  ON h.PROC_DEF_ID_ = p.ID_
WHERE p.KEY_ = N'OPP_QUOTATION'
  AND h.[<已确认发起时间列>] >= @window_start
  AND h.[<已确认发起时间列>] < @as_of
  AND h.ID_ > @lower_key
  AND h.ID_ <= @upper_key
GROUP BY p.KEY_, p.VERSION_
ORDER BY p.VERSION_;
```

**Q05：模板版本与表单字段语义**

对应全部编号；输出配置行，**TOP 500／目标配置版本**。只取空白配置，不读取 `formmain_*` 的业务值。以下结构全部为**假设，待元数据探查确认**。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (500)
       c.[<模板标识列>] AS template_id,
       c.[<模板版本列>] AS template_version,
       c.[<表单名称列>] AS form_name,
       c.[<字段标识列>] AS field_key,
       c.[<字段标签列>] AS field_label,
       c.[<字段业务含义列>] AS field_meaning,
       c.[<字段类型列>] AS field_type,
       c.[<受控值定义引用列>] AS controlled_values_ref,
       c.[<安全必填条件列>] AS required_condition,
       c.[<安全只读条件列>] AS readonly_condition,
       c.[<安全计算公式列>] AS calculation_expression,
       c.[<来源字段映射配置列>] AS source_mapping,
       c.[<引用关系定义列>] AS reference_definition
FROM [<已确认来源库>].dbo.[<已确认字段配置来源表>] AS c
WHERE c.[<目标配置版本引用列>] = @configuration_version_id
ORDER BY c.[<已确认字段排序键>];
```

若 `CAP_FORM_DEFINITION` 是容器式存储，不能假设存在这些关系型列；应以经核实的安全标量投影替换本草稿，不能导出整个定义。

**Q06：审批／办理节点与分支配置**

对应全部编号；特别覆盖 B31P-02、03、05、06、08–10、12、13、16、18–20、22、24–29。输出配置行，**TOP 500／模板版本**。节点名称来自定义，不取历史任务标题、办理意见或实际审批人。结构为**假设，待元数据探查确认**。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (500)
       n.[<模板版本引用列>] AS template_version_ref,
       n.[<节点标识列>] AS node_key,
       n.[<安全节点名称列>] AS node_name,
       n.[<显式顺序列>] AS node_order,
       n.[<前驱节点配置列>] AS predecessor,
       n.[<后继节点配置列>] AS successor,
       n.[<串并行配置列>] AS execution_mode,
       n.[<安全进入条件列>] AS entry_condition,
       n.[<安全完成条件列>] AS completion_condition,
       n.[<职责角色引用列>] AS role_definition_ref,
       n.[<安全角色解析表达式列>] AS role_expression,
       n.[<安全缺位分支列>] AS missing_role_branch,
       n.[<安全本人提交分支列>] AS self_submission_branch,
       n.[<安全失败或退回分支列>] AS failure_branch
FROM [<已确认来源库>].dbo.[<已确认节点配置来源表>] AS n
WHERE n.[<模板版本引用列>] = @template_version_ref
ORDER BY n.[<显式顺序或已核实技术分页键>];
```

图结构没有线性顺序时，返回边及条件，不按节点 ID 大小编造业务顺序。角色表达式不得执行解析，也不得返回解析后的成员。

**Q07：正式制度、批准依据和生效配置**

对应 B31P-01、04、07、11、14、17、21、27；也为其余项补正式依据。输出配置行，**TOP 100／制度或配置范围**。仅制度定义、引用和正式职责角色，无作者／批准人信息。结构为**假设，待元数据探查确认**。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (100)
       p.[<制度正式名称列>] AS policy_name,
       p.[<制度版本列>] AS policy_version,
       p.[<条款引用列>] AS clause_reference,
       p.[<正式批准记录引用列>] AS approval_reference,
       p.[<已核实批准状态列>] AS approval_state,
       p.[<配置版本列>] AS configuration_version,
       p.[<生效时间列>] AS effective_from,
       p.[<失效时间列>] AS effective_to,
       p.[<确认时间列>] AS confirmed_at,
       p.[<编制职责角色定义引用列>] AS drafting_role_ref,
       p.[<维护职责角色定义引用列>] AS maintenance_role_ref,
       p.[<批准职责角色定义引用列>] AS approval_role_ref,
       p.[<发布职责角色定义引用列>] AS publication_role_ref,
       p.[<适用范围定义列>] AS applicability
FROM [<已确认来源库>].dbo.[<已确认制度配置来源表>] AS p
WHERE p.[<目标配置范围列>] = @configuration_scope_id
ORDER BY p.[<已确认配置分页键>];
```

批准引用不返回批准实例明细。发布状态不能替代正式批准；批准状态码和引用关系未核实，仍记取证待补。

**Q08：受控值、映射及可执行规则**

对应 B31P-02、03、05、06、08–10、12、13、15、16、18–20、22–26、28、29。输出配置行，**TOP 500／题号及配置版本**。只返回类别、标准、阈值、公式和映射，不读取交易金额、人员事实、资产使用人或凭据。结构为**假设，待元数据探查确认**。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (500)
       r.[<规则配置键列>] AS rule_key,
       r.[<配置版本列>] AS configuration_version,
       r.[<受控类别代码列>] AS category_code,
       r.[<非个人化类别名称列>] AS category_name,
       r.[<启停状态列>] AS configuration_state,
       r.[<输入字段定义引用列>] AS input_field_ref,
       r.[<比较运算配置列>] AS comparison_operator,
       r.[<受控条件值或配置阈值列>] AS configured_value,
       r.[<配置单位或币种列>] AS unit_or_currency,
       r.[<安全组合条件列>] AS condition_expression,
       r.[<允许拒绝或路由结果配置列>] AS configured_result,
       r.[<职责角色定义引用列>] AS role_definition_ref,
       r.[<安全适用范围定义列>] AS applicability,
       r.[<显式优先级列>] AS priority_order,
       r.[<规则生效时间列>] AS effective_from,
       r.[<规则失效时间列>] AS effective_to,
       r.[<正式依据引用列>] AS policy_reference
FROM [<已确认来源库>].dbo.[<已确认规则或字典配置来源表>] AS r
WHERE r.[<目标配置版本引用列>] = @configuration_version_id
ORDER BY r.[<已确认配置分页键>];
```

该通用投影须按下列内容分别实例化；实际结构是多张表时分别查询，不能虚构统一规则表：

- **02／03／05／06**：用途、法人、归属对象、印章类型、授权层级、加审条件；来源类型／版本／审批状态映射、核准份数来源及累计公式。
- **08／09／10**：服务类别与子类、受控标志、人审理由及职责；类别提供／维护／发布范围；受理路由、采购交接映射、回执状态定义。
- **12／13**：各类单据金额字段的承载／展示／引用属性；两档阈值、币种、含税口径、总额／单价、边界运算。未见承载类型时，不查询业务金额验证猜测。
- **15／16**：岗位×资产类别、标准数量与单位、规格、在用数量公式、多岗位选择、超配允许／拒绝、重叠处理和清单维护范围。
- **18／19／20**：活动类型与适用员工条件；预算来源及期间、单次／人均／累计口径；次数、人数上限、组织角色、场地类别和失败条件。
- **22／23／24／25／26**：员工类别／任职状态规则；人员事实来源及日期字段定义、首次／本次任职选择、类型与时区；周年／闰日公式、重新入职累计规则；补跑窗口、初始覆盖起点和去重键定义。**不查询任何实际入职日期或履历。**
- **28／29**：空白登记样表版本、实际办理状态及凭据类型定义；报价业务版本、法人、文件版本、实际用印与凭据的引用关系；分别提取投标／非投标前置核验配置。不读取文件名实例、实际文件或凭据内容。

物理字段、受控值与流程条件应互相核对。`CTP_ENUM／CTP_ENUM_ITEM` 是已见来源线索，但其类别归属、标签列、排序列仍须确认，不能猜列名。

**Q09：周年调度与补跑配置**

对应 B31P-21–26，重点 24、26；输出配置行，**TOP 100／已确认相关作业集合**。不返回执行参数、负载、员工事件或任务日志。结构为**假设，待元数据探查确认**。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (100)
       j.[<非个人化任务名称列>] AS job_name,
       j.[<任务配置版本列>] AS configuration_version,
       j.[<处理器标识配置列>] AS handler_key,
       j.[<启停状态列>] AS enabled_state,
       j.[<周期表达式列>] AS schedule_expression,
       j.[<时区配置列>] AS schedule_timezone,
       j.[<漏跑处理策略列>] AS misfire_policy,
       j.[<补跑开关列>] AS catchup_enabled,
       j.[<补跑窗口定义列>] AS catchup_window,
       j.[<初始覆盖起点配置列>] AS initial_coverage,
       j.[<安全业务日期确定规则列>] AS business_date_rule,
       j.[<安全去重键定义列>] AS deduplication_rule,
       j.[<人工补跑职责角色引用列>] AS catchup_role_ref
FROM [<已确认来源库>].dbo.[<已确认调度配置来源表>] AS j
WHERE j.[<已确认周年功能关联列>] = @anniversary_configuration_ref
ORDER BY j.[<已确认任务配置分页键>];
```

`runtime_usage_queries.sql:38–46` 只是注明 XXL-JOB 可能位于独立库，并未核实库名、方言或访问权限。若周年配置实际在两库之外或非 SQL Server，本包不能冒充已覆盖；保留外围取证缺口，不新建跨库连接或链接服务器。

**Q10：主键、唯一约束和去重结构**

对应 B31P-06、15、23、25、26、28、29，同时为大表分页提供依据；输出配置行，**每表 TOP 500**。只读索引与列名称，不读取记录，不返回可能含业务常量的过滤表达式。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (500)
       t.name AS table_name,
       i.name AS index_name,
       i.is_primary_key,
       i.is_unique,
       i.has_filter,
       ic.key_ordinal,
       c.name AS column_name
FROM sys.tables AS t
JOIN sys.schemas AS sc ON sc.schema_id = t.schema_id
JOIN sys.indexes AS i ON i.object_id = t.object_id
JOIN sys.index_columns AS ic
  ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns AS c
  ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE sc.name = N'dbo'
  AND t.name = @confirmed_table_name
  AND (i.is_primary_key = 1 OR i.is_unique = 1)
  AND ic.key_ordinal > 0
ORDER BY i.index_id, ic.key_ordinal;
```

有唯一索引不等于周年业务去重已经正确；还须核对覆盖对象、版本、过滤范围与生成路径。没有查到索引也不能直接断言不存在应用层去重。

**Q11：职责角色／岗位定义及维护授权**

对应全部编号所需的正式职责，重点 02、03、07–11、14–18、20、21、26、27。输出配置行，**TOP 200／配置关联角色集合**。不联接角色成员、人员或账号表。结构为**假设，待元数据探查确认**。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
SELECT TOP (200)
       r.[<角色或岗位定义键列>] AS definition_key,
       r.[<非个人化职责名称列>] AS definition_name,
       r.[<职责定义代码列>] AS definition_code,
       r.[<安全职责范围定义列>] AS responsibility_scope,
       r.[<可维护配置字段定义列>] AS maintainable_fields,
       r.[<安全角色解析表达式列>] AS resolution_expression,
       r.[<授权版本列>] AS authorization_version,
       r.[<授权依据引用列>] AS authorization_reference,
       r.[<生效时间列>] AS effective_from,
       r.[<失效时间列>] AS effective_to
FROM [<已确认来源库>].dbo.[<已确认职责角色或岗位配置表>] AS r
WHERE r.[<配置关联范围列>] = @configuration_scope_id
ORDER BY r.[<已确认配置分页键>];
```

若配置直接绑定个人而没有职责定义，仅记录“存在直接人员绑定”的配置项数，不能把该个人改称“负责人角色”。

**Q12：近一年各节点通过／退回聚合**

对应全部实际定位出的流程；输出聚合，**TOP 500／模板版本及主键批次**。只输出定义节点和计数，不输出动作 ID、实例 ID、单号、办理人、意见。以下结构与动作码全部为**假设，待元数据探查确认**。

```sql
SET LOCK_TIMEOUT 1000;
-- 执行器：语句超时 2 秒，套接字超时 5 秒。
-- 前提：已证明历史来源一行对应一个唯一办理动作，关联不产生重复。
SELECT TOP (500)
       n.[<模板版本引用列>] AS template_version_ref,
       n.[<节点定义键列>] AS node_key,
       n.[<安全节点定义名称列>] AS node_name,
       SUM(CASE
             WHEN h.[<动作代码列>] = @passed_action_code
             THEN CONVERT(bigint, 1) ELSE CONVERT(bigint, 0)
           END) AS passed_actions,
       SUM(CASE
             WHEN h.[<动作代码列>] = @returned_action_code
             THEN CONVERT(bigint, 1) ELSE CONVERT(bigint, 0)
           END) AS returned_actions,
       SUM(CASE
             WHEN h.[<动作代码列>] IN
                  (@passed_action_code, @returned_action_code)
             THEN CONVERT(bigint, 0) ELSE CONVERT(bigint, 1)
           END) AS other_or_unmapped_actions
FROM [<已确认来源库>].dbo.[<已确认唯一动作历史来源表>] AS h
JOIN [<已确认来源库>].dbo.[<已确认节点定义来源表>] AS n
  ON h.[<节点定义外键列>] = n.[<节点定义主键列>]
WHERE <已核实的目标流程及版本限定谓词>
  AND <实例发起时间在近365天窗口内的服务器端限定谓词>
  AND h.[<动作发生时间列>] < @as_of
  AND h.[<已确认历史主键列>] > @lower_key
  AND h.[<已确认历史主键列>] <= @upper_key
GROUP BY n.[<模板版本引用列>],
         n.[<节点定义键列>],
         n.[<安全节点定义名称列>]
ORDER BY n.[<模板版本引用列>], n.[<节点定义键列>];
```

必须分别核实 A8 与老系统的动作语义：

- 已办、结束、归档不等于通过；驳回、退回、撤回、终止不得混算。
- 多处理人事宜行不一定等于多个办理动作；不能直接数候选事宜表。
- 无法证明动作唯一性、历史完整性或状态映射时，不执行该聚合，明确计数缺口。
- 完成全部批次后，服务器用 Q06 的节点定义全集补齐；只有查询成功且覆盖完整，缺少动作的节点才可记零。
- 历史清理或版本关系缺失时，应标“保留历史中的计数”，不能冒称完整近一年。

**交 one-ops 的执行清单**

1. **先核验通道和账号。** 使用当前已提交的专用取证通道及 `one_evidence_ro`，执行现行只读基线校验；失败或无法判定即停止。不得使用老系统应用数据源，也不运行建账号／改权限脚本。
2. **确认服务器落盘能力。** 拟用目录 `/data/forensics/<YYYYMMDD>-one-ops-b31p/`，属主为取证执行用户、目录 `0700`、文件 `0600`，最长保留 7 天。  
   当前 `tools/ops/dba/b5_evidence_scan.py:49–53` 会将结果带回并写到本机；`OneSql.java:103` 固定语句超时 300 秒。**不能直接用它们声称满足本包要求，也不能内存改写或包装绕过。** 若没有已提交且满足服务器落盘、限时和输出过滤要求的执行器，先报告通道缺口。
3. **Q00 → Q01 → Q03／Q10。** 分别落 `metadata-a8.tsv`、`metadata-legacy.tsv`、`source-settings.tsv`、`physical-columns/`、`indexes/`。确认实际表列、类型、主键与可用范围。
4. **Q02 → Q05 → Q06／Q11。** 落 `templates.tsv`、`fields/`、`nodes/`、`roles/`。建立模板—版本—表单—字段—节点的受控关联清单，剔除个人化配置和凭据。
5. **Q07 → Q08 → Q09。** 落 `policies/`、`rules/B31P-xx/`、`schedules/`。逐题记录实际来源和缺失项；制度批准证据与模板发布状态分开。
6. **最后执行 Q04、Q12。** 低峰、按核实主键范围分批。中间文件落 `aggregate-parts/`，只含聚合；服务器合并到 `aggregates/`，不得下载实例行或主键游标。
7. **服务器保存 manifest。** 记录查询文本及摘要、绑定参数口径、来源对象、采集时刻与时区、配置版本、批次覆盖、输出行数、超时／截断／权限错误、文件 SHA256。日志只留错误号和 SQLState，不记录可能含字段值的异常消息。
8. **逐次登记并到期删除。** 会话只留“已核实／部分证据／待补”的结论、覆盖边界及受控证据定位；结果文件、配置行和生产聚合文件均不带回本机、不入仓。删除后补登记实际删除时刻。未命中、无权限、超时和截断分别记录，不能统一写“没有配置”。

**`new_legacy_read.py` 六字段草稿**

字段名已核对 `phase1a/infra/new_legacy_read.py:4–10`。由 one-ops 实际执行后填写，`--session=one-ops`；每字段一行，不含 `|`。本次准备未执行，不新增登记。

1. **执行账号**：`one_evidence_ro（以执行前基线校验确认结果为准）`
2. **库与表**：`a8_db 与 ruoyi-vue-pro：填写实际成功读取的元数据、模板、字段、字典、角色、节点、规则、调度及聚合来源表；不得把候选对象当已读取对象`
3. **用途**：`B31P-01…29 参数预填取证，核对五块现行配置、正式依据、近365天实例及节点动作聚合，仅作现状对照`
4. **输出形式与去向**：`元数据、经筛选的配置行及聚合；仅落取证通道 ECS 数据盘专用目录；会话仅留结论及缺口，不带回本机、不入仓`
5. **落盘目录与删除时点**：`/data/forensics/<实际日期>-one-ops-b31p/；取证用户0700、文件0600；计划于<不超过采集后7天的实际时刻及时区>删除；完成后补实际删除时刻`
6. **依据**：`ARC-1017-DAT-01、指定 B31P_EVIDENCE_SPEC.md、（生产取证不逐次请示）、ADR-0037第5／7／8项及Q6`

**已验证**：本地规则、指定规格、上述文件证据及查询结构依据已只读核对。

**未运行**：任何生产查询、账号校验、SQL 编译或执行验证、取证登记；`scripts/precheck.sh` 也未运行，因为其第 29 行会创建临时目录，与本次“不改任何文件”约束冲突。没有 `SUMMARY 0 FAIL` 或生产参数已核实的声明。

