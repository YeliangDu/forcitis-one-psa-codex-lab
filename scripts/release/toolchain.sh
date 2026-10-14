#!/usr/bin/env bash
# Prepares the toolchain of the release and deploy jobs; every download is pinned by version and SHA256 into DIR, and each SHA256 was
# matched against the publisher's own checksum file when it was pinned.
#
#   scripts/release/toolchain.sh build  DIR    GitHub-hosted ubuntu-24.04 (release build): Java 25, Node 24, gh, pnpm via corepack
#   scripts/release/toolchain.sh deploy DIR    dedicated macOS deploy runner: vets PATH, system tools and the account's home first
#                                              (fails closed), then Java 25 (javap for verify_bundle.sh), gh, aliyun CLI
#   scripts/release/toolchain.sh check         the deploy runner vetting only
#
# Inside GitHub Actions the tool directories are appended to GITHUB_PATH; elsewhere the PATH line to export is printed.
set -euo pipefail

NODE_VERSION=24.18.0
JDK_RELEASE=jdk-25.0.4.1+1
GH_VERSION=2.100.0
ALIYUN_CLI_VERSION=3.5.0
# linux-x64 (GitHub-hosted build)
NODE_LINUX_SHA256=55aa7153f9d88f28d765fcdad5ae6945b5c0f98a36881703817e4c450fa76742
JDK_LINUX_ASSET=OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz
JDK_LINUX_SHA256=dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e
GH_LINUX_SHA256=e4d4bb4498e8d007abe545b6568926793ace1b6447da598294a610018cb164be
# darwin-arm64 (deploy runner)
JDK_MAC_ASSET=OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.4.1_1.tar.gz
JDK_MAC_SHA256=61979887f7506a24a57439ff99adb8b3a7fc89977d9cfe3b8984f58a981b7b9d
GH_MAC_SHA256=45f9a62da2f6e641a7fad57e2ce39656dfd7ef331372d80a2a2aed65abb01642
ALIYUN_CLI_MAC_SHA256=6f1b0c380a456849adab800ee1396be58976020c6d9dfa3196dcfc41f496a518
# Optional root-installed JDK on the deploy runner (Temurin .pkg installs root-owned under /Library)
SYSTEM_JDK=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
SYSTEM_TOOLS=(bash sh env python3 git curl tar shasum unzip ssh ssh-keygen base64 awk sed grep find xargs mktemp cut tr sort head tail cmp stat)
# Files in the account's home that would steer python3, git, aliyun or curl behind the job's back
HOME_STEERING=(Library/Python .gitconfig .config/git .aliyun .curlrc)

purpose="${1:?usage: toolchain.sh <build|deploy|check> [DIR]}"
fail() { echo "toolchain.sh: $*" >&2; exit 1; }

fetch() {
  local url="$1" sha="$2" out="$3" actual
  curl -fsSL --proto '=https' --tlsv1.2 --retry 3 -o "$out" "$url"
  if command -v sha256sum >/dev/null 2>&1; then actual="$(sha256sum "$out" | cut -d' ' -f1)"; else actual="$(shasum -a 256 "$out" | cut -d' ' -f1)"; fi
  [ "$actual" = "$sha" ] || { rm -f "$out"; fail "SHA256 mismatch for $url"; }
}

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

