# 确认页数据（decision-page）

- 本目录 `*.json` 与 `archive/*.json` 为旧确认页题源，冻结只读；新单子一律走 `rules/decide/`（G2 决定页合一，2026-10-26）。
- 本目录的页面生成器（`build_approval_page.py`、`build_bpmn_page.py`、`build_rules_review_page.py`、`build_samples.py`）已删；要重出旧页，用 `rules/decide/build_decision_page.py <页名> <输出>`，它按页名找得到本目录与 `archive/` 的题源。
- 出题检查 `check_pages.py` 与已答登记 `answered_pages.txt` 已搬到 `rules/decide/`；`check_pages.py` 仍查本目录一层的 `*.json`，不含 `archive/`。
- `archive/` 为已答记录，不再发布、不改题（one-coordinator 2026-10-16 定）。
- 未答的收件箱页与各组确认页源（FIN_*、PPL_*、REV_NOW / REST / PARAMS / VS01–04、THR）已从 #365 撤出，随对齐后统一改版（closeout/PAGE_IMPACT.md）重建，届时必须过检查；撤出前版本见提交 c96edeaa。
