# one-ops 速查（2026-10-22 增补版（含下面「2026-10-22 增补」）；原 2026-10-21 13:30+02:00 交接版：dev 在 rc.6，等 A 线 #489 与 F 合入后调度通知出新 rc 并重建 dev；用户新口径：不影响当前开发的运维/采购议题只记台账、到触发条件再报）

## 2026-10-24 夜交接二（上下文 24%，清空接续；以本节为准，下面「夜交接」已被覆盖）

- **在做：#693 批一整合最后一轮**（调度令：本轮之后批一不再加审查，用户 intent 171229「开发前期效率优先」）。都没推、没合 next。**已全部报调度派审，等结论**：
  - ops-fv2-b1：cdf8de735、07bff4ce3、f388c28cc（三者终审已过）→ **c21037088**（*.sh 的 ${VAR}＋机械检查）→ **4bb536a48**（settings.json 6 处 ${VAR}、检查范围加 settings.json、rm 路径加 ./）。
  - claude/one-ops-flow-v2-b1p（工作区 .claude/worktrees/ops-fv2-b1p）：855dd86ca（终审必改）→ **9cc8622c3**（final_paths 补 prod-approvals.txt，共 18 项）；closes.json 已加 855dd86ca→9cc8622c3，等调度签字。
  - 本机 cutover.sh sha1 **62c6ca8ff9f7f662597918c4e928b76f9653aa9a**（待终审）；沙箱 ~/.modernize/psa/ops/pr693-pyc/cut_sbx.sh。
  - 中文环境验证已过：light.sh（b1@c21037088）31 PASS 0 FAIL，acceptance_v2_selftest 通过；gates_selftest 过。
  - b1.md（本机，不审）：第 5 节加切换前置两步，第 3 节加两行已知例外（门只核入口脚本、本机缺 PyYAML）。已完成。
- **下一步**：等调度转复审结论；有必改就在对应分支作追加提交再报。都过后在 .claude/worktrees/flow-v2 依次 `merge --no-ff` claude/one-ops-flow-v2-b1、claude/one-ops-flow-v2-b1p、coordinator@95becd373 进 next（claude/one-ops-flow-v2-next）；重跑 check_reviews（基 pre-flow-v2）、acceptance_v2、light×3，结果写 acceptance/b1run-<短sha>/；更新 b1.md 状态行与 1.2；报最终 sha 与推送命令 `git -C /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/flow-v2 push origin claude/one-ops-flow-v2-next`（用户本人推）。推上后等调度通知 CI 跑完，汇总各作业结论、耗时、失败日志原样段；#693 queued 标签保留。
- 不做（推第二批，调度令）：PROMPT.md 子代理自行 post_review、CS-7.1 指针、budget_check H9、mq 快道 OneSql.java、门不核导入模块。
- 在途卡 OPS-1022-OPS-02、OPS-1023-OPS-01 未动（用户要求先做流程重构）。无其他未完成的用户直接请求。
- 坑：同上节（PYTHONDONTWRITEBYTECODE 须 env -u；后台脚本先 cd 进被测树；Bash cd 漂移；zsh 与 bash 差异；推送门拦 git push）。另：真切换沙箱用 expect 驱动 /dev/tty 输入（script 管道只送得进第一行）。

## 2026-10-24 夜交接（上下文 22%，清空接续；以本节为准，下面「晚交接」各条已被覆盖）

- **在做：#693 批一整合本轮必改**（调度令）。都没推、没合 next。
  - ops-fv2-b1（工作区 .claude/worktrees/ops-fv2-b1）追加：cdf8de735（pyc 修复，首轮、终审已过）→ **07bff4ce3**（清理挂 EXIT trap、-z、空列表不误报、函数挪进 light_triggers.sh）→ **f388c28cc**（runbooks/sql/ 挪进 READ_BY_BUILDS）→ **c21037088**（全仓 $VAR 紧跟全角字符改 ${VAR}，light_selftest 加机械检查）。
  - 新子分支 claude/one-ops-flow-v2-b1p（工作区 .claude/worktrees/ops-fv2-b1p，基 5d1e9fba4）：**855dd86ca**（policy final_paths 11→17 项）；check_reviews 按 17 项重算无新增缺终审。
  - 本机 cutover.sh：新 sha1 **62c6ca8ff9f7f662597918c4e928b76f9653aa9a**（tty_ok 前置、before.txt 不覆盖、回退打印最早快照、${VAR}）。旧版另存 ~/.modernize/psa/ops/pr693-pyc/cutover.sh.{bc4f318,138ad0e5}；沙箱脚本 ~/.modernize/psa/ops/pr693-pyc/cut_sbx.sh <cutover.sh>（中文 UTF-8 下 /bin/bash 3.2 跑 dry-run 两种、真切换、--rollback），新版全过，旧版复现崩溃。
  - b1.md 第 6 节已加两种人工回退手段，第 5 节第 7 步已加分支用会话名（调度令，已回报）。
- **已报调度并派审**：07bff4ce3、f388c28cc、855dd86ca、cutover 138ad0e5（后者终审必改＝${VAR} 问题，已修成 62c6ca8f）。
- **未报调度**：c21037088 与 cutover 62c6ca8f。报之前先看中文环境整跑结果（清空时还在后台跑）：scratchpad 下 /private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-one-ops-a5ff89/3fa31959-863e-4868-bae0-6d56437131f8/scratchpad/light-zh.log（末行 light rc=）与 acc-zh.log（末行 acc rc=；acceptance_v2_selftest 在 flow-v2＝next 上跑）。都 0 就报调度：c21037088 sha、cutover 新 sha1、验证（zh_CN.UTF-8、/bin/bash 3.2：light_selftest 过，变异红；hooks_manifest 旧版 --check 遇坏链接崩 rc=1、新版正常；cutover 沙箱四种全过、旧版复现崩）。没有末行就在 ops-fv2-b1 用 `LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 env -u PYTHONDONTWRITEBYTECODE /bin/bash scripts/ci/light.sh 38c98b4ffcbed870f9571e13d6ea4cd957594a55` 重跑，flow-v2 跑 acceptance_v2_selftest.sh。
- **之后**：所有复审过后，在 .claude/worktrees/flow-v2 依次 merge --no-ff claude/one-ops-flow-v2-b1 与 claude/one-ops-flow-v2-b1p 进 next，重跑 check_reviews（基 pre-flow-v2）、acceptance_v2、light×3（结果写 acceptance/b1run-<短sha>/，模板见旧 scratchpad b1run.sh），更新 b1.md 状态行与 1.2，报最终 sha 与推送命令 `git -C /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/flow-v2 push origin claude/one-ops-flow-v2-next`（用户本人推送）。推上后等调度通知 CI 跑完，汇总每个作业结论、耗时、失败日志原样段；#693 queued 标签保留。
- 在途卡 OPS-1022-OPS-02（Docker 自动拉起）、OPS-1023-OPS-01（卡不完整，已报调度）未动：用户要求先做流程重构。无其他未完成的用户直接请求。
- 坑：本机会话环境自带 PYTHONDONTWRITEBYTECODE=1，复现字节码问题须 env -u；后台复现脚本须先 cd 进被测树；Bash `cd` 会让主目录漂移；zsh 里 `$(...)` 列表与 bash 不同，扫描用 /bin/bash 脚本；推送门会拦命令里的 git push（沙箱里用 clone --bare 代替）。

## 2026-10-24 晚交接（上下文 20%，清空接续；以本节为准）

- **在做：#693 CI 重作业失败的修复**（调度令）。backend 重作业 1029 个 IT 里有 1 个 Error：LegacyNumberSourceIT.theOldBatchTermIsGone 报 MalformedInputException（run 40096266208，runner one-2）。根因：backend 作业先跑 light.sh 再在同一工作区跑 Maven；build_all 写出 backend/glossary/__pycache__/*.pyc，这个 IT 遍历 backend/glossary 时读到 .pyc 崩溃。
- **改动已做、未提交**（在 ops-fv2-b1 工作区，基 2f16d4aa4；补丁另存 ~/.modernize/psa/ops/pr693-pyc/pyc-fix.patch）：light.sh 开头 export PYTHONDONTWRITEBYTECODE=1；跑前用 `git ls-files -oi --exclude-standard --directory` 记下已有的被忽略 __pycache__/.pyc，最后一项「no new __pycache__ left in the checkout」查新增，有就 FAIL（只看目录级新增：跑前已有的 __pycache__ 目录里新增的 .pyc 查不出；CI 检出是干净的，不受影响）；ci.yml 工作流级 env 同设 PYTHONDONTWRITEBYTECODE。light.sh 120 行（上限 120），shellcheck -x、actionlint 通过。不改测试与断言。
- **复现在后台跑**（脚本 ~/.modernize/psa/ops/pr693-pyc/repro.sh）：在临时 worktree（scratchpad/repro-wt，@5d1e9fba4）上先跑原版 light.sh 再跑这个 IT（预期红），再打补丁跑一遍（预期绿）。结果汇总写到 /private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-one-ops-a5ff89/0dc6d04c-2106-4264-9b24-bf1dec472a98/scratchpad/repro.sum（末行「完成」表示跑完），各步日志在同目录 repro-{before,after}-{light,it}.log；跑完会自动删临时 worktree。如果 repro.sum 里没有「完成」又没有进程在跑，就直接重跑 repro.sh。
- **下一步**：①复现改前红、改后绿，并且改后 light 不 FAIL，就在 ops-fv2-b1 提交（fix(ci)…，GOV-1023-FV2；说明写根因、改法和复现结果），sha 报调度，派首轮＋终审（scripts/ci、.github 都是守护路径）；改后若 light 报「no new __pycache__」FAIL，说明还有 python3 -I 的脚本在写字节码，要找出来改。②复审过后在 .claude/worktrees/flow-v2 用 merge --no-ff 合进 next，本机重跑 check_reviews、acceptance_v2、light×3（照 b1run 的做法，脚本模板见 scratchpad/b1run.sh），b1.md 更新版本号。③推送仍由用户本人做（旧推送门挡）：把命令 `git -C /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/flow-v2 push origin claude/one-ops-flow-v2-next` 报调度转用户。④推上去后 CI 会自动重跑（#693 保留 queued 标签），等调度通知「CI 跑完了」，再汇总每个作业的结论、耗时、失败日志原样段，然后摘掉 queued。
- 本轮已完成：#693 草稿 PR 已开（用户本人推送 5d1e9fba4）；第一次 CI 结果：assertion drift、frontend 轻和重、gitleaks 通过，backend (light) 被加标签后的新 run 顶掉（取消，属预期），backend 重作业就是上面这 1 个失败。b1.md 第 3 节已记「旧推送门不认新树，整合分支由用户本人推送」。
- **接续后（同日）**：上一轮复现无效——本机会话环境自带 PYTHONDONTWRITEBYTECODE=1，改前 light 没写出 .pyc（repro.sum 已改名 .invalid-env），且清空时中断。repro.sh 第 2 行已加 `unset PYTHONDONTWRITEBYTECODE PYTHONPYCACHEPREFIX`，后台重跑中，结果仍写原 repro.sum。
- **再更新**：已提交 **cdf8de735**（ops-fv2-b1，未推），已报调度等首轮＋终审。复现（repro.sum）：改前 IT 1 Error（红），改后 4 个全过（绿），改后 light 32 PASS 0 FAIL、残留 __pycache__ 0。与原方案的差别：门和钩子的 python3 -I 子进程照样写字节码，末项从「有新增就 FAIL」改成「删掉本次新增的」。下一步为本节第②步起。复现脚本须 cd 进临时 worktree 再跑 light（已改）。
- **本轮必改已做（等 light 把关后报调度派审）**：ops-fv2-b1 追加 **07bff4ce3**（清理挂 EXIT trap、-z、空列表不误报、函数挪进 light_triggers.sh、自测＋4）与 **f388c28cc**（runbooks/sql/ 挪进 READ_BY_BUILDS，自测改断言并加防再犯）；policy 在新子分支 **claude/one-ops-flow-v2-b1p**（工作区 .claude/worktrees/ops-fv2-b1p，基 5d1e9fba4，因 final_paths 只在 next 上）提交 **855dd86ca**（final_paths 17 项）；check_reviews 按 17 项重算，除已知 #691 两个外无新增缺终审。cutover.sh 本机改（新 sha1 138ad0e5e…，旧版存 ~/.modernize/psa/ops/pr693-pyc/cutover.sh.bc4f318）：tty_ok 前置、before.txt 不覆盖、回退打印最早快照，沙箱实测过。合 next 时三条都要合（b1、b1p）。
- **调度令（最新）**：先别合 next。等 6 份结论齐了（cdf8de735 首轮和终审，加上整份复审 4 份：cutover.sh、policy.json、ci.yml＋relevant_change、install.sh＋hooks_manifest＋.pre-commit-config），调度一次转来必改，改完一起合、一起重跑、一次推送。已向调度报自查：①清理不在 EXIT trap 上，中断或 exit 2 会漏，打算挪进 trap 并保留原退出码；②ci.yml 243–245 行的 python3 -I（release 自测）要核对所在作业后面还有没有遍历源码的测试。
- 坑：Bash 里 `cd` 到别的工作区会让会话主目录漂移；推送门不展开 `$变量`，git -C 要写完整路径。

