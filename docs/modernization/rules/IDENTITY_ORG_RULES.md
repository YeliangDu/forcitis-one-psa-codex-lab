# Identity & Organization business rules — RULE-316..RULE-376

Source: `/Users/yeliang/Projects/FORCITIS/psa` (read-only). Scope: Phase 1a platform bridge — `platform.identity` (login, tokens, function permission, data scope, WeCom identity) and `organization.workforce` (dept tree, people, posts, A8 sync, reporting line).
Credentials are masked as `***`. Already-covered rules (not repeated): RULE-250, RULE-255, RULE-256, RULE-257, RULE-307 (reporting line / dept roles in approval), RULE-114 (delivery dept flag), RULE-265 (creator from request, SEC-005), RULE-022/176/310 (module visibility).

| ID | Domain | Title | Priority | Review |
|---|---|---|---|---|
| RULE-316 | identity | Password login: same error for unknown user and wrong password; disabled users refused | P0 | Yes |
| RULE-317 | identity | Login form: account and password are 4–16 characters; account is letters and digits only | P1 | Yes |
| RULE-318 | identity | Passwordless "customization" login issues real tokens for any username or user id | P0 | Yes |
| RULE-319 | identity | A8 login trusts the memberId in the request and redirects with tokens in the URL | P0 | Yes |
| RULE-320 | identity | Seeyon (A8 portal) SSO: server-side ticket check, one use per ticket, enabled users only | P0 | No |
| RULE-321 | identity | Seeyon SSO input limits and configuration defaults | P1 | No |
| RULE-322 | identity | WeCom H5 OAuth login maps the WeCom userid to `we_com_id` exactly | P0 | Yes |
| RULE-323 | identity | WeCom mini-program login (jscode2session) uses the same `we_com_id` mapping | P1 | Yes |
| RULE-324 | identity | PC WeCom web login guesses the user from the email local part or an "f-stripped" id | P0 | Yes |
| RULE-325 | identity | WeCom social auto-binding is written under a different user type than it is read | P1 | Yes |
| RULE-326 | identity | Only password and Seeyon logins check the disabled status | P0 | Yes |
| RULE-327 | identity | Resigned (soft-deleted) users are kept out only by the lookup filter | P0 | Yes |
| RULE-328 | identity | SMS login: the code must be valid and the mobile must exist | P2 | Yes |
| RULE-329 | identity | KM token: anonymous caller, memberId used as a mobile number or a legacy-system id | P0 | Yes |
| RULE-330 | identity | Every login attempt is logged; success stamps last-login IP and time | P1 | No |
| RULE-331 | identity | Every login creates a refresh token and an access token for client "default" | P0 | Yes |
| RULE-332 | identity | Access-token expiry slides to now + 10 h on every check | P0 | Yes |
| RULE-333 | identity | Refresh revokes sibling access tokens but never rotates the refresh token | P0 | Yes |
| RULE-334 | identity | Logout deletes the access token and its refresh token | P1 | No |
| RULE-335 | identity | Four SSO paths put access and refresh tokens in the redirect URL | P0 | Yes |
| RULE-336 | identity | Services trust the `login-user` header, treat a bad token as anonymous, and ignore user type | P0 | Yes |
| RULE-337 | identity | The gateway does no authentication | P0 | Yes |
| RULE-338 | identity | Disabling, deleting or a resignation via sync does not revoke live tokens | P0 | Yes |
| RULE-339 | identity | H5 token check: present, known and unexpired (and the check extends it) | P2 | No |
| RULE-340 | identity | Role code `super_admin` bypasses every permission and role check | P0 | No |
| RULE-341 | identity | A function permission is granted when an enabled role links a menu with that permission string | P0 | No |
| RULE-342 | identity | Role-code check: the user holds an enabled role with one of the codes | P1 | No |
| RULE-343 | identity | Only enabled roles count; a user with no roles has no function permissions | P0 | No |
| RULE-344 | identity | Every user without a role silently gets role 2 whenever the user cache reloads | P0 | Yes |
| RULE-345 | identity | Role maintenance: reserved code, unique name and code, system roles frozen, default scope ALL | P0 | Yes |
| RULE-346 | identity | Role-menu and user-role assignment replace the full set | P1 | No |
| RULE-347 | identity | Menu tree integrity | P2 | No |
| RULE-348 | identity | Navigation shows enabled dirs and menus of enabled roles; permission info adds buttons | P1 | No |
| RULE-349 | identity | Business data scope = union over roles; ALL anywhere means unrestricted | P0 | Yes |
| RULE-350 | identity | Row filtering applies only to opt-in methods with `@DataOwner` fields | P0 | Yes |
| RULE-351 | identity | An empty data scope fails open | P0 | Yes |
| RULE-352 | identity | A second, different data-scope engine guards system user and dept writes | P1 | Yes |
| RULE-353 | identity | Dept dropdown narrows to own dept when exactly one visible user | P2 | No |
| RULE-354 | org | A8 dept tree: prefix scope, parent from PATH, fail on a broken tree | P0 | No |
| RULE-355 | org | A8 dept "disabled" test and how it maps to legacy-system status and deleted | P0 | Yes |
| RULE-356 | org | A8 is authoritative for dept leader, regional AR, category and delivery flag | P0 | Yes |
| RULE-357 | org | Dept sync: insert, update on change, soft-delete what A8 no longer returns, revive | P0 | Yes |
| RULE-358 | org | Dept sync aborts if A8 returns < 80 % of active depts | P0 | No |
| RULE-359 | org | Manual dept edits: parent valid, no cycles, unique name, no delete with children | P1 | Yes |
| RULE-360 | org | "Dept and below" = recursive children from a cache that includes deleted depts | P1 | Yes |
| RULE-361 | org | A8 user sync scope uses a substring match, unlike the dept prefix rule | P0 | Yes |
| RULE-362 | org | A new A8 person becomes a legacy-system user with username = phone and a default password | P0 | Yes |
| RULE-363 | org | Resignation = A8 login name is null → legacy-system user soft-deleted | P0 | Yes |
| RULE-364 | org | Rehire and phone change restore and re-enable the legacy-system user | P0 | Yes |
| RULE-365 | org | Per-field update semantics in user sync are asymmetric | P0 | Yes |
| RULE-366 | org | Users that drop out of the A8 result set are never deactivated | P0 | Yes |
| RULE-367 | org | Post sync only adds and renames | P1 | Yes |
| RULE-368 | org | Manual user maintenance: unique username, mobile and email; enabled dept and posts | P1 | Yes |
| RULE-369 | org | Excel user import: per-row validation, optional update, init password | P2 | Yes |
| RULE-370 | org | Password change rules | P1 | Yes |
| RULE-371 | org | "Effective user" = enabled and not deleted; the user cache also carries deleted users | P1 | No |
| RULE-372 | org | Two separate leader concepts: person leader and dept leader | P0 | Yes |
| RULE-373 | org | Org-change events: field diff after commit; dept sync events miss fields and repeat deletes | P1 | Yes |
| RULE-374 | identity | WeCom message recipients are resolved through `we_com_id` | P1 | Yes |
| RULE-375 | identity | Non-production task messages go to one hard-coded person; agentId chosen by environment | P2 | Yes |
| RULE-376 | identity | WeCom card callback takes the acting user from the button key, not from FromUserName | P0 | Yes |

---

## identity — authentication

