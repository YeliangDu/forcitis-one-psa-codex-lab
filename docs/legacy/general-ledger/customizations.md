# U8C 二次开发、插件与外部接口清单

调查日期：2026-10-14 · 会话：psa-gl-scope · 上级文档：`../U8C_GL_PRACTICE.md` · 方式：§0–§5 为本地文件调查（sonnet 子代理，未连接服务器）；§6 为主会话经 PSA 服务器跨库只读 SELECT 的查证结果（查询见 `u8c_readonly_queries.sql` q10）。
凭据处理：文中不出现任何密码、令牌、密钥的值；凡涉及，只写「存在于某文件」。

路径缩写（下文「证据」列用）：
- `AL` = /Users/yeliang/Projects/FORCITIS/aliyun
- `EV` = AL/evidence/a8-migration-20260906
- `OAS` = AL/evidence/oa-psa-accrual-scope-20261013
- `PSA` = /Users/yeliang/Projects/FORCITIS/psa
- `FST` = PSA/psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst
- `MOD` = forcitis-one-psa 仓库 `docs`
- `IF` = PSA/docs/design/U8C凭证传递接口文档.md

证据强度标记：【文件直证】文件里明写；【推断】由多处事实推出，未被任何文件直接说明；【无证据】本次没找到。

## 0. 先说结论

1. 能确认的「U8C 服务端二开」只有一处：JSON 制单接口 `/service/voucherser`（及伴随的接口日志表 `ky_apilog`）。依据是接口文档对源码的描述（`IF:14-17`），本次没有直接看到 U8C 服务器上的类文件，**文档所说的源码未经我复核**。
2. 客商同步用的是**用友标准**外部交换平台 `XChangeServlet`，不是二开。
3. A8 侧的「制证插件」（包名 `com.seeyon.apps.lexmiscif`，日志前缀「C2+财务集成」）是**第三方厂商插件**，不是公司自研，也不是用友产品；销项开票插件 `LEXMISCIVOUT_*` 同属这个厂商的产品线（表名前缀相同）【推断，依据表前缀与包名】。
4. 除上面几项，U8C 库里还有哪些非标准功能节点、定时任务、触发器、存储过程，本地文件**完全没有证据**，必须靠只读 SQL 查（见第 4 节）。
5. ~~收付账套 2026 年 8,124 张 XX 凭证的来源~~ **已查证（§6 Q1）**：由权责账套上的二开接口「权责凭证发送收付实现」转发而来。U8C 服务端二开因此至少有两组：凭证接口（`voucherser` / 「权责凭证新增接口」「收付凭证新增接口」「权责凭证发送收付实现」）与主数据接口（「权责客商 / 部门 / 人员新增接口」「收付客商 / 部门」）。

## 1. 总表

说明：`QZ` = 权责账套 `u8cloud_QZ`（U8C 应用服务 172.16.10.53:9345）；`SF` = 收付账套 `u8cloud`（172.16.58.187:9345）。「pk_system」是 U8C `gl_voucher.pk_system`。

