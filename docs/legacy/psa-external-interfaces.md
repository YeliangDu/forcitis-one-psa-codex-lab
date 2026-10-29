# Phase 1a 平台桥接：外部接口目录

> 来源：只读分析 `/Users/yeliang/Projects/FORCITIS/psa`（当前 HEAD，未合入 `docs/security_remediation.patch`）。
> 路径缩写：
> - `SYS` = `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system`
> - `BPM` = `psa-backend/yudao-module-bpm/yudao-module-bpm-biz/src/main/java/cn/iocoder/yudao/module/bpm`
> - `FST` = `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst`
> - `CC` = `psa-backend/yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/enums/CommonConstants.java`
> - `FSTC` = `psa-backend/yudao-module-fst/yudao-module-fst-api/src/main/java/cn/iocoder/yudao/module/fst/enums/fst/FSTConstant.java`
> - `PC` = `psa-front-desk-pc/src`，`H5` = `psa-mini/src`
>
> 凭据一律打码或改写为占位符：`${WECOM_CORP_ID}` `${WECOM_CORP_SECRET}` `${WECOM_AGENT_ID}` `${WECOM_CB_TOKEN}` `${WECOM_CB_AES_KEY}` `${KM_BASE_URL}` `${KM_INTEGRATION_KEY}` `${SEEYON_OA_BASE_URL}` `${PSA_WEB_ORIGIN}` `${A8_DB_URL}` `${ROCKETMQ_NAMESRV}`。

## 0. 鉴权基线（所有端点共用）

| 项 | 事实 | 出处 |
|---|---|---|
| 令牌传递 | `Authorization: Bearer <accessToken>`，不认查询参数 | `psa-backend/yudao-framework/yudao-spring-boot-starter-security/.../SecurityFrameworkUtils.java:39-49` |
| 令牌存储 | 不透明令牌（`system_oauth2_access_token`），由 `OAuth2TokenService.createAccessToken` 签发，客户端 `CLIENT_ID_DEFAULT` | `SYS/service/auth/AdminAuthServiceImpl.java:405-413` |
| 匿名放行 | 方法上的 `@PermitAll` 加上 `yudao.security.permit-all-urls` | `YudaoWebSecurityConfigurerAdapter.java:124-146` |
| 服务间 RPC | `/rpc-api/**` 一律放行（system / bpm / fst / infra） | `SYS/framework/security/config/SecurityConfiguration.java:34` 等 |
| 网关 | 只转发 `/admin-api/{fst,system,infra,bpm}/**` 和 `/app-api/...`，不暴露 `/rpc-api`；网关的 `TokenAuthenticationFilter` 只解析令牌，不拦截 | `psa-backend/yudao-gateway/src/main/resources/application.yaml:15-62` |
| 多租户 | 请求头 `tenant-id`，PC 端注入 | `PC/utils/request.js:44` |
| 图片验证码 | 默认开启（`application.yaml:154`），dev / local / **prod 都关闭** | `SYS/../resources/application-prod.yaml:134` |
| 短信验证码 | 区间 9999–9999，即**固定为 9999** | `SYS/../resources/application.yaml:188-193`；`SYS/service/sms/SmsCodeServiceImpl.java:72` |
| 限流 | 部分端点标注 `@RateLimiter(name="backendA")`，仓库里没找到 backendA 的具体配额 | `AuthController.java:124,171,193` 等 |

## 1. 对端：PC / H5 终端用户（认证与菜单）

| # | 名称 | 方向 | 方法与路径 | 载荷（请求 → 响应） | 鉴权 | 频率 / SLA | 源码 |
|---|---|---|---|---|---|---|---|
| U1 | 账号密码登录 | 入站 REST | `POST /admin-api/system/auth/login` | `{username:str, password:str, captchaVerification?:str, socialType?:int, socialCode?:str, socialState?:str}` → `{userId:long, accessToken:str, refreshToken:str, expiresTime:datetime}` | **permitAll**；prod 无验证码 | 无限流 | `SYS/controller/admin/auth/AuthController.java:79-85`；`AdminAuthServiceImpl.java:136-151` |
| U2 | 登出 | 入站 REST | `POST /admin-api/system/auth/logout` | 请求头中的 Bearer → `true` | **permitAll**（有令牌就注销） | — | `AuthController.java:148-158`；`AdminAuthServiceImpl.java:416-430` |
| U3 | 刷新令牌 | 入站 REST | `POST /admin-api/system/auth/refresh-token?refreshToken=` | → `AuthLoginRespVO` | **permitAll**（凭刷新令牌） | PC 收到 401 时触发 | `AuthController.java:160-167`；`PC/utils/request.js:122-152` |
| U4 | 权限信息 | 入站 REST | `GET /admin-api/system/auth/get-permission-info` | → `{user:{id,code,nickname,avatar,leaderUserId,leaderUserName,deptId,deptName,email,mobile,postId,postName}, roles:[str], permissions:[str]}` | 需登录 | 每次登录后调用一次（PC 的 `GetInfo`） | `AuthController.java:169-189`；`PC/store/modules/user.js:117-119` |
| U5 | 权限信息（app） | 入站 REST | `GET /app-api/system/auth/get-permission-info` | 同 U4 | 需登录 | — | `SYS/controller/app/auth/AuthAPPController.java:52` |
| U6 | 菜单树 | 入站 REST | `GET /admin-api/system/auth/list-menus` | → `[{id,parentId,name,path,component,icon,visible,keepAlive,children[]}]` | 需登录 | 每次登录后调用一次（`GenerateRoutes`） | `AuthController.java:191-203`；`PC/api/menu.js:6`；`PC/store/modules/permission.js:31-34` |
| U7 | 短信登录 | 入站 REST | `POST /admin-api/system/auth/sms-login` | `{mobile, code}` → `AuthLoginRespVO` | **permitAll**；验证码固定 9999 | — | `AuthController.java:207-213`；`AdminAuthServiceImpl.java:299-311` |
| U8 | 发送短信验证码 | 入站 REST | `POST /admin-api/system/auth/send-sms-code` | `{mobile, scene}` → `true` | **permitAll** | 1 分钟 1 次，每天 10 次（`application.yaml:190-191`） | `AuthController.java:215-222`；`AdminAuthServiceImpl.java:289-296` |
| U9 | 验证码取图 / 校验 | 入站 REST | `POST /admin-api/system/captcha/get`、`/check` | AJ-Captcha 协议 | **permitAll** | — | `SYS/controller/admin/captcha/CaptchaController.java:33-45`；`PC/components/Verifition/api/index.js:12,22` |
| U10 | H5 令牌校验 | 入站 REST | `POST /admin-api/system/auth/qy-wechat/checkToken` | 请求头 Bearer → `true`，或 `{code:401}` | **permitAll** | H5 每次 `onLoad` 调用 | `AuthController.java:300-313`；`H5/pages/qyLogin/qyLogin.tsx:247` |
| U11 | 强制下线 | 入站 REST | `DELETE /admin-api/system/oauth2-token/delete?accessToken=` | → `true` | 权限点 `system:oauth2-token:delete` | — | `SYS/controller/admin/oauth2/OAuth2TokenController.java:43-48` |
| U12 | 字典（登录后加载） | 入站 REST | `GET /admin-api/system/dict-data/list-all-simple` | → 字典数组 | 需登录 | 每次登录后调用一次 | `PC/api/system/dict/data.js:67`；`PC/permission.js:41` |

