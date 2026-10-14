# B-4 交接包：`one.forcitis.com` 的 DNS 解析与 TLS 证书（给域名管理员 / 运维）

收件人：`forcitis.com` 的域名管理员与运维（人选与证书方式见 `DECISIONS_A.md` A-1）。时点：dev 部署前，先于 B-2。依据：INFRA_RUNBOOK D-3、E-8、E-9。

## 1. DNS（域名管理员）

执行前确认：
- `one.forcitis.com` 现在没有任何记录（下面 `dig` 返回空）；若已有记录，停止并回报。
- 目标 ECS 公网地址：`47.114.56.157`（forcitis-one-production）。

步骤（阿里云云解析 DNS 控制台，或域名实际托管处）：
1. 在 `forcitis.com` 下添加记录：类型 `A`，主机记录 `one`，记录值 `47.114.56.157`，TTL `600`。
2. 若采用 A-1 选项 A（阿里云免费 / 付费 DV 证书）：按证书控制台给出的 `_dnsauth` TXT 记录完成域名验证，签发后删除该 TXT。
3. 若 `forcitis.com` 有 CAA 记录：确认允许所选 CA 签发（例如 `0 issue "digicert.com"` 或 `"letsencrypt.org"`），否则签发会失败。

校验（任意机器）：
```bash
dig +short one.forcitis.com A            # 期望 47.114.56.157
dig +short forcitis.com CAA              # 有记录时须包含所选 CA
dig +short @223.5.5.5 one.forcitis.com A # 公共解析也已生效
```

回滚：删除该 A 记录（老系统域名不受影响）。

## 2. 证书（运维，按 A-1 所选方式）

### 选项 A：阿里云 SSL 证书服务（建议）
1. 证书控制台申请 DV 单域名证书 `one.forcitis.com`，DNS 验证（见上）。
2. 签发后下载「Nginx」格式（`.pem` 证书链、`.key` 私钥）。私钥只经云助手直接写到服务器，不经本地电脑中转、不发聊天。
3. 放置：`/etc/nginx/tls/one.forcitis.com.pem`、`/etc/nginx/tls/one.forcitis.com.key`，属主 `root:root`，权限 `600`（`runbooks/C-4-ecs-init.md` E-9）。
4. 到期前 30 天在控制台续签并替换（ARMS / 云监控对证书到期告警，见 C-7）。

### 选项 B：ACME（Let's Encrypt）自动续期
1. ECS 上安装 `certbot`（发行版仓库），以 webroot 方式签发：`certbot certonly --webroot -w /data/forcitis-one/current/web -d one.forcitis.com`。
2. nginx 指向 `/etc/letsencrypt/live/one.forcitis.com/{fullchain,privkey}.pem`。
3. 启用 `certbot-renew.timer`，续期后 `systemctl reload nginx`（deploy hook）。

## 3. 校验（证书部署、nginx 启用后）

```bash
curl -sI http://one.forcitis.com/ | head -3                 # 期望 301 → https
curl -sI https://one.forcitis.com/ | grep -i strict-transport # 期望 HSTS 头
echo | openssl s_client -connect one.forcitis.com:443 -servername one.forcitis.com 2>/dev/null \
  | openssl x509 -noout -subject -issuer -enddate            # 主题含 one.forcitis.com，到期日 > 30 天
echo | openssl s_client -connect one.forcitis.com:443 -tls1_1 2>&1 | grep -c "Cipher is (NONE)\|alert" # TLS 1.1 被拒
```

外部评测：SSL Labs 对 `one.forcitis.com` 评级 A 或以上（E-8 验收）。

## 4. 回传

不回传任何机密。回报 one-coordinator：所选方式（A / B）、证书到期日、DNS 生效时间。
