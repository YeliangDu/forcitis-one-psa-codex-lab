# 开工前术语门禁的小决策页

规则见 SESSION_PLAYBOOK 附 M6。负责会话在验收阶段把本卡新词交 one-glossary；one-glossary 当天预判，纯技术词直接定，业务用语按下面做成只含本卡新词的在线页交用户，确认后负责会话才派实现。

| 步骤 | 做法 |
|---|---|
| 1. 写数据 | `gate/<卡号>.json`，格式见下 |
| 2. 生成页面（多张单子） | 固定页：`build_gate_page.py ACL-UNDECIDED,ACL-TRANSITION,ACL-LONGTERM,ACL-DEFFIX,R3-AP,…,R3-DF <输出> --by-bc`：新系统用语按 bc 分组，ACL 单保持按归类分组（用户 2026-10-15），每组底部「提交本组」 |
| 2. 生成页面（同名异义样例） | 卡数据放 `glossary/homonym/`（不在 gate/，答复导出到 `glossary/answers/`、不自动写回），用法同下，见 `homonym/README.md` |
| 2. 生成页面 | `python3 docs/modernization/glossary/gate/build_gate_page.py <卡号> <scratchpad>/gate-<卡号>.html` |
| 3. 发布 | 发布到固定链接 https://claude.ai/artifact/QPwiXzEFWCTqSGVCQRdK6J（2026-10-15 起；原 MwoqwkhAYq9CtomgaedwZ1 停用不删）（Artifact 带 `url`，能力沿用 `db` + `user`，`write: owner`）；每次覆盖为当前这张卡，链接不变。用户手机上新链接首次加载会卡在加载中，固定链接能打开（2026-10-15） |
| 4. 读答复 | 用户点「我选好了，提交」并确认后才读。ArtifactData `list` 集合 `answers` 与 `answers-submit`（同一个 `out_dir`；答复文档 id 为 `gate-<卡号>~<编号>`，提交记录文档 id 为 `gate-<卡号>`），再 `python3 docs/modernization/glossary/gate/export_decisions.py <卡号> <out_dir>` → `glossary/decisions-gate-<卡号>.json`；没提交或提交后又改过的导出失败，没选的条目算未定 |
| 5. 写回 | `cd docs/modernization && python3 glossary/build_terms.py`（`gate/apply_gate.py` 登记正式词条）；归为通用词的补进 `backend/glossary/terms.yaml`；把结果回负责会话（抄 one-coordinator），负责会话删掉本卡在提议 yaml 里的同名条目 |

**性能约束（2026-10-15 实测）**：外壳要等页面就绪才显示，主线程忙太久手机上就一直停在加载中。所以默认一屏只画一组（单组上限 120 条，生成器校验），数据回来合并成一次重画，屏幕外条目不排版，所有单子共用一个答复集合、打开时只订阅 1 次（按单子各订阅一次时手机打不开）；提交状态只读一次不订阅；脚本包在独立作用域里。改模板后在已登录的浏览器里打开固定链接，先点一下页面让标签页在前台，3 秒内应显示。

页面上的选择只是草稿，提交时先列出同意、保持、改为、未选各几条，用户确认后才算答复（第三轮页面没有这一步，365 条逐条点选被当成最终答复，用户随后说「我没有同意所有用语」，2026-10-15）。页面版式与第三轮用语确认页相同：每条 同意建议 / 保持原样 / 改为…，不用外部字体，页面级不设 overflow 隐藏（手机端会加载失败）。

## 数据格式

```json
{"card": "R-1b-0", "session": "one-1a-next-3", "date": "2026-10-15", "lede": "一句话：这张卡做什么、新词从哪来",
 "items": [{
   "code": "G01", "zh": "提议中文名", "en": "ProposedEnglish", "kind": "entity", "bc": "platform.integration",
   "definition": "定义", "verdict": "正式|方言|待定", "refs": "依据：标准名或条文", "why": "推荐理由，一两句",
   "id": "可选：登记用的 id（默认取英文名的 snake_case）",
   "values": [{"zh": "取值中文", "en": "ValueEnglish"}],
   "code_note": "可选：需改代码之类的提示",
   "scope": "domain|kernel|acl（登记时必填，B1）", "acl_class": "ACL 时：过渡期|长期",
   "same_as": "可选：同一 bc 已登记的同一概念的 id，答复后不另立词条",
   "options": [{"action": "approve|rename|merge|common_word", "zh": "", "en": "", "into": "合并目标 id", "label": "给用户看的一句话", "recommended": true}]
 }]}
```