| 编号 | 名称 | 所属方 | 功能 | 触发 | 读写表 | 凭证 | 认证与暴露（文件所述事实） | 新系统去向 | 证据 |
|---|---|---|---|---|---|---|---|---|---|
| S1 | 凭证制单接口 `/service/voucherser`（类 `VoucherServlet`、`VoucherImpl`、`DataPubDAOSer`，`ncservice2.upm` 注册名 `voucherser`） | U8C 服务端二开（按接口文档） | 接收 JSON，一次调用生成一张记账凭证（含辅助核算、现金流量） | 接口调用（PSA 可靠投递作业；A8 插件是否也走它见 S4） | U8C 侧：写 `gl_voucher`、`gl_detail`、`gl_freevalue`、`gl_cashflowcase`；读 `bd_accsubj`、`bd_bdinfo`、`bd_cashflow`、`bd_glorgbook` 等档案；写 `ky_apilog`。对方侧：无 | 是。写 QZ，`pk_system='XX'`（服务端固定写 XX，另固定 `free9='SF'`、`free10=year#period#no`、`signflag=true`、`voucherkind=0`） | 无任何认证（服务端源码确认不读 token/签名头）；PSA 常量写死公网 IP:9345 的 http 明文；2026-08-01 安全组只放行受控来源，非白名单 ECS 探测公网 9345 为 CLOSED_OR_FILTERED；两台 U8C Windows 防火墙当时仍关闭；`xx_xlog` 有 2026-07 注入探测记录 | 替换，不迁移。新系统过账管道自生成凭证；第一步仍需推送到 U8C，则用带认证的内网/专线新接口（ADR-0022 §8）；第二步 U8C 只读后退役 | `IF:14-17,29,31,59,308`；`FST/../enums/fst/FSTConstant.java:171`（路径见 P1）；`AL/reports/account-security-group-isolation-2026-08-01.md:42-49,109-110,145`；`MOD/U8C_GL_PRACTICE.md:283` |
| S2 | 接口日志 `KyApiLogVO` / 表 `ky_apilog`（接口名「权责凭证新增接口」） | U8C 服务端二开（随 S1） | 记录每次调用的请求报文（截断 3800 字符）与结果 | 随 S1 每次调用 | 写 `ky_apilog`（约 9 万行，用户已知） | 否 | 随 S1，无独立入口 | 退役；`interfacename` 取值是盘点其它二开接口的抓手（见 Q3） | `IF:308` |
| S3 | 外部交换平台 `XChangeServlet`（`/service/XChangeServlet?account=U8cloud&receiver=0001`）及登记表 `xx_exsystem`、对照表 `xx_idcontrastpk`、日志 `xx_xlog` | 用友标准功能 | 外部系统用 XML 报文向 U8C 导入档案/单据（客商档案 billtype=bscubas；各外部系统登记在 `xx_exsystem`） | 接口调用 | U8C 侧写客商档案 `bd_cubasdoc`（推断表名）、`xx_xlog`、`xx_idcontrastpk`（后者在 A8 抽样核对中查过，12 条样本命中 0） | 客商不产生凭证；会计凭证类外部系统登记见 S3 子项 | PSA 报文头只有 account、receiver、operator（一个员工的 U8C 用户主键，嵌在 URL 常量里）；没有口令或令牌字段；`xx_xlog` 约 17.2GB、3,352 万行，为 SF 库最大表，清理机制待用友确认 | 登记的各外部系统逐个判定，见 §3.3；新系统不依赖 | `IF:321`；`FST/service/a8/A8ServiceImpl.java:115`；`AL/reports/forcitis-rds-storage-alert-rootcause-2026-08-30-v3.md:158,165`；`MOD/U8C_GL_PRACTICE.md:142`；`EV/formal-a8-cif-u8c-existing-voucher-match-readonly.txt:71` |
| S3a | 外部系统登记 `lexmisc2`（QZ）、`udh`（QZ，U 订货，未用） | 用友标准登记项，对应 A8 插件 | 外部系统身份登记 | — | `xx_exsystem` | `lexmisc2` 对应 XX 凭证 | 同 S3 | A8 制证插件整体停用；`udh` 清理 | `MOD/U8C_GL_PRACTICE.md:142` |
| S3b | 外部系统登记 `A8`、`9700 会计凭证`、`kehusync A8客商同步`、`fybxpz 费用报销凭证`、`org_member 人员同步`（SF） | 登记项为用友标准；背后调用方未知【无证据】 | 见 §3.3 | 未知 | `xx_exsystem` | `9700`、`fybxpz` 名称显示与会计凭证/费用报销凭证相关，是否仍有流量未知 | 同 S3 | 逐个查 `xx_xlog` 近期流量后判定，见 Q4 | `MOD/U8C_GL_PRACTICE.md:142` |
| A1 | A8「C2+财务集成」制证插件（包 `lexmiscif`，表 `LEXMISCIF_*`，菜单：待制证、已制证、凭证查询、数据修复、手工制证等） | A8 侧插件（第三方厂商） | 把 16 种 OA 表单审批通过后的单据生成 U8C 凭证；含科目/部门/银行对照、凭证查询、数据修复、手工制证页 | 表单审批后自动（`LEXMISCIF_AUDITEDBILL` 记录已审批单据）；人工（待制证/手工制证/数据修复页，需要菜单权限） | A8 侧读：16 张表单主表（如 `formmain_0399` 项目开票）、`LEXMISCIF_CONTRAST*`、`DEPTAUX`、`ERPFIELDSETUP`；写：`LEXMISCIF_VOUCHER`（402,285 行）、`AUDITEDBILL`（32,788 行）、`VOUCHERGROUP`、`DELLOG`、`CACHEACCTID` 等。U8C 侧读（日志直证）：`u8cloud_QZ..bd_glorgbook`、`bd_vouchertype`；写：由 U8C 服务端落 `gl_voucher/gl_detail` | 是。QZ，XX。A8 记的 `V_PZID` = U8C `gl_detail.pk_detail`，`V_EXTPZNO` 形如「LexmisCIF<数字>@<U8C pk_voucher>」。SF 是否同时写入见 §3.1 | 两条通路【文件直证】：(1) JDBC 直连 QZ 库，目标库登录名与受保护口令存在于 a8_db 表 `LEXMISCIF_DBCONFIG`，插件自带的解码逻辑可还原（迁移验收时已用其验证连接成功，口令值未落盘）；(2) 应用服务器端点存在于 `LEXMISCIF_ASCONFIG`，其中用户、口令、appkey、appsecret、appid 均为空，与 S1 无认证一致。2026-09-06 端点由公网 112.124.16.247:9345 改为内网 172.16.10.53:9345 | ADR-0021：6a–6c 的表单迁入新系统，凭证由新系统事实生成；这些表单对应的插件功能同步停用；切换日后插件对这些表单的凭证为 0 | `EV/formal-a8-cif-live-state-readonly.txt:56-72`；`EV/formal-a8-cif-u8c-as-endpoint-cutover.txt:55-62`；`EV/formal-browser-business-u8c-acceptance.md`；`OAS/live-middleware-main-settings.json`；`MOD/runtime/a8_u8c_vouchers.tsv` |
| A1-m | A1 的「手工制证」用法 | A8 侧插件 | 财务在插件内不经 OA 单据直接制证（手续费、商票兑付、理财、利息收入、保证金转回） | 人工 | `LEXMISCIF_VOUCHER` 中 `V_FORMID=0`、`V_OANO` 为空的行（714 行） | 是，QZ，XX | 同 A1 | 属 ADR-0022 的「手工凭证」范围，新系统总账手工分录承接 | `OAS/live-zero-formid-origin.json`；`OAS/live-unlinked-target-purpose.json`；`EV/formal-a8-zmcif-controller-api-readonly.txt:29` |
| A1-d | A1 的部门映射表 `LEXMISCIF_DEPTAUX`（276 行）及科目/银行对照表 | A8 侧（插件配置数据） | OA 部门→U8C 部门辅助核算；费用/科目/银行→U8C 科目 | 随 A1 | A8 侧读写；PSA 视图 `psa_oa_interface_dept` 跨库读 | 否（口径配置） | 同 A8 库 | 用户决定「主数据全部进新系统」，6c 前迁入新系统，成为科目映射 DMN | `MOD/OA_PROCESS_INVENTORY.md:214-217,273`；`PSA/psa-backend/.../V20261008175240__create_psa_oa_interface_dept_view.sql` |
| A2 | 销项开票插件（`LEXMISCIVOUT_*`，主表 `LEXMISCIVOUT_OPENMAIN`），同厂商 `LEXMISCIV*` 系列（`LEXMISCIVPOOL` 发票池、`LEXMISCIV_QUERYTEMPLATE` 等；付款申请单上有自定义控件类型 `lexmisciv0001`） | A8 侧插件（第三方厂商，推断） | 开具数电专票/普票并回写发票档案；税务平台对接在插件内 | 表单审批后（项目开票、首付款开票、退票）【推断】 | A8 侧读写 `LEXMISCIVOUT_OPENMAIN`、发票档案 `formmain_0428`；税务平台侧读写：**无任何文件**说明接口、地址、认证 | 本身不直接产生凭证；开票申请单再由 A1 制证 | 无证据 | ADR-0021：6b 迁入，`platform.integration` 重建税务数电平台对接，替代本插件；切换日后 `LEXMISCIVOUT_OPENMAIN` 无新发票 | `MOD/OA_PROCESS_INVENTORY.md:11,353`；`MOD/runtime/a8_u8c_vouchers.tsv:62-71`；`AL/evidence/a8-GD2026092089/launch-20260922.txt:149` |
| A3 | A8→U8C 客商待同步表 `T_U8C_BD_CUBASDOC`（PSA 代码注释称「OA 待同步视图」，登记表称表） | A8 侧（生产者未知）【无证据】 | 存放待推送的客商（编码、名称、简称、纳税人识别号、地区分类） | 未知 | 被 P2 读取 | 否 | 同 A8 库 | 客商主档归新系统，此表废止 | `FST/dal/dataobject/a8/CustomerAndSupplierDO.java:9-11`；`MOD/phase1a/A8_ACCESS_REGISTER.md:32` |
| P1 | PSA 凭证推送（`U8cVoucherClient` + `U8cVoucherPayloadRenderer` + 可靠投递作业 `reliableDeliveryDispatchJob`；旧链路 `CarryForwardServiceImpl.callU8cInterface`） | PSA 侧 | 把 WIP 结转、成本结转、人工重分类等过账凭证推到 U8C | 作业（可靠投递兜底扫描）+ 过账完成后即时投一次 | PSA 侧读：`psa_dept_u8c`、`psa_oa_account_u8c`、`psa_revenue_posting_rule*`；写：`psa_u8c_voucher`（旧链路）、凭证行的 `external_receipt_no`。U8C 侧：经 S1 写 | 是，**只进 QZ**，XX；制单人固定 `tbadmin`，类别「记账凭证」；2025 年约 70 张，2026 起随新结转引擎增加 | 目标 URL 硬编码在代码常量；http 明文；无认证；无幂等（回执丢失重投会重复）；旧链路调用已被注释、结果写死成功 | 新系统 `operations.facts` 单一过账管道取代；ADR-0022 §8 要求推送幂等、带认证、内网/专线 | `FST/enums`（文件 `PSA/psa-backend/yudao-module-fst/yudao-module-fst-api/src/main/java/cn/iocoder/yudao/module/fst/enums/fst/FSTConstant.java:169-171`）；`FST/infrastructure/cost/projectcost/voucher/U8cVoucherClient.java:42-52`；`FST/trigger/task/ReliableDeliveryDispatchJob.java:26`；`FST/service/wip/CarryForwardServiceImpl.java:211-219,311,328` |
| P2 | PSA 客商推送作业 `syncCustomerAndSupplierToU8CJob`（XXL-JOB 任务「同步客商信息到U8C」） | PSA 侧 | 逐条把 A8 待同步客商以 XML（billtype=bscubas，sender=A8，proc=add）推到 U8C 客商档案；单条失败不中断，汇总抛错 | 定时（XXL-JOB，近 30 天 3,950 次触发、成功 3,880、失败 39、无回调 31，截至 2026-08-01） | PSA 读 A8 `T_U8C_BD_CUBASDOC`；U8C 写客商档案 | 否 | 目标 `http://118.31.22.230:9345/service/XChangeServlet`（公网 IP，http 明文，无口令字段）；该 IP 在 aliyun 目录内**查不到归属**，不能确认指向 QZ 还是 SF | 客商主档归新系统；推送改从新系统主档出发（或随总账第二步消失）；新接口须带认证 | `FST/service/a8/A8ServiceImpl.java:56-61,91,115`；`FST/trigger/task/sysU8CJob.java:16-21`；`FST/../resources/fst/sync-u8c-template.xml:2`；`AL/reports/account-security-group-isolation-2026-08-01.md:81`；`PSA/.../FSTConstant.java:165-167` |
| P3 | PSA 跨库只读视图 `psa_u8c_gl_detail`、`psa_u8c_accsubj`、`psa_u8c_deptdoc`、`psa_u8c_voucher_supplier` | PSA 侧 | 读 U8C 实际入账分录、科目/部门有效性、凭证上的供应商辅助，用于委外/居间费未计提检测（#1973）和人工重分类（#1972） | 查询时（视图） | 读 `u8cloud_QZ`：`gl_voucher`、`gl_detail`、`bd_accsubj`、`gl_freevalue`、`bd_bdinfo`、`sm_user`、`bd_glorgbook`、`bd_glorg`、`bd_deptdoc`；写：无 | 否 | 同一 RDS 实例上用 SQL 登录名 `u8c`（PSA 数据源配置存在于 PSA 的 application-dev.yaml，口令不复述）；该登录名对 A8 库也有 db_owner（2026-08 报告） | 总账进新系统后，对账和重分类改读新系统自有明细账；第一步期间保留为对账只读通道，改用只读专用账号 | `PSA/psa-backend/.../V20261008175000__create_psa_u8c_gl_detail_view.sql`、`...175100...`、`...175120...`；`AL/evidence/psa-GD2026092192/01-host.txt:24-28`；`AL/reports/aliyun-account-optimization-audit-2026-08-01.md:107` |
| P4 | PSA 财务补登凭证号表 `psa_labor_reclass_voucher_reg` | PSA 侧（自有表） | 财务补登人工重分类要引用的 U8C 凭证号 | 人工 | PSA 自有 | 否 | — | 随人工重分类并入新系统过账 | `PSA/psa-backend/.../V20261008175210__create_psa_labor_reclass_voucher_reg.sql` |
| V1 | 自动转账（`gl_transfer*`，凭证 pk_system=`TR`） | 用友标准功能 | 期末结转模板（损益结转、项目成本结转、研发支出等） | 人工点击期末处理 | `gl_transfer`、`gl_transferdetail`、`gl_transferhistory`，写凭证 | 是，QZ/SF，TR | U8C 内部 | 新系统 `accounting.ledger` 期末结转（ADR-0022）；第二步前仍用 U8C | `MOD/U8C_GL_PRACTICE.md:113,166-188`；`MOD/u8c/u8c_readonly_queries.sql`（q3 `QZ_t_transfer`） |
| V2 | 总账手工录入、常用凭证（163 张模板，SF）、固定资产模块（`fa_*`，FA）、IUFO 报表 | 用友标准功能 | 手工凭证、固定资产卡片与折旧、报表 | 人工/期末 | 同名标准表 | 是，GL/FA | U8C 内部 | 手工凭证：新系统手工分录；固定资产：先导入后自建；报表：新系统出权责口径三大报表 | `MOD/U8C_GL_PRACTICE.md:162-164,240-251`；`OAS/live-u8c-fa-table-catalog.json` |
| D1 | 共享 SQL 登录名 `u8c`（A8、PSA、BI、两套 U8C 共用同一 RDS 实例和登录名）与 RDS 公网端点 | 基础设施，非二开 | 所有跨库读写的实际通道 | 常驻连接 | 实例上有 `a8_db`、`u8cloud`、`u8cloud_QZ`、PSA 库、BI 库 | 否 | 登录名对 A8 库为 db_owner；2026-08-01 起公网白名单收紧为 U8C 权责 EIP 与 BI；U8C 权责数据源 URL 用的是带 `0o` 后缀的主机名【推断为公网端点，依据 08-01 报告称 U8C 权责走公网】 | 新系统不得复用共享登录名；每个跨系统通道独立账号、最小权限 | `AL/reports/account-security-group-isolation-2026-08-01.md:70-74,151`；`AL/reports/a8-production-migration-execution-2026-09-06.md:68,234`；`EV/formal-u8c-accrual-datasource-location-readonly.txt` |

