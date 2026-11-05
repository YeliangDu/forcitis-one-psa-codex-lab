# Registered read-only forensic entry (card #1377)

The one command agents use for production reads (ADR-0037 decisions 5 and 7; user 2026-11-05: "agree to register a read-only forensic entry; whenever it is read-only I no longer decide"):

```bash
python3 tools/ops/dba/forensic_read.py --target <name> --session <session> --purpose "<text>" --delete-at "<when the local file is deleted>" --out <file outside every git work tree> <sql-file>
```

- Sent: one SELECT only (comments removed; no write, EXEC, INTO, second statement, batch separator, linked-server name). Anything else is refused before the config is read, a register line is written or anything is sent. `tools/ops/dba/OneSql.java ro` checks again on the server and rolls back.
- Account: the ADR-0037 forensic read-only account. Its credential stays on the servers (encrypted on the secret host to a throw-away key of the app host, decrypted only in the Java process). The first run per target and every 30 days first checks login, database and effective permissions (the platform baseline in `docs/ops/legacy-read/2026-10-15-000001-存量迁移.md`); a mismatch stops the run.
- Result: written to `--out` (mode 0600, never inside a git work tree); the terminal shows only a row count. Row-level and personal data stay out of sessions and repo: queries return names, types, counts or aggregates (groups under 5 people suppressed), and lead reads every query before it runs (card #1373 rules).
- Register: each run first writes one fragment `docs/ops/legacy-read/<date>-<time>-<session>.md` (account, target and tables, purpose, output form, deletion time); commit it with the card's PR.
- No other production command: the prod-write gate still refuses any command text that holds a production fingerprint. This command line holds none.

## Local config (outside the repo)

Path: `~/.config/forcitis-forensic/config.json`, mode 0600 (the entry refuses a file others can read). Created by the user once; the entry never prints it.

| field | meaning |
|---|---|
| `aliyun_profile`, `region` | aliyun CLI profile and region used for the cloud assistant |
| `app_host` | instance that runs the Java client (the host allowed to reach the database) |
| `secret_host` | instance that holds the forensic credential file |
| `app_work_dir`, `secret_work_dir` | temporary directories on those hosts (removed at the end of each run) |
| `secret_file` | path of the credential file on `secret_host` (key `EVIDENCE_DB_PASSWORD`) |
| `app_jar_process` | file name of a running application jar on `app_host` from which the JDBC driver is taken |
| `account` | forensic account login name |
| `targets` | `{ "<name>": { "database": "<database name>" } }`, e.g. one entry per database |

Known limits: the instance ids appear in the argv of the `aliyun` child process (not in the repo, the command line you type, or output); U8C has no forensic account in this setup and is not a target until one is registered by the user.
