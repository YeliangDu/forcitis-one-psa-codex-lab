# SECURITY_FINDINGS — psa

日期：2026-10-14 · 源码：`legacy/psa` → `/Users/yeliang/Projects/FORCITIS/psa` · 方式：security-auditor 子代理（命令规程中的兜底路径，未用 Workflow），对抗式审计；Critical / High 项由主会话逐条读代码复核，SEC-001 另外用生产访问日志和安全组规则核实了可利用性。

## 计分

| 严重度 | 数量 |
|---|---|
| Critical | 5（SEC-001、002、003、006 + 依赖中的 4 个 Critical 合并计入 SEC-017） |
| High | 9（SEC-004、005、007、008、009、010、011、012、016） |
| Medium | 7（SEC-013、014、015、017、018、019、020） |
| Low | 6（SEC-021 至 026） |

主要 CWE：CWE-306 未鉴权的关键功能（8 项）· CWE-798/321 硬编码凭据（3 项）· CWE-862/639 越权（2 项）· CWE-79 XSS（2 项）· CWE-1395 有漏洞的依赖（3 项）

**13 个硬编码凭据，清单在 `~/.modernize/psa/SECRETS.local.md`**（仅本机，不外发；工作区不是 git 仓库，所以放在仓外）。

## 覆盖缺口

- 本次未走 Workflow 的五类分路扫描器。注入、鉴权与会话、凭据、依赖 CVE、输入校验这五类由同一个审计代理一次覆盖，因此**没有「误报率」统计**。
- Critical / High 项已由主会话读代码复核：SEC-001、003、005、007 逐行核实，其余采信审计代理读码的结论。
- 后端依赖没有跑 OWASP dependency-check（本机未安装），CVE 是按版本人工对照的；前端用 `pnpm audit --prod`。
- 生产配置以 Nacos 中的为准。审计读的是仓库里的 `application-prod.yaml`，只有 SEC-001、SEC-007 用生产数据核实过。
- 如需补跑分路扫描：`/code-modernization:modernize-harden psa`（Workflow，约 5+N+M 个 agent）。

## 生产实证（只读）

- 网关在生产 ECS 上同时监听 48080 和 48088 两个端口；安全组对 `0.0.0.0/0` 开放 **48088**（规则描述 "PSA direct gateway compatibility"），48080 只对 BI 服务器开放，443 / 80 对公网开放。
- 近 180 天的 `infra_api_access_log`：

  | 端点 | 调用次数 | 最近一次 |
  |---|---|---|
  | `singleSignOn/YD-get` | 15,281 | 2026-09-28 |
  | `customization-login` | 2,371 | 2026-05-29 |
  | `customization-login/id` | 1,397 | **2026-10-13** |
  | `a8-login` | 1,458 | — |

  近 10 天的 nginx 日志里这些路径没有命中，说明调用方是直连网关端口，不经过 nginx。
- `cost/opening`、`cost-correction` 的页面在用（`cost-correction/page` 40 次），但生产 `system_menu` 里**没有** `cost:opening:*` / `cost:correction:*` 权限点。
- `/fst/fix*` 修数端点近 180 天调用次数为 0。

## 发现（按严重度）