## 2. 对端：OAuth2 客户端（芋道标准，Phase 1a 是否保留待定）

| # | 方法与路径 | 鉴权 | 源码 |
|---|---|---|---|
| O1 | `POST /admin-api/system/oauth2/token` | **permitAll**（client_id/secret 放在 Basic 头或参数里） | `SYS/controller/admin/oauth2/OAuth2OpenController.java:86-100` |
| O2 | `DELETE /admin-api/system/oauth2/token` | **permitAll**（凭客户端凭据） | `OAuth2OpenController.java:146-151` |
| O3 | `POST /admin-api/system/oauth2/check-token` | **permitAll** | `OAuth2OpenController.java:165-170` |
| O4 | `GET /admin-api/system/oauth2/authorize?clientId=` | 需登录 | `OAuth2OpenController.java:186-190`；`PC/api/login.js:105` |
| O5 | `POST /admin-api/system/oauth2/authorize` | 需登录 | `OAuth2OpenController.java:211-222`；`PC/api/login.js:122` |

## 3. 对端：企业微信

| # | 名称 | 方向 | 路径 | 载荷 | 鉴权 | 频率 / SLA | 源码 |
|---|---|---|---|---|---|---|---|
| W1 | 社交授权跳转 | 入站 REST | `GET /admin-api/system/auth/social-auth-redirect?type=&redirectUri=` | → 授权 URL 字符串 | **permitAll** | — | `AuthController.java:226-237` |
| W2 | 社交登录（code） | 入站 REST | `POST /admin-api/system/auth/social-login` | `{type:int, code:str, state:str}` → `AuthLoginRespVO` | **permitAll**（要求社交账号已绑定） | — | `AuthController.java:239-246`；`AdminAuthServiceImpl.java:352-368` |
| W3 | **企业微信 OAuth 回调**（GET） | 入站 REST | `GET /admin-api/system/auth/social-login-get?code=` | 先换 `userid`，再按 weComId 查用户 → **302 到 `http://${PSA_WEB_ORIGIN}/index?accessToken=…&refreshToken=…`（明文 http）** | **permitAll**；凭证是企业微信的 code | — | `AuthController.java:248-264`；`AdminAuthServiceImpl.java:372-397` |
| W4 | 企业微信小程序登录 | 入站 REST | `POST /admin-api/system/auth/qy-wechat/login?code=` | → accessToken 字符串 | **permitAll**；code 走 jscode2session | 企业微信调用超时 5 秒 | `AuthController.java:282-289`；`AdminAuthServiceImpl.java:462-541` |
| W5 | **企业微信 H5 登录** | 入站 REST | `POST /admin-api/system/auth/qy-wechat/h5-login`，表单 `code=` | → accessToken 字符串（**不返回 refreshToken**） | **permitAll**；code 走 `cgi-bin/auth/getuserinfo` | 超时 5 秒；按环境用 PSA 或 PSA_prod 的 Secret | `AuthController.java:291-298`；`AdminAuthServiceImpl.java:544-626`；`H5/pages/qyLogin/qyLogin.tsx:199-223` |
| W6 | 回调 URL 校验 | 入站 REST | `GET /admin-api/fst/we-com/callback/check-url?msg_signature=&timestamp=&nonce=&echostr=` | → echostr 明文 | **permitAll**；SHA1 签名 + AES 解密（`WXBizJsonMsgCrypt`），Token 和 AESKey 硬编码（打码：`FSTC:127,131`） | 配置回调时调用一次 | `FST/controller/admin/wecom/WeComCallbackController.java:46-62` |
| W7 | **模板卡片按钮回调** | 入站 REST | `POST /admin-api/fst/we-com/callback/check-url?msg_signature&timestamp&nonce`，body 为加密 XML | 解密后读 `ResponseCode`、`EventKey`（`task_{1接受/0拒绝}_{taskId}_{userId}`，格式由 split 推断）、`FromUserName`、`AgentID`；**登录人取自 EventKey[3]**（`:113`）；动作是 `resourceService.acceptTask/refuseTask` | **permitAll**；靠 `DecryptMsg` 验签 | 用户点一次触发一次 | `WeComCallbackController.java:65-142` |
| W8 | 获取 access_token | 出站 HTTP | `GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=${WECOM_CORP_ID}&corpsecret=${WECOM_CORP_SECRET}` | → `{errcode, access_token, expires_in}` | Secret 硬编码（`CC:128-135`，打码） | **不缓存，每次调用都请求一次**；fst 的 RestTemplate 没设超时 | `SYS/service/common/CommonServiceImpl.java:24`；`AdminAuthServiceImpl.java:480,566`；`FST/service/common/CommonServiceImpl.java:400-418` |
| W9 | 网页授权身份 | 出站 HTTP | `GET .../cgi-bin/auth/getuserinfo?access_token=&code=` | → `{errcode, UserId \| userid}` | access_token | 超时 5 秒 | `AdminAuthServiceImpl.java:576-601,377-386`；`CC:161` |
| W10 | 小程序 code2session | 出站 HTTP | `GET .../cgi-bin/miniprogram/jscode2session?access_token=&js_code=&grant_type=authorization_code` | → `{errcode, userid, session_key}` | access_token | 超时 5 秒 | `AdminAuthServiceImpl.java:490-513`；`CC:162` |
| W11 | **应用消息（审批通知）** | 出站 HTTP | `POST .../cgi-bin/message/send?access_token=` | `{touser:weComId, toparty:"@all", totag:"@all", msgtype: template_card\|textcard\|news\|markdown, agentid:${WECOM_AGENT_ID}, template_card:{card_type:"text_notice", source{icon_url,desc,desc_color}, main_title{title}, horizontal_content_list[{keyname,value}], task_id:uuid, card_action{type:1,url}, jump_list[{type:1,url,title}]}}` | access_token；agentid 生产 1000138、非生产 1000139（`CC:154-156`） | 每条待办、完成、知会各发一次；失败只记日志，不重试 | `FST/service/common/CommonServiceImpl.java:421-433`；`FST/service/message/MessageServiceImpl.java:140-149,153-334`；`FSTC:107` |
| W12 | 更新模板卡片 | 出站 HTTP | `POST .../cgi-bin/message/update_template_card?access_token=` | `{userids:[str], agentid:int, response_code:str, button:{replace_name:str}}` | access_token | 跟随 W7 | `WeComCallbackController.java:153-165`；`FSTC:109` |
| W13 | H5 OAuth 授权跳转 | 出站（浏览器） | `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${WECOM_CORP_ID}&redirect_uri=<H5 当前 URL>&response_type=code&scope=snsapi_base&agentid=${WECOM_AGENT_ID}&state=#wechat_redirect` | — | — | H5 没有 code 时触发 | `H5/pages/qyLogin/qyLogin.tsx:122-135`；`H5/config.ts:35,73` |