### RULE-316: Password login: same error for unknown user and wrong password; disabled users refused
**Domain:** identity · **Category:** Validation · **Priority:** P0 · **SEC:** SEC-011
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:114-151`
**Plain English:** Account + password login checks the captcha (only if enabled), then the account, then the password, then the status. An unknown account and a wrong password give the same error. A disabled account is refused. If a social type is supplied, the social account is bound on success.
**Specification:**
  Given user "13800000000" with status 0 (enabled) and the correct password
  When they log in with account + password
  Then a token pair is issued and a LOGIN_USERNAME success log is written.
  Given the same user with status 1 (disabled)
  Then the login fails with AUTH_LOGIN_USER_DISABLED and a USER_DISABLED log is written.
  Given an unknown account or a wrong password
  Then the login fails with AUTH_LOGIN_BAD_CREDENTIALS.
**Parameters:** `yudao.captcha.enable` (default true; off in prod per SEC-011); no lockout, no attempt counter.
**Suspected defect:** No brute-force protection (no lockout, captcha off in prod). Combined with default passwords (RULE-362) this enables credential stuffing.
**Confidence:** High.
**Review needed:** Yes. This conflicts with ADR-0009 §3 (local password login removed except an admin emergency account). **Suggested fix:** Keep password login only for a break-glass admin with a strong password, MFA and lockout; employees use WeCom or Seeyon SSO.

### RULE-317: Login form: account and password are 4–16 characters; account is letters and digits only
**Domain:** identity · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/vo/AuthLoginReqVO.java:26-42`
**Plain English:** The account is required, 4–16 characters, letters and digits only. The password is required, 4–16 characters. The captcha is required only when captcha is enabled.
**Specification:**
  Given account "abc" (3 characters)
  When the login form is submitted
  Then it is rejected with "账号长度为 4-16 位".
**Parameters:** account `^[A-Za-z0-9]+$`, length 4–16; password length 4–16.
**Suspected defect:** A 4-character password minimum is weak. A8 users whose username is a long or non-alphanumeric employee code (RULE-362) can never pass this form.
**Confidence:** High.
**Review needed:** Yes. This is weak password policy (SEC-011). **Suggested fix:** If local login survives at all, require a minimum of 12 characters plus a complexity rule.

### RULE-318: Passwordless "customization" login issues real tokens for any username or user id
**Domain:** identity · **Category:** Policy · **Priority:** P0 · **SEC:** SEC-001
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/AuthController.java:88-102`
**Plain English:** Three anonymous endpoints issue a full token pair from only a username (`customization-login`) or a user id (`customization-login/id`, `singleSignOn/YD-get`). There is no credential, no status check and no caller check.
**Specification:**
  Given user id 1001 exists (any status)
  When anyone POSTs `{"userId":"1001"}` to `/system/auth/customization-login/id`
  Then a valid access token and refresh token for 1001 are returned (log type LOGIN_CUSTOMIZATION).
**Parameters:** Endpoints `customization-login` (username), `customization-login/id` (id), `singleSignOn/YD-get?memberId=` (id, redirect). Service: `AdminAuthServiceImpl.java:153-164`. YD-get: `AuthController.java:266-280`.
**Edge cases handled:** An unknown username gives a NullPointerException. An unknown id gives IndexOutOfBounds. Neither returns a clean error.
**Confidence:** High (prod usage confirmed in SECURITY_FINDINGS: YD-get 15,281 calls / 180 d).
**Review needed:** Yes. This is SEC-001 Critical and conflicts with ADR-0009 §3/§5. **Suggested fix:** Replace with signed, short-lived, one-time tickets per caller (BI/YD/KM), verified server-side, refusing disabled users and super admins.

### RULE-319: A8 login trusts the memberId in the request and redirects with tokens in the URL
**Domain:** identity · **Category:** Policy · **Priority:** P0 · **SEC:** SEC-001, SEC-019
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/AuthController.java:104-117`
**Plain English:** `a8-login` takes `memberId` (the legacy-system user id, which equals the A8 member id) from the request. It logs that user in without any proof and redirects to the legacy-system home page with both tokens in the query string.
**Specification:**
  Given A8 member 1001 = legacy-system user 1001
  When POST `/system/auth/a8-login?memberId=1001`
  Then redirect to `http://psa.forcitis.com/index?accessToken=…&refreshToken=…`.
**Parameters:** Service `AdminAuthServiceImpl.java:206-216`; redirect target is hard-coded over http.
**Confidence:** High.
**Review needed:** Yes. This conflicts with ADR-0009 (one-time tickets, no tokens in URL). **Suggested fix:** Route A8 entry through the Seeyon ticket flow (RULE-320) and issue a session via a one-time code exchange.

### RULE-320: Seeyon (A8 portal) SSO: server-side ticket check, one use per ticket, enabled users only
**Domain:** identity · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:218-251`
**Plain English:** The OA ticket is checked back against OA from the server. Only the login name OA returns is trusted, and each ticket can mint a token once. The login name must match a legacy-system username exactly, and the user must be enabled.
**Specification:**
  Given SSO is enabled and OA resolves ticket "T1" to login name "13800000000", a user with that username exists, and its status is 0
  When GET `/system/auth/seeyon-sso?ticket=T1`
  Then a token pair is issued and the browser is redirected to the configured URL.
  When "T1" is presented again within 7 days
  Then it is rejected (AUTH_SEEYON_SSO_TICKET_INVALID) and a BAD_CREDENTIALS log is written.
  Given the login name has no legacy-system user
  Then AUTH_SEEYON_SSO_USER_NOT_EXISTS is returned. A disabled user gets AUTH_LOGIN_USER_DISABLED.
**Parameters:** Replay key prefix `seeyon_sso:ticket:`; TTL `ticketReplayTtl` = 7 days; no fuzzy matching.
**Edge cases handled:** OA down gives AUTH_SEEYON_SSO_OA_UNAVAILABLE. The replay mark is set before the user lookup, so a ticket for an unknown user is also burned.
**Confidence:** High.
**Review needed:** No. This is the reference pattern for ADR-0009. Its token-in-URL redirect is covered by RULE-335.

### RULE-321: Seeyon SSO input limits and configuration defaults
**Domain:** identity · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/framework/seeyon/SeeyonSsoTicketClient.java:34-70`
**Plain English:** A ticket must be non-empty and at most 256 characters. OA's reply is URL-decoded and accepted as a login name only if it is non-blank, at most 64 characters, and contains no `<`, `>`, space, CR or LF. A non-200 reply means OA is unavailable. The feature is off by default, and the landing URL comes only from configuration.
**Specification:**
  Given OA returns an HTML login page for the ticket
  When it is parsed
  Then the login name is null and SSO fails as an invalid ticket.
**Parameters:** Ticket max 256 (`AdminAuthServiceImpl.java:112`); login name max 64; timeout 5 s; validate path `/seeyon/thirdpartyController.do`; `enabled=false`; replay TTL 7 d (`SeeyonSsoProperties.java:18-39`).
**Confidence:** High.
**Review needed:** No.

### RULE-322: WeCom H5 OAuth login maps the WeCom userid to `we_com_id` exactly
**Domain:** identity · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:543-626`
**Plain English:** The H5 app exchanges the OAuth `code` for a corp access token (using the production or non-production secret by environment), then calls `auth/getuserinfo`. It reads `UserId`, falling back to `userid`. External contacts have neither and are refused. The user is found by exact `system_users.we_com_id`, and only an access token is returned (no refresh token).
**Specification:**
  Given WeCom resolves code "C1" to UserId "zhangsan", and user 1001 has `we_com_id`="zhangsan"
  When POST `/system/auth/qy-wechat/h5-login?code=C1`
  Then an access token for 1001 is returned.
  Given no user has `we_com_id`="zhangsan"
  Then the error is "当前企业微信账号未绑定系统用户，请联系管理员".
  Given errcode 40029
  Then the error is "登录凭证已过期，请重新进入登录".
**Parameters:** corpId / corpSecret = `***` (`CommonConstants.WeComSecret.PSA` / `PSA_prod`); HTTP timeout 5 s; all errors use code 503.
**Edge cases handled:** A blank code is refused. Under the `local` profile the login always succeeds as a fixed developer user id (`-70157…`).
**Suspected defect:** The user's status is not checked (a disabled user can log in, see RULE-326). The local-profile bypass is hard-coded. The secrets are hard-coded (SEC-006).
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** After mapping, require status = enabled and deleted = false; remove the local bypass; inject secrets from configuration.

### RULE-323: WeCom mini-program login (jscode2session) uses the same `we_com_id` mapping
**Domain:** identity · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:461-541`
**Plain English:** Same as RULE-322, but the code is exchanged through `miniprogram/jscode2session` with the legacy-system secret, and the `userid` field is read.
**Specification:**
  Given js_code resolves to userid "zhangsan" bound to user 1001
  When POST `/system/auth/qy-wechat/login?code=…`
  Then an access token for 1001 is returned.
