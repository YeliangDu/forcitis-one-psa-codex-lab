#!/usr/bin/env bash
# Hosted Ubuntu only; fixed upstream release and checksum-verified executable.
set -euo pipefail
test "$(uname -s)" = Linux
test "$(uname -m)" = x86_64
directory="$RUNNER_TEMP/lab-gitleaks-8.30.1"
mkdir -p "$directory"
cd "$directory"
release=https://github.com/gitleaks/gitleaks/releases/download/v8.30.1
asset=gitleaks_8.30.1_linux_x64.tar.gz
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
  "$release/$asset" --output "$asset"
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
  "$release/gitleaks_8.30.1_checksums.txt" --output checksums.txt
awk -v asset="$asset" '$2 == asset {print}' checksums.txt > selected-checksum.txt
test "$(wc -l < selected-checksum.txt)" -eq 1
sha256sum --check --strict selected-checksum.txt
tar -xzf "$asset" gitleaks
test "$(./gitleaks version)" = 8.30.1
printf '%s\n' "$directory" >> "$GITHUB_PATH"
