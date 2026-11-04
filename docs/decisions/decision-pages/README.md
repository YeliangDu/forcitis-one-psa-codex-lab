> 冻结（2026-11-04）：旧版确认页（2026-10-14 至 10-26）的页面清单，不再更新；决定页生成与检查机制（`rules/decide/`、`check_pages.py`）已不在仓库，下文对它们的指向无效；用户答复见 `answers/` 与 `sheets/`，结论见各 ADR 与 `docs/glossary/terms.yaml`。

# 业务决策在线页（one-rules）

待用户定的业务口径做成在线页：手机能开、推荐项预选、页面最后一个标签「检查答案」整页核对后一次提交（提交后显示编号），选择存在在线页数据库，one-rules 直接读取（不让用户导出）。性能约束沿用 `在线页性能约束`：一屏只画一组（单组上限 40 题）、答复集合只订阅一次、提交记录只读一次、脚本包在独立作用域、不加载外部资源。

| 单子 | 数据源 | 在线页 |
|---|---|---|
| FD 财务口径决策单（46 题 + 9 条问财务的事实） | `docs/business/finance-decisions.md`（离线版 `r0/FINANCE_DECISIONS.html` 由 `r0/build_finance_decisions.py` 生成） | https://claude.ai/artifact/UfWUw5PuwVe6HzC5QRn8nB（2026-10-15 起） |
| D5 工时审批路径（ARCH-D5，6 题） | `sheets/D5.json`（行业做法为主，老系统生产汇总只作核对，取证 `docs/data/evidence/dba-handoff/arch-d5-timesheet-routing.sql`） | https://claude.ai/artifact/GGz4AuMS63ooyL85itojya（2026-10-15 起） |
| F1 数据访问范围（F-1 待定 7 题） | `sheets/F1.json`（F-1 行业对照 `phase1a/cards/F-1-aec-check.md`；标准与产品依据 one-rules 调研） | https://claude.ai/artifact/4NMzvH2vxpJUYfckwrfcSJ（2026-10-15 起） |
| C6 工时审批回放差异（Phase 1a 出口 D-3，2 类） | `sheets/C6.json`（计数取自 `docs/ops/runbooks/legacy-export.md`「回放结果」，one-ops） | https://claude.ai/artifact/Y7ogz8AWPmZdeuE4HCEGqL（2026-10-16 起） |
| FD2 工时填报义务（6 题） | `sheets/FD2.json`（取证 `docs/data/evidence/dba-handoff/timesheet-filing-duty.sql`，行业调研 one-rules） | https://claude.ai/artifact/DWcj2U7HrYZ2zmVNreBmfS（2026-10-16 起） |
| A1 重议后续（A1 维不维持、无法定年审的替代把关，3 题） | `sheets/A1.json`（依据收入事实页 R-1～R-8 的答复） | https://claude.ai/artifact/TaLY2TVGaoqhZCpt7uPMrG（2026-10-16 起，未实看渲染） |
| ANM 流程方法与命名体系批准（ADR-0052、0053 与 SD-11–15，11 题；one-architecture） | `sheets/ANM.json` | https://claude.ai/artifact/KTzjRe3S2FvJEiDu1Priey（2026-10-17 起） |
| CAPINFO 能力层与信息层确认（9 题；one-architecture） | `sheets/CAPINFO.json` | https://claude.ai/artifact/HfPaPW8QrbBoct65sS6w8g（2026-10-17 起，未实看渲染） |
| TRN 过渡 ADR 批准（ADR-0054、0055、0056，14 题；one-architecture） | `sheets/TRN.json` | https://claude.ai/artifact/ViNWQdAhmyGLAA919TFBwN（2026-10-17 起，未实看渲染） |
| B31 内部服务价值流（9 题；one-architecture） | `sheets/B31.json` | https://claude.ai/artifact/96WEt2s9swq3LNsjyfpcMz（2026-10-17 起，未实看渲染） |
| ADR57 报价发出前用印核验的模块安排（1 题；one-architecture） | `sheets/ADR57.json` | https://claude.ai/artifact/HUEe8RUcHHVpY5MH7zkZkd（2026-10-17 起，未实看渲染） |
| B31P 内部服务制度参数（14 题；one-architecture） | `sheets/B31P.json` | https://claude.ai/artifact/B9YzD6twHGRWoLFXqJxbcP（2026-10-17 起，未实看渲染） |
| PAN 流程挂阶段与业务对象的规则（1 题；one-architecture） | `sheets/PAN.json` | https://claude.ai/artifact/SfHTmA4r4wYAh1hFVGvFFT（2026-10-17 起，未实看渲染） |
| NM8A 生成命名检查（一）（113 题；one-architecture） | `sheets/NM8A.json` | https://claude.ai/artifact/SgUfRc2BhoDaJXyrp9BW9S（2026-10-17 起，未实看渲染） |
| NM8B 生成命名检查（二）（117 题；one-architecture） | `sheets/NM8B.json` | https://claude.ai/artifact/ELUazxUJ3r14K4B8a17T3E（2026-10-17 起，未实看渲染） |
| ANC 流程归集闭环首轮提议（2 题；one-architecture） | `sheets/ANC.json` | https://claude.ai/artifact/4hLPshAW69nzpxC9e6b2yd（2026-10-17 起，未实看渲染） |
| OBJD 业务对象定论（9 题；one-architecture） | `sheets/OBJD.json` | https://claude.ai/artifact/FezyF4PPr5mnPGcoxL5CxT（2026-10-17 起，未实看渲染） |
| OBDS 业务对象界定标准（5 题；one-architecture） | `sheets/OBDS.json` | https://claude.ai/artifact/G5cAu3TNBSRr3nt2aFqVJA（2026-10-17 起，未实看渲染） |
| ADR58 状态转换记录批准（1 题；one-architecture） | `sheets/ADR58.json` | https://claude.ai/artifact/BCGoTd7EeWU1g1Zr83obEy（2026-10-17 起，未实看渲染） |
| ADV1 对抗第 1 轮：对象、流程与度量口径（29 题；one-architecture） | `sheets/ADV1.json` | https://claude.ai/artifact/1GMstmFyvpk6pQJn1F5CVB（2026-10-19 起，未实看渲染） |
| ADV1R 对抗第 1 轮：范围题（5 题，不预选；one-rules 出题） | `sheets/ADV1R.json` | https://claude.ai/artifact/3f8GHHVKLPJ1EgJHuv6yhk（2026-10-19 起，未实看渲染） |
| ADR59 架构骨架登记网络 v5 批准（2 题，不预选；one-architecture） | `sheets/ADR59.json` | https://claude.ai/artifact/KbuuP7NiaDgzgMEpPDcqL7（2026-10-19 起，未实看渲染） |
| OBJFIN 业务对象清单定稿（14 题，不预选；one-architecture） | `sheets/OBJFIN.json` | https://claude.ai/artifact/DYwP9bkmzJHD1RNncJaaYa（2026-10-19 起，未实看渲染） |
| STKH 干系人与业务对象对应（10 题，不预选；one-architecture） | `sheets/STKH.json` | https://claude.ai/artifact/QD66je3YcbEgZToGPq9WwT（2026-10-19 起） |
| STKH2 干系人子类（7 题，不预选；one-architecture） | `sheets/STKH2.json` | https://claude.ai/artifact/L4Ges3vue2F33Zhh4gwF7h（2026-10-19 起） |
| B31R 行政事务规则要点（10 题，预选推荐；one-architecture） | `sheets/B31R.json` | https://claude.ai/artifact/VMfomUnRLmCHuQ9EfZr51i（2026-10-20 起） |
| GL6 用语确认第七批：交付、结算与人员用语（6 题，不预选；one-glossary） | `sheets/GL6.json` | https://claude.ai/artifact/3cPAbRYnkQjZLz1pgZqGEK（2026-10-23 起，未实看渲染） |
| GL7 用语确认第八批：签章、链接与文件保存用语（4 题，不预选；one-glossary） | `sheets/GL7.json` | https://claude.ai/artifact/Wj1G14p8ox4yVaca6EDgy3（2026-10-23 起，未实看渲染） |
| GL5 用语确认第六批：协议状态与资产卡片（2 题，不预选；one-glossary） | `sheets/GL5.json` | https://claude.ai/artifact/UgWURDEPwcMvA4hBZCa4Lw（2026-10-22 起） |
| GL3 用语确认第四批（11 题，不预选；one-glossary） | `sheets/GL3.json` | https://claude.ai/artifact/EFi9EDMikxT4Pa9c9oBr3m（2026-10-21 起） |
| A7475 行政事务模块与子状态机转换记录批准（ADR-0074、0075，2 题，不预选；one-architecture） | `sheets/A7475.json` | https://claude.ai/artifact/5VR57YayWQCsNZ4Pdjg4RF（2026-10-21 起，未实看渲染） |
| A7677 合同原件借阅与信息技术资产引用的先行安排批准（ADR-0076、0077 与外借审批人口径，3 题，不预选；one-architecture） | `sheets/A7677.json` | https://claude.ai/artifact/HTw3rQHuMSDNN7R17W1SQU（2026-10-21 起，未实看渲染） |
| SGN 签章服务未准入时行政事务的停工范围（1 题，不预选；one-architecture） | `sheets/SGN.json` | https://claude.ai/artifact/JZc7KyLZLB8VSmd6FM4qNd（2026-10-22 起，未实看渲染） |
| LEB 换法人后的合同与项目归属及四项待定规则（9 题，不预选；one-architecture） | `sheets/LEB.json` | https://claude.ai/artifact/Hk9eivZEUYvKjAvaMHYrH2（2026-10-22 起，未实看渲染） |
| KPI 指标口径待定（7 题，不预选；one-architecture） | `sheets/KPI.json` | https://claude.ai/artifact/VbwhsaaqdA9BTxpEVbUsqZ（2026-10-22 起，未实看渲染） |
| FCQ 贡献毛利基线、直接人工费率与工资补发的财务口径（7 题，不预选；one-arch-finance） | `sheets/FCQ.json` | https://claude.ai/artifact/LgVhQxMc6Rv11hS4Z8iSAc（2026-10-22 起，未实看渲染） |