**卡片跳转地址：** `${fst.wecom.h5-base-url:https://psah5.forcitis.com}/#/pages/index/index`（待办、知会）或 `/#/pages/redirect/index?target=myProcess`（完成），见 `MessageServiceImpl.java:612-628`。

**通讯录：** 没有调用任何企业微信通讯录 API。`system_users.we_com_id` 只来自 A8 同步（`FST/service/sync/SyncServiceImpl.java:352,407`）。

## 4. 对端：致远 A8（含 OA 门户）

| # | 名称 | 方向 | 路径 / 主题 | 载荷 | 鉴权 | 频率 / SLA | 源码 |
|---|---|---|---|---|---|---|---|
| A1 | **A8 登录到老系统** | 入站 REST | `POST /admin-api/system/auth/a8-login?memberId=` | memberId 为老系统用户 ID（与 A8 ORG_MEMBER.ID 同源）→ **302 到 `http://${PSA_WEB_ORIGIN}/index?accessToken&refreshToken`** | **permitAll，不做任何校验** | — | `AuthController.java:104-117`；`AdminAuthServiceImpl.java:207-216` |
| A2 | **致远 SSO（验票）** | 入站 REST | `GET /admin-api/system/auth/seeyon-sso?ticket=` | ticket（不超过 256 字符）→ 服务端验票 → Redis `seeyon_sso:ticket:<t>` SETNX 防重放（保留 7 天）→ 302 到配置项 `redirect-url?accessToken&refreshToken` | **permitAll**；身份只认 OA 回验结果；`psa.seeyon-sso.enabled` 默认 false | 验票超时 5 秒 | `AuthController.java:119-132`；`AdminAuthServiceImpl.java:218-251`；`SYS/framework/seeyon/SeeyonSsoProperties.java:18-39`；`application.yaml:196-199` |
| A3 | OA 验票 | 出站 HTTP | `GET ${SEEYON_OA_BASE_URL}/seeyon/thirdpartyController.do?ticket=` | → `text/plain`，内容是 URL 编码的登录名（无效票据返回空串） | 无（凭 ticket） | 超时 5 秒；非 200 返回 OA 不可用错误 | `SYS/framework/seeyon/SeeyonSsoTicketClient.java:34-70` |
| A4 | 门户单点（YD） | 入站 REST | `GET /admin-api/system/auth/singleSignOn/YD-get?memberId=` | → 302 到 `https://${PSA_WEB_ORIGIN}?accessToken&refreshToken` | **permitAll，不做校验** | 已有报告的统计：15,281 次 | `AuthController.java:266-280` |
| A5 | 部门同步 | 定时任务 → 读 A8 库 → RPC 写 system | XXL `syncDeptJob` | 读 `a8_db.dbo.ORG_UNIT / ORG_RELATIONSHIP / ADDRESSBOOK / CTP_ENUM` → `[{id,code,name,parentId(由 PATH 反推),sort,deliveryFlag,status,leaderUserId,arUserId,category}]` → `POST /rpc-api/system/dept/syncDept` 做差异 upsert 和逻辑删除，设有源数据缩水保护 | `@DS("slave")` = `${A8_DB_URL}`；RPC 无鉴权 | cron 在 XXL-JOB 调度库里，仓库看不到 | `FST/trigger/task/SyncDeptJob.java:16`；`SyncServiceImpl.java:154-281`；`FST/dal/mysql/a8/A8DeptMapper.java:35-94`；`SYS/service/dept/DeptServiceImpl.java:201-250` |
| A6 | 人员同步 | 同上 | XXL `syncUserJob` | `ORG_MEMBER`（加 EXT_ATTR_21 生日）→ `batchCreateUsers` / `batchUpdateUsers1`，字段包括 `{id,deptId,nickname,username(手机号或 code),mobile,email,leaderUserId,weComId,postIds,billing,birthday,deleted,status}` | 同上 | 同上 | `SyncUserJob.java:17`；`SyncServiceImpl.java:285-442`；`A8UserMapper.java:24-30`；`AdminUserApi.java:97,105` |
| A7 | 岗位同步 | 同上 | XXL `syncPostsJob` | `ORG_POST(IS_DELETED=0)` → `batchCreatePosts` / `batchUpdatePost`，字段 `{id,code,name,status,sort}` | 同上 | 同上 | `SyncPostsJob.java:16`；`SyncServiceImpl.java:445-487`；`PostApi.java:39,43` |
| A8 | A8 库直读 | 跨域 DB 读 | `a8_db.dbo.*`（SQL Server，slave 数据源） | 见 A5–A7 | 数据源凭据放在 Nacos `psa-datasource*.yaml` | — | `FST/../resources/application-prod.yaml:41` |
| — | **A8 待办推送** | — | 没有找到 | — | — | — | 已全仓搜索 `seeyon` / `affair`，只有 A2 和 A3 |

## 5. 对端：KM 知识门户

| # | 名称 | 方向 | 路径 | 载荷 | 鉴权 | 源码 |
|---|---|---|---|---|---|---|
| K1 | KM 单点 | 入站 REST | `POST /admin-api/system/auth/login/KM?memberId=` | memberId（长度超过 11 时按用户 ID 换成手机号）→ 302 到 `${KM_BASE_URL}/jump.html?token=…&returnUrl=${KM_BASE_URL}/index.html` | **permitAll，不做校验** | `AuthController.java:134-146` |
| K2 | 获取 KM 令牌 | 出站 HTTP | `POST ${KM_BASE_URL}/api/services/Org/UserLoginIntegrationByUserLoginName`（明文 http） | `{loginName:str, ipAddress:"1.1.1.1", integrationKey:${KM_INTEGRATION_KEY}}` → `{result:int, data:token, message}` | integrationKey 硬编码（`CC:144`，打码） | `AdminAuthServiceImpl.java:254-281`；`CC:170` |

## 6. 对端：BI

| # | 事实 | 源码 |
|---|---|---|
| B1 | `weiXinLogin(code,"BI")` 用 BI 的 Secret 换企业微信身份并返回 UUID，留有 TODO"登录 BI"；**没有任何调用方**（死代码） | `AdminAuthServiceImpl.java:166-204` |
| B2 | A8 同步原本走"BI 服务器接口"，已改为直读 A8 库（见代码注释） | `SyncServiceImpl.java:155,289,448` |

## 7. 对端：未知集成调用方

| # | 方法与路径 | 载荷 | 鉴权 | 源码 |
|---|---|---|---|---|
| X1 | `POST /admin-api/system/auth/customization-login` | `{userId:<username>}` → `AuthLoginRespVO` | **permitAll，不做校验** | `AuthController.java:88-94`；`AdminAuthServiceImpl.java:153-157` |
| X2 | `POST /admin-api/system/auth/customization-login/id` | `{userId:<long>}` → `AuthLoginRespVO` | **permitAll，不做校验** | `AuthController.java:96-102`；`AdminAuthServiceImpl.java:159-164` |

