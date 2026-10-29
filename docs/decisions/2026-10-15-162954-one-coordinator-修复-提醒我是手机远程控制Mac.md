## 原话

修复，提醒我是手机远程控制Mac

## 落点

SessionStart 钩子 matcher 改为 startup、clear、compact 三种来源（桌面 App / 手机远程控制的 /clear 实为新开会话，来源 startup）；SESSION_PLAYBOOK §2.1 第 13、16 条
