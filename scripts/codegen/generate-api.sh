#!/usr/bin/env bash
# OpenAPI and its TypeScript consumers must be ready before either backend test plugin starts.
set -euo pipefail
cd "$(dirname "$0")/../.."
products=(frontend/packages/api/openapi.json)
for name in schema validators operations; do products+=("frontend/packages/api/src/generated/$name.ts"); done
if [ "${1:-}" = --check ]; then
  for product in "${products[@]}"; do
    if [ ! -s "$product" ]; then
      echo "缺少生成产物 ${product}；请在仓库根运行 bash scripts/codegen/generate-api.sh（需要已安装的前端依赖和 Docker；离线 Maven 加 -o）。" >&2
      exit 1
    fi
  done
  exit 0
fi
# Delete old output first: a failed export must not leave a usable snapshot from an earlier checkout.
rm -f "${products[@]}"
(cd backend && ./mvnw -B test-compile surefire:test@export-openapi "$@")
(cd frontend && pnpm --offline --config.verify-deps-before-run=false run generate:api)
bash scripts/codegen/generate-api.sh --check
