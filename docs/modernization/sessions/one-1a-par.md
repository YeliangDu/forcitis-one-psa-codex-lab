# one-1a-par

## 类型

工作

## 职责

- A 线人事与身份施工：按卡落实 workforce 人事命令与版本、账号生命周期、组织接口、工作日历及迁入衔接。
- 按批准 ADR 与签发契约交付一卡一单元；旧 PAR 编号只作历史依据，任务入口以当前卡为准。

## 独占

- 当前卡列明的人事、身份、日历代码、迁移、模块测试及页面；文件范围由卡锁定。
- 本线组织接口与受派 Flowable 时钟配置；共享登记仅本卡条目，不占整个文件。

## 不碰

- `scripts/release/deploy_gate.py`、本机资源、runner 与部署（one-ops）。
- one-1a-next-5 数据修复及登记改造卡；文件、签章与行政事务实现。
- 独立验收目录、他卡锁定文件（TEAM-69）。

## 输入

- 调度任务卡；架构设计、ADR、契约裁定。
- 数据签发、用语确认、业务规则及其他线的接口与依赖。

## 输出

- 本卡 PR、验证及复审材料 → 调度；涉数据材料 → one-data。
- 接口与依赖完成点 → 消费会话；发布需求 → one-ops；文档直交 one-merge（TEAM-53）。

## 触发与节奏

- 卡与设计、用语、DD 前置满足后开工（TEAM-17、22）。
- 一卡一单元、在制见 TEAM-21、43；完成点、阻塞及接口变化触发交出。

## 自动化

- 本卡测试、`scripts/precheck.sh` 与 `build_all.py --check`；验证见 TEAM-44、70–72。
- 开工单、快照与自清见 TEAM-06–07、14。

## 上下文文档

- `docs/modernization/phase1a/HANDOFF_one-1a-par.md`
- `docs/modernization/phase1a/a8-split/SPLIT_PLAN.md`
- 交接源: docs/modernization/phase1a/HANDOFF_one-1a-par.md#交接

## 交接条件

本卡交出、单元结束或上下文触发时按 TEAM-05–07；更新本线交接，调度安排接续或归档（TEAM-11）。

## 权限边界

- 自定：卡内实现、单元测试及交付材料；跨线范围变更报调度（TEAM-19–22）。
- 身份、授权、迁入写入权限等安全改动由负责会话承担（TEAM-33、67–69）。
- 用户决定与生产边界见 TEAM-91–99；部署由 one-ops 执行。
