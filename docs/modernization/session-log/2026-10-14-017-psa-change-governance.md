## 2026-10-14 · psa-change-governance · 变更治理写入会话方案与 ADR 规则
- 目标：把用户的四条变更治理规则写进 SESSION_PLAYBOOK.md 与 adr/README.md，生成器加校验，盘点现有偏差（psa-coordinator 派工；本会话原为 psa-oa-inventory，复用）。
- 输入：origin/main 9e6e279；SESSION_PLAYBOOK、adr/build_adrs.py、ADR-0001..0023、proposals/GL_SCOPE_REVISIONS.md、各 ADR 的 git 历史。
- 用户决定（原话见 INTENT.md）：变更治理四条。
- 产出：SESSION_PLAYBOOK §12（ADR 不改正文 / 三类规则调整 / 已过账事实只能冲销 / 谁可以改什么），§1 状态文件表、§4 收尾、§11 合并前核对交叉引用；adr/build_adrs.py 支持「取代」与四个必备小节（影响面、迁移方案、回滚方案、需同步修改的守护规则），校验状态集、ADR-0024 起必备小节、取代时旧 ADR 状态同步，README 由生成器写入「ADR 生命周期与变更规则」；CONFIG_CHANGELOG.md（只追加的配置变更登记，空表）。
- 验证：build_adrs.py 重新生成，ADR-0001..0023 无变化，只有 README 新增一节；负向测试（临时副本注入 ADR-0024）：缺必备小节、状态「讨论中」、取代 ADR-0016 第 6 条而旧状态未标注，三种均生成失败；正确写法生成成功并渲染「取代」行与四个小节。未使用子代理（盘点用 grep 与 git log 即可完成）。
- 现状清单（未修改这些 ADR）：已采纳后被直接改正文的——ADR-0009 第 6 条（5a9c69c 修订、2517ef3 补充）、ADR-0016 后果第 4 条后追加的「修订提议」（1cd7219）、ADR-0018 决定 2 追加第二轮确认记录（507b9bd、8d6f9d0）、ADR-0020 第 7 条改写与新增第 8 条（62da1fc）；ADR-0011 头部状态为全部定稿，正文仍有 3 处【提议】标记。修订稿形式的提议：GL_SCOPE_REVISIONS.md A（ADR-0016 第 6 条）、B（ADR-0017 第 4 条）；ADR-0016 第 44 行后的修订提议。缺四个必备小节：ADR-0001..0023 全部（其中 0022、0023 为提议）。
- 未完成与阻塞：建议的新 ADR 与历史偏差的处置待用户决定（见报告）。
- 下一步命令：用户决定后，由对应会话按 §12 起草取代 ADR（经 build_adrs.py 生成）。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-change-governance）