## 2026-10-24 傍晚增补（以本节为准）

- 外部复核四件都已完成：C3 修复链 dae30821d→789dc22da→2f16d4aa4（2f16d4aa4 首轮、终审均通过），已合进 next，**最终 next＝5d1e9fba4**；closes.json 已补，等调度签字。重跑结果在 b1run-5d1e9fba4/：check_reviews 88 个提交 86 个通过（只差 #691 两个基点），acceptance_v2 与上一版相同，light 三次 31 过、0 失败、7 跳过（约 200 秒）；b1.md 已更新。
- **已推送并开草稿 PR #693**（用户本人在终端推送：会话里的旧推送门要跑已删的 precheck.sh，拒绝推送；用户原话「同意」）。PR 不加标签、不入队、不批准。**下一步**：CI 全部作业（含重作业）结束后，把每个作业的结论、耗时和失败日志原样段报调度。next 在工作区 .claude/worktrees/flow-v2。
- 坑：后台 Bash 的 description 要用卡号格式，如「OPS-1023-OPS-01 … (one-ops)」；GOV-1023-FV2 被钩子拒。

## 2026-10-24 下午增补（以本节覆盖下面「外部复核四件」各项）

- C3：dae30821d 终审必改 M1（预筛放行 git、gh 和 VAR= 前缀）已修，追加 **789dc22da**（ops-fv2-b1，未推）：放行名单删去 git、gh，首词不再跳过 VAR=，远程特征加 /dev/tcp、/dev/udp；自测全过，3 个变异都红。调度已派首轮和终审。dae30821d 的首轮也是必改（失败预筛不查生产目标），调度认为这次改法已经覆盖；复审若再报，按「失败路径不得比正常路径放得宽」处理。
- C4：用户原话「A」（intent 2026-10-24-142512），维持 001330，代码不改，已在 b1.md 第 3 节记下。
- 3、4 已完成：check_reviews.py 改读 policy 的 final_paths，读不到退出 2；b1.md 已按调度清单订正，新增 8.1 第二批待办。
- **下一步**：789dc22da 复审都过后，把 ops-fv2-b1 合进 next，重跑 check_reviews（基 pre-flow-v2）、acceptance_v2、light×3，结果写 b1run-<短sha>/，并更新 b1.md 状态行和 1.2；报调度最终 sha，等调度确认后推送并开草稿 PR（见下面「之后」）。

## 2026-10-24 交接（上下文 27%，清空接续；以本节为准，下面 13:00 一节已过时）

- **在做：调度令「外部复核四件」**（结论 ~/.modernize/psa/coordinator/redesign-1023/acceptance/b1-review-repro.md，原文 b1-review-grokbot.md；最终 next 原为 955ddffd6）。四件都作追加提交，sha 报调度派审；齐了重合 next、重跑 check_reviews、acceptance_v2、light×3（运行脚本模板见本会话 b1run 做法：summary.tsv＋各步日志写 acceptance/b1run-<短sha>/）。
  1. **C3 已做**：ops-fv2-b1 提交 **dae30821d**（未推）——settings.json PreToolUse 入口内嵌保守预筛，门出错时只拒涉及生产的命令，其余放行告警并记 ~/.modernize/psa/tools/hooks/gate_failures.log；分发器门出错改退出 3；gates_selftest 1100 行（上限 1100，已满）、分发器 76 行。gates_selftest 全过，变异 7 个均红。**已向调度报 sha（在 C4 冲突那条消息里），等派首轮＋终审**。
  2. **C4 暂停**：调度要 --rollback 把 current 切回 before.txt 的版本，但与用户 intent 2026-10-24-001330（current 保持批一版本不动，在 claude/one-coordinator-two-lanes）冲突，已报调度请转用户（选 A 维持并在 b1.md 第 3 节记已知、B 按 C4 改需用户新原话）。等答复；B 的做法：读 $STATE/before.txt 的 current（须 40 位 sha 且 $HOOKS/<sha> 是目录，否则停），"$MAIN/scripts/gates/install.sh" --switch <sha>，回读 readlink；dry-run 两种都跑；cutover.sh 第 5、77 行及头注释同改。
  3. **未做**：acceptance/check_reviews.py 须终审前缀改读被核对分支上 policy.json 的 review.final_paths（不再内置 5 个），读不到键报错退出；调度已派 6 个补终审（390aef980、848c57731、660aee138、52f44c19f、7cc88028e、502054a9e），结论到后重跑。
  4. **未做**：b1.md 文字订正，逐条按调度消息（第 39 行删除构成 hooks 19＋precheck 1＋architecture 2＋PB_*.md 2；第 81 行算式 start_sheet 583、7 个文件 1,401 行；说明 1 的 164/196 秒与 build_all 98→122 秒；第 16 行 #691 如实写；1.1 改按 policy 11 个前缀；第 2 节注明后端、前端、门自测只在 b6bbeb3e8 本机跑过、最终以整合 PR 的 GitHub CI 为准；第 3 节加四行已知例外：每 PR<7 提交、角色阈值、send_message、复审标签空窗 C11；新增「第二批待办」C5～C9、C12 与防膨胀②～⑤）。调度原消息全文要从调度处再要（本节只是摘要）。
- **之后（用户已批，intent 141307，推前须核原话）**：四件与 6 个补终审都过、重合 next 并本机重跑后，报调度最终 sha，调度确认后推 claude/one-ops-flow-v2-next 并开**草稿 PR**（标题注明「批一整合，勿合」，加 hold，不入队不批准），CI 结果（含重作业）报调度。C10 用户定：trailer、复审头、模型名都不动。
- 已完成：gap-2（08b54d057＋ec5a6344e）已合 next 955ddffd6；closes.json 已签；b1.md 第 7 节 B1 行已改「已取消（intent 221336）」。
- 在途卡 OPS-1022-OPS-02、OPS-1023-OPS-01 未动（用户要求先做流程重构）。无其他未完成的用户直接请求。
- 坑：Bash `cd` 到别的工作区会让会话主目录漂移；改本工作区文件前先 cd 回 one-ops-a5ff89。gates_selftest.py 已到 1100 行上限，再加测试须先挪行或请调度定上限。

## 2026-10-24 13:00 交接（上下文 21%，清空接续；以本节为准）

- **再更新**：08b54d057 首轮必改（light.sh 超 120 行）由 ec5a6344e 关闭（拆 light_triggers.sh、light_selftest.sh，-z 与 git 退出码），两者首轮终审均过；closes.json 已补；已合进 next，**最终 next＝955ddffd6**（本地未推）；全套验收已重跑（b1run-955ddffd6/），b1.md 已更新，已报调度。下一步等调度与用户验收、切换时机；建议项留第二批。

- **更新（接续后）**：两种基线重跑发现 72228d656 带出两处失败（proposal_reads 未登记 light.sh；自测示例让 relevant_change 把 light.sh 判成读全部文档），已修并 amend 为 **08b54d057**（ops-fv2-b1，未推）；重跑 origin/main 30 PASS 0 FAIL，main~30 仅 check_pages --changed 报 GL6/GL7 方言（新页照查的设计，非回归）。已报调度派首轮＋终审，等结论后做原第③步（next 合入时用 08b54d057）。调度嘱：切换时旧 mq 锁文件不清（已核 cutover.sh 不碰 .git/merge-queue/）。

- **在做：调度令 gap-2（批一验收前必改）**：旧 precheck.sh 里按改动触发、旧 ci.yml 从未跑的五项（build_decision_page、check_pages 自测与 --changed、j_checks、shared_mock_verify_scan、weak_registration_scan）接进 light.sh。**已提交 b1 子分支 72228d656（本地，未推送，子分支照例不推）**：沿用 precheck 原路径模式按改动触发，无基线全跑；盘点另发现 shell 语法（改到的 .sh 逐个 bash -n）也无去处，一并补（须向调度说明这是第六项）；`light.sh --self-test` 已过，bash 3.2 过，shellcheck 过，两次变异（j_checks 模式、t_shell 模式）自测均失败＝有效。b1.md 已加「附录 A」26 项去处表。
- **接续后按序**：①在 ops-fv2-b1 工作区后台重跑 `scripts/ci/light.sh origin/main` 与 `scripts/ci/light.sh $(git rev-parse origin/main~30)`（后者应触发确认页规则、两个扫描、shell 语法；之前那次后台运行是在改文件途中开跑的，结果作废；后台任务 description 须用 `OPS-1023-OPS-01 … (one-ops)` 格式），看新增行 PASS；②把 72228d656 报调度派首轮＋终审（scripts/ci 属守护），说明第六项与结果；③复审过后重合 next（next 现 5e3b417d3，合 ops 新提交），重跑全套验收（check_reviews、acceptance_v2、light×3），更新 b1.md 第 1、2 节与附录 A 的 next sha；B4 不用重做（钩子文件未变）。
- B4 已完成并报调度（见下条）；cutover.sh 已按调度裁定加人工复测（切换第 2 步末、回退⑥），b1.md 第 3 节、5.2 d、第 6 节已写；演练会话已归档、临时工作区与分支已删。
- 无未完成的用户直接请求。在途卡 OPS-1022-OPS-02、OPS-1023-OPS-01 未动（用户要求先做流程重构）。
- 坑：用 Bash `cd` 到别的工作区后，会话主目录会跟着漂，Edit 工具会拒写本工作区文件；改本工作区文件前先 cd 回 one-ops-a5ff89。

## 2026-10-24 12:40 B4 已做（以本条为准）

- B4 完成并已报调度：current 12:32 切 5e3b417d3，12:37 回退 adcac8c22 回读一致；结果在 acceptance/b1.md 第 4 节（原始记录 acceptance/b4/）。第 1、2、4 项过；会话①（打开工作区目录）全过；**会话②（应用自建工作区）的会话钩子读主检出的 settings.json**，切换前测不到新版，已建议 5.2a 加切换后复测，待调度定。
- 待清理：工作区 b4-drill-a、xin-c15a36，分支 claude/one-ops-b4-drill-a、claude/b4-drill-b-5bcd39（均未推送），等用户同意归档两个演练会话后删。下一步：用户验收 → 合入窗口（b1.md 第 5 节）。

## 2026-10-24 下午九次增补（清空接续，以本节为准，覆盖八次增补「待」项）

- **最终 next＝5e3b417d3**（b6bbeb3e8 上合 ops @aee6f5fe4、coordinator @72f12026b；ops 合并提交 f1c851095 里去重了 relevant_change READ_BY_BUILDS 的 RULES.md、roles/，next 曾退回 b6bbeb3e8 重做两次合并，未推送）。已报调度（check_reviews 83 个提交 81 过、只剩 #691 两例外；acceptance_v2 过 5、跳 1、不过 2＝V02/V17a 已知例外；light.sh 连跑 3 次全过；日志 acceptance/b1run-5e3b417d3/，b1.md 已更新）。ops 追加三提交 0460bfae9、f2a9d37d9、aee6f5fe4 复审均过。
- **调度已批准 B4（清空前收到）**：next 5e3b417d3 定为验收版本，f1c851095 去重调度已看过；调度已核除 one-ops 与调度外无会话在跑。接续后**先做 B4**：按 b1.md 第 4 节切 current 到 5e3b417d3，另开两种会话实测，完后 current 回退 adcac8c22 并回读，结果填第 4 节报调度；中途任何一步异常立即回退再报。开做前再向调度确认一次无他会话在跑（清空期间可能有变）。
- **下一步（原）**：等调度安排 B4 演练（intent 120304：调度先确认除 one-ops、调度外无会话在跑；按 b1.md 第 4 节做，完后 current 回 adcac8c22 并回读，结果填第 4 节）→ 用户验收 → 合入窗口按 b1.md 第 5 节（cutover.sh 先 --dry-run；5.2a 切换时填）。若再有子分支新提交：复审过后合 next，重跑 final_checks（check_reviews、acceptance_v2、light×3）。
- **在途卡未动**：OPS-1022-OPS-02、OPS-1023-OPS-01（用户要求先做流程重构）；无未完成的用户直接请求。

## 2026-10-24 中午八次增补（以本节为准，覆盖七次增补同名项）