| ID | CWE | 严重度 | 位置 | 利用场景 | 修复方向 |
|---|---|---|---|---|---|
| SEC-001 | CWE-288/306 | **Critical** | `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/AuthController.java:88-110,266`；`service/auth/AdminAuthServiceImpl.java:154-216` | 公网向 `POST /admin-api/system/auth/customization-login` 发送 `{"userId":"admin"}`，即可拿到 admin 的真实访问令牌和刷新令牌 | 参照已正确实现的 `seeyonSsoLogin`：服务端校验票据、一次性使用、检查用户状态。过渡期对调用方做签名 + IP 白名单；网络层立即止血 |
| SEC-002 | CWE-306/798 | **Critical** | `AuthController.java:134`；`AdminAuthServiceImpl.java:254-279`；`CommonConstants.java:144` | 匿名调用 `POST /login/KM?memberId=<手机号>`，可换到该用户的 KM 令牌 | 要求已登录，用户从会话中取；key 移到 Nacos 并轮换；改用 HTTPS |
| SEC-003 | CWE-306 | **Critical** | `fst/controller/admin/cost/projectcost/CostOpeningController.java:110-547`；`cost/costsummaryext/CostCorrectionController.java:88-263` | 匿名调用 `POST /admin-api/fst/cost/opening/official/reset`，可重置会计期间的结果 | 按代码里的 TODO 改为 `@PreAuthorize`，新建权限点并给角色授权 |
| SEC-006 | CWE-798/345 | **Critical** | `CommonConstants.java:121-135`；`fst-api/.../FSTConstant.java:127,131`；`WeComCallbackController.java:65-110` | 持有仓库的人可以签发企业微信令牌、伪造审批回调 | 轮换，移到 Nacos；回调中的操作人改为由 `FromUserName` 映射得到 |
| SEC-004 | CWE-306/650 | High | `fst/controller/admin/manhour/{Fix,FixDirect,FixOriginal,FixRequest,ManHourWCheck}Controller.java` | 被爬虫或脚本访问这些 GET 接口，会改写工时费率和成本中心字段 | 去掉 `@PermitAll`，改为运维权限，或改成 XXL-JOB 任务 |
| SEC-005 | CWE-306/639 | High | `bpm/controller/admin/oa/CustomizedBpmController.java:29-34`；`app/oa/CustomizedBpmAPPController.java:29`；`CustomizedBpmOAServiceImpl.java:107,253` | 以任意发起人身份启动审批，并篡改流程变量 | 去掉 `@PermitAll`；HTTP 调用方不允许传 `creator`；变量 key 用白名单 |
| SEC-007 | CWE-306/200 | High | `infra-biz/src/main/resources/application-prod.yaml:11-16` | Druid 控制台可匿名访问：能看到 SQL、会话，还能重置统计 | 生产环境关闭 |
| SEC-008 | CWE-79 | High | `psa-front-desk-pc/src/views/project/weeklyMeeting/detail/index.vue:63`、`create/index.vue:86`；`visualized/index.vue:490` | 在周会内容里注入脚本，窃取查看者的令牌 | 改为文本渲染，或用 DOMPurify 净化 |
| SEC-009 | CWE-862/639 | High | 约 30 个 fst Controller（约 190 个端点）；`fst/framework/datapermission/config/DataPermissionConfiguration.java:16-19` | 任何已登录员工都能读人工成本、执行 WIP 结转 | 补 `@PreAuthorize`，并实现真正的数据权限 |
| SEC-010 | CWE-22/284/79 | High / Medium | `infra/controller/admin/file/FileController.java:46-107`；`LocalFileClient.java:28-48` | 上传时路径穿越；下载接口匿名可访问；可上传 HTML 文件形成 XSS | 文件 key 由服务端生成；下载要求鉴权；非图片类型强制以附件下载 |
| SEC-011 | CWE-521/307 | High | `CommonConstants.java:119`；`AdminUserServiceImpl.java:174`；各 prod yaml 中验证码关闭 | 用默认密码批量尝试员工手机号 | 随机初始密码并强制修改；开启验证码和登录锁定 |
| SEC-012 | CWE-321/798 | High | infra `application.yaml:45`；`yudao-gateway/.../bootstrap.yaml:22` + `ssl/psa.pfx` | 拿到库备份即可解密加密列；TLS 私钥泄露 | 轮换并重新加密；吊销证书，从 git 历史中清除 |
| SEC-016 | CWE-1395（CVE-2024-4367） | High | PC `pdfjs-dist 2.16.105`；`src/utils/pdfToImages.js:19` | 恶意 PDF 发票在预览时执行 JS | 设置 `isEvalSupported:false`，或升级到 4.2.67 及以上 |
| SEC-013 | CWE-89 | Medium | `bpm/dal/mysql/oa/SqlActionMapper.java:15-21` | 拥有模型编辑权限的人，可在 `tableName` 里注入 SQL | 表名白名单 |
| SEC-014 | CWE-306 | Medium | `fst/controller/admin/a8/A8Controller.java:31-36` | 匿名触发向 U8C 推送全部客商 | 去掉 `@PermitAll` |
| SEC-015 | CWE-922/321 | Medium | `psa-front-desk-pc/src/utils/auth.js:54-60`；`jsencrypt.js:5,8` | 「记住我」的密码可逆向解出 | 删除该功能 |
| SEC-017 | CWE-1395 | Medium | PC / H5 `package.json` | axios 0.27.2、crypto-js、vxe-table、swiper（Critical）等 | 升级 |
| SEC-018 | CWE-1395 | Medium | `psa-backend/yudao-dependencies/pom.xml:19-82` | Spring 5.3.24、Tomcat 9.0.70、Netty、hutool、redisson、org.json 等的已知 CVE | 先升到 Boot 2.7.18 并覆盖相关依赖版本 |
| SEC-019 | CWE-598 | Medium | `AuthController.java:110,127,139,254,272` | 令牌放在重定向 URL 中，会进入日志和 Referer | 改用一次性 code 交换 |
| SEC-020 | CWE-290 | Medium | `TokenAuthenticationFilter.java:44-45,139-141`；网关 `TokenAuthenticationFilter.java:88,103-106` | 各模块完全信任请求头 `login-user`，模块端口一旦可达就能伪造身份 | 对该头做签名，或不再信任；模块端口只对网关开放 |
| SEC-021–026 | 多项 | Low | 见审计原文 | 应用侧令牌可访问管理端接口；日志打印令牌；本地 profile 的 mock 登录；匿名读取 BPMN；`WrapperSqlUtil` 潜在注入；SBA 匿名访问 | 见各项 |

