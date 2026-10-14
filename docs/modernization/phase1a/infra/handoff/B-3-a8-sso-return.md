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