## 8. 对端：PC / H5 终端用户（审批）

### 8.1 运行态（流程实例与任务）

所有路径前缀为 `/admin-api`（PC）或 `/app-api`（H5）。

| # | 动作 | 方法与路径 | 请求载荷 | 鉴权 | 源码（admin / app） |
|---|---|---|---|---|---|
| P1 | **发起（自定义 OA）** | `POST /bpm/oa/customized/create`（admin 和 app 各一个） | `{paramsId:long, dataStamp:str(JSON), processKey:str, processInstanceVariables?:map, creator?:long, returnTaskId?:str}` → `true` | **permitAll**，并关闭数据权限 | `BPM/controller/admin/oa/CustomizedBpmController.java:29-38`；`BPM/controller/app/oa/CustomizedBpmAPPController.java:29-36`；`BPM/service/oa/CustomizedBpmOAServiceImpl.java:77-198`；PC 调用：`PC/api/public.js:46` |
| P2 | 发起（标准） | `POST /bpm/process-instance/create` | `{processDefinitionId, variables}` | 权限点 `bpm:process-instance:query` | `BPM/controller/admin/task/BpmProcessInstanceController.java:37` |
| P3 | 我的流程 | `GET /bpm/process-instance/my-page` | `{name,title,processDefinitionId,status,result,category,createTime[]}` 加分页 | `process-instance:query` | admin `:29`；app `BpmProcessInstanceAPPController.java:45` |
| P4 | 实例详情 | `GET /bpm/process-instance/get?id=` | — | `process-instance:query` | admin `:44`；app `:37` |
| P5 | **撤销（取消）** | `DELETE /bpm/process-instance/cancel` | `{id, reason, cancelAction?, inputs?:map}` | admin 要 `process-instance:cancel`；**app 只要 `:query`** | admin `:52`；app `:53` |
| P6 | 能否撤回 | `GET /bpm/process-instance/withdrawable` | `id` | `process-instance:query` | admin `:60` |
| P7 | **撤回修改** | `PUT /bpm/process-instance/withdraw-to-initiator` | `{id, reason}` | `process-instance:cancel` | admin `:69` |
| P8 | 待办分页 / 全部 | `GET /bpm/task/todo-page`、`/todo-list` | `{name,title,processInstanceId,assigneeUser[],createTime[]}` | `bpm:task:query` | `BPM/controller/admin/task/BpmTaskController.java:32,39`；app `BpmTaskAPPController.java:31,38` |
| P9 | 所有人待办 | `GET /bpm/task/todo-page/all` | 同上 | `bpm:task:query` | admin `:46` |
| P10 | **批量通过（代审）** | `PUT /bpm/task/batch/approve` | 同 P8 的筛选条件，对**所有人**的匹配待办逐条通过 | `bpm:task:update` | admin `:53`；`BpmTaskServiceImpl.java:430-455` |
| P11 | 已办 | `GET /bpm/task/done-page` | `{name,title,createTime[]}` | `bpm:task:query` | admin `:61`；app `:53` |
| P12 | 实例任务列表 | `GET /bpm/task/list-by-process-instance-id?processInstanceId=` | — | `bpm:task:query` | admin `:68`；app `:60` |
| P13 | **审批（通过 / 驳回）** | `PUT /bpm/task/decide` | `{decisions:[{taskId, result:int, reason}], approvalSnapshot?:map, actionValue?:str, inputs?:map}` → `true` 或 `WorkflowHookBeforeResultDTO` | 需登录，服务层校验办理人 | admin `:77-89`；app `:85` |
| P14 | 通过 / 不通过（app 旧接口） | `PUT /app-api/bpm/task/approve`、`/reject` | — | `bpm:task:update` | app `:69,77` |
| P15 | **退回发起人** | `PUT /bpm/task/return?hookInteraction=` | `{id, reason, actionValue?, inputs?, approvalSnapshot?}` | `isAuthenticated()`，加办理人校验 | admin `:92-100`；app `:95` |
| P16 | 可退回节点 | `GET /bpm/task/return-activity-list?taskId=` | — | 需登录（app 要 `task:update`） | admin `:102`；app `:103` |
| P17 | **退回到指定节点** | `PUT /bpm/task/return-to-activity` | `{id, targetTaskDefinitionKey, reason}` | 需登录（app 要 `task:update`） | admin `:109`；app `:110` |
| P18 | **转交（转派）** | `PUT /bpm/task/update-assignee` | `{id, assigneeUserId}` | `bpm:task:update` | admin `:117` |
| P19 | **加签** | `POST /bpm/task/add-countersign` | `{processInstanceId, taskDefinitionKey, assigneeUserIds:[long]}` | `bpm:task:update`，**不校验操作人与实例的关系** | admin `:125-131` |
| P20 | 任务详情 / 是否已审 | `GET /app-api/bpm/taskget`、`/taskcheck-approval` | — | `bpm:task:query` | app `:45,118` |
| P21 | 流程图高亮 | `GET /bpm/activity/list` | `processInstanceId` | `bpm:task:query` | `BpmActivityController.java:30` |
| P22 | 统一审批（表单） | `POST /admin-api/fst/workflow-form/approve` | 审批表单 VO（按节点字段权限采纳表头字段） | 需登录 | `FST/controller/admin/workflowform/WorkflowApproveController.java:36`；`PC/api/project/workflowForm.js:10`；`H5/api/process/index.ts:193-197` |
| P23 | 审批摘要 / 提交地址候选 | `GET /admin-api/fst/workflow-form/summary`、`/submit-candidates` | `businessKey, processInstanceId` | 需登录 | `WorkflowSummaryController.java:33`；`WorkflowSubmitCandidateController.java:54` |
| P24 | 待发送流程（出箱） | `GET /{admin,app}-api/fst/process/to-be-sent/page` | 分页 → `psa_process_to_be_sent` 中当前用户的行 | 需登录 | `FST/controller/admin/bpm/BPMController.java:30`；`FST/controller/app/bpm/BPMAPPController.java:30`；`ProcessToBeSentServiceImpl.java:92-95` |
| P25 | 工时流程编辑（H5） | `GET /app-api/fst/man-hour/process-edit-detail`、`POST /process-edit-submit`（admin 端也有一对） | `paramsId` / 编辑后的明细 | 需登录 | `FST/controller/app/manhour/ManHourAPPController.java:110,172`；`FST/controller/admin/manhour/ManHourController.java:227,235`；`H5/api/process/index.ts:143-156` |

### 8.2 流程定义（`bpm:service/definition`，共 31 个端点）

