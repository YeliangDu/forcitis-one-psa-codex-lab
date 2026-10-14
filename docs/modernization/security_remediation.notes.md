# PSA security remediation — per-hunk notes

Companion to `security_remediation.patch` (shareable) and `~/.modernize/psa/security_remediation.local.patch` (local only, mode 600, holds the credential-removal hunks because their `-` lines contain the raw values).

- Base: `/Users/yeliang/Projects/FORCITIS/psa` at `19d60d234` (branch `feature/issue-2068-e2e-quotation`). Apply with `patch -p1 -d <repo> < security_remediation.patch` or `git apply`.
- Round 2 (2026-10-14) revised SEC-001, SEC-002, SEC-003 (Flyway), SEC-005 and the SEC-006/012 ops notes after review.
- Round 3 (2026-10-14) addresses review findings SEC-101 to SEC-105:

  | Review finding | Patch finding | Change |
  |---|---|---|
  | SEC-101: the unsigned transition path could be spoofed through XFF hop counting | SEC-001 | XFF is no longer parsed. A new gateway `ClientIpHeaderFilter` overwrites `X-PSA-Client-IP` with the TCP peer address. The verifier trusts that header only from `gateway-addresses`. `trusted-proxy-hops` is removed. New nginx rules are in the ops steps. |
  | SEC-102: the super-admin refusal also hit WeCom OAuth logins | SEC-001 | The super-admin refusal now runs only on the 4 external endpoints (`externalCallerLogin`, `a8Login`). The `a8-login` and `social-login-get` redirects now use `https://`. |
  | SEC-103: Flyway granted too little | SEC-003 | `revenueEvents` holders now get `execute`. `reset` goes to `execute*` holders. `/official/reset` is split out to a new `cost:opening:official-reset` permission. |
  | SEC-104: IDOR through `paramsId` | SEC-005 | Both HTTP `createOA` endpoints are gated by `bpm:oa:customized:create`. No role is granted this permission, so the endpoints are effectively disabled. |
  | SEC-105: credential values in a tracked doc | SEC-006 | A new local-patch hunk masks the values in `docs/bugfix/PSA 项目现状痛点清单.md`, and the doc is added to the history purge list. |

  Unchanged since round 2: SEC-002 (KM), the SEC-003 `CostCorrectionController`, SEC-004, the other SEC-005/006 hunks, SEC-007, SEC-008, SEC-012, SEC-014 and SEC-016.
