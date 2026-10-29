## 原话

把 README.md 中的术语表改为由 backend/glossary/proposed/*.yaml 自动生成（与 docs/build_all.py 生成 INTENT.md 等同一思路：片段为源，产物不手改）

## 落点

backend/glossary/build_proposed_index.py（build_all.py 调用、--check 覆盖）；SESSION_PLAYBOOK §4 第 0 步、§6
