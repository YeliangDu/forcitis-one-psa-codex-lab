# INTENT — FORCITIS one

日期：2026-10-14

## 目标

**先评估、再定路线和栈**（understand first → 由 assess 推荐 uplift / transform / reimagine 之一）。

用户原话：「使用 code modernization 来现代化本仓，目标参照栈 /Users/yeliang/Projects/FORCITIS/forcitis-one/docs/adr/0001-tech-stack.md，你全面评估后再最终确定栈」

## From → To

- **From**：psa 单仓 —— `psa-backend`（Java 8 / Spring Boot 2.7.7 / Spring Cloud Alibaba 微服务，yudao/ruoyi-vue-pro 底座，MyBatis-Plus，SQL Server 2016，Flowable，Nacos/Redis/RocketMQ/XXL-JOB）、`psa-front-desk-pc`（Vue 2.7 + Element UI 2.15）、`psa-mini`（Taro 4.2 + React 18 H5）。
- **To（参照，未定稿）**：forcitis-one ADR-0001 及其修订 —— Java 25 LTS、Spring Boot 4.1.x、Spring Modulith 2.1.1 模块化单体、PostgreSQL 17（一 BC 一 schema）、Spring Data JDBC、Flyway（模块感知）、Spring MVC REST + OpenAPI 3、Modulith JDBC Event Publication Registry（outbox）、流程引擎推迟（规则型审批，引入时 Flowable 8）、Modulith test + ArchUnit + Testcontainers PG17、Maven。前端栈 ADR 未规定，由评估决定。
- **最终栈**：由 `brief` 在评估之后确定，ADR-0001 是参照而非预设结论。

## 必须保持成立（用户原话）

- 老系统迁移期间持续运行
- 顺手修已知 bug
- 先做安全审查
- 功能复刻但是行为要现代化

## 背景答复（用户原话）

- 范围：「完整系统，外部仅读库/接口」
- 与 forcitis-one 的关系：「forcitis-one 就是 PSA 的接班系统」
- 本轮禁区：「仅分析，不改 psa 仓代码」
- 「forcitis-one仓库代码不能参考」—— 只允许参考其 ADR-0001（技术栈）这一文档；不读取、不复用、不引用 forcitis-one 的源码。

## 待定项

- 前端目标栈（PC 端 Vue 2.7 已 EOL；H5 已是 Taro/React）——由 assess/brief 给建议。
- SQL Server → PostgreSQL 的数据迁移与外部只读消费者（BI）切换方式。

## 评估过程中用户追加的要求（原话，按时间顺序）

| 原话 | 落点 |
|---|---|
