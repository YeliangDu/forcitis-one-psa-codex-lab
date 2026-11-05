# Local stack (card #1478)

The whole system on one developer's Mac, in local containers and processes. Not the dev environment: nothing here reaches another machine.

```bash
tools/ops/local/local.sh up      # database container, migrations, backend :8080, web client :5173
tools/ops/local/local.sh down    # stop; the data stays
tools/ops/local/local.sh wipe    # stop and delete the database volume and the generated settings
tools/ops/local/local.sh status
tools/ops/local/local.sh e2e       # the Playwright smoke (headless) against the running stack: local login as a synthetic person, one page loads
```

Prerequisites: Docker running, Java (the version `backend/pom.xml` asks for), Node with pnpm, `curl`, `openssl`. Ports 8080, 5173 and 55432 free.

- `up` builds the backend, runs the Flyway migrations as the release pipeline does (profile `migrate,local`), then starts the backend (profile `local`) and the vite dev server (its `/api` proxy points at :8080).
- The database password is generated on the first `up` into `~/.forcitis-local/env` (mode 0600). It is never printed and never committed. Logs and process ids are in `~/.forcitis-local/`.
- Open `http://localhost:5173/`. The login page then also lists the accounts of this database under "本机登录"; picking one starts an ordinary session for it, with exactly the grants and data scope that account holds. `up` writes exactly one synthetic account, "Synthetic Admin" (roles employee, org_data_admin, business_admin, personnel_specialist; ordinary mode only; `bootstrap.sql`, idempotent, synthetic names only, never a migration). Everything else is seeded through the API as that account (card #1479).
- The local login exists only in the profile `local`. In every other profile its two paths are not mapped (the login page shows nothing). The profile refuses to start with `dev` or `prod`, or with a database that is not on this machine (`LocalProfileListener`). Never point this profile at a shared database.
- The clock: under the profile `local` the application's `Clock` is a settable one. Start it at a golden date with `LOCAL_CLOCK_START=2026-03-15T08:00:00Z tools/ops/local/local.sh up`, or move it while running: `GET /api/v1/platform/local-clocks` reads it, `POST /api/v1/platform/local-clocks:set` with `{"at":"2026-03-15T08:00:00Z"}` sets it (it then runs on at the real pace), `POST /api/v1/platform/local-clocks:reset` puts it back. Everything that takes its time from the application's `Clock` follows it (business "today", ids, session expiry); a database default such as `now()` does not.
- `e2e` installs the headless browser the first time (needs the network once), then runs `frontend/apps/web/e2e/*.e2e.ts` once, no retries.
