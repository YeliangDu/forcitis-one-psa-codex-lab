# Phase 1a 平台约定方案（6 项）

日期：2026-10-14 · 起草：psa-1a-platform-bridge-reimagine · 用户决定：「全部在 1a 做 (Recommended)」。

> **已升格，以下各节不再是现行规则**（正文保留，只作出处）：
> - §1 ID 策略 → `adr/src/ADR-0033.md`（自有标识与来源对照，用户 2026-10-15 已批准）；对外 ID 为字符串见 `NAMING_STANDARD.md` §2.6 A-7。
> - §2 时间与时区、§3 API 规范 → `NAMING_STANDARD.md` §2.6 HTTP 接口约定（用户 2026-10-15 对原则核对 A 答「全部按推荐 (Recommended)」）。其中 API 时刻改为 UTC 带 `Z`，取代 §2 末条的 `+08:00` 写法；业务日计算按 `Asia/Shanghai` 不变。
> - §4 可观测性 → `GUIDING_PRINCIPLES.md` §11（同一决定）。只用 correlationId，不再承诺 `traceId`。
> - §5 备份与恢复、§6 配置与密钥仍以本文与 `phase1a/infra/INFRA_RUNBOOK.md` 为准。

**状态：已决定**（2026-10-14，经 psa-coordinator 询问）：主键「UUIDv7 (Recommended)」；分页「游标分页 (Recommended)」；备份恢复「RPO ≤ 5 分钟、RTO ≤ 1 小时 (Recommended)」；告警「按级别分通道 (Recommended)」，接收群与值班人部署前定；并发版本传递按建议采用 If-Match / ETag（未单独作答）。

本文先给方案，标「**待定**」的取舍由 psa-coordinator 请用户决定，其余按建议直接实施。实施由下一轮代理完成，每条都配守护或契约测试。

## 1. ID 策略

**现状**
- 新聚合主键用 `bigint GENERATED ALWAYS AS IDENTITY`，legal_entities 用 `BY DEFAULT`，复审已指出这处混用。
- 人员和组织单元沿用 A8 ID（有符号 bigint，有负数）。
- event_publication 用 uuid。
- 对外的 ID 已经都是字符串。

| 方案 | 说明 | 取舍 |
|---|---|---|
| A 数据库序列 | 保持 bigint identity | 最简单，索引紧凑；但 ID 由数据库分配，在插入前拿不到，所以聚合在构造时无法把自己的 ID 写进领域事件（事件信封需要 aggregateId）；跨库合并和迁移时容易冲突 |
| **B UUIDv7（建议）** | 新建聚合的主键在应用侧生成 UUIDv7（kernel 统一提供 `IdGenerator`，可注入，测试可控） | 构造聚合时就有 ID，事件可以直接带上，与 eventId 同一种生成方式；按时间有序，B 树插入局部性好；不依赖数据库。代价是 16 字节，比 bigint 大 |
| C 雪花 bigint | 应用侧生成 64 位 | 与老系统形状一致（LegacyShapeTest 有相关规则），还要自己分配机器号，不建议 |

**建议：B**，并补充以下约定：
- 外部主数据沿用来源 ID：员工、组织单元用 A8 ID；法人用新系统 UUIDv7。来源 ID 只存在于对应的快照表里。
- 迁移数据：新表用 UUIDv7 主键，老 ID 放进 `legacy_id` 列（唯一约束），通过「原编号 / 原 ID」对照表检索，与 ADR-0010 的原编号做法一致。
- 对外 ID 一律用字符串（JSON 中为 string），由 OpenAPI 守护。
- 守护：新建表的主键必须是 uuid，不得用 identity；快照表列入白名单。

