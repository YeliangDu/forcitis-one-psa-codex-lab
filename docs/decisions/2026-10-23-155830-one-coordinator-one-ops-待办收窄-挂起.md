## 原话

one-ops 待办收窄：挂起 GOV-1023-QUA-05、QUA-08（新加检查）与 QUA-01（状态声明/文件信箱/回执）；末行状态声明的 Stop 拦截 7 天挡停 20 次，在 QUA-06/07 里删掉、不再加；one-ops 只专注 QUA-04 预检提速（第一优先）、QUA-06 钩子瘦身、QUA-07 交接，外加 #665/#659/#667 收尾。Grok Bot 盘点十项建议并入 QUA-04/06（推送前只留秒级检查、改动范围按本次推送算、build_all --check 只跑受影响生成器、删本地 21 个钩子 self-test、precheck_before_pr 并入或删除、heavy_slot 保留后再量、PB 新增一条须标退哪条、画像沿用 8KB 上限、context_handoff.py 冻结只删不加、mq.py 与 merge_train.py 自测只放 CI、CI 里 build_all --check 去掉一遍）；结构性规矩：推送前检查 60 秒耗时预算、CI 超了报警谁加谁瘦回、新加门须写成本和下线条件、造门会话不能自己验收门

## 落点

QUA-01/05/08 挂起；QUA-04/06/07 卡；规则表 TEAM-109、TEAM-53
