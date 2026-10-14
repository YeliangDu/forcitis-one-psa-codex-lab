# 组织与任职历史规则卡（organization.workforce）

编号待协调者分配（候选）。依据：ADR-0026 §2；数据打分卡 T1「组织单元与任职的变化历史在交易库保留」。

## 候选规则：历史从第一次同步之日起，更早日期的历史导入在补录前不得进行

```
Given 新系统第一次同步在 D0 日完成
 And  org_unit_versions / assignment_versions 的第一条记录的生效日是 D0（回填日）
When  某历史单据（迁移导入）的业务日期早于 D0，需要按日期解析法人
Then  解析找不到该日的部门或任职，返回「员工法人无法确定」（422），不猜、不取今天的值
 And  这类历史导入被阻止，直到组织数据管理员补录 D0 之前的历史
 And  补录是追加更早的生效区间，不改写已有区间；补录后同一导入可重试

Given 员工在第一次登录时登记（EmployeeRegistered），早于下一次同步
When  同一员工被两个并发登录同时登记
Then  登记先取得快照写锁，后到者发现已登记就不再写、不再发事件（一个员工只发一次 EmployeeRegistered）
 And  登记与同步互斥：同步进行中，登记等待，不会写进同步正在重算的快照
```

为什么现在写成规则而不写代码：回填日是部署事实，补录的数据来自业务，需要组织数据管理员提供；在没有历史来源之前阻止比猜测安全。守护：解析找不到时失败（G-1），见 `EmployeeLegalEntities.on` 的契约测试；并发登记见 `OrgDirectoryIntegrationTest.concurrentFirstLoginsRegisterOnce`。