- **已做**：cutover.sh 不换 mq 也备份旧 plist（标记 mq.kept），回读逐字节比对；第 1 步快进后删主检出 .claude 下 push-bypass.log、context-handoff-state.json(.lock) 残留；末尾提示第 7 步「新会话由 one-coordinator start_session 开、旧会话归档」（intent 000954）。check_reviews 复跑只剩 #691 两例外（reviews-check-20261024-1205.txt）。四子分支已合 next＝**b6bbeb3e8**（policy.json 冲突按键合并，budget_check 取 450）；b1.md 第 2 节全部填好（日志 acceptance/b1run-b6bbeb3e8/），5.2b、5.2c 通过（mqdrill.sh/.log）。mq 自测入口是 `mq.py --self-test`。
- **ops 子分支追加（待审）**：0460bfae9 policy claude_md_max_kb 1.8（首轮＋终审）；f2a9d37d9 .gitignore 删三项（O18）。relevant_change.py:71 注释仍成立（start_sheet.py:346 还指 SESSION_PLAYBOOK），已问调度是否改指 RULES.md 后一并删。
- **待**：C6 到 72f12026b（修 V01/V03）复审过后调度通知→合 next（coordinator 与 ops 新提交）→重跑 check_reviews、acceptance_v2→B4 演练（调度定时机，intent 120304：最终 next 重跑通过后、用户验收前；调度先确认无他会话在跑；完后 current 回 adcac8c22 回读）→填 b1.md 第 4 节、5.2a（切换时）。

## 2026-10-24 上午七次增补（清空接续，以本节为准；六次增补里 cutover／b1／复审口径各条以本节覆盖）

- **产物**（均在 `~/.modernize/psa/coordinator/redesign-1023/acceptance/`，不入库）：`cutover.sh`（切换／`--rollback`／`--rollback-main <sha>`，`--dry-run` 已跑通，shellcheck 过；新 main 无 `scripts/merge/com.forcitis.mq.plist.template` 就打「不换 mq」行并跳过，回退 ① 随之跳过；回退 ③ 多卸 budget_level，调度已同意）；`b1.md` 草稿；`check_reviews.py`（读 closes.json、`_改判必改`，兼容早期格式并标注）；`closes.json`（**调度已签**，84be6ded7 那条待修提交补进再签）；`reviews-check-20261024-1130.txt`。
- **调度已裁定**：必改以修提交关闭；one-merge 子分支不进 b1（intent 222945），核对时排除；#691 两个基点提交、早期格式结论文件按例外处理；补终审的 4 个（05c144e8b、242bb442d、b6dfe2f31、484de869e）**已通过**。
- **接续后先做**：①复跑 check_reviews（命令见 b1.md 1.1），更新 b1.md 1.2 表格（这 4 个与被它们关闭的 610fca580、9e80a8810、6e77ce19a 应转为通过）。②b1.md 第 3、5 节按调度 11:4x 令改：新 mq 冒烟三项（机械入队、head 加提交后拒入队、文档快道）标「随 one-merge 批验，搬到 M 批验收」（依据 intent 222945）；加三条验证「旧 mq 不受切换影响」：a. 切换后 `mq.py status` 正常，launchd 里旧 plist 仍为 loaded，与切换前备份逐字节一致（cutover 不换 mq 时也要先存一份备份比对，**要改 cutover.sh**：跳过分支里也备份，回读时比对并把「跳过」行写进日志）；b. 现行 mq 自测在新 main 上全过；c. 新装的 pre-merge-commit 钩子不挡旧 mq 的合入路径：在临时克隆上用假 PR 分支本地合并演练，不碰远端。
- **O10**：84be6ded7 调度改判必改（O_NOFOLLOW＋fstat 防硬链、handoff_max_kb 按 check_limit、超大文件读上限+1），已转帮手 ops-b1-o7fix（local_9be6f7e2）；它把修 sha 直报调度，调度转我后：补进 closes.json `84be6ded7` 列表→临时 worktree 复跑 start_sheet 自测、gates_selftest、light.sh→报调度。
- **O10 修已提交 f3741815a**（帮手，接 84be6ded7 后），已补进 closes.json（`84be6ded7`→f3741815a，待调度签这一条）；调度派首轮＋终审，结论来后复跑 check_reviews 即可，临时 worktree 复跑 start_sheet 自测、gates_selftest、light.sh。
- **第二批甲待办（调度裁定）**：画像「交接源」退回来源批一保留；C9 删画像目录时一起取消，build_sessions 必填检查与 session_context.handoff_source 同步去掉。
- **之后**：b1 收齐→按 README §5 顺序 `merge --no-ff` 进 next（不含 one-merge）→在 next 上跑 b1.md 第 2 节验证（后台跑）→B4 演练（两种会话）→填 b1.md 待填项→报调度。

## 2026-10-24 上午六次增补（清空接续，以本节为准，覆盖五次增补同名项）

- **10-24 11:25 接续后**：O8 修 c13a35210 首轮＋终审过；O9 390aef980 首轮过；O10 已提交 84be6ded7（待审）。按调度令写好 `~/.modernize/psa/coordinator/redesign-1023/acceptance/` 下 cutover.sh（切换／--rollback／--rollback-main，dry-run 通过）、b1.md 草稿（待填项收齐后在 next 上跑）、check_reviews.py（合暂存前复审核对；初查 reviews-check-20261024-1125.txt）。已报调度请裁定：必改原提交怎样算关闭；one-merge 5 个提交无结论文件；coordinator 8 个结论第 1 行写成「结论：」；4 个守护类提交缺 .final.md。下一步：等裁定与 O10 复审→临时 worktree 复跑→收齐合 next→填 b1.md、做 B4 演练。**11:35 调度已裁定**：必改以修提交关闭（acceptance/closes.json，`_改判必改` 现有 84be6ded7，待调度签）；one-merge 不进 b1（cutover 第 3 步无模板即不换 mq）；早期格式结论文件作例外；4 个缺终审调度已补派。O10 改判必改三条（O_NOFOLLOW＋fstat 防硬链、handoff_max_kb 按 check_limit、超大文件读上限+1）已转帮手，修 sha 帮手直报调度。待调度定：批一冒烟里依赖新 mq 的项怎么验。
- **帮手会话**：[ops-b1-o7fix](local_9be6f7e2-9246-4557-ac42-63447a972fd1)，桌面应用以 ops-fv2-b1 为工作目录开的 opus 会话（本会话写不了 b1：Claude Code 不让写别的工作区，用户已加常驻可写目录，新会话可能不再受限）。b1 的活由它做，我复跑核对后报调度。它的回报以跨会话消息/会话通知到达。
- **b1 提交（接在 adcac8c22 后）**：8f4b2b164 O7 修（终审过，首轮必改→674a5acfc）→ 77f995077 tools_lib.checks（首轮＋终审过）→ 674a5acfc 告警提示 install.sh <sha>（必改→d47a96fe2）→ 88edef207 可信脚本只认 cwd 与 CPD 同一仓库（git-common-dir realpath）、特征表并集（首轮过，终审必改→d47a96fe2）→ d47a96fe2 再修（首轮＋终审过）→ 099341882 O8（首轮必改）→ 390aef980 O9（首轮复审中；c15e29943 为改写前旧版，作废）→ **c13a35210 O8 修（排版 1,095 行、行长 ≤266、231 断言；policy selftest 1,100，intent 090726；待审）**。**O10 帮手在做**（start_sheet 读 state/<会话>/handoff.md，T3 452a2ac），回报直接发调度；接续后收 O10 sha→临时 worktree 复跑→报调度。批一 one-ops 子分支此后只剩各提交复审必改与写 cutover.sh／acceptance/b1.md。O9 已做（light.sh 99 行，删 precheck.sh，ci.yml 轻作业，顺带删 proposal_reads.yaml 74–76 行已知会调度转 one-architecture；actionlint 用 `-ignore 'label "forcitis-one" is unknown'`）。下一步：收 O9 sha→临时 worktree 复跑 light.sh/gates_selftest/actionlint→报调度；再派 O10（start_sheet 读 state/<会话>/handoff.md，T3 工具库 452a2ac 已就绪）。
- **真实钩子目录** ~/.modernize/psa/tools/hooks 已装 adcac8c22（用户批准；RESOURCES 已登记）；b1 会话靠它跑 Bash。**actionlint 1.7.12** 已 brew 装（用户批准，已登记）。
- **调度裁定**：cutover.sh／acceptance/b1.md 要写：主检出快进到新 main、回退⑥同样回退主检出；B4 两种会话都测；回退② current 不动（intent 001330）。第二批预告：onFailure:"block" 并删退出码转换（二甲）、O21 改 permissions.deny（二乙）。
- **调度规则**：修改一律追加新提交，不改写已有提交。O8 修之后由帮手接 O10；两者派出后再找时机清空。precheck.sh 其余读者由调度分派（PR 模板、AGENTS.md 归 C6）。
- **复核口径**：每个 b1 提交用 `git -C ops-fv2-b1 worktree add --detach <tmp> <sha>` 复跑相关自测（3.12 与 /usr/bin/python3 3.9），查 diff 有无删断言；别在本会话 `cd` 进别的目录（会改主工作目录）。
- 在途卡 OPS-1022-OPS-02、OPS-1023-OPS-01 本轮仍未动（用户要求先做流程重构）；无未完成的用户直接请求。

## 2026-10-24 凌晨五次增补（以本节为准）

- **b1 新提交（均已报调度待审）**：O6 必改 242bb442d → O2 必改 cd0014b51 → O3 必改 f756c32fc → O4 必改＋主检出一律拒 e88824f83 → policy tools_lib.dir 5ae1a8e59。
- **用户已定（intent 225957）**：O2 上限 1,100、一行一句；主检出一律拒；codex_stdin_gate 按定稿 O21 删（O3 起已不挂），临时人工规则「codex 带 </dev/null 并禁网」由调度 C6 写 RULES；TEAM-90 大输出提醒改人工规则，归调度。
- **D40 已办**：~/.codex/config.toml sandbox_mode 改 workspace-write（approval never 不变），备份在 ~/.modernize/psa/ops/backup-20261023-flow-v2/。
- **更新**：policy 提交改写为 535d321e2（tools_lib.dir、tools_lib/mq.max_lines_total 1800/2500、gates.max_lines.budget_check 350，替换 5ae1a8e59）；O7 535c03bfe（install.sh 59 行，settings 直调 current，127 两层堵），均已报待审。
- **待调度定**：回退 v1 不能用 pre-flow-v2（无 scripts/gates），子代理建议 5ae1a8e59。
- **第二批待办**：build_sessions 角色文件补非空检查（455de7e15 复审建议）。**二乙验收（调度裁定）**：移走/删掉 start_sheet、session_context、codex_stdin_gate、command_words 后，scripts/gates+hooks 运行代码连同 hooks_manifest.sh（单独文件，不并进 install.sh）须 ≤8 个文件、≤1,500 行；批一 H1 只告警。H23 scripts/merge+queue 超限为批一已知例外（222945 不改 mq），不归 one-ops。
- **再更新（10-24）**：终审/首轮已过：cd0014b51 终审、f756c32fc、e88824f83、535d321e2、242bb442d 首轮。新报待审：O7 修 fcacecd45（M1 重解包、M2 hooks_manifest.sh 清单核对＋只读、M3 无 CLAUDE_PROJECT_DIR 不认 cwd）、O2 修 dca117c93（裸赋值目标变量按导出、xh 整词）、policy budget_check 450 adcac8c22（intent 000537）。回退 v1 调度裁定：git 恢复 settings 旧挂载，不走 install.sh。H1 文件数/行数超（11 个、2,267 行）已报调度，待定 hooks_manifest 落点。
- **下一步（清空接续）**：①读 `~/.modernize/psa/coordinator/reviews/redesign/<完整sha>.md/.final.md` 看 fcacecd45、dca117c93、adcac8c22 的结论，必改就在 ops-fv2-b1 上加「修：」提交（opus 子代理），报 sha；②O8（sonnet，门自测合一 ≤1000 行含 17 例）→ O9（只做轻作业）→ O10。b1 当前 tip adcac8c22，工作区干净。
- **清空前收到、未做（调度 O7 首轮，看 535c03bfe）**：①必改：`install.sh --switch current`（或解析成 current 自身的参数）把 current 链向自己却退出 0，之后全员 Bash 被拒；改为参数先校验成 40 位 sha 或已装版本目录名，不合格退出 1、不碰 current，补自测。②必补测试：复审 7 处变异 4 处存活，其中 2 处要补，见 reviews/redesign/535c03bfeefea67c3098d9753a8e1f2ff55ada84.md。③建议：先校验参数再建目录；并发装同一提交时发现版本目录已就绪就退出 0。M3 已由 fcacecd45 修；p99 依赖字节码缓存判不用修。回退：current 不动、只用 git 恢复 settings（调度在找用户确认定稿文字，cutover 文档先按此写）。做法：opus 子代理在 ops-fv2-b1 上加「修：」提交（install.sh 已 60 行，不许压行，放不下就拆到小文件），报 sha。
- **10-24 接续**：fcacecd45 终审必改（分发器 alarm 抛 TimeoutError 被门的 except OSError 吞，改 BaseException 或 os._exit(2)）、dca117c93 与 adcac8c22 终审通过；三者首轮仍在跑。O7 首轮必改＋必补测试＋两条建议与 fcacecd45 终审 M1 已派 opus 子代理在 b1 上合成一个「修：」提交。**用户已定回退改法（intent 001330）**：定稿回退 ② 改为 current 保持批一版本不动，靠 ④ git revert 恢复旧 settings 与 run_from_main；B4 演练「切回 v1」改为切回上一个 flow-v2 版本——install.sh 文件头随本次修正改；cutover.sh 与 acceptance/b1.md 尚未写，写时照此。
- **10-24 再接续**：b1 新提交 8f4b2b164（O7 修，终审过，首轮必改：告警提示改 `install.sh <sha>`，已派帮手会话 local_9be6f7e2 在 b1 修）、77f995077（tools_lib.checks，首轮＋终审过）。真实安装目录 ~/.modernize/psa/tools/hooks 已装 adcac8c22（用户批准，RESOURCES 已登记），b1 工作区会话靠它跑 Bash。**调度裁定（写 cutover.sh 与 acceptance/b1.md 时照做）**：cutover 加「主检出快进到新 main」，回退 ⑥ 同样回退主检出；B4 两种会话都测（start_session 直接打开工作区目录、应用自建工作区）。**待调度复定**：prod_write_gate 改取钩子输入 cwd 的仓库根——与 O7 首轮必改 2／M3 冲突，已提「特征表并集、可信脚本只认 cwd 与 CPD 一致的仓库」方案，定前不动。第二批预告：onFailure:"block" 并删退出码转换（二甲）；O21 改 permissions.deny（二乙）。
- **在途卡**：OPS-1022-OPS-02（Docker 自动拉起）、OPS-1023-OPS-01（search_path/TEMP 核验、M-29 取证 SQL）本轮仍未动，用户要求先做流程重构；无未完成的用户直接请求。

