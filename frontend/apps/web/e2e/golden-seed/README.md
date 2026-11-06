# Golden mainline, pilot slice S1 (card #1479)

Seed runner and per-node Playwright specs for nodes HCM-LIFE-007, 009, 016, 022, 023 on the local stack (card #1478). Synthetic data only; the pack's expected values are NOT_REVIEWED and are never bent to the code: a mismatch is a gap below.

## Seed runner

```bash
python3 -I frontend/apps/web/e2e/golden-seed/seed.py --list                              # the supported nodes (the NODES table in the runner): pack day, what they need, what they do
python3 -I frontend/apps/web/e2e/golden-seed/seed.py --stop-before <node>                # everything the node builds on, through the public HTTP API; then a read-back check against the pack
python3 -I frontend/apps/web/e2e/golden-seed/seed.py --no-clock ...                       # leave the clock alone (start the stack with LOCAL_CLOCK_START)
```

Supported nodes (card #1518): 007, 009, 016, 022, 023 (slice S1) and 026, 027, 030, 031 (S2 candidates); a node outside the table is refused with the first missing step named. 024, 025, 028, 029 belong to other owners (leave, identity, competency, payroll), change no workforce state and are listed as "not seeded". After a run the runner reads the person back and prints each check as ok, MISMATCH or "not readable": the pack checkpoints for relationship count and working state (026, 027, 030) have no public read, so they are listed, not verified (gap for the dev side). The checks compare with the current stack, so a run on a stack that is already past the node shows MISMATCH: wipe first.

It logs in as "Synthetic Admin" (local login), builds the small world, and refuses any host but localhost. It is re-runnable.

## Per node (role, client, page, steps, expected = pack, actual = run on #1478 bb0e540ca with #1491 5e31db672 and #1498 4c6698bba merged locally)

| Node | Start state | Role / client | Page and steps | Expected (pack) | Actual |
|---|---|---|---|---|---|
| 007 | `--stop-before 007`, clock 2026-01-05 | personnel specialist (Synthetic Admin) / web | 发起人事命令: 入职, name, email, date, 用人单位, Nanjing unit, junior post, fee earner 是, phase 试用, 提交 | one employee, one relationship, in service, one primary post | pass with #1491 (employer field). Without it: 提交失败, server 422 |
| 009 | after 007 | F-P01 herself; Synthetic Admin / web | F-P01: local login, 待办, `/admin/employees`; admin: `/admin/account-grants`, 用户账号, column 来源 | ordinary access works, source traceable, no administrator access | F-P01 part: pass. Source via grant records (`GET /api/v1/platform/account-role-grants`, as Synthetic Platform Admin after the explicit admin-mode step of #1512, stable over 3 runs): pass (employee, channel DEFAULT_ROLE, grantedBy null). Source on the page `/admin/account-grants`: **FAIL until PR #1498 merges** (main has no such route: 页面不存在); the admin mode needs the page parked before the step (see support file), and moving the clock back before the mode end is refused (409), so the mode window of one hour matters for later nodes |
| 016 | `--stop-before 016`, clock 2026-04-05 | personnel specialist / web | 发起人事命令: 转正, employee, date, 提交 | same relationship, primary post unchanged | pass (command 已生效, junior post and Nanjing unit unchanged) |
| 022 | `--stop-before 022`, clock 2026-06-15 | personnel specialist / web | 调岗 with date 2026-07-01, senior post | on 06-30 still the old post; two decisions stored separately | post part: pass (command 待生效, old post still shown). Salary decision SAL-A-V2 is not built in S1 (user ruling b): known gap, pack value unchanged, not asserted |
| 023 | after 022; the spec sets the clock to 2026-07-01 | personnel specialist / web | set clock (due jobs run once), sign in again, 人事命令 filtered 调岗, open the employee | current primary post is the senior one, old post readable | pass (transfer 已生效, none 待生效, senior post shown, junior post gone). Earlier post history: assertion added for the 主岗历史 table (card #1495, PR #1500), not yet run (needs #1500 merged) |
| 026 | `--stop-before 026`, clock 2026-08-10 | personnel specialist / web | 发起人事命令: 停薪留职开始, employee, date 2026-08-10, 提交; open the employee | relationship kept, the person is on leave | command: pass (已生效, primary post unchanged). Visible leave state: **FAIL**: the employee page and the roster still show only 在职; nothing but the command says the person is on leave. Pack counters active_relations 1 / working_relations 0: internal, not checked |
| 027 | `--stop-before 027`, clock 2026-08-17 (the spec sets it again) | personnel specialist / web | 停薪留职结束, employee, date 2026-08-17, 提交; open the employee | same relationship, person works again | pass (已生效, 在职, senior post unchanged, no 停薪留职 text). That the relationship is the same one: relation_count 1 / working_relations 1 internal, not checked |
| 030 | `--stop-before 030`, clock 2026-10-01 | personnel specialist / web | 调岗, employee, date 2026-10-01, Shanghai unit, lighting support post, 提交; open the employee | new primary post, same employer, relationship unchanged | transfer: pass (已生效, Shanghai unit and lighting support post shown). History (主岗历史, #1495): **FAIL until PR #1500 is on main**. relation_count 1 (ER-001-A unchanged): internal, not checked |
| 031 | waits for card #1520 | | | authorization change | not written yet |

## Playwright specs

`node-0NN.golden.ts` use roles and accessible names; they need the stack from #1478 and are not part of the plain `playwright test` run (own config, suffix `.golden.ts`). Run one node: from `frontend/apps/web`, `npx playwright test -c e2e/golden-seed/playwright.config.ts e2e/golden-seed/node-007.golden.ts`. The `EMP000001` employee code in node 016 holds for a freshly wiped database.

## Gap list (to lead; no fixes in this card)

| Node | What the UI cannot do or does wrong | Likely owner |
|---|---|---|
| 007 | Closed by #1491 (employer field); was: the hire form had none and failed with 「人事命令的内容不完整」 | frontend (workforce) |
| 009 | The grant-source page is not on main yet (PR #1498 open); the account of #1512 can read the records through the API | identity (#1498) |
| 009 | The relationship as the source of the grant is not shown, only the grant channel (known gap, #1496 ruling) | identity |
| 022 | No salary decision (SAL-A-V2) to register next to the post: not built in S1 (user ruling b, fragment in #1497); pack value unchanged | payroll, later slice |
| 023 | Closed by #1486: setting the clock runs the due jobs once (also creates the audit partitions); a session ages out with the clock, sign in again after moving it | #1478 |
| 023 | Closed by #1500 (主岗历史 table), spec extended, run after it merges; the old row's 失效日 is asserted as the transfer day 2026-07-01 as stated by the implementer | frontend (workforce) |
| 026 | No page tells that the person is on unpaid leave: employee detail and roster show 在职 only after 停薪留职开始 is 已生效 | frontend (workforce): employee detail/roster, state 停薪留职 |
| 030 | 主岗历史 on the employee page needs PR #1500 | frontend (workforce) |
| (stack) | Cold start on a loaded machine takes over 180 s, so `up` reports the backend as not up although it comes up later | #1478 |