vet_deploy_runner() {
  [ "$(/usr/bin/uname -s)-$(/usr/bin/uname -m)" = Darwin-arm64 ] || fail "deploy runs on the macOS arm64 deploy runner"
  local entry tool resolved account_home item
  IFS=':' read -r -a path_entries <<< "$PATH"
  for entry in "${path_entries[@]}"; do
    [ -n "$entry" ] || continue
    case "$entry" in "${RUNNER_TEMP:-/nonexistent}"/*) continue ;; esac
    trusted "$entry" || fail "PATH entry $entry is not root-owned and protected; the runner's PATH must list system directories only (R-0)"
  done
  for tool in "${SYSTEM_TOOLS[@]}"; do
    resolved="$(command -v "$tool")" || fail "$tool not found"
    trusted "$resolved" || fail "$tool resolves to $resolved, which is not root-owned and protected"
  done
  # /usr/bin/python3 and /usr/bin/git hand over to the Command Line Tools; vet the program that really runs
  trusted "$(python3 -I -c 'import sys; print(sys.executable)')" || fail "python3 runs an interpreter outside root-owned directories"
  trusted "$(/usr/bin/xcrun --find git)" || fail "git runs a binary outside root-owned directories"
  account_home="$(python3 -I -c 'import os, pwd; print(pwd.getpwuid(os.getuid()).pw_dir)')"
  for item in "${HOME_STEERING[@]}"; do
    [ ! -e "$account_home/$item" ] || fail "$account_home/$item exists; it would steer the job's tools (R-0)"
    [ ! -e "$HOME/$item" ] || fail "$HOME/$item exists; it would steer the job's tools (R-0)"
  done
}

case "$purpose" in
  check) vet_deploy_runner; echo "toolchain.sh: deploy runner vetted"; exit 0 ;;
  build)
    [ "$(uname -s)-$(uname -m)" = Linux-x86_64 ] && [ "${RUNNER_ENVIRONMENT:-}" = github-hosted ] \
      || fail "the release build runs on a GitHub-hosted ubuntu-24.04 runner"
    ;;
  deploy) vet_deploy_runner ;;
  *) fail "unknown purpose '$purpose'" ;;
esac

dir="${2:?usage: toolchain.sh <build|deploy> DIR}"
mkdir -p "$dir"
paths=()

if [ "$purpose" = build ]; then
  fetch "https://github.com/adoptium/temurin25-binaries/releases/download/${JDK_RELEASE/+/%2B}/$JDK_LINUX_ASSET" "$JDK_LINUX_SHA256" "$dir/jdk.tar.gz"
  mkdir -p "$dir/jdk" && tar -xzf "$dir/jdk.tar.gz" -C "$dir/jdk" --strip-components 1 && rm "$dir/jdk.tar.gz"
  java_home="$dir/jdk"
  fetch "https://github.com/cli/cli/releases/download/v$GH_VERSION/gh_${GH_VERSION}_linux_amd64.tar.gz" "$GH_LINUX_SHA256" "$dir/gh.tar.gz"
  tar -xzf "$dir/gh.tar.gz" -C "$dir" && rm "$dir/gh.tar.gz"
  paths+=("$java_home/bin" "$dir/gh_${GH_VERSION}_linux_amd64/bin")
  node_dir="$dir/node-v$NODE_VERSION-linux-x64"
  fetch "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-x64.tar.xz" "$NODE_LINUX_SHA256" "$dir/node.tar.xz"
  tar -xJf "$dir/node.tar.xz" -C "$dir" && rm "$dir/node.tar.xz"
  mkdir -p "$dir/corepack-bin"
  # corepack fetches the pnpm named by packageManager and checks it against the +sha512 pinned there
  COREPACK_HOME="$dir/corepack" "$node_dir/bin/corepack" enable pnpm --install-directory "$dir/corepack-bin"
  paths+=("$node_dir/bin" "$dir/corepack-bin")
else
  if [ -x "$SYSTEM_JDK/bin/java" ] && trusted "$SYSTEM_JDK/bin/java"; then
    java_home="$SYSTEM_JDK"
  else
    fetch "https://github.com/adoptium/temurin25-binaries/releases/download/${JDK_RELEASE/+/%2B}/$JDK_MAC_ASSET" "$JDK_MAC_SHA256" "$dir/jdk.tar.gz"
    mkdir -p "$dir/jdk" && tar -xzf "$dir/jdk.tar.gz" -C "$dir/jdk" --strip-components 1 && rm "$dir/jdk.tar.gz"
    java_home="$dir/jdk/Contents/Home"
  fi
  fetch "https://github.com/cli/cli/releases/download/v$GH_VERSION/gh_${GH_VERSION}_macOS_arm64.zip" "$GH_MAC_SHA256" "$dir/gh.zip"
  unzip -q -o "$dir/gh.zip" -d "$dir" && rm "$dir/gh.zip"
  cli_dir="$dir/aliyun-$ALIYUN_CLI_VERSION"
  mkdir -p "$cli_dir"
  fetch "https://github.com/aliyun/aliyun-cli/releases/download/v$ALIYUN_CLI_VERSION/aliyun-cli-macosx-$ALIYUN_CLI_VERSION-arm64.tgz" \
    "$ALIYUN_CLI_MAC_SHA256" "$dir/aliyun.tgz"
  tar -xzf "$dir/aliyun.tgz" -C "$cli_dir" && rm "$dir/aliyun.tgz"
  [ "$("$cli_dir/aliyun" version)" = "$ALIYUN_CLI_VERSION" ] || fail "aliyun CLI reports another version"
  paths+=("$java_home/bin" "$dir/gh_${GH_VERSION}_macOS_arm64/bin" "$cli_dir")
fi
"$java_home/bin/java" -version 2>&1 | head -1 | grep -q '"25' || fail "Java 25 expected at $java_home"

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
