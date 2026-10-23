# 用语问题片段

一条问题一个文件，用 `python3 docs/modernization/glossary/new_term_issue.py --session=<会话名> --scope=domain|kernel|acl --bc=<bc> "<词或现象>" "<出处>" [--kind=new|dialect|conflict|duplicate|other]` 新增，不手写文件名；问题片段只增不改，状态恒为「待处理」。one-glossary 用 `glossary/resolve_term_issue.py <问题文件名> <已预判|待用户|已关闭> "<处理>"` 写同名的 `.resolution.md` 处理片段。汇总为 `glossary/ISSUES.md`（生成，不入仓），规则见 SESSION_PLAYBOOK 附 M6。
