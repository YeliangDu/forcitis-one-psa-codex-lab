> R0 工具对比小样的实测报告（子代理 sonnet 撰写，合成数据，本机 Docker；代码与数据留在会话 scratchpad，未入库）。结论与建议见 ../TOOL_COMPARISON.md。

# BI 工具四选项小样 — 实测报告

- 日期：2026-10-14 · 位置：`W=…/scratchpad/toolsample/`（下文路径均相对 W）
- 范围：全部合成数据，本机 Docker（`postgres:17`，端口仅绑 127.0.0.1:55432）。未连接任何生产或远程库，未改动 `/Users/yeliang/Projects/FORCITIS` 下任何仓库（只读参考），未推送。
- 收尾：已停止并删除我启动的容器 `toolsample-pg`、`toolsample-metabase` 与网络 `toolsample-net`；镜像 `metabase/metabase:latest`（1.92GB，v0.64.2.2）保留。Metabase 内的数据随容器删除；PG 数据可用 `python3 data/gen.py`（需先启动同名容器，见 §1）重建。
- 「耗时」是本代理的墙钟估算（含写代码与调试，不是人工工时），仅供量级参考。

## 1. 合成数据与目录（与选项无关）

| 项 | 内容 |
|---|---|
| 生成 | `data/gen.py`（140 行，固定随机种子）→ `data/csv/*.csv` → `docker exec psql \copy` 入库，建表脚本 `data/schema.sql` |
| 规模 | 5 个 BU（BU-A..E）、2 个法人（LE-1/LE-2）、200 个项目、30 个客户集团、24 个月（2025-01..2026-12，实际到 2026-09） |
| BU 生效期 | BU-E 在 2026-01 由 LE-1 转到 LE-2，`dim_bu` 有两个版本（bu_key 5、6，生效期不重叠）；事实表带版本化的 bu_key |
| 事实行数 | 收入确认 2597、开票 1151、回款分配 1431、工时 9450、在册人数快照 105、人工成本 105、目标 1575、应收期末快照 6695 |
| 星型 | schema `reporting`：dim_period / dim_bu / dim_project / dim_customer_group + 8 张事实；`fact_ar_snapshot` 是应收月末快照（每张未结清发票每月末一行，由 SQL 从开票与回款算出） |
| 目标 | 类型 budget / commitment / challenge；指标 M-01（新签合同额，仅有目标无实际）/ M-08 / M-10 / M-11；预算在 2026-04 起有 v2（v2 = v1 × 1.05），其余为 v1 |
| 税率 | 项目随机 6%/9%/13%，收入确认行带不含税、含税、税率 |

指标目录 `catalog/metrics.yaml`（208 行）共 15 个指标：基线中的 M-08、M-10、M-11、M-12、M-13、M-14、M-15、M-38、M-40、M-41、M-42、M-45，加 3 个样例内部辅助指标 X-01 人工成本、X-02 可计费工时、X-03 标准产能工时（提议，不在基线）。每个指标带 10 个 OAT 声明字段（oat_symbol、term、unit、period_type、aggregation_rule、fact_owner_bc、accounting_basis、tax_policy、policy_version、formula）和一个供编译器读的 `sql` 段。另有 `catalog/dashboards.yaml`（三个看板的请求，33 行）与 `catalog/terms.yaml`（模拟 terms.yaml：正式名白名单 + 禁用方言）。

我对口径做的取舍（目录中已写明，不是基线结论）：
- 账龄 = 快照月末日 − 开票日，桶为 ≤45、(45,90]、(90,180]、(180,365]、>365；M-14 为 >90 天；M-15 为月末日 > 约定到期日。
- 目标「最新版本」= 每个 目标类型 × 指标 × BU × 期间 取 MAX(version)。最初写成「每类型 × 指标全局最大版本」，结果 2026-04 前的预算全为空，已改正。
- M-38 = (月初 + 月末在册) / 2；M-42 分子只取可计费工时，分母 = 平均在册 × 工作日（周一至周五，不扣节假日）× 日标准工时（默认 7.5，LE-2 覆盖为 8.0）。「分子是否含商机工时」基线仍是开放问题，样例取可计费。
- D1「四项」：收入确认、开票、回款有三类目标；现金回款（M-12）只有实际，无目标与达成率（该两列为空）。新签合同额无实际事实，省略。
- D1 402 行 / D2 30 行（5 个 BU × 5 个桶 + 5 个 `ALL` 合计行）/ D3 105 行；D2 截至 2026-09 末。

## 2. 校验（`check.py`）