**Parameters:** Secret `***`; timeout 5 s; 40029 means expired.
**Suspected defect:** No status check. MessageServiceImpl states the WeCom app is now H5, not a mini-program (`MessageServiceImpl.java:600-606`), so this path may be dead.
**Confidence:** Medium. SME: is the mini-program still in use?
**Review needed:** Yes. **Suggested fix:** Retire this path if it is unused; otherwise apply the same status gate as RULE-322.

### RULE-324: PC WeCom web login guesses the user from the email local part or an "f-stripped" id
**Domain:** identity · **Category:** Calculation · **Priority:** P0 · **SEC:** SEC-019
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/social/SocialUserServiceImpl.java:169-194`
**Plain English:** For the PC `social-login-get` flow (`AdminAuthServiceImpl.java:371-397`), the WeCom userid is not looked up in `we_com_id`. If it contains "@", the user whose email local part matches (case-insensitive) is chosen. Otherwise every letter "f" is removed and the remainder is compared with the legacy-system user id. The first match in cache order wins, and deleted users are included.
**Specification:**
  Given WeCom userid "Zhang.San@forcitis.com" and user 1001 with email "zhang.san@corp.cn"
  When the PC user logs in through WeCom web
  Then user 1001 is logged in and redirected with tokens in the URL (`AuthController.java:248-264`).
  Given userid "f1001f"
  Then user 1001 is chosen.
**Parameters:** "@" detection; `replaceAll("f","")`.
**Suspected defect:** Different people can collide on an email local part across domains. Ids made of digits plus "f" can collide. The match is not the canonical `we_com_id` used by H5. No status check.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Map only through the `we_com_id` exact match (the same key as H5) and refuse unmapped users.

### RULE-325: WeCom social auto-binding is written under a different user type than it is read
**Domain:** identity · **Category:** Lifecycle · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/social/SocialUserServiceImpl.java:139-167`
**Plain English:** When a WeCom social account has no binding, it is auto-bound to the user found by RULE-324. The binding is looked up with the caller's user type (ADMIN) but inserted with type MEMBER, so it is never found again and is re-inserted on every login.
**Specification:**
  Given a WeCom social user S with no ADMIN binding, mapped to user 1001
  When the user logs in
  Then a MEMBER binding (1001, S) is inserted. On the next login the ADMIN lookup misses again and another row is inserted.
**Suspected defect:** Duplicate bindings pile up. `long userId = getUserIdByWeComId(...)` unboxes null when nothing matches, which throws a NullPointerException instead of AUTH_THIRD_LOGIN_NOT_BIND.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Drop social-bind tables (ADR-0019 does not migrate social login except WeCom) and use `we_com_id` only.

### RULE-326: Only password and Seeyon logins check the disabled status
**Domain:** identity · **Category:** Validation · **Priority:** P0 · **SEC:** SEC-001
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:153-216`
**Plain English:** Status = enabled is enforced only in `authenticate` (lines 127-131) and `seeyonSsoLogin` (lines 246-249). Customization, A8, YD, KM, SMS, WeCom H5/mini and PC social logins issue tokens to disabled users.
**Specification:**
  Given user 1001 with status 1 (disabled) and `we_com_id`="zhangsan"
  When they log in through WeCom H5
  Then a token is issued.
  When they log in with a password
  Then the login is refused.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Put one gate in the identity BC so that every login path requires status = enabled and deleted = false before a session is created.

### RULE-327: Resigned (soft-deleted) users are kept out only by the lookup filter
**Domain:** identity · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:153-164`
**Plain English:** Users soft-deleted by the A8 sync (RULE-363) cannot log in only because the user queries carry the logical-delete filter (`BaseDO.deleted` is `@TableLogic`). Lookup by username returns null and the login crashes with a NullPointerException. Lookup by id returns an empty list and crashes with IndexOutOfBounds. WeCom mapping returns "not bound".
**Specification:**
  Given user 1001 soft-deleted (deleted = 1)
  When `customization-login/id` is called with 1001
  Then the request fails with a server error (not a business error), and no token is issued.
**Confidence:** Medium. The behaviour is inferred from the logical-delete annotation and the code paths.
**Review needed:** Yes. **Suggested fix:** Add an explicit "employee resigned" check with a clear error, independent of ORM behaviour.

### RULE-328: SMS login: the code must be valid and the mobile must exist
**Domain:** identity · **Category:** Validation · **Priority:** P2
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:288-311`
**Plain English:** Sending a code requires a user with that mobile. Logging in consumes the code for scene ADMIN_MEMBER_LOGIN and then issues tokens. The status is not checked.
**Specification:**
  Given mobile 13800000000 is not registered
  When a code is requested
  Then AUTH_MOBILE_NOT_EXISTS is returned.
**Suspected defect:** Per ADR-0019 no SMS has ever been sent in prod, so this path is effectively unusable.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Do not migrate (ADR-0019).

### RULE-329: KM token: anonymous caller, memberId used as a mobile number or a legacy-system id
**Domain:** identity · **Category:** Policy · **Priority:** P0 · **SEC:** SEC-002
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:253-281`
**Plain English:** The anonymous `/login/KM` endpoint takes `memberId`. If it is longer than 11 characters it is treated as a legacy-system user id and replaced by that user's mobile; otherwise it is used directly as the KM login name. The legacy system then asks KM for a token using a shared integration key and a fixed IP "1.1.1.1", and redirects to the KM jump page over http with the token in the URL (`AuthController.java:134-146`).
**Specification:**
  Given memberId "13800000000" (11 characters)
  When POST `/system/auth/login/KM?memberId=13800000000`
  Then KM is asked for that login name's token, and the browser goes to `http://km…/jump.html?token=…`.
  Given memberId "1001234567890123" (> 11 characters)
  Then that legacy-system user's mobile is used.
**Parameters:** integrationKey = `***` (`CommonConstants.SecretKey.KM`); KM result code 0 = success.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Require a logged-in legacy-system session and derive the KM identity from it; move the key to configuration and rotate it; use HTTPS.

### RULE-330: Every login attempt is logged; success stamps last-login IP and time
**Domain:** identity · **Category:** Policy · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:332-349`
**Plain English:** Each login writes a login log (type, trace id, user, user agent, client IP, result). On success it also updates the user's `login_ip` and `login_date`. Logouts are logged with type LOGOUT_SELF.
**Specification:**
  Given user 1001 logs in through WeCom from IP 10.0.0.8
  Then a log row (LOGIN_SOCIAL, SUCCESS) is written and `system_users.login_ip` = 10.0.0.8.
**Confidence:** High.
**Review needed:** No.

## identity — tokens and sessions

### RULE-331: Every login creates a refresh token and an access token for client "default"
**Domain:** identity · **Category:** Lifecycle · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/oauth2/OAuth2TokenServiceImpl.java:54-62`
**Plain English:** A login creates a refresh token, then an access token linked to it. Both are random 32-character UUIDs. Expiry = now + the client's validity seconds. The access token is stored in the DB and in Redis (with the Redis TTL equal to the remaining life).
**Specification:**
  Given client "default" with access validity A and refresh validity R seconds
  When user 1001 logs in at T
  Then the access token expires at T + A and the refresh token at T + R.
**Parameters:** Client id `default`; token generation `OAuth2TokenServiceImpl.java:159-200`; the repo seed sets A = 180, R = 8640 (prod value unknown).
**Confidence:** Medium. SME/DBA: what are prod `access_token_validity_seconds` and `refresh_token_validity_seconds` for client `default`?
**Review needed:** Yes. **Suggested fix:** Make session lifetimes explicit configuration (ADR-0009 server-side sessions).

