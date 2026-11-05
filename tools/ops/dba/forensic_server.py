"""Server half of the forensic entry (card #1389): the body of the ONE pre-created Cloud Assistant command, run on the secret host and the app host.

forensic_command.py renders this file (constants baked in, compressed) into the command content; it is never typed on a host. The caller's only input is ONE
base64url request blob that the command template appends after its `exit` line, where the shell never parses it; this code reads the blob out of the script file
and takes it as data only: nothing from it is executed, formatted into a shell line or used as a path. Every refusal prints one `REFUSED:` line and exits 3.
No double brace may appear in this file (Cloud Assistant substitutes them).
"""
import base64
import gzip
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

CONF = json.loads(base64.b64decode("@@CONF@@"))  # baked by the renderer: paths, account, allowed databases, public key, hashes
REV = "@@REV@@"
BLOB_MAX = 9000
OPS = {"ping": {"op"}, "encrypt": {"op"}, "query": {"op", "db", "cipher", "sql"}}


def refuse(reason):
    print("REFUSED:" + reason)
    sys.exit(3)


def read_request(path):
    try:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
    except (OSError, ValueError):
        refuse("script file unreadable")
    marker = "#@@" + "DATA@@\n"  # the first occurrence is the template's own line; whatever the caller supplied lies behind it
    at = text.find(marker)
    if at < 0:
        refuse("data marker not found")
    blob = text[at + len(marker):].strip()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,%d}" % BLOB_MAX, blob):
        refuse("the request is not one base64url line")
    try:
        req = json.loads(base64.urlsafe_b64decode(blob + "=" * (-len(blob) % 4)).decode("utf-8"))
    except (ValueError, UnicodeError):
        refuse("the request does not decode")
    if not isinstance(req, dict) or req.get("op") not in OPS or set(req) != OPS[req["op"]] or any(not isinstance(v, str) for v in req.values()):
        refuse("the request has an unknown shape")
    return req


def op_encrypt():
    """The forensic credential, encrypted to the public key baked into this command: only the app host's private key opens it."""
    password = None
    try:
        with open(CONF["secret_file"], encoding="utf-8") as handle:
            for line in handle:
                if "=" in line and not line.startswith("#"):
                    key, value = line.strip().split("=", 1)
                    if key == "EVIDENCE_DB_PASSWORD":
                        password = value.strip("'\"")
    except OSError:
        refuse("credential file unreadable")
    if not password:
        refuse("credential not found")
    work = tempfile.mkdtemp()
    try:
        pub = os.path.join(work, "pub")
        with open(pub, "w", encoding="utf-8") as handle:
            handle.write(CONF["pub"])
        done = subprocess.run(["openssl", "pkeyutl", "-encrypt", "-pubin", "-inkey", pub, "-pkeyopt", "rsa_padding_mode:oaep", "-pkeyopt", "rsa_oaep_md:sha256",
                               "-pkeyopt", "rsa_mgf1_md:sha256"], input=password.encode(), capture_output=True)
        if done.returncode != 0:
            refuse("encryption failed")
        print("EVIDENCE:" + base64.b64encode(done.stdout).decode())
    finally:
        shutil.rmtree(work, ignore_errors=True)


def jdbc_jar(work):
    """The JDBC driver inside the running application jar (the same source as before this command existed)."""
    done = subprocess.run(["pgrep", "-f", CONF["jar"]], capture_output=True, text=True)
    mine = {os.getpid(), os.getppid()}
    for pid in sorted(int(p) for p in done.stdout.split() if p.isdigit()):
        if pid in mine:
            continue
        try:
            jar = os.path.join(os.readlink("/proc/%d/cwd" % pid), CONF["jar"])
            with zipfile.ZipFile(jar) as archive:
                names = [n for n in archive.namelist() if re.fullmatch(r"BOOT-INF/lib/mssql-jdbc-[^/]+\.jar", n)]
                if names:
                    target = os.path.join(work, "jdbc.jar")
                    with open(target, "wb") as handle:
                        handle.write(archive.read(names[0]))
                    return target
        except (OSError, zipfile.BadZipFile):
            continue
    refuse("driver not found")


def op_query(req):
    database = req["db"]
    if database not in CONF["dbs"]:
        refuse("database not allowed")
    if not re.fullmatch(r"[A-Za-z0-9+/=]{1,2048}", req["cipher"]):
        refuse("credential shape")
    try:
        sql = base64.b64decode(req["sql"], validate=True).decode("utf-8")
    except (ValueError, UnicodeError):
        refuse("query does not decode")
    if not sql.strip() or "\x00" in sql or len(sql.encode()) > CONF["max_sql"]:
        refuse("query empty, too long or holds a NUL")
    gated = hashlib.sha256(sql.encode()).hexdigest() not in CONF["gate_free"]  # only the entry's two fixed account-check queries run without the structural plan gate
    try:
        with open(CONF["client"], "rb") as handle:  # installed by the user from the repository (OneSql.java); the hash baked into this command must match
            source = handle.read()
    except OSError:
        refuse("client source not installed on this host")
    if hashlib.sha256(source).hexdigest() != CONF["onesql_sha"]:
        refuse("client source on this host does not match the repository revision")
    work = CONF["work"]
    if not os.path.isabs(work) or work.rstrip("/").count("/") < 2:
        refuse("work directory")
    shutil.rmtree(work, ignore_errors=True)
    os.umask(0o077)
    os.makedirs(os.path.join(work, "stage"), mode=0o700)
    try:
        stage = os.path.join(work, "stage")
        for name, data in (("OneSql.java", source), ("enc", req["cipher"].encode()), ("q.sql", sql.encode())):
            with open(os.path.join(stage, name), "wb") as handle:
                handle.write(data)
        env = dict(os.environ, LOGIN_USER=CONF["account"], LOGIN_SECRET_ENC=os.path.join(stage, "enc"), LOGIN_KEY=CONF["key"])
        env.pop("ONE_SQL_PLAN_GATE", None)
        if gated:
            env["ONE_SQL_PLAN_GATE"] = "1"
        try:
            done = subprocess.run(["java", "-cp", jdbc_jar(work), os.path.join(stage, "OneSql.java"), "ro", database, os.path.join(stage, "q.sql")], env=env,
                                  stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=CONF["timeout"])
        except (OSError, subprocess.TimeoutExpired):
            refuse("client did not run to the end")
        packed = base64.b64encode(gzip.compress(done.stdout[:4000000], 9)).decode()
        print("TOOBIG" if len(packed) > 22000 else packed)
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main():
    req = read_request(sys.argv[2])
    print("REV:" + REV)
    if req["op"] == "encrypt":
        op_encrypt()
    elif req["op"] == "query":
        op_query(req)


if __name__ == "__main__":
    main()
