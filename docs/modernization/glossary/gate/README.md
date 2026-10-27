# 开工前术语门禁的小决策页

规则见 SESSION_PLAYBOOK 附 M6。负责会话在验收阶段把本卡新词交 one-glossary；one-glossary 当天预判，纯技术词直接定，业务用语按下面做成只含本卡新词的在线页交用户，确认后负责会话才派实现。

| 步骤 | 做法 |
|---|---|
| 1. 写数据 | `gate/<卡号>.json`，格式见下 |
| 2～4. 出页、发布、读答复 | 本目录的页面生成器与答复导出（`build_gate_page.py`、`export_decisions.py`、`page-template.html`）已归档（O16，见 `ARCHIVE_INDEX.md`）；决定页只留 `rules/decide/` 一套（G2）：题写成 `rules/decide/sheets/<单子>.json`（用语审定页标 `terms_review`），过 `rules/decide/check_pages.py`，用 `rules/decide/build_decision_page.py` 出页，流程见 `rules/decide/README.md`。已有的 `gate-<卡号>` 答复与 `glossary/decisions-gate-<卡号>.json` 是历史记录，照旧由第 5 步重放 |
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