### RULE-332: Access-token expiry slides to now + 10 h on every check
**Domain:** identity · **Category:** Policy · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/oauth2/OAuth2TokenServiceImpl.java:95-137`
**Plain English:** Each time a token is checked and found in Redis, its expiry is reset to now + 10 hours. On a Redis miss, the DB row is used: if it is still unexpired it is re-cached with now + 10 hours; if it is expired, the check fails with "访问令牌已过期". The DB expiry itself is never extended.
**Specification:**
  Given a token cached in Redis, used at 09:00 and again at 18:00
  Then it stays valid until 04:00 the next day.
  Given it is idle for more than 10 hours
  Then the Redis copy expires; if the DB expiry has also passed, the next request is 401.
**Parameters:** 10 h sliding window (hard-coded, marked TODO).
**Suspected defect:** There is no absolute session cap: an active token never expires. The behaviour depends on Redis eviction. The invalid-token path prints the token to stdout (line 128).
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Configurable idle timeout plus an absolute maximum lifetime, in a server-side session store.

### RULE-333: Refresh revokes sibling access tokens but never rotates the refresh token
**Domain:** identity · **Category:** Lifecycle · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/oauth2/OAuth2TokenServiceImpl.java:64-93`
**Plain English:** Refresh requires an existing refresh token issued to client `default`. All access tokens issued from it are deleted first. If the refresh token has expired, it is deleted and the call fails with 401 "刷新令牌已过期". Otherwise a new access token is issued against the same refresh token.
**Specification:**
  Given refresh token R (unexpired) with access tokens A1, A2
  When `/system/auth/refresh-token?refreshToken=R`
  Then A1 and A2 are revoked and A3 is issued; R stays the same.
  Given R has expired
  Then R is deleted and the response is 401.
**Edge cases handled:** An unknown refresh token gives 400 "无效的刷新令牌". A client mismatch gives 400.
**Suspected defect:** No rotation and no reuse detection, so a leaked refresh token (RULE-335) stays usable until it expires.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Rotate the refresh token on each use and revoke the whole family on reuse.

### RULE-334: Logout deletes the access token and its refresh token
**Domain:** identity · **Category:** Lifecycle · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:415-447`
**Plain English:** Logout is anonymous. With a bearer token, every DB row with that token value is removed from the DB and Redis, together with its refresh token, and a LOGOUT_SELF log is written. Without a token it does nothing and still reports success.
**Specification:**
  Given user 1001 holds A1 issued from R1
  When POST `/system/auth/logout` with A1
  Then A1 and R1 are deleted and a logout log is written.
**Confidence:** High.
**Review needed:** No.

### RULE-335: Four SSO paths put access and refresh tokens in the redirect URL
**Domain:** identity · **Category:** Policy · **Priority:** P0 · **SEC:** SEC-019
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/AuthController.java:104-132`
**Plain English:** `a8-login`, `seeyon-sso`, `social-login-get` (lines 248-264) and `singleSignOn/YD-get` (lines 266-280) redirect with `?accessToken=…&refreshToken=…`. The PC router guard stores them in local storage and then strips them from the address bar (`psa-front-desk-pc/src/permission.js:21-31`).
**Specification:**
  Given a successful Seeyon SSO
  Then the browser lands on `<redirectUrl>?accessToken=X&refreshToken=Y`, the PC guard saves X and Y and replaces the URL without them.
**Confidence:** High.
**Review needed:** Yes. This conflicts with ADR-0009 §2. **Suggested fix:** Use a one-time code exchanged server-side for an HttpOnly session cookie.

### RULE-336: Services trust the `login-user` header, treat a bad token as anonymous, and ignore user type
**Domain:** identity · **Category:** Validation · **Priority:** P0 · **SEC:** SEC-020, SEC-021
**Source:** `psa-backend/yudao-framework/yudao-spring-boot-starter-security/src/main/java/cn/iocoder/yudao/framework/security/core/filter/TokenAuthenticationFilter.java:40-135`
**Plain English:** Each service first trusts a JSON `login-user` request header. Without one, it validates the bearer token through system; an invalid or expired token yields "no user" (anonymous) rather than an error. A token's user type (ADMIN vs MEMBER) need not match the API family (the check is commented out). When `mockEnable` is on, a token starting with the mock secret plus a user id logs in as that id.
**Specification:**
  Given a request carrying header `login-user: {"id":1}`
  When it reaches a service directly
  Then it runs as user 1.
  Given an expired token on a `@PermitAll` endpoint
  Then the request proceeds anonymously.
**Parameters:** Header `login-user`; `security.mock-enable`, `mock-secret` = `***`.
**Confidence:** High.
**Review needed:** Yes. This conflicts with ADR-0009 §5. **Suggested fix:** Identity comes only from the server session; never trust identity headers from outside; enforce user type per API family; no mock login outside local.

### RULE-337: The gateway does no authentication
**Domain:** identity · **Category:** Policy · **Priority:** P0 · **SEC:** SEC-020
**Source:** `psa-backend/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/security/TokenAuthenticationFilter.java:86-128`
**Plain English:** All requests are denied unless authenticated, decided in one place, the identity module's security configuration (ADR-0009 decisions 1 and 5, G28/G33). A request without a valid session on any endpoint that is not explicitly public returns 401. A caller-supplied identity header is never trusted.
**Specification:**
  Given an anonymous client calls every endpoint that is not marked public
  Then  each returns 401 (C1a-07)
  Given a client sends a forged `login-user` header
  Then  the header is ignored and the identity comes only from the session
**Legacy behaviour:** the gateway discarded the token check and forwarded every request (`if (true) return chain.filter(exchange)`), so authentication depended on each service (RULE-336) and a forged header reached services unchanged.
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Deny by default in one place (the identity BC / modular-monolith security config, ADR-0009 G33).

### RULE-338: Disabling, deleting or a resignation via sync does not revoke live tokens
**Domain:** identity · **Category:** Lifecycle · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/user/AdminUserServiceImpl.java:338-412`
**Plain English:** `updateUserStatus`, `deleteUser` and `deleteBatchIds` (A8 resignation) update the user and publish an org-change event, but never touch `system_oauth2_access_token` or `refresh_token`. Token checks (RULE-332) do not look at user status, so a disabled or resigned employee keeps working until the token lapses, and refreshes until the refresh token expires.
**Specification:**
  Given user 1001 is logged in, and HR disables them at 10:00
  Then their token keeps working after 10:00 (and keeps sliding, RULE-332).
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Revoke all of the user's sessions on disable, delete or resignation, and check status when resolving the session.

### RULE-339: H5 token check: present, known and unexpired (and the check extends it)
**Domain:** identity · **Category:** Validation · **Priority:** P2
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImpl.java:628-637`
**Plain English:** H5 calls `qy-wechat/checkToken` on start-up. A blank token gives 401 "token不能为空"; an unknown or expired token gives 401. Otherwise the token is valid, and the check itself extends the token by the RULE-332 window. If the check fails, H5 restarts WeCom OAuth (`psa-mini/src/pages/qyLogin/qyLogin.tsx:233-279`).
**Specification:**
  Given a stored H5 token that expired
  When the app opens
  Then checkToken returns 401 and the app redirects to WeCom OAuth.
**Confidence:** High.
**Review needed:** No.

## identity — function permission (roles and menus)

