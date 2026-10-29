#!/usr/bin/env bash
# 由 C-3 的 dev 脚本生成 C-9 的 prod 脚本（只做名称替换），或用 --check 校验已提交的 prod 脚本与 dev 一致。
# 用法：./render-prod-sql.sh          生成 prod-db*.sql
#      ./render-prod-sql.sh --check  不写文件，不一致时以 1 退出
set -euo pipefail
cd "$(dirname "$0")"

render() {
  sed -e 's/forcitis_one_dev/forcitis_one_prod/g' \
      -e 's/one_dev_/one_prod_/g' \
      -e 's/one\\_dev\\_/one\\_prod\\_/g' \
      -e 's/dev-db/prod-db/g' \
      -e 's/FORCITIS one · C-3/FORCITIS one · C-9/' \
      -e 's/生产版 prod-db.sql 由本文件经 render-prod-sql.sh 生成，不手改。/由 dev-db.sql 经 render-prod-sql.sh 生成，不手改。/' \
      "$1"
}

status=0
for suffix in "" -verify -rollback -after-migrations; do
  src="dev-db${suffix}.sql"
  dst="prod-db${suffix}.sql"
  if [[ "${1:-}" == "--check" ]]; then
    if ! diff -u "$dst" <(render "$src") >/dev/null; then
      echo "不一致：$dst 与 $src 的渲染结果不同，请重新运行 render-prod-sql.sh" >&2
      status=1
    fi
  else
    render "$src" > "$dst"
    echo "已生成 $dst"
  fi
done
exit $status
