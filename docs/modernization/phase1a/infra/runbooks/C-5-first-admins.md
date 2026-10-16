# C-5 剧本：首任超级管理员与应急账号开通

> **dev 阶段（2026-10-15 用户决定）**：只有平台负责人一人操作——超管 1 名、应急账号 1 个，均为用户本人；第二名超管、第二个应急账号与「双人在场」推迟到生产部署前。代码中 ADR-0027 的双人与「至少 2 名」检查在 dev 单人下会拒绝，须等 1a 的「仅非生产」例外 ADR 获批并实现；在此之前不以单人绕过这些检查（例如不以同一人充当 `--operator` 与 `--witness`）。

依据：ADR-0026 第 5 项第 5、6 条，ADR-0027 第 1 项（应急账号至少 2 个、对应超管持有人、不依赖在职快照），D-1a-47、D-1a-48，INFRA_RUNBOOK §6。时点：dev 前（dev 用测试人员；生产切换前再对生产执行一次）。性质：运维命令，**双人在场**，执行前须用户确认人选（A-3）。

> **阻塞：运维命令尚未实现。** 代码中没有一次性运维命令入口（无 `forcitis.ops.command`、无 CommandLineRunner），首任 `super_admin` 授予命令与应急账号登记命令都要等 ADR-0027 下一批实现（status/blockers/070 第 ②④ 项）。下列步骤按 INFRA_RUNBOOK §6 的设计写好，实现合并后按实际命令名核对再执行。

## 执行前确认点

1. A-3 已定：超级管理员持有人至少 2 名（姓名不进仓库，只在执行记录中写账号 ID）；应急账号 2 个，各绑定一名超管持有人。
2. 持有人已能用企业微信登录 dev（B-2、B-6 完成，A8 中有其企业微信 ID），系统里已有其账号。
3. 第二人在场（运维 + 一名超管持有人，或两名运维），双方在执行记录上签名。
4. `EMERGENCY_TOTP_ENCRYPTION_KEY` 已生成并写入 one.env（下面第 1 步）。
5. 持有人手机已装 TOTP 应用（例如企业微信以外的认证器），并在场。

> **流程管理员（清单 B-12）**：`process_administrator` 角色没有任何持有人时，应用在任何环境都拒绝启动。dev 阶段授给平台负责人本人（用户「我们开发过程中所有操作只有我一个人」），在首次部署时与本剧本一并执行：`--operation=grant-role --role-code=process_administrator`（命令形式见 INFRA_RUNBOOK §6）。同样须等 ADR-0030（dev 单人例外）获批并实现后再做，不以单人绕过双人确认。

> **命令形式待改（F-12）**：下面命令中的 `--spring.profiles.active=identity-operations` 会顶掉环境档案、跳过环境校验；1a 在 F-12 改为追加档案写法后，按 INFRA_RUNBOOK §6 同步修改。

## 步骤

1. 生成 TOTP 加密密钥（只一次；轮换见 C-8）：
   ```bash
   # 密钥经环境变量交给 perl，不出现在命令行参数
   KEY=$(openssl rand -base64 32) perl -i -pe 's/^EMERGENCY_TOTP_ENCRYPTION_KEY=.*/EMERGENCY_TOTP_ENCRYPTION_KEY=$ENV{KEY}/' /etc/forcitis-one/one.env
   chown root:forcitis /etc/forcitis-one/one.env && chmod 0640 /etc/forcitis-one/one.env   # perl -i 重写文件后恢复属组
   grep -c '^EMERGENCY_TOTP_ENCRYPTION_KEY=.\+' /etc/forcitis-one/one.env   # 期望 1，不打印值
   ```
