#!/usr/bin/env bash
# Prepares the toolchain on the dedicated release / deploy runner, whose macOS user shares nothing with the CI runners (no nvm, no
# ~/.m2, no pnpm store). Downloads are pinned by version and SHA256; nothing is installed outside DIR.
#
#   scripts/release/toolchain.sh build  DIR    Java 25 (Homebrew openjdk@25), Node 24 and the pnpm pinned in frontend/package.json
#   scripts/release/toolchain.sh deploy DIR    Java 25 (javap for verify_bundle.sh) and the aliyun CLI
#
# Inside GitHub Actions the tool directories are appended to GITHUB_PATH; elsewhere the PATH line to export is printed.
set -euo pipefail

NODE_VERSION=24.18.0
NODE_SHA256=4477b9f78efb77744cf5eb57a0e9594dba66466b38b4e93fa9f35cb907a095a6
ALIYUN_CLI_VERSION=3.5.0
ALIYUN_CLI_SHA256=6f1b0c380a456849adab800ee1396be58976020c6d9dfa3196dcfc41f496a518
JAVA_HOME_25=/opt/homebrew/opt/openjdk@25

purpose="${1:?usage: toolchain.sh <build|deploy> DIR}"
dir="${2:?usage: toolchain.sh <build|deploy> DIR}"
fail() { echo "toolchain.sh: $*" >&2; exit 1; }
[ "$(uname -s)-$(uname -m)" = Darwin-arm64 ] || fail "expects the macOS arm64 runner"
mkdir -p "$dir"
paths=()

fetch() {
  local url="$1" sha="$2" out="$3"
  curl -fsSL --proto '=https' --tlsv1.2 --retry 3 -o "$out" "$url"
  [ "$(shasum -a 256 "$out" | cut -d' ' -f1)" = "$sha" ] || { rm -f "$out"; fail "SHA256 mismatch for $url"; }
}

"$JAVA_HOME_25/bin/java" -version 2>&1 | head -1 | grep -q '"25' || fail "Java 25 expected at $JAVA_HOME_25 (brew install openjdk@25)"
paths+=("$JAVA_HOME_25/bin")

case "$purpose" in
  build)
    node_dir="$dir/node-v$NODE_VERSION-darwin-arm64"
    if [ ! -x "$node_dir/bin/node" ]; then
      fetch "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-darwin-arm64.tar.xz" "$NODE_SHA256" "$dir/node.tar.xz"
      tar -xJf "$dir/node.tar.xz" -C "$dir" && rm "$dir/node.tar.xz"
    fi
    paths+=("$node_dir/bin" "$dir/corepack-bin")
    mkdir -p "$dir/corepack-bin"
    # corepack fetches the pnpm named by packageManager and checks the npm registry signature
    COREPACK_HOME="$dir/corepack" "$node_dir/bin/corepack" enable pnpm --install-directory "$dir/corepack-bin"
    ;;
  deploy)
    cli_dir="$dir/aliyun-$ALIYUN_CLI_VERSION"
    if [ ! -x "$cli_dir/aliyun" ]; then
      mkdir -p "$cli_dir"
      fetch "https://github.com/aliyun/aliyun-cli/releases/download/v$ALIYUN_CLI_VERSION/aliyun-cli-macosx-$ALIYUN_CLI_VERSION-arm64.tgz" \
        "$ALIYUN_CLI_SHA256" "$dir/aliyun.tgz"
      tar -xzf "$dir/aliyun.tgz" -C "$cli_dir" && rm "$dir/aliyun.tgz"
    fi
    [ "$("$cli_dir/aliyun" version)" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI reports another version"
    paths+=("$cli_dir")
    ;;
  *) fail "unknown purpose '$purpose'" ;;
esac

if [ -n "${GITHUB_PATH:-}" ]; then
  printf '%s\n' "${paths[@]}" >> "$GITHUB_PATH"
  { echo "JAVA_HOME=$JAVA_HOME_25"; echo "COREPACK_HOME=$dir/corepack"; echo "COREPACK_ENABLE_DOWNLOAD_PROMPT=0"; echo "ALIYUN_CLI_VERSION=$ALIYUN_CLI_VERSION"; } >> "$GITHUB_ENV"
else
  echo "export PATH=$(IFS=:; echo "${paths[*]}"):\$PATH JAVA_HOME=$JAVA_HOME_25 COREPACK_HOME=$dir/corepack"
fi