## 2. 跨项事实：认证与网络暴露（文件所述）

- U8C 两台应用服务均为 Windows Server 2016，监听 9345，JVM 为 Oracle Java 1.7.0_141，fastjson 版本 1.1.41（升级门禁关闭，尚未替换）。文件：`AL/reports/u8c-fastjson-1.2.84-gated-upgrade-2026-08-25.md:11-12`、`EV/formal-u8c-accrual-service-config-readonly.txt`。
- 2026-08-01 安全组隔离：9345 只对 A8、PSA 生产/测试的安全组引用，加上固定 EIP 兼容白名单和办公来源开放；两台 U8C 的 Windows 防火墙当时关闭，列为下一优先级。文件：`AL/reports/account-security-group-isolation-2026-08-01.md:15-18,42-49,145-148`。2026-09-17 安全复核对 U8C 收付服务器记录为「无全网 TCP 放行」（`AL/reports/aliyun-security-review-2026-09-17.md:90`）；是否已启用 Windows 防火墙，文件未说明。
- PSA 到 U8C 的两个 URL 是不同的公网 IP（凭证 112.124.16.247，客商 118.31.22.230），都是 http 明文。A8 到 U8C 的端点已于 2026-09-06 切到内网 172.16.10.53。
- 接口文档称「联调即生产」：只有一个地址且直连生产账套，没有测试账套（`IF:322`）。
- 业务验收边界：迁移后一笔全新凭证的写入闭环**没有跑过**（`NEW_VOUCHER_WRITE_E2E=NOT_RUN`），A8 到 U8C 的写通道只验证了「配置可达、元数据可查、既有凭证 12/12 一致」，`EV/formal-browser-business-u8c-acceptance.md`。