- Round 4 (2026-10-14) addresses review findings SEC-107 to SEC-110. Only the shareable patch changed; the local patch is unchanged.

  | Review finding | Change |
  |---|---|
  | SEC-107 (High, **release blocker**): nginx `/other-api/` reaches system `48081` directly; `/rpc-api/system/**` is open, so `oauth2/token/create` mints a token for any user; services trust a client-sent `login-user` header | nginx `/other-api` removed (repo nginx file + runbook). System `InternalRpcTokenFilter` now guards **all** of `/rpc-api/**` with a constant-time compare and rejects everything when the token is unset. A global Feign interceptor sends the token on every `/rpc-api` call. The gateway sends it on its `token/check` call and (r4) on every forwarded request, after stripping client-sent `X-PSA-Internal-Token` and `login-user`; **r5 (SEC-113):** only on requests where the gateway itself writes `login-user`. Services trust `login-user` only with a valid token. bpm, infra and gateway now load `psa-system-rpc.yaml`. See the SEC-107 section. |
  | SEC-108 (Medium): (r4 premise, corrected in r5: `48088` is the gateway's own TLS port; only requests through the 443 nginx arrive from 127.0.0.1) the gateway's TCP peer can be 127.0.0.1 | `ClientIpHeaderFilter` uses nginx's `X-Real-IP` only when the TCP peer is in the new `psa.gateway.trusted-proxies`. `TrustedCallerVerifier.checkConfig` logs ERROR, and the verifier ignores the rule, when a `legacy-allowed-ips` rule covers a loopback address or a `gateway-addresses` entry. ~~The ops text now says `48088` is nginx reverse-proxying to the gateway.~~ **r5:** wrong; corrected throughout (48088 is the gateway itself). |
  | SEC-109 (Low): a `location` with its own `proxy_set_header` does not inherit server-level ones | The ops step and the repo nginx file put `proxy_set_header X-PSA-Client-IP "";` in each such `location`. (r5: `psa-h5.conf` too.) |
  | SEC-110 (Low): address strings compared literally (`::1` vs `0:0:0:0:0:0:0:1`) | The verifier compares `remoteAddr`, `gateway-addresses`, `X-PSA-Client-IP` and exact-IP rules after `InetAddress` normalisation. The gateway filter does the same for `trusted-proxies`, the peer and `X-Real-IP`. |
- Round 5 (2026-10-14) addresses the r4 review leftovers (SEC-109 remainder, SEC-111, SEC-113) and corrects the `48088` facts after the read-only prod check for SEC-112. Only the shareable patch changed; the local patch is unchanged.

  | Review finding | Change |
  |---|---|
  | **Fact correction (SEC-112 prod check)** | `48088` is **not** nginx. It is the TLS port of the gateway process (yudao-gateway) itself, the same process as `48080`, and the security group opens it to `0.0.0.0/0`. The prod nginx (`/etc/nginx3/conf.d/psa.conf`) has a 443 server whose 6 gateway-bound locations forward to `https://127.0.0.1:48088`, and all 6 set `X-Real-IP`. Every "48088 is nginx" statement in this file and in `docs/ops/prelaunch-golive-runbook.md` is corrected. SEC-108 still holds; see "SEC-108 after the 48088 correction" below. |
  | SEC-109 remainder (Low) | Repo `scripts/deploy/dev-lan/nginx/psa-h5.conf` `/api/` now clears `X-PSA-Client-IP`. All repo nginx files were checked: `psa-pc.conf` and `psa-h5.conf` each have one gateway-bound location (`/api/`), and both now set `X-Real-IP $remote_addr` and clear `X-PSA-Client-IP`. `default.conf` is empty and `psa-front-desk-pc/deploy/nginx.conf.sample` has no `proxy_pass`. The only other `proxy_pass` is a commented-out example in `psa-mini/docs/deploy-and-wecom-setup.md`, which is not a deployed config. |
  | SEC-111 (`/rpc-api` of infra, bpm and fst open) — **fixed this round** (user decision) | The system-only `InternalRpcTokenFilter` / `InternalRpcTokenConfiguration` are deleted and replaced by a framework auto-configuration in the security starter (`YudaoInternalRpcTokenAutoConfiguration` + `framework.security.core.filter.InternalRpcTokenFilter`). It registers on `/rpc-api/*` in every servlet service that uses the security starter: system, infra, bpm and fst. Same property, same header constant, constant-time compare, both tokens accepted, and 401 for all `/rpc-api` when the token is unset (startup is not blocked). |
  | SEC-113 (Low): token read only at startup; gateway attached the token to every forwarded request | (a) A second accepted value `system.rpc.internal-token-previous`. Verifiers (the `/rpc-api` filter and the framework `login-user` check) accept either value through `InternalRpcTokenUtils.matchesAny` (`MessageDigest.isEqual` on both, no early exit). Senders (Feign interceptor, gateway, the two per-client interceptors) send only `system.rpc.internal-token`. The `refresh: true` lines are kept but now say the token is **not** hot-reloaded; see the rotation procedure. (b) The gateway attaches the token only where it writes `login-user` (`TokenAuthenticationFilter`, step 2.2). `InternalTokenRelayFilter` now only strips client `X-PSA-Internal-Token` / `login-user` and returns 404 for `/rpc-api`. The gateway's `oauth2/token/check` WebClient call still sends the token. |
- Round 6 (2026-10-14) fixes two Low review findings. Only the shareable patch changed; the local patch is unchanged.

  | Review finding | Change |
  |---|---|
  | SEC-114 (Low): the framework `/rpc-api` filter ran at order 0, after Spring Security (-100) and the tenant security filter (-99) | New `WebFilterOrderEnum.INTERNAL_RPC_TOKEN_FILTER = ENV_TAG_FILTER + 1` (`Integer.MIN_VALUE + 3`); the auto-configuration uses it. A request without a valid token now gets 401 before Spring Security runs. See the SEC-114 section. |
  | SEC-115 (Low): `SocialUserApi` mappings had no `PREFIX`, so system served them at `/get-authorize-url`, `/bind`, `/unbind`, `/get-bind-user-id` | All 4 mappings now use `PREFIX` (`/rpc-api/system/social-user/...`), so the `/rpc-api` filter guards them. Every other `@FeignClient` interface was checked; none has the same gap. See the SEC-115 section. |
- The two patches touch disjoint files and can be applied in either order. The shareable patch is safe on its own: the credential binders keep the existing in-code values when the Nacos keys are missing.
- Verification run (round 6): `git apply --check` passes for both patches on a fresh `git archive HEAD` copy. A JDK 8 offline `mvn compile` of gateway, system-biz, bpm-biz, fst-biz and infra-biz (with `-am`, so the framework starters are compiled too) passed with both patches applied, and 19 related unit tests pass. See "Verification" at the end.
- No credential value appears in this file or in the shareable patch. This was checked by machine against every literal removed in the local patch.

---

## SEC-001 (Critical): passwordless token minting on `customization-login`, `customization-login/id`, `a8-login`, `singleSignOn/YD-get`

### Hunks
| File | Change |
|---|---|
| `yudao-module-system-api/.../enums/ErrorCodeConstants.java` | New `AUTH_TRUSTED_CALLER_REJECTED` (1002000012) and `AUTH_PASSWORDLESS_SUPER_ADMIN_FORBIDDEN` (1002000013). |
| `.../framework/sso/TrustedCallerProperties.java` (new) | `psa.sso.trusted-callers.*`: `require-signature` (default **true**), `gateway-addresses`, `max-clock-skew` (5m), and per caller `callers.<code>.secret` / `.endpoints` / `.allowed-ips` / `.legacy-allowed-ips`. The repo holds no values. Round 3 removed `trusted-proxy-hops`. A leftover Nacos value for it is ignored, because Spring binding drops unknown keys. |
| `.../framework/sso/TrustedCallerConfiguration.java` (new) | Registers the properties, following the same pattern as `SeeyonSsoConfiguration`. |
| `.../framework/sso/TrustedCallerVerifier.java` (new) | See the rules below. |
| `yudao-gateway/.../filter/security/ClientIpHeaderFilter.java` (new, r3; r4) | A `GlobalFilter` at `HIGHEST_PRECEDENCE`. On every request it removes any client-supplied `X-PSA-Client-IP` and sets it to the client IP. The client IP is the gateway's TCP peer. **r4 (SEC-108):** when that peer is in `psa.gateway.trusted-proxies`, the filter uses the `X-Real-IP` header that nginx writes instead, if the header is an IP literal. All values are `InetAddress`-normalised. It never reads XFF. |
| `yudao-gateway/.../filter/security/ClientIpProperties.java` (new, r4) | `psa.gateway.trusted-proxies`: a list of exact IPs. It is empty by default, so the TCP peer is used. |
| `AuthController.java` | The 4 endpoints keep their paths, `@PermitAll` and response shapes. Each one calls `trustedCallerVerifier.verify(...)` with an `ENDPOINT_*` constant before minting a token. The signature fields travel in the same carrier as the user id: the JSON body for `customization-login*`, query/form params for `a8-login` and `YD-get`. `customization-login`, `customization-login/id` and `YD-get` call `authService.externalCallerLogin(...)`; `a8-login` calls `a8Login`. Round 3 also switched the token-carrying redirects of `a8-login` (~L121) and `social-login-get` (~L266) from `http://psa.forcitis.com/index?...` to `https://`. |
| `AdminAuthService.java` / `AdminAuthServiceImpl.java` | A shared private `passwordlessLogin(user, subject, externalCaller)` rejects missing users (`AUTH_LOGIN_BAD_CREDENTIALS`) and disabled users (`AUTH_LOGIN_USER_DISABLED`) on **every** passwordless path. This replaces the old `IndexOutOfBounds` / NPE. Only when `externalCaller=true` does it also reject **users holding an enabled super-admin role** (`AUTH_PASSWORDLESS_SUPER_ADMIN_FORBIDDEN`, via `PermissionService.getUserRoleIdsFromCache` + `RoleService.hasAnySuperAdmin`). `externalCaller=true` is set only by the new interface methods `externalCallerLogin(String)` and `externalCallerLogin(Long)` and by `a8Login`. `customizationLogin(String|Long)` and `customizationLoginByUserId` keep `externalCaller=false`; the WeCom OAuth paths `qyMiniLogin`, `qyH5Login` and `weiXinLogin` use them. Each rejection writes a login log. |

### Verifier rules
- **Client IP:** XFF is not parsed. If `request.getRemoteAddr()` is in `gateway-addresses` and `X-PSA-Client-IP` holds a literal IPv4 or IPv6 address, that header is the client IP. Otherwise the client IP is `request.getRemoteAddr()`. A client cannot set the header: the gateway filter overwrites it, and a request that bypasses the gateway is not from a gateway address, so its header is ignored.
- **Address comparison (r4, SEC-110):** `remoteAddr`, the `gateway-addresses` entries, the header value and exact-IP rules are normalised with `InetAddress.getByName(literal).getHostAddress()` before they are compared. Only IP literals are accepted, so no DNS lookup happens. `::1` and `0:0:0:0:0:0:0:1` now match, and so do `::ffff:10.0.0.1` and `10.0.0.1`. Normalised `gateway-addresses` are cached and recomputed when the list changes, so a Nacos refresh takes effect.
- **Signed request** (`sign` present):
  - `caller` must exist with a non-blank secret.
  - The endpoint must be in `callers.<code>.endpoints`.
  - If the caller has `allowed-ips`, the source IP must match one.
  - `timestamp` must match `^\d{13}$` and `nonce` must match `^[A-Za-z0-9_-]{16,64}$`.
  - The timestamp must fall in the window: reject when `ts < now - skew || ts > now + skew`.
  - The HMAC-SHA256 check uses a constant-time compare.
  - The nonce is single-use, held in Redis (`trusted_caller_sso:nonce:<caller>:<nonce>`) with a TTL of 2×skew. It is claimed only after the signature passes.
- **Unsigned request**: passes only when **all** of the following hold. Each pass logs a WARN.
  - `require-signature=false`.
  - The endpoint is **not** `a8-login` or `singleSignOn/YD-get`. These are browser redirects, where the source IP is the end user's browser, so they must sign or move to `/seeyon-sso`.
  - `request.getRemoteAddr()` is in `gateway-addresses`.
  - `X-PSA-Client-IP` is present. A gateway that predates the filter does not set it, and those requests are rejected, so the check fails closed.
  - The resolved client IP matches the `legacy-allowed-ips` of a caller that lists this endpoint. If `caller` is sent, only that caller is checked.
  - Legacy rules wider than `/29`, or with a prefix that does not parse, are ignored and logged at ERROR.
  - **r4 (SEC-108):** legacy rules that cover a loopback address (exact `127.x`/`::1`, or a CIDR containing `127.0.0.1`) or an address in `gateway-addresses` are also ignored and logged at ERROR. nginx, the gateway and the services run on the same host, so every request through a proxy arrives from one of those addresses. Such a rule would let any request through that proxy skip the signature check.
- **Subject:** a blank subject, or one containing CR/LF, is rejected on both paths. `callerCode`, `subject`, the client IP and the reason are logged with CR/LF replaced by `_`.
- **Startup (`@PostConstruct`):**
  - ERROR when `require-signature=true` and no callers are configured: all 4 endpoints will reject.
  - ERROR when `require-signature=false` and `gateway-addresses` is empty.
  - ERROR for unknown endpoint ids and for legacy rules wider than /29.
  - ERROR (r4) for `gateway-addresses` entries that are not IP literals, and for legacy rules that cover a loopback or gateway address.
  - There is no health indicator. The check runs once, so a Nacos hot refresh does not repeat it.

### Behaviour impact on current production callers
- **The patch breaks all 4 endpoints for unsigned callers as soon as it deploys with the default config.** Over 180 days these endpoints took YD-get 15,281, customization-login 2,371, customization-login/id 1,397 and a8-login 1,458 calls, all from external systems. **Do not deploy before the Nacos config below is in place.**
- **`YD-get` and `a8-login` (16.7k calls/180d) have no unsigned transition path at all.** Before the deploy, their callers must either sign or move to `/seeyon-sso`. This is a hard cutover that needs coordination.
- `customization-login*` return the token in JSON, so they are server-to-server calls (likely the BI server). They may use the unsigned transition if they reach system-server through a listed gateway and come from a /29-or-narrower legacy range.
- **Super-admin accounts can no longer log in through the 4 external passwordless endpoints** (customization-login, customization-login/id, a8-login, YD-get). They must use password login, or the WeCom or `/seeyon-sso` login.
- **WeCom OAuth logins are not affected by the super-admin refusal.** These are `qyMiniLogin`, `qyH5Login` and `weiXinLogin`, where WeCom vouches for the identity. Missing or disabled users still get an error instead of a token there; before, they got an NPE or a token. Note that `weiXinLogin` still falls back to a hard-coded personal account when the WeCom user is unmapped. That is out of scope here, and the fallback should be removed as a follow-up.
- `a8-login` and `social-login-get` now redirect to `https://psa.forcitis.com/index?...`. YD-get already redirected to `https://psa.forcitis.com`, so the front-end is assumed to be served over HTTPS. Confirm with `curl -sSI https://psa.forcitis.com/index` before release.
- The client IP comes from the gateway-written `X-PSA-Client-IP` and is never taken from XFF. In production there are two ways in (**corrected r5**): direct to `48088`, which is the gateway's own TLS port, so the gateway's TCP peer is the real client IP; or through the 443 nginx server, which forwards to `https://127.0.0.1:48088`, so the peer is `127.0.0.1`. **r4:** set `psa.gateway.trusted-proxies: [127.0.0.1]` (plus `::1` if nginx connects over IPv6) on the gateway. For the 443 path the gateway then uses nginx's `X-Real-IP`, which nginx sets from `$remote_addr` and so replaces any client value. Direct `48088` callers are not in `trusted-proxies`, so any `X-Real-IP` they send is ignored and the peer address is used. Without that setting, requests through the 443 nginx carry `127.0.0.1` and the legacy path cannot tell them apart. **Do not** put a loopback or proxy IP in `legacy-allowed-ips`: the verifier now ignores such rules and logs ERROR. Treat the IP checks as defence in depth; the signature is the real control.

### Caller-side change (send to each external system owner)
Add four fields next to the existing user id:
- `caller`: the assigned code.
- `timestamp`: epoch millis, 13 digits.
- `nonce`: 16–64 characters from `[A-Za-z0-9_-]`, single use.
- `sign`: computed as below.

```
sign = lowercase_hex( HMAC_SHA256( secret, caller + "\n" + timestamp + "\n" + nonce + "\n" + endpoint + "\n" + subject ) )
endpoint ∈ { "customization-login", "customization-login/id", "a8-login", "singleSignOn/YD-get" }
subject  = the exact string sent as userId (customization-login*) or memberId (a8-login, YD-get); no CR/LF
```
- `customization-login`: `POST {"userId":"<username>","caller":"bi","timestamp":"…","nonce":"…","sign":"…"}`
- `singleSignOn/YD-get`: `GET …/singleSignOn/YD-get?memberId=<id>&caller=yd&timestamp=…&nonce=…&sign=…`. The link must be created server-side right before the redirect: it is valid for 5 minutes and works once.
- Shell check: `printf '%s\n%s\n%s\n%s\n%s' "$caller" "$ts" "$nonce" "$endpoint" "$subject" | openssl dgst -sha256 -hmac "$secret" -hex`

### Ops steps
1. **Stopgap now, before any code ships.** These are network and config changes only:
   - Pull 30 days of access logs for `/admin-api/system/auth/{customization-login,customization-login/id,a8-login,singleSignOn/YD-get,login/KM}` and list the distinct source IPs. **Correction (r5, supersedes the r4 correction):** `:48088` is the gateway process's own TLS port (same process as `48080`), not nginx. Calls to `:48088` reach the gateway directly with their real source IP, so the source IPs come from the gateway's access log (`AccessLogFilter`), not from nginx. Only calls through the 443 nginx server (`/etc/nginx3/conf.d/psa.conf`, which forwards to `https://127.0.0.1:48088`) arrive as `127.0.0.1`; that server's access_log is off, and it had 0 hits on these paths in the r4 check. The BI server reaches `:48080` directly.
   - Aliyun SG: narrow `48088 ← 0.0.0.0/0` to the caller CIDRs plus the office/VPN egress. Keep `48080 ← BI server`.
   - Gateway (Nacos route config, no code): add a higher-priority route for the 4 paths plus `/login/KM`, with a `RemoteAddr=` predicate listing the caller CIDRs, and a catch-all route for those paths that returns 403.
   - **nginx (r4, SEC-107): delete the `location ^~ /other-api/` block** from the 443 server in `/etc/nginx3/conf.d/psa.conf` (it forwards to system `127.0.0.1:48081` and bypasses the gateway), then `nginx -t && nginx -s reload`. Prod Druid is already off (SEC-007), so the location has no legitimate use. If Druid is ever needed, expose only that path with an allowlist:
     ```nginx
     location ^~ /other-api/druid/ {
         allow <ops IP>; deny all;
         proxy_pass http://127.0.0.1:48081/druid/;
         proxy_set_header X-PSA-Client-IP "";
     }
     ```
     This is a stopgap that needs no code change. Do it now, before the code below ships. The repo copy `scripts/deploy/dev-lan/nginx/psa-pc.conf` drops the same block in the patch.
   - **SEC-109 (r4):** nginx does not inherit `proxy_set_header` from the `server` level into a `location` that has its own `proxy_set_header` lines, and every PSA location has them. So put the clear line **inside every such `location`** of the 443 server (the only nginx server in front of the gateway in prod; `48088` is the gateway itself, not an nginx server), next to the existing headers:
     ```nginx
     location /api/ {                       # and every other location that proxies to the gateway (6 in prod)
         proxy_pass https://127.0.0.1:48088/;   # prod; dev-lan uses http://127.0.0.1:48080/
         proxy_set_header Host $host;
         proxy_set_header X-Real-IP $remote_addr;   # SEC-108: the gateway trusts this only from trusted-proxies
         proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
         proxy_set_header X-PSA-Client-IP "";        # SEC-109: drop any client-supplied value
     }
     ```
     The gateway also strips `X-PSA-Client-IP`, `X-PSA-Internal-Token` and `login-user` itself, so this line is defence in depth. The `X-Real-IP $remote_addr` line, by contrast, is **required** in every gateway-bound location (SEC-112): the gateway trusts `X-Real-IP` from `127.0.0.1`, so a location without it would pass a client-supplied value through. The prod check found all 6 locations compliant; it is now a go-live checklist item in the runbook.
2. Find the gateway address(es) as system-server sees them (`remoteAddr` of requests forwarded by yudao-gateway; the internal IPs of the gateway hosts).
   - These IPs must not be shared with any other proxy that can reach system-server directly. In production the gateway is on the same host, so this is `127.0.0.1`. That is safe only after nginx `/other-api/` is removed (step 1) and nothing else on the host proxies to `48081`.
   - **Deploy order:** follow the SEC-107 order below: Nacos token first, then system, the other services and the gateway, then nginx. If system-server ships before the gateway, unsigned requests are rejected (fail closed). Signed requests still work, but `allowed-ips` will see the gateway IP.
3. Nacos (`system-server` dataId, prod namespace) **before** deploying the patch:
   Gateway dataId (named after the gateway's `spring.application.name`), SEC-108:
   ```yaml
   psa:
     gateway:
       trusted-proxies: [ 127.0.0.1 ]   # the 443 nginx server, which forwards to https://127.0.0.1:48088; add ::1 if it connects over IPv6
   ```
   system-server dataId:
   ```yaml
   psa:
     sso:
       trusted-callers:
         require-signature: false            # transition only; customization-login* only
         gateway-addresses: [ <gateway internal IPs> ]   # only trusted source of X-PSA-Client-IP
         callers:
           yd:  { secret: <≥32 random bytes>, endpoints: [ "singleSignOn/YD-get" ] }
           a8:  { secret: <…>, endpoints: [ "a8-login" ] }
           bi:  { secret: <…>, endpoints: [ "customization-login", "customization-login/id" ],
                  allowed-ips: [ <BI server IP> ], legacy-allowed-ips: [ <BI server IP or ≤/29> ] }
   ```
4. Deploy, then check the startup log for `[checkConfig]` ERRORs. Watch the WARN `未签名的免密登录请求按过渡期白名单放行` to see who still sends unsigned requests. Hand each caller its secret over a secure channel.
5. Once the WARN count reaches 0, set `require-signature: true` and remove `legacy-allowed-ips`.
6. Longer term: move the A8/YD callers to `/seeyon-sso` and remove these endpoints.

### Tests
- Unit test for `TrustedCallerVerifier` (mock Redis and properties). **Not written yet.** Cover:
  - a valid signature;
  - a wrong signature;
  - a timestamp at `now±skew` (accepted) and at `±(skew+1)` (rejected);
  - a 12- or 14-digit timestamp, a 15- or 65-character nonce, and a nonce with `.`;
  - a replayed nonce;
  - an unknown caller;
  - an endpoint the caller does not list;
  - an IP outside `allowed-ips`;
  - unsigned requests:
    - on `a8-login`/`YD-get` → always rejected;
    - with `remoteAddr` not in `gateway-addresses` (even if `X-PSA-Client-IP` is set) → rejected;
    - from a gateway address but without `X-PSA-Client-IP` → rejected;
    - with a client-prepended XFF → ignored, so the IP comes from the header;
    - with a non-IP header value → falls back to `remoteAddr`;
    - with a legacy rule `/28` → the rule is ignored;
    - the happy path → accepted;
  - gateway `ClientIpHeaderFilter`: a request that already carries `X-PSA-Client-IP: 1.2.3.4` reaches the route with the TCP peer address instead;
  - (r4, SEC-108) peer `127.0.0.1` in `trusted-proxies` with `X-Real-IP: 203.0.113.9` → header `203.0.113.9`; the same request with `trusted-proxies` empty → `127.0.0.1`; peer not in `trusted-proxies` with `X-Real-IP: 203.0.113.9` → the peer; `X-Real-IP: evil.example` → the peer;
  - (r4, SEC-108) `legacy-allowed-ips: [127.0.0.1]`, `[127.0.0.0/29]`, `[::1]` or a gateway address → ERROR at startup, and an unsigned request from that IP is rejected;
  - (r4, SEC-110) `gateway-addresses: [::1]` with `remoteAddr` `0:0:0:0:0:0:0:1` → treated as the gateway; `allowed-ips: [::ffff:10.0.0.5]` matches client `10.0.0.5`; `gateway-addresses: [gw.local]` → ERROR, the entry is ignored;
  - a subject containing `\n`.
- Service:
  - a super-admin user via `externalCallerLogin` / `a8Login` → 1002000013, no token;
  - the same user via `qyH5Login` / `qyMiniLogin` → token issued, as before;
  - a disabled user → 1002000001;
  - a missing id → 1002000000.
- dev end-to-end: sign with the shell snippet and call each endpoint. Replaying the same request must be rejected.

---

## SEC-107 (High, release blocker): nginx `/other-api` → system `48081` bypasses the gateway; `/rpc-api/system/**` and `login-user` are trusted without proof

### Why the source address cannot be the control
nginx, the gateway and the services run on the same host. A request that comes in through `/other-api` reaches system from `127.0.0.1`, the same address the gateway uses. So the fix binds trust to the shared internal token (`X-PSA-Internal-Token` / `system.rpc.internal-token`), which the repo already used for two paths. The source address is not used.

### Hunks (shareable patch)
| File | Change |
|---|---|
| `yudao-common/.../enums/RpcConstants.java` | Adds the `INTERNAL_TOKEN_HEADER` (`X-PSA-Internal-Token`) and `INTERNAL_TOKEN_PROPERTY` (`system.rpc.internal-token`) constants. |
| `yudao-common/.../util/rpc/InternalRpcTokenUtils.java` (new) | `matches(expected, supplied)`: `MessageDigest.isEqual` over UTF-8 bytes. It returns false when the expected value is blank, so a missing token fails closed. |
| `yudao-spring-boot-starter-rpc/.../rpc/config/YudaoRpcAutoConfiguration.java` (new) + `META-INF/spring/...AutoConfiguration.imports` (new) | The rpc starter had no auto-configuration; this adds one. It registers `InternalRpcTokenRequestInterceptor` as a parent-context bean, which every Feign client picks up. When the token is unset it logs ERROR at startup. |
| `yudao-spring-boot-starter-rpc/.../rpc/core/feign/InternalRpcTokenRequestInterceptor.java` (new) | Sets the token on every Feign request whose path starts with `/rpc-api/`, removing any existing value first. Every PSA Feign client targets `/rpc-api/*`. Third-party Feign clients added later will not get the token. |
| `PermissionApiFeignConfiguration` / `OrgSynchronizationSnapshotApiFeignConfiguration` | Remove the header before setting it, so the header is not sent twice next to the global interceptor. |
| ~~system `InternalRpcTokenFilter` / `InternalRpcTokenConfiguration`~~ **r5: deleted**, replaced by the framework filter below (SEC-111) | (r4) Registered on `/rpc-api/*` in system only. |
| **r5** security starter `core/filter/InternalRpcTokenFilter.java` (new) + `config/YudaoInternalRpcTokenAutoConfiguration.java` (new) + `AutoConfiguration.imports` | `@ConditionalOnWebApplication(SERVLET)`, so it registers in system, infra, bpm and fst (all four use the security starter; the WebFlux gateway does not). `FilterRegistrationBean` on `/rpc-api/*`, order 0 (**r6, SEC-114:** now `WebFilterOrderEnum.INTERNAL_RPC_TOKEN_FILTER`, before Spring Security), covering all RPC of each service, including System `oauth2/token/create` and `token/check`. Accepts `system.rpc.internal-token` or `system.rpc.internal-token-previous` (constant time). When the current token is unset, every request gets 401 and an ERROR is logged at startup; startup continues. A WARN is logged while `-previous` is set. The Spring Security `permitAll` on each `/rpc-api/**` is unchanged; this filter is the gate. |
| system tests `InternalRpcTokenFilterTest` / `InternalRpcTokenConfigurationTest` (now test the framework classes) | `token/create` without the token → 401 and with it → 200; representative infra, bpm and fst RPC paths → 401 / 401 / 200; an unset token (or only `-previous` set) rejects everything; during rotation both values pass and a third value fails; removing `-previous` makes the old value fail; `matchesAny` cases; the filter is registered on `/rpc-api/*`; the auto-configuration is listed in `AutoConfiguration.imports`. |
| `yudao-spring-boot-starter-security/.../TokenAuthenticationFilter.java` + `YudaoSecurityAutoConfiguration` | `buildLoginUserByHeader` trusts `login-user` only when the request has a valid `X-PSA-Internal-Token`. Otherwise it ignores the header and falls through to the `Authorization` token check. The token is injected from `system.rpc.internal-token`; **r5:** `system.rpc.internal-token-previous` is also accepted. This covers system, infra, bpm and fst. |
| **r5** `RpcConstants` / `InternalRpcTokenUtils` | Adds `INTERNAL_TOKEN_PREVIOUS_PROPERTY` and `matchesAny(current, previous, supplied)`: false when the current token is unset; otherwise compares against both with `MessageDigest.isEqual` and no early exit. |
| gateway `TokenAuthenticationFilter` | The WebClient `oauth2/token/check` call sends the token (unchanged in r5). **r5 (SEC-113 b):** where the filter writes `login-user` (step 2.2) it also sets the token, and that is the only place the gateway attaches it to a forwarded request. Note: the gateway currently skips auth (`if (true) return chain.filter(...)`), so today it never calls `token/check` and never writes `login-user`; both headers keep working if auth is turned back on. |
| gateway `InternalTokenRelayFilter` (new; r5 narrowed) | A `GlobalFilter` at `HIGHEST_PRECEDENCE + 1`. It removes client-sent `X-PSA-Internal-Token` **and `login-user`**, and returns 404 for any path that contains `/rpc-api` (raw or decoded). **r5:** it no longer attaches the token, so forwarded requests carry no token unless the gateway wrote `login-user`. It runs before the gateway's own `TokenAuthenticationFilter` (-100), so what that filter writes survives. |
| **r5** gateway test `InternalTokenRelayFilterTest` (new) | Client token / `login-user` stripped, `Authorization` kept, no token attached; `/rpc-api`, `../../rpc-api` and `%2e%2e/RPC-API` → 404 and not forwarded. |
| gateway / bpm / infra `bootstrap-{local,dev,prod}.yaml` | Add the `psa-system-rpc.yaml` shared config (system and fst already load it). **r5:** the comment now says the token is read only at startup and `refresh: true` does not hot-reload it. |
| `scripts/deploy/dev-lan/nginx/psa-pc.conf` | Removes `location /other-api/`, adds `proxy_set_header X-PSA-Client-IP "";` to `/api/` (SEC-109), and adds a header comment. |
| **r5** `scripts/deploy/dev-lan/nginx/psa-h5.conf` | Adds `proxy_set_header X-PSA-Client-IP "";` to `/api/` (SEC-109 remainder). `X-Real-IP $remote_addr` was already there. |
| `docs/ops/prelaunch-golive-runbook.md` | Drops the `/other-api` row from the entry table and replaces the warning: the location is gone, and Druid is allowed only via `^~ /other-api/druid/` with an IP allowlist. **r5:** corrects the `48088` rows and text (gateway's own TLS port, not an nginx server; the maintenance page and nginx `geo` cannot restrict it, the security group must; stopping nginx does not stop it; direct 48088 traffic is not in the nginx access log), and adds T-1 checklist items for SEC-112 (every gateway-bound location sets `X-Real-IP $remote_addr` and clears `X-PSA-Client-IP`) and for the internal token. |

**A related bug, fixed in passing.** The gateway's `TokenAuthenticationFilter.filter` calls `SecurityFrameworkUtils.removeLoginUser(exchange)` but throws away the returned exchange. So a client-sent `login-user` header has **always** been forwarded to the services. Before r4 that was harmless only when the services were reached directly; through the gateway it was already a spoofing path. The new relay filter attaches the token to every forwarded request, so it must also strip `login-user`, and it does.

### Coverage: Feign clients and other internal calls
Every service-to-service call is a Feign client under `/rpc-api/*`, and the global interceptor covers all of them. A search for `@FeignClient`, `@EnableFeignClients`, `RestTemplate`, `WebClient`, `HttpUtil` and `/rpc-api|4808x` literals across `psa-backend`, the front-ends, `scripts/` and `tools/` found:

| Client (target service, prefix) | Declared in | Enabled by |
|---|---|---|
| system (`/rpc-api/system/*`): `AdminUserApi`, `DeptApi`, `PostApi`, `RoleApi`, `PermissionApi`*, `OAuth2TokenApi`, `DictDataApi`, `ErrorCodeApi`, `LoginLogApi`, `OperateLogApi`, `MailSendApi`, `NotifyMessageSendApi`, `SmsCodeApi`, `SmsSendApi`, `SocialUserApi`, `SensitiveWordApi`, `SkillTagsApi`, `OrgSynchronizationSnapshotApi`* | system-api | framework `YudaoSecurityRpcAutoConfiguration` (OAuth2Token, Permission), error-code / operatelog / dict starters, and the bpm, fst and infra `RpcConfiguration` |
| infra (`/rpc-api/infra/*`): `FileApi`, `ApiAccessLogApi`, `ApiErrorLogApi` | infra-api | web starter `YudaoApiLogRpcAutoConfiguration`, system `RpcConfiguration` |
| bpm (`/rpc-api/bpm/*`): `BpmProcessTaskApi`, `BpmProcessInstanceExtApi`, `BpmTaskApi`, `CustomizedBpmApi` (`/rpc-api/bpm/customized`) | bpm-api | fst `RpcConfiguration` / `@EnableFeignClients` |
| fst (`/rpc-api/fst/*`): `ProjectTaskApi`, `WorkflowFormApi`, `WorkflowHookApi` | fst-api | bpm `RpcConfiguration` |

\* These clients also have their own per-client interceptor, which is now idempotent.

- **Non-Feign:** only the gateway's WebClient `token/check` call (hunk above). There are no `RestTemplate`, `HttpUtil` or WebClient calls to `/rpc-api` or to service ports in any module.
- `psa-backend/http-client.env.json` points at `127.0.0.1:48081/admin-api` (IDE scratch requests). It sends `Authorization`, so it is unaffected.
- **Front-end:** only `VUE_APP_OTHER_API + "/druid/index.html"` (`src/views/infra/druid/index.vue`). It breaks once `/other-api` is removed. Druid is off in prod anyway (SEC-007). Follow-up: point `.env.*` at an allowlisted Druid path or remove the menu.
- ~~**Not covered by the system filter:** `/rpc-api/{infra,bpm,fst}/**` stay `permitAll`.~~ **r5 (SEC-111): covered.** The framework filter now guards `/rpc-api/*` in infra, bpm and fst too. Callers of those prefixes, checked in r5: all 10 clients (infra 3, bpm 4, fst 3) are Feign clients in the `*-api` modules, called from services that all include the rpc starter (system, infra, bpm, fst), so the global interceptor sends the token on every call. There is no non-Feign caller (no `RestTemplate` / `HttpUtil` / WebClient call to `/rpc-api` in any module, script or front-end). All three services load `psa-system-rpc.yaml` (infra and bpm via this patch, fst already). The gateway returns 404 for any external path containing `/rpc-api`, so no external request reaches these prefixes through it. No existing test in infra, bpm or fst calls `/rpc-api` over HTTP.
- **External requests no longer carry the token (r5, SEC-113 b).** Because the gateway never writes `login-user` today, requests it forwards reach the services with neither `login-user` nor the token. The framework `TokenAuthenticationFilter` then authenticates them by their own `Authorization` token through `OAuth2TokenApi.checkAccessToken` (a Feign call to `/rpc-api/system/oauth2/token/check` that carries the token via the global interceptor; in system it is a local call). That is the same path every external request took before r4, so nothing downstream depends on the gateway attaching the token.
- **Doc conflict:** `docs/domain/.../差旅接口规范资产.md` (~L99) says the Feign token must be injected only into the snapshot API and must not become a default header. That conflicts with this design, which security review has approved. Update the contract doc in the same PR. `scripts/operations/travel-menu-bootstrap/README.md` and `docs/ops/travel-menu-bootstrap.md` also say only System and FST load `psa-system-rpc.yaml`; now all 5 services do. `tools/local-dev/lib/nacos-config.js` writes only `internal-token` into the local `psa-system-rpc.yaml`; that is enough (`-previous` is optional).

### Deploy order (r5: all four servlet services now enforce the token on `/rpc-api`)
1. **Tokens first, in every service.** In each namespace (dev, then prod), make sure `DEFAULT_GROUP/psa-system-rpc.yaml` contains `system.rpc.internal-token`. It already exists for system and fst; reuse that value. If this release also rotates the token (recommended after SEC-107, because the prod access_log was off), set it up as in phase A of the rotation procedure below **before** the first service ships, so `system.rpc.internal-token-previous` is in place too. gateway, system, fst, bpm and infra all load the same dataId after this patch. Values are read once at startup; do not change them while a release is in progress.
2. **Release the services one after another, in the same window: system, then infra, bpm and fst, then gateway.**
   - **What breaks if a service lags behind:**
     - **system, infra, bpm, fst (SEC-107/111):** once one of them is on r5 it rejects any `/rpc-api` call without the token (401). Callers still on the old build send no token, so their calls into that service fail until they are released too. For example: fst on r5 + bpm still old → bpm's `WorkflowHookApi` / `ProjectTaskApi` calls into fst fail; bpm on r5 + fst still old → fst's `BpmProcessInstanceExtApi` / `CustomizedBpmApi` calls fail; infra on r5 + others old → API access/error logging and `FileApi` uploads fail. Keep the gap between the four releases short.
     - **login-user:** services on r5 ignore `login-user` from callers without the token; callers on the old build lose the user context on that RPC until released.
     - **gateway:** it no longer attaches the token, so its position in the order only matters for the `/rpc-api` 404 and the header stripping. Releasing it last is still the safest order.
   - Watch every service log for `system.rpc.internal-token 未配置` ERRORs. Any such line means that service's `/rpc-api` returns 401 for every call.
3. **Last, nginx (prod `/etc/nginx3/conf.d/psa.conf` and dev-lan `psa-pc.conf` / `psa-h5.conf`):** delete `/other-api`, make sure every gateway-bound location has `X-Real-IP $remote_addr` (SEC-112) and `proxy_set_header X-PSA-Client-IP "";` (SEC-109), `nginx -t`, then `nginx -s reload`. The `/other-api` removal can also be done first, as the stopgap in SEC-001 step 1. It does not depend on the code.

### Internal token rotation (SEC-113)
The token is read only at startup in every service. `refresh: true` on the shared config does **not** hot-reload it: the filter, the Feign interceptor and the gateway capture the value when their beans are built. Every change therefore needs a restart of all 5 services. `system.rpc.internal-token` is the only value anyone **sends**; `system.rpc.internal-token-previous` is only **accepted**.

The two-step sequence from the r4 review (set `token=new, previous=old` → rolling restart → remove `previous` → rolling restart) has a gap during the first rolling restart: a service that has already restarted sends the new value to services that have not, and those still accept only the old value, so those calls get 401 until the last restart. Either restart all 5 services together in a maintenance window, or use these three phases, which never produce a 401:

| Phase | `psa-system-rpc.yaml` | Then | Effect |
|---|---|---|---|
| A | `internal-token: <old>`, `internal-token-previous: <new>` | restart all 5 one by one | every service still sends `<old>`; restarted ones also accept `<new>` |
| B | `internal-token: <new>`, `internal-token-previous: <old>` | restart all 5 one by one; check logs for 401 / `internal rpc authentication required` | restarted services send `<new>`; every service (restarted or not) accepts both |
| C | delete `internal-token-previous` | restart all 5 one by one | every service sends `<new>` already; restarted ones accept only `<new>` |

- In phase A the `-previous` key temporarily holds the **new** value. That is intended: the key means "also accepted", not "older".
- Confirm zero 401s before moving on, and keep the `-previous` WARN in mind: after phase C no service should log it.
- Generate the new value with ≥32 random bytes, store it only in Nacos, and never paste it into tickets or shell history.

### dev test checklist (192.168.6.200)
1. All 5 services start with no `未配置` ERROR. Log in on the PC and in H5, open a project list, submit and approve one timesheet (bpm↔fst↔system RPC), and upload and preview a file (infra).
2. `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:48081/rpc-api/system/oauth2/token/check?accessToken=x` → `401`. The same call with `-H "X-PSA-Internal-Token: <value from Nacos>"` → `200`. Do not paste the value into tickets or shells with history.
3. `curl … -X POST http://127.0.0.1:48081/rpc-api/system/oauth2/token/create -H 'Content-Type: application/json' -d '{"userId":1,"userType":2,"clientId":"default"}'` with no token → `401`.
4. `curl -H 'login-user: {"id":1,"userType":2,"tenantId":1}' http://127.0.0.1:48081/admin-api/system/user/page` with no `Authorization` → `401`. It used to return the super admin's view.
5. Through nginx: `curl -s -o /dev/null -w '%{http_code}' http://192.168.6.200:8100/other-api/rpc-api/system/oauth2/token/check` → the SPA `index.html` (200 with HTML) or 404, and never JSON from system.
6. Through the gateway: `curl --path-as-is https://<gw>/admin-api/system/../../rpc-api/system/user/get?id=1` and `…/admin-api/system/%2e%2e/%2e%2e/rpc-api/…` → 404/400. Sending `X-PSA-Internal-Token: x` and `login-user: {…}` with no `Authorization` header → 401.
7. FST restart: the startup Runner refresh of the permission cache still succeeds (that call goes through `PermissionApi`).
8. **(r5, SEC-111) infra, bpm and fst `/rpc-api` without the token → 401** (the filter rejects before any controller runs, so nothing is written):
   - `curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:48082/rpc-api/infra/api-access-log/create` → `401`;
   - `curl -s -o /dev/null -w '%{http_code}' 'http://127.0.0.1:48083/rpc-api/bpm/customized/get?id=1'` → `401`;
   - `curl -s -o /dev/null -w '%{http_code}' 'http://127.0.0.1:48084/rpc-api/fst/project/task/get?id=1'` → `401`.
   The two read-only GETs with the Nacos token reach the controller (200 or a business error, never 401). Do not repeat the POST with the token.
9. **(r5) Internal call paths of the three services, end to end, with no 401 in any log:**
   - **infra:** upload and preview a file from the PC (`FileApi`); after any admin request, a row appears in the API access log (`ApiAccessLogApi`, called from every service); trigger an error and check the API error log (`ApiErrorLogApi`).
   - **bpm:** submit a timesheet or a quotation for approval from fst (fst → `BpmProcessInstanceExtApi` / `CustomizedBpmApi`), then approve one task in the PC (`BpmTaskApi` / `BpmProcessTaskApi`).
   - **fst:** completing that approval calls back into fst (bpm → `WorkflowHookApi`, `WorkflowFormApi`, `ProjectTaskApi`); the business document's status changes.
10. **(r5, SEC-113 b)** Through the gateway with a valid `Authorization` header, the request succeeds as before (the service validates the token itself). With a forged `login-user` and no `Authorization` → 401.
11. **(r5, SEC-113 a) Rotation dry run on dev:** run phases A, B and C of the rotation procedure and repeat items 1 and 9 after each phase.

### Rollback
- **Code:** redeploy the previous jars, in reverse order (gateway, then bpm, fst and infra, then system). The token stays in Nacos, which is harmless.
- Rolling back only the framework while system stays on r4 does not work: old clients send no token, so System RPC returns 401. Since r5 the same holds for infra, bpm and fst. Roll back all five as a set.
- **nginx:** restore `/other-api` only if it is truly needed, and then only the allowlisted `^~ /other-api/druid/` form. Never restore the open passthrough.

### Tests
- Unit tests, done (r5): `InternalRpcTokenFilterTest` (9 cases, incl. infra/bpm/fst paths and rotation), `InternalRpcTokenConfigurationTest` (2), gateway `InternalTokenRelayFilterTest` (2) pass. `OrgSynchronizationSnapshotApiFeignConfigurationTest` and gateway `AccessLogFilterTest` still pass.
- Unit tests, not written yet:
  - `InternalRpcTokenRequestInterceptor`: sets one header on `/rpc-api/...`; sets nothing on other paths or when the token is blank; does not duplicate an existing value.
  - framework `TokenAuthenticationFilter`: `login-user` with no token or a wrong token → ignored; with the current or `-previous` token → trusted; token unset → always ignored.
  - gateway `TokenAuthenticationFilter`: when auth is re-enabled and a user is resolved, the forwarded request has both `login-user` and the token; otherwise neither.
- dev: the checklist above.

---

## SEC-114 (Low): the `/rpc-api` filter ran after Spring Security

### Problem
`YudaoInternalRpcTokenAutoConfiguration` (r5) registered the filter with `setOrder(0)`. Spring Boot registers the Spring Security chain as a `DelegatingFilterProxyRegistrationBean` at `spring.security.filter.order`, default `SecurityProperties.DEFAULT_FILTER_ORDER` = -100 (no PSA config overrides it). Servlet filters run in ascending order, so an unsigned `/rpc-api` request first went through the whole Spring Security chain, including the framework `TokenAuthenticationFilter` (which parses `Authorization` and `login-user` and may make a Feign `token/check` call), and through the tenant security filter (-99). Only then did it get 401. `/rpc-api/**` is `permitAll` in Spring Security, so it was still rejected, but work and side effects ran before the gate.

### Change
| File | Change |
|---|---|
| `yudao-common/.../enums/WebFilterOrderEnum.java` | Adds `INTERNAL_RPC_TOKEN_FILTER = ENV_TAG_FILTER + 1` (`Integer.MIN_VALUE + 3`). It is a separate constant, not `TRACE_FILTER + 1`, because that value equals `ENV_TAG_FILTER` and two equal orders have no defined order between them. |
| security starter `YudaoInternalRpcTokenAutoConfiguration` | `registration.setOrder(WebFilterOrderEnum.INTERNAL_RPC_TOKEN_FILTER)` plus a Javadoc line on why. |

Resulting order (servlet services): CORS (`MIN`) → trace (`MIN+1`) → env tag (`MIN+2`) → **internal token (`MIN+3`)** → request body cache (`MIN+500`) → tenant context (-104) → API access log (-103) → XSS (-102) → Spring Security (-100) → tenant security (-99) → Flowable (-98). The filter runs after trace, so the trace id is set when it logs. `ApiAccessLogFilter` only handles `/admin-api` and `/app-api`, so no access-log entry is lost for `/rpc-api`. fst's `FenbeitongEventReceptionConfiguration` (`MIN+499`) is on a non-`/rpc-api` path and is not affected.

### Test cases
- `InternalRpcTokenConfigurationTest.shouldRunBeforeSpringSecurityAndTenantFilters`: the order equals the new constant, is greater than `TRACE_FILTER`, and is less than `REQUEST_BODY_CACHE_FILTER`, `TENANT_CONTEXT_FILTER`, `SecurityProperties.DEFAULT_FILTER_ORDER` and `TENANT_SECURITY_FILTER`.
- `InternalRpcTokenConfigurationTest.shouldBeRegisteredBeforeSpringSecurityFilterChain`: puts the filter, a `DelegatingFilterProxyRegistrationBean("springSecurityFilterChain")` at the Boot default order and a tenant-security registration into a bean factory. `ServletContextInitializerBeans` (the class Boot uses to register filters with Tomcat) returns them in the order internal token → Spring Security → tenant security.
- Dev (runtime, not run): `curl -i http://127.0.0.1:48081/rpc-api/system/oauth2/token/create` with a forged `Authorization` and no token → 401, and the system log has no `TokenAuthenticationFilter` / `checkAccessToken` entry for that request.

### Risk
- Low. Only the position of the filter changes; what it accepts and rejects is unchanged.
- CORS (`MIN`) still runs before the filter, so a 401 keeps its CORS headers; `/rpc-api` is never called from a browser anyway.
- If a service sets `spring.security.filter.order` below `MIN+3` (none does today), Spring Security would run first again. The first test pins the relation to the Boot default only.
- Rollback: with the jars.

---

## SEC-115 (Low): `SocialUserApi` served at root paths

### Problem
In `yudao-module-system-api/.../api/social/SocialUserApi.java` the 4 mappings were `@GetMapping("/get-authorize-url")`, `@PostMapping("/bind")`, `@DeleteMapping("/unbind")` and `@GetMapping("/get-bind-user-id")`, without the `PREFIX` the interface declares (`/rpc-api/system/social-user`). `SocialUserApiImpl` is a `@RestController` with no class-level mapping, so system served them at the root paths. They are outside `/rpc-api`, so the internal-token filter did not cover them. Spring Security treats them as `anyRequest().authenticated()`, so any logged-in user who reaches system directly could bind or unbind a social account for any `userId` / `userType`, and resolve a bound user id from a `code`/`state`. Through the gateway they are not routed (it forwards only `/admin-api`, `/app-api` and similar), so exposure was limited to direct access to the service port (for example the old nginx `/other-api`, removed in r4).

### Change
| File | Change |
|---|---|
| `SocialUserApi.java` | All 4 mappings use `PREFIX + "/..."`, the same pattern as `SmsCodeApi`, `OAuth2TokenApi` and the other system `*Api` interfaces. |

Callers checked:
- `SocialUserApiImpl` only implements the interface and inherits the mappings; it has no mapping of its own, so it needs no change.
- No module injects `SocialUserApi` as a Feign client (the only references are the interface and its implementation), and no front-end, script or nginx file calls these paths (the PC `permission.js` whitelist entry `/bind` is a front-end route, not this API). So no caller has to change. If a Feign caller is added later, it will use the prefixed path and get the token from the global interceptor.
- The admin `SocialUserController` (`/system/social-user/bind`, `/unbind`, under `/admin-api`) is a separate controller and is unchanged.

Whole-repo scan: every `@FeignClient` interface (28, across system, infra, bpm and fst `*-api`) was checked for mapping values without `PREFIX` or `/rpc-api`. `SocialUserApi` was the only one. No `*ApiImpl` declares its own mapping, and no non-Feign `*Api` interface has mappings.

### Test cases
- `SocialUserApiMappingTest.everyMappingIsUnderRpcPrefix`: `PREFIX` equals `/rpc-api/system/social-user`, and all 4 merged `@RequestMapping` paths start with `PREFIX + "/"`.
- Dev (runtime, not run): `POST http://127.0.0.1:48081/bind` → 404; `POST /rpc-api/system/social-user/bind` without the token → 401 (SEC-114: before Spring Security); with the token → reaches the controller.

### Risk
- Low. The old root paths stop working. Nothing in the repo calls them; an external caller that hit system directly would now get 404, which is the intent.
- The 4 endpoints still accept a client-chosen `userId` once the token is valid, like every other internal RPC. That is the trust model of `/rpc-api`.
- Rollback: with the jars.

---

## SEC-108 / SEC-109 / SEC-110: client-IP trust behind nginx
These change SEC-001 code and ops and are described there:
- **SEC-108:** gateway `ClientIpHeaderFilter` + `ClientIpProperties` (`psa.gateway.trusted-proxies`); a `TrustedCallerVerifier.checkConfig` ERROR for, and the verifier ignores, legacy rules that cover a loopback or gateway address; the corrected `48088` wording in the SEC-001 ops step 1 (r5: corrected again; `48088` is the gateway's own TLS port).
- **SEC-109:** `proxy_set_header X-PSA-Client-IP "";` in every location that sets its own proxy headers (the SEC-001 ops step 1 example and the repo `psa-pc.conf`; r5: `psa-h5.conf`).

### SEC-108 after the 48088 correction (r5)
The r4 premise ("48088 is nginx, so the gateway always sees 127.0.0.1") was wrong, but the fix still holds, and the reasoning is simpler:
- **Direct to `48088`** (open to `0.0.0.0/0`): the gateway's TCP peer is the real client IP. It is not in `trusted-proxies`, so `ClientIpHeaderFilter` ignores any `X-Real-IP` the client sends and writes the peer address into `X-PSA-Client-IP`. A forged `X-Real-IP` is not trusted.
- **Through the 443 nginx** (`proxy_pass https://127.0.0.1:48088`): the peer is `127.0.0.1`, which is in `trusted-proxies`, so the gateway uses `X-Real-IP`. This is safe only because nginx overwrites `X-Real-IP` with `$remote_addr` in every gateway-bound location (SEC-112; all 6 in prod do). A new location without that line would let a client choose its IP; the runbook checklist now covers this.
- The `legacy-allowed-ips` guard (no loopback, no gateway address) still matters: every request through the 443 nginx arrives from `127.0.0.1`.
- Not affected: the system-side `gateway-addresses` (gateway → system is a separate hop) and SEC-110 normalisation.
- **SEC-110:** `InetAddress` normalisation in the verifier and the gateway filter.

Tests: see the SEC-001 test list, (r4) items.

Rollback: SEC-108 and SEC-110 are pure code and roll back with the jars. Remove `psa.gateway.trusted-proxies` to go back to TCP-peer behaviour.

---

## SEC-002 (Critical): `/system/auth/login/KM` mints a KM token for any `memberId`

### Hunks
- Shareable, `AuthController.singleSignOnKM`: drops `@PermitAll` and the `@RequestParam Map`. The memberId is now always `getLoginUserId()`. The redirect now goes to `https://km.forcitis.com:40380/jump.html?...&returnUrl=https://km.forcitis.com:40380/index.html`.
- Local patch, `CommonConstants.APIUrl.KM_TOKEN`: `http://` → `https://`, same host and port. The URL is not a secret. It sits in the local patch only because that file's other hunks remove credential literals; this keeps the two patches on disjoint files.

### Behaviour impact
- The server-to-server token call now carries the integration key over TLS instead of plaintext.
- **Assumption:** KM serves HTTPS on port 40380. If it does not, both URLs must be changed to KM's HTTPS origin before release. Otherwise the KM jump breaks (`KM获取token异常`).
- A browser **navigation** or form POST to this endpoint now gets 401, because yudao reads the token only from the `Authorization` header.

### Pre-release checks
1. `curl -sSI https://km.forcitis.com:40380/index.html` and `curl -sS -X POST https://km.forcitis.com:40380/api/services/Org/UserLoginIntegrationByUserLoginName` from the system-server host. Both must complete a TLS handshake with a trusted cert (the JDK 8 truststore).
2. **Browser-navigated `/login/KM` callers:** in 30 days of gateway logs, list requests to `/admin-api/system/auth/login/KM` and split them by whether an `Authorization` header is present. If you can't see headers, use `Referer`, `Sec-Fetch-Mode: navigate` or `Accept: text/html`. Any navigate or form request is a caller that breaks (401). If such callers exist, hold SEC-002. Instead, change the endpoint to return the jump URL as JSON and set `window.location` in the front-end. That is a contract change.
3. Neither PSA front-end in this repo calls this endpoint.

### Ops / tests
- Rotate the KM integration key anyway (see SEC-006).
- Tests:
  - anonymous → 401;
  - logged-in user A sends `memberId=B` → the token is minted for A;
  - the 302 `Location` starts with `https://`.

---

## SEC-003 (Critical): cost opening and cost correction endpoints are `@PermitAll`

### Hunks
| File | Change |
|---|---|
| `CostOpeningController.java` | 39 × `@PermitAll // TODO cost:opening:X` → `@PreAuthorize("@ss.hasPermission('cost:opening:X')")`, using the permission named in each TODO. The `TODO 同上` entries on the health-check endpoints become `query`. In round 3, `/official/reset` (`officialReset`) moved to its own `cost:opening:official-reset`. The trial-run `/reset` keeps `cost:opening:reset`. |
| `CostCorrectionController.java` | 15 × `@PermitAll` → `cost:correction:query` for page, scan status, export, error-file, options, project/term options, history, batch and batch page. `cost:correction:edit` for starting the scan, posting-check, row edit and batch revoke. `cost:correction:import` for import. |
| `db/migration/V20261014120000__add_cost_opening_correction_permissions.sql` (new) | See below. |

### Migration behaviour
1. **Button rows.** Inserts the 7 `type=3` rows (round 3 added `期初正式重置` / `cost:opening:official-reset`, sort 104) into `system_menu`. This uses the precedent pattern: TABLOCKX, identity or manual id, and a skip when an active row already exists. It **THROWs 51420 only** on a duplicate active permission, a soft-deleted collision, or an existing non-button row with the same permission.
2. **Anchors.** Opening buttons go under the single common parent of the `costopenning/*` pages (excluding costCorrection). Correction buttons go under the `costopenning/costCorrection*` page. If an anchor is missing or ambiguous, the script `PRINT`s `WARN SEC-003: …` and uses `parent_id = 0`. Startup is no longer blocked.
3. **Grants.** These are inserted into `system_role_menu` **only for non-deleted roles that already hold the matching page** (`type=2`, matched by `component`). The shared parent directory is never used:

   | Page held (component LIKE) | Granted |
   |---|---|
   | `%costopenning/%` except `%costCorrection%` (execute, executeOfficial, result, revenueEvents, index/health check) | `cost:opening:query` |
   | `%costopenning/execute%` (execute + executeOfficial) | `cost:opening:execute`, `cost:opening:reset` |
   | `%costopenning/revenueEvents%` | `cost:opening:execute` |
   | `%costopenning/executeOfficial%` | `cost:opening:official-reset` |
   | `%costopenning/costCorrection%` | `cost:correction:query`, `:edit`, `:import` |

   - Grants are keyed by `(role_id, menu_id, tenant_id)`.
   - A `NOT EXISTS` guard makes the grant step idempotent.
   - The script handles both identity and manual `id` columns.
   - Grants also go to a button row that an admin created by hand earlier.
4. **Partial schemas.** If `system_menu`, `system_role_menu` or `system_role` is missing (an isolated fst DB), the script returns without doing anything.
5. **Compatibility.** No 2017+ syntax is used (no `STRING_AGG`, `CREATE OR ALTER` or `TRIM`).
6. **Smoke test (round 3).** The script was re-run on a throwaway SQL Server container at **compatibility level 130** (the engine was 2022, not a real 2016):
   - On a seeded DB:

     | Role | Holds | Got |
     |---|---|---|
     | 1 | execute page | query + execute + reset |
     | 2 | executeOfficial page | query + execute + reset + official-reset |
     | 3 | result page | query |
     | 4 | revenueEvents page | query + execute |
     | 5 | costCorrection page | the 3 correction buttons |
     | 6 | parent directory only | nothing |
     | 8 | execute + revenueEvents pages | query + execute + reset (no duplicate rows) |
     | 7 (deleted role) | executeOfficial page | nothing |

     That is 7 buttons and 16 grants.
   - A second run inserted nothing: still 7 buttons and 16 grants.
   - The empty-schema and soft-deleted-collision paths did not change in round 3. Round 2 results: 2 WARN lines with every button under `parent_id=0` and no grants; THROW 51420.

### Behaviour impact
- Roles that hold the pages today keep working after deploy without manual grants. A role that reached these endpoints without holding the page (anonymous or other users) now gets 401/403.
- Grants follow the reviewers' mapping:
  - `revenueEvents` holders get `execute`, because that page's actions are `execute`-gated.
  - Holders of either execute page get the trial `reset`.
  - Only `executeOfficial` holders get `official-reset`.
  - `result`-only roles stay read-only.
- The PC front-end has no `v-hasPermi` on these codes (`src/api/accounting/costOpening.js` only mentions them in a comment), so it needs no change.

### Ops steps
1. Pre-check on dev and prod, before merge (the script is immutable once merged):
   ```sql
   -- pages and their parents (expect 1 distinct parent for the non-correction pages, exactly 1 costCorrection page)
   SELECT id,name,parent_id,component FROM system_menu
   WHERE deleted=0 AND type=2 AND component LIKE N'%costopenning/%' ORDER BY component;
   -- what the migration will grant (role x page)
   SELECT r.id role_id,r.name role_name,m.component,rm.tenant_id FROM system_role_menu rm
   JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.type=2 AND m.component LIKE N'%costopenning/%'
   JOIN system_role r ON r.id=rm.role_id AND r.deleted=0 WHERE rm.deleted=0 ORDER BY r.id,m.component;
   -- pre-existing rows that would THROW
   SELECT id,permission,type,deleted FROM system_menu WHERE permission LIKE N'cost:opening:%' OR permission LIKE N'cost:correction:%';
   ```
   If the components are not `costopenning/...`, adjust the LIKE patterns before merging.
2. After deploy, refresh the permission cache: restart system-server, or wait for its role/menu cache refresh. Then spot-check one user per role.
3. Rollback (manual; Flyway Community has no undo):
   ```sql
   DELETE rm FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
   WHERE m.creator=N'flyway' AND m.type=3 AND (m.permission LIKE N'cost:opening:%' OR m.permission LIKE N'cost:correction:%');
   DELETE FROM system_role_menu WHERE creator=N'flyway' AND menu_id IN
     (SELECT id FROM system_menu WHERE permission LIKE N'cost:opening:%' OR permission LIKE N'cost:correction:%');
   DELETE FROM system_menu WHERE creator=N'flyway' AND type=3
     AND (permission LIKE N'cost:opening:%' OR permission LIKE N'cost:correction:%');
   ```
   - Only rows created by this migration (`creator='flyway'`) are removed. The first statement drops all grants for those buttons; the second drops only the migration's own grants on buttons an admin created by hand.
   - Reverting the controller hunks restores `@PermitAll`. **Do not** delete the `flyway_schema_history` row; ship a new forward migration instead if needed.
4. Flyway: CI runs it on the 2017 Testcontainers image, and it was smoke-tested at compat level 130. It still needs confirmation on the real dev SQL Server 2016 instance.

### Tests
- CI `pr-gate` Flyway run, plus a re-run on a DB that already has the rows (it should insert nothing).
- dev:
  - a user whose role holds the costCorrection page → 200 on `/fst/cost-correction/page` and `importExcel`;
  - a user without it → 403;
  - a role holding only `result` → 200 on query endpoints, 403 on execute/reset;
  - a role holding only `revenueEvents` → 200 on its execute actions;
  - a role holding only `execute` → 200 on `/reset`, 403 on `/official/reset`.

---

## SEC-004 (High): manhour data-fix endpoints are `@PermitAll`

### Hunks
21 × `@PermitAll` → `@PreAuthorize("@ss.hasPermission('fst:manhour:fix')")` across `FixController` (9), `FixDirectController` (5), `FixOriginalController` (4), `FixRequestController` (1) and `ManHourWCheckController` (2). The imports are adjusted.

### Impact / ops / tests
- 0 calls in 180 days, so no business impact. No menu row is created on purpose, so only the super admin can run them. If ops ever needs them, create a button row and grant it to an ops role.
- Test: anonymous `GET /admin-api/fst/fix/fix-type-a` → 401. A normal user → 403.

---

## SEC-005 (High): anonymous `bpm/oa/customized/create` with a client-chosen `creator`

### Who calls it
- **No live caller (round 3).** Prod access logs show the last calls at 2026-05-07 (`/admin-api/...create`, 697 calls in total) and 2026-04-30 (`/app-api/...create`, 2,267 calls in total), with none since. The timesheet now goes through the fst flow, which starts the process over RPC (`BpmProcessTaskApiImpl`).
- **PC front-end (legacy code):** `src/api/public.js#createWorkflow` is still imported by `views/project/timesheet/detail/index.vue` and `create.vue`, and their routes still exist in `router/index.js`. It sends `{dataStamp, paramsId, processKey: 'TIMESHEET'}`. With the round-3 gate, these legacy views get 403 on submit. **Pre-release check:** confirm no menu still links to `project/timesheet/detail/*`, or remove those views.
- **psa-mini:** no caller.
- **Internal fst flows** (travel, quotation, budget, revenue, timesheet outbox and others) call `BpmProcessTaskApiImpl.createOA` over **RPC**, not this controller. They legitimately set `creator` and `processInstanceVariables`, including the timesheet row-routing skeleton.

### Hunks (fix at the HTTP edge; `CustomizedBpmOAServiceImpl` unchanged)
- **Round 3:** both HTTP `createOA` methods, in `CustomizedBpmController` (`/admin-api`) and `CustomizedBpmAPPController` (`/app-api`), carry `@PreAuthorize("@ss.hasPermission('bpm:oa:customized:create')")`.
  - No menu row is created and no role is granted this permission, so the endpoints are **effectively disabled**.
  - Only a super admin can still pass the `/admin-api` endpoint.
  - This closes the `paramsId` IDOR, where a user could bind a flow to another user's timesheet id.
  - **Follow-up:** delete both endpoints (and `createWorkflow` in the PC front-end) in a separate PR.
- New `controller/admin/oa/CustomizedBpmCreateReqSanitizer.sanitize(reqVO)`, called by `CustomizedBpmController` and `CustomizedBpmAPPController` before the service. Both controllers also drop `@PermitAll`. The sanitizer:
  1. Calls `reqVO.setCreator(null)` and `reqVO.setProcessInstanceVariables(null)`.
  2. Rejects any `processKey` other than `TIMESHEET` with 400 `不支持发起该流程`.
  3. Rebuilds the variables from `dataStamp` using the same aggregation as `submitApprovalInstance` (L105–131): an object is used per key; an array becomes key → `LinkedHashSet` of row values. **Only these keys are kept:** `id, taskId, declareHours, approvalHours, progress, worksDate, projectCode, projectName, type, workPackageType, workPackageId, remarks, managerId, termNumber, stageId, oppStageId, costOrgId, projectOrgId, profitCenterId`. These are the `ManHourUpdateVO` fields the fst timesheet path also turns into variables.
  4. Rejects the request with 400 if no allowlisted key remains. Otherwise the service would fall back to deriving variables from the full `dataStamp`.
  5. Leaves `dataStamp` itself untouched, so the approver-facing snapshot and the resubmit comparison do not change.
- Keys the client can no longer inject include `manHourType`, `areaLeader`, `rowUnits`, `rejected`, and any assignee or gateway variable not in the list. Server-built keys (`creUserId`, `roles`, `leaderUserId`, `depts`, `postName`, `billing`, `rejected`) are still overwritten by `buildProcessInstanceVariables`.

### Impact / tests
- With both endpoints disabled, the TIMESHEET BPMN allowlist check from round 2 only matters if an endpoint is re-enabled.
- Both `/admin-api/...create` and `/app-api/...create` are disabled by the ungranted permission. The sanitizer stays as defence in depth for a super admin or any future re-enable.
- Residual risks:
  - `managerId` is client-supplied in PC rows (a user can pick a row's approver). The legacy PC path is now disabled, so this only matters if it is re-enabled.
  - **`paramsId` ownership** is not checked. While the endpoints are disabled, only a super admin can reach this. If an endpoint is ever re-enabled, it must first check that `psa_time_sheet` (by `paramsId`) belongs to the login user.
- Tests:
  - anonymous → 401;
  - a normal logged-in user (admin or member token) → 403 on both endpoints;
  - as super admin only: user A posts `creator=B` → the starter is A;
  - posting `processInstanceVariables` → ignored;
  - `processKey=TRAVEL` → 400;
  - a `dataStamp` row with `areaLeader`/`rejected` → those variables are absent on the instance;
  - `dataStamp=[]` → 400;
  - the fst timesheet submit on dev → the flow routes as before;
  - an RPC-path flow (travel approval) on dev → the applicant is still the one passed in.

---

## SEC-006 (Critical): hard-coded WeCom corpsecrets, KM key, default password, WeCom callback AES key and token

### Hunks
- **Local patch only:** `CommonConstants.java` drops the literals for `DEFAULT_PASSWORD`, `WeComSecret.{PSA,PSA_prod,BI,H5,H5_prod}` and `SecretKey.KM`, including a commented-out test KM key. `FSTConstant.java` drops the literals for `WeComCallback.{ENCODING_AES_KEY,TOKEN}`. The fields stay `public static String` with the same names, so all existing static references compile unchanged.
- **Shareable:** two `LegacyCredentialBinder` components, one in system-biz and one in fst-biz (bean name `fstLegacyCredentialBinder`). At `@PostConstruct` they copy Nacos values into those static fields. If a key is missing they log a WARN and **keep the current value**. Before the local patch is applied, that current value is the old literal, so merging the shareable patch first changes nothing at runtime. After the local patch it is `null`, and the dependent feature fails on use.
- **Local patch only (round 3):** `docs/bugfix/PSA 项目现状痛点清单.md` (section B6, code sample ~L833–835) quoted the live WeCom callback `ENCODING_AES_KEY` and `TOKEN` and a WeCom app secret. The hunk replaces the three values with `<已移除：…改由 Nacos … 下发>` placeholders. The shareable patch carries only a comment line pointing to this hunk.
- **Shareable (non-credential):** `WeComCallbackController.savaTaskAction` now resolves the acting user from the signed and decrypted `FromUserName`. It uses `AdminUserApi.getUserAllByCache()` and requires exactly one user with that weComId who is enabled and not deleted. If the result is ambiguous, missing, or differs from EventKey segment 4, the callback is ignored and `success(false)` is returned. EventKey is no longer an identity source.

### Nacos keys to add (system-server and fst-server, per environment)
```
psa.wecom.secret.psa / psa-prod / bi / h5 / h5-prod
psa.km.integration-key
psa.user.default-password
psa.wecom.callback.encoding-aes-key
psa.wecom.callback.token
```
(fst only needs `psa.wecom.secret.psa[-prod]` and `psa.wecom.callback.*`; putting them in a shared dataId is fine.)

### Ops steps — **needs manual remediation** (not doable in a patch; the values are in git history, so treat them all as compromised)
1. WeCom admin console: **reset the Secret** of each self-built app (PSA dev/prod, BI, H5 dev/prod) and put the new values in Nacos.
2. WeCom "接收消息" callback: generate a new Token and EncodingAESKey. Change them in Nacos and in the WeCom console **in the same window**, because callbacks fail until both sides match. Then re-verify the URL with GET `check-url`.
3. KM: ask the KM vendor or admin to issue a new integration key and revoke the old one.
4. Default password: set a new value in Nacos. Find users whose password hash still matches the old default and force a reset. The old default is public in history.
5. Purge history, **after** steps 1–4 have rotated every value. Use `git filter-repo --replace-text` (or BFG) with the literals removed by the local patch, plus `psa.pfx` (see SEC-012).
   - The purge must cover these files: `CommonConstants.java`, `FSTConstant.java`, infra `application.yaml`, gateway `bootstrap.yaml`, `docs/bugfix/PSA 项目现状痛点清单.md` (round 3), and the 6 `application-{local,dev,prod}.yaml` files listed in step 6.
   - `--replace-text` works on values, not files, so one expressions file built from the local patch's removed literals covers every file. Then force-push all branches and tags, have every clone re-clone, and ask GitHub support to purge cached views and PR refs. Purging does not replace rotation: forks, CI caches and existing clones keep the old values.

6. **Not in either patch (found in round 3):** `justauth.type.WECHAT_ENTERPRISE.client-secret` and `justauth.type.DINGTALK.client-secret` are hard-coded in `application-{local,dev,prod}.yaml` of both system-biz and fst-biz, which is 6 files with 2 secrets each.
   - The WeCom one is the same app secret the doc quoted at L833.
   - Rotate both secrets in the WeCom and DingTalk consoles.
   - Move them to Nacos (for example `${PSA_JUSTAUTH_WECOM_SECRET}`) in a follow-up local-only patch.
   - Add them to the purge list.

### Tests
- Startup log shows no `凭据配置项(...) 未下发` WARN in dev or prod.
- WeCom login (PC H5 and mini), the KM jump, creating a user with the default password, and the task-card accept/reject callback all work on dev.
- Callback: a card sent to user A and clicked by A → accepted. A forged EventKey with another user id → ignored and logged.

---

## SEC-007 (High): Druid monitor console enabled in prod with no allowlist or login

### Hunks
`stat-view-servlet.enabled: false` in `application-prod.yaml` of **infra, system, bpm and fst**. The finding named infra; the other three have the same config, so they are fixed in the same way. `web-stat-filter` and the `stat` filter (slow-SQL logging) are untouched.

### Impact / ops / tests
- `/druid/*` returns 404 in prod. No business caller uses it.
- If Nacos prod config sets `spring.datasource.druid.stat-view-servlet.enabled`, it overrides this file. Check it and set it to false there too.
- Test: `curl -I https://<prod-gw>/druid/index.html` (and the direct service ports) → 404 or 401.

---

## SEC-008 (High): stored XSS through `v-html` and `innerHTML`

### Why not plain text
`taskProgressInfo` is built **on purpose** as HTML in `ResourceServiceImpl` (~L1426–1432): a bold task name, a red progress value when hours are under 80, and a `<br/>`. Text rendering would show raw tags. The value is also posted back and **persisted** (`worksDO.setWorkProgressInfo(...)` ~L1705), so a stored client-controlled payload is possible as well. `DOMPurify` is not a dependency, so none was added.

### Hunks
- `src/utils/sanitizeHtml.js` (new): parses with `DOMParser`, which builds an inert document that runs no scripts and loads nothing. It rebuilds the tree keeping only `span`, `br`, `b` and `strong`. The only styles kept are `font-weight`, `font-family`, `font-size` and `color`, copied through the CSSOM. No attributes are kept. `script`, `style`, `iframe` and similar elements are dropped with their content. Other tags are unwrapped and their text kept.
- `weeklyMeeting/detail/index.vue` and `create/index.vue`: `v-html="sanitizeRichText(item.taskProgressInfo)"`, with the function exposed in `methods`.
- `visualized/index.vue`: the tooltip lines are built with `textContent` instead of an `innerHTML` template, which removes the `row.taskName` injection.

### Impact / tests
- The rendering looks the same (bold name, red progress). Event handlers, links, images and iframes in stored values no longer render.
- Tests: on dev, create a task named `<img src=x onerror=alert(1)>` and open the weekly meeting create, detail and visualized views. You should see literal text and no alert. Also send a saved `taskProgressInfo` containing `<a href=javascript:…>` → it renders as plain text.
- Follow-up (not in the patch): HTML-escape `taskDO.getName()` in `ResourceServiceImpl` for defence in depth, and stop accepting `taskProgressInfo` from the client on save.
- Lint: `eslint` in this checkout fails on a config issue (`vue/name-property-casing` rule not found) for both the original and the patched files. Rely on the CI `pr-gate` frontend lint and build.

---

## SEC-012 (High): mybatis encryptor password and gateway keystore password/PFX in the repo

### Hunks (local patch only)
- infra `application.yaml`: `mybatis-plus.encryptor.password: ${PSA_MYBATIS_ENCRYPTOR_PASSWORD:}`. Nacos `mybatis-plus.encryptor.password` takes precedence. If both are missing, `EncryptTypeHandler` fails on first use (`Assert.notEmpty`).
- gateway `bootstrap.yaml`: `key-store: ${PSA_GATEWAY_SSL_KEY_STORE:classpath:ssl/psa.pfx}` and `key-store-password: ${PSA_GATEWAY_SSL_KEY_STORE_PASSWORD}`, with no default, so the gateway **will not start** unless the env var (or a Nacos key of the same name) is set.

### Ops steps — **needs manual remediation** (only the YAML externalisation is in the patch)
1. Before deploying: set `PSA_GATEWAY_SSL_KEY_STORE_PASSWORD` in the gateway's runtime environment (Woodpecker or the systemd unit), and set `mybatis-plus.encryptor.password` in Nacos for infra-server. **Keep the current encryptor value.** Rotating it requires decrypting and re-encrypting `infra_data_source_config.password` first; otherwise those rows become unreadable.
2. **Needs manual remediation (not in the patch):** `psa-backend/yudao-gateway/src/main/resources/ssl/psa.pfx` holds the TLS private key, and a unified diff cannot delete a binary file portably. Do these in order:
   1. Rotate the secrets: a new keystore password, and a new mybatis encryptor password only after the data-source rows have been re-encrypted.
   2. Purge git history of the literals and of `psa.pfx` (SEC-006 step 5).
   3. `git rm psa-backend/yudao-gateway/src/main/resources/ssl/psa.pfx`.
   4. Re-issue the certificate with a new key pair and revoke the old one. Store the new PFX outside the repo (for example `/etc/psa/ssl/psa.pfx`) and set `PSA_GATEWAY_SSL_KEY_STORE=file:/etc/psa/ssl/psa.pfx` everywhere.
   5. Drop the `classpath:ssl/psa.pfx` default from `bootstrap.yaml` (`key-store: ${PSA_GATEWAY_SSL_KEY_STORE}`), so a missing env var fails fast instead of falling back to the leaked key.
3. Test: the gateway starts with the env set and serves the new certificate (`openssl s_client -connect <host>:48088`). Without the env var it fails fast. `infra` data-source config pages still decrypt.

---

## SEC-014 (Medium): `/fst/a8/test` anonymously triggers a U8C push

The hunk replaces `@PermitAll` with `@PreAuthorize("@ss.hasPermission('fst:a8:test')")`. No menu row is created, so only the super admin can call it.

Impact: none expected for business users. It is a test hook. Test: anonymous → 401.

---

## SEC-016 (High): pdf.js 2.16 font `eval` path (CVE-2024-4367)

The hunk adds `isEvalSupported: false` to `pdfjs.getDocument({ data, ... })` in `src/utils/pdfToImages.js`.

Impact: font rendering switches from compiled glyph paths to interpreted ones. Output is the same and only slightly slower on large PDFs. Follow-up: upgrade `pdfjs-dist` to 4.2.67 or later; webpack 4 needs the legacy build, so check that first.

Test: print an Alipay e-invoice PDF and compare the images. Load the public CVE PoC PDF and confirm no script runs.

---

## Open decisions
1. **SEC-001 rollout:** the patch is unsafe to deploy until the Nacos `psa.sso.trusted-callers` config exists, because the default fails closed.
   - `a8-login` and `YD-get` have **no unsigned transition**. The A8 and YD owners must ship signing, or move to `/seeyon-sso`, before this deploys.
   - `customization-login*` may use the gateway-bound legacy path in the meantime.
   - Follow the SEC-107 deploy order. Remove nginx `/other-api` (r4 supersedes the r3 `/other-api/admin-api/system/auth/` 403 rule). Set `psa.gateway.trusted-proxies` on the gateway.
   - Super admins are blocked only on the 4 external endpoints; WeCom OAuth is unchanged (round 3).
2. **SEC-002:** confirm that KM serves HTTPS on `:40380` and that there are no browser-navigated `/login/KM` callers (see the pre-release checks).
3. **SEC-003:** run the pre-check queries on dev and prod before merge. The migration grants by page holding:
   - `execute` → execute* and revenueEvents;
   - `reset` → execute*;
   - `official-reset` → executeOfficial.

   Confirm that this matches the business intent.
4. **SEC-005:** the HTTP `createOA` endpoints are disabled through an ungranted permission. Confirm that no menu still reaches the legacy PC `project/timesheet/detail/*` views, then open the follow-up to delete the endpoints.
5. **SEC-006/012 id mapping:** source-code constants are labelled SEC-006 and config files (encryptor, gateway keystore) SEC-012. Adjust if the findings file maps them differently.
6. **SEC-006/012 manual remediation:** secret rotation, history purge (now including `docs/bugfix/PSA 项目现状痛点清单.md`), `git rm` of `psa.pfx`, certificate re-issue, and dropping the `classpath:ssl/psa.pfx` default. None of these is in a patch.
7. **New, not patched:** the justauth WeCom and DingTalk `client-secret` values in 6 `application-*.yaml` files (SEC-006 step 6).
8. **SEC-107 (release blocker):**
   - Confirm that no one depends on `/other-api` other than the Druid iframe. Prod has 2 allowlisted IPs and access_log is off, so ask their owners.
   - ~~Decide whether to also guard `/rpc-api/**` of infra, bpm and fst.~~ **Decided and done in r5 (SEC-111):** framework filter in all four servlet services.
   - Update the travel contract doc that forbids a default Feign token header.
   - Because access_log was off, rotate `system.rpc.internal-token` (use the three-phase procedure in "Internal token rotation"; r5 makes it possible without 401s) and revoke the active OAuth2 tokens of super-admin accounts as a precaution.
9. **SEC-113 hot reload (optional):** the token is still read only at startup. Reading it from the `Environment` on each request would let a Nacos change take effect without restarts, but the three phases would still be needed. Not done; decide whether it is worth it.
10. **48088 exposure:** the gateway's own TLS port is open to `0.0.0.0/0`, so every gateway route is reachable without nginx. With SEC-108 that does not break client-IP trust, but narrowing the security group (SEC-001 ops step 1) remains open.
11. **SEC-114 follow-up (optional):** add a runtime test (embedded Tomcat or MockMvc with the real security chain) that shows an unsigned `/rpc-api` request never reaches `TokenAuthenticationFilter`. The r6 tests check the registration order, not a live chain.

## Verification (round 6)
- Fresh `git archive HEAD` copy (`19d60d234`): `git apply --check` → ok for the shareable patch (**67** files: the 64 from r5, plus `WebFilterOrderEnum.java`, `SocialUserApi.java` and the new `SocialUserApiMappingTest.java`) and for the local patch (5 files, unchanged).
- The patch was regenerated with `git diff HEAD` per file, in the r5 file order, with the 3 new files appended. Compared with r5 only three blocks changed: `YudaoInternalRpcTokenAutoConfiguration` (order + Javadoc), `InternalRpcTokenConfigurationTest` (2 new tests) and the 3 appended files. All other blocks are byte-identical.
- Backend compile on the copy with both patches applied: `JAVA_HOME=corretto8 mvn -q -o -f psa-backend/pom.xml -pl yudao-gateway,yudao-module-system/yudao-module-system-biz,yudao-module-bpm/yudao-module-bpm-biz,yudao-module-fst/yudao-module-fst-biz,yudao-module-infra/yudao-module-infra-biz -am compile -DskipTests` → exit 0.
- Unit tests (offline, JDK 8): the r5 set plus `SocialUserApiMappingTest` (`-Dtest='InternalRpcTokenFilterTest,InternalRpcTokenConfigurationTest,SocialUserApiMappingTest,OrgSynchronizationSnapshotApiFeignConfigurationTest,InternalTokenRelayFilterTest,AccessLogFilterTest'`) → 19 run, 0 failed (gateway 4, system 15).
- Leak scan (r6): quoted or assigned literals of 8+ characters on the local patch's `-` lines that do not survive into its `+` lines (10 with this round's extraction rule; r5 used a looser rule and counted 13) → 0 hits in the shareable patch and 0 in this file. Generic patterns (private-key header, `AKIA…`, `LTAI…`, `secret: <16+ chars>`) → 0 hits in each. The r6 additions contain no token or secret value.
- Not verified (r6): the filter order in a running Tomcat (the dev checks in the SEC-114 and SEC-115 sections).

## Verification (round 5)
- Fresh `git archive HEAD` copy (`19d60d234`): `git apply --check` → ok for the shareable patch (**64** files: the 59 from r4, plus the framework `InternalRpcTokenFilter`, `YudaoInternalRpcTokenAutoConfiguration`, the security starter `AutoConfiguration.imports`, gateway `InternalTokenRelayFilterTest` and `psa-h5.conf`; the two system `InternalRpcToken*` main classes are now deletions) and for the local patch (5 files, unchanged). Both were then applied.
- The patch was regenerated with `git diff` per file, in the r4 file order, with new files appended. Files not touched in r5 are byte-identical to r4.
- Backend compile on the copy with both patches applied: `JAVA_HOME=corretto8 mvn -q -o -f psa-backend/pom.xml -pl yudao-gateway,yudao-module-system/yudao-module-system-biz,yudao-module-bpm/yudao-module-bpm-biz,yudao-module-fst/yudao-module-fst-biz,yudao-module-infra/yudao-module-infra-biz -am compile -DskipTests` → exit 0.
- Unit tests (offline, JDK 8): `mvn -o … -pl yudao-gateway,yudao-module-system/yudao-module-system-biz -am test -Dtest='InternalRpcTokenFilterTest,InternalRpcTokenConfigurationTest,OrgSynchronizationSnapshotApiFeignConfigurationTest,InternalTokenRelayFilterTest,AccessLogFilterTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false` → 16 run, 0 failed.
- Leak scan (r5): the 13 literals removed by the local patch (values that do not survive into its `+` lines) were searched for in the shareable patch and in this file → 0 hits each. A generic pattern scan (private-key headers, cloud access keys, `secret: <16+ chars>`) → 0 hits each. The r5 additions name the tokens only as Nacos keys or `<old>` / `<new>` placeholders.
- Not verified (r5): runtime on Tomcat/SCG for infra, bpm and fst (filter registration in each service, the RPC paths in dev checklist items 8–9), the rotation phases, nginx reload.

## Verification (round 4)
- Fresh `git archive HEAD` copy (`19d60d234`): `git apply --check` → ok for the shareable patch (**59** files) and for the local patch (5 files, unchanged). Both were then applied.
- The tree with the patches applied matches the scratch tree where r4 was edited. The only differences are the 5 local-patch files, as expected.
- Unchanged r1–r3 hunks are kept byte-for-byte. Only the `TrustedCallerVerifier` and `ClientIpHeaderFilter` blocks were regenerated, plus 27 new file blocks.
- Backend compile on the copy with both patches applied: `JAVA_HOME=corretto8 mvn -q -o -f psa-backend/pom.xml -pl yudao-gateway,yudao-module-system/yudao-module-system-biz,yudao-module-bpm/yudao-module-bpm-biz,yudao-module-fst/yudao-module-fst-biz,yudao-module-infra/yudao-module-infra-biz -am compile -DskipTests` → exit 0. `-am` also compiles yudao-common and the rpc, security and web starters.
- Unit tests (offline, JDK 8): `InternalRpcTokenFilterTest`, `InternalRpcTokenConfigurationTest` and `OrgSynchronizationSnapshotApiFeignConfigurationTest` → 8 run, 0 failed.
- Leak scan:
  - Every literal removed by the local patch was searched for in the shareable patch → 0 hits.
  - The same search in this file found 2 matches. Both are non-credential literals that the local patch also touches: the KM API path (SEC-002 pre-release checks) and `classpath:ssl/psa.pfx`. There are 0 credential hits.
  - The r4 additions contain no token value; the token is only named as a Nacos key.
- The scratch copies were deleted.
- Not verified (r4):
  - runtime on SCG and Tomcat (relay filter order, 404 on encoded `/rpc-api`, the Feign interceptor in each service);
  - nginx reload;
  - the SEC-107 dev checklist.

## Verification (round 3)
- `patch -p1 -d /Users/yeliang/Projects/FORCITIS/psa --dry-run < security_remediation.patch` → ok for all **32** files. Round 3 adds `ClientIpHeaderFilter.java` and `AdminAuthService.java`.
- `patch -p1 -d /Users/yeliang/Projects/FORCITIS/psa --dry-run < ~/.modernize/psa/security_remediation.local.patch` → ok for all **5** files. Round 3 adds the doc file; its `---`/`+++` headers end in a TAB because of the space in the name, and both `patch` and `git apply` accept that.
- `git apply --check` → ok for both patches.
- Backend compile: both patches were applied to a clean `git archive HEAD` scratch copy, then `JAVA_HOME=corretto8 mvn -q -o -f psa-backend/pom.xml -pl yudao-gateway,yudao-module-system/yudao-module-system-biz,yudao-module-bpm/yudao-module-bpm-biz,yudao-module-fst/yudao-module-fst-biz -am compile -DskipTests` exited 0. The scratch copy was deleted afterwards.
- SEC-003 migration: re-run on a throwaway SQL Server container (2022 engine, compatibility level 130). It is idempotent and produced the grants in the table above. It still needs CI Testcontainers and the real dev 2016 instance.
- Leak scan: every literal removed in the local patch was searched for in this file and the shareable patch, with 0 hits.
- Not verified:
  - runtime behaviour, including the gateway filter on a live SCG and the nginx rule;
  - unit tests (none were added; the cases are listed per finding);
  - KM HTTPS reachability;
  - HTTPS on `psa.forcitis.com/index`.
