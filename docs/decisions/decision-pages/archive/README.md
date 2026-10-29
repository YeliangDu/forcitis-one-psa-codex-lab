# 确认页数据（decision-page）

- 本目录 `*.json` 与 `archive/*.json` 为旧确认页题源，冻结只读；新单子一律走 `rules/decide/`（G2 决定页合一，2026-10-26）。
- **新页不得放进本目录或 `archive/` 绕检查**（one-coordinator 2026-10-16 定，G2 按新结构重申）：新题只写 `rules/decide/sheets/<单子>.json`，出页前过 `rules/decide/check_pages.py`。`check_pages.py` 只查本目录一层的 `*.json` 与 `sheets/`，不含 `archive/`；所以 `rules/decide/build_decision_page.py` **不读 `archive/`**（登记已答也不读），只从本目录一层重出**已答**旧页（`rules/decide/answers/<页>.json` 有导出，或页名登记在 `docs/decisions/decision-pages/answered-pages.txt`），未答的拒绝出页。`answered_pages.txt` 只追加、须写收件箱或用户答复的出处，不得为出页而登记。
- 本目录的页面生成器（`build_approval_page.py`、`build_bpmn_page.py`、`build_rules_review_page.py`、`build_samples.py`）已删。用 `build_decision_page.py <页名> <输出>` 重出已答旧页时，只重出题目与作答，不含原页的图（泳道图、图例）与专用版式；不合现行校验（如恰好一个推荐）的旧页照样拒绝。
- 出题检查 `check_pages.py` 与已答登记 `answered_pages.txt` 已搬到 `rules/decide/`。
- `archive/` 为已答记录，不再发布、不改题（one-coordinator 2026-10-16 定）。
- 未答的收件箱页与各组确认页源（FIN_*、PPL_*、REV_NOW / REST / PARAMS / VS01–04、THR）已从 #365 撤出，随对齐后统一改版（closeout/PAGE_IMPACT.md）重建，届时必须过检查；撤出前版本见提交 c96edeaa。
