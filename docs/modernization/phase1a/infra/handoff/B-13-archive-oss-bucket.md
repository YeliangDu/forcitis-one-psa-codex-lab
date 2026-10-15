# B-13 交接包：归档 OSS bucket（从产生起满 10 年）

收件人：用户（bucket 与 RAM 由用户创建；运维会话不建 RAM 用户 / AccessKey）。依据：用户决定「一个 10 年的 bucket」（国内、与 ECS 同地域、归档存储），保留期「锁约 7 年（2557 天）」——数据先在线保存 3 年，再进本 bucket 归档 7 年，从产生算满 10 年（one-coordinator 2026-10-15 转达）。时点：不阻塞首次 dev 部署；第一个写入方上线前建好。编号 B-13（B-10 是发布与部署一次性设置；B-11、B-12 是 1a 加的 dev 库清理与流程管理员；本交接包原编号 B-11，2026-10-15 由 one-coordinator 改为 B-13）。

发布流水线不用 OSS（RELEASE.md R-3 已取消，ECS 自取 GitHub Release），本 bucket 只作归档。

## 1. 建 bucket（OSS 控制台，华东 1 杭州）

| 设置 | 取值 | 说明 |
|---|---|---|
| 名称 | `forcitis-one-archive`（被占用时加后缀，例如 `-hz`） | 名称全局唯一，建后不能改 |
| 地域 | 华东 1（杭州）cn-hangzhou | 与 ECS i-bp122xlpar8w2uylmal2、VPC vpc-bp1ovla4k0s9pl70fs5jl 同地域，走内网 endpoint `oss-cn-hangzhou-internal.aliyuncs.com` 不收外网流量费 |
| 存储类型 | 归档存储 | 读取前须先解冻（约 1 分钟，有取回费用）；最短计费 60 天、单对象最小按 64 KB 计；不开「归档直读」 |
| 冗余 | 本地冗余 | 用户决定「本地冗余 (Recommended)」（one-coordinator 2026-10-15 转达） |
| 读写权限 | 私有 | |
| 阻止公共访问 | 开启 | |
| 版本控制 | 不开启 | 防删改由合规保留策略负责 |
| 服务端加密 | OSS 完全托管（SSE-OSS，AES256） | 免费；KMS 托管另收 KMS 费用 |
| 实时日志查询、传输加速、跨区域复制、生命周期规则 | 都不开 | 生命周期对受保留策略约束的对象无意义；日志转存另定（依赖 C-7 付费项 P-2） |

## 2. 合规保留策略（WORM）

1. bucket →「数据安全」→「合规保留策略」→ 创建：保留天数 **2557**（7 年含闰日；保留期从对象写入本 bucket 时起算，加上写入前在线保存的 3 年，从产生算满 10 年）。策略进入「InProgress」状态，24 小时内可删除。
2. 在 InProgress 期间做一次写入演练（见第 4 节），确认写入方权限与路径正确。
3. 确认无误后**锁定**。锁定不可撤销：保留期内对象不能删除、不能覆盖；保留天数只能延长、不能缩短；bucket 内有对象时不能删除 bucket。演练对象锁定后同样保留 7 年（体积很小，费用可忽略）。
4. 24 小时内没有锁定，策略失效，需重建。

## 3. 最小权限

用 RAM 自定义策略，绑在 ECS 实例 RAM 角色 `forcitis-one-ecs-archive` 上（本 bucket 的策略单独建一份，不并入部署策略；同一实例角色另挂部署请求的拉取策略 `forcitis-one-deploy-pull`，RELEASE.md R-4、§8.2——该实例角色承载归档写入与部署请求拉取两份最小策略），不发 AccessKey。

