## 2026-10-14 · psa-repo-fragments · 记录类产物不入仓
- 目标：INTENT / SESSION_LOG / STATUS 不再入仓，消除生成产物的合并冲突；main 更新后自动发布到 generated-docs 分支（psa-coordinator 派工）。
- 输入：origin/main 8c8ea00（含 #44、#49、#50）；.gitignore、ci.yml、build_all.py、build_records.py、CLAUDE.md、README.md、SESSION_PLAYBOOK；全仓引用这三个文件的脚本与文档。
- 用户决定（原话见 INTENT.md）：问「合并冲突是什么原因」后选「改：记录类产物不入仓，main 自动生成到只读分支 (Recommended)」。
- 产出：git rm --cached 三个产物并写入 .gitignore；.github/workflows/generated-docs.yml（push main / 手动触发，contents: write，concurrency 互斥；build_all 后把三个产物与 ADR、README、brief 的快照提交到孤儿分支 generated-docs 并强制推送，提交信息 generated from main <SHA>，不推 main）；build_all.py --check 说明（逻辑不变：被忽略的产物不参与比对，ADR 与 brief 仍比对）；ci.yml 注释；build_records.py 说明；CLAUDE.md、README.md（generated-docs 链接）、SESSION_PLAYBOOK §1、§3、§4、§8。
- 验证：发布脚本在本地以裸仓库模拟推送成功（33 个文件、提交信息带 SHA）；依赖调查——没有脚本把这三个文件当输入读，build_records 只写、new_intent / new_log 只在说明中提到，文档中的引用指记录本身，本地生成后仍成立；build_all.py --check、check_legacy_name.py 通过。未使用子代理。
- 未完成与阻塞：仓库若在组织层面把 GITHUB_TOKEN 设为只读，首次发布会失败，需管理员在 Settings → Actions → Workflow permissions 放开（或改用部署密钥）。
- 下一步命令：合并后看 Actions 中 generated-docs 运行结果并打开 generated-docs 分支确认。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-records-untracked）
