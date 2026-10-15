# 业务决策在线页（one-rules）

待用户定的业务口径做成在线页：手机能开、推荐项预选、每组底部单独提交，选择存在在线页数据库，one-rules 直接读取（不让用户导出）。性能约束沿用 `glossary/gate/README.md`：一屏只画一组（单组上限 40 题）、答复集合只订阅一次、提交记录只读一次、脚本包在独立作用域、不加载外部资源。

| 单子 | 数据源 | 在线页 |
|---|---|---|
| FD 财务口径决策单（46 题 + 9 条问财务的事实） | `r0/FINANCE_DECISIONS.md`（离线版 `r0/FINANCE_DECISIONS.html` 由 `r0/build_finance_decisions.py` 生成） | https://claude.ai/artifact/UfWUw5PuwVe6HzC5QRn8nB（2026-10-15 起） |
| D5 工时审批路径（ARCH-D5，6 题） | `sheets/D5.json`（行业做法为主，老系统生产汇总只作核对，取证 `phase1a/infra/handoff/B-5-dba/evidence/arch_d5_timesheet_routing.sql`） | https://claude.ai/artifact/GGz4AuMS63ooyL85itojya（2026-10-15 起） |

| 步骤 | 做法 |
|---|---|
| 1. 写数据 | FD 改 `r0/FINANCE_DECISIONS.md`；其他单子写 `sheets/<单子>.json`，格式见下 |
| 2. 生成 | `python3 docs/modernization/rules/decide/build_decision_page.py <单子>[,<单子>...] <scratchpad>/<文件>.html` |
| 3. 发布 | 同一单子覆盖到上表链接（Artifact 带 `url`）；能力 `db`（集合 `answers`、`answers-submit` 只许 owner 写）+ `user` |
| 4. 读答复 | 用户说已提交后，ArtifactData `list` 集合 `answers` 与 `answers-submit`（同一个 `out_dir`），再 `python3 docs/modernization/rules/decide/export_answers.py <单子> <out_dir>` → `answers/<单子>.json`；提交后又改过的组导出失败，请用户重新提交 |
| 5. 写回 | 答复写回规则卡（如 `rules/GL_FINANCE_DECISIONS.*`、`r0/FINANCE_QUESTIONS.md` 去向），用户原话记 intent；会计类经财务（ADR-0018） |

提交前页面只是草稿；提交时先列出按推荐（其中没动过的）、改选、暂不定、写了说明各几题，用户确认后写提交记录，记录里有本组每题的选择，没动过的题标 `defaulted`。

## sheets/<单子>.json 格式

```json
{"sheet": "D5", "title": "页面标题", "lede": "一句话：这张单子定什么",
 "groups": [{"id": "d5-routing", "title": "组名", "desc": "本组说明"}],
 "items": [{"no": "D5-01", "grp": "d5-routing", "title": "题目", "src": "来源", "ctx": "背景",
            "now": "现状（可空）", "check": "需核实（可空）", "basis": ["依据"], "practice": ["行业做法"],
            "opts": [{"label": "选项", "effect": "后果", "rec": true}, {"label": "选项", "effect": "后果", "rec": false}]}],
 "info": [{"title": "首组顶部的可展开说明", "lines": ["…"], "table": [["表头"], ["行"]]}],
 "side": [{"title": "左栏可展开清单", "lines": ["…"]}]}
```
