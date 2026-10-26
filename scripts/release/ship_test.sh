#!/usr/bin/env bash
# Exercises ship.sh against stand-ins for gh, curl and aliyun: signed-link resolution from Location, the OIDC to STS exchange, masking,
# the OOS request parameters, polling the result parameter, and that no ECS execution API is called. Tokens are generated per run; nothing
# leaves the host.
#
#   scripts/release/ship_test.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
mkdir -p "$scratch/bin"

cat > "$scratch/bin/gh" <<'EOF'
#!/usr/bin/env bash
# FAKE_GH_HANG: a gh that ignores SIGALRM and SIGTERM and never answers (only SIGKILL stops it)
[ -z "${FAKE_GH_HANG:-}" ] || exec python3 -I -c 'import signal, time
for s in (signal.SIGALRM, signal.SIGTERM): signal.signal(s, signal.SIG_IGN)
time.sleep(60)'
python3 -I -c '
import json
top = "forcitis-one-0.1.0"
print(json.dumps({"tag_name": "v0.1.0", "draft": False, "assets": [
    {"name": top + s, "id": 10 + i, "state": "uploaded"} for i, s in enumerate([".tar.gz", ".tar.gz.sha256", ".release", ".release.sig"])]}))'
EOF

cat > "$scratch/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_DIR/argv"
[ "${1:-}" = -q ] || { echo "UNSAFE curl: -q is not the first argument" >&2; exit 90; }
url="" header_stdin=no form=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    -H) [ "$2" = @- ] && header_stdin=yes; shift ;;
    --data-urlencode) form+=("$2"); shift ;;
    -o|-D|--max-time|--max-redirs|--proto) shift ;;
    https://*) url="$1" ;;
  esac
  shift
done
case "$url" in
  https://api.github.com/*/releases/assets/*)
    [ "$header_stdin" = yes ] && grep -qx "Authorization: Bearer $GH_TOKEN" || { echo "token not on stdin" >&2; exit 91; }
    id="${url##*/}"
    printf 'HTTP/2 %s\r\n' "${FAKE_ASSET_STATUS:-302}"
    printf 'location: https://%s/github-production-release-asset/1/%s?sp=r&se=2026-10-15T00%%3A05%%3A00Z&sig=%s\r\n\r\n' \
      "${FAKE_HOST:-release-assets.githubusercontent.com}" "$id" "$FAKE_LINK_SIG" ;;
  https://oidc.invalid/*)
    [ "$header_stdin" = yes ] && grep -qx "Authorization: Bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN" || { echo "request token not on stdin" >&2; exit 92; }
    printf '{"value": "%s"}' "$FAKE_OIDC_TOKEN" ;;
  https://sts.aliyuncs.com/*)
    [ "$(cat)" = "$FAKE_OIDC_TOKEN" ] || { echo "OIDC token not on stdin" >&2; exit 93; }
    printf '%s\n' "${form[@]}" > "$FAKE_DIR/sts-form"
    if [ "${FAKE_STS:-ok}" = ok ]; then
      printf '{"RequestId":"r","AssumedRoleUser":{"Arn":"acs:ram::1:role/deploy-dev"},"Credentials":{"AccessKeyId":"%s","AccessKeySecret":"%s","SecurityToken":"%s","Expiration":"2026-10-15T01:00:00Z"}}' \
        "$FAKE_AK" "$FAKE_SK" "$FAKE_ST"
    else
      printf '{"RequestId":"r","Code":"AuthenticationFail.OIDCToken.SubjectMismatch","Message":"sub does not match"}'
    fi ;;
  *) echo "unexpected url $url" >&2; exit 94 ;;
esac
EOF

cat > "$scratch/bin/aliyun" <<'EOF'
#!/usr/bin/env bash
[ "${ALIBABA_CLOUD_IGNORE_PROFILE:-}" = TRUE ] || { echo "aliyun without ALIBABA_CLOUD_IGNORE_PROFILE=TRUE" >&2; exit 99; }
[ "$1" = version ] && { echo 3.5.0; exit 0; }
printf '%s\n' "$*" >> "$FAKE_DIR/argv"
case " $* " in *" --region $ECS_REGION "*) ;; *) echo "aliyun without --region" >&2; exit 95 ;; esac
[ "$ALIBABA_CLOUD_ACCESS_KEY_ID" = "$FAKE_AK" ] && [ "$ALIBABA_CLOUD_ACCESS_KEY_SECRET" = "$FAKE_SK" ] \
  && [ "$ALIBABA_CLOUD_SECURITY_TOKEN" = "$FAKE_ST" ] && [ -z "${ALIBABA_CLOUD_ROLE_ARN:-}" ] || { echo "credentials not from STS" >&2; exit 96; }
