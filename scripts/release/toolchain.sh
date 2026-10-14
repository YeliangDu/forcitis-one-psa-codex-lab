#!/usr/bin/env bash
# Prepares and vets the toolchain on the dedicated release / deploy runner. Fails closed: every program a release or deploy job runs is
# either root-owned with no group- or world-writable directory on its path (system tools, a root-installed JDK), or downloaded here by
# pinned version and SHA256 into DIR. Nothing from /opt/homebrew or any other user-writable prefix is used (RELEASE.md §7 R-0).
#
#   scripts/release/toolchain.sh build  DIR    Java 25, Node 24, the pnpm pinned in frontend/package.json (corepack), gh
#   scripts/release/toolchain.sh deploy DIR    Java 25 (javap for verify_bundle.sh), gh, aliyun CLI
#   scripts/release/toolchain.sh check         only vet PATH and the system tools
#
# Inside GitHub Actions the tool directories are appended to GITHUB_PATH; elsewhere the PATH line to export is printed.
set -euo pipefail

NODE_VERSION=24.18.0
NODE_SHA256=4477b9f78efb77744cf5eb57a0e9594dba66466b38b4e93fa9f35cb907a095a6
JDK_RELEASE=jdk-25.0.4.1+1
JDK_ASSET=OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.4.1_1.tar.gz
JDK_SHA256=61979887f7506a24a57439ff99adb8b3a7fc89977d9cfe3b8984f58a981b7b9d
GH_VERSION=2.100.0
GH_SHA256=45f9a62da2f6e641a7fad57e2ce39656dfd7ef331372d80a2a2aed65abb01642
ALIYUN_CLI_VERSION=3.5.0
ALIYUN_CLI_SHA256=6f1b0c380a456849adab800ee1396be58976020c6d9dfa3196dcfc41f496a518
# Optional root-installed JDK (Temurin .pkg installs root-owned under /Library); used instead of the download when present and trusted
SYSTEM_JDK=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
SYSTEM_TOOLS=(bash sh env python3 git curl tar shasum unzip ssh ssh-keygen base64 awk sed grep find xargs mktemp cut tr sort head tail cmp stat)

purpose="${1:?usage: toolchain.sh <build|deploy|check> [DIR]}"
fail() { echo "toolchain.sh: $*" >&2; exit 1; }
[ "$(/usr/bin/uname -s)-$(/usr/bin/uname -m)" = Darwin-arm64 ] || fail "expects the macOS arm64 runner"