### RULE-340: Role code `super_admin` bypasses every permission and role check
**Domain:** identity · **Category:** Policy · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:391-440`
**Plain English:** If any of the user's enabled roles has code `super_admin`, every `hasPermission` and `hasRole` returns true, and they get all menus (lines 199-216) and all menu ids for the role (lines 236-244).
**Specification:**
  Given user 1 holds enabled role `super_admin`
  When checking `fst:cost:opening:official-reset`
  Then the check is granted, even though no role links that permission.
**Parameters:** `RoleCodeEnum.SUPER_ADMIN = "super_admin"` (`psa-backend/yudao-module-system/yudao-module-system-api/src/main/java/cn/iocoder/yudao/module/system/enums/permission/RoleCodeEnum.java:14-29`).
**Confidence:** High.
**Review needed:** No (keep as break-glass; see SEC-102 for refusing external SSO into super admin).
**Superseded:** by ADR-0026 D-7 (user approved 2026-10-14): `super_admin` no longer bypasses any check and holds no business permission; in an ordinary session it grants nothing, and the administrator mode carries a fixed list of platform permissions only (ADR-0027 section 2, ADR-0028). The old behaviour above is evidence, not a requirement.

### RULE-341: A function permission is granted when an enabled role links a menu with that permission string
**Domain:** identity · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:391-419`
**Plain English:** `@ss.hasPermission('x')` passes if any listed permission maps to a menu or button that is linked to one of the user's enabled roles. An empty list passes. A permission string with no menu fails (strict). Having no roles fails.
**Specification:**
  Given role 7 links button "system:user:create", and user 1001 holds role 7 (enabled)
  Then `hasPermission('system:user:create')` = true.
  Given the string "cost:opening:reset" exists in no menu
  Then the check = false for every non-super-admin.
**Confidence:** High.
**Review needed:** No.

### RULE-342: Role-code check: the user holds an enabled role with one of the codes
**Domain:** identity · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:421-440`
**Plain English:** `hasAnyRoles` is true when the user's enabled role codes intersect the given codes (super admin is always true; an empty list is true).
**Specification:**
  Given user 1001 holds "BU-002"
  Then `hasAnyRoles("BU-002","PMO")` = true.
**Confidence:** High.
**Review needed:** No.

### RULE-343: Only enabled roles count; a user with no roles has no function permissions
**Domain:** identity · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:218-234`
**Plain English:** A user's role set comes from the user-role cache, filtered to roles whose status is in the requested set (always ENABLE = 0 in permission checks). Roles missing from the cache are dropped.
**Specification:**
  Given user 1001 holds roles 7 (enabled) and 8 (disabled)
  Then permission checks consider only role 7.
**Confidence:** High.
**Review needed:** No (but see RULE-349: the data-scope engine does not apply this filter).

### RULE-344: Every user without a role silently gets role 2 whenever the user cache reloads
**Domain:** identity · **Category:** Policy · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:512-534`
**Plain English:** On every user-cache initialisation (start-up and each user refresh message, `AdminUserServiceImpl.java:100-124`), every cached user with no user-role row gets role `DEFAULT_ROLE` = 2, with creator/updater = admin 1. Deleted users are included because the cache includes them.
**Specification:**
  Given A8 sync creates user 2001 with no role
  When the user-refresh message is consumed
  Then a user-role row (2001, role 2) is inserted.
**Parameters:** `CommonConstants.DEFAULT_ROLE = 2`; `ADMIN_ID = 1`.
**Suspected defect:** This is an implicit grant on a cache event. If an admin removes all roles from someone, role 2 comes back on the next refresh. The fst `SyncServiceImpl.setDefaultRole` (lines 489-507) duplicates this logic and is unused.
**Confidence:** Medium. SME: what does role 2 grant, and should resigned users receive it?
**Review needed:** Yes. **Suggested fix:** Make the default role an explicit onboarding step for enabled, non-deleted employees only.

### RULE-345: Role maintenance: reserved code, unique name and code, system roles frozen, default scope ALL
**Domain:** identity · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/RoleServiceImpl.java:90-173`
**Plain English:** A new role starts enabled with data scope ALL. Code `super_admin` cannot be created or reused. Name and code must each be unique. SYSTEM-type roles cannot be updated, status-changed, re-scoped or deleted. Deleting a role also deletes its user-role and role-menu links. Validation is at lines 230-267.
**Specification:**
  Given an admin creates role "Analyst"
  Then it has dataScope = 1 (ALL) until changed.
  Given an admin tries to delete a SYSTEM role
  Then the error is ROLE_CAN_NOT_UPDATE_SYSTEM_TYPE_ROLE.
**Parameters:** `DataScopeEnum.ALL = 1`.
**Suspected defect:** Defaulting to ALL fails open: a new role grants full row visibility until someone narrows it.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** New roles default to SELF (least privilege).

### RULE-346: Role-menu and user-role assignment replace the full set
**Domain:** identity · **Category:** Lifecycle · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:246-336`
**Plain English:** Assigning menus to a role, or roles to a user, replaces the whole set: new ids are inserted and missing ids deleted. An empty or null menu list removes all of the role's menus. Caches are refreshed after commit.
**Specification:**
  Given role 7 has menus {1,2,3}
  When assigned {2,4}
  Then {4} is inserted and {1,3} are deleted.
**Confidence:** High.
**Review needed:** No.

### RULE-347: Menu tree integrity
**Domain:** identity · **Category:** Validation · **Priority:** P2
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/MenuServiceImpl.java:140-293`
**Plain English:** A menu's parent must exist, must not be the menu itself, and must be a directory or menu (not a button). Names are unique under a parent. A menu with children cannot be deleted. Buttons have no path, icon or component. Deleting a menu also revokes it from every role.
**Specification:**
  Given menu 10 has child 11
  When deleting 10
  Then the error is MENU_EXISTS_CHILDREN.
**Confidence:** High.
**Review needed:** No.

### RULE-348: Navigation shows enabled dirs and menus of enabled roles; permission info adds buttons
**Domain:** identity · **Category:** Calculation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/AuthController.java:169-203`
**Plain English:** `list-menus` returns a tree of enabled DIR and MENU entries reachable from the user's enabled roles. `get-permission-info` returns the user, enabled roles and enabled DIR/MENU/BUTTON entries (button permission strings drive front-end button visibility).
**Specification:**
  Given user 1001 holds role 7, linking enabled menu M and disabled menu N
  Then the navigation shows M only.
**Confidence:** High.
**Review needed:** No.

## identity — data scope (row-level)

### RULE-349: Business data scope = union over roles; ALL anywhere means unrestricted
**Domain:** identity · **Category:** Calculation · **Priority:** P0 · **SEC:** SEC-009
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/RoleServiceImpl.java:289-345`
**Plain English:** For business modules, visible depts and users are the union over all of the user's roles:
- Any role with ALL makes the scope unrestricted (null).
- Each role's `data_scope_dept_ids` is added (whatever its scope type).
- DEPT_ONLY adds the user's own dept.
- DEPT_AND_CHILD adds the own dept and all descendants.
- SELF_AND_SUBORDINATES adds self plus users whose `leader_user_id` = self (one level only).
- SELF adds self.
Finally, every user whose dept is in the visible depts becomes a visible user.
**Specification:**
  Given user 1001 (dept 15, which has child 16) with role A = DEPT_AND_CHILD and role B = SELF_AND_SUBORDINATES, where 2001 and 2002 report to 1001
  Then visible depts = {15, 16} and visible users = {1001, 2001, 2002} ∪ all users in depts 15 and 16.
**Parameters:** `DataScopeEnum`: ALL 1, DEPT_CUSTOM 2, DEPT_ONLY 3, DEPT_AND_CHILD 4, SELF 5, SELF_AND_SUBORDINATES 6.
**Suspected defect:**
- Roles are read from the DB without filtering status, so disabled roles still widen scope (unlike RULE-343).
- Custom dept ids count even when the scope is not DEPT_CUSTOM.
- DEPT_CUSTOM does not add the own dept here, though it does in RULE-352.
- The user list includes soft-deleted users.
- A leader's indirect reports are not visible.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** One explicit AccessScope (legal entity × dept × project role, ADR-0009 §4) computed from enabled roles only, and documented per scope type.

