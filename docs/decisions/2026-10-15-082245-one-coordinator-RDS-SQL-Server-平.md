## 原话

RDS SQL Server 平台使所有登录带 ALTER ANY LOGIN 等服务器级权限且无法 DENY：「接受并登记风险，加校验与轮换 (Recommended)」；取证专用只读账号：「照建 (Recommended)」

## 落点

风险登记于台账与 B-5；口令只放 ECS、90 天轮换；启动校验服务器级有效权限等于平台基线；实例白名单不动；建 one_evidence_ro
