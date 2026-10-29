## 原话

OIDC 拉取模式后 dev / prod 环境的 v* 标签部署策略已无用处，是否撤掉：「撤掉，只留 main (Recommended)」

## 落点

dev / prod environment 部署分支策略只留 main；部署一律从 main 触发 deploy.yml，v* 标签只用于发布
