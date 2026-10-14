## 原话

复合主键技术表是否也加 UUIDv7 主键：「也加，自有表零例外 (Recommended)」

## 落点

所有自有表 UUIDv7 主键、原组合键改唯一约束；豁免只限框架自建表（Spring Session、ShedLock、Modulith 事件登记）；Flowable 接 UUIDv7；R-1b / ADR-0033 落地