## 3. 分项细节与不确定处

### 3.1 A1 制证插件（最重要）

**构成**【文件直证】
- 16 张被绑定的表单（`LEXMISCIF_CONTRASTMAIN` 16 行，均未停用 `CM_ISSTOP=0`）：BU预提费用明细表、差旅费用报销单、产值坏账申请、发票退票申请、付款申请单、付款申请单-财务、付款申请单-人事、还款单新增、回款单新增、借款单新增、客户产值确认单、日常费用报销单、首付款开票申请、项目开票申请、业务招待费用报销单、预付款核销申请。
- 其中「产值坏账申请」已绑定但**没有任何凭证记录**（`a8_u8c_vouchers.tsv` 无此项）；另有 784 行 `V_FORMID=0`（714 行无 OA 单号，是手工制证）。
- 累计 402,285 行、32,339 张单据；近 180 天 99,179 行。按表单（行数 / 单据数 / 近 180 天行数）：差旅 159,154/8,726/39,652；日常费用 72,038/5,648/19,902；付款申请单 61,552/1,972/13,620；付款-人事 32,093/897/6,848；回款 15,756/2,839/3,992；业务招待 14,149/2,442/3,380；项目开票 14,574/4,494/2,802；预付款核销 10,986/670/2,364；客户产值确认 7,560/2,520/2,283；BU预提 4,696/190/1,210；首付款开票 3,237/971/573；付款-财务 2,562/18/1,248；发票退票 2,700/802/588；借款 222/61/40；还款 222/63/36。（`MOD/runtime/a8_u8c_vouchers.tsv`）
- 配置：`ASCONFIG` 1 行、`DBCONFIG` 1 行（目标库 `u8cloud_QZ`，ERP 类型 U8C 版本 `u8c_2.7`），`CONTRASTDEF` 88、`CONTRASTDET` 17,650、`ERPFIELDSETUP` 14,104、`DEPTAUX` 276、`PERSONCONFIG` 416、`FLOWMAKEAUTH` 16。`EV/formal-a8-cif-db-inventory-readonly.txt`、`formal-a8-cif-live-state-readonly.txt:56-61`。
- 状态：`V_ERPSTATE=1`（已制证）381,970 行，`-1` 49 行，`3` 48 行；`AUDITEDBILL` 按状态 0/1/3 为 852/30,904/1,032 条（状态含义文件未解释）。
- 插件同时具备 U8C 通用工具类 `CIFU8CTools` 与 U8C OpenAPI 工具类 `U8COPENAPITools`，但 OpenAPI 的应用 id、key、secret 在配置里全为空，`LEXMISCIF_OAASCONFIG`、`APPSERVERCONFIG` 均 0 行，所以走 OpenAPI 的可能性低【推断】。

