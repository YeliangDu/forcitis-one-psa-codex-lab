## 原话

让 macOS 系统来托管合并队列（launchd 用户代理），现在开工

## 落点

one-merge 按其方案安装 ~/Library/LaunchAgents/com.forcitis.mq.plist（WorkingDirectory 主检出、RunAtLoad+KeepAlive、日志 merge-queue/launchd.out），先停手动 runner；登记进常驻配置台账