`python3 check.py A B C D`：以 A 为参照，逐格比对（相对容差 1e-9，绝对 1e-6，空值须同为空）。

| 看板 | 行 / 格 | A vs B | A vs C | A vs D |
|---|---|---|---|---|
| D1 经营月报 | 402 / 2814 | 一致 | 一致 | 一致 |
| D2 应收 | 30 / 90 | 一致 | 一致 | 一致 |
| D3 人效 | 105 / 525 | 一致 | 一致 | 一致 |

结论：**全部一致，差异 0**。几点须说明：
- 独立性：A（手写 SQL）与 B（TypeScript 编译器）、C（Java 编译器）是三份独立实现，彼此一致有证明力；B、C 共用同一口径设计，C 直接读 `metrics.yaml`，B 读手抄的 TS 目录。D 的视图 SQL 是从 A 的接口 SQL 抽出（参数改常量），所以 D 与 A 一致不是独立证据，它验证的是 Metabase 读视图、取数、不改数。
- 比对脚本自身做过反向验证：把 A 的 D2 一格改成 1.00，脚本报出 1 处差异（BU-A / 90-180 / overdue），还原后恢复一致。
- D2 的合计行 `ALL` 等于该 BU 各桶之和（SQL 抽查通过）。
- 首轮中途发现并修了一个口径问题（目标最新版本，见 §1），当时 A 与 B 都是错的、彼此却一致——说明「各选项一致」只能证明实现与同一份口径一致，不能证明口径对。

## 3. 选项 A：保留老 BI 写法（`optA/`）

- 实现：Node/Express + `pg`，一个 `ReportController` 类三个静态方法对应三个接口（`/api/report/monthly|receivable|productivity`），每个接口里手写 SQL，不做前端。组织方式仿老 BI `controllers/transction/finance/index.js`（类 + 静态方法 + 方法内 SQL）；老 BI 用 mssql，这里按合成数据用 PG。
- 代码量：`server.js` 133 行（SQL 约 110 行）。
- 耗时：约 3 分钟写成，一次跑通。接口响应 2–24 ms（本机合成数据）。
- 指标定义位置：**多处**。M-08、M-10、M-11 的求和在 D1 与 D3 各写一遍；「预算最新版本」子查询、日标准工时 CASE、账龄桶 CASE 在多处重复（D2 的桶 CASE 在 SELECT 与 GROUP BY 各写一遍）。改一个口径要改多个位置，也没有机制保证改全。
- OAT 声明 / 构建守护：无承载位置；要守护只能另建文件并靠约定，或对 SQL 文本做正则扫描。
- 方言禁用：只能对源码做文本扫描。
- 行级授权：无内建，需在每个接口手工拼 BU 条件。
- 导出 / 下钻 / 移动端：未做（无前端）。
- 与 FORCITIS one 技术栈：不一致（Node/Express，SQL 为 PG 方言写法；老 BI 实际是 mssql，`p.month_end - invoice_date` 这类日期运算不可移植）。
- 许可与运维：无额外许可；一个 Node 进程 + 一个连接池。

## 4. 选项 B：以 BI-olap analytics 为基础（`optB/`）

- 实现：按 `contracts/semantic.ts` 的 dimensionCatalog / factCatalog / metricCatalog 结构写 `src/semantic.ts`（TS，`as const satisfies` 写法），`src/compile.ts` 是「目录 + 请求 → 一条 PG SQL」编译器（加法型指标生成分组子查询，比率型指标递归编译分子分母再按维度键 LEFT JOIN，支持 GROUPING SETS 合计行，参数用枚举校验后代入，枚举校验同时防注入），`src/run.ts` 跑三个看板并写 JSON 与生成的 SQL。
- 代码量：145 行 TS（semantic 68 + compile 43 + run 34）。`tsc --strict` 类型检查通过。
- 耗时：约 5 分钟（一次类型循环引用报错和一次 ESM 配置问题，修后跑通）。三个看板（含 4 次 D1 查询）总计 0.42 s（含 tsx 启动）。
- 指标定义位置：**单一但与共同目录并存两份**——B 的 TS 目录是手抄 `metrics.yaml`，不是读它；现实中若采用 B，TS 目录就是唯一来源，`metrics.yaml` 不存在。D1 在 B 里每个看板的请求也写在 `run.ts` 里。
- 原类型对样例指标的表达能力（对照 `BI-olap/analytics/contracts/semantic.ts` 实际类型）：