**不确定处**
1. **写入 U8C 的实际通道不明**：是调 S1 `voucherser`，还是走 S3 外部交换平台，还是别的接口？文件只证明（a）端点配置在 ASCONFIG；（b）迁移验收时 60 秒采样未抓到 9345 新会话；（c）A8 记录的 `V_PZID` 等于 U8C 生成的 `pk_detail`，说明 U8C 服务端生成主键后回传，因此**不太像插件直接 INSERT 数据库**【推断】。判别办法见 Q2。
2. **是否同时写 SF**：A8 只有一行 DBCONFIG（QZ）。但 U8C_GL_PRACTICE 称 A8 插件同时向两套账制证，且 SF 的 XX 凭证 2026 年有 8,124 张。A8 侧外部键核对显示，每组 `V_EXTPZNO` 的目标 `pk_voucher` 在 SF 或 QZ 之一存在，而不是两边都在（付款申请单-人事 794 组：SF 282、QZ 508、两边都无 4；`OAS/live-u8c-external-key-link.json`）。可能解释：(a) 2024 年（QZ 建账前）写 SF，2025 起写 QZ，但 SF 2025/2026 的 XX 又是谁写的？(b) U8C 服务端有二开把 QZ 的凭证镜像到 SF（权责账套一张凭证同时记数字科目与 Q 科目，镜像数字科目行很自然）。(c) 曾有第二套配置。**三者本地都无证据**，需 Q1。
3. 迁移后验收只做到技术链路，「待制证/已制证」菜单权限验收为 PARTIAL。

**去向细节**：6a（客户产值确认单、产值坏账）、6b（开票、首付款开票、退票、回款、预付款回款类）、6c（费用、付款、预付款核销、借还款、BU预提）的表单各自迁入后，A1 对应绑定逐个关停；全部关停后 `LEXMISCIF_VOUCHER` 应无新行（OA 盘点 6c 出口条件）。手工制证页的用法转给新系统总账手工分录。

