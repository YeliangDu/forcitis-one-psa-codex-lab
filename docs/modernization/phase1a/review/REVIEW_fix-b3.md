# 修复批 B3 单独 opus 复审

条目编号以本文为准，外部引用加前缀 B3-。按时间顺序收录首轮复审与各轮复核。

---

## 第 1 轮

## B3 单独复审结论：修后整合

我核对了自报数字：`./mvnw -B verify` 结果是 surefire 703/0/0/19，failsafe 214/0/0/0，BUILD SUCCESS，与自报一致。本次只读，没有改任何文件。迁移与范围、H-4/H-5、错误链与幂等这三块由三个 opus 子代理分头只读核查，关键结论我已用 grep 抽查证实。

不判「可整合」，原因有四个：
- 与 B2 的迁移和表模型直接冲突，合进同一条线后 Flyway 会失败。
- 这个分支自己的前端 CI 会失败。
- 幂等键整个功能没有测试，也没有清理作业。
- 推迟清单里有几项本期就能做完，按调度口径应当在当期做。

结构性的设计是对的，下面这些只需按问题清单修补，不必返工：
- B-1 的解析模型：本人归属优先，否则沿当天的部门链向上找；解析不到就失败。
- H-2 的锁顺序：没有死锁路径，竞态测试是真的两个事务交错执行。
- H-3 错误链的主干，以及 ErrorContractIT 遍历全部处理方法。
- C-M7 在服务端真正清空了权限，不只是藏菜单。

---

### Blocker

**B3-B1　与 B2 的法人归属表名和写法冲突**
- 位置：`V20261014215000__…legal_entity_attribution_and_versions.sql:25-58`；`JdbcLegalEntityAttributionRepository.java:38-42,90-93`。
- 证据：
  - B1、B2、B2b、A 四个分支里的这份迁移仍是旧版，旧版建的是 `legal_entity_attribution_periods`。B3 在 7068547 把它拆成了 `org_unit_legal_entities` 和 `employee_legal_entities`。
  - B2 的 `V20261015011000` 和 `011100` 在旧表上建触发器、写注释，`one_db_roles_schemas.sql:103-107` 也对旧表做 REVOKE。合并后干净库会报 relation does not exist。
  - B3 的仓储每次保存都整表先 delete 再 insert，而 B2 把期间表定为只追加（触发器拒绝删除，运行角色收回了 DELETE）。合并后，dev 和生产里每次保存都会被拒；测试用超级用户跑，可能发现不了。
  - `scripts/ci/migrations_immutable.py` 拿 origin/main 比较：如果 B2 带着旧版 215000 先合进 main，B3 的 PR 会因为这份文件被修改而失败。
- 建议：
  - 以 B3 的表名为准。B2 在其上 rebase，把 011000、011100 和建库脚本改指新表。
  - B3 的仓储改成「关闭开放期间并追加新期间」，不再整表删除，否则 B2 的只追加规则成立不了。
  - 集成提交在干净库上全量跑迁移和守护，所有本地库重建。

**B3-B2　前端没有重新生成 API 类型，前端 CI 会失败**
- 证据：
  - `openapi.json` 改了 1686 行，但 `frontend/packages/api/src/generated/*` 一行没动。`pnpm verify` 的第一步 `check:generated` 必然失败。
  - 运行时也会坏：`GET sync-runs` 现在返回 `{items,nextCursor}`，前端 `use-workforce.ts` 和 `sync-runs-page` 还按数组处理。
- 建议：在本分支重新生成，或明确和 C 批、B2b 的 c6656e5 协同，并在 PR 里写清楚。

### High

**B3-H1　删除角色时，数据库级联改了账号的角色集合，没有经过聚合（H-4 的残留）**
- 位置：`V20261014130000…accounts_and_roles.sql:63`（`account_roles.role_id … ON DELETE CASCADE`）；`DeleteRole.java:26-35`。
- 证据：删除一个仍有人持有的角色，账号的角色集合被悄悄改掉，没有 AccountRolesReplaced 事件，账号 version 不变，也没有会话吊销审计。守护只扫 Java 源码，看不到这种级联。
- 建议：外键改成 RESTRICT，删除仍被持有的角色时拒绝（409）；或者在 DeleteRole 里逐个经 `UserAccount.replaceRoles` 处理。

