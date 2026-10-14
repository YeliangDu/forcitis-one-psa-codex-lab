# ASSESSMENT — FORCITIS one

日期：2026-10-14 · 源码：`legacy/psa` → `/Users/yeliang/Projects/FORCITIS/psa`（软链）· 运行时数据：生产库只读探查（`runtime/prod_usage.tsv`）+ 生产 ECS 与安全组配置

## Executive Summary

老系统是 FORCITIS 的项目型业务运营系统，覆盖报价、合同、项目交付、工时、项目成本核算、差旅报销和审批。它基于 yudao（ruoyi-vue-pro）微服务底座：Java 8 / Spring Boot 2.7 / Spring Cloud Alibaba，SQL Server 2016，Flowable 6.8，前端为 Vue 2.7 PC 端和 Taro React H5。主代码约 **40 万行**（后端 Java 24 万行），但生产实际使用面明显更小：近 180 天 **335 个活跃用户、约 176 万次 API 调用，只用到 817 个端点中的 405 个（50%）**，启用并授权的页面只有 89 个。

整体风险**高**，主要来自三方面：
- **生产安全漏洞**：存在公网可利用的免密登录（SEC-001），且密钥明文入库。
- **技术栈全线过保**：Java 8、Boot 2.7、Vue 2 均已停止维护。
- **核心业务规则缺少保护**：成本、交付、工时、报价四个域相互 import，耦合成一团；规则集中在没有测试的老 service 层超大类里。

建议采用 **Rearchitect（重构架构，按域绞杀）**：在模块化单体的新架构上按业务域逐块重建，老系统在迁移期间持续运行，执行命令是 `reimagine`。第一步不是写代码，而是先修复 SEC-001 到 SEC-006 这几项生产安全问题。

## System Inventory

| 维度 | 事实 | 证据 |
|---|---|---|
| 主代码 | Java 239,575 行（4,308 个文件）；Vue 94,382；SQL 23,100；TS 16,339；JS 15,639；Sass/CSS 12,299（scc 统计，不含测试、生成物和 node_modules） | scc |
| 测试 | 后端 740 个测试类（fst 618 个，测试代码约 14.2 万行，集中在新的 DDD 分层）；PC 113 个 spec、H5 19 个；Playwright E2E 套件 | `find`；技术债报告 |
| 运行进程 | 5 个 Spring Boot 服务：gateway、system、infra、bpm、fst；外加 Nacos、Redis、RocketMQ 4.9.7、XXL-JOB 2.4.0（docker）。**全部部署在同一台 15G 内存的 ECS 上**，fst 堆内存 1G，其余 0.5–1G | 生产 `ps` / `docker ps` |
| 构建 | Maven 多模块（CI 可用时走 mvnd）；pnpm（PC，Node 16 + node-sass 6）；Taro 4.2（H5，Node 20）；GitHub Actions：pr-gate / pr-hygiene / cd-dev / cd-pre / cd-prod | `.github/workflows` |
| 数据 | SQL Server 2016（RDS `ruoyi-vue-pro` 库）。370 张表，236 张有数据，共 3,524 万行，最大的是 Flowable 历史表和成本 / WIP 明细表；第二数据源直连 A8 OA 库（`a8_db.dbo.*`，30 处跨库查询） | 生产 `sys.partitions`；技术债报告 |
| 接口 | 175 个 Controller，817 个端点（静态统计）；SQL 写在 528 个 MyBatis 注解里（只有 1 个 mapper XML）；28 个 Feign 客户端 | grep；`live_code.py` |
| 异步 | RocketMQ（Spring Cloud Stream）：bpm 发出 4 个审批主题，system 发组织变更，fst 有 4 个成本批处理主题；27 个 XXL-JOB 处理器 | 领域分析报告 |
| 外部集成 | 分贝通（差旅，HTTP 出站 + webhook 入站）、用友 U8C（凭证、客商）、致远 A8（直接读库 + SSO）、企业微信（OAuth、消息、回调）、KM 门户（SSO）、BI（直接读老系统库，并通过 48080 端口做 SSO） | 领域分析报告；安全组规则 |
| 版本 | Boot 2.7.7（2023-11 已停止 OSS 支持）、Spring Cloud 2021.0.5、Flowable 6.8.0、MyBatis-Plus 3.5.3.1、XXL-JOB 2.3.1、Vue 2.7.0（2023-12 EOL）、Element UI 2.15.10、axios 0.27.2 | poms；package.json |

## Architecture at a Glance

领域图见 `ARCHITECTURE.mmd`（边上的数字是 Java import 次数）。

