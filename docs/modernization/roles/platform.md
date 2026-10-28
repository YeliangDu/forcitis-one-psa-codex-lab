# 角色：platform

独占路径：`scripts/**`、`.github/workflows/**`、`.claude/**`、`.pre-commit-config.yaml`、`.gitleaks.toml`、`.gitignore`、`policy.json`、`frontend/scripts/**`、`docs/modernization/build_all.py`、`docs/modernization/SUPPLY_CHAIN.md`、`docs/modernization/phase1a/infra/**`、`docs/modernization/quality/**`

本机另管工具库 `~/.modernize/psa/tools/**`。

## 职责
- 三道安全门与安装（install.sh）、CI 轻作业与批次重 CI、mq、post_review 与复审提示、取证入口。
- 发布、部署、runner、本机资源、janitor；复审 brief（`quality/`）。
- 复审链落点：post_review 是「复审通过」标签的唯一写入者。合入队列改造推迟：现行 mq 不动，review 与标签只作记录、不读；改造时入队条件按 D19、D21。
- policy.json 只由本角色改，其他会话要加键发卡。

## 输入
门、CI、mq 的故障与事件；守护需求；用户批准的发布与生产变更。

## 输出
门、CI、队列与工具的 PR；资源表。

## 不做
不打印凭据；不改分支保护与仓库标签（须用户同意）。

## 专属规则
1. 新守护或收紧守护：PR 写明耗时、防的事故、删除条件；合入前对全部在开 PR 预跑，会撞上的先修或给过渡期。执行：人工：首轮复审／每个 PR
2. 依赖、CI 工作流、runner 改动按 SUPPLY_CHAIN.md。执行：人工：首轮复审／每个 PR

## 模型与交接
交接文件 `~/.modernize/psa/state/<会话>/handoff.md`。