### 3.2 A2 销项开票插件

- 唯一直接证据是计数：`LEXMISCIVOUT_OPENMAIN` 中数电专票/普票、各状态共数千张（累计 5,956、近 180 天 1,143，OA 盘点）。
- 税务平台的地址、认证方式、是用厂商 SaaS 还是税控盘/直连，**本地没有任何文件**。ADR-0021 已要求在 `platform.integration` 重建，所以这些必须在 6b 入口前从 A8 服务器的插件配置里查出来（A8 库表 `LEXMISCIVOUT_*`、`LEXMISCIV_*`，如 `LEXMISCIVOUT_AUTHREGISTER`、`LEXMISCIV_AUTHENTICATIONLOG`、`LEXMISCIV_CFGAUTHMAIN`）。这不是 U8C 侧问题，列出供 6b 使用。
- A8 凭证插件里有一批 `source_doc=OA发票`、`source_db=ef_uto_v_kpxx_new` 的凭证摘要记录（2024-03），说明早期开票数据来自另一个开票库视图，而不是现在的 `LEXMISCIVOUT`；这个来源现在是否还用，文件未说明（`EV/formal-a8-cif-live-state-readonly.txt` LATEST_VOUCHER_SUMMARY 段）。

### 3.3 S3 外部交换平台及其登记项

- 已知登记（用户提供，未经我复核）：QZ 有 `lexmisc2`、`udh`；SF 有 `A8`、`9700 会计凭证`、`kehusync A8客商同步`、`fybxpz 费用报销凭证`、`org_member 人员同步`。
- `kehusync`：与 P2 的 billtype=`bscubas`、sender=`A8` 对应，是客商同步通道。但 P2 的 URL 里 `account=U8cloud`，而 A8 的 ASCONFIG 里 `nc_code=U8cloud` 同值，与 QZ 的端点配合；而登记表把 `kehusync` 放在 SF。**账号编码 U8cloud 究竟映射哪个库，无法从本地文件判定**（Q5）。
- `fybxpz`（费用报销凭证）、`9700 会计凭证`、`org_member`（人员同步）：名称显示曾有外部系统向 U8C 导入报销凭证、凭证、人员；本地没有这些调用方的代码、配置或流量，可能是早年系统（如旧报销系统、用友 9700）遗留。**必须用 `xx_xlog` 的 sysname 与时间分布判定是否仍活跃**（Q4）。
- 安全：`xx_xlog` 中曾出现 2026-07 的 SQL 注入探测字符串（来源字段）（`MOD/U8C_GL_PRACTICE.md:283`）。

### 3.4 P1 PSA 凭证推送

- 地址：`FSTConstant.U8C_VOUCHER_URL`（公网 IP，写死）。投递语义：`flag=="success"` 为成功，回执取 `rsdata.no`。
- 文档与代码不一致（供核对）：`U8C_GL_PRACTICE.md:160` 说本地 mock 开关在代码中不存在，但 `U8cVoucherClient.java:42-52` **存在** `fst.cost.u8c.voucher.mock-enabled`（默认 false）；「测试」前缀护栏 `injectTestMark` 确实在代码中找不到。
- 凭证科目映射和报文结构见 `MOD/U8C_GL_PRACTICE.md:144-160`，这里不重复。
- 旧 WIP 结转链路：`CarryForwardServiceImpl` 中调用被注释，`updateResult` 写死成功，因此旧链路不再实际推送；`psa_u8c_voucher` 表是否仍有新增未核。

### 3.5 P2 PSA 客商推送

- 报文模板里嵌有一个真实的客商样例数据（名称、纳税号）；这是模板默认值，实际推送时被覆盖。未复述。
- 近 30 天 3,950 次触发（约每 11 分钟一次）、39 次失败、31 次无回调，需单独排查（2026-08-01 报告原话）。当前状态未知（#1928 已改为汇总失败，说明已处理过一轮）。
- `operator` 参数是某位员工的 U8C 用户主键，代码注释写了该员工姓名；员工离职或停用会让推送失败。此处不复述姓名与主键。

### 3.6 U8C 服务器上值得点名但无证据的位置

`EV/formal-u8c-accrual-service-config-readonly.txt` 的 `D:\U8CQZ` 目录列表里，下列目录可能放置二开产物，**仅有目录名与修改时间，无内容**：
`apilog`（2024-11-07）、`tempvoucher`（2026-08-03，较新）、`external`、`pfxx`（2017）、`hotwebs`、`u8cscript`（2019）、`DBTrans`、`localPatchesRepo`、`security-backup`（2026-07-31）、`domain`（2025-12-08）、`webapps`（2025-09-28）。
`tempvoucher` 在 2026-08-03 被写过，可能是凭证接口的临时文件，需到服务器上看（本次不允许）。

## 4. 还需在 U8C 库里查证的问题与建议查询思路

全部为只读 SELECT。先对 `INFORMATION_SCHEMA.COLUMNS` 取表的实际列名再写正式语句，因为我没有 `sm_funcregister`、`ky_apilog` 的列定义。