## 2026-10-24 凌晨四次增补（第九次交接；流程重构第一批，以本节为准，覆盖三次增补同名项）

- **子分支 `claude/one-ops-flow-v2-b1`（工作区 `.claude/worktrees/ops-fv2-b1`，本机，不推送）现有提交**：O2 d4eedf9c1 → O3 e3fe6dcbd → O4 79a2d005e → O5 610fca580 → O5 补 848c57731（relevant_change 列 roles/）→ O5 必改 05c144e8b（目录名优先）→ O6 9e80a8810（settings 只剩 4 挂载、删 12 个钩子文件、start_sheet 加 --hook、Stop 挂工具库 stop_check 且 `; exit 0`）→ policy 三键 cf938271d（retention_days.evidence=7、shell.foreground_max_seconds=60、ci.new_test_repeat=3，守护类要终审）→ build_sessions 指针画像 455de7e15（调度 C4 画像一行指针；标题可选，正文一行恰一个 roles/*.md 指针）。均已报「待审」。
- **复审结论**：O5 及两修正通过；O6、cf938271d、455de7e15 待结论（读 `~/.modernize/psa/coordinator/reviews/redesign/<完整sha>.md`）。
- **下一步（按序）**：①**O2～O4 必改**（调度 10-24 消息，详情见各 sha 的 .md/.final.md）：O2 恢复一行一句（上限暂按 1,100 或拆 shell_words.py，调度在问用户，先展开写）、特征表空且读不到 origin/main 要拦、自测按 id 从 prod_targets.json 取值不留拆段生产值、`PGHOST=…; export PGHOST` 与 `set -a` 后的生产写要拦；O3 分发器预筛不得藏掉门的拒绝（lua、dash、ksh、`. x`）；O4 保留 GIT_DIR/GIT_COMMON_DIR/GIT_WORK_TREE 检查，工作区里 `GIT_DIR=<主检出>/.git` 或 `--git-dir/--work-tree` 提交进 main 要拒。这些是 opus 级，派 opus 子代理写，改完各报 sha（调度派独立 opus 终审）。②O7（opus，install.sh 原子切换、删 run_from_main 与 O3 旧名别名；**O3、O4、O7 同批合入**；验收按实际挂载复测分发器 p99≤100ms）→ O8 → O9（只做轻作业）→ O10。
- **mq 相关搁置（intent 2026-10-23-222945，已报调度）**：O13 整项；O7 中「mq 合入后自动 install.sh」；O9 的必需检查改名；§6 判据「mq 自测」待调度定；不合 one-merge 子分支；旧整合清单 MRG-08/02/04。
- **调度已确认**：搁置标记照办；§6 判据第 2 条改为「现行 mq 自测仍然全过」（新 mq 自测从 fv2-b1 判据删除）——b1 @455de7e15 上 `python3 scripts/merge/mq.py --self-test` 已过（约 31 秒），之后每个提交照跑；附表 C 行随 C6。主检出 reset/checkout 不再拦并入 O2～O4 opus 复审判定，结论出来前别依赖。
- **待用户定（调度在问，先不动）**：主检出在其他分支是否一律拒；codex_stdin_gate 是否留到 O21；O2 行数上限。
- **待调度确认**：SESSION_PLAYBOOK 附表 C 行旧口径随 C6；旧 SESSION_PLAYBOOK 指针（ci.yml、relevant_change、钩子文案）随第二批。
- **本次学到**：①zsh 里 `$c` 不分词，循环跑命令用 eval；②`git commit -F msg -- <路径>` 只提交该路径、不动已暂存的其他改动；③`tail -1` 会只取到断言消息末尾空行，看变异结果用 grep Assertion 或退出码；④python 字符串替换先确认锚点唯一。
- **在途卡 OPS-1022-OPS-02、OPS-1023-OPS-01**：本轮未动（用户要求先做流程重构）。

## 2026-10-24 凌晨三次增补（上下文 21% 第八次交接；流程重构第一批，以本节为准）

- **已完成**：①整合分支备份已推 `claude/one-ops-infra-batch`＝0059c2df7（不开 PR）。②B1 比对脚本 `~/.modernize/psa/ops/b1_compare.py`（不入库）已交调度转用户：查用户输入的凭据是否出现在 security_remediation 两件的任一历史版本里，只输出匹配／不匹配。③本机残留 `.git/hooks/pre-push` 已移到 `~/.modernize/psa/ops/backup-20261023-flow-v2/`。
- **派工**：`~/.modernize/psa/coordinator/redesign-1023/workorders/{README,one-ops}.md`；定稿 `../proposal-final.md`。只本机提交，不推送、不开 PR、不合入。兼容任务号：正文写「门兼容：GOV-1023-COO-01」，后台任务描述也以它开头（GOV-1023-FV2 过不了现行门）。每个提交给调度发「待审 <sha> <项号>」；结论自己读 `~/.modernize/psa/coordinator/reviews/redesign/<sha>.md`（守护类另有 `.final.md`）。改 policy.json 的提交都算守护类。
- **分支**：本机标签 `pre-flow-v2`＝38c98b4ff。整合分支 `claude/one-ops-flow-v2`＝6981e351a（工作区 `.claude/worktrees/flow-v2`，**该工作区当前检出的是 -next**）。暂存分支 `claude/one-ops-flow-v2-next`：基 d71e8977b（通过）→ O1 6981e351a（必改，已由下面的修订补齐）→ 修 502054a9e（通过）→ 660aee138（通过）→ 36bba5d02（首轮＋终审通过，加 review.final_paths）→ 65feb5308（删 final_reviewer_tag，待审）。我的子分支 `claude/one-ops-flow-v2-b1`（工作区 `.claude/worktrees/ops-fv2-b1`）：O2 d4eedf9c1、O3 e3fe6dcbd（均待审，要终审）。
- **在途**：O4（主检出保护只留 pre-commit）已派 opus 子代理在 ops-fv2-b1 上写，清空后它的汇报收不到；接续时看 `git -C ../ops-fv2-b1 log` 有没有 O4 提交，有就读提交正文后报待审，没有就重派。
- **下一步**：O5（sonnet，commit-msg 只追加 trailer，删 task_ids；注意 bg_task_name_gate 依赖 task_ids）→ O6（sonnet，删定稿 2.1 所列钩子，settings.json 只剩 4 个挂载）→ O7（opus，install.sh 原子切换，删 run_from_main 和 O3 加的旧名别名）→ O8（sonnet，门自测合一 ≤1000 行，含 17 例）→ O9（等 one-quality Q3 4d75f99c9 复审通过并合进暂存）→ O10（等 T3；改读 state/<会话>/handoff.md，并让 start_sheet、session_context 改读 roles/）。
- **调度加派（裁定由我改）**：①等 coordinator C4b 合进暂存后，build_sessions.py 接受指针画像（只校验指向的 roles 文件存在），让 build_all --check 回绿；②SESSION_PLAYBOOK 读者改指 RULES.md／roles：relevant_change.py:71、build_work_board.py、scripts/ci 下 3 个、release/deploy_gate.py、留下的 scripts/hooks 文件、docs/modernization 下 5 个 .py；ci.yml 随 O9，start_sheet 随 O10；mq.py 归 one-merge。
- **合暂存**：各会话子分支复审通过（守护类有 .final.md）后由我 merge 进 -next；顺序见 README §5。现有子分支：one-data（b3cd899f7、c8de7ace3、d261f7a1c）、one-quality（Q3 4d75f99c9 等）、one-merge（到 8c21377f6）、one-coordinator（C0～C5，复审中，过了调度会告诉我）。整批齐后整合分支快进到 -next，打 fv2-b1，写 `../acceptance/b1.md`。
- **待调度定**：O2 靠压版式到 796 行能否接受；O3 起 codex_stdin_gate 空档（到二乙）是否补人工规则；O3 最小环境 p99 压线。
- **用户待办**：B1 脚本由用户运行；改本机 Codex 全局为 workspace-write＋never 前须再问用户一次（D40）。
- **在途卡 OPS-1022-OPS-02、OPS-1023-OPS-01**：本轮未动，用户要求先做流程重构。

## 2026-10-24 凌晨二次增补（上下文 20% 第七次交接；以本节覆盖下面同名项）

- **用户新决定（经 one-coordinator，intent 2026-10-23-202455 等）**：基建 PR 全部并进**一个整合 PR**，原 5+ 个 PR 由调度在整合 PR 开出后关闭；规则表（SESSION_PLAYBOOK.md）改动不放整合分支，走调度的单独小 PR（intent 191447 第〇项）。**等「目前合入结束、队列清空」的通知后才从最新 main 并/变基并开草稿 PR；之前一律不推送**（#685、#688、#690 在合，#685 合完就不用再并）。
- **整合分支（本地，未推送）**：工作区 `../gov-batch`，分支 `ops-infra-batch`（远端名定 `claude/one-ops-infra-batch`）。已用 `git merge --no-ff` 带入原提交：#683（claude/one-ops-qua06-s1 @f35007f41）、#684（claude/one-1a-par-gen-out-openapi @8354220ee，含 --needs-api 退出码 10 小必改）、#654（claude/one-quality-gate-approve-post）、#669（claude/one-merge-mrg07-02）、#686（claude/one-merge-mrg10-cancel）、#677（claude/one-merge-mrg10-openapi）、#689（claude/one-ops-gh-timeout）。冲突解法：#654 的自测改动搬到 scripts/hooks/tests/（approve_post_gate 自测抽出、pre_tool_dispatch 自测加 2c）；#677×#669 在 mq.py 三处合并；mq_selftest `_precheck_wiring` 的 build_all 次数断言 1→2（#684 两分支）。SESSION_PLAYBOOK 改动已撤出（diff 在 /tmp/ops-playbook-for-coordinator.diff，已发调度）。
- **已写提交（整合分支，已报调度）**：QUA-03 批准门放宽 e5c15832d（one-quality 可发「one-quality 批准入队 head <sha>」，前缀后一个空格）；QUA-06 ② 的两项（node_handoff.min_percent 20→30、删 prod_write_gate 的 --self-test 例外）8bba35c64。
- **还没写（按序，一项一提交，每项报调度提交号）**：①QUA-06 ② 余项与 ③（目标 ≤12 文件/≤2500 行/≤150KB；当前 22 文件/4188 行/272K）：pre_tool_dispatch 出错分级放行、prod_write_gate 规则表 ≤300 行（现 917）、命令解析并入 command_words、codex_stdin_gate/bg_task_name_gate 改规则表、mailbox 旧门注释删、#674 归还两条、快照 3 合 1、precheck_before_pr 并入 pre_push_gate、context_handoff 瘦到约 150 行、CI 上限检查；②MRG-08 合批（扩展 scripts/queue/merge_train.py：select_batch 选批——队首起至多 3 个互无文件交叠、互无 after 依赖、未知文件集不合批；verdict 判定——绿合入、红二分、head 变出队重建；plan/replay 子命令；merge.log 在 `git rev-parse --git-common-dir`/merge-queue/merge.log，近 3 天回放估算贴 PR；`docs` 子命令加「relevant_change 判为 code 的文件拒收」；自测五种情形＋破坏验证）；③MRG-02 合入通知与回填；④MRG-04 在制 PR 上限；⑤A09 文档合集自扫（原 MRG-11）。卡文件在 docs/modernization/tasks/ 里按卡号找（MRG-08 卡已读）。
- **#685**（TEAM-26 放宽）head 725161fe7 在合入，不并。**#691**（架构）也改 build_all.py，等整合 PR 合入后由它解冲突。
- **本次学到**：①整合多个分支用 merge --no-ff 保留原提交，冲突逐个解；②自测搬迁后别人的 PR 若改了旧位置自测，要把改动搬到 tests/；③gapscan.APPROVAL 现要求 head 后 12–40 位十六进制且 `[ \t]+head`，旧样例 abc1234 不再匹配；④zsh 下 sed -i 换含斜杠/括号的文本易错，用 python 改；⑤跨会话发消息先用 ToolSearch 加载 mcp__ccd_session_mgmt__send_message。
- **临时工作区**：../gov-batch（整合，保留）、../gov-q6（#683/QUA-06 旧）、../gov-q2（#684）、../gov-bg、../gov-gt、../gov-b2、../gov-654；整合 PR 开出且原 PR 关闭后可清。

## 2026-10-24 凌晨增补（上下文 25% 第六次交接；以本节覆盖下面同名项）

- **本轮已推送（均等复核/CI，别重做）**：**#689**（gh 超时，claude/one-ops-gh-timeout，工作区 ../gov-gt）head 9affd1e9e——bounded() 先 TERM 后 KILL、默认 120 秒（`export SHIP_GH_TIMEOUT="${SHIP_GH_TIMEOUT:-120}"`，alarm 的 `||` 优先级坑）、ship_test 22 项；顺手补了 new_adr/task_backfill 超时、session_daily 首次超时后其余调用立即失败、pr create 超时提示；等 one-quality 复核。release.yml:80「查 tag 超时当没有 tag」是老问题，待单独开卡。**#685**（TEAM-26 放宽，../gov-bg）head f6779f7cd，one-quality 复核通过，等轻量 CI 全绿 one-coordinator 批。**#684**（QUA-02 第二段，claude/one-1a-par-gen-out-openapi，工作区 ../gov-q2）head 67b26c16c：opus 三条必改已改——build_all 去掉全局 OpenAPI 前置、加 `--without-api`（跳 panorama_build/notes_build/object_freeze_check）与 `--needs-api --changed 文件`；mq.py 两次生成与 --check 带 `--without-api`，mq_selftest 替身加「缺 openapi 且无参即失败」，两处变异已验证；precheck --pre-push 按 `--needs-api` 免生成物（端到端已验证）；清单重扫 50 个贴 PR 正文；RELEASE.md §2 第 5 步写明构建机需 Docker；PR 正文写了「#677 先合，本 PR 合入时删它的 openapi 三方合并整段」。等 opus 复核（调度转）与 CI heavy 档。
- **#683**（QUA-06 第①步，claude/one-ops-qua06-s1，工作区 ../gov-q6）head 5f11a8fcc 已复核通过；**调度裁定：#679 先合，合入后把 main 合进 #683 解冲突（两边都改 scripts/hooks/precheck_before_pr.py），一次推送报新 head；#679 合入前别推**。#679 head 052e8639b 在队。
- **待做**：①#683 等 #679 合入后合 main 解冲突（只解冲突，one-quality 快速核）；②QUA-06 第②步（在 #683 上叠分支 `mq.py enqueue --after 683`）：pre_tool_dispatch 出错分级放行、prod_write_gate 规则表≤300 行（**必删**：「scripts/hooks 脚本带 --self-test 放行」例外及其自测用例）、命令解析并入 command_words、codex_stdin_gate/bg_task_name_gate 改规则表、mailbox 旧门注释删、#674 归还两条；**并入**：钩子配置统一到 TEAM-05 阈值（TEAM-14 与 context_windows.json 还写 20%，调度跟着改规则表）；③第③步见夜增补；④MRG-08（扩展 merge_train.py，依赖 #669，`enqueue --after 669`）；⑤QUA-03 批准门放宽（#654 合入后另开小 PR）；⑥QUA-06 瘦身计划已发调度（三步，预计各约 1 工作日；目标 ≤12 文件/≤2500 行/≤150KB）。
- **本次学到**：①推送/提交要分两条 Bash；②precheck 的 shellcheck 会拦 `A && B || C`（SC2015）；③评审复现靠假命令：忽略 SIGALRM/SIGTERM 的假 gh；④precheck 端到端要用 `precheck.sh --pre-push <基线>` 且工具改动与文档改动分两个提交，否则 changed 含工具文件；⑤extract.py（/private/tmp/claude-501/q6/，会丢）可把 main 新版 self_test 重新抽取到 tests/，抽完需把 `globals()[..]` 改 `_mod.__dict__[..]`、还原 Usage 行与 `__main__`；⑥mq 自测入口是 `python3 scripts/merge/mq.py --self-test`；⑦新建临时 worktree 前端依赖/生成物缺失会让 precheck 在 backend/前端相关步骤失败，纯脚本改动推送无碍。
- **临时工作区**：../gov-gt（#689）、../gov-bg（#685）、../gov-q2（#684）、../gov-q6（#683）、../gov-b2（#679）、../gov-654（#654）；旧的 ../gov-667/668/fix/rfm 可删。

## 2026-10-23 深夜增补（上下文 21% 第五次交接；以本节覆盖下面同名项）

- **已推送（等 one-coordinator 批/复审）**：#679 head 052e8639b（必改已改）；#654 head bfa67700f（解冲突，已批在队）；**#683**（QUA-06 第①步，claude/one-ops-qua06-s1，工作区 ../gov-q6）——#667 合入后与 main 冲突（DIRTY），**one-coordinator 要求先别推**，等 opus 复审结论（按 09ec2326b）到后，改动与合 main 解冲突放在同一次推送，再报新 head；**#684**（QUA-02 第二段，claude/one-1a-par-gen-out-openapi head 5cf5c8bac，工作区 ../gov-q2，已合 main、本地生成成功，等 opus 首轮）；**#685**（TEAM-26 放宽，claude/one-ops-team26-relax head fe419d604，工作区 ../gov-bg，分支 ops-team26）等复核 CI 绿就合。
- **待做（用户决定 intent 2026-10-23-191447，调度转）**：①gh 命令加超时（单独 PR，从最新 main 切）：scripts/queue/merge_train.py 的 run()（无 timeout，gh pr view/close/create）、scripts/ops/session_daily.py `_json`、scripts/release/ci_passed.py `gh_json`、scripts/release/ship.sh 的 `gh api`（用 `perl -e 'alarm 120; exec @ARGV'` 包，macOS 无 timeout）；mq.py 的 sh() 已有 180 秒、ci_recurrence 已有、handoff_snapshot 已有 10 秒。②**今晚要回 one-coordinator：QUA-06 瘦身计划**（scripts/hooks 当前约 25 个文件、6255 行、448KB，上限 ≤12 文件、≤2500 行、≤150KB；每步内容与预计合入时间；自测与运行代码分开，#683 已做自测；第②③步见下面夜增补，运行代码目标：dispatch 出错分级放行、prod_write_gate 改规则表 ≤300 行、命令解析并入 command_words、codex_stdin_gate/bg_task_name_gate 改规则表、mailbox 旧门注释删、快照 3 合 1、precheck_before_pr 并入 pre_push_gate、context_handoff 瘦到约 150 行；#683 后行数基线 4330 行/220KB/22 个顶层文件）。③预告：规则表第六步完成后，开工单只注入「PLAYBOOK_CORE 路径＋本角色附录路径」，附表 B 守护索引单独成文件归 one-ops 维护。
- **顺序（调度定）**：第 1、2 项（#685 已开；gh 超时待做）→ #683 收尾 → QUA-02 第二段（#684）→ QUA-06 第②步（可在 #683 分支上叠分支，`mq.py enqueue --after 683`；别碰 #679、#667 已改的钩子文件区段）→ MRG-08（合批，扩展 scripts/queue/merge_train.py，依赖 #669，PR 用 enqueue --after 669；完成标准照卡：自测五种情形、用 merge.log 回放估算、破坏验证）。MRG-04 等 mq.py 相关 PR（#678、#677、#669）合完。QUA-03 批准门放宽（调度或 one-quality 的批准评论都可作入队授权）：#654 合入后另开小 PR。
- **本次学到**：①zsh 里 `--include=*.py` 不加引号会报 no matches；②BSD sed 的 `s#…#` 带 `;` 易错，改写文件用 python；③Write 工具不能写别的工作区，用 scratchpad 写好再 cp；④钩子自测搬迁用 AST 改写（/private/tmp/claude-501/q6/extract.py 会丢）；run_all.py 在「分支 main 检出」副本里复现过 #681 同类问题；⑤新工作区推送前要 `pnpm install --frozen-lockfile --offline`，QUA-02 后还要 `bash scripts/generate-api.sh -o`（约 30 秒）才能过 build_all --check；⑥推送报 500 时等 20 秒重推。
- **临时工作区**：../gov-b2（#679）、../gov-654（#654）、../gov-q6（#683）、../gov-q2（#684）、../gov-bg（#685）、../gov-667/668/fix/rfm（旧，可删）。

## 2026-10-23 夜增补（上下文 20% 第四次交接；以本节覆盖下面同名项）

- **治理实现排序（用户 intent 2026-10-23-165917，加快推送合入为先）**：#668 已合入 → #667 已推 5bdb2d2e2 等轻量 CI 绿 one-coordinator 批 → **QUA-04 第二批 PR #679**（claude/one-ops-qua04-b2 @f4b9fe822，gh pr create 钩子改跑 --pre-push；heavy_slot 复量与 build_all 去重结论：不改）等 one-quality 验收 → **QUA-06 钩子瘦身三步（下一步）** → QUA-02 第二段（分支 claude/one-1a-par-gen-out-openapi @8a8629293，已转本会话）→ MRG-04。
- **QUA-06 基线（main @e5d9c2b42）**：scripts/hooks 27 项、6747 行、417KB；目标 ≤12 个文件、≤2500 行、≤150KB。方案三步：①自测整体挪 scripts/hooks/tests/（约 2500 行）+ CI 必需作业「hook and CI-script self-tests」改路径，零行为变化，并随 PR 给 context_handoff 清单（已发 one-coordinator）；②pre_tool_dispatch 出错分级放行（只拦 git push、gh pr create、可能写生产）+ prod_write_gate 按需加载并改规则表 ≤300 行 + 命令解析并入 command_words + codex_stdin_gate/bg_task_name_gate 改规则表 + mailbox.py 旧门注释删 + #674 归还两条（自测补 git 失败用例、复查多保留的 assume-unchanged 检查）；③快照 3 合 1、precheck_before_pr 并入 pre_push_gate、TEAM-109 ①③ CI 检查（上限 ≤12 文件/2500 行/150KB 与共用函数唯一）。context_handoff 裁定：删 pilot_sessions、状态声明残留、Bash 超大提示、会话未识别提示；留节流；两档按角色（调度 50/60 其余 30/40）、提醒文案合一、越线挡停留；节点收窄为「开 PR」「任务完成/合入」两个，目标运行代码约 150 行；PR 描述写删了哪些节点与提醒预期下降；验收归 one-quality；冻结只删不加。
- **22:00 后更新**：①main push CI 红的急修 PR **#681**（claude/one-ops-dispatch-selftest-fix @aae49e26e，自测 cwd 改非仓库目录）等 one-quality 复核；②**#679 必改未做**（one-coordinator 定）：precheck_before_pr.main 开始时 tree_state 为 None 就不要调 record_pass(checked=None)——否则检查期间工作区变干净会给没检查过的树留标记；补自测（开始脏/读不到、结束干净 → 不留标记），在 ../gov-b2（ops-qua04-b2）上做，推送报 head；③#667 head 5bdb2d2e2 等批；④之后 QUA-06 第①步。临时工作区增 ../gov-fix（急修，可删）。教训：push 事件检出分支 main，PR 事件是游离合并提交，自测不得依赖当前分支或 cwd=仓库根。
- **临时工作区**：../gov-668（ops-668-tmp，#668 已合，可删）、../gov-667（ops-667-tmp，跟踪 #667 分支）、../gov-b2（ops-qua04-b2，跟踪 #679）；开 QUA-06 第①步从最新 origin/main 另切。
- **做法与坑**：推送/开 PR 的 Bash 整条命令会先被 PreToolUse 预检拦住，合并解冲突与提交要和推送分成两条命令；precheck.sh 里提到 docs/modernization 路径会触发 relevant_change 自测「docs read by builds」，新增读文档路径要进 READ_BY_BUILDS；`py` 是脚本运行器不接 -c；main 上曾丢 LAST_LOG（#668 已补）；--reads-docs 只认 READ_BY_BUILDS。
- **仍在**：OPS-1022-OPS-02、OPS-1023-OPS-01 见下面各节（#662 余项、RDS 12-01 续费提醒、dev 重建、ClamAV 联调等不变）。

## 2026-10-23 傍晚增补（上下文 20% 第三次交接；以本节覆盖下面同名项）

- **#662 已合入 main**（V6c 按 Codex 必改改完，带标签两行，含库级 ALTER ROLE 覆盖反例；dev 复跑 V6c 两行 t）。
- **用户新定（经 one-coordinator）**：one-ops 兼做治理实现。在做上限 3 张，顺序：**GOV-1023-QUA-04 预检提速（第一优先，#668）** > QUA-06 钩子瘦身（#659 已合入，余项：删 stop_status_gate 的 Stop 拦截、画像按开工单 8KB 限体量、context_handoff.py 冻结只删不加）> QUA-07 交接（#667，等 opus 结论；另加调度阈值 50/60）。挂起：QUA-05、QUA-08（分支 origin/claude/one-1a-par-cmdparse，已答：带理由 --no-verify 一律拒、COO-17 余项纳入）、QUA-01；MRG-04 后排。one-quality 的 13 张挂起实现卡与 QUA-03（#654 复审中）已转我名下，均挂起。SSOT 归属 TEAM-108：资源正本 phase1a/infra/RESOURCES.md 归我维护。
- **#668（QUA-04）**：分支 claude/one-quality-precheck-mark，我已推 head **305700cb0**（在 one-quality 的 53f9bdf7e 上加）：①`build_all.py --check --changed 文件`（自测只跑改到的工具；改动全在 docs/modernization 的 architecture/、glossary/、rules/ 之外时，跳过 panorama_build、build_terms、notes_build、object_freeze_check 四个慢生成器；自测已加）；②precheck.sh --pre-push 的改动范围按「上次推送以来且异于 main」算；③删本地 21 个钩子/守护 self-test（原约 85 秒），缺的补进 ci.yml 的 assertion-drift 作业（precheck_before_pr、commit_msg_gate、bg_task_name_gate、heavy_slot、handoff_selftest、session_context、start_sheet、stop_status_gate、mailbox）；④Java 轻量单测只在改了 backend/ 时跑；⑤run_step 输出每步耗时。**实测**：纯文档推送原 106 秒→7 秒（两次）；典型脚本改动推送原 229 秒（自测 125＋单测 91）→ 去掉守护自测与单测后主要剩 build_all（本分支因改了 build_all.py 全跑自测 133 秒，普通推送不会）。原始逐项耗时：mq.py 自测 40、panorama 自测 23、session_daily 7、merge_train 7、四生成器合计 29、pre_tool_dispatch 自测 22、handoff 13、heavy_slot 7、shellcheck 6–9、acceptance_guard 7。
  **还欠（17:00 承诺，未做）**：①把「每项检查、耗时、去留（留/移 CI/删）」表附进 #668 描述并报 one-coordinator（上面数据即素材；mq.py/merge_train.py 自测只放 CI 前先问 one-merge 在队列哪步补跑）；②第二批（承诺 18:30）：precheck_before_pr 并入或删（gh pr create 中位 127 秒）、heavy_slot 复量、CI 里 build_all --check 去一遍（目前 ci.yml 只见一处，查队列/他处是否另有）；推送前总预算 60 秒；新门要写成本与下线条件，由 one-quality 验收；等 opus 首轮结论（one-coordinator 转）并入。
- **#672（claude/one-ops-rfm @8e6bd2de9）**：run_from_main.sh 修复（.DS_Store 排除；缺工作区副本时跑 main 已提交内容快照，不再让全员 Bash 失败关闭），已开 PR，等 CI 与 one-quality 验收；已报 one-coordinator。
- **#665**：已解冲突，新 head 3f520f1d0，等 one-coordinator 重批。**#659** 已合入。
- **本会话的临时工作区**：../gov-668（分支 ops-668-tmp，跟踪 #668 远端）、../gov-rfm（claude/one-ops-rfm）；git stash 里有一条 ops-668-wip（我建的，已无用，按 `git stash list --format='%H %gs'` 找 SHA 后删除）。
- **坑**：①gh pr diff 要在 git 仓库里跑；②codex exec 在非 git 目录要 `--skip-git-repo-check`；③后台命令 description 须写成 `<任务号或#PR> <名> (<执行者>)`；④cross-session 发消息用 `mcp__ccd_session_mgmt__send_message` 的 `session_id`；⑤分支已被别的工作区检出时，另起本地分支再 `git push origin HEAD:<远端分支>`。

## 2026-10-23 晚增补（上下文 21% 时第二次交接；以本节覆盖下面同名项）

- **已完成**：#639（docker 自动拉起脚本）、#640（ci.yml 检查，Codex 复核通过）均已合入；launchd 代理 com.forcitis.docker-watch 已装并运行（90 秒一轮）；**Docker 停机演练经用户撤销**（「去掉这个演练，我不关 docker」），不再监视空档，runner 会话能否 kickstart 未验证；RESOURCES 与任务卡 OPS-1022-OPS-02 已登记。nightly-legacy-it.yml 加同类检查：未做，需要时另开 PR。
- **生产特征表**：`phase1a/infra/PROD_FINGERPRINT.md`（含第五节两台生产 ECS 只读通道，仅 SDK 工具 b5_legacy_run/b5_evidence_scan/c14_run 只读核实放行），已发 one-quality（#653 用）。
- **#662（code 道，OPS-1023-OPS-01，分支 claude/one-ops-sql @d00c5a9f1）进行中**：opus 首轮「改后通过」，全文 `~/.modernize/psa/coordinator/reviews/pr662-opus-1.md`。必改①已改并推送（42 个取证 SQL 文件头 ADR-0037 改为第 5 项第 4 条）。**必改②未做：改 PR 正文**，补三项并分开写 check.sh 跑在本机容器、verify 跑在 dev：
  - dev V6c 两行结果（本会话 2026-10-23 在 dev ECS 经云助手用 one-dev-readonly.pgpass 只读查询）：`t`、`t`（TEMP 缺失 与 search_path 首项为 pg_catalog 的整串匹配）；
  - 本机容器（postgres:18，跑完自动回收）跑 `c3_dev_db_check.sh`：退出码 0，18 条 PASS、0 条 FAIL，含新增的「runtime and readonly roles hold no temp; runtime search_path starts with pg_catalog」；
  - 用户批准出处：intent `docs/modernization/intent/2026-10-22-155217-one-coordinator-都做.md`（批准本机起 postgres:18 跑 check.sh、dev 执行 ALTER ROLE one_dev_app SET search_path = pg_catalog, platform_approval 并重连、dev 只读查 TEMP）。
  还要做：①把 opus 全文贴到 #662 评论；②改 PR 正文后，把 PR diff 交 Codex（`codex exec -m gpt-6.1-sol -c model_reasoning_effort=high -s read-only --ephemeral -o <文件> "<提示>" < /dev/null`）复核并贴 #662；③报 one-coordinator head。opus 建议项（小单元格聚合门槛、库名守卫 ELSE THROW、c3 增量脚本留档、c9 环境守卫、V6c 带标签、脚本迁 scripts/ops/db）：不在本 PR，需要时另开任务，可在 C-3-dev-db.md 执行记录补一行 dev 的 ALTER 留档。
- **dev 重建**：#644 改了已发布基线，含它的发布部署 dev 前必须走 `runbooks/dev-db-rebuild.md` 重建（先问用户）；重建即清空 event_publication，无需另查大写 processKey。
- **其他仍等**：RDS 12-01 续费提醒；新 rc 与重建 dev 等调度通知；WORKFORCE_SYNC_ENABLED 保持 true（one-coordinator 10-23 确认）；ClamAV 联调。
- **待命口径**：用户 10-23 定 one-ops 为「随时待命」，15 分钟内响应，任务交 Codex 或后台子代理。
- **坑**：zsh 下变量里的多行路径不会按空白拆分，用 `xargs`；macOS 没有 `timeout`；pre-push 的 precheck 第一次偶发 FAIL（生成物），重跑即过；`/private/tmp/claude-501/ra.sh` 是云助手脚本模板（RunCommand + base64 + 轮询），会丢。

## 2026-10-23 增补（上下文 21% 时交接；以本节覆盖下面的同名项）

- **#637（迁入角色 history_loader，OPS-1022-OPS-03）**：opus 首轮与终审均已过并改完，one-coordinator 已批入队；dev 库已补建 `one_dev_history_loader`（用户经收件箱批准，云助手 t-hz070ulib73ylts，已登记 C-3 手册）。V13 待下次含本 PR 的发布后在 dev 跑；prod 无实例。
- **OPS-1022-OPS-02（Docker 自动拉起）进行中**：PR #639（scripts/ops：docker_ensure.sh、自测、docker_watch_install.sh、plist 模板、RESOURCES 行）分支 claude/one-ops-docker-watch @9a13cb984，已按 opus 首轮必改改完（--ci 不拿锁、假 launchctl 真跑代理的并发自测、不再 pkill、nocli/paused 告警、安装脚本），自测全过，**还没报 one-coordinator、等 sonnet 复核**；PR #640（ci.yml，分支 claude/one-ops-ci-docker-check @4f5c08e3f）**还没跟改**。
- **下一步（按序）**：①报 one-coordinator：#639 改完 @9a13cb984，并把 `~/.modernize/psa/coordinator/reviews/pr639-640-opus-1.md` 的结论贴到 #639/#640（一条 PR 评论）。②#640：#639 合入后 rebase；把「docker is running」挪到 docker events 记录器之前（或成功后重启记录器）；在 CI 的「release and deploy scripts self-test」步加 `shellcheck scripts/ops/*.sh` 与 `scripts/ops/docker_ensure_selftest.sh`；复核 `timeout-minutes: 5` 与脚本最坏耗时（--ci 约 10+120+14≈145 秒）；失败文案已带「已暂停/未安装」；注释里「10-22 22:35-22:50」改指任务卡；nightly-legacy-it.yml 同样加（可另开 PR）。③两 PR 合入后：`scripts/ops/docker_watch_install.sh` 装 launchd 代理（卡与 intent 2026-10-22-230334 已授权，one-coordinator 10-23 确认不进收件箱）。④演练：挑合并队列空、无 CI 在跑时退出 Docker，两分钟内应自动恢复；**同时验证 runner 会话（SessionCreate）能 `launchctl kickstart gui/501/com.forcitis.docker-watch`**（只靠推断，没验过）。⑤RESOURCES 登记装机与演练结果，演练记录回填任务卡，报 one-coordinator。
- **仍等 one-coordinator 示下**：dev one.env 的 WORKFORCE_SYNC_ENABLED（现 true，要求不能是 true）。
- **本会话踩到的坑**：①macOS 的 `seq 1 0` 会输出 1、0（倒数），循环次数为 0 时别用 seq；②pre-push 钩子跑 backend 单测时 ClamdScannerTest 的超时重试用例偶发失败（已转 one-platform-file），重推即过；③云助手连 dev RDS 要 `PGSSLROOTCERT=/etc/forcitis-one/rds-ca.pem`，pgpass 第一行取主机；模板 `/private/tmp/claude-501/ra.sh`（会丢）= RunCommand + base64 + 轮询 DescribeInvocationResults；④`git push` 被中断时先 `git status -sb` 看是否 ahead 再重推。

## 2026-10-22 增补（上下文 46% 时按钩子交接；以本节覆盖下面交接节里已变的项）

- **已完成**：ClamAV dev 装好并通过加密项必验与耗时测量、健康检查与 C-7 告警及三类演练（卡 OPS-1021-OPS-02；还差与应用侧联调与 MaxScanTime 复核，等 one-platform-file 约时间，全部做完才报「建成」）；撤 `psa_one_legacy_ro`（卡 OPS-1022-OPS-01，已完成，视图 v_psa_one_legacy_todo 仍在，删与否另请示；dev one.env 的 …LEGACY_INBOX_* 已删）；dev `one_dev_app` 的 search_path 改为 pg_catalog, platform_approval 并重启（c3/c9 脚本与 verify V6c 已改，本机容器检查通过）；dev 核 TEMP：四个账号均 false（#547 待办的 TEMP 核验已完成，三张日历表的 REVOKE 由 #547 自带）。
- **取证（均已登记，结果在本机 `~/.modernize/psa/ops/adr0073/`，仓库外；用完删，删后补登记）**：ADR-0073 U1 法人归属（老系统与 A8 实质单一法人）、DAT-1021-ARC-01、B-31 用印附件（三步）、入职日期口径、M-29 前后值 Q 批与 B 批（m29*.txt 的删除日期 2026-10-29）。SQL 都在 `handoff/B-5-dba/evidence/`。
- **WORKFORCE_SYNC_ENABLED**：dev one.env 现值 true（2026-10-16 为 A8 组织同步启用）；one-coordinator 要求不能是 true（W2 PR-B 入队前提）。生产环境尚无新系统实例（ECS i-bp122xlpar8w2uylmal2 是 dev），所以 prod 无此项。是否改回 false 等 one-coordinator 示下，改了要重启。
- **待办仍在**：12-01 续费提醒；新 rc 与重建 dev（等调度）；ClamAV 联调；工作日历投产写入前置；c14_run.py 自测 PR；回滚脚本 DROP ROLE IF EXISTS 与 check.sh 旧库用例（OPS-007）；#547 合入后的 dev 部署前置（platform_file 等 schema 的建库授权，见在途表）。
- **本会话踩到的坑**：①取证工具（`b5_evidence_scan.py`）语句超时固定 300 秒，跨库联接要给两侧加 `COLLATE DATABASE_DEFAULT`（排序规则不同会报错，失败时工具只说 Failed，不给原因，先拆小查询二分）；②工具失败退出时服务器上留的临时目录要靠下一次成功运行清理，别忘；③跨库查询在 IF DB_NAME() 里整体外包；④`SUM(CASE WHEN EXISTS(...))` 在 SQL Server 非法（聚合里不能带子查询）；⑤云助手脚本模板：`/tmp/ra.sh`（本会话临时，会丢）= RunCommand + base64 + 轮询 DescribeInvocationResults。

以 RULES.md 为准；二甲删除

## 交接（2026-10-21，上下文 83%；分支 origin/claude/one-ops-r56，文档分支，不开 PR，提交号以 `git log -1` 为准）

**未完事项与触发条件**

1. **RDS 续费（dev/测试库，`pgm-bp13vak0sew3m003`）**：已续 1 个月（订单 2006474717310288，¥1,456），到期 **2026-12-19 00:00 北京时间**；**自动续费已关**（回读 Duration 0）。**12-01 提醒：把下月续费方案报 one-coordinator**（用户说这件影响 dev，可报用户）；不续则断库。高可用降单节点官方文档不支持、控制台没法试；单节点新购询价（40 GB ESSD，月）2C4G 405、2C8G 520、4C8G 771，原高可用续 1,456；降配走「新买基础版并重建 dev、旧实例到期不续」另评估。B、C（生产 RDS 8C32G/200G 3,084/月；生产 ECS 802/月）**不下单**，条件：首次按量演练跑通、规格实测、距投产≥4 周，一次按年下（B 省 8,868、C 省 2,650；B 带 200 GB 自动扩容，备份与 WAL 归档月成本另列）。付款纪律：付款前报订单号、实付、规格、周期并等确认，订单号或付款方式变了（含取消重下）也先报（错题本 OPS-010）。
2. **dev 现状**：rc.6 在位健康；`one_dev_app` 连接上限 25（预算 31，脚本在文档分支）；`forcitis_one` 加了库注释、`it_portal_runtime` 上限 5（该库仍有 172.16.10.52 老系统服务器的活动连接，不 drop）；ECS i-bp122xlpar8w2uylmal2 到期 2027-10-15（RESOURCES 已记，提前 30 天提醒）。
3. **新 rc 与重建 dev**：等调度通知（A 线 #489 列改名、F 删老待办合入后）。重建走 `runbooks/dev-db-rebuild.md`（回滚脚本对旧库缺角色不幂等，OPS-007）；装 rc 的手工路径与脚本这次会话的临时目录会丢，按 OPS_CONTEXT 与剧本重来；授角色要用户扫码登录一次。
4. **工作日历**：dev 已导入 2016–2026 共 272 条（临时，按老系统 `workday` 推出），读回一致。对照国办官方通知差异 17 条与老系统工时核查见 `handoff/WORK_CALENDAR_AUDIT_2026-10-21.md`；用户口径：dev 不修；这 16 天以工时核查为公司加休依据，2023-04-23 按官方补。**投产用「官方＋公司加休」表**已出，在本机 `~/.modernize/psa/ops/work-calendar-prod-2016-2026.csv`（及 json、cancelled、README；273 行，不进仓库），投产首次写入时用；写入前要 `work_calendar_days` 加 `basis` 列、OD 补 11 行公司安排的书面依据、2027 年清单另出。dev 导入包（旧）`work-calendar-2016-2026.json` 同目录。
5. **ClamAV**：dev 已装 clamav 1.4.6 并通过加密项必验与耗时测量（2026-10-21，详见卡 OPS-1021-OPS-02 测量记录与 RESOURCES）。健康检查与 C-7 告警及演练已完成（one-clamav-check.timer）；运维侧还差：与应用侧联调（EICAR 写入→检测→隔离、停 clamd→拒绝＋告警）、MaxScanTime 复核；生产实例上线时同步装；应用侧归 one-platform-file，ADR-0060 补篇归 one-architecture。
6. **NAS 与 WORM**：NAS 取证暂停并已回收（权限组与新挂载点已删，未读内容）；2.04 TB 即全部文件；估价 `handoff/NAS_WORM_COST.md`（冷类型单价未核实，B-13 前到购买页核对；锁定不可逆，先演练再锁）。
7. **待做代码类**：`c14_run.py` 自测 PR（须与 main 逐字节一致、opus 首轮复审）、回滚脚本 `DROP ROLE IF EXISTS` 与 check.sh 旧库用例（OPS-007）；可交 Codex（卡写四项），取证、部署、凭据类不交。

**本会话产出的文档（都在 `docs/modernization/phase1a/infra/handoff/` 与 `evidence/`）**：PROD_AND_DRILL_ENV_ASSESSMENT、PROD_RUNNING_COST、MALWARE_SCAN_CANDIDATES、NAS_CONTRACT_EVIDENCE_PLAN、NAS_WORM_COST、ORDER_PROPOSAL_2026-10-21、RDS_DEV_ASSESSMENT_2026-10-21、WORK_CALENDAR_AUDIT_2026-10-21；取证结果在 `B-5-dba/evidence/`（B31P 五批、附件大小、迁入数据量、日历核查）；INFRA_RUNBOOK 已按 ADR-0055 对齐（§5 有过渡说明，现有 rc 仍读 A8 变量，别删 dev 的 one.env）。

**工作方式提醒**：本机取证工具要从仓库检出、与 origin/main 逐字节一致才能运行（`b5_evidence_scan.py <输出> <sql>`，两库各跑一次，SQL 用 DB_NAME() 守卫；只出聚合，出值类结果写文件前必须对全部出值做模式扫描，错题本 OPS-009）；`git push` 的钩子会在命令开头按当时的 HEAD 跑 precheck，所以改文件、提交、推送要分成不同的调用；会话 scratchpad 里的临时脚本会丢。云助手执行脚本的 `ecs.py` 之类也在 scratchpad（会丢）：用 `aliyun ecs RunCommand` + base64 内容，输出 24 KB 上限，gzip+base64 分块。

## 资源

| 名称 | 标识 | 状态 | 依据 |
|---|---|---|---|
| 本机 Python（SDK） | ~/.modernize/psa/ops-venv，按 [ops-venv-requirements.txt](ops-venv-requirements.txt) 安装；B-5 工具用它的 python 运行，不借其他会话的 scratchpad | 用 | — |
| 阿里云账号 / 本机凭据 | 1980847222980247 / profile forcitis-production | 用 | — |
| 新 ECS | i-bp122xlpar8w2uylmal2，实例角色 forcitis-one-ecs-archive | 用 | C-4 |
| 老系统应用服务器 | i-bp1b34hcl0g1cnkdtrb9 | 只读探针 | B-5 |
| RDS PG | pgm-bp13vak0sew3m003 / forcitis_one_dev / one_admin | 用 | [C-3](runbooks/C-3-dev-db.md)、#131 |
| RDS SQL Server 2016 | rm-bp1690ep53t9778dp；one_a8_views_ro、one_legacy_names_ro、one_evidence_ro（psa_one_legacy_ro 2026-10-22 已撤）；u8c = RDS 高权限（只用于授权变更，破例读须用户批准） | 全库只读 | #135、#143、#167 |
| SLS | forcitis-one-observability（nginx / ops 180 天） | 用 | [C-7](runbooks/C-7-alerts.md)、#123 |
| OSS 归档 | forcitis-one-archive（WORM 未锁） | 待锁 | B-13 |
| OIDC 提供商 | acs:ram::1980847222980247:oidc-provider/github-actions | ✅ | [RELEASE](runbooks/RELEASE.md) R-4、#144 |
| 部署角色 / 策略 | acs:ram::1980847222980247:role/forcitis-one-deploy-dev；forcitis-one-deploy-dev | ✅ | R-4 |
| 拉取策略 | forcitis-one-deploy-pull（挂 forcitis-one-ecs-archive） | ✅ | R-4 |
| OOS 参数 | forcitis-one-deploy-request-dev（+ -bundle / -bundle-sha256 / -statement / -statement-sig）、forcitis-one-deploy-result-dev | ✅ | R-7 |
| 时间同步检查 | one-time-sync-check.timer（每 5 分钟，100 ms → P2、1 s → P1） | ✅ 运行 | INFRA_RUNBOOK §8 |
| ECS 拉取 | /usr/local/sbin/forcitis-one-release、forcitis-one-release-pull.timer | ✅ 运行 | R-7 |
| Release | v0.1.0-rc.5（main 2694ecc6，run 38979438148；包 sha256 b3cef521…，jar sha256 4583f176…；本机 statement.sh verify + verify_bundle.sh 通过；已发布预发布）。更早：rc.4（f7cd54d0，jar 468d229a…，dev 当前运行版本）、rc.3、rc.2、rc.1。ECS 无 rc.5 整包（/root/one-ops-preload 里是 rc.4 的四个文件）；/root/one-ops-c14/app.jar 是 rc.4 jar | ✅ | #151 |
| GitHub | sub 定制（不可变）；environments 只允许 main；dev 变量 3 个；仓库级构建变量 8 个（SSO 门户与 PROD_* 待替换） | ✅ | R-1、R-4、R-6、§6 |

| ECS one.env | 2026-10-16 补 C-17 `SEEYON_VIEWS_ROOT_PATH`、C-18 `FORCITIS_JOBS_ENABLED=true`，`SSO_ENABLED` 改 false（t-hz07057zxlib280；备份 one.env.bak-20261016035106）；白名单仍 4 条，待用户指定保留哪 1 条 | 用 | intent 214511、215052 |
| ECS one.env（dev 首次启动） | 2026-10-16 起 dev 在位的临时项：`FORCITIS_PLATFORM_APPROVAL_PROCESS_ADMINISTRATOR_REQUIRED=false`（原因：process_administrator 首批持有人须先有员工 ID，而员工靠运行中的同步灌入，全新库上启动自检成死循环；删除条件：one-1a-par 的 ADR-0030 单人运维模式新 rc 出来后授予用户本人 process_administrator 与 super_admin，授完删此开关并重启确认自检通过）；同日 `WECOM_ENABLED=true`（扫码登录被拒：该开关同时是企业微信网关总开关；出站仍限 `WECOM_RESTRICT_RECIPIENTS=true` 与白名单 1 条，用户批准）；同日启用 `INTEGRATION_SEEYON_VIEWS_ENABLED=true`、`WORKFORCE_SYNC_ENABLED=true`、A8 实例专属 4 个编号、`ARCHIVE_STORE_*` 五项（本地目录暂用 /data/forcitis-one/tmp/archive-local）；备份 one.env.bak-20261016* | 用 | 用户 2026-10-16「好」、intent 2026-10-16 |

## 在途

| 步骤 | 下一步 | 卡在 |
|---|---|---|
| （已收尾）rc.6 与授角色 | rc.6 已装 dev；用户本人（员工 ID 末 6 位 238eea）持 super_admin、process_administrator、org_data_admin（后者 2026-10-17 经用户批准授，为导日历；授后其会话被撤销，须重新登录）；单人运维模式开；临时开关已删 | 完成 |
| SINGLE-OP-GATE（P4 部署闸门） | 卡在 one-1a-par 文档分支 claude/one-1a-par-docs@33e345a2 的 phase1a/cards/SINGLE-OP-GATE.md：systemd ExecStartPre、安装器正则与 DropInPaths、deploy_gate C-11、PREDEPLOY C-11/C-12、运维手册改 --spring.profiles.include、SECURITY_INVARIANTS I-14、P1-18 扫描范围；测试代理先写验收，实现 sonnet，opus 首轮+终审，一个代码 PR；挡生产不挡 dev | 排在 rc.5 部署之后 |
| 取证（ADR-0037，只出聚合，各登记片段） | 已交：Q-02 及补充、E-B1、E-B2、E-B6、N/V/S、F05-06、F05-09；待做：E-B3（退回/驳回/撤回比例）、E-B4（岗位待办与处理时长）、E-B5（客户签认等待）、E-B7（角色持有人数）、E-B8（外部回执时长），清单见 one-architecture 分支的 closeout/BURDEN_ANALYSIS_PLAN.md；收入专题 C1–C11（见 one-coordinator 消息里的 REVENUE_AEC_CONTRACT_MODELS.md 取证需求一节）；Q-04 E1–E10 | 逐项做；STATE/SUB_STATE 等枚举 A8 内无含义，须标注推测 |
| 缺陷卡（首次部署暴露，部署后开） | ①current→bootstrap 被当版本比较（cmd_pull）；②应用没有 migrate 模式（无 application-migrate.properties，旧路径 web=none 起不来）；③one.env.template 与安装器校验缺 ARCHIVE_STORE_* 五项；④服务单元 ReadWritePaths 缺归档目录（INFRA_RUNBOOK §5.3 与单元不一致）；⑤首次启动 process_administrator 鸡生蛋 | 待开卡报 one-coordinator |
| dev-db-rebuild 剧本 | 补一步「重新导入 2016–2026 与 2027 工作日历」（#396 合入后下次 dev 部署须按此重建开发库） | 待写（文档分支） |
| 运维手册 profile 形态 | 改 --spring.profiles.active=<自身> 为 --spring.profiles.include=<自身>（dev 开单人模式前必须改好；随 SINGLE-OP-GATE 一并） | 同上 |
| platform_file schema（one-platform-file 单元 3，分支 claude/one-platform-file-u3，PR 待开） | 含该 PR 的 rc 部署 dev/生产前，按 c3/c9 建库脚本建 schema platform_file 并授权（Flyway create-schemas=false）；after_migrations 新增两条 REVOKE；重建 dev 时一并带上 | 等 PR 合入、出新 rc |
| #547 工作日历版本化的首次部署前待办（one-coordinator 派，已更正） | 只做核验运行账号的 TEMP 权限（三张日历表的 REVOKE 由 #547 自己带）；完成点报 one-coordinator | 等 #547 合入，排在 ClamAV 联调之后 |
| 审计分区维护角色（#370） | 单开 PR：c3/c9 建库脚本新建 one_*_audit_maintainer（LOGIN NOINHERIT CONNECTION LIMIT 1）、*_after_migrations.sql 授权、INFRA_RUNBOOK §5.3「在线行暂不删除」是否改写 | 等 #370 合入、出下一个 rc |
| 迁入 ID 对照 | 抽取 | ADR-0037 实现 |

## 近期完成（2026-10-16/17）

- C-14 完成，阻塞项 101 已删（#414，文档 PR）；工具演进：探针账号经 RDS 云接口建删（#394）、第 15/17 项认平台拒绝（#399、#407）、rdscore 里 one_readonly_probe 用户为永久已知残留（msdb 随 DeleteAccount 清除）；第 14 项 (g) 口径待 one-1a-par、生产前须定。
- dev 手工装 rc.4 成功运行（https://one.forcitis.com ，企业微信扫码登录；WECOM_ENABLED=true；A8 视图与组织同步已开；归档凭据 403 告警待另议）。
- 发布门禁（#438）：ci_passed.py 沿第一父链证明重步骤（合并 PR 头的完整 pull_request 运行、步骤未跳过、与 M 只差纯文档，schedule 兜底）；残余风险用户 2026-10-16 已接受（写权限者可伪造同头 PR，见 RELEASE.md）。
- 文档分支（新规：文档按 TEAM-53 自扫分支收合集）：origin/claude/one-ops-r56（OPS_CONTEXT/RESOURCES/画像）、origin/claude/one-ops-r60（取证登记片段与取证 SQL，取证 SQL 在 handoff/B-5-dba/evidence/）。

## 待批 / 待办

- 首次真实换 STS：核对实际 sub（job_workflow_ref 段）
- OIDC 首部署成功后：提醒用户删 R-13 后备令牌
- dev 稳定后：删 /root/one-ops-backup-20261015020632、one.env 各备份、/root/one-ops-preload、/root/one-ops-c14/app.jar、/data/forcitis-one/current.bootstrap-link；一次性脚本在会话 scratchpad（会话清理即失）
- 构建变量待替换：SSO 门户地址（B-3 回传后）、PROD_*（生产前确认）
- 拉取单元沙箱收紧：首次 dev 部署跑通后、下次改安装器或单元时（RELEASE §10，#157）
- #228 合并后复核：迁移进程（migrate 模式参数未覆盖 FORCITIS_JOBS_ENABLED，沿用 one.env 的 true）不挂定时作业——migrate 模式在建上下文前退出，预期不挂；只有无 migrate 模式的旧包走整应用启动（#227 复审第 5 条）
- pg_stat_statements：Phase 2 前请示

## 坑

- SQL Server 2016 + RDS：服务器级权限不能 GRANT / DENY（4631 / 4613 / 4621）；u8c 服务器权限 = 只读基线，看不到服务器触发器 → B-5 README、#143、#167
- PG 共享实例：superuser 级参数只能改实例级 → C-3、#131
- OIDC 证书链会变 → RELEASE §10
- 只读探针拒绝含 ALTER / KILL / DELETE 的字面量 → 用 `K_LL%`
- 云助手：命令内容有长度上限、输出约 24 KB → 分块写入、gzip + base64
- 阿里云临时凭据（OAuth）只在过期后才换新，过期前调用不会延长 → `ops_credentials` 自动等过期再换；工具必须从仓库检出、未改动的文件运行
- OOS 参数值 ≤ 4096 字符 → RELEASE §10
- 安装器比对 current 的 migrations.txt：基线文件被改后第二次部署必失败 → [dev-db-rebuild](runbooks/dev-db-rebuild.md) 第 6 步
- corepack 按启动目录选 pnpm 版本 → 进 frontend 再调（#151、#156）
- CI 偶发 postgres:18 测试容器起不来 → `gh run rerun --failed`

- 部署前核对基线迁移（错题本 OPS-005）：任何 dev 部署前先跑 `git diff --name-status <当前运行提交> <目标提交> -- backend/src/main/resources/db/migration`，有 M（已有文件被改）即走 [dev-db-rebuild](runbooks/dev-db-rebuild.md)，先问用户。
- 改 `runbooks/sql/` 下脚本后：先单独跑 `c3_dev_db_check.sh` 看退出码为 0，再 add、commit、push，不把检查与提交连在一条命令里（错题本 OPS-006）。

- 工作方式：生产取证走 handoff/B-5-dba/tools/b5_evidence_scan.py（one_evidence_ro，两库各跑；SQL 里用 DB_NAME() 守卫只在 a8_db 出结果）；工具与 c14_run 等必须与 origin/main 逐字节一致；本机无 timeout 命令；`timeout`/管道会让长任务出问题，长任务用 run_in_background。

## 偏离记录

- 2026-10-15 约 21:18–21:33（one-architecture 派的 sonnet 取证子代理）：① 未经询问在其会话 scratchpad 建 venv（`ve`，Python 3.12.13），从 PyPI 装 alibabacloud-ecs20140526 7.11.6、alibabacloud-tea-openapi 0.4.6 及依赖（alibabacloud-credentials 1.0.12、alibabacloud-tea 0.4.3、alibabacloud-tea-util 0.3.15、darabonba-core 1.0.9、cryptography 50.0.2、aiohttp 3.14.5、requests 2.34.2 等 29 个），未登记；② 用包装脚本 `dr/ev_run.py` 在内存中把 `b5_legacy_run.py` 的凭据阈值 900 秒改成 240 秒后执行（仓库文件未改）。取证本身只用 one_evidence_ro、只取聚合，LEGACY_READ_REGISTER 已登记 2 行。**已清理**（one-ops，2026-10-15）：venv 与 `dr/ev_run.py` 已删除；两台服务器上的取证临时目录核实无残留（t-hz070573w93l2bk、t-hz0705748l9198g）。**防护**：通道脚本改用 `runbooks/scripts/ops_credentials.py`，凭据不足 15 分钟时等过期后自动换新，失败时给出处理步骤；拒绝从仓库外副本或未提交的改动运行（在内存里改写源码的做法仍拦不住，靠派工说明约束）；依赖只用 `~/.modernize/psa/ops-venv`。

## 决定（2026-10-16）

- Docker Desktop 订阅不买（用户：「不买，只有我一个人用 docker」）。条款：Docker Subscription Service Agreement 第 3 节，商用免费需「fewer than 250 employees and less than US $10,000,000 in annual revenue」，公司约 304 人已超员工数线；取自 https://www.docker.com/legal/docker-subscription-service-agreement/ （页面标注 Last updated on August 26, 2026），风险由用户承担。
- CI 不搬到 Linux 机器，不开试用机。本机测试提速调查：5 个数据库集成测试类单 fork 基线 46.3 秒、43.2 秒，PostgreSQL 容器改内存盘并关 fsync 后 41.7 秒，差距在波动内，不值得做；时间主要花在每类各自起容器、建应用环境、跑基线迁移（76 个类各起一个容器）；已做的 fork 降到 2、看门狗、停闲置容器够用，停止。
- H-2（探针的 msdb 用户移出 SQLAgentOperatorRole）：平台不可构造（u8c 在 msdb 无 ALTER ANY ROLE，2026-10-16 实测），不做；探针账号改经 RDS 云接口建删（msdb 用户随之清除），rdscore 里同名用户是永久已知残留（用户 2026-10-16 决定接受，见 C-14 剧本与 RESOURCES）。

## 暂停项（2026-10-17 用户「ops 这一轮 rc 后，暂时不考虑 dev 的事儿了」，one-coordinator 转达）

- dev 工作日历重导：2016–2026 的 JSON 已按 workday 标记推出（HOLIDAY 207、调休 65，各年条数见 C-6 剧本步骤 3），本机会话临时目录里的 cal.json 会随会话清理而失；导入须有 org_data_admin 的登录会话逐年 PUT /api/v1/organization/work-calendars/{年}，再逐年 GET 核条数；dev 目前没有任何日历（重建后清空），依赖日历的审批时限会失败告警；导入的 207 条属推断数据，标临时。2027 年清单须组织数据管理员给（B-7）。
- 后续 rc 的 dev 部署与重建：每次基线迁移有改动即要重建 dev 库并重新扫码（dev-db-rebuild.md；回滚脚本对旧库缺维护角色不幂等，见错题本 OPS-007）。
- c14_run 自测 PR、OPS-007（回滚脚本 DROP ROLE IF EXISTS 与 check.sh 旧库用例）：待做，可交 Codex。
- 维护角色 one_dev_audit_maintainer 口令未设。
- NAS 合同取证（2026-10-17 暂停，用户额度紧）：方案与现状见 handoff/NAS_CONTRACT_EVIDENCE_PLAN.md；已建的权限组 one_contract_ro 与挂载点 0f3fa4b751-vqo78 已删并回读确认，未读取任何内容；恢复时须先请用户批准 ECS 安全组加一条出向（445 → 新挂载点 /32）。
- org_data_admin（用户本人持有，2026-10-17 用户批准授予，为导日历）：保留，不收回（one-coordinator 转达，依据 intent 2026-10-15-215052「dev 全由我兼任」：A-3/A-4/A-5/A-8 在 dev 阶段全由用户本人兼任，A-4 即组织数据管理员）；要收回用 identity-operations 的 revoke-role（不带 --witness），收回后其会话被撤销。

- 2026-10-16 暂停（one-coordinator 决定）：C-6 回放 6 条差异的原因取证（#254，加密中转脚本与诊断查询，草稿，未运行）。理由：成本与风险偏高（新的安全类脚本加一轮复审），生产数据只作参考，A8 没有上级字段，做完也只能间接判断。6 条归为「回放方法限制」，未取证坐实；以后真要坐实再启用，须先过复审并合并。

## 剧本

[RELEASE](runbooks/RELEASE.md) · [C-3](runbooks/C-3-dev-db.md) · [C-14](runbooks/C-14-readonly-probe.md) · [dev-db-rebuild](runbooks/dev-db-rebuild.md) · [C-7](runbooks/C-7-alerts.md) · [C-8](runbooks/C-8-kms.md) · [B-5](handoff/B-5-dba/README.md) · [PREDEPLOY](PREDEPLOY_CHECKLIST.md) · [台账](../../COORDINATOR_LEDGER.md)
