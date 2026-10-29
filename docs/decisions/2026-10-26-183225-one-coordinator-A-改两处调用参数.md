## 原话

A 改两处调用参数

## 落点

O11 配套：scripts/merge/mq.py finish_merge 与 regenerate_conflicts 两处调 build_all 加 --without-api，仅改调用参数，不改队列逻辑；视为 O11 必要适配，不算改造合入队列（intent 2026-10-23-222945 其余不变）