| 控制器 | 端点 | 鉴权 | 源码 |
|---|---|---|---|
| 流程定义 | `GET /bpm/process-definition/page`、`/list`、`/get-bpmn-xml`、`/customized/get-bpmn-xml` | `bpm:process-definition:query` | `BPM/controller/admin/definition/BpmProcessDefinitionController.java:36,44,52,61` |
| 模型 | `GET page`、`GET get`、`POST create`、`PUT update`、`POST import`、`POST deploy`、`PUT update-state`、`DELETE delete` | `bpm:model:*`（page 只要登录） | `BpmModelController.java:32-89` |
| 表单 | `/bpm/form/` 下的 create、update、delete、get、list-all-simple、page | `bpm:form:*` | `BpmFormController.java:31-71` |
| 分配规则 | `/bpm/task-assign-rule/` 下的 list、create、update | `bpm:task-assign-rule:*` | `BpmTaskAssignRuleController.java:31-51` |
| 用户组 | `/bpm/user-group/` 下的 create、update、delete、get、page、list-all-simple | `bpm:user-group:*` | `BpmUserGroupController.java:35-76` |
| 节点字段权限 | `/bpm/node-field-permission/` 下的 field-catalog、list、by-task、save | `bpm:node-field-permission:*` 或 `bpm:task:query` | `BpmNodeFieldPermissionController.java:37-77` |

## 9. 对端：内部业务模块

### 9.1 RocketMQ 主题

| # | 主题（binding） | 生产者 | 消费者（group） | 消息体 | 投递语义 | 源码 |
|---|---|---|---|---|---|---|
| M1 | `topic-bpm-approval-task-result`（`bpmApprovalTaskResult-out-0`） | bpm：任务完成后（`BpmTaskServiceImpl.java:1025,1059,1062`） | fst `ApprovalTaskResultConsumer`（`fst_approval_task_result_consumer_group`） | `{businessKey, paramsId:long, processInstanceId, taskId, taskDefinitionKey, taskName, assigneeId:long, result:int, reason, afterHookKey?, approvalSnapshot?:map, rowId?, rowIds?:[str], createTime}` | 事务提交后发；**至少一次**（Redis 暂存 + 出箱 `psa_bpm_mq_outbox` + 清扫 / 重投 / 对账）；消费端无去重表，失败抛错让 MQ 重投 | `BPM/mq/producer/BpmApprovalTaskResultProducer.java:63-167`；`BpmApprovalMqReliableSender.java:75,144`；`FST/mq/consumer/approval/ApprovalTaskResultConsumer.java` |
| M2 | `topic-bpm-process-result` | bpm：流程到达终态（通过、驳回、真撤回）后，在 `AFTER_COMMIT` 发 | fst `ProcessResultConsumer` | `{businessKey, paramsId, processInstanceId, result:int(2通过/3不通过/4取消), reason, afterHookKey?, cancelAction?, createTime}` | 同上；撤回时先写出箱（`BpmProcessInstanceServiceImpl.java:500`） | `BpmProcessResultProducer.java:47-88`；`ProcessResultConsumer.java` |
| M3 | `topic-bpm-task-created` | bpm：Flowable 创建任务时，且节点配置了 Hook | fst `TaskCreatedConsumer` | `{businessKey, paramsId, processInstanceId, taskId, taskDefinitionKey, afterHookKey, createTime}` | 先写出箱，提交后发送 | `BPM/framework/flowable/core/listener/BpmTaskEventListener.java:154-158` |
| M4 | `topic-bpm-activity-event` | bpm：BPMN 显式配置的活动执行监听器 | fst `ActivityEventConsumer`（有去重表 `insertIfAbsent(messageId)`） | `{messageId, businessKey, paramsId, processInstanceId, activityId, eventName, createTime}` | 先写出箱，提交后发送 | `BpmActivityEventExecutionListener.java:52-56`；`FST/mq/consumer/approval/ActivityEventConsumer.java` |
| M5 | `topic-websocket-01`（`websocket-out-0`） | bpm：`BpmMessageServiceImpl` 在待办、完成、知会时发；fst 的任务消息也发这里 | fst `WebsocketConsumer`（`fst_websocket_send_consumer_group`）→ 企业微信 W11 | `PSAMessageModel{userId:long, type:int(0流程/1任务), stage:int(0待办/1完成/2知会), id:str(待办是 taskId，其余是实例 ID), realUserId?}` | **发出即不管**，异常只记日志；非生产环境把收件人强制改成某个固定用户（`WebsocketConsumer.java` 中 `accept` 的第一个分支） | `BPM/service/message/BpmMessageServiceImpl.java:93-148`；`FST/mq/consumer/websocket/WebsocketConsumer.java` |
| M6 | `topic-org-change`（`orgChange-out-0`） | system：部门 / 人员增删改及同步时，在 `AFTER_COMMIT` 发 | fst `OrgChangeConsumer`（`fst_org_change_consumer_group`） | `OrgChangeMessage{eventId, occurredTime, items:[{entityType, changeType, entityId, entityName, changedFields:[OrgFieldChange]}]}` | **至多一次**（发送失败直接丢弃），依赖 fst 缓存 1 分钟过期来自愈 | `SYS/mq/producer/org/OrgChangeProducer.java:13-49`；`DeptServiceImpl.java:246`；`AdminUserServiceImpl.java:150-411`；`FST/trigger/mq/organization/OrgChangeConsumer.java` |
| M7 | `springCloudBus` | system：`DeptRefreshMessage`、`UserRefreshMessage`、`Role` / `Menu` / `RoleMenu` / `UserRoleRefreshMessage` | 各服务本地缓存 | Spring Cloud Bus 事件 | 至多一次 | `SYS/mq/producer/dept/DeptProducer.java:16-18`；`user/UserProducer.java:15-17`；`permission/*.java` |
| M8 | `system_sms_send` | bpm：任务分配时经 `SmsSendApi` 发短信 | system `smsSendConsumer` | 短信模板参数（含 `detailUrl`） | 异常吞掉 | `BpmMessageServiceImpl.java:80-90`；system `application.yaml` 中的 stream bindings |

### 9.2 定时任务

| # | 名称 | 类型 | 频率 | 动作 | 源码 |
|---|---|---|---|---|---|
| J1–J3 | `syncDeptJob` / `syncUserJob` / `syncPostsJob` | XXL-JOB | cron 存在调度库，仓库不可见 | 见 A5–A7 | `FST/trigger/task/Sync*Job.java` |
| J4 | `asyncCreateProcessJob` | XXL-JOB | cron 不可见；每批 100 条 | 扫 `psa_process_to_be_sent`（`status=0` 且未超过最大重试次数）→ Redisson 锁 → `createOA` 或 `continue-after-return` RPC → 工时状态推进到"审批中"；失败调 `markFailed` | `FST/trigger/task/AsyncCreateProcessJob.java:23-34`；`FST/service/bpm/ProcessToBeSentServiceImpl.java:97-200` |
| J5 | `reliableDeliveryDispatchJob` | XXL-JOB | cron 不可见 | `psa_reliable_delivery_task` 到期派发给 15 个 `DeliveryHandler`（分贝通、U8C 等） | `FST/trigger/task/ReliableDeliveryDispatchJob.java:26-30`；`FST/application/platform/integration/reliabledelivery/ReliableDeliveryDispatcher.java:89-190` |
| J6 | bpm MQ 清扫 | `@Scheduled` | 60 秒 | 陈旧的 Redis 暂存转入出箱表 | `BpmApprovalMqReliableSender.java:327` |
| J7 | bpm MQ 重投 | `@Scheduled` | 30 秒；退避 30/60/120/300/600/1800 秒，最多 10 次 | 出箱表重投 | `:97-98,355` |
| J8 | bpm MQ 对账 | `@Scheduled` | 600 秒，回看 24 小时 | 补发漏掉的消息 | `:100-102,402` |
| J9–J10 | `syncWorkHoursJob` / `calculateProjectWipJob` | XXL-JOB | 不可见 | 工时归档、WIP 汇总（fst 域内） | `FST/trigger/task/syncWorkHoursJob.java:16`；`SyncProjectWipJob.java:19` |

