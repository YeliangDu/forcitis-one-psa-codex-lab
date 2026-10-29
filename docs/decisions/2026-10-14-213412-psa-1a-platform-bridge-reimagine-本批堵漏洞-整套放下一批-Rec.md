## 原话

本批堵漏洞，整套放下一批 (Recommended)

## 落点

本批 B1 只修 org_units fail-open（范围无法计算或不覆盖即拒绝、越权 404 并审计）；ADR-0026 1a 交付的整套数据范围机制列为下一批第一项，首次部署任何环境前必须完成；DEFERRED 与 status/blockers 登记（经 one-coordinator 转达）
