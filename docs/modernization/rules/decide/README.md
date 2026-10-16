# 业务决策在线页（one-rules）

待用户定的业务口径做成在线页：手机能开、推荐项预选、页面最后一个标签「检查答案」整页核对后一次提交（提交后显示编号），选择存在在线页数据库，one-rules 直接读取（不让用户导出）。性能约束沿用 `glossary/gate/README.md`：一屏只画一组（单组上限 40 题）、答复集合只订阅一次、提交记录只读一次、脚本包在独立作用域、不加载外部资源。

| 单子 | 数据源 | 在线页 |
|---|---|---|
| FD 财务口径决策单（46 题 + 9 条问财务的事实） | `r0/FINANCE_DECISIONS.md`（离线版 `r0/FINANCE_DECISIONS.html` 由 `r0/build_finance_decisions.py` 生成） | https://claude.ai/artifact/UfWUw5PuwVe6HzC5QRn8nB（2026-10-15 起） |
| D5 工时审批路径（ARCH-D5，6 题） | `sheets/D5.json`（行业做法为主，老系统生产汇总只作核对，取证 `phase1a/infra/handoff/B-5-dba/evidence/arch_d5_timesheet_routing.sql`） | https://claude.ai/artifact/GGz4AuMS63ooyL85itojya（2026-10-15 起） |
| F1 数据访问范围（F-1 待定 7 题） | `sheets/F1.json`（F-1 行业对照 `phase1a/cards/F-1-aec-check.md`；标准与产品依据 one-rules 调研） | https://claude.ai/artifact/4NMzvH2vxpJUYfckwrfcSJ（2026-10-15 起） |
| C6 工时审批回放差异（Phase 1a 出口 D-3，2 类） | `sheets/C6.json`（计数取自 `phase1a/infra/runbooks/C-6-legacy-export.md`「回放结果」，one-ops） | https://claude.ai/artifact/Y7ogz8AWPmZdeuE4HCEGqL（2026-10-16 起） |

| 步骤 | 做法 |
|---|---|
| 1. 写数据 | FD 改 `r0/FINANCE_DECISIONS.md`；其他单子写 `sheets/<单子>.json`，格式见下 |
| 2. 生成 | `python3 docs/modernization/rules/decide/build_decision_page.py <单子>[,<单子>...] <scratchpad>/<文件>.html` |
| 3. 发布 | 同一单子覆盖到上表链接（Artifact 带 `url`）；能力 `db`（集合 `answers`、`answers-submit` 只许 owner 写）+ `user`；**固定 `contract: "0.2.80"`**（0.2.81、0.2.82 在手机上白屏过，新版验证可用后再升）；发布后用内置浏览器核对真实渲染，没登录就报告「未实看」并请人先在手机上打开确认 |
| 4. 读答复与回写 | 用户说已提交后，ArtifactData `list` 集合 `answers` 与 `answers-submit`（同一个 `out_dir`），再 `python3 docs/modernization/rules/decide/decide_sync.py <单子> <out_dir> --inbox-id=<收件箱事项 id>`：对账（有提交后又改过的题就失败，请用户重新提交）、写 `answers/<单子>.json`、按提交编号生成 intent 片段，并打印收件箱事项改为已定所需的 ArtifactData `update`，由会话对收件箱页执行（各页是独立发布件，确认页不能直接写收件箱）。只想导出答复不生成 intent 时用 `export_answers.py <单子> <out_dir>` |
| 5. 写回 | 答复写回规则卡（如 `rules/GL_FINANCE_DECISIONS.*`、`r0/FINANCE_QUESTIONS.md` 去向），用户原话记 intent；会计类经财务（ADR-0018） |

提交前页面只是草稿；「检查答案」先列出全部题的选择与按推荐（其中没动过的）、改选、暂不定、写了说明各几题，用户确认后写一份提交记录（含全部题的选择，没动过的题标 `defaulted`）。

## sheets/<单子>.json 格式

```json
{"sheet": "D5", "preselect": false, "title": "页面标题", "lede": "一句话：这张单子定什么",
 "groups": [{"id": "d5-routing", "title": "组名", "desc": "本组说明"}],
 "items": [{"no": "D5-01", "grp": "d5-routing", "title": "题目", "src": "来源", "ctx": "背景",
            "now": "现状（可空）", "check": "需核实（可空）", "basis": ["依据，必填字段，可为空数组"], "practice": ["行业做法，必填字段，可为空数组"],
            "opts": [{"label": "选项", "effect": "后果", "rec": true}, {"label": "选项", "effect": "后果", "rec": false}]}],
 "info": [{"title": "首组顶部的可展开说明", "lines": ["…"], "table": [["表头"], ["行"]]}],
 "side": [{"title": "左栏可展开清单", "lines": ["…"]}]}
```

## 模板版本（2026-10-16 起）

- **整页「检查答案」后一次提交**：分组标签带各组状态，最后一个标签「检查答案」列出全部题的选择（可逐题「改这题」），确认后写一份提交记录，文档 id 即**提交编号**（形如 `FD2-20261016-135758-38z`），页面显示编号。提交后又改任何一题，该题与「检查答案」标签变为待重新提交，再提交得新编号；同一题以最晚一次提交为准。
- 旧版页面（按组提交，提交记录文档 id 为组 id）已发布的不迁移，导出脚本两种格式都认。
- 新单子一律用 `sheets/<单子>.json` 与本生成器；`glossary/gate/` 与 `architecture/decision-page/` 的旧页冻结不迁（工件治理研究第 3 章）。
- 发布固定 `contract: "0.2.80"`；发布后核对真实渲染（见画像工作守则）。

## 预选与「未作答」

- **新模板默认不预选**（ADR-0041 决定 6：「决策页写明代价，不预选推荐项」）：页面不选中任何选项，没作答的题记为「未作答」（提交记录 `choice: null`、`unanswered: true`，`decide_sync` 在原话里标「（未作答）」）；推荐项仍显示「推荐」标签。
- 单子 json 里写 `"preselect": true` 才预选推荐项（没动过的题按推荐计，提交记录标 `defaulted`）。同一页上的单子 `preselect` 须一致。
- 业务口径单要不要预选，用户尚未裁定（one-coordinator 已问，三选一）；裁定前一律不预选。已发布的旧页保持原样（FD、D5、F1、C6 等都是预选版）。

## 对账检查

调度报「待答」前先跑：`python3 docs/modernization/rules/decide/decide_sync.py --check <收件箱 out_dir> <单子>=<确认页 out_dir> ...`，先用 ArtifactData `list` 把收件箱与各确认页的集合导出到各自的 out_dir。它报出「收件箱仍是 pending、确认页已提交」与「确认页提交后又改过」的事项，有不一致时返回码 1。事项与单子的对应：事项里写 `sheet` 字段，或事项 `link` 等于上面页面表里的链接。
