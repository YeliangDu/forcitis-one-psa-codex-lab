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
3. 放置：`/etc/nginx/tls/one.forcitis.com.pem`、`/etc/nginx/tls/one.forcitis.com.key`，属主 `root:root`，权限 `600`（`runbooks/ecs-init.md` E-9）。
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

## 5. 余项（one-ops，2026-10-15）

### ICP 备案主体（只读）

- 公司官网 `https://www.forcitis.com/` 页脚：「弗思特建筑科技有限公司 版权所有」与备案号 `苏ICP备12028598号-4`（页面另有一段嵌入内容带其他单位的备案号，与本公司无关）。
- 间接证据：`one.forcitis.com` 解析到杭州 ECS，80 / 443 自 2026-10-14 起一直可访问；阿里云对境内节点上未在阿里云接入备案的域名会拦截 80 / 443，未被拦截说明 `forcitis.com` 已接入备案。子域名随主域名备案，无须另备。
- **未完成的直接核验**：工信部查询页（beian.miit.gov.cn）有人机验证，不由代理操作。请用户在该页或阿里云「ICP 备案」控制台按域名 `forcitis.com` 查一次，确认主体为本公司、网站备案号以 `-4` 结尾的那条对应 `forcitis.com`。确认后本项完成。

### TLS 配置（本机 OpenSSL 3.6.3 只读探测）

| 项 | 结果 |
|---|---|
| TLS 1.0 / 1.1 | 拒绝 |
| TLS 1.2 / 1.3 | 接受；1.3 协商 `TLS_AES_256_GCM_SHA384` |
| 证书 | ECDSA P-256，签发者 Let's Encrypt YE2，SAN 仅 `one.forcitis.com`，到期 2027-01-12，链校验通过 |
| HSTS | `max-age=31536000` |
| OCSP 装订 | 无（Let's Encrypt 已停止 OCSP 服务，不扣分） |
| TLS 1.2 套件 | 除 AEAD 外仍接受 CBC 套件（例如 `ECDHE-ECDSA-AES128-SHA`、`ECDHE-ECDSA-AES256-SHA384`）：nginx 未设 `ssl_ciphers`，用的是 OpenSSL 默认 |

判断：按 SSL Labs 现行评级规则大概率为 A；CBC 套件会被标为 WEAK，但不封顶。建议（随 C-4 的 nginx 模板一起改，执行前确认）：TLS 1.2 只留 ECDHE + AEAD，`ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305;`。**已处理（2026-10-15，one-ops，t-hz0701e7qeb9lvk）**：nginx 换用 main 上的模板并加上述 `ssl_ciphers`（模板同改）；`/etc/nginx/tls/one.forcitis.com.{pem,key}` 软链到 Let's Encrypt 现网证书（C-4 E-9 现状做法）；续期 webroot 改为 `/data/forcitis-one/static-pinned`；企业微信校验文件复制到 static-pinned 并经 nginx 原样返回；`certbot renew --dry-run --run-deploy-hooks` 全部成功并运行 nginx 重载；备份在 ECS `/root/one-ops-backup-<时间>`。

### SSL Labs 评级

**结果（2026-10-15，套件收紧之后，用户「允许 (Recommended)」）**：`one.forcitis.com`（47.114.56.157）评级 **A+**，无警告；协议仅 TLS 1.2 / 1.3，HSTS 已识别，前向保密等级 4（全部套件支持）。以 API `publish=off` 运行，不进公开列表。E-8 外部评测验收通过。

原说明：

SSL Labs 是第三方（Qualys）对外扫描：它会从境外向 `one.forcitis.com` 发起数十次 TLS 握手，并在评测结束后在公共列表中短时显示主机名（可用 `hideResults=on` 不公开）。要做的话，建议在上面的套件收紧之后跑一次，带 `hideResults=on`；不做的话，以上本机探测作为 E-8 的替代证据。
