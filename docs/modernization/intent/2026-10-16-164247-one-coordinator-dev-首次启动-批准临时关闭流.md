## 原话

（dev 首次启动）批准临时关闭流程管理员自检；同时授予超级管理员；A8 登录名让 one-ops 查

## 落点

dev：one.env 临时设 FORCITIS_PLATFORM_APPROVAL_PROCESS_ADMINISTRATOR_REQUIRED=false，启动同步员工后按 C-5 授予用户 process_administrator 与 super_admin（登录名由 one-ops 按姓名查到后报用户确认），随即删除开关重启并确认自检通过