| 步骤 | 做法 |
|---|---|
| 1. 写数据 | FD 改 `docs/business/finance-decisions.md`；其他单子写 `sheets/<单子>.json`，格式见下 |
| 2. 生成 | `python3 docs/decisions/decision-pages/build_decision_page.py <单子>[,<单子>...] <scratchpad>/<文件>.html` |
| 3. 发布 | 同一单子覆盖到上表链接（Artifact 带 `url`）；能力 `db`（集合 `answers`、`answers-submit` 只许 owner 写）+ `user`；**固定 `contract: "0.2.80"`**（0.2.81、0.2.82 在手机上白屏过，新版验证可用后再升）；发布后用内置浏览器核对真实渲染，没登录就报告「未实看」并请人先在手机上打开确认 |
| 4. 读答复与回写 | 用户说已提交后，ArtifactData `list` 集合 `answers` 与 `answers-submit`（同一个 `out_dir`），再 `python3 docs/decisions/decision-pages/decide_sync.py <单子> <out_dir> --session=<本会话名> --inbox-id=<收件箱事项 id>`（其余必填项见脚本头）：对账（有提交后又改过的题就失败，请用户重新提交）、写 `answers/<单子>.json`、按提交编号生成 intent 片段，并打印收件箱事项改为已定所需的 ArtifactData `update`，由会话对收件箱页执行（各页是独立发布件，确认页不能直接写收件箱）。只想导出答复不生成 intent 时用 `export_answers.py <单子> <out_dir>` |
| 5. 写回 | 答复写回规则卡（如 `rules/GL_FINANCE_DECISIONS.*`、`docs/business/finance-questions.md` 去向），用户原话记 intent；会计类经财务（ADR-0018） |

