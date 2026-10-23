## 原话

采纳（one-merge/one-quality 写代码交 Codex 后台、调度在 one-merge 超 15 分钟未处理批准时可自行 mq.py enqueue）

## 落点

one-merge、one-quality 写代码一律交 Codex 后台，队列操作与解冲突优先；one-merge 一轮超过 15 分钟未处理调度批准时，调度可自行执行 mq.py enqueue（只入队，不动其他队列操作），事后告知 one-merge；MRG-07 上线后自然退场