### RULE-350: Row filtering applies only to opt-in methods with `@DataOwner` fields
**Domain:** identity · **Category:** Policy · **Priority:** P0 · **SEC:** SEC-009
**Source:** `psa-backend/yudao-framework/yudao-spring-boot-starter-biz-data-permission/src/main/java/cn/iocoder/yudao/framework/datapermission/core/db/DataPermissionDatabaseInterceptor.java:68-195`
**Plain English:** Every controller call computes the RULE-349 scope (`psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/framework/aop/ParamOutAspect.java:35-67`). A SELECT is rewritten only if the calling method has `@DataPermission(enable = true)` (default false) and the result entity has `@DataOwner` fields. It then appends `AND (dept_col IN depts OR user_col IN users OR json_user_col contains users)`.
**Specification:**
  Given a method annotated `@DataPermission(enable = true)` returning an entity with `@DataOwner(type=USER) creatorId`, and visible users {1001, 2001}
  Then the query gets `AND creator_id IN (1001, 2001)`.
  Given a method without the annotation
  Then the query is unfiltered.
**Parameters:** About 41 opt-in methods and 26 `@DataOwner` fields in fst.
**Suspected defect:** Opt-in by annotation. Parse or rewrite exceptions are swallowed (`printStackTrace`) and the query runs unfiltered.
**Confidence:** High.
**Review needed:** Yes. This conflicts with ADR-0009 §4 and ADR-0019 (no implicit SQL rewrite). **Suggested fix:** Pass AccessScope explicitly to repositories and fail closed.

### RULE-351: An empty data scope fails open
**Domain:** identity · **Category:** Policy · **Priority:** P0 · **SEC:** SEC-009
**Source:** `psa-backend/yudao-framework/yudao-spring-boot-starter-biz-data-permission/src/main/java/cn/iocoder/yudao/framework/datapermission/core/db/DataPermissionDatabaseInterceptor.java:108-112`
**Plain English:** If the computed scope has no depts and no users (a user with no roles, or roles whose scope adds nothing), the rewrite condition is false and no filter is added, so the user sees every row.
**Specification:**
  Given user 3001 with no roles (before RULE-344 assigns role 2), calling an opt-in list
  Then they see all rows.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** An empty scope must return no rows.

### RULE-352: A second, different data-scope engine guards system user and dept writes
**Domain:** identity · **Category:** Calculation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:442-510`
**Plain English:** The yudao dept rule (configured for `system_users.dept_id`/`id` and `system_dept.id` in `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/framework/datapermission/config/DataPermissionConfiguration.java:17-25`) uses enabled roles only. In this engine:
- No roles means self only.
- DEPT_CUSTOM also adds the own dept.
- SELF_AND_SUBORDINATES is treated as SELF.
It is applied only to UPDATE and DELETE; the SELECT rewrite is commented out (`DataPermissionDatabaseInterceptor.java:200-243`).
**Specification:**
  Given a role with SELF_AND_SUBORDINATES
  Then RULE-349 lets the user see direct reports, while this engine limits their system-user updates to self.
**Confidence:** Medium. The SELECT disablement is inferred from the commented `parserSingle`.
**Review needed:** Yes. **Suggested fix:** Merge into one scope model (see RULE-349).

### RULE-353: Dept dropdown narrows to own dept when exactly one visible user
**Domain:** identity · **Category:** Calculation · **Priority:** P2
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/dept/DeptServiceImpl.java:377-387`
**Plain English:** When the RULE-349 visible-user set has exactly one member, the dept selector shows only the user's own dept (plus ancestors up to the root, lines 343-375). Otherwise it shows the normal list.
**Specification:**
  Given a SELF-only user in dept 15
  Then the dropdown shows dept 15 and its ancestors.
**Confidence:** High.
**Review needed:** No.

## org — departments (A8 sync and maintenance)

### RULE-354: A8 dept tree: prefix scope, parent from PATH, fail on a broken tree
**Domain:** org · **Category:** Calculation · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:148-281`
**Plain English:** The scheduled job `syncDeptJob` reads A8 `ORG_UNIT` rows whose PATH starts with `00000001`, parents first (`A8DeptMapper.java:35-48`). Each PATH level is 4 characters, so a dept's parent is the dept whose PATH equals its own minus the last 4 characters. The root (`00000001`) gets parent 0. If two depts share a PATH, the active one is the parent candidate (otherwise the first). If a parent PATH is missing, the whole run aborts.
**Specification:**
  Given dept X with PATH `000000010004` and an active dept Y with PATH `00000001`
  Then X.parent = Y.
  Given dept Z with PATH `0000000100990001` and no `000000010099`
  Then the sync throws and nothing is written.
**Parameters:** `A8_DEPT_ROOT_PATH = "00000001"`; `A8_DEPT_PATH_LEVEL_LENGTH = 4`.
**Confidence:** High.
**Review needed:** No.

### RULE-355: A8 dept "disabled" test and how it maps to legacy-system status and deleted
**Domain:** org · **Category:** Calculation · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/mysql/a8/A8DeptMapper.java:35-48`
**Plain English:** An A8 dept is disabled when `IS_DELETED = 1 OR IS_INTERNAL != 1 OR IS_ENABLE != 1` (NULLs fall to "active"). Legacy-system status = that flag (0 active, 1 disabled) (`SyncServiceImpl.java:171-172`), and legacy-system deleted = (status ≠ 0) (`DeptServiceImpl.java:180-198`). A disabled A8 dept is therefore also soft-deleted in the legacy system.
**Specification:**
  Given an A8 dept with IS_ENABLE = 0
  Then legacy-system status = 1 and deleted = true.
  Given IS_INTERNAL = NULL
  Then legacy-system status = 0 (active).
**Confidence:** High.
**Review needed:** Yes. NULL-as-active is accidental, and "disabled" and "deleted" are conflated. **Suggested fix:** Decide NULL handling explicitly with the A8 owner, and keep disabled vs dissolved as separate states.

### RULE-356: A8 is authoritative for dept leader, regional AR, category and delivery flag
**Domain:** org · **Category:** Calculation · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:184-221`
**Plain English:** The dept leader and regional AR come from A8 `ORG_RELATIONSHIP` rows of a fixed relation type whose person is not deleted (`A8DeptMapper.java:50-98`). A dept with no relation gets NULL (the legacy-system value is cleared). With several relations, the first by person id wins and a warning is logged. Category (总部/区域) comes from the ADDRESSBOOK enum. The delivery flag is 1 if any ADDRESSBOOK row marks it, else 0.
**Specification:**
  Given A8 dept 15 with leaders 801 and 805
  Then legacy-system leader = 801 (the lower id first by sort order).
  Given A8 removes dept 15's AR
  Then legacy-system `ar_user_id` becomes NULL.
**Parameters:** Relation type ids and the ADDRESSBOOK "yes" enum id are hard-coded per A8 instance (`DEPT_LEADER_RELATIONSHIP_TYPE_ID`, `DEPT_AR_RELATIONSHIP_TYPE_ID`, `ADDRESSBOOK_FLAG_YES`).
**Suspected defect:** If the A8 instance changes, all leaders, ARs and delivery flags silently become NULL or 0. "First by id" is an arbitrary tie-break.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Move the ids to configuration with a start-up check; escalate multi-leader depts to data stewards instead of picking silently.

### RULE-357: Dept sync: insert, update on change, soft-delete what A8 no longer returns, revive
**Domain:** org · **Category:** Lifecycle · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/dept/DeptServiceImpl.java:200-305`
**Plain English:**
- A new id is inserted.
- An existing id is updated only when one of the 10 sync fields differs (code, name, parent_id, sort, status, deleted, leader_user_id, ar_user_id, category, delivery_flag). All 10 are written explicitly, including NULLs.
- Every legacy-system dept not returned by A8 (except root 0 and already-deleted rows) is soft-deleted.
- Deleted rows that A8 returns again are revived.
- Other columns (short_name, final_dept, phone, email) are never touched.
Caches refresh only if something changed.
**Specification:**
  Given the legacy system has depts {15, 16, 99} (99 created manually in the legacy system) and A8 returns {15, 16}
  Then 99 is soft-deleted.
  Given A8 later returns 99
  Then it is revived.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Mark row and column ownership (ADR-0011): A8-owned depts follow A8; FORCITIS one-owned depts or columns are never auto-deleted.

