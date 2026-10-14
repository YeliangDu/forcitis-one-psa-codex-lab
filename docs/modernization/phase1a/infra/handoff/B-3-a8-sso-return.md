# B-3 交接包：A8 登记 FORCITIS one 的单点登录入口（给 A8 管理员）

收件人：A8（致远 OA）管理员。时点：dev 部署前。依据：INFRA_RUNBOOK E-16、D-1a-39、D-1a-40、INTERFACES.md A3。

## 要做什么

在 A8 门户中**新增**一个跳转到 FORCITIS one 的单点登录入口，**不修改、不替换**现有跳转老系统的入口。

| 项 | 值 |
|---|---|
| 入口名称 | FORCITIS one |
| 跳转地址（回跳地址） | `https://one.forcitis.com/login/sso` |
| 票据参数 | A8 在跳转时追加 `ticket=<一次性票据>`，即 `https://one.forcitis.com/login/sso?ticket=…`，与老系统入口使用的同一种第三方单点登录票据 |
| 打开方式 | 新窗口 |
| 可见范围 | dev 阶段只对项目组成员可见；生产切换时另行通知放开 |

FORCITIS one 收到票据后，由服务端向 A8 验票：`GET <A8 地址>/seeyon/thirdpartyController.do?ticket=…`，A8 返回登录名即通过。所以 A8 侧还需确认：

1. 验票接口 `/seeyon/thirdpartyController.do` 允许来自 ECS 内网地址 `172.16.89.86`（或其公网出口 `47.114.56.157`，以 A8 实际部署位置为准）的访问。
2. 回传给运维：A8 验票用的根地址（例如 `https://<A8 域名>`），运维写入 one.env 的 `SEEYON_OA_BASE_URL`。该地址不是机密。

## 需要回传

| 值 | 变量名 | 存放 |
|---|---|---|
| A8 验票根地址 | `SEEYON_OA_BASE_URL` | `/etc/forcitis-one/one.env` |
| A8 门户中能带出新入口的页面地址（PC 登录页「致远单点登录」按钮跳到这里） | `VITE_SSO_PORTAL_URL` | PC 前端构建时注入（CI 变量） |
| A8 门户中新入口的位置（截图或菜单路径） | — | 回报 one-coordinator，用于冒烟 |

运维另需在 one.env 设置 `FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED=true`（代码中默认关闭）与 `LEGACY_LOGIN_URL`（验票失败时「回到老系统登录」的链接）。

## 验收标准

- 项目组成员在 A8 门户点击新入口，进入 FORCITIS one 并已登录；同一个票据第二次使用被拒（防重放）。
- A8 不可用或票据无效时，FORCITIS one 显示失败页并给出「回到老系统登录」链接。
- 老系统入口照常可用（回滚演练 D-7 也会再验一次）。

## 回滚

在 A8 门户中停用或删除新入口；运维设 `FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED=false` 并重启。老系统入口不受影响。


## 实测（2026-10-15，one-ops，只读）

- A8 里与老系统有关的两处配置：
  - 「关联系统」**数字运营平台**（启用，作为空间展示）：POST `https://psa.forcitis.com:48088/admin-api/system/auth/a8-login`，只带参数 `memberId`，**不带票据、不验票**。这正是老系统的身份信任缺陷，新系统不得照搬。
  - 「第三方应用」`FORCITIS PSA`（**未启用**）：门户配置页面地址为老系统的 `seeyon-sso` 接口，即票据方式：A8 跳转时追加 `ticket`，对方回调 `thirdpartyController.do` 验票。
- **做法**：在 A8 后台「第三方应用」中，参照「`FORCITIS PSA`」那一条新建「FORCITIS one」：PC 页面地址 `https://one.forcitis.com/login/sso`，认证方式与「`FORCITIS PSA`」相同，启用；可见范围 dev 阶段只放平台负责人。不改「`FORCITIS PSA`」与「数字运营平台」。
- A8 验票根地址：`https://bpm.forcitis.com`（取自老系统配置中心的 `seeyon-sso.oa-base-url`）。从 ECS 访问 `https://bpm.forcitis.com/seeyon/thirdpartyController.do` 返回 200，假票据得到空响应，可达。
- 运维侧已写入 `one.env`：`SEEYON_OA_BASE_URL=https://bpm.forcitis.com`、`FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED=true`、`LEGACY_LOGIN_URL=https://psa.forcitis.com`（应用未部署前不生效）。
- 还缺：A8 门户中新入口的页面地址（`VITE_SSO_PORTAL_URL`，R-2 构建期变量）与入口位置截图，在 A8 中建好入口后取。

## A8 后台操作步骤（给用户或 A8 管理员）

1. 用 A8 系统管理员账号登录 `https://bpm.forcitis.com`，进入「第三方应用」（`FORCITIS PSA` 就在其中，未启用）。
2. 打开 `FORCITIS PSA` 看它的各项设置（只看不改），按同样的设置新建一个应用：
   - 应用名称：FORCITIS one
   - 认证方式：与 `FORCITIS PSA` 相同（票据方式：A8 跳转时追加 `ticket`，对方回调 `/seeyon/thirdpartyController.do` 验票）
   - PC 页面地址：`https://one.forcitis.com/login/sso`
   - H5 页面地址：留空（H5 走企业微信工作台入口）
   - 可见范围：dev 阶段只放平台负责人本人
   - 启用
3. 不修改、不停用 `FORCITIS PSA` 与关联系统「数字运营平台」。
4. 保存后在 A8 门户找到新入口，点一次：浏览器应跳到 `https://one.forcitis.com/login/sso?ticket=…`（应用未部署前会看到占位页或错误页，属正常，只看地址栏里是否带了 `ticket`）。

## 建好后回传（经 one-ops 或 one-coordinator）

| 回传项 | 用途 |
|---|---|
| 新入口在 A8 门户中的位置（菜单路径或截图） | 冒烟测试与回滚演练 D-7 |
| 点击新入口时 A8 先打开的门户页面地址（浏览器地址栏，去掉 `ticket` 等参数） | PC 登录页「致远单点登录」按钮的目标，R-2 构建期变量 `VITE_SSO_PORTAL_URL` |
| 第 4 步地址栏是否带 `ticket=`（是 / 否，不要回传票据值） | 确认 A8 按票据方式跳转 |

运维侧已就绪：`one.env` 中 `SEEYON_OA_BASE_URL=https://bpm.forcitis.com`、`FORCITIS_PLATFORM_IDENTITY_SSO_ENABLED=true`、`LEGACY_LOGIN_URL=https://psa.forcitis.com`；ECS 到验票接口可达。
