## 原话

W1 期间本机临时 postgres:18 容器做表结构对比：「允许，用完即删 (Recommended)」；ADR-0029 生产只读计数（0029-Q2）：「授权 (Recommended)」

## 落点

one-1a-next W1 可用 Testcontainers / docker 起临时空库，用完即删；one-ops 做一次只读计数（只出计数、走索引、设超时），结果写入 ADR-0029 证据