写入方策略 `forcitis-one-archive-writer`（只能经 VPC 内、HTTPS 写，不能读、删、改配置）：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oss:PutObject", "oss:AbortMultipartUpload", "oss:ListParts"],
      "Resource": ["acs:oss:*:*:forcitis-one-archive/*"],
      "Condition": {
        "Bool": { "acs:SecureTransport": ["true"] },
        "StringEquals": { "acs:SourceVpc": ["vpc-bp1ovla4k0s9pl70fs5jl"] }
      }
    },
    {
      "Effect": "Allow",
      "Action": ["oss:ListObjects"],
      "Resource": ["acs:oss:*:*:forcitis-one-archive"],
      "Condition": {
        "Bool": { "acs:SecureTransport": ["true"] },
        "StringEquals": { "acs:SourceVpc": ["vpc-bp1ovla4k0s9pl70fs5jl"] }
      }
    },
    {
      "Effect": "Deny",
      "Action": ["oss:DeleteObject", "oss:PutObjectAcl", "oss:PutBucket*", "oss:DeleteBucket*", "oss:*Worm*"],
      "Resource": ["acs:oss:*:*:forcitis-one-archive", "acs:oss:*:*:forcitis-one-archive/*"]
    }
  ]
}
```

调阅策略 `forcitis-one-archive-reader`（审计调阅时临时授予某个人的 RAM 身份，用完收回；不授给实例角色）：`oss:GetObject`、`oss:RestoreObject`、`oss:ListObjects`、`oss:GetBucketInfo`，同样带 `acs:SecureTransport` 条件。

另加 bucket 策略：拒绝所有主体的非 HTTPS 访问（Effect Deny，条件 `acs:SecureTransport = false`）。合规保留策略的创建、锁定、延长只由主账号操作。

## 4. 演练（InProgress 期间，在 ECS 上）

实例角色绑好策略后，经云助手执行：上传一个 1 KB 文本到 `drill/<日期>.txt`（成功）；列出 `drill/`（成功）；读取该对象（被拒绝）；删除该对象（被拒绝）；从本机公网用同一身份上传（被拒绝，因为不在 VPC 内）。结果回报 one-ops，再由用户锁定。

## 5. 写入约定（第一个写入方上线前定）

- 路径：`<来源>/<YYYY>/<MM>/<文件名>`，来源用词表术语，例如审计记录的归档。
- 每个写入方在本文件追加一行：来源、写什么、频率、预计年增量、批准出处。
- 存什么目前未定：审计「至少 5 年」（intent 153）、老系统登录日志下线时导出归档（intent 122）是已知候选。写入方须在数据产生满 3 年时写入（在线 3 年 + 归档 7 年）；提前写入的，从产生算的保留期相应超过 10 年。

## 回滚

锁定前：删除合规保留策略和 bucket。锁定后：不能回滚，只能停止写入、收回写入方策略。


## 执行记录（2026-10-15，one-ops；用户「需要我做的，都让ops 会话自己做」，one-coordinator 批准）

| 项 | 结果 |
|---|---|
| bucket | `forcitis-one-archive`，oss-cn-hangzhou，归档存储，本地冗余，私有；阻止公共访问开启；SSE-OSS（AES256）；bucket 策略拒绝所有主体的非 HTTPS 访问 |
| WORM | `InitiateBucketWorm` 2557 天（worm id 40BCDD0175F247879AFDDA7DFBF94E2B，2026-10-14T22:46:12Z），**未锁定**。用户「现在不锁，第一个写入方上线前再锁 (Recommended)」：任其 24 小时后自然失效；第一个归档写入方上线前重新 `InitiateBucketWorm` 2557 天并锁定，锁定前报 one-coordinator |
| 写入方 | 自定义策略 `forcitis-one-archive-writer`（对象：`PutObject`、`AbortMultipartUpload`、`ListParts`；bucket：`ListObjects`、`GetBucketInfo`、`GetBucketWorm`、`GetBucketLocation`；均要求 HTTPS 且来源 VPC `vpc-bp1ovla4k0s9pl70fs5jl`；显式拒绝删除、改 ACL、改 bucket 配置与 WORM）挂在 ECS 实例角色 `forcitis-one-ecs-archive`（受信服务 `ecs.aliyuncs.com`），已绑定实例 i-bp122xlpar8w2uylmal2 |
| 演练 | 云助手 t-hz070239sy27bi8，ECS 上以实例角色执行：内网 HTTPS 写入 `drill/20261015.txt` → 200；列出 `drill/` → 200；读取 → 403；删除 → 403；经公网地址写入 → 403 |
| 发现 | ① 本 ECS 的元数据服务为加固模式，取实例角色凭据前须先取元数据令牌（应用所用 SDK 须支持）；② 1a 运行手册列的 `oss:GetObjectMetadata` 未授予（动作名待核对），应用若需要读对象元信息，按实际调用补授权。均已转 one-1a-next |
| 演练对象 | `drill/20261015.txt` 被策略拒绝删除而保留；锁定前用高权限清掉或保留，届时报 one-coordinator |
