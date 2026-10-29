## 原话

问「这些表符合olap、oltp、dama的核心思想吗？这么检查对吗？」后选「做，并把能自动化的变成守护 (Recommended)」（2026-10-14，经 psa-coordinator 转达）

## 落点

docs/data/data-scorecard.md：1a 全部表逐表按 OLTP / OLAP 准备 / DAMA 检查；可自动化项给出守护规格（由 1a 实现）；数据架构检查清单并入 SESSION_PLAYBOOK §14
