#!/usr/bin/env python3
"""01 干系人：以 SSOT 为唯一真相的校验。

SSOT.yaml 的 model 三段：taxonomy / stakeholders / roles / project_roles。
  K-1 Category ∈ 冻结 6 类
  K-2 Type ∈ {内部, 外部, 两者}
  K-3 每个 Category 至少 1 个干系人
  K-4 同一 Category 下 Specific 不重名
  K-5 taxonomy.frozen=true 且 categories 与内容一致
  K-6 06 的 roll-up 不出现 6 类之外
  K-7 每个干系人的 family（若有）∈ 其 Category 的族词表
  K-8 每个族标签至少 1 个干系人
  K-9 角色 / 项目参与方不得混入干系人表
退出码：0 = PASS，1 = FAIL。
"""
import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
FROZEN = ('客户', '合作伙伴', '人力资源', '监管', '股东', '社会公众')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--json', action='store_true')
    a = ap.parse_args()

    s = yaml.safe_load((HERE / 'SSOT.yaml').read_text(encoding='utf8'))
    m = s.get('model') or {}
    tax = m.get('taxonomy') or {}
    shs = m.get('stakeholders') or []
    roles = m.get('roles') or []
    proj = m.get('project_roles') or []
    families = tax.get('families') or {}
    errors = []

    by_cat, by_family = defaultdict(list), defaultdict(list)
    for x in shs:
        c, g, sp = x.get('category'), x.get('family'), x.get('specific')
        if c not in FROZEN:
            errors.append(f"K-1 {x.get('id')} {sp}：Category「{c}」不在冻结 6 类里")
        if x.get('type') not in ('内部', '外部', '两者'):
            errors.append(f"K-2 {x.get('id')}：Type「{x.get('type')}」非法")
        if g and g not in (families.get(c) or []):
            errors.append(f"K-7 {x.get('id')} {sp}：family「{g}」不在「{c}」的族词表里")
        by_cat[c].append(sp)
        by_family[(c, g)].append(sp)
    for c in FROZEN:
        if not by_cat.get(c):
            errors.append(f'K-3 冻结类别「{c}」没有干系人')
        for g in families.get(c) or []:
            if not by_family.get((c, g)):
                errors.append(f'K-8 「{c}／{g}」在族词表里，但没有干系人')
    for c, names in by_cat.items():
        dup = [n for n, k in Counter(names).items() if k > 1]
        if dup:
            errors.append(f"K-4 「{c}」下 Specific 重名：{'、'.join(dup)}")

    if not tax.get('frozen'):
        errors.append('K-5 taxonomy.frozen 不是 true（SSOT 未声明为唯一真相）')
    if list(tax.get('categories') or []) != list(FROZEN):
        errors.append('K-5 taxonomy.categories 与冻结 6 类不一致')

    roll = HERE.parent / '06-价值流与阶段' / 'stakeholder-rollup.proposal.yaml'
    if roll.exists():
        d = yaml.safe_load(roll.read_text(encoding='utf8')) or {}
        cats = {r['category'] for r in d.get('category_to_value_stream') or []}
        bad = cats - set(FROZEN)
        if bad:
            errors.append(f"K-6 roll-up 出现 6 类之外的 Category：{'、'.join(sorted(bad))}")

    sids = {x.get('id') for x in shs}
    for r in roles + proj:
        if r.get('id') in sids:
            errors.append(f"K-9 {r.get('id')} 同时出现在干系人与角色/项目参与方里")

    stats = {'stakeholders': len(shs), 'categories': len(FROZEN),
             'families': sum(len(v) for v in families.values()),
             'roles': len(roles), 'project_roles': len(proj),
             'per_category': {c: len(by_cat.get(c, [])) for c in FROZEN},
             'per_family': {f'{c}／{g}': len(by_family.get((c, g), [])) for c in FROZEN for g in families.get(c, [])}}
    if a.json:
        print(json.dumps({'stats': stats, 'errors': errors}, ensure_ascii=False, indent=2))
    else:
        print(f"干系人 {len(shs)}｜类别 {len(FROZEN)}｜族 {stats['families']}"
              f"｜角色 {len(roles)}｜项目参与方 {len(proj)}")
        print('  ' + '｜'.join(f'{c} {len(by_cat.get(c, []))}' for c in FROZEN))
        for e in errors:
            print(f'  ✗ {e}')
        print('结论： ' + ('PASS ✅' if not errors else 'FAIL ❌'))
    return 0 if not errors else 1


if __name__ == '__main__':
    sys.exit(main())