提交前页面只是草稿；「检查答案」先列出全部题的选择与按推荐（其中没动过的）、改选、暂不定、写了说明各几题，用户确认后写一份提交记录（含全部题的选择，没动过的题标 `defaulted`）。

## 出页守则

- 出页先审题：确认页做好先交 one-coordinator 派 opus 独立审题，按报告改完、调度复审后才进收件箱（status 写 `pending`）。题面过滤：设计方按原则能自定的不上页（页首列清单）；期限、天数类并成参数表，每值给出处，无出处写「经验值」；推荐理由引用户原话、规则卡或法规；已答的不再问（逐条核 intent 原文）；审批人按「这条命令的负责人」问；财务、审计、法务题由顾问给推荐；存储、选型题不预选。
- 手机与网络：只用系统字体栈，不引 fonts.googleapis 等任何境外资源；不在 html/body 设 overflow 隐藏，只在内容外框用 `overflow-x:clip`；一屏只画一组（≤120 条），快照回调合并重画，屏外条目 `content-visibility:auto`。
- 每条只给「同意建议 / 保持原样 / 改为…」几个按钮，每组可一键全部同意；点选只算草稿，须「检查答案」后提交才算答复。
- 发布后用浏览器打开核对渲染（先点一下页面使标签页在前台）。

## 用户答复的流程要点

原记忆 decision-pages-online、done-keyword 的流程部分并入此处（GOV-1023-FV2 G6，来源为两份记忆原文，2026-10-26 已由调度压缩）。链接不在本节写：单子页链接在上面的页面表，收件箱页链接在 `inbox/README.md`，登记在 `ONLINE_PAGES.md`。

