## 原话

C-14 (f) H-2（SQLAgentOperatorRole 能否移出）选 A：「a」

## 落点

批准在 C-14 探针阶段对探针自己的 msdb 用户试一次 ALTER ROLE SQLAgentOperatorRole DROP MEMBER 并回读，探针用完即删，不碰正式账号与已有主体（「共享实例上不动 msdb」的一次性例外，仅限探针）；能移出则正式账号处理另报用户批，不能则 (f) 接受该角色并写入 ADR-0037 第 4 项 (f)（one-architecture）；one-ops 另行向用户核实，用户选「批准这一次」
