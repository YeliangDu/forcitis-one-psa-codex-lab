## 原话

dev ECS 时间同步检查：批准，测试告警直接发

## 落点

one-ops 安装 one-time-sync-check 定时器（每 5 分钟只读 chronyc，>100 ms 或未同步 P2、>1 s P1，走群机器人），演练一次后恢复阈值并登记 RESOURCES；INFRA_RUNBOOK §8（#187）