**Q1 收付账套里的 8,124 张 XX 凭证是谁写的，与权责账套是什么关系**
- 在两库各取 2026 年 XX 凭证的 `ts`、`prepareddate`、`explanation`、`free1`–`free10`、`pk_sourcepk`、`pk_prepared`，以摘要里的单据号（如 `EX…`、`FK…`、`HK…` 前缀）为键做两库对照：同一单据号在两库是否都有凭证、`ts` 是否相差几秒（相差几秒高度提示镜像或双写）。
- 用 `sm_user` 解出两库 XX 凭证的 `pk_prepared`（是否都是同一个制单人，是否为 `tbadmin`）。
- 查两库 `sys.triggers`、`sys.procedures`（排除 `is_ms_shipped=1`）是否有跨库写入逻辑；查 `xx_exsystem` 全量与各系统在两库的 `xx_xlog` 近 90 天条数。

**Q2 A8 插件走哪条通道写 U8C**
- 取一张已知由 A8 生成的凭证（本地样本：pk_voucher `1001181000000000E20G`，2026-09-04，QZ，凭证号 822）和一张已知由 PSA 生成的凭证（摘要以「PSA系统」开头），比较 `free9`、`free10`、`attachment`、`signflag`、`pk_system`、`voucherkind`、`pk_sourcepk`。接口文档称 S1 固定写 `free9='SF'`、`free10=year#period#no`；若 A8 凭证也是这个模式，则 A8 走的是 S1；否则是别的通道。
- `ky_apilog` 里看有没有来自 A8 服务器内网 IP 的调用记录（需先看列名）。

**Q3 `ky_apilog` 里的接口清单**
- `SELECT interfacename, COUNT(*), MIN(ts), MAX(ts) FROM ky_apilog GROUP BY interfacename`，若有来源/调用方列，再按其分组；这是发现 S1 之外其他二开接口的最直接办法。接口名除「权责凭证新增接口」外还有什么，各自近期是否有调用。
- 注意：该表只记二开接口的调用，标准 XChangeServlet 的调用在 `xx_xlog`。

**Q4 外部交换平台的活跃度**
- `xx_exsystem` 全部列；`xx_xlog` 按系统名（以及来源/接口字段）统计 `COUNT`、`MIN(ts)`、`MAX(ts)`、近 90 天条数，重点判定 `fybxpz`、`9700 会计凭证`、`org_member`、`A8`、`udh`、`lexmisc2`、`kehusync` 哪些还在被调用。
- 因 `xx_xlog` 约 3,352 万行，查询必须带时间范围，并用 `READ UNCOMMITTED`，避免拖累生产。
- 对 `xx_xlog` 里来源字段含 `' OR`、`WAITFOR`、`sysadmin` 的行单独计数，给安全会话登记用。

**Q5 `account=U8cloud` 对应哪个库**
- 查 U8C 的账套/数据源配置（`sm_*` 或 `pub_*` 中存账套编码的表，先按列名搜 `accountcode`、`acccode`），确认编码 `U8cloud` 属于 QZ 还是 SF；再对比 P2 的 118.31.22.230 与 P1 的 112.124.16.247 各对应哪台服务器（这个只能在阿里云侧确认，不是 SQL）。

**Q6 非标准功能节点与菜单**
- `sm_funcregister` 与菜单表（疑似 `sm_menuitemreg` 一类，需核表名）：筛 `class_name` 不以标准包前缀（`nc.ui.`、`nc.bs.`、`nc.vo.`）开头的，或包含 `ky`、`.ky.`、`lexmis` 的；筛功能编码不在标准模块编号段的；按 `ts`/创建时间倒序看最近新增。
- 同时查按钮注册、单据模板里挂的自定义动作类（`pub_billaction`、`pub_billtemplet` 一类，需核表名）。

**Q7 数据库层二开**
- 两库 `sys.tables` 里不属于标准前缀（`bd_`、`gl_`、`fa_`、`iufo_`、`sm_`、`pub_`、`xx_`、`ky_` 等）的表，以及按 `create_date` 看 2024 年后新增的表；`sys.triggers`、`sys.procedures`、`sys.views`、`sys.sql_modules` 里非 `is_ms_shipped` 的对象；`msdb.dbo.sysjobs` 中针对这两库的 SQL Agent 作业（需 RDS 权限，可能查不到）。

**Q8 U8C 内部定时任务与用户**
- 找任务调度表（按表名含 `task`、`sched`、`job`、`timer` 搜），列出已启用的定时任务及类名；`sm_user` 中非人员的服务账号（`tbadmin`、A8 或接口使用的操作员）及其最近登录、所属角色与可访问公司，用于新接口设计时的最小权限。

**Q9 凭证来源 TR 的含义**
- 两份文档矛盾：`U8C_GL_PRACTICE.md:113` 说 TR 是自动转账；`OA_PROCESS_INVENTORY.md:14` 说 TR 是出纳。查 `gl_voucher` TR 类的摘要前缀与 `gl_transferhistory` 的对应，判定后更正其一。

**Q10 BI 对 U8C 库的使用**
- 2026-08-01 报告称 BI 走 RDS 公网端点，且 BI 会话使用 `u8c` 登录名；BI 是否读 `u8cloud`/`u8cloud_QZ`，本地无证据。在 RDS 侧用连接来源和 `sys.dm_exec_sessions` 的 `program_name`/`host_name` 查（只读 DMV）。

## 5. 对新系统方案的直接提示