product="$1" action="$2"; shift 2
name="" value=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --Name) name="$2"; shift ;;
    --Value) value="$2"; shift ;;
  esac
  shift
done
case "$product $action" in
  "oos UpdateParameter") printf '%s\t%s\n' "$name" "$value" >> "$FAKE_DIR/oos"; echo '{"Parameter":{"Value":"echoed"}}' ;;
  "oos GetParameter")
    [ "$name" = forcitis-one-deploy-result-dev ] || { echo "read of $name" >&2; exit 97; }
    # Each read takes the next state of the result parameter; when they run out it stays as it was
    state="$(head -1 "$FAKE_DIR/results")"; [ -n "$state" ] || state=none
    tail -n +2 "$FAKE_DIR/results" > "$FAKE_DIR/results.next"; mv "$FAKE_DIR/results.next" "$FAKE_DIR/results"
    [ "$state" != fail ] || { echo "ServiceUnavailable" >&2; exit 1; }
    python3 -I -c '
import json, sys
s = sys.argv[1]
values = {"none": "{}", "garbage": "not json",
          "other": json.dumps({"request_id": "4100:1", "status": "succeeded", "reason": None, "time": "2026-10-15T00:00:00Z"})}
value = values.get(s) or json.dumps({"request_id": "4242:2", "status": s, "reason": {"failed": "download", "retrying": "link-mismatch"}.get(s),
                                     "time": "2026-10-15T00:00:00Z"})
print(json.dumps({"Parameter": {"Name": "forcitis-one-deploy-result-dev", "Value": value}}))' "$state" ;;
  *) echo "unexpected aliyun $product $action" >&2; exit 98 ;;
esac
EOF
printf '#!/bin/sh\nexit 0\n' > "$scratch/bin/sleep"
# Each `date +%s` is 100 seconds later than the one before, so the wait limit is reached without waiting; other formats are the real date
cat > "$scratch/bin/date" <<'EOF'
#!/usr/bin/env bash
if [ "$*" = +%s ]; then
  now=$(( $(cat "$FAKE_DIR/clock" 2>/dev/null || echo 1700000000) + 100 ))
  echo "$now" > "$FAKE_DIR/clock"
  echo "$now"
else
  exec /bin/date "$@"