**B3-H2　RepositoryWriteGuardTest 可以绕过**
- 位置：`RepositoryWritePolicy.java:18,22-26,87-89`；`RepositoryWriteGuardTest.java:84,115`。
- 证据：
  - 规则 2 只看方法名前缀（save/insert/update…），而且只扫文件名以 `Repository.java` 结尾的类。
  - 新写一个 `JdbcXxxStore`，在任意方法里写带 version 的聚合表，规则 2 和规则 3 都拦不住。
  - 现有代码里已经有漏网写法：`JdbcOrgSnapshotRepository.updateShortNames`（`:186`）在聚合之外把 org_units 的 version 加 1；`saveRegisteredEmployee`（`:94-98`）无条件覆盖 event_count。
  - SQL 正则不认识 CTE、MERGE、拼接表名；方法正则要求恰好 4 个空格缩进。
  - 例外文件能识别过期条目（allowed 与 found 必须完全相等），这一点没问题。但理由不是必填，键也太粗：规则 2 只到方法，规则 3 只到表。登记过之后，同一方法新增写别的表、或同一文件新增写这张表的方法，都会被自动放行。
- 建议：扫描 `..infrastructure..` 下的所有类；写带 version 的表时，要求 SQL 带 `version = :version` 条件，否则必须登记；理由必填；键改成「文件#方法#表」。

**B3-H3　幂等键整个功能没有测试，也没有清理作业**
- 位置：`platform/web/IdempotencyFilter.java`；`JdbcIdempotentResponses.java:20`。
- 证据：`src/test` 里搜不到 IdempotencyFilter 或 IDEMPOTENCY 相关的测试。过期行只在同一用户复用同一个键时才删，表会无限增长，响应体明文长期留存。
- 建议：
  - 补 IT：重放；同键不同请求返回 422；并发同键返回 409（两个线程加 latch）；跨用户同键互不影响；5xx 后可以重试；过期后可以复用。
  - 加一个 ShedLock 定时作业，删除过期行。
  - 在规则卡写明「返回凭据的接口不保存响应体」，并加守护。

**B3-H4　登录限流按客户端地址计数，公司出口 NAT 下会误伤**
- 位置：`LoginThrottle.java:23-25`；`platform_identity.properties`（每分钟 30 次）。
- 证据：
  - 代理配置本身没问题：nginx 在本机并覆盖 X-Forwarded-For，只信任 127.0.0.1（RUNBOOK E-8），X-Forwarded-For 伪造不了。
  - 问题在于：一次企业微信登录要调两次接口（login-states 和 wecom-login）。办公室共用一个出口 IP 时，每分钟大约只能登录 15 人。约 1300 人早上集中登录，会被成批返回 429。
  - SSO、签名票据、应急登录三个入口没有限流。
- 建议：
  - login-states 改按地址加较高额度；wecom-login 主要靠 state 一次性，额度放宽到每分钟几百次。
  - 额度按办公室出口 IP 实测设定，在 RUNBOOK 写明。
  - 把 SSO 和票据登录也纳入限流。

**B3-H5　B2 的审计归档会丢掉 B3 新加的审计列**
- 证据：B3 的 `V20261014222000` 给审计表加了来源地址、结果、变更说明、变更前后值。B2 的 `AuditArchiveJob.java:101-103` 只 select 7 个旧列，归档后删除在线行。合并后这几列的数据会永久丢失。
- 建议：集成时归档改为取全部列，并加一条「归档列与表列一致」的守护。

**B3-H6　工作日历「过去日期」的权限，B2 与 B3 各做了一套**
- 位置：`WorkCalendarController.java:58`。
- 证据：B3 的做法是 PUT 带 `lockedDaysConfirmed`，再校验 locked-day:update 权限。B2 的 df29c5f、90028a8 做成了独立用例和独立权限常量 `LOCKED_DAY_UPDATE`。两套接口形状不同，集成时必须由调度二选一。

**B3-H7　D-1a-45/162 只做了一部分（这项本期必须做，见第 10 项）**
- 位置：`ChangeAccountStatus.java:37`；`ReplaceAccountRoles.java:68`。
- 证据：停用账号和角色变更只吊销会话，不写 `platform.identity.session.revoked` 审计；只有离职（`HandleOrgSnapshotChange.java:55`）写了。会话过期清理用的是 Spring Session 自带的 `cleanup-cron`，每个过期会话都没有审计。

### Medium

**B3-M1　B-1 的守护只拦一种写法**
- 位置：`CurrentUserGuardTest.java:18-19`。
- 证据：只匹配「实现了 CurrentUser 的类里出现 `new LegalEntityId(数字)`」。以下写法都能绕过：
  - 常量，如 `new LegalEntityId(DEFAULT)`；
  - `Long.valueOf(1)`；
  - 在 `EmployeeLegalEntities` 的实现里返回默认值，或加 `.orElse(...)`；
  - 匿名类或 lambda 实现 CurrentUser。
  
  `LegalEntityId` 目前没有 `of()` 工厂方法。