| # | 域 | 后端主要位置 | 后端行数 | 生产使用（180 天调用量 / 端点使用率） |
|---|---|---|---|---|
| 1 | 售前：CRM、商机、报价 | `fst/{domain,application,infrastructure}/presales`, `controller/admin/{opportunity,quotation,customer}` | 1.3 万 | 7.6 千次；报价 14/19 |
| 2 | 合同与回款 | `fst/service/contract`, `{application,infrastructure}/receivables` | 0.4 万 | 很低（1 个端点） |
| 3 | 项目交付（台账、WBS、预算、交付物、周会）+ 必捷巡检 | `fst/{…}/delivery/project`, `controller/admin/{pmproject,project,projecttask,…}` | 3.7 万 | 18.6 万次；pmproject 37/48，任务 15/18 |
| 4 | 工时 | `fst/{controller,service,dal}/manhour`, `calendar` | 1.4 万 | **37.6 万次**（第二大流量）；29/55 |
| 5 | 项目成本与核算（归集、OH 分摊、结转 / WIP、凭证 → U8C） | `fst/{…}/cost/projectcost/*`（约 20 个子 BC） | **8.8 万** | 9.2 千次 API；52/156；主要由定时任务和 MQ 批处理驱动 |
| 6 | 差旅报销（分贝通） | `fst/{…}/expense/travel/*` | **8.0 万** | 3.8 千次；28/58（2026 年新上线） |
| 7 | 审批（Flowable） | `yudao-module-bpm` + fst 的 workflowhook / workflowform / 审批消费者 | 2.1 万 | **32.4 万次**；task 30/34 |
| 8 | 身份、组织、A8 / 企业微信集成 | `yudao-module-system` + fst 的 `service/{a8,sync,dept,user}` | 3.7 万 | 70.3 万次（主要是登录和字典） |
| 9 | 平台：可靠投递发件箱、枚举注册表 | `fst/{application,infrastructure}/platform` | 0.4 万 | 由定时任务驱动 |
| 10 | Infra（文件、代码生成、配置、日志） | `yudao-module-infra` | 0.7 万 | 9.0 万次（文件上传 / 下载） |

结构性事实：
- **fst 里两代架构并存**：
  - 新的 `application` / `domain` / `infrastructure` 共 16.4 万行，有测试；
  - 老的 `service` / `dal` 共 4.2 万行，冻结，基本没有测试。
  - 新代码反向依赖老代码：100 个文件 import 了老的 `dal` / `service`。
- **服务边界在数据层不成立**：
  - 所有服务共用一个 SQL Server 库；
  - fst 直接映射 system / bpm 的表；
  - fst 的 Flyway 替 bpm 建表；
  - bpm 通过动态表名直接写业务表。
- **fst 与 bpm 存在构建期和运行期的循环依赖**：两边的 Feign 调用互相指向对方。

## Production Runtime Profile

数据来源：生产 `infra_api_access_log`（2024-08 至今共 490 万行，本表取近 180 天）、`system_menu`、`sys.partitions`。

| 域 | 调用量（180 天） | 失败数 | 失败率 |
|---|---|---|---|
| 身份：登录鉴权 | 422,116 | 26,732 | **6.33%** |
| 工时 | 376,422 | 7,513 | **2.00%** |
| 审批（BPM） | 323,867 | 719 | 0.22% |
| 身份与组织（字典、用户、部门） | 280,962 | 63 | 0.02% |
| 交付：任务 | 154,859 | 68 | 0.04% |
| Infra（文件） | 89,874 | 86 | 0.10% |
| 交付：项目 | 31,409 | 124 | 0.39% |
| 成本与核算 | 9,155 | 56 | 0.61% |
| 售前 | 7,577 | 3 | 0.04% |
| 差旅报销 | 3,827 | 48 | 1.25% |

- **流量重心**：工时填报、任务、审批、登录这几块占了 75% 以上的调用。它们是用户每天都会碰到的路径，切换时风险最高。
- **运维风险最大的域是工时**：流量第二、失败率 2%，代码也是全仓最复杂的（`ManHourServiceImpl` 3,085 行、44 个依赖注入），并且没有测试。
- 耗时字段 `duration` 大多为 0，单位存疑，**本表不采用延迟数据**；要做延迟画像需接 SkyWalking 或网关日志。
- **在用面**：
  - 817 个端点中，405 个在近 180 天被调用过；
  - 144 个 Controller 中，34 个完全没有被调用（3,018 行，主要是 yudao 自带的短信 / 邮件 / 站内信 / OAuth2 / Redis 监控、修数工具和示例代码）；
  - 470 个菜单中，89 个页面处于启用且已授权状态。
- **静态可达性口径**（从入口点按类级 import 链计算）几乎没有缩减（约 19 万行）。这说明代码耦合过密，**范围应按功能（端点、页面、规则）界定，不能按行数界定**。

## Technical Debt（价值排序前 10）