> **已被 ADR-0033 取代（采纳时生效；ADR-0033 现为提议，采纳前仍按上文）**：上文第 1、2、4 条中「外部主数据沿用来源 ID：员工、组织单元用 A8 ID……来源 ID 只存在于对应的快照表里」「老 ID 放进 `legacy_id` 列（唯一约束）」「快照表列入白名单」三句。依据：用户 2026-10-15「改为自有 UUIDv7 + A8 ID 对照列 (Recommended)」「主键统一」。上文按 保留不删。新做法（详见 `adr/src/ADR-0033.md`）：
> - 自有的每张表一律单列 uuid 主键，由 kernel `IdGenerator` 生成 UUIDv7，自有表零例外（用户「主键统一」「也加，自有表零例外 (Recommended)」）；只有框架自建表（Spring Session、ShedLock、Spring Modulith）登记豁免并逐表写理由。原以组合键为主键的表改为 UUIDv7 主键 + 命名唯一约束；版本 / 生效区间表为 UUIDv7 代理主键 + 自然键（实体自有 ID + 生效区间）排他约束。
> - 外部系统的标识（A8 人员、部门、岗位 ID，老系统 ID，核算组织编码）只登记在集成层的来源对照 `platform_integration.source_references`，`(来源系统, 来源对象类别, source_id)` 唯一；`source_id` 按来源实际类型存放（A8 为 bigint，可为负数）。迁入数据不在业务表加 `legacy_id` 列，原单据编号仍进 `legacy_numbers`。
> - `user_accounts.user_id` 为自有 UUIDv7，另有唯一的 `employee_id`；员工编号不作任何键。
> - 第 3 条「对外 ID 一律用字符串」保留不变。

**待定**：新建聚合主键用 B（UUIDv7，建议）还是 A（继续用序列）。

## 2. 时间与时区（无取舍，直接实施）

- 时刻一律存 `timestamptz`（现状已经满足，加 DDL 守护：禁止 `timestamp without time zone`）。
- 业务日期用 `date`。业务日的计算（工作日历、T+1、期间归属、编号中的 yyyyMM）统一按 `Asia/Shanghai`，由配置项 `forcitis.kernel.business-zone` 提供。
- 时间一律来自注入的 Clock（已有守护），新增测试：在跨日、跨月边界上，按上海时区计算业务日。
- API 中的时刻使用带时区的 ISO-8601（`2026-10-14T10:00:00+08:00`），日期使用 `2026-10-14`。

## 3. API 规范

| 项 | 建议 | 待定 |
|---|---|---|
| 分页 | **游标分页**：参数为 `cursor` 和 `limit`，`limit` 默认 50、上限 200；响应为 `{items, nextCursor}`。游标不透明，内部编码为 (排序键, id)，与 UUIDv7 天然配合。管理类小表（法人、编号规则）不分页 | **待定**：游标分页（建议），或页码分页（`page` 加 `size`，能跳页，但大表深翻页慢，并发写入时还会漏行或重行） |
| 过滤 | 查询参数用驼峰命名的字段名，多值用逗号分隔；不提供通用表达式语言 | — |
| 排序 | `sort=field,-field`，只允许白名单中的字段 | — |
| POST 幂等 | 有副作用的 POST 接受 `Idempotency-Key` 请求头，服务端按「用户 + 键」保存 24 小时的「键 → 响应」（状态、正文、ETag、Location），重复请求原样回放。同一个键带不同的请求体时返回 422；第一次请求仍在处理时返回 409；处理中的占位超过「处理中超时」（配置项，节点宕机时用）后允许重试接管；5xx 不保存，重试会重新执行；过期应答由 ShedLock 保护的定时任务清除。**范围**：只覆盖 POST；会话与登录状态端点（路径含 `/sessions`、以 `/login-states` 结尾）返回凭据或会话状态，**永不保存应答正文**，也不在契约中声明该请求头。规则由 `IdempotencyScope` 唯一表达，`HttpContractIT.idempotencyKeyIsOfferedExactlyWhereItIsStored` 守护契约与实现一致，新增返回凭据的端点必须放在这两类路径下，否则评审不予通过 | — |
| 并发更新 | 更新类请求必须带版本：`If-Match: "<version>"`；不一致返回 412。读取接口返回 `ETag` | **待定**：放在 If-Match 请求头（建议，符合 HTTP 语义），还是放在请求体的 version 字段（前端更直观） |
| 错误 | RFC 9457 ProblemDetail，type 和 code 取 ErrorCode（platform.web，已定） | — |

守护：
- 契约测试遍历 OpenAPI，检查列表接口的分页参数与上限、更新接口的版本要求、错误响应的 ProblemDetail 结构。
- 前端客户端统一处理游标、幂等键和 If-Match。

## 4. 可观测性（无取舍，直接实施；告警通道见末行）

