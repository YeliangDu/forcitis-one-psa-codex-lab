#!/usr/bin/env bash
# Tools only: never invoke installer, application, Flyway, cloud or systemd.
set -euo pipefail
umask 077
test "$(id -u)" = 0
mkdir -p /run/lab54 /data/forcitis-one/logs /data/forcitis-one/tmp /data/forcitis-one/web-probe /etc/forcitis-one/migrate
chmod 755 /run/lab54
chown root:forcitis /data/forcitis-one
chmod 750 /data/forcitis-one
chown forcitis:forcitis /data/forcitis-one/logs /data/forcitis-one/tmp
chmod 750 /data/forcitis-one/logs /data/forcitis-one/tmp
chmod 700 /etc/forcitis-one/migrate
printf 'synthetic-only\n' > /etc/forcitis-one/migrate/probe-secret
chmod 600 /etc/forcitis-one/migrate/probe-secret
printf 'lab54-static-tool-probe\n' > /data/forcitis-one/web-probe/index.html
chmod 750 /data/forcitis-one/web-probe
chmod 640 /data/forcitis-one/web-probe/index.html
setfacl -m u:nginx:--x /data/forcitis-one
setfacl -R -m u:nginx:r-X /data/forcitis-one/web-probe
runuser -u nginx -- cat /data/forcitis-one/web-probe/index.html > /run/lab54/acl-read.txt
runuser -u forcitis -- test ! -r /etc/forcitis-one/migrate/probe-secret
runuser -u forcitis-migrate -- test ! -r /etc/forcitis-one/migrate/probe-secret
getfacl -p /data/forcitis-one /data/forcitis-one/web-probe > /run/lab54/acl.txt
java -version > /run/lab54/java-version.txt 2>&1
javac -version >> /run/lab54/java-version.txt 2>&1
cat > /run/lab54/Probe.java <<'JAVA'
public class Probe {
    public static void main(String[] args) {
        System.out.println("lab54-real-java-tool-probe");
    }
}
JAVA
javac /run/lab54/Probe.java
chmod 644 /run/lab54/Probe.class
runuser -u forcitis -- /usr/bin/java -cp /run/lab54 Probe > /run/lab54/java-probe.txt
grep -qx 'lab54-real-java-tool-probe' /run/lab54/java-probe.txt
mkdir /run/lab54/pgdata /run/lab54/pgsocket
chown postgres:postgres /run/lab54/pgdata /run/lab54/pgsocket
chmod 700 /run/lab54/pgdata
openssl req -x509 -newkey rsa:3072 -nodes -days 1 -subj /CN=localhost \
  -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
  -keyout /run/lab54/server.key -out /run/lab54/server.crt >/run/lab54/tls-generation.log 2>&1
chown postgres:postgres /run/lab54/server.key
chmod 600 /run/lab54/server.key
chmod 644 /run/lab54/server.crt
runuser -u postgres -- initdb -D /run/lab54/pgdata --auth-local=peer --auth-host=scram-sha-256 > /run/lab54/initdb.log
cat >> /run/lab54/pgdata/postgresql.conf <<'PG'
listen_addresses = '127.0.0.1'
unix_socket_directories = '/run/lab54/pgsocket'
ssl = on
ssl_cert_file = '/run/lab54/server.crt'
ssl_key_file = '/run/lab54/server.key'
password_encryption = 'scram-sha-256'
PG
cat > /run/lab54/pgdata/pg_hba.conf <<'HBA'
local all all peer
hostssl lab54_probe lab54_probe 127.0.0.1/32 scram-sha-256
host all all 0.0.0.0/0 reject
host all all ::/0 reject
HBA
chown postgres:postgres /run/lab54/pgdata/pg_hba.conf
finish() {
  result=$?
  trap - EXIT
  runuser -u postgres -- pg_ctl -D /run/lab54/pgdata -m fast -w stop >/dev/null 2>&1 || true
  if [ "$result" -ne 0 ]; then echo "BLOCKED: tool probe failed" >&2; exit 86; fi
}
trap finish EXIT
touch /run/lab54/postgresql.log
chown postgres:postgres /run/lab54/postgresql.log
runuser -u postgres -- pg_ctl -D /run/lab54/pgdata -w -t 60 -l /run/lab54/postgresql.log start
password="$(openssl rand -hex 24)"
runuser -u postgres -- psql -X -q -v ON_ERROR_STOP=1 -h /run/lab54/pgsocket \
  -v pw="$password" postgres <<'SQL'
CREATE ROLE lab54_probe LOGIN PASSWORD :'pw';
CREATE DATABASE lab54_probe OWNER lab54_probe;
SQL
export PGPASSWORD="$password"
export PGSSLMODE=verify-full PGSSLROOTCERT=/run/lab54/server.crt
psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U lab54_probe -d lab54_probe \
  -Atc 'select ssl from pg_stat_ssl where pid=pg_backend_pid()' > /run/lab54/tls.txt
grep -qx t /run/lab54/tls.txt
psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U lab54_probe -d lab54_probe \
  -Atc 'select version(), current_database(), current_user' > /run/lab54/pg-version.txt
unset PGPASSWORD password
nginx -v > /run/lab54/nginx-version.txt 2>&1
curl --version > /run/lab54/curl-version.txt
python3 - <<'PY'
from pathlib import Path
import hashlib,json,subprocess
root=Path("/run/lab54")
interfaces=sorted(p.name for p in Path("/sys/class/net").iterdir())
if interfaces != ["lo"]:
    raise SystemExit("BLOCKED: non-loopback interface")
routes=Path("/proc/net/route").read_text()
for row in routes.splitlines()[1:]:
    fields=row.split()
    if fields[1] == "00000000" and fields[7] == "00000000":
        raise SystemExit("BLOCKED: external default route")
names=["java-version.txt","java-probe.txt","pg-version.txt","tls.txt",
       "acl.txt","acl-read.txt","nginx-version.txt","curl-version.txt"]
print(json.dumps(dict(kind="lab54-tools-only/1",tool_probe_completed=True,
                     versions={n:(root/n).read_text() for n in names if "version" in n},
                     users={n:subprocess.check_output(["id",n],text=True).strip()
                            for n in ["postgres","forcitis","forcitis-migrate","nginx"]},
                     files_sha256={n:hashlib.sha256((root/n).read_bytes()).hexdigest() for n in names},
                     pg_tls=True,interfaces=interfaces,ipv4_routes=routes,
                     installation_authority=False,application_started=False,
                     flyway_executed=False,migration_pass=False,install_pass=False)))
PY