- 现状：B-1 本身是对的。`SessionCurrentUser:39-41` 按业务时区的当天解析，解析失败抛 422，没有兜底；解析顺序符合 ADR-0026 第 2.5 条和 D-1a-27；四张期间表的 EXCLUDE 约束（左闭右开、失效日为空表示至今、CHECK 要求 to > from）正确；循环用 visited 集合防止。
- 建议：守护改为同时检查 `EmployeeLegalEntities` 的实现；禁止 `orElse` 和 `orElseGet` 产出 LegalEntityId；禁止 `LegalEntityId` 由常量构造。

**B3-M2　业务事实没有按事实发生日解析法人（跨批次问题，由工时单批处理）**
- 证据：`SubmitTimesheet.java:70` 用的是 `currentUser.legalEntityId()`，也就是当天的法人。D-1a-27 要求业务事实按事实发生日解析。B3 已经提供了 `EmployeeLegalEntities.on(employee, date)`，但工时单没用。
- 建议：交 A 批改用这个接口。

**B3-M3　组织历史和法人归属的几处缺口**
- 回填迁移（第 126-134 行）以 `created_at` 作为第一版的生效日。导入历史业务时，早于这个日期的事实都会解析失败。按 G-1 这是正确的失败，但会挡住历史导入，需要在规则卡写明。
- 员工登记路径经 OrgHistory 写 `assignment_versions`，却没有拿快照写锁，与 `unversioned-table-writes.txt:8` 登记的理由不符。
- `PersistRegisteredEmployee` 写 event_count 时不带旧值条件，同一个人并发首次登录会发出两个序号都是 1 的事件。

**B3-M4　有几个写接口没走 If-Match**
- 位置：`LegalEntityAttributionController.java:35,42,54,61`；`RoleController.java:97-117`。
- 证据：法人归属的 PUT 和 `:end`、账号的 replace-roles、enable、disable 都不要求 If-Match，GET 归属也不输出 ETag。replace-roles 是整组替换，两个管理员先后提交，后者会静默覆盖前者。
- 另外，`ExpectedVersion.java:21` 剥掉 `W/` 后照常接受，而 RFC 9110 规定 If-Match 必须用强比较。`If-Match: *` 会得到 412，畸形值也是 412（严格来说应是 400）。

**B3-M5　幂等键实现上的几处细节**
- `IdempotencyFilter.java:87-89`：抢占失败后再查，如果为空就直接执行业务，这时没有持有任何预留，可能重复执行。应改为重新抢占，抢不到就返回 409。
- 业务事务和保存应答不在同一个事务。`finish` 失败或进程崩溃时，这一行停在「进行中」，24 小时内用同一个键重试都会得到 409。需要给「进行中」状态一个短超时。
- 重放时不还原 `ETag` 和 `Location`，客户端拿不到后续 If-Match 需要的版本号。
- 过滤器对 PUT、PATCH、DELETE 以及已登录状态下的 `/sessions:*` 都生效，与 OpenAPI 的声明（只有 POST、不含登录接口）不一致。已登录用户带键调用应急登录时，含密码的请求体会以无盐 SHA-256 存进 `request_hash`。
- 已确认正确的部分：
  - 主键 `(user_id, key)`，天然按用户隔离；
  - 请求指纹包含方法、URI、查询串和请求体（用户隔离由主键完成）；
  - 抢占用 `ON CONFLICT DO NOTHING`，检查和插入是同一条语句；
  - 5xx 时释放预留；
  - 目前没有接口在响应体里返回凭据。

**B3-M6　安全过滤链委托异常解析器时没有兜底**
- 位置：`SecurityConfiguration.java:43-44`。
- 证据：
  - `resolveException` 返回 null 时，响应会停在默认的 200、空体，拒绝在协议上看起来像成功。不会越权，但客户端会误判。
  - 在 DispatcherServlet 之前的过滤器（例如幂等过滤器访问数据库）抛出的异常，走的是 Boot 默认的 `/error`，不是 ProblemDetail。
  - 已确认：`detail` 已改为固定对外文案，内部消息只写日志，M-12 的消息泄露已修复。ErrorContractIT 确实遍历了 `RequestMappingHandlerMapping` 里的全部处理方法，但断言过宽：有权限时得到 500 也算通过，无权限时得到 2xx 也不报错。

**B3-M7　M-12 的枚举问题还在（在 A 批范围内，需要在报告里写明尚未关闭）**
- 证据：`TaskGuard.java:25,35,52` 对不存在的任务返回 404，存在但非参与人返回 403；工时单的 Get、Cancel、Submit 也一样。这两类 ID 都可以靠 404 和 403 的差别探测出来。B3 没有改这些模块，这一项不算 B3 越界。