| 样例需要 | 原类型 | 结论 | 需要的扩展（本小样 `[EXT]` 标注） |
|---|---|---|---|
| M-08/10/11 求和，指定日期与度量列 | `fact` + `date` + `measure` + `aggregate:'sum'` | 能 | — |
| M-12 现金回款（回款方式 = 现金） | `where:{dimension:'collectionMethod…',in:[..]}` | 能（原目录有回款方式维度） | — |
| M-13/14/15 应收（期末快照、账龄 >90、逾期） | 原 `receivable` 事实是「截至最近一次采集」的当前未结清发票；账龄、逾期是预先算好的带表维度 `receivableAgingBand/OverdueBand`；`beforeDataAsOf`/`allPeriods` 只表示「数据截止日位置」 | **部分**：任意月末历史快照表达不了；账龄起算日、桶边界在仓库里固化，不在目录里 | 快照型事实（按期间保存）+ 期末日参与的派生维度/条件 |
| M-14 与 M-15 两个日期口径（开票日 vs 到期日） | 一个指标一个 `date`；双口径要靠预计算桶 | 间接能（若仓库已有两个桶），无法在目录里声明起算日 | 条件里可引用多个日期列 |
| M-38 平均在册人数 | `aggregate:'sum'\|'count'` | **不能** | `avg` 及半可加规则（跨时间平均、跨组织求和） |
| M-40 目标（类型 × 版本，取最新） | 无版本、无目标类型概念 | **不能**（可把每种类型拆成单独指标，但「最新版本」表达不了） | 参数、版本选择规则 |
| M-41 / M-42 / M-45 比率 | 无派生指标 | **不能** | `ratio` 指标（分子分母各引用指标，分母为 0 时空值），分子可参数化 |
| M-42 日标准工时（默认 7.5、按法人覆盖） | 无参数 | **不能** | 全局参数 + 按维度覆盖 |
| OAT 声明字段 | 只有 `label`、`definition`、`unit` | **不能** | `oat` 对象（10 字段） |
| 目标类型作参数（M-41 / M-45 的分子可选） | 无 | **不能** | 指标参数 + 枚举 |
| 度量为表达式（如 (期初+期末)/2 × 工作日 × 工时） | `measure` 只能是列名 | **不能** | 表达式度量 |

  小样里 `[EXT]` 共 8 处：派生维度、`avg`、表达式度量、SQL 条件、参数、`ratio`、`oat`、全局参数。
- 构建守护：类型层面可做——把 `oat` 设为 MetricDefinition 的必填字段，TS 编译就会拒绝缺字段的指标（小样中已是必填，未另写守护测试）；term 白名单、方言禁用需另写脚本（BI-olap 仓库有 vitest，可承载）。
- 行级授权：原系统有 `access` 表字段（每个事实一张 `*_access`）与 dataset 级授权，本小样未实现，也未验证其细节。
- 导出 / 下钻 / 移动端：本小样未做；原系统有 React + AG Grid 前端，未实测。
- 与 FORCITIS one 技术栈：不一致（Fastify + TS；前端 React 一致）。
- 许可与运维：自有代码；运维含 Node 服务 + PG；目录与编译器要自己维护。

## 5. 选项 C：并入 FORCITIS one 的 reporting（`optC/`，Java 25 已跑通）

- 实现：Maven 项目，Java 25（`maven.compiler.release=25`），纯 JDBC，无 Spring。`Catalog` 读 `catalog/metrics.yaml`（SnakeYAML 2.6），`Compiler` 与 B 同算法，`Main` 读 `dashboards.yaml` 生成 SQL 并经 JDBC 执行，写 `out/C/Dn.json` 与 SQL；`Guard` 是构建守护。
- 守护测试 `CatalogGuardTest`（JUnit 6.0.3，5 个用例，全部通过）：
  1. 真实目录零违规；
  2. 对 10 个 OAT 字段逐个删除，守护均报出对应字段；
  3. term 改为「产值」→ 同时报「不在白名单」与「含禁用方言」；
  4. SQL 写死税率 1.06 → 报错（对应基线 K2）；
  5. 参数值不在枚举（含注入串）→ 编译器抛异常。
  另对真实 `Main` 做了反向演示：复制目录后删 M-45 的 `oat_symbol`、把 M-42 的 term 改成「产工比」，运行退出码 2，输出三条违规。
