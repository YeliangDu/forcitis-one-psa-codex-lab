# Frontend: the home areas and the shared page pieces — design (#1516)

- Card: #1516. Author: one-design, 2026-11-06. Base: origin/main.
- Status: proposal. No code, no migration. Implementation is the follow-up cards in section 7.
- Evidence marks: **[V]** = read on origin/main today; **[U]** = not checked or a judgement.
- Source of the intent: user 2026-11-05 23:58 「补横向，按顺序来」 via lead (card text). Pilot S1 (#1479) and its gaps #1491 #1498 #1499 #1500 are the consumers.

## 1. The one thing to decide

Before slice S2: (a) how a task page plugs into a role's home so that a later layout change is configuration, not page rewrites; (b) which page pieces every slice reuses, so S2 pages are not copies of the S1 pages.

**Decision (recommended, section 3):** the home is not a new screen. It stays the navigation tree the server already builds, with three top-level groups for the three areas; a page plugs in with one route and one navigation row; the one new frontend seam is a **document slot** on the approval task page. Seven small shared pieces carry the repeated page code (section 6).

## 2. Verified facts

1. **Navigation is already data and already permission-driven.** `platform_identity.navigation_items` (item id, parent, name, route path, `permission_code`, sort order) is read by `NavigationTree.visibleTo(permissionCodes, items)`: an item shows only when the user holds its permission code, a directory with nothing visible disappears, depth is capped at 8 [V: `backend/src/main/java/com/forcitis/one/platform/identity/domain/NavigationTree.java:19-50`]. The web shell gets it as `user.navigation` and maps it to `AppShell` entries [V: `frontend/apps/web/src/app/app-layout.tsx:25`, `navigation.ts`]. No role name appears in the frontend; pages that gate a button use `session.can(permissionCode)` [V: `numbering-rules-page.tsx`, `packages/core/src/session/hooks.ts:23-31`].
2. **Today's tree is two roots:** `待办` (`/inbox`) and `系统管理`, holding 角色管理, 编号规则, 组织浏览, 同步记录 (deleted), 员工名册, 人事命令, 授权申请, 待调整权限 [V: migrations `V20261104190000…`, `V20261104220000…` and the earlier seed]. Work calendars and the numbering pages are reached by route, the roster by the 系统管理 row. The index route redirects to `/inbox` [V: `app-routes.tsx:35`].
3. **To-dos exist:** the approval inbox, the done list and the task page with claim/approve/reject/return [V: `modules/approval`]. The task page shows process, document type and id, nodes, initiator, summary and history; it has **no place for the business document itself** (`documentText` = type label + id) [V: `task-detail-page.tsx`, `packages/core/src/approval/model.ts:72`].
4. **"What I started" has no read.** `ApprovalProcesses` offers start, isFinished, progress by (process, document), checkWithdraw, withdraw, cancel; the task endpoints are inbox, handled records and an all-tasks overview. Nothing lists the approvals a user initiated [V: `platform/approval/api/ApprovalProcesses.java`, `web/*Controller.java`]. **MISSING** (backend).
5. **"What I can look up"** has no home area: the read-only pages (roster, org units, work calendars) sit inside 系统管理 beside the administration pages [V: fact 2].
6. **Name clash:** the glossary term `workbench` (`workbench`, 企业微信工作台) is the WeCom web entry of `platform.integration` [V: `docs/glossary/terms.yaml:6211`]. A frontend module, route or UI label called "workbench" would collide with it.
7. **Shared pieces today** (`frontend/packages/ui/src`): app-shell, button, data-display (`DataTable`, `DescriptionList`), feedback (`Dialog`, `EmptyState`, `LoadingState`, `Notice`), form-fields (`TextField`, `SelectField`, `TextAreaField`), inbox-list, login-panel, page-title, result-page, status-badge, text-link [V: `index.ts`]. `ui` depends only on design-system, antd and react, so it takes strings and callbacks, never queries or `describeFailure` [V: `packages/ui/package.json`].

## 3. Options for the home (choose one; mine to choose)

| Option | What it is | For | Against |
|---|---|---|---|
| A. Three navigation groups, no landing page (recommended) | Seed three root directories (my to-dos, what I started, what I can look up) and move existing rows under them by migration; `/` stays on the to-do list | Uses what exists; a layout change is a migration of rows; no new screen, no new code path to maintain | Area 2 is empty until the backend read exists (a directory with nothing visible disappears, so nothing broken shows) |
| B. A landing page with three cards | New page that reads the same tree and shows area cards with counts | A friendlier first screen | New screen and new reads (counts) for no function the tree lacks; revisit when two areas hold real content |
| C. Frontend manifest | Each module exports its entries in code; the shell builds the tree | Entries live next to pages | Second source of truth beside `navigation_items`; role visibility would move into code or need the server twice |

**Choice A.** Reason: the tree is the only place where "who sees what" is decided, and it already is configuration. Area names are navigation row names (data), not frontend text.

## 4. How a page plugs in

1. **Any page** = one route in `app-routes.tsx` plus one `navigation_items` row (name, route, the permission code the page's read already requires, sort order, parent = the area). Hidden child pages (detail, form) have a route and no row. [U: a check that every top-level route has a row or a stated reason would be a new guard; not proposed now.]
2. **To-do entry** = an approval task. The inbox already lists any process's tasks; a slice adds nothing to the list. What a slice adds is the **document slot** on the task page: a small registry in the approval module, keyed by `documentType`, whose value is a component receiving the document id. The task page renders the registered component between the summary and the action bar, and falls back to today's summary when none is registered. A slice registers in its module's `index.ts`. Approval actions, history and permissions stay in the shared page; the module component only reads and shows its document (read-only).
3. **Start entry** = a navigation row to the module's form route (as `/admin/personnel-commands/new` today). It shows to users holding the create permission, because the row carries it.
4. **Look-up entry** = a navigation row to a list route under the look-up area, bound to the read permission.
5. **Role-specific views** need no role names: the same tree filtered by the user's permission codes. Which role holds which codes is the permission catalogue and the lead's separate role-to-task mapping, not this design.

## 5. Naming and wording (open for lead)

- The three area names are the card's words: 我的待办 / 我发起的 / 我能查阅的 [U]. They become `navigation_items.name` values; the copy glossary check (`check:copy`) reads frontend source, not seeds, but the names should still be registered terms. 待办 exists (`todo`, `inbox`); the other two need checking.
- Internal names must not use `workbench` (fact 6). Suggested module/folder name: `home` or `my-work` [U].
- 系统管理 keeps the administration pages (roles, numbering rules, authorization requests, permission adjustments); the roster, org units and work calendars move to the look-up area. That is a judgement on the page's purpose, not a permission change; the permission codes stay.

## 6. Inventory of repeated patterns

Consumers are pages under `frontend/apps/web/src/modules/` (path abbreviated to `module/page`). "In ui?" = already in `packages/ui`.

| Pattern | Existing pages that repeat it | In ui? | Proposed shared piece |
|---|---|---|---|
| Query guard for a detail page: loading state, then error notice through `describeFailure`, then the content | workforce/employee-detail, workforce/personnel-command-detail, identity/permission-adjustment-detail, approval/task-detail, identity/authorization-requests, numbering/numbering-rules, attendance/work-calendar, approval/inbox (8; plus 4 forms/lists with the same two lines) | `LoadingState`, `Notice` only | `QueryBoundary` in `apps/web/src/shared/` (it uses `describeFailure` from core, so not in `ui`): props query, error title, render function |
| List section: pending, error notice, table, "显示更多" for the next page | workforce/employee-roster, workforce/personnel-command-list, identity/permission-adjustment-list (3 with the load-more button; approval/inbox has it too in its own list) | `DataTable`, `Button` | `PagedTable` in `apps/web/src/shared/`: query result in, table and load-more out |
| Mutation error notice with a per-action title | personnel-command-detail (撤回失败), permission-adjustment-detail (维持授权失败, 标记已调整失败), authorization-requests, task-detail (操作失败) (4) | `Notice` | `MutationNotice` beside `QueryBoundary` (same file family) |
| Reason dialog: title, confirm text, danger flag, textarea, required rule, error notice | approval/task-action-bar (`REASON_RULES`), workforce/personnel-command-detail (撤回), identity/permission-adjustment-detail (维持授权) (3; authorization-requests is the same dialog without a reason) | `Dialog`, `TextAreaField` | `ReasonDialog` in `ui` (props: title, confirm text, label, required, danger, loading, error slot, onConfirm(reason)) |
| Router link: `TextLink` with `href` and `onNavigate={() => navigate(path)}` | employee-roster, employee-detail (2), personnel-command-list, personnel-command-detail, permission-adjustment-list (6 pages, 8 sites) | `TextLink` | `RouterLink` in `apps/web/src/shared/` (`ui` must not import the router) |
| Status badge from a label map and a tone | employee-roster, employee-detail, personnel-command-list, personnel-command-detail, permission-adjustment-list/detail, task-detail (7) | `StatusBadge` | `badgeOf(labels, tones, key)` helper in core's `labelOf/toneOf` style; not a component |
| Select options with a leading "全部" or "请选择" | personnel-command-list (3 selects), permission-adjustment-list (2), employee-roster (2), personnel-command-form (5) (4 pages) | `SelectField` | `optionsOf(items, valueOf, labelOf, empty)` helper in `shared/` |
| Title with status and actions on the right, then a description list | employee-detail, personnel-command-detail, permission-adjustment-detail, task-detail (4) | `PageTitle`, `DescriptionList` | **not shared yet** (already two ui pieces; the arrangement differs per page) |
| Filter row of selects | employee-roster, personnel-command-list, permission-adjustment-list (3) | `SelectField` | **not shared yet** (layout only, no behaviour) |
| Permission-gated action | numbering-rules (1) | none | **not shared yet** (one consumer) |
| Edit form with draft state and field-by-field setters | personnel-command-form (the only large form); numbering-rules, work-calendar (small dialogs) | `TextField`, `SelectField` | **not shared yet** (S2 forms will show whether a form helper repeats) |

## 7. Implementation slicing (follow-up cards, one behaviour each; file scope per PR)

Order matters: slices 1, 2 and 5 are what S2 pages need first.

1. **Query guard** — add `shared/query-boundary.tsx` (`QueryBoundary`, `MutationNotice`) and its test; move employee-detail, personnel-command-detail, permission-adjustment-detail onto it. Existing tests unchanged and green.
2. **Reason dialog** — add `ui/src/reason-dialog.tsx` and test; move task-action-bar, personnel-command-detail (撤回), permission-adjustment-detail (维持授权).
3. **Paged table + router link** — `shared/paged-table.tsx`, `shared/router-link.tsx`; move the three list pages and the link sites.
4. **Option and badge helpers** — `shared/options.ts`, badge helper in core; move the four pages.
5. **Document slot** — registry in `modules/approval` (`registerTaskDocument`), the task page renders the slot, falls back to the summary; test: registered type renders, unregistered shows the fallback. No business module registers in this PR.
6. **Navigation areas (data only)** — one migration seeding the three directories and re-parenting the existing rows; needs the names of section 5. No frontend change. The area "what I started" stays empty until slice 7.
7. **"What I started" read** — a platform.approval read of instances by initiator (backend card, design needed: it is a new api method, count against the module's G4 limit) and a list page. Not before slice 6.

Each PR keeps behaviour; the move PRs (1 to 4) change no visible text.

## 8. Questions for lead

| # | Question | Options | Recommendation |
|---|---|---|---|
| H1 | Area names and which existing pages go to the look-up area | the card's three names; other words | Use the card's words after the glossary check; move roster, org units and work calendars to look-up |
| H2 | Landing page now or later | B now; A only | A only; add B when two areas hold content |
| H3 | Internal name for the feature (the glossary term `workbench` is the WeCom entry) | `home`, `my-work` | `my-work` |
| H4 | Backend read for "what I started" | in the approval module (new api method) or each module lists its own documents | approval module, one read by initiator; modules keep their own lists for admin views |

None blocks slices 1 to 5.

## 9. Done-when (for the test engineer)

1. Slice 1 to 4: every moved page's existing test passes unchanged; no page imports `describeFailure` for the moved guard or notice; `pnpm typecheck` and the `check:*` scripts pass.
2. Slice 2: the reason dialog refuses confirm until the required reason is non-empty and shows the error slot content; the personnel-command withdraw flow behaves as before.
3. Slice 5: a task whose document type has a registered component renders it between summary and action bar; a task of an unregistered type renders the current summary; the approval actions are unaffected.
4. Slice 6: a user holding only the read permission of a look-up page sees the look-up directory and not the administration one; a user holding none sees neither; no role name occurs in any changed file.
5. No new visible Chinese text outside registered terms and existing page text.

## 10. Out of scope

Role-to-task mapping (lead's separate draft), the permission catalogue, H5 (`apps/h5`), any business form, copying the legacy or golden-pack screens, a landing page, a route-to-navigation guard.