**B3-M8　UUIDv7：现在的代码会让下一批改起来更难（按调度口径，不评估是否该推迟）**
- B3 新建的 `legal_entity_attributions.attribution_id` 又用了 `bigint IDENTITY`。
- 新期间表和版本表的外键都是 bigint。
- 内核的 `LegalEntityId(long)`、`UserId(long)` 写死为 long，整个代码库都在调 `.value()` 取 long。
- 路径参数写成 `@PathVariable long`，OpenAPI 里是 integer，前端还在用 `Number(syncRunId)`。
- sync-runs 的游标按数字 id 排序。
- 没有 `IdGenerator`，也没有「新建表主键必须是 uuid」的守护。
- 建议：下一批先加守护和 `IdGenerator`，在冻结之前别再新建 bigint 主键表。

**B3-M9　M-17 改了一半**
- 已改：作业总开关 `jobs-enabled` 在测试里关闭了全部 `@Scheduled`。
- 未改：`WorkforceIntegrationSupport.java:86-99` 在 TRUNCATE 之前轮询 `event_publication`，最多等 20 秒，而且统计的是全部模块的未完成行。只要有一个监听器永久失败，后面所有 workforce 测试都会超时，形成连锁失败。这是 CI 稳定性问题，不影响生产。
- 建议：只等本测试相关的监听；超时时打印卡住的监听器名称；或者在 TRUNCATE 之前把未完成行标记为完成。
- 另外，主配置里写的是 `${FORCITIS_JOBS_ENABLED:true}`，和代码注释「the key has no default」不一致（Nit）。
- M-18 已落实：A8 的关系类型、枚举编号与根路径不再有默认值，启用视图读取而缺配置时启动失败。

**B3-M10　事件序号的回填与测试**
- 回填 `event_count = version`：b2fc504 之前，每次保存 version 前进 max(1, 事件数)，所以 version 一定不小于历史上发出的最大序号，回填后接着发号不会重号。但回填值并不是迁移注释里说的「已发出的事件数」。`outbound_deliveries` 从没发过事件，却被回填成非 0。员工那份 3 行的迁移没有回填。
- `EventSequenceContinuityTest`：只覆盖了 9 个聚合中的 7 个，缺 RegisteredEmployee 和 OutboundDelivery；而且只在内存里调 `drainEvents`，没有经过「保存 → 读回 → 修改 → 保存」的持久化往返。仓储如果把 version 写进 event_count，或者漏写这一列，测试照样通过。
- 建议：每个仓储加一个参数化 IT 做读写往返；用反射枚举所有 AggregateRoot 子类，断言每个都被覆盖；改正迁移注释，outbound 置 0。

**B3-M11　DdlNamingTest 与 btree_gist**
- 不写约束名的 EXCLUDE：在 CREATE TABLE 里会被误当成一个名叫 `exclude` 的列，报错提示有误导；在 ALTER TABLE 里会被静默放过。
- btree_gist 扩展没有写进建库脚本和 RUNBOOK，会落在迁移角色的 `one_migration` schema 里。应在迁移里写 `WITH SCHEMA`，或由 DBA 预先建好。

**B3-M12　术语**
- 新概念没有登记为提议术语：核算组织编码、来源地址、变更说明、变更前值和变更后值、幂等键、部门法人对照、员工法人归属、身份确认。
- `fix-b.yaml` 第 480、606、662 行用「老编号」，应按 `platform-numbering.yaml:60` 和 `contract.md:181` 改为「原编号」。
- `legacy-numbers` 的 OpenAPI 把三选一的三个参数都标成了必填。

**B3-M13　`lockActiveCode` 的事务传播应为 MANDATORY**
- 位置：`LegalEntityDirectoryService.java:27`。
- 证据：现在是 REQUIRED。接口承诺锁持有到调用方事务结束，但在没有外层事务时调用，会自开事务，方法一返回锁就释放。

### Nit
- `LoginStateCookie` 的注释说 cookie「只发往登录接口」，实际 Path 是 `/api/v1/platform`，覆盖整个平台模块。
- `AggregateRoot.java:27-31` 标了 forRemoval 的单参构造器已没有调用方，应删除。
- `UserAccount.java:105-109` 角色集合没变时也会记一个 AccountRolesReplaced 事件。
- `PageRequest.java:15` 中 limit 越界返回的是游标错误码；`slice()` 在游标指向的行被删除后报 400。
- `LegalEntityCodeRaceIT.java:83` 统计的是库里任意在等锁的会话，应改用 `pg_blocking_pids`；缺「停用与发号」方向的竞态测试。
- 账号开立的行为变了：默认角色缺失时，原来跳过并写审计，现在所有登录都会失败。这符合 G-1，但 RUNBOOK 要写明。

