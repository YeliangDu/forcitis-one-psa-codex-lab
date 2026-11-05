# Golden mainline, pilot slice S1 (card #1479)

Seed runner and per-node Playwright specs for nodes HCM-LIFE-007, 009, 016, 022, 023 on the local stack (card #1478). Synthetic data only; the pack's expected values are NOT_REVIEWED and are never bent to the code: a mismatch is a gap below.

## Seed runner

```bash
python3 -I frontend/apps/web/e2e/golden-seed/seed_s1.py --stop-before 007|009|016|022|023   # everything before the node through the public HTTP API
python3 -I frontend/apps/web/e2e/golden-seed/seed_s1.py --no-clock ...                       # leave the clock alone (start the stack with LOCAL_CLOCK_START)
```

It logs in as "Synthetic Admin" (local login), builds the small world, and refuses any host but localhost. It is re-runnable.

## Per node (role, client, page, steps, expected = pack, actual = run on #1478 bb0e540ca with #1491 5e31db672 and #1498 4c6698bba merged locally)

| Node | Start state | Role / client | Page and steps | Expected (pack) | Actual |
|---|---|---|---|---|---|
| 007 | `--stop-before 007`, clock 2026-01-05 | personnel specialist (Synthetic Admin) / web | 发起人事命令: 入职, name, email, date, 用人单位, Nanjing unit, junior post, fee earner 是, phase 试用, 提交 | one employee, one relationship, in service, one primary post | pass with #1491 (employer field). Without it: 提交失败, server 422 |
| 009 | after 007 | F-P01 herself; Synthetic Admin / web | F-P01: local login, 待办, `/admin/employees`; admin: `/admin/account-grants`, 用户账号, column 来源 | ordinary access works, source traceable, no administrator access | F-P01 part: pass. Source via grant records (`GET /api/v1/platform/account-role-grants`, as Synthetic Platform Admin after the explicit admin-mode step of #1512, stable over 3 runs): pass (employee, channel DEFAULT_ROLE, grantedBy null). Source on the page `/admin/account-grants`: **FAIL until PR #1498 merges** (main has no such route: 页面不存在); the admin mode needs the page parked before the step (see support file), and moving the clock back before the mode end is refused (409), so the mode window of one hour matters for later nodes |
| 016 | `--stop-before 016`, clock 2026-04-05 | personnel specialist / web | 发起人事命令: 转正, employee, date, 提交 | same relationship, primary post unchanged | pass (command 已生效, junior post and Nanjing unit unchanged) |
| 022 | `--stop-before 022`, clock 2026-06-15 | personnel specialist / web | 调岗 with date 2026-07-01, senior post | on 06-30 still the old post; two decisions stored separately | post part: pass (command 待生效, old post still shown). Salary decision SAL-A-V2 is not built in S1 (user ruling b): known gap, pack value unchanged, not asserted |
| 023 | after 022; the spec sets the clock to 2026-07-01 | personnel specialist / web | set clock (due jobs run once), sign in again, 人事命令 filtered 调岗, open the employee | current primary post is the senior one, old post readable | pass (transfer 已生效, none 待生效, senior post shown, junior post gone). Earlier post history: assertion added for the 主岗历史 table (card #1495, PR #1500), not yet run (needs #1500 merged) |

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
| (stack) | Cold start on a loaded machine takes over 180 s, so `up` reports the backend as not up although it comes up later | #1478 |
