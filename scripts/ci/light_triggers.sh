# shellcheck shell=bash disable=SC2034,SC2154  # 被 source 的库：变量在读入方用，base 与 changed 由读入方设
# light.sh 的辅助：按改动触发的检查（GOV-1023-FV2 gap-2）与字节码清理（#693）；由 light.sh 与 light_selftest.sh 用 source 读入，不单独运行。
# 路径模式照搬原 precheck.sh 的 touches 模式（ERE，对仓库相对路径）；改动＝相对基线的提交、未提交与未跟踪文件；没有基线（$base 为空）时全算改到。
# 读入方须先设 base（基线，可为空）与 changed（改动列表文件，一行一个路径）。Bash 3.2 可用。

t_shell='\.sh$'
TRIGGER_KEYS='t_shell'

# 检查表：模式名|名字|命令（命令按空格切分，路径里不能有空格）。#742 删了确认页、j_checks 与两个只报不拦的扫描，只剩 shell 语法一项。
TRIGGERED_CHECKS='t_shell|shell syntax (bash -n)|shell_syntax'

touches() {  # touches <模式>：没有基线时一律算改到；否则看 $changed 里有没有匹配的路径（大小写不敏感：macOS 上大小写改名的路径也触发，同 check_pages.is_page_path）
  [ -z "$base" ] || grep -Eqi "$1" "$changed"
}

list_changed() {  # list_changed <输出文件>：写出相对 $base 的改动路径；任一条 git 命令失败即返回非零（不当成「没有改动」）
  # 用 -z：含引号、反斜杠、制表符的文件名，git 不加引号原样给出（core.quotepath=off 只管非 ASCII）
  {
    git diff -z --name-only --no-renames "$base...HEAD" &&
      git diff -z --name-only --no-renames HEAD &&
      git ls-files -z --others --exclude-standard
  } >"$1.z" || return 1
  tr '\0' '\n' <"$1.z" | sed '/^$/d' | sort -u >"$1"
}

shell_syntax() {  # 改到的 .sh 逐个 bash -n（没有基线时全部已跟踪的 .sh）；已删除的跳过
  local file
  while IFS= read -r file; do
    [ ! -f "$file" ] || bash -n "$file" || return 1
  done < <(if [ -n "$base" ]; then grep -E "$t_shell" "$changed"; else git ls-files '*.sh'; fi)
}

pyc() {  # 被忽略的未跟踪字节码，未跟踪目录折叠成一行；-z 防非 ASCII 路径被加引号；一个没有也返回 0
  git ls-files -z -oi --exclude-standard --directory | tr '\0' '\n' | { grep -E '(^|/)__pycache__/$|\.pyc$' || true; } | sort
}

rm_new_pyc() {  # 删掉 $work/pyc_before 之后新增的；跑前已有的 __pycache__ 目录整个不动（本机局限，CI 检出是干净的）
  local p
  [ -f "$work/pyc_before" ] || return 0
  pyc | comm -13 "$work/pyc_before" - | while IFS= read -r p; do
    echo "removed $p"
    rm -rf -- "./${p%/}" || return 1  # ./ 前缀：文件名含换行被 tr 拆开时，残片也只落在仓库内；去掉结尾斜杠：目录被换成符号链接时只删链接本身
  done
}
