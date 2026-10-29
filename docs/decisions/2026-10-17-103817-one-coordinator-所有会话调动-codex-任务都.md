## 原话

所有会话调动 codex 任务都要在自己的后台任务显示

## 落点

SESSION_PLAYBOOK §5 Codex 调用：codex exec 一律用会话 Bash 的 run_in_background 启动，不用 nohup、& 或 setsid 脱离；已脱离在跑的按进程盯到结束