- **做成在线页，不让用户导出**：用户常在手机上处理，导出文件再发回太麻烦，版式要简单。选择存在页面数据库，会话用 ArtifactData 读取；离线 HTML 只作仓库产物。答复只用一个集合 `answers` 加 `answers-submit`。
- **点选只算草稿，必须有提交步骤**：页面底部「检查答案」先汇总同意、保持、改为、未选各几条，用户确认后才写提交记录。只按已提交、且之后没改过的答复写回；未选算未定，不算答复。事故依据：2026-10-15 一页 365 条被用户快速连点，被会话当成答复，用户说并没有全部同意，该批作废重做。
- **固定链接，覆盖发布**：手机上新发布的页面链接可能加载不出，已能打开的固定链接正常，所以同一单子覆盖发布到既有链接，不另发新链接。新页面先在「在线页登记表」加一行。
- **手机与网络**：用户常在手机的移动网络下打开，外部站点未必连得上，所以不引用 Google 字体等任何外部资源，只用系统字体，否则页面会一直停在加载中；不在 html、body 上设 overflow 隐藏（手机端外壳会加载失败）。性能：外壳等页面就绪才显示，默认一屏只画一组（≤120 条），快照回调合并重画，屏外条目 `content-visibility:auto`，少订阅；一次画 410 条、重画 18 次时电脑要 15～20 秒、手机打不开，改后 3 秒内显示。
- **测试**：用用户已登录的浏览器打开固定链接，先点一下页面让标签页在前台再看（后台标签页本来不显示）；没登录就报告「未实看」。
- **收件箱事项状态**：新建事项 `status` 一律写 `pending`，页面只认 `pending`、`decided`、`withdrawn`；写 `open` 用户看不到（字段见 `inbox/README.md`）。
- **口令「答完」**（用户 2026-10-16 约定）：表示用户已在收件箱或某个确认页答完待办，因为用户常用手机、不想每次说明答了哪一页。收到后调度自己扫收件箱（集合 `inbox`，看 `decidedAt` 晚于上次核对的事项）和最近更新的确认页，把新答复逐条转给提出会话（要求记 intent），再用一句话回用户答了几条、分给谁；不反问是哪一页。

## 在线页登记表（ONLINE_PAGES.md）

每个在线页在仓库登记一行：用途、负责会话、退役日期（定稿 1.5 第 1 点，GOV-1023-FV2 G5）。登记表只登记，不删、不改任何在线页；旧页过期后的只读化或删除须用户批准（第四批）。`ONLINE_PAGES.md` 只放标题与表，说明都写在本节（G3，调度 10-27 晚定）。

