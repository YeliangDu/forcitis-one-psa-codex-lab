#!/usr/bin/env bash
# Release statement: a short text naming the bundle, its checksums, the commit and the workflow that built it, signed with the release
# key (ssh-keygen -Y, namespace forcitis-one-release). It stands in for artifact attestations, which the GitHub Team plan does not offer
# for private repositories (RELEASE.md §11). The private key lives only in the `release` environment, which only main can use.
#
#   statement.sh write  <bundle.tar.gz> <statement> <commit>            inside the release workflow (reads GITHUB_WORKFLOW_REF, GITHUB_RUN_ID);
#                                                                       <commit> is the sha the workflow proved, never read from the bundle
#   statement.sh sign   <statement> <private key file>                  writes <statement>.sig
#   statement.sh verify <bundle.tar.gz> <statement> <allowed signers> <version> <commit>
set -euo pipefail

NAMESPACE=forcitis-one-release
PRINCIPAL=forcitis-one-release
FORMAT=forcitis-one-release-statement/1
EXPECTED_WORKFLOW=FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main

fail() { echo "statement.sh: $*" >&2; exit 1; }
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi
}
member_sha256() {
  local archive="$1" member="$2"
  tar -xzOf "$archive" "$member" | sha256_of
}
field() { awk -v key="$1:" '$1 == key { print $2; found = 1 } END { exit !found }' "$2"; }

command="${1:-}"
shift || true
case "$command" in
  write)
    archive="${1:?bundle}"; out="${2:?statement}"; commit="${3:?commit}"
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || fail "commit must be a full sha"
    name="$(basename "$archive" .tar.gz)"
    version="${name#forcitis-one-}"
    [ -n "${GITHUB_WORKFLOW_REF:-}" ] && [ -n "${GITHUB_RUN_ID:-}" ] || fail "write runs inside GitHub Actions only"
    built_from="$(tar -xzOf "$archive" "$name/manifest.json" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["gitSha"])')"
    [ "$built_from" = "$commit" ] || fail "bundle manifest says $built_from, the proved commit is $commit"
    {
      echo "format: $FORMAT"
      echo "version: $version"
      echo "commit: $commit"
      echo "bundle: $name.tar.gz"
      echo "bundle-sha256: $(sha256_of < "$archive")"
      echo "manifest-sha256: $(member_sha256 "$archive" "$name/manifest.json")"
      echo "sha256sums-sha256: $(member_sha256 "$archive" "$name/SHA256SUMS")"
      echo "workflow: $GITHUB_WORKFLOW_REF"
      echo "run: $GITHUB_RUN_ID"
    } > "$out"
    ;;
  sign)
    statement="${1:?statement}"; key="${2:?key}"
    ssh-keygen -q -Y sign -f "$key" -n "$NAMESPACE" "$statement" >/dev/null 2>&1 || fail "signing failed"
    [ -s "$statement.sig" ] || fail "no signature written"
    ;;
  verify)
    archive="${1:?bundle}"; statement="${2:?statement}"; signers="${3:?allowed signers}"; version="${4:?version}"; commit="${5:?commit}"
    grep -qE "^${PRINCIPAL} " "$signers" || fail "$signers holds no release key; the one-time key setup (RELEASE.md §7 R-12) is not done"
    ssh-keygen -Y verify -f "$signers" -I "$PRINCIPAL" -n "$NAMESPACE" -s "$statement.sig" < "$statement" >/dev/null 2>&1 \
      || fail "the release statement's signature does not verify against $signers"
    name="forcitis-one-$version"
    [ "$(field format "$statement")" = "$FORMAT" ] || fail "unknown statement format"
    [ "$(field version "$statement")" = "$version" ] || fail "statement is for another version"
    [ "$(field commit "$statement")" = "$commit" ] || fail "statement is for another commit"
    [ "$(field bundle "$statement")" = "$name.tar.gz" ] || fail "statement names another bundle"
    [ "$(field workflow "$statement")" = "$EXPECTED_WORKFLOW" ] || fail "statement was not produced by the release workflow on main"
    [ "$(field bundle-sha256 "$statement")" = "$(sha256_of < "$archive")" ] || fail "bundle differs from the signed statement"
    [ "$(field manifest-sha256 "$statement")" = "$(member_sha256 "$archive" "$name/manifest.json")" ] || fail "manifest.json differs from the signed statement"
    [ "$(field sha256sums-sha256 "$statement")" = "$(member_sha256 "$archive" "$name/SHA256SUMS")" ] || fail "SHA256SUMS differs from the signed statement"
    echo "statement.sh: $name.tar.gz matches the signed statement of release run $(field run "$statement")"
    ;;
  *) fail "usage: write | sign | verify (see the header)" ;;
esac