1. 新系统与 U8C 之间真正需要重建的接口只有两个：凭证推送（取代 S1 的调用方 P1 和 A1）与客商推送（取代 P2）。前者必须幂等、带认证、走内网或专线；U8C 侧若不改造 S1，认证只能在新系统与 U8C 之间加一层反向代理或网关，这点需要用户决定，U8C 本身「先不管」只是决定不修 U8C 暴露面，不能让新接口同样裸奔。
2. 迁移期**无法在 U8C 侧区分 A8 与 PSA 的 XX 凭证**（pk_system 都是 XX）。可用的区分线索是 S1 固定写的 `free9/free10` 模式与摘要前缀，需 Q2 验证后写入对账方案。
3. A8 制证插件在切换日前一直在推凭证，且 SF 账套的 XX 凭证来源未明；Phase 4 的「U8C 推送权已切换」检查必须同时覆盖 SF（Q1 的结论决定是否要给 SF 单独设计关停点）。
4. 税务数电平台对接（A2）是 6b 的硬前置，其地址、认证、回执格式要从 A8 服务器的插件配置里取，当前零证据。
5. 共享 SQL 登录名 `u8c` 是现存最大的横向风险：A8、PSA、BI 都用它，并对 A8 库为 db_owner。新系统读 U8C 只用独立只读账号。


## 6. 只读查证结果（2026-10-14，主会话）

| 问题 | 结论 | 依据 |
|---|---|---|
| Q1 收付账套 XX 凭证从哪来 | **权责账套转发**。收付账套 2026 年 8,124 张 XX 凭证全部 `free9='QZ'`，摘要前缀构成与权责账套一致（EX 4,884、发票 1,215、HK 811、FK 552、SP 194、YF 193 …）；权责账套 `ky_apilog` 有「权责凭证发送收付实现」成功 29,954 次（2024-12-11 至 2026-10-12），收付账套 `ky_apilog` 有「收付凭证新增接口」成功 20,795、失败 3,686 次（2025-02-12 起）。反方向：权责账套中「计提折旧」「固定资产」类 XX 凭证 `free9='SF'`，即收付账套固定资产模块的折旧凭证转入权责账套 | q10 `Q1_*`、`Q2_free_pattern`、`Q3_kyapi_*` |
| Q2 A8 插件走哪条通道 | 权责账套 XX 凭证的 `free9` 为空（A8 / PSA 直接写入），与收付账套的转发凭证（`free9='QZ'`）可以区分；「权责凭证新增接口」成功仅 539、失败 222 次，远少于 A8 凭证量，说明 **A8 插件不走 `voucherser`**，走的是另一条通道（推断为插件直接调用 U8C 服务或 XChange 的 `gl` 单据类型：权责账套 `xx_xlog` 中 `gl` 类型 18,123 条，2024-11 至今，近 90 天 2,941 条） | `Q3_kyapi_qz`、`Q4_xlog_activity_qz` |
| Q3 二开接口清单 | 权责账套：权责凭证新增接口（成功 539 / 失败 222）、权责凭证发送收付实现（29,954）、权责客商新增接口（65,842，至今）、权责人员新增接口（1,917，至 2026-01）、权责部门新增接口（324，至 2025-07）。收付账套：收付凭证新增接口（20,795 / 3,686）、收付客商（成功 7,590 / 失败 58,953）、收付部门（321） | `Q3_kyapi_*` |
| Q4 外部交换平台活跃度 | 权责账套：`gl` 18,123（近 90 天 2,941）、`bsdept` 80、少量 `so`、`psndoc`、`voucher`、`test`；收付账套：`bscubas` 客商 191,097（近 90 天 19,875，A8 客商同步至今在用）、`bspsn` 人员 336 万条（2024-04 后停止）、`gl` 58,372（2026-07 后停止）、`bsdept` 433（2025-01 后停止） | `Q4_xlog_activity_*` |
| Q6 非标准功能节点 | 没有发现 `ky` / `lexmis` 包名的界面节点；二开在服务端（接口与日志），不在菜单。两库都有一批 Web 报销节点（`bxmanageui.jsp`、`jkmanageui.jsp`，用友 Web 报销，未见使用）与 `u8c.ui.oip.*` 开放平台节点 | `Q6_nonstd_func` |
| Q7 数据库层对象 | 权责账套：视图 101 个（最近创建 2024-10），无触发器、无存储过程；收付账套：视图 102 个、触发器 2 个（2020-07 创建） | `Q7_db_objects_*` |
| Q9 凭证来源 TR | **TR 是自动转账**（期末结转）：权责账套 TR 凭证摘要全部为「结转损益」「期间损益结转」；收付账套为期间损益结转、项目成本结转、研发支出结转、结转未分配利润。`OA_PROCESS_INVENTORY.md` 中「TR 是出纳」的说法需更正 | `Q9_tr_explanations` |

**对新系统的含义**：G1 期间新系统只需对**法定账那一套**推送；若法定账是收付账套，而新系统按准则口径记账，则推送前要按「权责 → 收付」的口径差异生成收付口径分录（即现在人工在权责凭证里写的那组数字科目分录），这部分规则要在 `rules/GL_RULES.md` 中由财务确认。所有二开接口无认证，新系统不复用，按 ADR-0022 第 8 条另建带认证的内网通道。
