## 原话

新浏览器才确认 (Recommended)

## 落点

C-M7 外部票据登录：本浏览器近 N 天（默认 30，可配置）该账号登录过则免确认，否则走 sessions:confirm-identity；长期 HttpOnly/Secure/SameSite=Lax 浏览器标识 cookie，服务端只存哈希，绑定 employeeId 与最近登录时间；登出不清，账号停用/离职作废全部标识；审计免确认/需确认及原因（经 one-coordinator 转达）