1. **密钥明文入库**：生产的企业微信 secret、回调 AES key、KM key、默认密码、TLS 私钥、MyBatis 加密 key 都在代码里。清单见 SECRETS.local.md，例如 `CommonConstants.java:128-146`、`gateway/bootstrap.yaml:22`。
2. **84 个 `@PermitAll` 端点可以匿名改数据**：`CostOpeningController.java`（39 个）、`CostCorrectionController.java`，以及 `controller/admin/manhour/Fix*Controller.java` 的修数 GET 接口。
3. **SQL Server 方言锁定**：
   - 105 个 Java 文件含方言写法：ISNULL 121 处、TOP 84 处、锁提示 56 处、CONVERT 64 处、OUTER APPLY、MERGE … HOLDLOCK；
   - 30 处跨库查询 `a8_db.dbo.*`；
   - 300 个 Flyway 脚本和 5.3 万行 `dbo.sql` 基线是 SQL Server DDL。
4. **老 service 层的超大类没有测试**：`ManHourServiceImpl`（3,085 行）、`ResourceServiceImpl`（1,877 行）、`ProjectServiceImpl`（1,751 行）、`WipServiceImpl`（1,660 行）、`TaskServiceImpl`（1,597 行）。
5. **WIP 结转的事务边界有缺陷**：`WipServiceImpl.java:1030-1066` 没有加事务，且通过 `this.` 自调用绕过了代理事务；`:283-291` 用字符串拼 SQL 做全表 UPDATE。
6. **状态常量多源**：`FSTConstant.java` 有 56 个可变的内部类、被引用 763 次；状态 ID 在前端、数据库、后端三处各手抄一份；`enums.registry` 只迁移了 18 个文件。
7. **两代架构并存且边界泄漏**：新 application / infrastructure 层 import 老 `dal` / `service`（100 个文件）；bpm 直接写业务表（`SqlActionMapper.java:15-21`）。
8. **平台全线 EOL**：838 个文件 import `javax.*`，760 个文件用 Swagger 2 注解；网关内嵌了自己改过的 Spring 类；前端有 841 处只适用于 Vue 2 的 API；node-sass 把 CI 锁死在 Node 16。
9. **吞异常**：65 个空 catch，72 个 catch 后直接返回 null，49 处 `System.out`；MQ 生产者发送失败时静默丢弃。
10. **yudao 模板残留与死代码**：fst 里未被引用的 producer 副本、pay starter、示例 Controller、demo job、54 处 `TODO 芋艿`。

## Security Findings

完整的 CWE 表、证据和修复建议见安全审计结果（后续由 `harden` 落成 `SECURITY_FINDINGS.md` 并附补丁）。Critical 和 High 项如下：

| ID | CWE | 严重度 | 位置 | 问题 |
|---|---|---|---|---|
| SEC-001 | CWE-288/306 | **Critical（生产已核实）** | `system/controller/admin/auth/AuthController.java:88-110`、`AdminAuthServiceImpl.java:154-216` | `customization-login`、`/id`、`a8-login`、`singleSignOn/YD-get` 不校验凭证，凭用户名或 ID 签发令牌。生产安全组对 0.0.0.0/0 开放网关端口 48088，近 180 天这些端点被调用约 2 万次，有合法调用方依赖 |
| SEC-002 | CWE-306/798 | Critical | `AuthController.java:134` | 匿名可为任意用户换取 KM 令牌（硬编码 key，走 http） |
| SEC-003 | CWE-306 | Critical | `fst/controller/admin/cost/projectcost/CostOpeningController.java:110-547`、`CostCorrectionController.java:88-263` | 匿名调用者可执行或重置期初、结转等财务流程 |
| SEC-006 | CWE-798/345 | Critical | `CommonConstants.java:121-135`、`FSTConstant.java:127-131`、`WeComCallbackController.java:65-110` | 生产企业微信 secret 和回调密钥入库；回调信任 `EventKey` 中携带的用户身份 |
| SEC-004 | CWE-306/650 | High | `controller/admin/manhour/Fix*Controller.java` | 约 25 个匿名 GET 接口会改写工时数据 |
| SEC-005 | CWE-306/639 | High | `bpm/.../CustomizedBpmController.java:29-34` | 匿名可以任意身份发起审批，并篡改流程变量 |
| SEC-007 | CWE-306/200 | High | `infra/application-prod.yaml:11-16` | Druid 监控台匿名可访问 |
| SEC-008 | CWE-79 | High | `views/project/weeklyMeeting/*/index.vue`（`v-html`）| 存储型 XSS；服务端 XSS 过滤关闭，令牌存放在 localStorage |
| SEC-009 | CWE-862/639 | High | 约 30 个 fst Controller、约 190 个端点 | 没有功能级和行级授权，fst 的数据权限配置为空实现 |
| SEC-010 | CWE-22/284/79 | High（视文件存储类型） | `infra/controller/admin/file/FileController.java` | 上传路径可由客户端控制（路径穿越）；下载接口匿名可访问 |
| SEC-011 | CWE-521/307 | High | `CommonConstants.java:119`、各 prod yaml | 默认密码不强制修改，生产关闭验证码，没有登录锁定 |
| SEC-012 | CWE-321/798 | High | infra `application.yaml:45`、`gateway/bootstrap.yaml:22` + `ssl/psa.pfx` | 列加密密钥和 TLS 私钥入库 |
| SEC-016 | CWE-1395 | High | PC `pdfjs-dist 2.16.105` | CVE-2024-4367：恶意 PDF 发票在预览时可执行 JS |

