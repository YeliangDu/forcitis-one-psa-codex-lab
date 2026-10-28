## 原话

A 外层沙箱管全部（推荐）

## 落点

codex 读隔离 49：外层 sandbox-exec 读只放行快照、系统目录、codex 程序与认证文件，写只放行快照与 codex 状态目录，出网只放 443；codex 改 danger-full-access 写死，改动须终审；代价是子命令也能连 443（macOS 沙箱不能嵌套，B 不限读、C 起不来）

## 类别

技术

## 负责会话

one-ops

## 影响路径

scripts/delegate.py