### 9.3 内部 RPC（`/rpc-api/**`，服务级 permitAll）

| 被调方 | 端点 | 调用方 | 源码 |
|---|---|---|---|
| bpm `/rpc-api/bpm/process-task/*`（23 个） | `get`、`is-participant`、`is-task-assignable`、`create`、`create-with-flatten`、`create-with-flatten-idempotent`、`approval-batches/submit`、`required-fields`、`continue-after-return`、`cancel-started-instance`、`approval-batches/cancel`、`returned-to-initiator`、`return-task-id`、`active-approval-task`、`active-task`、`active-approval-task-by-definition-key`、`close`、`field-permission`、`complete-approval`、`countersign/add`、`set-variables`、`get-variable`、`travel-expense-allocation-payout` | fst | `psa-backend/yudao-module-bpm/yudao-module-bpm-api/.../api/task/BpmProcessTaskApi.java:38-206` |
| bpm 其他（2 个） | `process-instance-ext/get`、`customized-bpm/get` | fst | `BpmProcessInstanceExtApi.java:27`；`CustomizedBpmApi.java:14` |
| fst（3 个） | `POST /rpc-api/fst/workflow-hook/before`（`WorkflowHookExecuteReqDTO{hookKey,businessKey,paramsId,processInstanceId,taskId,taskDefinitionKey,rowIds,rejectedRowIds,operatorId,action,cancelAction,reason,approvalSnapshot,processVariableSnapshot,actionValue,inputs}` → `{outcome,code,msg,actionValue,cancelAction,inputs[],committed,interaction,title,...,variableUpdates}`）；`GET workflow-form/field-catalog`、`/descriptor` | bpm（`BpmApprovalTaskHookService.java:124`） | `psa-backend/yudao-module-fst/yudao-module-fst-api/.../workflowhook/WorkflowHookApi.java:24-26`；`workflowform/WorkflowFormApi.java:24-30` |
| system（5 个） | `dept/syncDept`、`user/batchCreateUsers`、`user/batchUpdateUsers1`、`post/batchCreatePosts`、`post/batchUpdatePost` | fst 同步任务 | `DeptApi.java:59`；`AdminUserApi.java:97,105`；`PostApi.java:39,43` |

### 9.4 跨域表读写

| # | 表 | 读写方 | 事实 | 源码 |
|---|---|---|---|---|
| D1 | `psa_process_to_be_sent`（fst 和 bpm 两边都有 DO 映射） | fst：insert / page / selectPending / delete / markFailed；**bpm：createOA 成功后按 businessId 删除** | 唯一索引 `business_id`；列 `{id, business_id, code, title, creator, creator_name, dept_name, post_name, create_time, process_params(JSON: dataStamp, variables, titleDate), process_business_key, action_type(CREATE_OA\|CONTINUE_AFTER_RETURN), status(0待发起/2失败), retry_count, last_error, update_time}` | `FST/service/bpm/ProcessToBeSentServiceImpl.java:73-88,94,99,118,170,195`；`BPM/service/oa/CustomizedBpmOAServiceImpl.java:193-195`；`psa-backend/sql/sqlserver/356_process_to_be_sent_async.sql`、`368_process_to_be_sent_action.sql` |
| D2 | `bpm_process_instance_ext` | fst 直接读（用于组装通知卡片） | fst 有独立的 Mapper | `FST/dal/dataobject/bpm/BpmProcessInstanceExtDO.java:22`；`MessageServiceImpl.java:192,281` |
| D3 | `system_user_role`（表名由 Mapper 推断） | fst `setDefaultRole` 写入 | **fst 侧没有调用方**（system 自己有 `PermissionServiceImpl.java:513`） | `FST/service/sync/SyncServiceImpl.java:490-506` |
| D4 | `a8_db.dbo.*` | fst 只读 | 见 A8 | 同 A5–A7 |

**自有状态表（不计入接口数）：** `psa_bpm_mq_outbox`（`BPM/dal/dataobject/mq/BpmMqOutboxDO.java:18`）、`psa_reliable_delivery_task` 和 `_attempt`（`FST/infrastructure/platform/integration/reliabledelivery/*DO.java`）、`psa_workflow_start_round`（`FST/infrastructure/platform/workflow/WorkflowStartRoundDO.java:11`，幂等键 `processKey:businessId:round`，见 `WorkflowStartRoundService.java`）。

## 10. 前端：登录页、路由守卫、菜单加载

| # | 端 | 入口 | 调用的接口 | 源码 |
|---|---|---|---|---|
| F1 | PC | `/login` | `captcha/get`、`captcha/check` → `auth/login`（或 sms-login）；`tenant/get-id-by-name` **后端不存在**；社交登录走 W1 和 W2 | `PC/views/login.vue:129,214-219,254-277`；`PC/api/login.js:17,51,59,72,84`；`PC/api/system/tenant.js:6` |
| F2 | PC | 全局 `beforeEach` | URL 上带 `accessToken`/`refreshToken` 时存入 localStorage 并从地址栏抹掉；有令牌 → `GetInfo`（U4，加 `fst/user/get`）→ `GenerateRoutes`（U6）→ 动态 `addRoutes`；无令牌 → 白名单 `['/login','/social-login','/auth-redirect','/bind','/register','/oauthLogin/gitee']`，否则跳 `/login?redirect=` | `PC/permission.js:13,21-82`；`PC/store/modules/user.js:117-154`；`PC/store/modules/permission.js:31-34` |
| F3 | PC | 令牌刷新 | 收到 401 → U3 → 重放原请求 | `PC/utils/request.js:122-152` |
| F4 | PC | 登出 | Navbar → `LogOut` → U2 | `PC/layout/components/Navbar.vue:103`；`PC/api/login.js:43` |
| F5 | PC | 记住我 | 密码用 RSA 公钥加密后存 localStorage，**私钥也打包在前端**（打码） | `PC/utils/auth.js:53-60`；`PC/utils/jsencrypt.js:5,8` |
| F6 | PC | 流程实例页（index、create、detail） | P1、P2–P7、P12、P13、P15–P19、P22 | `PC/views/bpm/processInstance/*`；`PC/api/bpm/processInstance.js:5-56`；`PC/api/bpm/task.js` |
| F7 | PC | 任务页（todo、done、todoAll） | P8–P11 | `PC/views/bpm/task/*.vue`；`PC/api/bpm/task.js:5-38` |
| F8 | PC | 前端调用了但后端不存在的接口 | `/bpm/task/complete`、`/bpm/task/back`（`backTask` 只在注释里引用） | `PC/api/bpm/task.js:45,88`；`PC/views/bpm/processInstance/detail.vue:1315` |
| F9 | H5 | `pages/qyLogin/qyLogin` | U10 → 没有 code 时走 W13 → W5 → 存 `App-Token` → reLaunch | `H5/pages/qyLogin/qyLogin.tsx:12-23,122-247` |
| F10 | H5 | 路由守卫（Taro 拦截器） | 拦截 navigateTo、redirectTo、reLaunch、switchTab；白名单 `register`、`webview`、`qyLogin` | `H5/permission.ts:19-75` |
| F11 | H5 | `pages/login/login`（账号登录） | 走 admin-api 的 U1；`getInfo` 调 `/app-api/system/user/profile/get`；登出走 `config.logoutUrl`；`/login`、`/register`、`/captchaImage` 三个调用**后端不存在**（推断是若依框架遗留） | `H5/api/login.ts:3-12,32-82`；`H5/config.ts:63,68` |
| F12 | H5 | `pages/work/process`（processEdit、toBeSent） | P25；toBeSent 页实际调的是 P3 加 P5，**没有用 P24** | `H5/pages/work/process/processEdit.tsx:4-7`；`toBeSent.tsx:4-9`；`H5/api/process/index.ts:104-156` |