- **整份白名单（`decide_sync.py` 解析）**：`ONLINE_PAGES.md` 每行只能是空行、`#` 标题行（行首 1～6 个 `#` 紧跟空格），或登记表行；其他任何行（说明文字、列表、引用、HTML、代码块、缩进行）decide_sync 都整份判损坏并报行号。登记表行行首 `|` 起、行尾 `|` 止，不缩进、不带尾随空白，恰好六格，格内不得有 `|`、`\`、`<`、回车符（不用 CRLF）；标题行也不得有 `<`（行内 HTML 能在页面上把一行登记拆成别的行或格）；全文只许一张连续的表，表后紧跟空行或文件尾。表头固定为「单子或页面、用途、负责会话、起用日期、退役日期、出处」六列，下一行是分隔行（各格为 -，可带对齐冒号）；单子行第一格是单子编号（整格匹配 `[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*`，下划线只能夹在中间）。改列顺序或列名，decide_sync 与其自测会失败关闭。
- **链接不在登记表重复**：单子页的链接在上面的页面表里（`decide_sync.py` 读它），其余在「出处」列的文件里（出处路径都相对 `docs/`）；登记表以单子编号或页面名对应。
- **负责会话**：登记时出题会话的名字，定不出写「roles/ 未列」（decide_sync 遇到即拒）；负责会话只核 policy.json 的 `sessions.config`，旧名先经 `sessions.aliases` 换成现名，换名后不在 `sessions.config` 即拒，不看 `roles/` 过渡映射。题与答复只经唯一决定页（本目录生成器）入决定库，与 RULES ⑨ 一致。第四批会话合并后的对应，只写 `roles/` 原文里有的：one-glossary 对应 language（`docs/rules/roles/design.md`），one-architecture 对应 design（`docs/rules/roles/design.md` 第 31 行），one-coordinator 对应 lead（`docs/rules/roles/lead.md` 第 32 行）。one-rules 与 one-arch-finance 是旧名，经 `sessions.aliases` 分别换成 one-coordinator、one-architecture。
- **退役日期**：写 `YYYY-MM-DD` 或 `待定`。现在全是 `待定`：没有任何文件或用户答复定过退役日期，这里不编；日期由用户在第四批批准旧页只读化或退役时定，定一页补一行。
- **新页必须先加一行**再发布；单子页的行与上面页面表的单子一一对应（核对：登记表前 36 行单子编号＝页面表 36 行）。登记表中没有单子 json 的行：FD（数据源是 `docs/business/finance-decisions.md`，不是 sheets/）；其余单子行均有 `sheets/<单子>.json`（2026-10-26 逐一核对）。
- 账号下的在线页总数（盘点称 161 个）仓库里没有清单，登记表只登记仓库里有记载的页（页面表 36 张、收件箱、固定确认页两个）；其余页由用户在账号侧列出后补登。
- `sheets/` 里的 GL、GL2、N1、NM1～NM6 共 9 个单子数据在页面表没有页面链接（答复在 `answers/` 里），仓库没有记载它们的在线页；为让 `decide_sync.py` 能按单子查负责会话，已在登记表登记（G3 要求每个有答复的单子都有登记），起用日期写「无记载」、用途取自单子标题、出处写单子文件；出题会话确认在线页后补链接与日期。用语类（GL、GL2、NM1～NM6）负责会话写 one-glossary，对应 `docs/rules/roles/design.md`；N1 由 one-rules 出题（提交说明 docs(rules)），负责会话写「roles/ 未列」（调度 10-26 深夜定）：decide_sync 对 N1 生成 intent 会拒，须先把这一格改成 `sessions.config` 里的会话名（旧名经 `sessions.aliases` 换名）。

## sheets/<单子>.json 格式

```json
{"sheet": "D5", "preselect": true, "title": "页面标题", "lede": "一句话：这张单子定什么",
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
- 决定页生成器只此一套（G2，2026-10-26）：`architecture/decision-page/` 的四个页面生成器与 `glossary/gate/` 的页面生成器已删；出题检查 `check_pages.py` 与已答登记 `answered_pages.txt` 在本目录。旧页题源留原处只读，`build_decision_page.py <旧页名> <输出>` 按单子名也找得到（先找 `sheets/`，再找 `architecture/decision-page/` 一层；其 `archive/` 不读，登记已答也不读），但只重出已答旧页（本目录 `answers/<页>.json` 有导出或 `answered_pages.txt` 已登记），未答的拒绝；不合现行校验的旧页照样拒绝出页。新页不得放进旧题源目录绕检查（one-coordinator 2026-10-16，见 `docs/decisions/decision-pages/archive/README.md`）。出页前跑 `python3 docs/decisions/decision-pages/check_pages.py <单子 json>`。
- 发布固定 `contract: "0.2.80"`；发布后核对真实渲染（见画像工作守则）。

## 预选与「未作答」

用户 2026-10-16 答「B」（经 one-coordinator）：业务口径页预选推荐项，框架、标准、选型类页不预选。两类的划分与出处：

| 类 | 例子 | 单子里 | 页面行为 | 出处 |
|---|---|---|---|---|
| 业务口径单 | 财务口径、工时审批、数据范围、回放差异、填报义务 | 缺省或 `"preselect": true` | 预选推荐项；没动过的题按推荐计，提交记录标 `defaulted` | 用户 2026-10-16 答「B」 |
| 框架、标准、选型类单子 | 架构方法、框架选型、标准采用、技术栈取舍 | **必须写** `"preselect": false` | 不选中任何选项；没作答的题记为「未作答」（提交记录 `choice: null`、`unanswered: true`，`decide_sync` 在原话里标「（未作答）」）；推荐项只留「推荐」标签 | ADR-0041 决定 6：决策页写明代价，不预选推荐项 |

- 拿不准算哪类时按框架、标准、选型类处理（不预选）。同一页上的单子 `preselect` 须一致（生成器校验）。
- 收件箱页不预选，不受此开关影响。已发布的旧页保持原样。

## 对账检查

调度报「待答」前先跑：`python3 docs/decisions/decision-pages/decide_sync.py --check <收件箱 out_dir> <单子>=<确认页 out_dir> ...`，先用 ArtifactData `list` 把收件箱与各确认页的集合导出到各自的 out_dir。它报出「收件箱仍是 pending、确认页已提交」与「确认页提交后又改过」的事项，有不一致时返回码 1。事项与单子的对应：事项里写 `sheet` 字段，或事项 `link` 等于上面页面表里的链接。