# root owns the path and every directory above it, and none of them is writable by group or others
trusted() {
  local path
  path="$(/bin/realpath "$1" 2>/dev/null)" || return 1
  while :; do
    [ "$(/usr/bin/stat -f %u "$path")" = 0 ] || return 1
    (( (8#$(/usr/bin/stat -f %Lp "$path") & 8#022) == 0 )) || return 1
    [ "$path" = / ] && return 0
    path="$(/usr/bin/dirname "$path")"
  done
}

entry=""
IFS=':' read -r -a path_entries <<< "$PATH"
for entry in "${path_entries[@]}"; do
  [ -n "$entry" ] || continue
  case "$entry" in "${RUNNER_TEMP:-/nonexistent}"/*) continue ;; esac
  trusted "$entry" || fail "PATH entry $entry is not root-owned and protected; the runner's .path must list system directories only (R-0)"
done
for tool in "${SYSTEM_TOOLS[@]}"; do
  resolved="$(command -v "$tool")" || fail "$tool not found"
  trusted "$resolved" || fail "$tool resolves to $resolved, which is not root-owned and protected"
done
# /usr/bin/python3 and /usr/bin/git hand over to the Command Line Tools; vet the program that really runs
trusted "$(python3 -c 'import sys; print(sys.executable)')" || fail "python3 runs an interpreter outside root-owned directories"
trusted "$(/usr/bin/xcrun --find git)" || fail "git runs a binary outside root-owned directories"

[ "$purpose" = check ] && { echo "toolchain.sh: PATH and system tools vetted"; exit 0; }
dir="${2:?usage: toolchain.sh <build|deploy> DIR}"
mkdir -p "$dir"
paths=()

fetch() {
  local url="$1" sha="$2" out="$3"
  curl -fsSL --proto '=https' --tlsv1.2 --retry 3 -o "$out" "$url"
  [ "$(shasum -a 256 "$out" | cut -d' ' -f1)" = "$sha" ] || { rm -f "$out"; fail "SHA256 mismatch for $url"; }
}

if [ -x "$SYSTEM_JDK/bin/java" ] && trusted "$SYSTEM_JDK/bin/java" && "$SYSTEM_JDK/bin/java" -version 2>&1 | head -1 | grep -q '"25'; then
  java_home="$SYSTEM_JDK"
else
  fetch "https://github.com/adoptium/temurin25-binaries/releases/download/${JDK_RELEASE/+/%2B}/$JDK_ASSET" "$JDK_SHA256" "$dir/jdk.tar.gz"
  mkdir -p "$dir/jdk" && tar -xzf "$dir/jdk.tar.gz" -C "$dir/jdk" --strip-components 1 && rm "$dir/jdk.tar.gz"
  java_home="$dir/jdk/Contents/Home"
fi
"$java_home/bin/java" -version 2>&1 | head -1 | grep -q '"25' || fail "Java 25 expected at $java_home"
paths+=("$java_home/bin")

fetch "https://github.com/cli/cli/releases/download/v$GH_VERSION/gh_${GH_VERSION}_macOS_arm64.zip" "$GH_SHA256" "$dir/gh.zip"
unzip -q -o "$dir/gh.zip" -d "$dir" && rm "$dir/gh.zip"
paths+=("$dir/gh_${GH_VERSION}_macOS_arm64/bin")

case "$purpose" in
  build)
    node_dir="$dir/node-v$NODE_VERSION-darwin-arm64"
    fetch "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-darwin-arm64.tar.xz" "$NODE_SHA256" "$dir/node.tar.xz"
    tar -xJf "$dir/node.tar.xz" -C "$dir" && rm "$dir/node.tar.xz"
    mkdir -p "$dir/corepack-bin"
    # corepack fetches the pnpm named by packageManager and checks the npm registry signature
    COREPACK_HOME="$dir/corepack" "$node_dir/bin/corepack" enable pnpm --install-directory "$dir/corepack-bin"
    paths+=("$node_dir/bin" "$dir/corepack-bin")
    ;;
  deploy)
    cli_dir="$dir/aliyun-$ALIYUN_CLI_VERSION"
    mkdir -p "$cli_dir"
    fetch "https://github.com/aliyun/aliyun-cli/releases/download/v$ALIYUN_CLI_VERSION/aliyun-cli-macosx-$ALIYUN_CLI_VERSION-arm64.tgz" \
      "$ALIYUN_CLI_SHA256" "$dir/aliyun.tgz"
    tar -xzf "$dir/aliyun.tgz" -C "$cli_dir" && rm "$dir/aliyun.tgz"
    [ "$("$cli_dir/aliyun" version)" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI reports another version"
    paths+=("$cli_dir")
    ;;
  *) fail "unknown purpose '$purpose'" ;;
esac

if [ -n "${GITHUB_PATH:-}" ]; then
  printf '%s\n' "${paths[@]}" >> "$GITHUB_PATH"
  {
    echo "JAVA_HOME=$java_home"
    echo "COREPACK_HOME=$dir/corepack"
    echo "COREPACK_ENABLE_DOWNLOAD_PROMPT=0"
    echo "ALIYUN_CLI_VERSION=$ALIYUN_CLI_VERSION"
  } >> "$GITHUB_ENV"
else
  echo "export PATH=$(IFS=:; echo "${paths[*]}"):\$PATH JAVA_HOME=$java_home COREPACK_HOME=$dir/corepack COREPACK_ENABLE_DOWNLOAD_PROMPT=0"
fi