---

### M-13 与 C-M7 的安全复核

**M-13 已确认正确的部分：**
- 绑定 cookie 设了 HttpOnly、SameSite=Lax、Secure（由配置控制，默认 true），有效期 10 分钟，与 state 相同。
- 服务端只存 SHA-256 哈希。
- 消费 state 用的是 `delete … where state_hash and binding_hash and expires_at > now returning`，一次性使用，能防重放。
- 迁移在加列前先清空了 login_states 表。

**C-M7 现状（按调度口径维持「每次都确认」，只审安全性）：**
- 权限是真的被清空了：`SessionGuardFilter` 在每次请求时把未确认会话的权限置为空，所有业务接口都要求 `hasAuthority`。我核查过，除登出、确认、`sessions:current` 这三个接口外，没有只要求「已登录」的接口。
- 需要修的几点：
  1. **缺省放行**：`ServletSessionRegistry.identityConfirmed()` 写的是 `!Boolean.FALSE.equals(attr)`，会话里没有这个属性时视为已确认。应改为只有属性等于 TRUE 才算确认。
  2. **未确认时仍返回完整权限和菜单**：`sessions:current` 在未确认时照样返回完整的 `permissionCodes` 和菜单。应在未确认时返回空，和服务端行为一致。
  3. **确认后没有轮换**：确认之后没有轮换会话 ID 和 CSRF token。建议轮换。
  4. **审计不全**：只在确认时写审计，登录时「需确认」这一状态没有记。
- 需要知道的边界：这个确认只能防「登录 CSRF」（受害者被悄悄登进攻击者的账号，看到别人的名字后会拒绝），防不了票据被盗：拿到票据的人自己点一下确认就行。这一点应写进 SECURITY_INVARIANTS。

**「新浏览器才确认」的实现依据（供 B3 修复时使用）：**
1. **标识**：
   - 用 32 字节随机数，cookie 设 HttpOnly、Secure、SameSite=Lax，Path 限定在 `/api/v1/platform/sessions`，用 `__Host-` 前缀；Max-Age 等于 N 天（N 可配置，默认 30）。
   - 服务端存 `sha256(值)` 绑定 `employee_id` 和 `last_login_at`，主键是 (哈希, employee_id)。
   - 新表用 uuid 主键，放在 `platform_identity` 下。
2. **免确认的判定**：
   - 只有 cookie 的哈希存在、绑定的员工正好是本次票据解析出的员工、并且 `last_login_at` 在 N 天以内，才免确认。
   - 其余情况一律需要确认，包括：没有 cookie、哈希不存在、绑定的是另一个员工、已过期、已作废。
   - 判定必须在服务端、登录事务里完成，前端无法影响结果。
3. **多个账号共用一个浏览器**：
   - 一个浏览器标识可以绑定多个员工（多行），但每个员工各自判定。
   - 用 A 的标识登录 B，B 仍需确认。
   - 登录 CSRF 的场景下，受害者浏览器里没有攻击者账号的绑定，所以仍会弹出确认。这保住了 C-M7 原本的防护目的。
4. **轮换**：
   - 每次免确认登录或确认成功后，都生成新值、替换哈希、重新下发 cookie，`last_login_at` 更新为当前时间。
   - 如果出示的是已经被轮换掉的旧值（说明 cookie 被复制使用过），作废该账号的全部标识，记一条高危审计，并要求确认。
5. **cookie 被盗时的边界**：
   - 盗取者只有同时拿到「该员工的有效票据」和「该浏览器的标识」，才能跳过确认。
   - 标识不授予任何权限，只是跳过一次确认。
   - 因为 cookie 是 HttpOnly，脚本读不到；主要风险来自本机被入侵，第 4 条的轮换检测可以发现复用。
6. **作废时机**：
   - 账号停用、员工离职、角色变更（可选），在 `ChangeAccountStatus`、`HandleOrgSnapshotChange` 里与吊销会话同一事务删除该员工的全部标识。
   - 管理员可以按员工手动作废。
   - 登出不清除标识（按用户决定）。
   - 清理作业删除超过 N 天的行。
7. **审计**：
   - 每次登录写 `platform.identity.login.identity-check`，结果为「免确认」或「需确认」，原因从固定枚举里取：无标识、标识属于他人、已过期、已作废、轮换冲突、已识别。
   - 审计里只写哈希的前缀，不写原值。
8. **测试**：上述每种原因各一个 IT，外加「作废后需要确认」和「旧值重放会作废全部标识」两例。

---

### 第 10 项：推迟清单逐条判断（按调度口径）

