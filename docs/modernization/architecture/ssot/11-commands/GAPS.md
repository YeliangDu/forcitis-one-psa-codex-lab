# 命令编号缺口

由 panorama/scan_commands.py 生成，勿手改。

## 合并核对

- commands finance：171
- commands people：168
- commands revenue：264
- commands 合计：603
- matrix：603
- 合并后：613；两处都有：593；仅 commands：10；仅 matrix：10
- 已建命令类与聚合关系：85（未做类名与 CMD 对照）

## 无 owner_bc（3）

- CMD-1afc1f3f — AGG-136 — 生效账期规则版本
- CMD-c401c884 — AGG-136 — 编制账期规则版本
- CMD-c2d24f95 — REF-01 — 登记招标项目

owner_bc 仅取 planned.yaml 的 aggregates[].BC 原文；未列入该段的引用不推断 BC。
actor 只取 raci.A 的 role、function 原文；未声明的字段保留 null，不从实例字段推断角色。
source.index 为各来源列表的从 0 开始序号；group 汇集 commands 文件分组与 matrix 的 a_source group 值。

## 同名跨聚合（6 组，33 条）

- 开具销项发票并改写 WIP：AGG-44 / CMD-cc7b8dce；AGG-46 / CMD-7af93128
- 提交申报：AGG-90 / CMD-c4c6427c；AGG-95 / CMD-ce706d71
- 提交过账候选：AGG-109 / CMD-01e1b9fe；AGG-131 / CMD-d7e12434；AGG-39 / CMD-a298af3d；AGG-41 / CMD-c6c46996；AGG-43 / CMD-0e8c882e；AGG-44 / CMD-2fdf7108；AGG-46 / CMD-f86a5a2d；AGG-49 / CMD-e000dd09；AGG-50 / CMD-660c68fb；AGG-51 / CMD-c60405cf；AGG-52 / CMD-7abe02af；AGG-53 / CMD-8760f315；AGG-54 / CMD-29588f43；AGG-57 / CMD-f45ea637；AGG-59 / CMD-7fdb9433；AGG-73 / CMD-e33f0978；AGG-88 / CMD-b0b2bf05；AGG-89 / CMD-4cb5a200；AGG-90 / CMD-6e73407b；AGG-91 / CMD-2438ab38；AGG-95 / CMD-e175f409；AGG-98 / CMD-40938608；AGG-99 / CMD-5a647d09
- 更正申报：AGG-90 / CMD-80764bf6；AGG-95 / CMD-70829ffb
- 生成申报：AGG-90 / CMD-5d835e89；AGG-95 / CMD-f5007ba9
- 登记缴款：AGG-90 / CMD-3361ae6b；AGG-95 / CMD-7983b5b2

## 转换回填率

- 合计：834/881（94.67%）
- transitions：825/872；未回填 47
- pending：9/9；未回填 0

只匹配 (aggregate, command 原文)，不去空格、不去「〔缺〕」、不做近似或同义匹配。

### 未回填清单

- transitions / OTR-017 — AGG-55 — None
- transitions / OTR-019 — AGG-55 — None
- transitions / OTR-020 — AGG-55 — None
- transitions / OTR-021 — AGG-55 — None
- transitions / OTR-025 — AGG-55 — None
- transitions / OTR-026 — AGG-55 — None
- transitions / OTR-027 — AGG-55 — None
- transitions / OTR-087 — AGG-56 — None
- transitions / OTR-088 — AGG-56 — None
- transitions / OTR-414 — AGG-40 — None
- transitions / OTR-415 — AGG-40 — None
- transitions / OTR-416 — AGG-40 — None
- transitions / OTR-423 — AGG-45 — None
- transitions / OTR-511 — AGG-53 — None
- transitions / OTR-514 — AGG-53 — None
- transitions / OTR-515 — AGG-53 — None
- transitions / OTR-536 — AGG-59 — None
- transitions / OTR-537 — AGG-59 — None
- transitions / OTR-567 — AGG-90 — None
- transitions / OTR-584 — AGG-91 — None
- transitions / OTR-589 — AGG-82 — None
- transitions / OTR-590 — AGG-82 — None
- transitions / OTR-612 — AGG-98 — None
- transitions / OTR-613 — AGG-98 — None
- transitions / OTR-614 — AGG-98 — None
- transitions / OTR-619 — AGG-86 — None
- transitions / OTR-620 — AGG-86 — None
- transitions / OTR-690 — AGG-109 — None
- transitions / OTR-720 — AGG-115 — None
- transitions / OTR-725 — AGG-115 — None
- transitions / OTR-785 — AGG-78 — None
- transitions / OTR-786 — AGG-78 — None
- transitions / OTR-789 — AGG-123 — None
- transitions / OTR-790 — AGG-123 — None
- transitions / OTR-793 — AGG-124 — None
- transitions / OTR-794 — AGG-124 — None
- transitions / OTR-797 — AGG-133 — None
- transitions / OTR-798 — AGG-133 — None
- transitions / OTR-807 — AGG-101 — None
- transitions / OTR-827 — AGG-85 — None
- transitions / OTR-828 — AGG-85 — None
- transitions / OTR-831 — AGG-94 — None
- transitions / OTR-832 — AGG-94 — None
- transitions / OTR-839 — AGG-128 — None
- transitions / OTR-840 — AGG-128 — None
- transitions / OTR-843 — AGG-129 — None
- transitions / OTR-844 — AGG-129 — None