### RULE-358: Dept sync aborts if A8 returns < 80 % of active depts
**Domain:** org · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/dept/DeptServiceImpl.java:253-271`
**Plain English:** Before writing, if incoming rows × 100 < active legacy-system depts × 80, the run is aborted with no writes. An empty legacy-system table skips the check.
**Specification:**
  Given 300 active legacy-system depts and A8 returns 230
  Then the sync aborts (230 < 240).
  Given A8 returns 240
  Then the sync proceeds.
**Parameters:** `SYNC_SOURCE_MIN_RATIO_PERCENT = 80`.
**Confidence:** High.
**Review needed:** No.

### RULE-359: Manual dept edits: parent valid, no cycles, unique name, no delete with children
**Domain:** org · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/dept/DeptServiceImpl.java:488-543`
**Plain English:**
- A null parent means the root (0).
- A non-root parent must exist, be enabled, not be the dept itself, and not be one of its descendants.
- The name is unique under the same parent.
- A dept with children cannot be deleted (lines 325-341).
**Specification:**
  Given dept 15 with child 16
  When the parent of 15 is set to 16
  Then the error is DEPT_PARENT_IS_CHILD.
**Suspected defect:** Delete does not check for users still in the dept. Every manual edit to an A8 dept is overwritten by the next sync, and manual depts are soft-deleted (RULE-357).
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Make the dept master read-only in FORCITIS one (A8-owned), or define FORCITIS one-owned dept types explicitly.

### RULE-360: "Dept and below" = recursive children from a cache that includes deleted depts
**Domain:** org · **Category:** Calculation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/dept/DeptServiceImpl.java:421-486`
**Plain English:** Descendants are found recursively, with no depth limit, from the in-memory dept cache. The cache is built from `deptAllMapper` (lines 127-147), so it includes soft-deleted and disabled depts. The user list filtered by dept includes descendants (`AdminUserServiceImpl.java:505-519`).
**Specification:**
  Given 15 → 16 (active) → 17 (soft-deleted)
  Then "15 and below" = {15, 16, 17}.
**Confidence:** Medium.
**Review needed:** Yes. **Suggested fix:** Exclude dissolved depts from scope expansion unless historical reporting needs them.

## org — people, posts, reporting line

### RULE-361: A8 user sync scope uses a substring match, unlike the dept prefix rule
**Domain:** org · **Category:** Validation · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/dal/mysql/a8/A8UserMapper.java:24-40`
**Plain English:** `syncUserJob` reads A8 members that are internal (`IS_INTERNAL = 1`), have a non-empty CODE, have a department, and whose dept PATH contains `00000001` (`LIKE '%00000001%'`). Deleted A8 members are included on purpose, so that resignations can be detected (RULE-363).
**Specification:**
  Given a member whose dept PATH is `0000000200000001`
  Then it is synced as a user (substring match), although its dept is outside the dept-sync prefix scope.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Use the same prefix rule as RULE-354 so that users never point at unsynced depts.

### RULE-362: A new A8 person becomes a legacy-system user with username = phone and a default password
**Domain:** org · **Category:** Lifecycle · **Priority:** P0 · **SEC:** SEC-011
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:299-340`
**Plain English:** An A8 member id not yet in the legacy system is created with:
- id = A8 id, dept, nickname, employee code
- username = A8 login name (phone), or the code if there is no phone; mobile = phone
- status enabled; deleted = (no phone)
- leader, enterprise email, a single post, billing flag, birthday
- password = a shared default (`AdminUserServiceImpl.java:156-188`)
`we_com_id` is not set on creation.
**Specification:**
  Given A8 member 2001 with phone 13900000000 and code E001
  Then legacy-system user 2001 is created: username 13900000000, enabled, password = `***` (default).
  Given no phone
  Then the user is created already soft-deleted.
**Parameters:** `CommonConstants.DEFAULT_PASSWORD` = `***`.
**Suspected defect:** A known default password exists for every synced user. `we_com_id` arrives only on the next run (the update loop compares against the pre-run snapshot), so WeCom login and messages fail for new hires until the following sync.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** No usable local password for synced users; write `we_com_id` at creation.

### RULE-363: Resignation = A8 login name is null → legacy-system user soft-deleted
**Domain:** org · **Category:** Lifecycle · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:359-369`
**Plain English:** For an existing user whose A8 login name (phone) is null: if the legacy system already marks the user deleted, the whole row is skipped (no other field is updated). Otherwise the user is marked deleted and soft-deleted through `deleteBatchIds` (`AdminUserApiImpl.java:182-207`). Role links and posts are kept; tokens are not revoked (RULE-338).
**Specification:**
  Given legacy-system user 2001 (active) and A8 login name now NULL
  Then 2001 becomes deleted = 1 and an org-change DELETED event is published.
**Suspected defect:**
- The update path tests `phone == null`, while the create path tests empty. An empty-string phone is treated as present, so the user is not deleted, and the mobile is set to "".
- A deleted user's dept and leader changes are frozen.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** One normalised "has login" test; on resignation revoke sessions and roles; keep updating HR fields for history.

### RULE-364: Rehire and phone change restore and re-enable the legacy-system user
**Domain:** org · **Category:** Lifecycle · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:380-398`
**Plain English:** If A8 has a phone and either the phone differs from the legacy-system mobile or the legacy system marks the user deleted, the user is set to status 0 (enabled) and deleted = 0. The restore runs before the field update (`AdminUserApiImpl.java:186-205`, `AdminUserServiceImpl.java:374-399`).
**Specification:**
  Given legacy-system user 2001 was disabled manually (status 1) and A8 changes their phone
  Then 2001 is re-enabled.
  Given legacy-system user 2001 is soft-deleted and A8 has a phone again
  Then 2001 is restored and a CREATED event is published.
**Suspected defect:** A phone change silently overrides a manual disable. The username is not updated when the phone changes, so password and Seeyon login (username = OA login name) can break.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Keep the enable/disable status FORCITIS one-owned and separate from A8 identity changes; keep username in step with the A8 login name.

### RULE-365: Per-field update semantics in user sync are asymmetric
**Domain:** org · **Category:** Calculation · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:342-441`
**Plain English:** For existing users:
- dept is overwritten when different;
- email only when A8 is non-null;
- leader only when A8 is non-null (an A8 clear is ignored);
- `we_com_id` only when A8 is non-null;
- the post set is replaced by the single A8 primary post when it differs (extra legacy-system posts are lost);
- billing is overwritten even with null;
- birthday only when non-null.
Only changed fields are sent.
**Specification:**
  Given A8 removes 2001's leader (NULL) while the legacy system has leader 1001
  Then the legacy system keeps 1001.
  Given legacy-system user 2001 has posts {5, 9} and A8 primary post 5
  Then the posts are reset to {5}.
**Suspected defect:** Stale reporting lines after A8 clears a leader. A null A8 dept or post id throws a NullPointerException and aborts the whole run.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** A8-authoritative columns write explicit NULLs (as the dept sync does); guard missing dept or post per row.

### RULE-366: Users that drop out of the A8 result set are never deactivated
**Domain:** org · **Category:** Lifecycle · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:283-441`
**Plain English:** The user sync only iterates A8 rows. A legacy-system user whose A8 row is no longer returned (marked external, CODE blanked, moved out of scope, physically deleted) is left exactly as it was: active and able to log in.
**Specification:**
  Given legacy-system user 2001 is active and A8 sets IS_INTERNAL = 0
  Then 2001 stays active in the legacy system.
**Confidence:** Medium. The rule is inferred from missing logic; nothing else in the repo deactivates users.
**Review needed:** Yes. **Suggested fix:** Treat absent users as resignation candidates, protected by a shrink guard like RULE-358.

### RULE-367: Post sync only adds and renames
**Domain:** org · **Category:** Lifecycle · **Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:444-487`
**Plain English:** A8 posts with `IS_DELETED = 0` (`A8PostMapper.java:19-22`) that are new to the legacy system are inserted with A8 status (null → 0) and sort. For existing posts only code and name are updated. Posts deleted in A8 are never removed or disabled in the legacy system, and status and sort changes are ignored.
**Specification:**
  Given A8 disables post 5 (STATUS = 1)
  Then legacy-system post 5 stays enabled.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Sync status and deletion, so that the "post must be enabled" check in RULE-368 stays meaningful.