| 项 | 判断 |
|---|---|
| UUIDv7 | 已改到下一批，在迁移基线之前完成，这里不评估。会增加后续难度的地方见 B3-M8。 |
| D-1a-174 必记动作守护 | **本期必须做。** 只要写一份必记动作清单（YAML），再加一个守护：每个动作都必须有对应的审计写入点（事件默认审计，或显式调用 `audit.record`），工作量小。现状是领域事件默认都会审计，但登录、会话这类不走事件的动作，既没有清单也没有守护。 |
| D-1a-45 / D-1a-162 会话吊销与清理审计 | **本期必须做。** 停用和角色变更要补 `session.revoked` 审计；会话清理改为自有作业：先查出过期会话，逐条写审计（区分空闲过期和到达上限），再删除，取代 Spring Session 自带的 `cleanup-cron`。 |
| D-1a-22 / D-1a-23 专门测试 | **本期补测试。** 功能大体已实现（`OrgHierarchy:43-66` 排除已撤销部门的子树；`SnapshotReconciler:275` 不对已撤销的再发事件；outbox 有重投），缺的是能证明契约的测试，以及「事件字段清单与同步字段同一份」的守护，成本低。 |
| OpenAPI 列表接口带 limit/cursor 的契约测试 | **本期必须做。** 现在就已经能抓到两处违例：`GET /organization/org-units` 返回裸数组；`GET /platform/approval-tasks` 没有参数（A 批已经补了审批分页）。org-units 要么改成分页，要么在测试里豁免并写明理由（组织树需要整棵返回）。 |

---

### 第 11、12 项补充
- **迁移**：目前没有任何环境跑过这串迁移，所以生产校验和风险为零。风险集中在三处：各分支间的结构冲突（B3-B1）、本地库需要重建、main 上的迁移不可变检查。另外，A 批新增的 `V20261014200000`–`205000` 比地基的 `2100xx` 版本号小；干净库按顺序执行没问题，已经跑过地基迁移的本地库需要重建（Flyway 没有开 out-of-order）。
- **越界检查**：B3 没有改 approval、timesheet 的 Java 代码和迁移。`openapi.json` 里这两个模块的接口有变化，来自横切的 Problem 和幂等键声明，可以接受。与 B2 重叠 28 个文件，与 B2b 重叠 18 个，与 A 重叠 7 个。与 B2 的实质重叠见 B3-B1、H5、H6。`check_legacy_name.py` 检查通过。B3 没有提交 session-log、intent、status 片段，推迟项也没有任何记录。

---

### 整合前必须修
1. B3-B1：与 B2 统一法人归属的表名和只追加模型，修正 B2 的追加迁移和建库脚本；集成提交在干净库上全量跑迁移和守护。
2. B3-B2：重新生成前端 API 类型，修好 sync-runs 页面，让前端 CI 通过（或与 C 批、B2b 明确分工）。
3. B3-H1：删除角色的级联改为 RESTRICT，或改为经聚合处理。
4. B3-H2：仓储写入守护扫描全部 infrastructure 类，例外理由必填、键加上表名。
5. B3-H3 和 M5：幂等键补 IT、加清理作业、修抢占后的查询窗口和「进行中」卡死、重放还原 ETag 和 Location、作用范围与 OpenAPI 一致。
6. B3-H4：登录限流按 NAT 场景重新设计额度，把 SSO 和票据登录也纳入。
7. B3-H5、H6：与 B2 定下审计归档的列和工作日历过去日期的接口方案。
8. B3-H7 与第 10 项：D-1a-45/162 会话吊销与清理审计、D-1a-174 必记动作守护、D-1a-22/23 测试、列表分页契约测试，都在本期完成。
9. C-M7：修上面列出的四点（缺省放行、未确认时返回空权限、确认后轮换、登录时记录需确认），并按「新浏览器才确认」的实现依据落地，各原因都有 IT。
10. B3-M4：法人归属和账号的写接口加 If-Match，If-Match 改为强比较。
11. B3-M10：事件序号测试覆盖全部 9 个聚合，并做持久化往返；改正回填迁移的注释。
12. B3-M1：CurrentUserGuardTest 堵住常量、`orElse` 和 EmployeeLegalEntities 实现这几个口子。

其余 Medium 和 Nit 可以随整合一起修，或在规则卡里写明推迟理由。

---

## 第 2 轮

## 结论：可整合

在整合分支 HEAD 390e786 上，原先列出的 Blocker 和 High 都已关闭，也没有发现新的 Blocker 或 High。B1 的安全语义在三次合并后都还在。本次只读，没有改任何文件。三个 opus 子代理分头核查，关键结论我已对照代码确认。