### 依赖 CVE

| 组件 | 当前版本 | CVE / 通告 | 修复版本 |
|---|---|---|---|
| pdfjs-dist（PC） | 2.16.105 | CVE-2024-4367 | ≥ 4.2.67 |
| axios（PC / H5） | 0.27.2 | GHSA-jr5f-v2jv-69x6 等 | ≥ 1.x 最新版 |
| crypto-js（PC） | < 4.2.0 | GHSA-xwcq-pm8m-c4vf | ≥ 4.2.0 |
| swiper（H5） | 旧版 | GHSA-hmx5-qpq5-p643（Critical） | 最新版 |
| Spring Framework | 5.3.24 | CVE-2023-20860/20861/20863、CVE-2024-22243/22259/22262、CVE-2024-38809 | 5.3.39+（Boot 2.7.18 + 覆盖）/ 迁移 |
| Spring Security | 5.7.6 | CVE-2024-22257 | 5.7.12+ |
| Tomcat | 9.0.70 | CVE-2023-44487、CVE-2023-46589、CVE-2023-41080、CVE-2025-24813 | 9.0.98+ |
| Netty | 4.1.86 | CVE-2023-34462、CVE-2024-29025 | 4.1.108+ |
| SnakeYAML | 1.30 | CVE-2022-1471（未发现 sink） | 2.x |
| hutool | 5.8.11 | CVE-2023-24162/24163、CVE-2022-45688、CVE-2023-51080 | ≥ 5.8.26 |
| redisson | 3.18.0 | CVE-2023-42809 | ≥ 3.22 |
| org.json | 20200518 | CVE-2022-45688、CVE-2023-5072（可经企业微信回调触达） | ≥ 20231013 |
| commons-net / jsch / okio / guava | 3.8.0 / 0.1.55 / 3.0.0 / 31.1 | CVE-2021-37533 / CVE-2023-48795 / CVE-2023-3635 / CVE-2023-2976 | 3.9+ / mwiede 版 jsch / 3.4+ / 32.0.1+ |

pnpm audit 汇总：PC 端 critical 4 / high 45 / moderate 42；H5 端 critical 4 / high 49 / moderate 39。

## Remediation Log

补丁：`security_remediation.patch`（可共享，30 个文件）、`~/.modernize/psa/security_remediation.local.patch`（凭据部分，仅本机，权限 600）。说明见 `security_remediation.notes.md`。**两份补丁都没有应用到 psa 仓库**（本轮约定只分析）。