## 11. 重点接口规格

### 11.1 OpenAPI：认证

```yaml
openapi: 3.0.3
info: { title: PSA legacy auth (as-is), version: legacy }
servers: [ { url: "https://${PSA_API_ORIGIN}/admin-api" } ]
components:
  securitySchemes:
    bearer: { type: http, scheme: bearer }
  schemas:
    AuthLoginResp:
      type: object
      properties:
        userId: { type: integer, format: int64 }
        accessToken: { type: string }
        refreshToken: { type: string }
        expiresTime: { type: string, format: date-time }
    CommonResult:
      type: object
      properties: { code: { type: integer }, msg: { type: string }, data: {} }
paths:
  /system/auth/login:
    post:
      security: []            # @PermitAll; prod captcha disabled
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [username, password]
              properties:
                username: { type: string }
                password: { type: string }
                captchaVerification: { type: string }
                socialType: { type: integer }
                socialCode: { type: string }
                socialState: { type: string }
      responses: { "200": { description: CommonResult<AuthLoginResp> } }
  /system/auth/customization-login:
    post:
      security: []            # @PermitAll — NO proof of identity (SEC-001)
      requestBody:
        content:
          application/json:
            schema: { type: object, properties: { userId: { type: string, description: "username" } } }
      responses: { "200": { description: CommonResult<AuthLoginResp> } }
  /system/auth/seeyon-sso:
    get:
      security: []            # @PermitAll; feature flag psa.seeyon-sso.enabled (default false)
      parameters:
        - { name: ticket, in: query, required: true, schema: { type: string, maxLength: 256 } }
      responses:
        "302":
          description: "Location: ${PSA_SSO_REDIRECT_URL}?accessToken=..&refreshToken=.."
  /system/auth/qy-wechat/h5-login:
    post:
      security: []            # @PermitAll; identity proven by WeCom OAuth code
      requestBody:
        content:
          application/x-www-form-urlencoded:
            schema: { type: object, required: [code], properties: { code: { type: string } } }
      responses: { "200": { description: "CommonResult<string accessToken> (no refreshToken)" } }
  /system/auth/refresh-token:
    post:
      security: []
      parameters: [ { name: refreshToken, in: query, required: true, schema: { type: string } } ]
      responses: { "200": { description: CommonResult<AuthLoginResp> } }
  /system/auth/list-menus:
    get:
      security: [ { bearer: [] } ]
      responses: { "200": { description: "CommonResult<[{id,parentId,name,path,component,icon,visible,keepAlive,children}]>" } }
```

### 11.2 OpenAPI：企业微信回调（入站）与应用消息（出站依赖）

```yaml
openapi: 3.0.3
info: { title: WeCom integration (as-is), version: legacy }
paths:
  /admin-api/fst/we-com/callback/check-url:
    get:
      security: []   # verified by msg_signature (SHA1 over ${WECOM_CB_TOKEN}, timestamp, nonce, echostr)
      parameters:
        - { name: msg_signature, in: query, schema: { type: string } }
        - { name: timestamp, in: query, schema: { type: string } }
        - { name: nonce, in: query, schema: { type: string } }
        - { name: echostr, in: query, schema: { type: string } }
      responses: { "200": { description: "plaintext echostr (AES ${WECOM_CB_AES_KEY})" } }
    post:
      security: []
      parameters:
        - { name: msg_signature, in: query, required: true, schema: { type: string } }
        - { name: timestamp, in: query, required: true, schema: { type: string } }
        - { name: nonce, in: query, required: true, schema: { type: string } }
      requestBody:
        content:
          text/xml:
            schema:
              type: string
              description: >
                Encrypted <xml>; decrypted fields used: ResponseCode, EventKey
                ("task_{1|0}_{taskId}_{psaUserId}", inferred), FromUserName, AgentID
      responses: { "200": { description: "CommonResult<boolean>" } }
  https://qyapi.weixin.qq.com/cgi-bin/message/send:
    post:
      parameters: [ { name: access_token, in: query, required: true, schema: { type: string } } ]
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                touser: { type: string, description: system_users.we_com_id }
                toparty: { type: string, example: "@all", description: "AS-IS hardcoded" }
                totag: { type: string, example: "@all", description: "AS-IS hardcoded" }
                msgtype: { type: string, enum: [template_card, textcard, news, markdown] }
                agentid: { type: integer, description: "${WECOM_AGENT_ID}" }
                template_card:
                  type: object
                  properties:
                    card_type: { type: string, example: text_notice }
                    source: { type: object, properties: { icon_url: {type: string}, desc: {type: string}, desc_color: {type: integer} } }
                    main_title: { type: object, properties: { title: { type: string } } }
                    horizontal_content_list: { type: array, items: { type: object, properties: { keyname: {type: string}, value: {type: string} } } }
                    task_id: { type: string }
                    card_action: { type: object, properties: { type: { type: integer, enum: [1] }, url: { type: string } } }
                    jump_list: { type: array, items: { type: object } }
```

### 11.3 OpenAPI：审批

