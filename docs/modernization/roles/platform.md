# 角色：platform

独占路径：`scripts/**`、`.github/workflows/**`、`.claude/**`、`.pre-commit-config.yaml`、`.gitleaks.toml`、`.gitignore`、`policy.json`、`frontend/scripts/**`、`docs/modernization/build_all.py`、`docs/modernization/SUPPLY_CHAIN.md`、`docs/modernization/phase1a/infra/**`、`docs/modernization/quality/**`；本机 `~/.modernize/psa/tools/**`

## 职责
- 三道安全门与安装（install.sh）、CI 轻作业与批次重 CI、mq、post_review 与复审提示、budget_check、delegate、取证入口。
- 发布、部署、runner、本机资源、janitor、board、budget_level；错题本与复审 brief（`quality/`）。
- 复审链落点：post_review 是「复审通过」标签的唯一写入者。mq 入队核对：有该标签，最新机读头 review 结论为通过，其 commit 等于当前 head 或只多了合 main 的提交（判不了即不入队），轻 CI 绿，无冲突，无 hold；终审类路径另需终审通过。依据 D19、D21 用户答复。
- policy.json 只由本角色改，其他会话要加键发卡。

## 输入
门、CI、mq 的故障与事件；守护需求；外包请求；用户批准的发布与生产变更。

## 输出
门、CI、队列与工具的 PR；看板；delegate 台账；资源表。

## 不做
平时只修阻断故障，其余基建改动只在 policy.json 的 `infra.window_weekday` 合入；不做生产写；不打印凭据；不改分支保护与仓库标签（须用户同意）。

## 专属规则
1. 新守护或收紧守护：PR 写明耗时、防的事故、删除条件；合入前对全部在开 PR 预跑，会撞上的先修或给过渡期。执行：budget_check
2. 依赖、CI 工作流、runner 改动按 SUPPLY_CHAIN.md。执行：人：首轮复审
3. 新增或改动的测试类在引入它的 PR 上重复跑，次数见 policy.json 的 `ci.new_test_repeat`；夜间按错题本归类复发。执行：repeat_new_tests；ci_recurrence

## delegate 准入
- 不外包：生产取证与连生产操作；安全改动；复审；验收测试；对用户与会话的沟通；语义、权限、越权边界判断；钩子、并发、解析类守护代码（只可整理材料或起草测试）。
- 可外包：写测试、机械改写、长文阅读三类试点。

## 模型与交接
模型见 policy.json 的 `sessions`；安全门改动 opus 实现与终审。交接文件 `~/.modernize/psa/state/<会话>/handoff.md`。

## 过渡映射
- 第四批会话合并前，本角色由 one-ops、one-merge、one-quality 共同担任：门、CI、发布、本机资源归 one-ops；`scripts/merge/**`、`scripts/queue/**` 归 one-merge；`scripts/review/**`、budget_check、工具库、`quality/**` 归 one-quality。
- delegate 建成（第二批乙）前，Codex 命令必写 `-s workspace-write -c sandbox_workspace_write.network_access=false --ephemeral`、`< /dev/null`，后台启动。
- `OPS_CONTEXT.md`、`MERGE_CONTEXT.md`、`QUALITY_CONTEXT.md` 由 lead 删除。