| ID | 修复摘要 | 所在补丁 |
|---|---|---|
| SEC-001 | 四个免密端点要求调用方 HMAC 签名、时间戳、一次性 nonce；按调用方配置可用端点；过渡期未签名通道只放行白名单 IP，且 a8-login 和 YD-get 不允许未签名；拒绝停用用户和超级管理员 | 可共享 |
| SEC-002 | `/login/KM` 必须已登录，令牌签给当前登录用户；KM 的调用和跳转都改为 https | 可共享 + 凭据补丁 |
| SEC-003 | 54 个 `@PermitAll` 改为 `@PreAuthorize`；Flyway 脚本新建权限点，并按「已有页面权限」授权角色 | 可共享 |
| SEC-004 | 修数端点要求 `fst:manhour:fix`，不建菜单，只有超级管理员可用 | 可共享 |
| SEC-005 | `customized/create` 要求登录；忽略请求中的 creator 和流程变量；processKey 白名单；之后整体关闭 HTTP 入口 | 可共享 |
| SEC-006 | 硬编码的企业微信、KM 密钥和默认密码改为从 Nacos 注入；企业微信回调改用 `FromUserName` 映射操作人 | 可共享 + 凭据补丁 |
| SEC-007 | 4 个 prod 配置关闭 Druid 控制台 | 可共享 |
| SEC-008 | 周会富文本经白名单净化；可视化页面提示框改为 `textContent` | 可共享 |
| SEC-012 | 列加密密钥和 keystore 口令改由环境变量 / Nacos 提供 | 凭据补丁 |
| SEC-014 | `/a8/test` 要求运维权限 | 可共享 |
| SEC-016 | pdf.js 设置 `isEvalSupported: false` | 可共享 |
| 人工处理项 | 发布前确认没有菜单还指向老工时详情 / 新建页（`project/timesheet/detail`、`create.vue` 仍调用 `createWorkflow`，补丁后会 403）；部署顺序为先网关后 system；A8 / YD 调用方先改为签名；轮换全部泄露凭据（含 SEC-106）；清理 git 历史（包括 `docs/bugfix/PSA 项目现状痛点清单.md`）；从仓库删除 `ssl/psa.pfx` 并重签证书 | — |

## Patch Review

