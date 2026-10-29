## 原话

采纳（main 只多了纯文档合入时不算代码 PR 落后、不重跑，文档道不再等代码道）

## 落点

one-merge 先核 relevant_change.py 对 docs 档判断的准确性（会被测试读到的文档须归 guarded/code），再改 scripts/merge/mq.py 的 BEHIND-AT-MERGE 与 WAIT-CODE-LANE 判断；守护代码，PR 单列、opus 复审；兜底沿用 main 推送轻量检查、夜间全量与红 main 自动暂停两道