```yaml
openapi: 3.0.3
info: { title: PSA BPM (as-is), version: legacy }
paths:
  /admin-api/bpm/oa/customized/create:
    post:
      security: []     # @PermitAll + @DataPermission(enable=false) — SEC-005
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [paramsId, dataStamp, processKey]
              properties:
                paramsId: { type: integer, format: int64, description: business doc id }
                dataStamp: { type: string, description: JSON snapshot }
                processKey: { type: string, description: BPMN process key, e.g. TIMESHEET }
                processInstanceVariables: { type: object, additionalProperties: true }
                creator: { type: integer, format: int64, description: "falls back to login user" }
                returnTaskId: { type: string }
      responses: { "200": { description: CommonResult<boolean> } }
  /admin-api/bpm/task/decide:
    put:
      security: [ { bearer: [] } ]   # no permission point; service-layer assignee check
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                decisions:
                  type: array
                  items:
                    type: object
                    properties:
                      taskId: { type: string }
                      result: { type: integer, description: "2=approve,3=reject" }
                      reason: { type: string }
                approvalSnapshot: { type: object }
                actionValue: { type: string }
                inputs: { type: object }
      responses: { "200": { description: "CommonResult<true | WorkflowHookBeforeResultDTO>" } }
  /admin-api/bpm/task/return:
    put:
      security: [ { bearer: [] } ]
      parameters: [ { name: hookInteraction, in: query, schema: { type: boolean, default: false } } ]
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties: { id: {type: string}, reason: {type: string}, actionValue: {type: string}, inputs: {type: object}, approvalSnapshot: {type: object} }
  /admin-api/bpm/process-instance/cancel:
    delete:
      security: [ { bearer: [] } ]   # bpm:process-instance:cancel (app variant: :query only)
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties: { id: {type: string}, reason: {type: string}, cancelAction: {type: string}, inputs: {type: object} }
```

### 11.4 AsyncAPI：审批事件

```yaml
asyncapi: 2.6.0
info: { title: PSA BPM events (as-is), version: legacy }
servers:
  rocketmq: { url: "${ROCKETMQ_NAMESRV}", protocol: rocketmq }
channels:
  topic-bpm-approval-task-result:
    publish:
      summary: bpm → fst, after task completion commit; at-least-once (Redis staging + psa_bpm_mq_outbox)
      message:
        payload:
          type: object
          properties:
            businessKey: { type: string, description: "process definition key" }
            paramsId: { type: integer, format: int64 }
            processInstanceId: { type: string }
            taskId: { type: string }
            taskDefinitionKey: { type: string }
            taskName: { type: string }
            assigneeId: { type: integer, format: int64 }
            result: { type: integer }
            reason: { type: string }
            afterHookKey: { type: string }
            approvalSnapshot: { type: object }
            rowId: { type: string }
            rowIds: { type: array, items: { type: string } }
            createTime: { type: string, format: date-time }
    subscribe: { operationId: fst_approval_task_result_consumer_group }
  topic-bpm-process-result:
    publish:
      summary: bpm → fst on APPROVE/REJECT/real CANCEL, AFTER_COMMIT
      message:
        payload:
          type: object
          properties:
            businessKey: { type: string }
            paramsId: { type: integer, format: int64 }
            processInstanceId: { type: string }
            result: { type: integer, enum: [2, 3, 4] }
            reason: { type: string }
            afterHookKey: { type: string }
            cancelAction: { type: string }
            createTime: { type: string, format: date-time }
  topic-websocket-01:
    publish:
      summary: bpm(/fst) → fst notifier → WeCom; fire-and-forget
      message:
        payload:
          type: object
          properties:
            userId: { type: integer, format: int64 }
            type: { type: integer, enum: [0, 1], description: "0=流程,1=任务" }
            stage: { type: integer, enum: [0, 1, 2], description: "0=待办(id=taskId),1=完成,2=知会(id=processInstanceId)" }
            id: { type: string }
            realUserId: { type: integer, format: int64 }
  topic-org-change:
    publish:
      summary: system → fst, AFTER_COMMIT, at-most-once
      message:
        payload:
          type: object
          properties:
            eventId: { type: string }
            occurredTime: { type: string, format: date-time }
            items:
              type: array
              items:
                type: object
                properties:
                  entityType: { type: string }
                  changeType: { type: string }
                  entityId: { type: integer, format: int64 }
                  entityName: { type: string }
                  changedFields: { type: array, items: { type: object } }
```

## 12. 其他发现（风险与历史遗留）

- 短信验证码固定为 9999：`SYS/../resources/application.yaml:192-193`、`FST/../resources/application.yaml:283-284`，仓库内没有 profile 覆盖；`SmsCodeServiceImpl.java:72`。生产 Nacos 是否覆盖需要 SME 确认。
- 令牌在明文 http 的 URL 里传递：`AuthController.java:111,258`（http），`:275`（https）。PC 路由守卫会抹掉地址栏里的令牌（`PC/permission.js:25-30`），但令牌已经出现在 302 响应和服务端访问日志里。
- `qyH5Login` 的注释写"H5 用独立 Secret"，实际用的是 `WeComSecret.PSA` / `PSA_prod`（`AdminAuthServiceImpl.java:547-551`）。`H5` / `H5_prod` 两个常量没有被引用，取值与 PSA 前缀相同（打码对比，`CC:129-134`）。
- 本地环境的登录逻辑写死了一个固定用户 ID（`AdminAuthServiceImpl.java:518,606`）；`WebsocketConsumer` 在非生产环境把所有通知改发给同一个固定用户。
- 迁移脚本注释说部门同步是"全删全插"（`V20260829130000__add_category_to_system_dept.sql:3`），实际代码是差异比对（`DeptServiceImpl.java:201-250`），注释已过时。
- `MessageServiceImpl` 的"完成"分支每轮 sleep `i*1s+5s`，最多 100 轮，约 5,450 秒，会阻塞共享的消费线程。
- 待办通知查任务时有 5 次 × 300 毫秒的重试，用来容忍事务可见性延迟（`MessageServiceImpl.java:168-186`）。
- `add-countersign` 和 `batch/approve` 只靠菜单权限点控制，不校验操作人与实例的关系（`BpmTaskController.java:53-59,125-131`）。
- 本次扫描的源文件中没有发现针对 AI 分析工具的诱导性文本。

## 13. 置信度与缺口

- **确定（有代码行为证据）：** 端点、`permitAll` 状态、载荷字段、MQ 绑定、出箱表与定时周期、出站 URL。
- **推断：** EventKey 的段位含义、`a8-login` 和 `YD-get` 的实际调用方（只有 ApiOperation 文案）、D3 的表名。
- **无法确定：**
  - XXL-JOB 的 cron（在调度中心数据库里）。
  - Nacos 中 prod 的实际覆盖值：`seeyon-sso.enabled`、`sms-code`、`captcha`、`backendA` 限流配额。
  - 企业微信对 `toparty`/`totag` 等于 `"@all"` 的实际投递效果。
  - 各接口的调用量。已有报告只给出 4 个免密端点的统计。
- **想问 SME 的问题：**
  1. 门户端"YD"指什么系统？
  2. A8 是否需要接收老系统待办（目前完全没有）？
  3. `psa_process_to_be_sent` 在新平台归 bpm 还是 fst？
  4. `customization-login` 的真实调用方是谁？