- 代码量：主代码 235 行（Catalog 36 + Guard 29 + Compiler 103 + Main 68）+ 测试 49 行 = 284 行 Java + pom。
- 耗时：约 6 分钟；`mvn -o`（依赖已在本地仓库）一次通过；三个看板运行 1.2 s（含 JVM 启动）。
- 指标定义位置：**单一**，即 `catalog/metrics.yaml`；SQL 段与 OAT 声明在同一条目里。看板请求在 `dashboards.yaml`。本小样的 yaml 是为演示而设计，与 `operations.policy` 的实际口径版本模型无对应实现。
- 构建守护：**能，且最直接**（目录是数据文件，测试在 `mvn test` 中运行，CI 可阻断）。方言禁用做了两类：词表（`forbidden_dialect`）与 SQL 文本里的写死税率。
- 行级授权：本小样未实现；Java 侧可在编译器里向每个子查询追加 BU 条件（目录层面强制），但未写、未验证。
- 导出 / 下钻 / 移动端：未做（无前端）；本小样输出 JSON。
- 与 FORCITIS one 技术栈：一致（Java 25；未使用 Spring Boot，纯 JDBC 仅为小样简化，并入单体时应改用其数据访问与模块边界）。前端 React 本小样未涉及。
- 许可与运维：自有代码；无新增运行时组件；依赖 PG 驱动、SnakeYAML（Apache 2.0 类许可，未逐项核对）。

## 6. 选项 D：Metabase 开源版（`optD/`）

- 版本与运行：`metabase/metabase:latest` = v0.64.2.2（镜像 1.92GB），`docker run`，127.0.0.1:3102；与 PG 容器同一 docker 网络，用容器名连接。启动到 `/api/health` 正常约 45 s（内置 H2 应用库）。
- 自动化：`optD/mb.py`（11 行 HTTP 小工具）+ `optD/build.py`（37 行）。步骤：`/api/setup` 初始化 → `/api/database` 连接合成 PG（只同步 reporting schema）→ `/api/database/2/sync_schema` → 用 MBQL 建 6 张问题卡（每个看板一张明细表 + 一张图）、1 张 `metric` 类型卡（收入确认）、3 个仪表盘（D1 经营月报 / D2 应收 / D3 人效，各含 2 张卡）→ `/api/card/:id/query` 取数落盘 `out/D/`。`optD/views.sql`（104 行）是三个 reporting 视图，由 A 的接口 SQL 抽出。
- 初始化说明：`/api/setup` 需要创建首个管理员账号。我在这个一次性本地沙箱实例里用随机生成的测试凭据创建了它（凭据只写在一个本地文件，已在收尾时删除，未写入报告），实例随容器删除。这一步属于「创建账号」，若你不允许此类操作请告知。
- 耗时：镜像拉取 + 启动 + 脚本，约 8 分钟；三张明细卡查询各 20–40 ms。
- 结果：D1 402 行 / D2 30 行 / D3 105 行，与 A 逐格一致。导出的 JSON 数值为浮点数，没有出现精度造成的差异。
- 指标定义位置：**多处，且在 Metabase 之外**。本小样中口径在 PG 视图里（SQL）；Metabase 自己还有三处可放定义：question（MBQL 或原生 SQL）、model（可作为数据源）、metric（v0.64 的 `type=metric` 卡片，已创建 1 张，仅创建，未验证其在其他问题中的复用与跨看板口径一致性）。三者相互独立，无「全局指标目录」。
- 能否强制只用共同目录：**不能强制**。实测 All Users 组对该库的权限图为 `create-queries: query-builder-and-native`、`view-data: unrestricted`，任何人可手写原生 SQL 另算口径。只能靠权限收紧（把组降为仅 query-builder、仅开放视图）并把视图当作唯一入口，这是约定而非机制；`metrics.yaml` 与视图之间没有一致性检查，除非另写脚本（例如从目录生成视图 DDL）。
- OAT 声明 / 构建守护：Metabase 的 question / metric 没有 OAT 声明字段，只有名称、描述；守护只能对 API 导出（`/api/card`）另写检查脚本。方言禁用同理。
- 行级授权（按 BU）：开源版**没有**沙箱（`token-features` 全为空）。实测可行的替代：静态嵌入 + 锁定参数——把原生 SQL 问题加上 `{{bu}}` 模板变量，设 `embedding_params:{bu:'locked'}`，用 JWT（HS256）签发；BU-A 的 token 只返回 BU-A 的 21 行，BU-D 同理；用查询串覆盖 BU 被拒（`You can only specify a value for bu in the JWT.`），JWT 缺 bu 也被拒。限制：必须是原生 SQL 问题（MBQL 问题的该方式本次未测）、授权在嵌入层而非用户登录层、需要自己的服务签发 token。另一思路是每个 BU 一套库账号 + 视图，未测。
- 导出：卡片级 CSV（41 768 B）、XLSX（36 487 B）、JSON（91 844 B）三种都可用（`/api/card/:id/query/{csv,xlsx,json}`）。注意导出的列名是展示名，如 `Bu`、`Ach Budget`，与视图列名不同。
- 下钻 / 移动端：未实测（只验证了 API，未做浏览器截图）。
- 与 FORCITIS one 技术栈：不一致（Metabase 是 Clojure/JVM 独立应用，前端自有）；以 iframe / 签名嵌入接入 React。
- 许可与运维（来自通用知识，未逐项核对）：开源版为 AGPL；Pro/Enterprise 另行收费并提供沙箱等功能；应用库默认 H2，生产应换 PG；升级与 Metabase 自身的元数据备份由运维负责。

