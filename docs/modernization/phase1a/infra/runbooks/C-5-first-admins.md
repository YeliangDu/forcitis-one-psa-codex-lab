# C-5 剧本：首任超级管理员与应急账号开通

依据：ADR-0026 第 5 项第 5、6 条，ADR-0027 第 1 项（应急账号至少 2 个、对应超管持有人、不依赖在职快照），D-1a-47、D-1a-48，INFRA_RUNBOOK §6。时点：dev 前（dev 用测试人员；生产切换前再对生产执行一次）。性质：运维命令，**双人在场**，执行前须用户确认人选（A-3）。

> **阻塞：运维命令尚未实现。** 代码中没有一次性运维命令入口（无 `forcitis.ops.command`、无 CommandLineRunner），首任 `super_admin` 授予命令与应急账号登记命令都要等 ADR-0027 下一批实现（status/blockers/070 第 ②④ 项）。下列步骤按 INFRA_RUNBOOK §6 的设计写好，实现合并后按实际命令名核对再执行。

## 执行前确认点

1. A-3 已定：超级管理员持有人至少 2 名（姓名不进仓库，只在执行记录中写账号 ID）；应急账号 2 个，各绑定一名超管持有人。
2. 持有人已能用企业微信登录 dev（B-2、B-6 完成，A8 中有其企业微信 ID），系统里已有其账号。
3. 第二人在场（运维 + 一名超管持有人，或两名运维），双方在执行记录上签名。
4. `EMERGENCY_TOTP_ENCRYPTION_KEY` 已生成并写入 one.env（下面第 1 步）。
5. 持有人手机已装 TOTP 应用（例如企业微信以外的认证器），并在场。

## 步骤

1. 生成 TOTP 加密密钥（只一次；轮换见 C-8）：
   ```bash
   # 密钥经环境变量交给 perl，不出现在命令行参数
   KEY=$(openssl rand -base64 32) perl -i -pe 's/^EMERGENCY_TOTP_ENCRYPTION_KEY=.*/EMERGENCY_TOTP_ENCRYPTION_KEY=$ENV{KEY}/' /etc/forcitis-one/one.env
   chown root:forcitis /etc/forcitis-one/one.env && chmod 0640 /etc/forcitis-one/one.env   # perl -i 重写文件后恢复属组
   grep -c '^EMERGENCY_TOTP_ENCRYPTION_KEY=.\+' /etc/forcitis-one/one.env   # 期望 1，不打印值
   ```
2. 授予首任 `super_admin`（每名持有人一次；命令名以实现为准）：
   ```bash
   systemd-run --wait --pipe --uid=forcitis -p EnvironmentFile=/etc/forcitis-one/one.env \
     /usr/bin/java -jar /data/forcitis-one/current/forcitis-one.jar --forcitis.ops.command=grant-first-super-admin
   # 从标准输入读入：持有人账号 ID、在场第二人账号 ID、原因
   ```
3. 登记应急账号（每个账号一次）：
   ```bash
   systemd-run --wait --pipe --uid=forcitis -p EnvironmentFile=/etc/forcitis-one/one.env \
     /usr/bin/java -jar /data/forcitis-one/current/forcitis-one.jar --forcitis.ops.command=register-emergency-admin
   # 从标准输入读入：应急账号名、对应的超管持有人账号 ID、初始口令（≥ 12 位且有复杂度，持有人当场输入，运维不看）
   # 终端只输出一次 TOTP 绑定 URI / 二维码：持有人当场扫码，随后清屏；不截图、不留存
   ```
4. 持有人当场在内网用应急账号登录一次（口令 + TOTP），确认进入管理模式、1 小时后失效，并收到告警；首次登录后改口令（「强制改口令」待实现，实现前人工执行）。

## 校验

- 审计中有：首任授予（含在场第二人）、应急账号登记、首次应急登录与告警，各一条。
- 非内网地址访问应急登录被拒；连续 5 次失败锁 15 分钟。
- `super_admin` 持有人数 ≥ 2 后，敏感权限授予需第二人确认（ADR-0026 第 6 项）。

## 执行记录模板（存公司内部，不进仓库）

| 日期 | 环境 | 动作 | 对象账号 ID | 对应超管账号 ID | 执行人 | 在场第二人 | 审计条目 ID |
|---|---|---|---|---|---|---|---|

## 回滚

- 授予错误：由另一名超管在管理模式收回 `super_admin`（双人确认）；只有 1 名时用运维命令收回并由第二人在场。
- 应急账号：运维命令注销（持有人离职时同样处理，D-1a-48）。
