## 原话

随时待命的任务codex子代理；尽量待命的优先codex子代理，合适吗？

## 落点

待命分级：随时待命（one-coordinator、one-merge、one-data、one-ops）15 分钟内响应，任务一律交 Codex 或后台子代理；尽量待命（one-architecture、one-glossary、one-quality）优先交 Codex，长活拆段、段间看消息；钩子与语义判断等须自写的也放后台子代理，会话不长时间占住；开发线与收尾会话不待命。写入各画像触发与节奏