fi
EOF
chmod +x "$scratch/bin"/*

token() { python3 -I -c 'import secrets; print(secrets.token_urlsafe(24))'; }
GH_TOKEN_VALUE="$(token)"; REQUEST_TOKEN="$(token)"; OIDC_TOKEN="$(token)"
AK="STS.$(token)"; SK="$(token)"; ST="$(token)"; LINK_SIG="$(token)"

passed=0 failed=0

# run <name> <result states, one per read> [VAR=value ...]: runs ship.sh in a clean environment; output in $scratch/<name>/out
run() {
  local name="$1" results="$2" dir="$scratch/$1"
  shift 2
  mkdir -p "$dir"
  printf '%s\n' "$results" | tr ' ' '\n' > "$dir/results"
  set +e
  env -i PATH="$scratch/bin:/usr/bin:/bin" FAKE_DIR="$dir" FAKE_LINK_SIG="$LINK_SIG" FAKE_OIDC_TOKEN="$OIDC_TOKEN" \
    FAKE_AK="$AK" FAKE_SK="$SK" FAKE_ST="$ST" GH_TOKEN="$GH_TOKEN_VALUE" GITHUB_REPOSITORY=FORCITIS/forcitis-one-psa \
    GITHUB_RUN_ID=4242 GITHUB_RUN_ATTEMPT=2 ACTIONS_ID_TOKEN_REQUEST_URL='https://oidc.invalid/token?api-version=2.0' \
    ACTIONS_ID_TOKEN_REQUEST_TOKEN="$REQUEST_TOKEN" ALIYUN_CLI_VERSION=3.5.0 ALIBABA_CLOUD_ROLE_ARN=acs:ram::1:role/deploy-dev \
    ALIBABA_CLOUD_OIDC_PROVIDER_ARN=acs:ram::1:oidc-provider/github-actions ECS_REGION=cn-hangzhou \
    "$@" "$here/ship.sh" dev 0.1.0 > "$dir/out" 2>&1
  echo $? > "$dir/rc"
  set -e
}

# expect <name> <description> <condition function>: the function gets the case directory
expect() {
  local name="$1" what="$2" condition="$3" d="$scratch/$1"
  if ("$condition" "$d") >/dev/null 2>&1; then
    passed=$((passed + 1)); echo "PASS  $name: $what"
  else
    failed=$((failed + 1)); echo "FAIL  $name: $what"; sed 's/^/      /' "$d/out"
  fi
}

rc_is() { [ "$(cat "$1/rc")" = "$2" ]; }
rc_failed() { ! rc_is "$1" 0; }
says() { grep -qF -- "$2" "$1/out"; }

# Secrets may appear only on ::add-mask:: lines of the output; tokens and credentials on no command line at all
no_leaks() {
  python3 -I - "$1" "$GH_TOKEN_VALUE" "$REQUEST_TOKEN" "$OIDC_TOKEN" "$AK" "$SK" "$ST" "sig=" <<'PY'
import os, sys
d, secrets = sys.argv[1], sys.argv[2:]
out = [l for l in open(os.path.join(d, "out")).read().splitlines() if not l.startswith("::add-mask::")]
argv = open(os.path.join(d, "argv")).read() if os.path.exists(os.path.join(d, "argv")) else ""
leaks = [s for s in secrets if any(s in l for l in out)] + [s for s in secrets[:-1] if s in argv]
sys.exit(1 if leaks else 0)
PY
}
succeeded_after_polling() { rc_is "$1" 0 && says "$1" "request 4242:2: the ECS started installing" && says "$1" "request 4242:2: succeeded on the ECS"; }
all_masked() {
  local s
  for s in "$OIDC_TOKEN" "$AK" "$SK" "$ST"; do grep -qxF "::add-mask::$s" "$1/out" || return 1; done
}
sts_form_right() {
  grep -qx "RoleArn=acs:ram::1:role/deploy-dev" "$1/sts-form" && grep -qx "OIDCToken@-" "$1/sts-form" \
    && grep -qx "RoleSessionName=deploy-dev-4242-2" "$1/sts-form" && grep -qx "DurationSeconds=3600" "$1/sts-form"
}
parameters_in_order() {
  [ "$(cut -f1 "$1/oos" | tr '\n' ' ')" = "forcitis-one-deploy-request-dev-bundle forcitis-one-deploy-request-dev-bundle-sha256 forcitis-one-deploy-request-dev-statement forcitis-one-deploy-request-dev-statement-sig forcitis-one-deploy-request-dev " ]
}
# The request names this run and carries the SHA-256 of exactly the values written to the four link parameters
request_value_right() {
  python3 -I - "$1/oos" <<'PY'
import hashlib, json, sys
rows = [line.rstrip("\n").split("\t", 1) for line in open(sys.argv[1])]
request = json.loads(rows[-1][1])
links = {name.rsplit("forcitis-one-deploy-request-dev-", 1)[1]: value for name, value in rows[:-1]}
expected = {"version": "0.1.0", "environment": "dev", "run_id": 4242, "run_attempt": 2, "request_id": "4242:2",
            "asset_url_sha256": {k: hashlib.sha256(v.encode("utf-8")).hexdigest() for k, v in links.items()}}
sys.exit(0 if request == expected and len(rows[-1][1]) <= 4096 and sorted(links) == ["bundle", "bundle-sha256", "statement", "statement-sig"] else 1)
PY
}
link_value_right() {
  [ "$(head -1 "$1/oos" | cut -f2)" = "https://release-assets.githubusercontent.com/github-production-release-asset/1/10?sp=r&se=2026-10-15T00%3A05%3A00Z&sig=$LINK_SIG" ]
}
expiry_reported() { [ "$(grep -c "valid until 2026-10-15T00:05:00Z" "$1/out")" = 4 ]; }
no_execution_api() { ! grep -qE '^ecs ' "$1/argv"; }
refused_host_before_aliyun() { rc_failed "$1" && says "$1" "signed link is not https on" && ! grep -qE '^(oos|ecs) ' "$1/argv"; }
refused_host() { rc_failed "$1" && says "$1" "signed link is not https on"; }
refused_streamed() { rc_failed "$1" && says "$1" "asset API answered 200, expected a redirect"; }
sts_refusal_shown() {
  rc_failed "$1" && says "$1" "STS refused: AuthenticationFail.OIDCToken.SubjectMismatch sub does not match" && [ ! -e "$1/oos" ]
}
# Success only after this request_id was seen started, and after exactly the five reads the sequence needs
other_request_waited() {
  rc_is "$1" 0 && says "$1" "request 4242:2: the ECS started installing" && says "$1" "request 4242:2: succeeded on the ECS" \
    && ! says "$1" "4100:1" && [ "$(grep -c '^oos GetParameter ' "$1/argv")" = 5 ] \
    && [ "$(grep -n 'started installing' "$1/out" | cut -d: -f1)" -lt "$(grep -n 'succeeded on the ECS' "$1/out" | cut -d: -f1)" ]
}
retry_waited() {
  rc_is "$1" 0 && [ "$(grep -c 'the ECS will retry (link-mismatch)' "$1/out")" = 1 ] && says "$1" "succeeded on the ECS"
}
failure_reported() { rc_is "$1" 1 && says "$1" "request 4242:2: the ECS reports failed (download)"; }
timeout_names_request() {
  rc_failed "$1" && says "$1" "no result for request 4242:2 after 4320s" && says "$1" "journalctl -u forcitis-one-release-pull.service" \
    && says "$1" "set forcitis-one-deploy-request-dev back to {}"
}
transient_retried() { rc_is "$1" 0 && says "$1" "failed (2 of 6 in a row)" && says "$1" "succeeded on the ECS"; }
unreadable_fails() { rc_failed "$1" && says "$1" "cannot read the result of request 4242:2" && says "$1" "journalctl -u forcitis-one-release-pull.service"; }

run success "none started started succeeded"
expect success "waits through the placeholder and started, then reports success" succeeded_after_polling
expect success "OIDC token and the three STS values are masked" all_masked
expect success "no token, credential or signed link outside the mask lines; no token or credential on any command line" no_leaks
expect success "STS form names role, provider, session and reads the OIDC token from stdin" sts_form_right
expect success "four link parameters, then the request parameter last" parameters_in_order
expect success "request names this run and hashes exactly the four values written" request_value_right
expect success "link parameters hold the signed links from Location" link_value_right
expect success "the expiry of each link is reported without the link" expiry_reported
expect success "no Cloud Assistant or other ECS call at all" no_execution_api

run evil-host "succeeded" FAKE_HOST=evil.example
expect evil-host "a Location outside the asset hosts fails before any Aliyun call" refused_host_before_aliyun
run port-host "succeeded" FAKE_HOST=release-assets.githubusercontent.com:443
expect port-host "a Location with an explicit port fails" refused_host
run streamed "succeeded" FAKE_ASSET_STATUS=200
expect streamed "an asset API answer other than 302 fails" refused_streamed
run sts-refused "succeeded" FAKE_STS=refuse
expect sts-refused "an STS refusal shows code and message, and nothing is written to OOS" sts_refusal_shown
run other-request "other other garbage started succeeded"
expect other-request "a result of another request_id (or unreadable) means not yet taken up; keeps waiting" other_request_waited
run retrying "retrying retrying started succeeded"
expect retrying "retrying is not an end state: keeps waiting, reports the reason once" retry_waited
run install-failed "started failed"
expect install-failed "a failed result fails the job with the cause" failure_reported
run timeout "other"
expect timeout "no result in time fails, naming the request, the timer log and how to withdraw" timeout_names_request
run transient "fail fail started succeeded"
expect transient "two failed reads of the result are retried" transient_retried
run unreadable "fail fail fail fail fail fail succeeded"
expect unreadable "six failed reads in a row fail with a clear message" unreadable_fails

hung_gh_stopped() {
  rc_failed "$1" && says "$1" "does not have the four assets" && [ "$(cat "$1/seconds")" -lt 15 ]
}
start=$SECONDS
run hung-gh "succeeded" SHIP_GH_TIMEOUT=2 FAKE_GH_HANG=1
echo $((SECONDS - start)) > "$scratch/hung-gh/seconds"
expect hung-gh "a gh that ignores SIGALRM and SIGTERM is killed at the deadline and the job fails" hung_gh_stopped

# the default must be 120 seconds when the variable is unset or empty (alarm(0) would mean no timeout at all)
for unset_form in "unset SHIP_GH_TIMEOUT" "SHIP_GH_TIMEOUT="; do
  if [ "$(bash -c "$unset_form; $(grep '^export SHIP_GH_TIMEOUT=' "$here/ship.sh"); printf %s \"\$SHIP_GH_TIMEOUT\"")" = 120 ]; then
    passed=$((passed + 1)); echo "PASS  default-timeout: 120 seconds when SHIP_GH_TIMEOUT is ($unset_form)"
  else
    failed=$((failed + 1)); echo "FAIL  default-timeout: not 120 when SHIP_GH_TIMEOUT is ($unset_form)"
  fi
done

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
