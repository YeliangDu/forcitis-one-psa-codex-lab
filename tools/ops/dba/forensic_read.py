#!/usr/bin/env python3
"""Registered read-only forensic entry for production (card #1377; ADR-0037 decisions 5 and 7). The only command that reaches production for agents.

  python3 tools/ops/dba/forensic_read.py --target <name> --session <session> --purpose <text> --delete-at <text> --out <file> <sql-file>

The prod-write gate matches production fingerprints in the command text. This command line carries none: profile, region, instances, database names,
account and server paths are read from ONE local file outside the repo, ~/.config/forcitis-forensic/config.json (mode 0600; fields in docs/ops/forensic-entry.md).
The forensic credential never leaves the servers: it is encrypted on the secret host with a throw-away public key of the app host and decrypted only inside the
Java process there. Nothing the server prints is shown here: the result is written to --out (outside any git work tree) and only counts are printed.

What is sent: the SQL file after validate_select (one SELECT, comments removed, no write/EXEC/INTO/second statement); the Java side (tools/ops/dba/OneSql.java ro)
checks again and rolls back. Each run first appends one register fragment (docs/ops/legacy-read/) without row data. The first run per target (and every 30 days)
verifies the forensic account and stops when it is not the expected read-only one.
"""
import base64
import datetime
import gzip
import json
import os
import re
import secrets
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
CONFIG = Path.home() / ".config" / "forcitis-forensic" / "config.json"
ONE_SQL = HERE / "OneSql.java"
MAX_SQL_BYTES = 64 * 1024
RECHECK_DAYS = 30
# Effective database permissions of the forensic account (docs/ops/legacy-read fragment 2026-10-15, platform baseline): anything else, or no SELECT, stops the run.
EXPECTED_PERMISSIONS = {"CONNECT", "KILL DATABASE CONNECTION", "SELECT", "SHOWPLAN", "VIEW ANY COLUMN ENCRYPTION KEY DEFINITION",
                        "VIEW ANY COLUMN MASTER KEY DEFINITION", "VIEW DATABASE STATE"}
CHECK_DBS_SQL = "SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1"
SYSTEM_DATABASES = {"master", "msdb", "rdscore", "tempdb"}  # guest access the platform gives every login (2026-10-15 baseline)
CELL_LIMIT, DEFINITION_CELL_LIMIT = 300, 2000  # longest cell a result may hold; --definitions raises it for form/field definition text
PERSONAL = {"id number": r"(?<!\d)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\d)", "mobile number": r"(?<!\d)1[3-9]\d{9}(?!\d)", "email address": r"[\w.+-]+@[\w-]+\.[\w.-]+"}
CHECK_SQL = "SELECT SUSER_SNAME() AS login_name, DB_NAME() AS current_db, permission_name FROM fn_my_permissions(NULL, 'DATABASE')"
FORBIDDEN = re.compile(r"\b(INTO|INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|GRANT|DENY|REVOKE|EXEC|EXECUTE|DECLARE|SET|USE|BEGIN|COMMIT|ROLLBACK|SAVE|"
                       r"IF|WHILE|GOTO|RETURN|TRY|CATCH|PRINT|RAISERROR|THROW|WAITFOR|BACKUP|RESTORE|SHUTDOWN|KILL|DBCC|RECONFIGURE|BULK|WRITETEXT|UPDATETEXT|"
                       r"OPENROWSET|OPENDATASOURCE|OPENQUERY|OPENXML|GO|UPDLOCK|XLOCK|HOLDLOCK|TABLOCKX|PAGLOCK|NEXT\s+VALUE|(sp|xp)_\w*)\b", re.I)


class Refused(Exception):
    """The SQL, the arguments or the environment is refused; the message holds no identifiers."""