### Superset（仅文字比较，未运行；以下为通用知识，未实测）
- 定义位置：dataset（物理或虚拟）上的计算列和 metrics，在 dataset 范围内可复用；同样没有跨 dataset 的全局目录。
- 行级授权：开源版内建 Row Level Security 规则（按角色加 SQL 条件，可用 Jinja），比 Metabase 开源版更接近「按 BU」的需求。
- 导出：CSV / Excel / 图片 / PDF（部分需启用）；Alembic 迁移与 Celery、Redis 依赖使运维面更大。
- 技术栈：Python / Flask + React，Apache 2.0。
- 与 Metabase 相同：不能强制只用共同目录，SQL Lab 可写任意 SQL（可用权限关闭）。

## 7. 对照表（只列实测或明确标注的事实）

| 维度 | A 老 BI 写法 | B BI-olap 基础 | C 并入 one | D Metabase |
|---|---|---|---|---|
| 代码量 | 133 行 JS | 145 行 TS | 284 行 Java + pom | 37 + 11 行 Python + 104 行 SQL 视图 |
| 耗时（估算） | ~3 min | ~5 min | ~6 min | ~8 min（含拉镜像） |
| 三看板结果 | 参照 | 一致 | 一致 | 一致 |
| 指标定义位置 | 多处（各接口 SQL） | 单一（TS 目录；需扩展类型） | 单一（yaml） | 多处（视图 + question/model/metric） |
| OAT 声明承载 | 无 | 需扩类型，TS 必填字段可编译期守护 | yaml 字段，`mvn test` 守护（已演示） | 无；需外部脚本 |
| term 白名单 / 方言禁用 | 文本扫描 | 需另写 | 已实现并演示 | 需外部脚本 |
| 行级授权（BU） | 手工拼条件 | 原系统有 access 表（未实测） | 未实现（可在编译器统一追加，未验证） | 开源版无沙箱；锁定参数嵌入可行（实测，限原生 SQL） |
| 导出 | 无 | 未实测 | 无（JSON） | CSV/XLSX/JSON 实测可用 |
| 下钻 / 移动端 | 无 | 未实测 | 无 | 未实测 |
| 技术栈一致（Java 25 / React） | 否 | 否（前端 React 一致） | 是（后端） | 否 |
| 能强制只用共同目录 | 否 | 是（只能走目录；但目录类型需扩展） | 是（只能走目录） | 否（只能靠权限收紧） |
| 许可 / 运维 | 无额外 | 无额外 | 无额外 | AGPL；独立 JVM 应用与应用库（来自通用知识） |

## 8. 遇到的问题

1. 目标「最新版本」口径写错（全局最大版本导致早期预算为空）：由我的第一次运行输出发现，不是由比对脚本发现——因为 A 与 B 当时同样错误。
2. B：`semantic.ts` 风格的类型会循环引用（维度定义引用 FactId，而 FactId 又来自 factCatalog 的 `keyof`），需把 `onlyFacts`、`Ref.metric` 放宽为 `string`；包默认是 CommonJS，顶层 `await` 需改为 `"type":"module"`。
3. Metabase：首次 `/api/setup` 因密码「过于常见」被拒；静态嵌入需先设 `embedding-secret-key` 并在卡片上单独 `PUT enable_embedding`（创建时传入被忽略）；导出需 `application/x-www-form-urlencoded` 的 `parameters` 字段；导出列名用展示名。
4. 未做：任何前端；浏览器截图；下钻、移动端；B 的行级授权与前端；C 的 Spring Boot 集成；Metabase 的 model / metric 复用与 MBQL 问题的嵌入锁参。
5. 本地环境：`BI-olap` 仓库存在一个我未创建的未跟踪目录 `analytics/docs/research/`（只读核对 `git status` 时看到，与本任务无关）。

模型：sonnet
