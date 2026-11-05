#!/bin/bash
# Card #1506: serves the built web client (frontend/apps/web/dist, run `pnpm build` there first) behind the real tools/ops/ecs/files/nginx-one.conf in a local
# container, proxying /api to the local stack (tools/ops/local/local.sh up), and reports Content-Security-Policy violations. Never production; the certificate is a
# throwaway self-signed one in a temp dir. Only three things differ from the shipped file: certificate paths, the proxy target and the port.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
DIST="${CSP_DIST:-$REPO/frontend/apps/web/dist}"   # CSP_DIST: another build of the web client (the local login button is on main only with card #1478)
[ -f "$DIST/index.html" ] || { echo "csp_check.sh: build the web client first (pnpm build in frontend/apps/web)" >&2; exit 1; }
curl -fsS -o /dev/null http://localhost:8080/actuator/health || { echo "csp_check.sh: the local stack is not running (tools/ops/local/local.sh up)" >&2; exit 1; }
TMP="$(mktemp -d)"; NAME="forcitis-csp-check-$$"
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; rm -rf "${TMP:?}"; }
trap cleanup EXIT
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 1 -subj "/CN=one.forcitis.com" -keyout "$TMP/key.pem" -out "$TMP/cert.pem" >/dev/null 2>&1
python3 -I - "$REPO/tools/ops/ecs/files/nginx-one.conf" "$TMP/one.conf" <<'PY'
import sys
s = open(sys.argv[1]).read()
for old, new in (("/etc/nginx/tls/one.forcitis.com.pem", "/tls/cert.pem"), ("/etc/nginx/tls/one.forcitis.com.key", "/tls/key.pem"), ("http://127.0.0.1:8080", "http://host.docker.internal:8080")):
    assert s.count(old) == 1, old
    s = s.replace(old, new)
open(sys.argv[2], "w").write(s)
PY
mkdir -p "$TMP/pinned"
docker run -d --name "$NAME" -p 127.0.0.1:18443:443 --add-host host.docker.internal:host-gateway \
  -v "$TMP/one.conf:/etc/nginx/conf.d/one.conf:ro" -v "$TMP:/tls:ro" -v "$DIST:/data/forcitis-one/current/web:ro" -v "$TMP/pinned:/data/forcitis-one/static-pinned:ro" nginx:stable >/dev/null
for _ in $(seq 1 20); do curl -ksS -o /dev/null --resolve one.forcitis.com:18443:127.0.0.1 https://one.forcitis.com:18443/ 2>/dev/null && break; sleep 0.5; done
# add_header is not inherited by a location that has its own: every kind of response must carry the policy
ASSET=""
for f in "$DIST"/assets/*.js; do ASSET="/assets/$(basename "$f")"; break; done
for path in / /admin/org-units "$ASSET" /WW_verify_x.txt /api/v1/platform/sessions:current; do
  headers="$(curl -ksI --resolve one.forcitis.com:18443:127.0.0.1 "https://one.forcitis.com:18443$path")"
  printf '%-40s %s CSP=%s\n' "$path" "$(head -1 <<<"$headers" | tr -d '\r')" "$(grep -qi '^content-security-policy:' <<<"$headers" && echo yes || echo NO)"
done
# Playwright comes from the web app (added by card #1478); CSP_NODE_DIR names another web app directory that has it
cd "${CSP_NODE_DIR:-$REPO/frontend/apps/web}" && node "$HERE/csp_check.mjs" https://one.forcitis.com:18443