def validate_select(text):
    """The text to send: `text` with comments removed, when it is one single SELECT statement; else Refused. Strings and quoted names are skipped when scanning keywords."""
    if len(text.encode("utf-8")) > MAX_SQL_BYTES or re.search(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", text):
        raise Refused("SQL file too large or holds control characters")
    clean, code, i, n = [], [], 0, len(text)
    while i < n:
        two, ch = text[i:i + 2], text[i]
        if two == "--":
            i = text.find("\n", i) if text.find("\n", i) >= 0 else n
            clean.append(" ")
            code.append(" ")
        elif two == "/*":
            end = text.find("*/", i + 2)
            if end < 0 or "/*" in text[i + 2:end]:
                raise Refused("unterminated or nested block comment")
            i = end + 2
            clean.append(" ")
            code.append(" ")
        elif ch in "'\"[":
            close = "]" if ch == "[" else ch
            j = i + 1
            while True:
                j = text.find(close, j)
                if j < 0:
                    raise Refused("unterminated string or quoted name")
                if text[j + 1:j + 2] == close:
                    j += 2
                    continue
                break
            clean.append(text[i:j + 1])
            code.append("''" if ch == "'" else "[]")
            i = j + 1
        else:
            clean.append(ch)
            code.append(ch)
            i += 1
    sql, masked = "".join(clean).strip(), "".join(code).strip()
    if masked.endswith(";"):
        sql, masked = sql[:-1].rstrip(), masked[:-1].rstrip()
    if not masked or ";" in masked or not re.match(r"SELECT\b", masked, re.I):
        raise Refused("not exactly one SELECT statement")
    if FORBIDDEN.search(masked) or re.search(r"^\s*:|\$\(", masked, re.M) or re.search(r"[\w\]]+\.[\w\[\]]*\.[\w\[\]]*\.[\w\[]", masked):
        raise Refused("SQL holds a keyword or construct that is not a plain read (write, EXEC, INTO, batch separator, linked-server name)")
    return sql


def tables_of(sql):  # best-effort table list for the register line; names only
    names = re.findall(r"\b(?:FROM|JOIN)\s+([\w.\[\]#\"]+)", sql, re.I)
    return ", ".join(sorted(dict.fromkeys(n for n in names if not n.startswith("(")))) or "(no table reference)"


def load_config():
    try:
        if CONFIG.stat().st_mode & 0o077:
            raise Refused("config file must not be readable by group or others (chmod 600)")
        conf = json.loads(CONFIG.read_text(encoding="utf-8"))
    except OSError:
        raise Refused(f"config file missing or unreadable; see docs/ops/forensic-entry.md for its path {CONFIG.parent.name}/{CONFIG.name} and fields")
    except ValueError:
        raise Refused("config file is not valid JSON")
    need = ("aliyun_profile", "region", "app_host", "secret_host", "app_work_dir", "app_jar_process", "secret_file", "secret_work_dir", "account", "targets")
    if not isinstance(conf, dict) or any(not conf.get(k) for k in need) or not isinstance(conf["targets"], dict):
        raise Refused("config file lacks a required field; see docs/ops/forensic-entry.md")
    return conf


class Cloud:
    """Cloud assistant through the aliyun CLI profile named in the config; the CLI reads its own credentials. Instance ids sit in the child's argv only."""

    def __init__(self, conf):
        self.profile, self.region = conf["aliyun_profile"], conf["region"]

    def _cli(self, *args):
        run = subprocess.run(["aliyun", *args, "--profile", self.profile, "--RegionId", self.region], capture_output=True, text=True, timeout=120)
        if run.returncode != 0:
            raise Refused(f"cloud call failed (exit {run.returncode}; output withheld)")
        return json.loads(run.stdout)

    def run(self, instance, script, timeout=600):  # (status, exit code, text the script printed)
        content = base64.b64encode(script.encode()).decode()
        invoke = self._cli("ecs", "RunCommand", "--Type", "RunShellScript", "--CommandContent", content, "--ContentEncoding", "Base64",
                           "--InstanceId.1", instance, "--Timeout", str(timeout), "--KeepCommand", "false").get("InvokeId")
        if not invoke:
            raise Refused("cloud call returned no invocation")
        for _ in range(timeout // 2 + 30):
            time.sleep(2)
            res = self._cli("ecs", "DescribeInvocationResults", "--InvokeId", invoke, "--InstanceId", instance)
            item = (res.get("Invocation") or res).get("InvocationResults", {}).get("InvocationResult", [{}])[0]
            if item.get("InvocationStatus") in ("Success", "Failed", "Stopped", "Timeout", "TimedOut", "Cancelled", "Error", "Aborted"):
                return item["InvocationStatus"], item.get("ExitCode"), base64.b64decode(item.get("Output") or "").decode("utf-8", "replace")
        raise Refused("cloud invocation did not finish")


def server_run(cloud, conf, database, sql):
    """Runs the validated `sql` on the app host with the forensic account; returns OneSql's output text. Raises Refused on any failed step; the app-host work dir is removed in every case."""
    work, secret_work = conf["app_work_dir"], conf["secret_work_dir"]
    tag = secrets.token_hex(4)
    try:
        pub_status, _, pub = cloud.run(conf["app_host"], f"set -eu; umask 077; rm -rf {work}; install -d -m 700 {work}/stage; openssl genpkey -algorithm RSA -pkeyopt "
                                       f"rsa_keygen_bits:3072 -out {work}/k.key 2>/dev/null; openssl pkey -in {work}/k.key -pubout")
        if pub_status != "Success" or "-----BEGIN" not in pub:
            raise Refused("could not prepare the throw-away key")
        pub = pub[pub.index("-----BEGIN"):]
        encrypt = ("import subprocess,sys,base64\nv=dict(l.strip().split('=',1) for l in open(sys.argv[2]) if '=' in l and not l.startswith('#'))\n"
                   "s=v['EVIDENCE_DB_PASSWORD'].strip(\"'\\\"\")\nc=subprocess.run(['openssl','pkeyutl','-encrypt','-pubin','-inkey',sys.argv[1],'-pkeyopt','rsa_padding_mode:oaep',"
                   "'-pkeyopt','rsa_oaep_md:sha256','-pkeyopt','rsa_mgf1_md:sha256'],input=s.encode(),capture_output=True,check=True).stdout\nprint('EVIDENCE:'+base64.b64encode(c).decode())\n")
        st, _, out = cloud.run(conf["secret_host"], f"set -eu; umask 077; install -d -m 700 {secret_work}; {put(f'{secret_work}/pub', pub)}{put(f'{secret_work}/enc.py', encrypt)}"
                               f"python3 -I {secret_work}/enc.py {secret_work}/pub {conf['secret_file']}; rm -rf {secret_work}")
        cipher = re.findall(r"^EVIDENCE:([A-Za-z0-9+/=]+)$", out, re.M)
        if st != "Success" or len(cipher) != 1:
            raise Refused("forensic credential not available on the secret host")
        stage = f"{work}/stage"
        driver = (f"if [ ! -s {work}/jdbc.jar ]; then pid=$(pgrep -f '{conf['app_jar_process']}' | head -1); jar=\"$(readlink -f /proc/$pid/cwd)/{conf['app_jar_process']}\"; "
                  f"entry=$(unzip -l \"$jar\" | grep -oE 'BOOT-INF/lib/mssql-jdbc-[^ ]+\\.jar' | head -1); unzip -p \"$jar\" \"$entry\" > {work}/jdbc.jar; fi\n")
        env = f"LOGIN_USER={conf['account']} LOGIN_SECRET_ENC={stage}/enc LOGIN_KEY={work}/k.key"
        script = (f"set -u; umask 077\n{put(f'{stage}/OneSql.java', ONE_SQL.read_text(encoding='utf-8'))}{put(f'{stage}/enc', cipher[0])}{put(f'{stage}/q.sql', sql)}{driver}"
                  f"{env} java -cp {work}/jdbc.jar {stage}/OneSql.java ro {database} {stage}/q.sql < /dev/null 2>&1 | head -c 4000000 > {work}/out.txt; rc=$?\n"
                  f"if [ \"$(gzip -9 -c {work}/out.txt | base64 -w0 | wc -c)\" -gt 22000 ]; then echo TOOBIG; else gzip -9 -c {work}/out.txt | base64 -w0; echo; fi; exit $rc\n")
        status, _, out = cloud.run(conf["app_host"], script)
        if "TOOBIG" in out:
            raise Refused("result is too large for one cloud-assistant reply; aggregate more or narrow the query")
        packed = re.findall(r"^[A-Za-z0-9+/=]{20,}$", out, re.M)
        if not packed:
            raise Refused(f"no result came back ({status})")
        return gzip.decompress(base64.b64decode(packed[-1])).decode("utf-8", "replace")
    finally:
        _, _, left = cloud.run(conf["app_host"], f"rm -rf {work}; test -e {work} && echo LEFT || echo CLEAN")
        if "CLEAN" not in left:
            sys.stderr.write("warning: the app-host work directory could not be confirmed removed\n")


def put(path, text):
    return f"echo '{base64.b64encode(gzip.compress(text.encode(), 9)).decode()}' | base64 -d | gunzip > {path}\n"


def rows_of(output):  # data rows of OneSql's first result: lists of cells
    lines = output.splitlines()
    head = next((i for i, l in enumerate(lines) if l.startswith("  #")), None)
    return [] if head is None else [[c.strip() for c in l[2:].split(" | ")] for l in lines[head + 1:] if l.startswith("  ") and not l.startswith("  (")]


def account_ok(conf, target, rows):
    if not rows or any(len(r) != 3 for r in rows):
        return False
    logins, dbs, perms = {r[0] for r in rows}, {r[1] for r in rows}, {r[2] for r in rows}
    return logins == {conf["account"]} and dbs == {conf["targets"][target]["database"]} and "SELECT" in perms and perms <= EXPECTED_PERMISSIONS


def databases_ok(conf, rows):  # the login reaches exactly the configured target databases (plus the platform guest ones)
    names = {r[0] for r in rows}
    return bool(rows) and all(len(r) == 1 for r in rows) and names - SYSTEM_DATABASES == {t["database"] for t in conf["targets"].values()}


def output_problems(text, limit):  # kinds and counts only, never the matched text: personal-data shapes anywhere, cells longer than the limit
    found = {kind: len(re.findall(rx, text)) for kind, rx in PERSONAL.items()}
    long_cells = sum(1 for line in text.splitlines() if line.startswith("  ") for cell in line[2:].split(" | ") if len(cell) > limit)
    found["cell longer than %d characters" % limit] = long_cells
    return ", ".join(f"{n} x {kind}" for kind, n in found.items() if n)


def marker_for(target):
    return CONFIG.parent / f"verified-{target}"


def verified_recently(target):
    try:
        return (time.time() - marker_for(target).stat().st_mtime) < RECHECK_DAYS * 86400
    except OSError:
        return False


def register(args, conf, sql):  # one fragment per run, before anything is sent (ADR-0037 decision 7); no row data
    sys.path.insert(0, str(REPO / "tools" / "evidence"))
    import new_legacy_read
    new_legacy_read.main([f"--session={args.session}", f"forensic account ({conf['account']})", f"{args.target}: {tables_of(sql)}", args.purpose,
                          f"file {os.path.basename(args.out)} outside the repository; the session keeps counts and conclusions only",
                          f"app-host temporary directory removed at the end of the run; local file: {args.delete_at}", "ADR-0037 decision 7; card #1377 entry"])


def out_path_ok(path):
    real = Path(os.path.realpath(path))
    if REPO in real.parents or any((p / ".git").exists() for p in real.parents):
        raise Refused("--out must lie outside every git work tree")
    if real.exists():
        raise Refused("--out already exists")
    return real


def main(argv, cloud_factory=Cloud):
    import argparse
    parser = argparse.ArgumentParser(description="registered read-only forensic entry", usage="forensic_read.py --target T --session S --purpose P --delete-at D --out FILE SQLFILE")
    for name in ("target", "session", "purpose", "delete-at", "out"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--definitions", action="store_true", help="the result is definition text (formulas, field defaults): cells up to 2000 characters")
    parser.add_argument("sqlfile")
    args = parser.parse_args(argv)
    try:
        sql = validate_select(Path(args.sqlfile).read_text(encoding="utf-8"))  # refused here: nothing is read from the config, registered or sent
        out = out_path_ok(args.out)
        conf = load_config()
        if args.target not in conf["targets"] or not conf["targets"][args.target].get("database"):
            raise Refused("unknown target")
        database = conf["targets"][args.target]["database"]
        register(args, conf, sql)
        cloud = cloud_factory(conf)
        if not verified_recently(args.target):
            if not (account_ok(conf, args.target, rows_of(server_run(cloud, conf, database, CHECK_SQL)))
                    and databases_ok(conf, rows_of(server_run(cloud, conf, database, CHECK_DBS_SQL)))):
                raise Refused("forensic account check failed (login, database, permissions or reachable databases); nothing was run")
            marker_for(args.target).write_text(datetime.date.today().isoformat(), encoding="utf-8")
        text = server_run(cloud, conf, database, sql)
        problems = output_problems(text, DEFINITION_CELL_LIMIT if args.definitions else CELL_LIMIT)
        if problems:
            raise Refused(f"result withheld, nothing written: it holds {problems}; aggregate the query, or pass --definitions for definition text only")
        out.write_text(text, encoding="utf-8")
        os.chmod(out, 0o600)
        print(f"ok: {len(rows_of(text))} rows in the first result, {len(text)} characters written to the --out file; nothing else is shown")
        return 0
    except Refused as error:
        sys.stderr.write(f"refused: {error}\n")
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