**端口兜底（OPS-PORT 代码修复前）**：one.env 的 `SERVER_PORT=8080`、`MANAGEMENT_SERVER_PORT=8081` 是环境变量，优先于档案 `identity-operations` 里的 `server.port=-1`；不去掉的话运维命令会去绑 8080 / 8081，常驻服务运行时启动失败。所以下面每条命令都带 `-p UnsetEnvironment=SERVER_PORT -p UnsetEnvironment=MANAGEMENT_SERVER_PORT`。2026-10-15 在 ECS 上用临时单元核实（t-hz0704dqsgcmio0，systemd 255，只打印非机密变量）：`UnsetEnvironment` 在 `EnvironmentFile` 之后生效，两个端口变量为未设置，`SERVER_ADDRESS`、`MANAGEMENT_SERVER_ADDRESS` 仍为 127.0.0.1。运维命令若仍由 Tomcat 开出端口（one-1a-par 探针所见），主连接器按 `server.address` 只绑 127.0.0.1，独立管理端按 `management.server.address` 同样只绑回环；这一点按配置推断，未在 ECS 上运行运维命令验证。执行时另开一个窗口用 `ss -ltnp` 看 java 进程的监听地址，只应出现 127.0.0.1。本兜底只覆盖环境变量：F-12 改为追加档案后，生产档案 `application-prod.properties` 里的 `management.server.port=8081` 是配置文件，不受 `UnsetEnvironment` 影响，届时须随 OPS-PORT 一并处理。

2. 查出持有人的员工 ID（只读，不写数据；命令与参数以 INFRA_RUNBOOK §6 为准）。员工 ID 是小写 uuid，按组织源登录名或企业微信 userid 查：
   ```bash
   systemd-run --wait --pipe --uid=forcitis -p EnvironmentFile=/etc/forcitis-one/one.env \
     -p UnsetEnvironment=SERVER_PORT -p UnsetEnvironment=MANAGEMENT_SERVER_PORT \
     /usr/bin/java -Duser.timezone=UTC -jar /data/forcitis-one/current/forcitis-one.jar \
     --spring.profiles.active=integration-operations --operation=find-accounts-by-external-subject \
     --provider-kind=SEEYON_LOGIN_NAME --external-subject=<持有人组织源登录名>
   # 输出每个匹配一行 employee-id=<uuid> user-id=<…>；多于一行时与持有人当面核对是哪一位，不凭姓名或员工编号猜
   ```
3. 授予首任 `super_admin`（每名持有人一次；命令与参数以 INFRA_RUNBOOK §6 为准）。JVM 必须带 `-Duser.timezone=UTC`，否则应用拒绝启动：
   ```bash
   systemd-run --wait --pipe --uid=forcitis -p EnvironmentFile=/etc/forcitis-one/one.env \
     -p UnsetEnvironment=SERVER_PORT -p UnsetEnvironment=MANAGEMENT_SERVER_PORT \
     /usr/bin/java -Duser.timezone=UTC -jar /data/forcitis-one/current/forcitis-one.jar \
     --spring.profiles.active=identity-operations --operation=grant-role --role-code=super_admin \
     --employee-id=<持有人员工 ID> --operator=<操作人员工 ID> --witness=<见证人员工 ID>
   ```
4. 登记应急账号（每个账号一次）：
   ```bash
   systemd-run --wait --pipe --uid=forcitis -p EnvironmentFile=/etc/forcitis-one/one.env \
     -p UnsetEnvironment=SERVER_PORT -p UnsetEnvironment=MANAGEMENT_SERVER_PORT \
     /usr/bin/java -Duser.timezone=UTC -jar /data/forcitis-one/current/forcitis-one.jar \
     --spring.profiles.active=identity-operations --operation=register-emergency-admin \
     --employee-id=<超管持有人员工 ID> --login-name=<应急账号名> --operator=<操作人员工 ID> --witness=<见证人员工 ID>
   # 口令与 TOTP 密钥由命令生成，只在这一次输出里显示：持有人当场记录并扫码，随后清屏；不截图、不留存、不进文件或聊天
   ```
5. 持有人当场在内网用应急账号登录一次（口令 + TOTP），确认进入管理模式、1 小时后失效，并收到告警；首次登录后改口令（「强制改口令」待实现，实现前人工执行）。

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
