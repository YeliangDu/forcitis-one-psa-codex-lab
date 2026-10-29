## 原话

问「合并冲突是什么原因」后选「改：记录类产物不入仓，main 自动生成到只读分支 (Recommended)」（2026-10-14，psa-coordinator 当面询问）

## 落点

INTENT.md / SESSION_LOG.md / STATUS.md 移出仓库并加入 .gitignore；.github/workflows/generated-docs.yml 在 main 更新后发布到 generated-docs 分支；CI 对三者只要求能生成；CLAUDE.md、README、SESSION_PLAYBOOK §1、§3、§4、§8 更新
