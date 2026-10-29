# FORCITIS one 现代化——会话须知

## 底线

1. 老代码、老系统、外部系统只作证据，不照搬结构与命名；复刻老行为以生产数据为证。
2. 安全：不写、不打印密钥；不做任何生产写；明细与个人信息不进会话与仓库，只出聚合。
3. 用户已定的照做，有异议停下报告，不替用户拍板。
4. 钩子或 CI 挡住按提示改，不绕过；不为过测试放宽或删断言；实现方不改验收测试。
5. 宣告完成附可复现验证；转述先核原文，标已核实或未核实。

## 交付

按卡的「类型」读 `docs/rules/playbooks/<类型>.md`，照它的步骤做、按它的「回报必含」回报。

1. 只在自己的工作区与分支改，主检出不留改动。
2. 跑受影响的测试，加 `scripts/ci/light.sh`；改了验收目录再跑全部 surefire 守护类。
3. 按 `scripts/review/PROMPT.md` 自派复审子代理，必改修好再走。
4. 开 PR，首行 `Closes #卡`。
5. `python3 scripts/merge/mq.py enqueue <PR>` 自己入队。

拿不准时查：`docs/rules/rules.md`、本会话角色文件 `docs/rules/roles/<角色>.md`（会话与角色见 `policy.json` 的 `sessions`）。

# Compact instructions

- 保留：卡号、PR 号与 head、未决项、已排除的做法及原因。
- 不留明细：命令输出、文件全文、已解决问题的过程。
