## 2026-10-14 · psa-repo-fragments · 片段文件名去序号化
- 目标：片段序号在并行会话间撞号（intent 099–101 各两条），改为不需要协调的文件名（psa-coordinator 派工；非用户决定，INTENT 不记）。
- 输入：origin/main 190c79f（#37–#39 已合并）；build_records.py、new_intent.py、new_log.py、build_all.py。
- 用户决定（原话）：无。
- 产出：new_intent.py（新增必填 --session）/ new_log.py 生成 <日期>-<HHMMSS>-<会话名>[-<短名>].md，同秒冲突自动顺延一秒；build_records.py 按（日期、旧格式在前、旧序号或时刻、文件名）确定排序，旧格式同日同序号给出警告（不失败），不合规文件名失败；build_all.py 透传警告；SESSION_PLAYBOOK §1、§4、§8 与 CLAUDE.md 更新用法。已有文件不改名。
- 验证：新 main 上重新生成产物无变化（现有顺序不变），并报出 3 组真实撞号警告；临时副本测试：旧格式撞号警告、新格式排在旧格式之后、同一秒两个会话按文件名排序、缺 --session 打印用法、坏文件名失败。build_all.py --check 通过。未使用子代理。
- 未完成与阻塞：无。
- 下一步命令：在途会话按新用法建片段（psa-coordinator 转达）。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-fragment-naming）
