# Local stack (card #1478)

The whole system on one developer's Mac, in local containers and processes. Not the dev environment: nothing here reaches another machine.

```bash
tools/ops/local/local.sh up      # database container, migrations, backend :8080, web client :5173
tools/ops/local/local.sh down    # stop; the data stays
tools/ops/local/local.sh wipe    # stop and delete the database volume and the generated settings
# down and wipe refuse when the stack was started from another checkout (the message names it); add --force only when that stack's processes are gone
tools/ops/local/local.sh status
tools/ops/local/local.sh e2e       # the Playwright smoke (headless) against the running stack: local login as a synthetic person, one page loads
```

Prerequisites: Docker running, Java (the version `backend/pom.xml` asks for), Node with pnpm, `curl`, `openssl`. Ports 8080, 5173 and 55432 free.

`up` waits up to 600 s for the backend (`LOCAL_BACKEND_WAIT=<seconds>` changes it; a loaded machine can need minutes). If it gives up, the backend may still be starting: `local.sh status`, then `~/.forcitis-local/backend.log`.

- `up` builds the backend, runs the Flyway migrations as the release pipeline does (profile `migrate,local`), then starts the backend (profile `local`) and the vite dev server (its `/api` proxy points at :8080).
- The database password is generated on the first `up` into `~/.forcitis-local/env` (mode 0600). It is never printed and never committed. Logs and process ids are in `~/.forcitis-local/`.
- Open `http://localhost:5173/`. The login page then also lists the accounts of this database under "本机登录"; picking one starts an ordinary session for it, with exactly the grants and data scope that account holds. `up` writes two synthetic accounts (`bootstrap.sql`, idempotent, synthetic names only, never a migration): "Synthetic Admin" (roles employee, org_data_admin, business_admin, personnel_specialist; ordinary mode only, no `super_admin`) and "Synthetic Platform Admin" (roles super_admin, employee). Everything else is seeded through the API as that account (card #1479).
- The local login exists only in the profile `local`. In every other profile its two paths are not mapped (the login page shows nothing). The profile refuses to start with `dev` or `prod`, or with a database that is not on this machine (`LocalProfileListener`). Never point this profile at a shared database.
- The clock: under the profile `local` the application's `Clock` is a settable one. Start it at a golden date with `LOCAL_CLOCK_START=2026-03-15T08:00:00Z tools/ops/local/local.sh up`, or move it while running: `GET /api/v1/platform/local-clocks` reads it, `POST /api/v1/platform/local-clocks:set` with `{"at":"2026-03-15T08:00:00Z"}` sets it (it then runs on at the real pace), `POST /api/v1/platform/local-clocks:reset` puts it back. Everything that takes its time from the application's `Clock` follows it (business "today", ids, session expiry); a database default such as `now()` does not. Moving the clock backwards makes new ids (time-ordered) older than rows written before; a POST needs the XSRF header like any other: after logging in, make one GET (the cookie is refreshed by it) before the first POST. Setting the clock also creates the audit partitions from the new month on (24 months; an entry in a month without one is refused) and runs the daily jobs (due personnel and org-structure commands, countersign deadlines) once for the new date, which a real-time schedule never would.
- `e2e` installs the headless browser the first time (needs the network once), then runs `frontend/apps/web/e2e/*.e2e.ts` once, no retries.

- Administrator mode (card #1499): pages that need the platform permissions held only in the administrator mode (for example `/admin/account-grants`) cannot be opened by an ordinary session. Log in as "Synthetic Platform Admin", then take the explicit step `POST /api/v1/platform/sessions:enter-local-administrator-mode` (XSRF header as for any POST; the session id is rotated, use the cookie the response sets). It is the production step without the WeCom proof, which does not exist here: same one-hour limit, never extended, same audit entry and ALERT. It works only for a session of the local test login whose account holds `super_admin`; it exists only in the profile `local`. "Synthetic Admin" stays ordinary and gets 403 on those pages.
