## 原话

调度用 get_usage 落盘

## 落点

V17a：额度读数改由调度每次唤醒用 get_usage 写 budget_level 读数文件，桌面应用 plan-usage-history.json 作回退；第三批读数验证 7 天自此起算
