#!/usr/bin/env bash
# Install the same ShellCheck release as the shared CI guard, for GitHub Linux.
set -euo pipefail

: "${RUNNER_TEMP:?GitHub runner temp directory required}"
: "${GITHUB_PATH:?GitHub path file required}"
version=0.11.0
test "$(uname -s)-$(uname -m)" = Linux-x86_64
asset="shellcheck-v$version.linux.x86_64.tar.xz"
directory="$RUNNER_TEMP/lab-shellcheck"
mkdir -p "$directory"
cd "$directory"
curl --fail --silent --show-error --location --retry 3 \
  "https://github.com/koalaman/shellcheck/releases/download/v$version/$asset" -o "$asset"
printf '%s  %s\n' 8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198 "$asset" | sha256sum --check -
tar -xJf "$asset" "shellcheck-v$version/shellcheck"
test "$("shellcheck-v$version/shellcheck" --version | awk '/^version:/ { print $2 }')" = "$version"
printf '%s\n' "$PWD/shellcheck-v$version" >> "$GITHUB_PATH"