### RULE-368: Manual user maintenance: unique username, mobile and email; enabled dept and posts
**Domain:** org · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/user/AdminUserServiceImpl.java:521-601`
**Plain English:** Creating or updating a user requires:
- username, mobile and email each unique among non-deleted users;
- the dept to exist and be enabled; the posts to be enabled;
- username of 4–30 letters or digits (`UserBaseVO.java:23-26`).
New users start enabled with a 4–16 character password (lines 126-154). Deleting a user removes their roles and posts (lines 355-372).
**Specification:**
  Given user 1001 with mobile 13800000000
  When another user is created with that mobile
  Then the error is USER_MOBILE_EXISTS.
**Suspected defect:** Uniqueness ignores soft-deleted rows, so restoring a resigned user (RULE-364) can create duplicates.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Enforce uniqueness across all rows, or define the identity key as the A8 id only.

### RULE-369: Excel user import: per-row validation, optional update, init password
**Domain:** org · **Category:** Validation · **Priority:** P2 · **SEC:** SEC-011
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/user/AdminUserServiceImpl.java:620-665`
**Plain English:** Each row is validated (mobile, email, dept). A new username is inserted with the configured init password. An existing username is updated only when "update supported" is ticked; otherwise it is reported as a failure. Per-row errors do not stop the batch.
**Specification:**
  Given 3 rows, where row 2's dept is disabled
  Then rows 1 and 3 are imported and row 2 fails with DEPT_NOT_ENABLE.
**Parameters:** `sys.user.init-password` (default `***`).
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Drop manual import if A8 is the master; otherwise do not set a shared password.

### RULE-370: Password change rules
**Domain:** org · **Category:** Validation · **Priority:** P1 · **SEC:** SEC-011
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/service/user/AdminUserServiceImpl.java:304-336`
**Plain English:** A self-service change requires the old password to match (USER_PASSWORD_FAILED otherwise) and a new password of 4–16 characters. An admin reset requires only that the user exists. Passwords are stored hashed. Existing sessions are not revoked.
**Specification:**
  Given user 1001 enters a wrong old password
  Then the change is refused.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Revoke sessions on password change; follow ADR-0009 (local passwords only for the emergency admin).

### RULE-371: "Effective user" = enabled and not deleted; the user cache also carries deleted users
**Domain:** org · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/api/user/AdminUserApiImpl.java:91-113`
**Plain English:** Other modules get "effective users" as status = 0 and deleted = 0. The all-users cache (`AdminUserServiceImpl.java:100-124`) includes soft-deleted users with their flag. `validUsers` requires each id to exist and be enabled (lines 464-482).
**Specification:**
  Given users 1001 (enabled), 1002 (disabled) and 1003 (deleted)
  Then the effective set = {1001}, and the all-users cache = {1001, 1002, 1003}.
**Confidence:** High.
**Review needed:** No.

### RULE-372: Two separate leader concepts: person leader and dept leader
**Domain:** org · **Category:** Calculation · **Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/sync/SyncServiceImpl.java:324-405`
**Plain English:** The "direct leader" (直属领导) is `system_users.leader_user_id`, taken from A8 member attribute EXT_ATTR_37 (`A8UserMapper.java:27`). The "dept leader" is `system_dept.leader_user_id`, taken from A8 relations (RULE-356). The two are not reconciled. The leader's leader (上级领导) is derived by following `leader_user_id` twice (RULE-250). "Subordinates" = users whose `leader_user_id` = me, one level (RULE-349).
**Specification:**
  Given user 2001 with leader_user_id = 1001, and dept 15's leader = 1005
  Then the approval "initiator leader" is 1001 (RULE-250) while the "dept leader" script gives 1005 (RULE-257).
**Suspected defect:** No validation that the leader exists, is active, is not the user themself, or that there is no cycle. A cleared leader in A8 is not cleared in the legacy system (RULE-365).
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Validate reporting lines on sync (exists, active, no self, no cycle) and document which leader each process uses.

### RULE-373: Org-change events: field diff after commit; dept sync events miss fields and repeat deletes
**Domain:** org · **Category:** Lifecycle · **Priority:** P1
**Source:** `psa-backend/yudao-module-system/yudao-module-system-biz/src/main/java/cn/iocoder/yudao/module/system/mq/producer/org/OrgChangePublisher.java:105-140`
**Plain English:** User and dept changes publish CREATED, UPDATED or DELETED items after commit. The diff is field-level over a fixed field list; a null new value means "unchanged"; no diff means no event (lines 200-270). The dept-sync diff compares only code, name, parentId, sort and deleted.
**Specification:**
  Given A8 changes dept 15's leader only
  Then the dept table is updated (RULE-357), but no UPDATED event is emitted.
  Given a legacy-system-only dept that was soft-deleted earlier
  Then every sync run emits DELETED for it again.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Derive event fields from the same SYNC_FIELDS list; exclude already-deleted rows from the "gone" set.

## identity — WeCom messaging and callback

### RULE-374: WeCom message recipients are resolved through `we_com_id`
**Domain:** identity · **Category:** Validation · **Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/message/MessageServiceImpl.java:324-333`
**Plain English:** Approval cards go to the recipient's `we_com_id`. A missing user or blank `we_com_id` means the message is skipped with a warning. Task cards (lines 422-427, 466-471, 533-538) look up `we_com_id` without a null guard.
**Specification:**
  Given approver 2001 with no `we_com_id`
  Then no WeCom card is sent (warning only).
  Given a task for a user missing from the cache
  Then a NullPointerException is thrown.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Same null-safe resolution everywhere, plus a data-quality report of active users without `we_com_id`.

### RULE-375: Non-production task messages go to one hard-coded person; agentId chosen by environment
**Domain:** identity · **Category:** Policy · **Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/message/MessageServiceImpl.java:357-362`
**Plain English:** Outside prod, every task notification is redirected to one fixed developer user id. The WeCom agentId is the prod app in prod and the test app otherwise (lines 67-71).
**Specification:**
  Given profile dev
  When a task is assigned to 2001
  Then the card goes to the fixed developer user instead.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Make the test-recipient allowlist configuration, not code.

### RULE-376: WeCom card callback takes the acting user from the button key, not from FromUserName
**Domain:** identity · **Category:** Validation · **Priority:** P0 · **SEC:** SEC-006
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/controller/admin/wecom/WeComCallbackController.java:65-135`
**Plain English:** The user who acts on a WeCom card is resolved from `FromUserName` → `we_com_id` → user, and must be the handler of that task; the user id in the button key is not trusted. The callback token and AES key come from configuration and are rotated (SEC-006).
**Specification:**
  Given a validly signed callback with EventKey `task_1_555_2001` from FromUserName "lisi", where "lisi" maps to user 3001
  Then  the action is taken as user 3001 only if 3001 is a handler of task 555, otherwise it is refused
  Given FromUserName that maps to no user
  Then  it is refused
**Parameters:** token and AES key are configuration values, never in code or documents.
**Legacy behaviour:** the button key set the acting user, so anyone able to produce a signed callback could act as any handler; the secrets were hard-coded.
**Corrected:** 2026-10-21 per the verdict "wrong" of 2026-10-14 (RULE_REVIEWS.json); the legacy behaviour is kept only as evidence.
**Confidence:** High.
**Review needed:** Yes. **Suggested fix:** Resolve the actor from `FromUserName` → `we_com_id` → user, require that it equals the task assignee, and move the secrets to configuration and rotate them.