| 轮次 | 结果 |
|---|---|
| 第 1 轮 | 7 组 RESOLVES，5 组 PARTIAL，1 组 INTRODUCES-RISK：SEC-003 的 Flyway 脚本没有授权角色，且锚点菜单查不到时会抛错导致 fst 无法启动 |
| 第 2 轮 | SEC-002、SEC-006/012 可共享部分 RESOLVES。SEC-001 仍为 PARTIAL：按代理跳数判断来源可被伪造（SEC-101），且超级管理员拒绝误伤企业微信登录（SEC-102）。SEC-003 PARTIAL：漏授权两个在用操作（SEC-103）。SEC-005 PARTIAL：存在 paramsId 越权（SEC-104）。另发现仓库文档中残留明文密钥（SEC-105）。**没有新增风险** |
| 第 3 轮修订 | SEC-101：网关新增 `ClientIpHeaderFilter`，丢弃客户端自带的 `X-PSA-Client-IP` 并按真实来源重写；`TrustedCallerVerifier` 不再读 `X-Forwarded-For`，删除 `trusted-proxy-hops`。SEC-102：超级管理员拒绝只作用于 4 个外部端点（`externalCallerLogin`），企业微信登录不受影响；a8 / social 跳转改为 https。SEC-103：`/official/reset` 拆出 `cost:opening:official-reset`，按页面权限分档授权；在临时 SQL Server（兼容级别 130）上重跑：8 个角色、7 个按钮、16 条授权，二次执行不重复插入。SEC-104：两个 HTTP `createOA` 要求一个不授予任何角色的权限，等同关闭（生产最后一次调用分别是 2026-05-07 和 2026-04-30）。SEC-105：凭据补丁屏蔽文档中的密钥（含第 833 行的企业微信应用密钥），并把该文档列入清理历史清单 |
| 第 3 轮复审 | SEC-102、103、105 RESOLVES；SEC-104 RESOLVES（超级管理员仍可调用）；SEC-101 PARTIAL：网关过滤器本身正确，但部署链路上还有 SEC-107、SEC-108 |
| 新发现 SEC-107（High，CWE-288） | nginx 的 `/other-api/` 直通 system 服务 48081，绕过网关：`/rpc-api/system/**` 完全放行（`SecurityConfiguration.java:34`），`oauth2/token/create` 可以为任意用户（含超级管理员）签发令牌；伪造 `login-user` 头也可以访问 `/admin-api/system/**`。**生产只读核实**：`/etc/nginx3/conf.d/psa.conf` 的 443 server 中确有 `location ^~ /other-api/ → 127.0.0.1:48081`，但只允许 2 个固定公网 IP 和本机访问；安全组没有开放 48081。因此不是公网可利用，但这 2 个 IP 背后的任何人都能拿到超级管理员令牌，故定为 High（代码层面为 Critical）。该 nginx 的 access_log 是关闭的，无法判断是否被利用过。修复：删除 `/other-api` location（Druid 已关闭，没有正当用途）；system 侧用 `InternalRpcTokenFilter` 保护整个 `/rpc-api/**`，只在 `remoteAddr` 是网关时才信任 `login-user` 头。**列为发布阻断项** |
| 新发现 SEC-108（Medium） | 48088 是 nginx 反代到网关，网关看到的对端恒为 127.0.0.1，按 IP 区分调用方的白名单失效；运维若把 127.0.0.1 加入白名单，未签名通道就对公网开放。修复：网关只在对端是前置代理时采用 nginx 写入的 `X-Real-IP`；`legacy-allowed-ips` 出现回环或网关地址时启动报错；更正 notes 第 88 行 |
| 新发现 SEC-109、110（Low） | location 内自带 `proxy_set_header` 时不继承 server 级的同类指令，清空 `X-PSA-Client-IP` 的指令必须写进每个 location；网关地址比较应先用 `InetAddress` 规整（`::1` 与 `0:0:0:0:0:0:0:1`） |
| 第 4 轮修订 | SEC-107：删除 nginx `/other-api`（含仓库 `psa-pc.conf` 与上线手册）；system 的 `InternalRpcTokenFilter` 保护整个 `/rpc-api/**`（`MessageDigest.isEqual`，令牌未配置时一律 401）；新建 rpc 自动配置，全局 Feign 拦截器给 `/rpc-api/` 请求附内部令牌（全仓 28 个 Feign 客户端全部覆盖）；网关令牌校验调用补令牌头；新增网关 `InternalTokenRelayFilter`：删除客户端自带的令牌头和 `login-user`，对含 `/rpc-api` 的外部路径返回 404；框架只在内部令牌有效时采信 `login-user`。**顺带修复存量问题**：网关丢弃了 `removeLoginUser` 的返回值，客户端伪造的 `login-user` 一直被透传。SEC-108：新增 `psa.gateway.trusted-proxies`，只信任来自前置代理的 `X-Real-IP`；`legacy-allowed-ips` 含回环或网关地址时报错并忽略。SEC-109：每个 location 单独清空 `X-PSA-Client-IP`。SEC-110：地址先用 `InetAddress` 规整再比较。验证：可共享补丁 59 个文件，`git apply --check` 通过；五个服务模块与框架 JDK 8 编译通过；新增和改动的 8 个单测通过；可共享补丁泄密扫描 0 命中。**运行时行为未在 dev 实测** |
| 第 4 轮复审 | SEC-107 RESOLVES（代码层面：外部路径穿越、编码绕过均被拦截；28 个 Feign 全覆盖，内部令牌不会发往第三方）；SEC-101、108、110 RESOLVES；SEC-109 PARTIAL（仓库 `psa-h5.conf` 的 `/api/` 未清空 `X-PSA-Client-IP`，网关已删除该头，仅属纵深防御）。新问题：SEC-112（Medium）网关信任来自 127.0.0.1 的 `X-Real-IP`，前提是每个转发到网关的 nginx location 都改写该头；SEC-113（Low）内部令牌只在启动时读取，轮换需五个服务同步重启，且网关给所有转发请求都附令牌 |
| SEC-112 生产核实（只读） | 生产 nginx（`/etc/nginx3/conf.d/psa.conf`）中转发到网关的 6 个 location 全部设置了 `X-Real-IP`，**当前满足前提**；保留为上线检查项。同时更正一个事实：**48088 不是 nginx，而是网关进程自身的 TLS 端口**（与 48080 同一进程），且对 0.0.0.0/0 开放；直连 48088 的请求对端就是真实客户端 IP，不在 `trusted-proxies` 内，伪造的 `X-Real-IP` 不会被采信。notes 与上线手册中「48088 是 nginx」的描述需更正 |
| 第 5 轮修订 | SEC-109：仓库内两处转发到网关的 nginx 配置都设置 `X-Real-IP` 并清空 `X-PSA-Client-IP`。SEC-111：内部令牌过滤器移入 security starter 的通用自动配置，system、infra、bpm、fst 四个服务共用；三服务的 10 个 Feign 客户端都由全局拦截器附令牌。SEC-113：校验方同时接受新旧两个令牌、发送方只发新值；网关只在写入 `login-user` 时附令牌（当前从不写，转发请求不再带令牌）；调 system 校验令牌的 WebClient 仍带令牌；令牌轮换采用三阶段（先让所有服务接受新值 → 切换发送值 → 删除旧值），全程不出 401。48088 的描述已在 notes 和上线手册中更正，手册新增 SEC-112 与内部令牌两条检查项。验证：`git apply --check` 通过；JDK 8 编译五个服务与框架通过；16 个相关单测通过；泄密扫描 0 命中。**运行时未在 dev 实测** |
| 第 5 轮复审 | SEC-109、111、113 RESOLVES：四个服务都启用内部令牌过滤器，网关不会误加载；28 个 Feign 客户端全覆盖，XXL-JOB、MQ 等非 HTTP 线程发起的调用同样带令牌；三阶段轮换推演无 401（前提：每阶段五个服务全部重启完再进入下一阶段）。新问题：SEC-114（Low）过滤器顺序排在 Spring Security 之后，外部请求可先触发一次跨服务令牌校验，修复为提前到追踪过滤器之后；SEC-115（Low，存量）`SocialUserApi` 路径缺 `PREFIX`，暴露在 `/bind`、`/unbind` 等根路径，不受内部令牌保护。另：令牌未配置时全平台登录校验会失败（失败即拒绝），已在部署顺序中说明 |
| 第 6 轮修订 | SEC-114：新增常量 `INTERNAL_RPC_TOKEN_FILTER = ENV_TAG_FILTER + 1`（避免与 `ENV_TAG_FILTER` 同序），内部令牌过滤器排在 Spring Security 与租户过滤器之前，单测按 Spring Boot 的实际排序方式验证。SEC-115：`SocialUserApi` 4 个映射补上 `PREFIX`，落到 `/rpc-api/system/social-user/...`；全仓 28 个 Feign 接口逐一检查，只有这一处。验证：`git apply --check` 通过（可共享补丁 67 个文件）；JDK 8 编译五个服务与框架通过；19 个相关单测通过；泄密扫描 0 命中。两处均为低危、改动小且有单测覆盖，不再单独复审；**真实 Tomcat 中的过滤器顺序与整套补丁的运行时行为须在 dev 实测**（notes 中的 dev 实测清单） |
| 当前结论 | 代码层面已知问题全部有修复；剩余是运维与人工事项（Nacos 内部令牌、部署顺序、nginx 删除 `/other-api`、凭据轮换与历史清理、dev 实测） |
| SEC-111（已决定） | infra、bpm、fst 的 `/rpc-api` 同样由内部令牌保护（用户 2026-10-14：「SEC-111 也加内部令牌保护」），与 system 共用一套框架级过滤器，第 5 轮实现 |
| 补丁之外的处置 | 生产 access_log 关闭，无法排除 SEC-107 已被利用：建议轮换内部令牌、吊销超级管理员的在用令牌；Druid 前端页面随 `/other-api` 删除失效；差旅契约文档中「令牌只注入快照接口」的表述需更新 |
| 新发现（未修补） | SEC-106：`justauth.type.WECHAT_ENTERPRISE.client-secret`、`DINGTALK.client-secret` 硬编码在 system-biz、fst-biz 的 `application-{local,dev,prod}.yaml`（6 个文件），企业微信那一条与文档第 833 行是同一个密钥。处理：轮换，改由 Nacos 注入，列入清理历史清单 |

两份补丁每一轮都通过了 `patch --dry-run` 和 `git apply --check`；打到临时副本后，用 JDK 8 离线编译通过；可共享补丁和说明文档的泄密扫描均为 0 命中。