**后端测试结果：** 我跑了全量 `./mvnw -B verify`，BUILD SUCCESS。surefire 851 个，0 失败，19 个跳过；failsafe 302 个，0 失败；另有打包冒烟 2 个。合计 1155 个（含跳过 1174 个），和你报的 1233 对不上，请核一下 1233 是怎么数的。

### Blocker / High 逐项复核

| 项 | 结论 | 依据 |
|---|---|---|
| B3-B1 | 已关闭 | 旧表名 `legal_entity_attribution_periods` 已全部清除。仓储不再有 DELETE，只新增期间、或给开放期间填结束日，其余改动直接抛异常（`JdbcLegalEntityAttributionRepository.java:57-77`）。B2 的触发器 `V20261015011000` 已指向两张新表，只放行"结束日从空变为有值"，与仓储写法兼容。`attribute()` 遇到已关闭期间返回 422，不再改写它。角色脚本第 112-116 行已对新表 REVOKE DELETE 和 TRUNCATE。 |
| B3-B2 | 已关闭 | 9ab7cb1 重新生成了前端类型，同步记录页改用游标分页，`check:generated` 通过。前端 typecheck 和 test 按你的安排交给 C 批。 |
| B3-H1 | 已关闭 | `V20261014223000` 把账号角色外键改为 RESTRICT。仓储捕获外键违例，返回 409 `ROLE_IN_USE`。`RoleManagementIntegrationTest:131-135` 断言了 409，也断言了账号与角色的关联还在。 |
| B3-H2 | 已关闭（剩 Medium） | 守护已扫描全部 `/infrastructure/` 类，例外登记键是"文件#方法#表"。仍有三个可绕过的口子，见下方 Medium 第 1 条。 |
| B3-H3 | 已关闭（剩 Medium） | `IdempotencyIT` 有 8 个用例：重放（含 ETag 和 Location）、同键不同请求 422、并发 409、跨用户隔离、5xx 后重试、过期后复用、处理中超时接管、作用范围。清理作业带 ShedLock，每 15 分钟一次。作用范围是只管 POST、排除登录类接口，与 OpenAPI 声明用同一个判断，并有契约测试逐个核对。 |
| B3-H4 | 已关闭 | 5 个登录类别分别配置额度，缺一项就启动失败。SSO、签名票据、应急登录都计入限流，有 IT 覆盖。默认额度按 NAT 场景设定，RUNBOOK 已写明依据和实测方法。 |
| B3-H6 | 已关闭（剩 Medium） | 只剩一套实现：PUT 带 `lockedDaysConfirmed=true` 时，转到 B2 的独立用例，并校验 `locked-day:update` 权限；这个权限只授给 org_data_admin。 |
| B3-H7 及推迟项 | 已关闭（剩 Medium） | 会话到期由自有作业处理：每个过期会话写一条审计并区分空闲过期和到达上限，再删除；Spring Session 自带的清理已关闭。停用、角色替换、离职、强制下线都写会话吊销审计。必记动作清单有了守护。D-1a-23 有字段清单契约测试。列表分页契约测试遍历 OpenAPI，org-units 的豁免写明了理由。D-1a-22 的"排除已撤销部门"有 RULE-360 测试覆盖；"历史报表可含"因 1a 还没有报表，无从测试。 |
| C-M7 | 已关闭 | 见下文。 |

**C-M7「新浏览器才确认」与全局闸门：**
- **浏览器标识 cookie：** 用 `__Host-` 前缀，HttpOnly、Secure、SameSite=Lax，值为 32 字节随机数。服务端只存哈希，绑定账号和最近一次登录时间，新表主键是 uuid，有效期默认 30 天、可配置。
- **轮换与重放：** 每次使用后轮换；出示已被轮换掉的旧值时，作废该账号全部标识并写高危审计。
- **多账号同一浏览器：** 按"标识 + 账号"判定，有 IT 证明 A 的标识不能让 B 免确认。
- **其他规则：** 判定在登录事务内完成；登出不清除标识；停用账号会作废标识；每次登录都审计"免确认"或"需确认"及原因；旧标识由清理作业删除。
- **四处修正都已完成：**
  - 会话里没有确认标记时按未确认处理，只认 TRUE；
  - 未确认时 `sessions:current` 返回空权限和空菜单；
  - 确认后轮换会话 ID 和 CSRF token；
  - 登录时审计"需确认"。
- **全局闸门：** 放在 `SecurityConfiguration.java:48-56`。放行清单只有三条：GET `current`、POST `confirm-identity`、POST `logout`，按 HTTP 方法加路径精确匹配。其余请求一律要求已确认，并返回 403 ProblemDetail。因为是清单外全拒，路径变体（尾斜杠、分号、大小写、其他方法、`%3A`）只会落进拒绝分支。需要确认的只有 SSO 和签名票据登录；企业微信和应急登录不受影响。

