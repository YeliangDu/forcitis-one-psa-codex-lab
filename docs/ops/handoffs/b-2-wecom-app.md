# B-2 交接包：企业微信新建「FORCITIS one」自建应用（给企业微信管理员）

收件人：企业微信管理后台的管理员。时点：dev 部署前（先于 C-4 的 E-15 联调）。依据：D-1a-138（新系统用新的自建应用：新 AgentId、新 Secret、独立回调）、SECURITY_INVARIANTS I-8 / SEC-119（老应用的值已在老仓历史中泄露，不得复用）、INFRA_RUNBOOK E-15。

前置：`one.forcitis.com` 已解析并有证书（B-4），nginx 已能提供静态文件（C-4 的 E-8）——「可信域名」校验要从该域名下载一个校验文件。

## 1. 新建应用

管理后台 →「应用管理」→「自建」→「创建应用」。

| 字段 | 填写 |
|---|---|
| 应用名称 | FORCITIS one |
| 应用 logo | 由用户提供（无则先用公司 logo） |
| 应用介绍 | 新一代业务平台：合并待办、审批 |
| 可见范围 | **dev 阶段只放项目组成员**（与 A-8 收件人白名单同一批人）；生产切换前改为全员，届时另行通知 |

**不要修改、停用或复用老应用**，老应用随老系统下线。

## 2. 应用内配置

| 位置 | 配置 | 值 |
|---|---|---|
| 网页授权及 JS-SDK →「可信域名」 | 设置可信域名 | `one.forcitis.com`。按页面提示下载 `WW_verify_*.txt`，交运维放到网站根目录（C-4 E-10 的 web 根），能通过 `https://one.forcitis.com/WW_verify_*.txt` 访问后再点「确定」。域名须已在公司主体下 ICP 备案 |
| 企业微信授权登录 →「Web 网页」 | 授权回调域 | `one.forcitis.com`（PC 扫码登录回跳 `https://one.forcitis.com/login/wecom`） |
| 企业可信 IP | 添加 | `47.114.56.157`（新 ECS 公网出口；不配置则调用发消息等接口会被拒绝） |
| 工作台应用主页 | 主页地址 | H5 入口地址，以 C-4 nginx 定稿路径为准（当前建议 `https://one.forcitis.com/h5/`，见 `CROSSCHECK.md` 第 4 条）。**新增入口，不替换老 H5**（切换手册 §3.11） |
| 接收消息 →「设置 API 接收」 | **1a 不设置** | 卡片按钮回调已推迟（D-1a-138，P1）。到实现时再配 URL、Token、EncodingAESKey，Token 与 AESKey 只进 KMS |

FORCITIS one 在 1a 只用到的接口：网页授权换身份（`auth/getuserinfo`）、发送应用消息与模板卡片、更新模板卡片。这些接口对自建应用默认可用，**不需要额外申请「通讯录读取」等权限**；只能给「可见范围」内的人发消息。

## 3. 填完后回传（只回传变量名对应的值给运维，不发聊天、不进工单）

| 值 | 变量名 | 存放 | 是否机密 |
|---|---|---|---|
| 企业 ID（我的企业 → 企业信息） | `WECOM_CORP_ID`（后端）、`VITE_WECOM_CORP_ID`（PC 构建）、`TARO_APP_WECOM_CORP_ID`（H5 构建） | 后端写 `/etc/forcitis-one/one.env`；前端两个在构建时注入（CI 变量） | 否 |
| AgentId | `WECOM_AGENT_ID`（后端）、`VITE_WECOM_AGENT_ID`（PC 构建） | 同上 | 否 |
| Secret（在应用详情页点「查看」，发到管理员企业微信客户端） | `WECOM_APP_SECRET` | 只写 `/etc/forcitis-one/one.env`，后续迁 KMS（`runbooks/C-8-kms.md`）；**轮换周期 180 天，泄露立即重置** | **是** |

Secret 交接：管理员在企业微信客户端查看后，当面或经密码管理器共享交运维，运维写入后确认，管理员删除客户端里的这条消息。

## 4. 验收标准

- `https://one.forcitis.com/WW_verify_*.txt` 可访问，「可信域名」显示已验证。
- 运维在 ECS 上用新 CorpID 与 Secret 调 `gettoken` 返回 `errcode=0`（`runbooks/C-4-ecs-init.md` E-15 校验命令）；从其他出口 IP 调用发消息接口被拒（可信 IP 生效）。
- dev 冒烟：可见范围内的成员 PC 扫码登录成功、企业微信工作台打开 H5 静默登录成功；范围外成员看不到应用。
- 老应用配置与老 H5 入口均未改动。

## 5. 回滚

停用新应用（不删除，保留 AgentId 以便审计）；运维在 one.env 中设 `WECOM_ENABLED=false` 并重启。老应用不受影响。


## 企业可信 IP（2026-10-15）

用户已在企业微信后台为 FORCITIS one 应用配置企业可信 IP `47.114.56.157`。one-ops 从 ECS（出口 IP 即该地址）调用 `message/send`，接收人填不存在的 userid：返回 `81013`（接收人无效）而非 `60020`（IP 不在可信范围），说明 IP 检查已通过；未发出任何消息。「其他出口发消息被拒」的反向验证需要在别的出口持有应用凭据，不做。
