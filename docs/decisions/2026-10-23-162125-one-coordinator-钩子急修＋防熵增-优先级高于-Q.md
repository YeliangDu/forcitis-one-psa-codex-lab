## 原话

钩子急修＋防熵增，优先级高于 QUA-04 第二批以外的一切：急修借调 one-merge（run_from_main.sh 删第 54 行、第 52 行改 status --porcelain 只看 hooks 下 py/sh/json、git 失败告警用主检出副本；第 63–76 行找不到副本告警运行主检出副本不再 exit 2，--strict 只用于钩子本身崩溃；补 .DS_Store 回归自测），归还条件急修 1、2 合入 main 且 .DS_Store 回归测试通过后归还 one-ops，one-quality 只复核；pre_tool_dispatch 出错只拦 git push、gh pr create、可能写生产的命令，prod_write_gate 按需加载，钩子瘦身到约 12 文件、≤2500 行、≤150KB 归 one-ops QUA-06；防熵增规矩写进 SESSION_PLAYBOOK 并做 CI 检查：上限、一进一出、共用函数唯一、默认失败放行（只推送、开 PR、写生产允许失败即拦）、测试与运行代码分离、造检查的不得自己验收

## 落点

one-merge 急修 PR；GOV-1023-QUA-06；规则表 TEAM-110 取代 TEAM-109 部分