### 另外三点

1. **B1 安全语义：都在，没有被削弱。**
   - fix-b1-identity 的末端提交是 HEAD 的祖先。B1 的测试方法在 HEAD 上一个不少（340 个变为 372 个），`@Disabled` 数量没变。
   - 管理模式五项权限：`AdministratorMode.java` 与 B1 完全相同，进入、退出、自然到期三处审计都在。
   - 敏感权限双人确认：涉及敏感角色时，`ReplaceAccountRoles` 在真正替换之前就返回"待确认"，确认人限制没变，确认后的应用没有 HTTP 入口。
   - 离职暂停放行：`HandleOrgSnapshotChange` 与 B1 没有差异；放行仍需 separation-hold 权限并填写原因。
   - 数据范围：org-units 的列表和详情、法人归属的两个 GET，范围外都返回 404 并写拒绝审计。
   - `SessionGuardFilter` 的顺序正确：先处理管理模式到期和会话终止，最后套用确认闸门。未确认的会话连管理模式权限也没有，也进不了管理模式。

2. **新登记的守护例外：都合理。**
   - 测试隔离例外相对上次新增的是 5 条，不是 3 条。你说的 3 条是数据范围那一组，都写了"随数据范围批移到 scenario/"；另 2 条也有理由，其中一条替代了已删掉的 7 条审批、工时单例外。
   - `AuditEntryResponse#sourceAddress`：审计查询只授给安全审计员，这个权限本身需要双人确认，每次查询也会写审计，符合 ADR-0026 把审计定为只给安全审计员的口径。审计导出的 CSV 是否也带来源地址，这次没核。
   - `emergency_admins` 的 3 条：分别是单条语句原子加计数和锁定、TOTP 步数防重放、成功后清零。三条都不抬版本号，所以一次失败的登录不会让并发的账号修改失败。成立。

3. **V20261014222000 的乱序风险：没有。**
   - 任何配置都没有开 out-of-order，用的是 Flyway 默认值 false。
   - 全部 73 个迁移没有重复版本；逐条检查过，每个迁移引用的对象都在更低版本里已经建好。222000 只改审计表，审计表在 134000 建。
   - origin/main 上的最高版本是 V20261014145000，整合分支新增的迁移都比它高。
   - 目前没有环境跑过这批迁移（STATUS 写明部署前被阻塞），风险只在开发者本地保留的旧库上。

### 不阻塞整合、建议尽快修的 Medium

1. **仓储写入守护还能绕过。** 例外理由仍不是必填。版本条件在整条 SQL 里匹配，所以只在 SET 里出现 `version = :` 也算通过。`INSERT … ON CONFLICT DO UPDATE` 不检查。拼接出来的 SQL 识别不到。目前代码里没有利用这些口子的写法。
2. **幂等键还有两处窗口。**
   - 抢占失败、查询又为空时，会在没有占位行的情况下执行业务（`IdempotencyFilter.java:94-96`，已核实）。
   - 超时接管后，被接管的旧请求事后仍会写入或删除新占有者的行，缺防护令牌。
3. **账号没有任何角色时，离职不会作废浏览器标识。** `UserAccount.recordSeparation()` 遇到无角色账号直接返回、不发事件（`UserAccount.java:116-119`，已核实），所以作废标识的监听收不到。这偏离了用户"离职即作废"的决定，但因为离职账号本来就登录不了，实际风险很低。修起来很简单：在 `HandleOrgSnapshotChange.separate()` 里直接作废。
4. **还有三条吊销会话的路径不写会话吊销审计：** 运维授权角色（`GrantRoleByOperations`），以及应急管理员的停用和重置凭据。
5. **必记动作守护偏弱。** 它只核对动作字符串在代码里出现过，证明不了每个改状态的用例真的写了审计。
6. **工作日历缺一个 HTTP 层测试，文档也没写确认标志。** 带 `lockedDaysConfirmed=true` 但没有 locked-day 权限时应返回 403，这条没有测试。RUNBOOK 和 OpenAPI 也没写这个标志需要该权限。
7. **建议在 CI 加一条迁移版本单调性检查：** 拦住新增迁移的版本号低于 main 上已有的最高版本。

**测试名引用复审编号的抽查：** 只有 B3-M1、M4、M8 在测试里出现，其中 M1 和 M8 只出现在类注释里，不在 DisplayName 中。M2、M3、M5 到 M7、M9 到 M13 都没有被引用。如果按 Medium 由测试关闭的规则，需要补上这些编号。
