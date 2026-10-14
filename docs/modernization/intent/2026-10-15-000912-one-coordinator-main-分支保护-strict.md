## 原话

main 分支保护 strict 状态检查：「开启 (Recommended)」；CODEOWNERS 与代码所有者审查（0032-Q4）：「现在不开，生产前再定 (Recommended)」

## 落点

已开启 required_status_checks.strict；CODEOWNERS 列入生产前待定，在此之前改守护文件的 PR 由 one-coordinator 单列并要求 opus 复审