- **日志**：结构化 JSON，使用 Spring Boot 4 的 structured logging，ECS 格式，输出到 `/data/forcitis-one/logs/*.json`，由 SLS 采集。字段至少包含 timestamp、level、logger、message、correlationId、userId（只记 ID）、module。不记录姓名、手机号、令牌。
- **correlationId 全链路**：
  - HTTP 入口读取 `X-Correlation-Id`，没有就生成；
  - 写入 MDC，并放进响应头；
  - 发布领域事件时写入信封的 correlationId，causationId 指向触发它的事件；
  - 处理事件和出站投递时，从信封恢复到 MDC；
  - 发往企业微信或致远的出站请求带上这个头（对方忽略也无妨）。
  - 测试：一次 HTTP 请求产生的事件与投递，correlationId 一致。
- **指标命名**：`forcitis.<子域>.<bc>.<名词>.<度量>`，例如 `forcitis.platform.integration.outbound.dead_letters`，与现有 G46 风格一致。现有指标统一改名，并由守护检查命名格式。
- **出口条件中的四类告警**：

| 告警 | 级别 | 触发 |
|---|---|---|
| 登录失败率 | P1 | 5 分钟内失败率 > 20 % 且 ≥ 20 次 |
| 未完成事件发布 / 死信 | P2 | 未完成 > 10 分钟，或死信数 > 0 |
| 组织同步延迟或中止 | P2 | 超过 2 个周期无成功，或缩水保护中止 |
| 作业失败 / Flowable 死信 | P3 | 失败数 > 0 |

- **告警通道**：建议 P1 用企业微信群机器人加短信，P2 用企业微信群，P3 只进看板。**待定**：接收群和值班人由用户指定。

## 5. 备份与恢复（写入 INFRA_RUNBOOK，执行属部署前事项）

- **建议目标**：RPO ≤ 5 分钟，依靠 RDS 高可用、日志备份和按时间点恢复；RTO ≤ 1 小时，实例内恢复或克隆。**待定**：用户确认这两个目标值。
- **RDS**：开启删除保护；自动备份保留 ≥ 7 天，生产建议 30 天；开启日志备份与按时间点恢复；每月保留一份长期快照，保留 1 年。
- **恢复演练**：每季度一次，dev 的首次演练放在上线前，作为出口条件的补充。步骤为：
  1. 按时间点克隆到临时实例；
  2. 应用以只读模式指向临时实例，完成冒烟测试（登录、查看待办、查看组织）；
  3. 核对关键表的行数和最近事件的 recordedAt；
  4. 记录实际的 RPO 和 RTO；
  5. 销毁临时实例。
- **ECS**：数据盘每日快照，保留 7 天；应用无状态，按版本目录可以快速回滚。

## 6. 配置与密钥（写入 INFRA_RUNBOOK）

- **密钥清单**：

| 项 | 用途 | 轮换 |
|---|---|---|
| PG `one_dev_app`、`one_dev_migrator` 口令 | 数据库 | 90 天 |
| 企业微信 corp secret | 新应用 | 180 天，泄露时立即轮换 |
| 致远验票 | 只用地址，无密钥 | — |
| `FORCITIS_TICKET_KEY_<KID>` | 签名票据，按签发方 | 180 天，使用 kid 双钥过渡 |
| `EMERGENCY_TOTP_ENCRYPTION_KEY` | 应急管理员 TOTP 加密 | 365 天，需重新加密已存密钥 |
| A8 视图、老库只读账号 | 外部只读 | 90 天，由对方 DBA 执行 |
| AG Grid 许可证（如采用） | 前端构建 | 按许可期 |

- **从 one.env 迁到 KMS**：
  1. 在 KMS 凭据管家中逐项建立凭据，名称为 `forcitis-one/<env>/<name>`；
  2. ECS 绑定 RAM 角色，只授予读取这些凭据的权限；
  3. 应用启动时通过阿里云凭据客户端拉取（Spring 配置导入），one.env 只保留凭据名；
  4. 逐项切换并验证后，删除 one.env 中的明文；
  5. 轮换使用 KMS 的自动轮换，或运维手工轮换，应用侧支持热加载或滚动重启。
- **守护**：已有的 gitleaks、ProductionConfigSanityTest；新增「prod profile 中的密钥类配置必须是占位符」的检查。

---

## 汇总：请用户决定

1. 新建聚合的主键：UUIDv7（建议）或数据库序列。
2. 分页方式：游标分页（建议）或页码分页。
3. 并发版本的传递方式：If-Match 请求头（建议）或请求体中的 version 字段。
4. RPO ≤ 5 分钟、RTO ≤ 1 小时（建议），是否接受。
5. 告警通道：接收群与值班人。