另有 Medium 7 项、Low 6 项。依赖审计结果：PC 端 4 个 Critical、45 个 High；H5 端 4 个 Critical、49 个 High（pnpm audit）。后端为 Spring 5.3.24、Tomcat 9.0.70、Netty、hutool 等已知 CVE。

13 个硬编码凭据的清单在 `~/.modernize/psa/SECRETS.local.md`（工作区不是 git 仓库，所以放在仓外；仅本机可见，不外发）。

## Documentation Gaps（前 5）

仓库已有较完整的 DDD 战略设计（`docs/domain/00_战略设计`：12 个子域、23 个限界上下文、上下文映射、全域业务规则清单），还有报价、核算、交付、采购、总账的域文档，以及 openspec 的 25 项成本规格。缺口如下：

1. **工时域**：流量最大、失败率最高，代码也最复杂，但没有域文档（规则、状态机、费率与人天计算），实现全在 `ManHourServiceImpl` 里。
2. **审批定制层**：`CustomizedBpmOAServiceImpl` 按表名回写、workflowhook / workflowform 双向 Feign、4 个 MQ 主题的语义和幂等保证，都只能从代码推断。
3. **外部 SSO 契约**：BI、A8、KM 依赖的 `customization-login` / `singleSignOn` 调用方、调用频率和预期安全模型没有文档。这是修复 SEC-001 的前置条件。
4. **WIP / 结转运行手册**：期间状态（例如「结转中」）卡住后如何恢复、批处理 MQ 加锁的语义，没有运维文档。
5. **数据所有权**：哪个服务拥有哪张表、BI 读哪些表、`psa_crm_*` 和 `psa_biget_*` 由谁写入（老系统内部只读），都没有文档。

## Relative Scale

- **主代码 404 KSLOC，相对规模指数 2,166**（`2.94 × KSLOC^1.10`）；若按 scc 全仓口径计算，指数为 3,902。
- 该指数**只用于和其他系统横向排序，不代表工期，也不代表成本**。
- 按生产在用面折算：端点使用率约 50%，启用页面 89 个。实际需要重建的功能面明显小于行数所示。

## Recommended Modernization Pattern

**Rearchitect（重构架构）→ `/code-modernization:modernize-reimagine psa`，按业务域绞杀式切换。**

理由：
- **目标与现状在每个维度上都不同**：
  - 部署形态：5 个微服务 → 模块化单体；
  - 数据库：SQL Server → PostgreSQL，「一 BC 一 schema」；
  - 持久化：MyBatis 注解 SQL → Spring Data JDBC；
  - 异步机制：RocketMQ / Feign → Modulith 事件发件箱；
  - 运行时：Java 8 / Boot 2 → Java 25 / Boot 4；
  - 前端：Vue 2 → 现代前端。
  - 838 个文件依赖 `javax`、105 个文件含方言 SQL、业务域之间互相 import，这已经超出 same-stack uplift 的「最小 diff」能承受的范围。逐模块 transform 又会把现在混乱的边界原样搬过去。
- **用户要求「功能复刻但是行为要现代化」、顺手修已知 bug**：行为契约应该从抽取出的业务规则重建，而不是照搬旧实现。
- **用户要求「老系统迁移期间持续运行」**：按域逐块切换，按依赖从少到多推进：先平台、身份、审批底座，再报价 / 交付 / 工时，最后核算。新旧系统靠只读同步、防腐层和发件箱桥接。
- **单机部署和 335 个用户的规模**说明：微服务只带来了运维成本，没有带来伸缩收益。模块化单体是合适的规模，这一点与目标栈 ADR-0001 一致。
- **前置条件**：先修复生产安全问题 SEC-001 到 SEC-006（`harden`）；先把工时、WIP 结转、成本核算的 P0 规则抽取成规则卡（`extract-rules`）。

最终目标栈将在 `brief` 中逐项论证后确定（ADR-0001 作为参照，不作为预设结论）。
