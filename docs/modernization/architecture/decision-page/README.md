# 确认页数据（decision-page）

- 本目录 `*.json` 为待发布或在用的确认页题源，发布前须过 `check_pages.py`（precheck 对改动页执行）。
- `archive/` 为已答记录，不再发布、不改题（one-coordinator 2026-10-16 定）；`check_pages.py` 只查本目录一层，不含 archive/。
- 未答的收件箱页与各组确认页源（FIN_*、PPL_*、REV_NOW / REST / PARAMS / VS01–04、THR）已从 #365 撤出，随对齐后统一改版（closeout/PAGE_IMPACT.md）重建，届时必须过检查；撤出前版本见提交 c96edeaa。
