#!/usr/bin/env python3
"""校验 concerns.proposal.yaml 与 state-transitions.proposal.yaml 是否满足闭包规则。

用法：
  python3 validate_concerns.py            # 在 architecture/proposals 目录下运行
  python3 validate_concerns.py --json

校验项：
  CON 侧
    C-1 每条关注点二选一：accepted_as 非空 或 rejected 非空（不得皆空/皆非空）
    C-2 承接的关注点必须有 answered_by（≥1）
    C-3 弃权的必须写 reason / decider / review_if
    C-4 by 必须是 value-streams.yaml 里出现的 SH-ID；accepted_as 必须是已出现的阶段号或 VS 号
    C-5 每条必须有 evidence
  P-1 流程→执行的 Command 约束（v5 新增）
      07 业务流程的每一步只要改变对象状态，必须声明 command；
      规则来源：BPMN 的 Task 与 DDD 的 Command 不是一一对应，必须显式映射，
      否则流程目录与对象模型各写一套。登记文件：process-steps.proposal.yaml（可选）。
  TRANS 侧
    T-1 每条跃迁有 guard（或 free）
    T-2 终态有 terminal 标注，且终态无出边（更正走冲销，不靠出边表达）
    T-4 状态集合与跃迁端点一致（无孤儿状态、无未知状态）
    T-5 from/to 必须出现在该对象的 states 列表里（"*" 除外）
"""
import argparse, json, re, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load_yaml(p):
    try:
        import yaml
    except ImportError:
        sys.exit('需要 pyyaml：pip install pyyaml')
    return yaml.safe_load(Path(p).read_text(encoding='utf8'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--concerns', default=str(HERE / 'concerns.proposal.yaml'))
    ap.add_argument('--transitions', default=str(HERE / 'state-transitions.proposal.yaml'))
    ap.add_argument('--vs', default=str(HERE.parent / 'ssot' / '05-value-streams' / 'value-streams.yaml'))
    ap.add_argument('--json', action='store_true')
    a = ap.parse_args()

    C = load_yaml(a.concerns)
    T = load_yaml(a.transitions)
    vs_text = Path(a.vs).read_text(encoding='utf8')
    sh_ids = set(re.findall(r'SH-[A-Za-z0-9]+', vs_text))
    stages = set(re.findall(r'- id: (VS-\d+(?:\.\d+)?)', vs_text))

    errors, warns = [], []
    concerns = C.get('concerns') or []
    for c in concerns:
        cid = c.get('id', '?')
        acc, rej = c.get('accepted_as') or [], c.get('rejected') or None
        if bool(acc) == bool(rej):
            errors.append(f'{cid} C-1：accepted_as 与 rejected 必须二选一（当前 acc={bool(acc)}, rej={bool(rej)}）')
        if acc and not (c.get('answered_by') or []):
            errors.append(f'{cid} C-2：已承接但缺 answered_by（不可验证）')
        if rej:
            for k in ('reason', 'decider', 'review_if'):
                if not (isinstance(rej, dict) and rej.get(k)):
                    errors.append(f'{cid} C-3：弃权缺 {k}')
        if c.get('by') not in sh_ids:
            errors.append(f'{cid} C-4：by={c.get("by")} 不在 STAKEHOLDERS 登记里')
        for t in acc:
            if t not in stages and not re.match(r'VS-\d+$', str(t)):
                warns.append(f'{cid} C-4：accepted_as "{t}" 不是已登记阶段号（可能是 VS 级引用，请确认）')
        if not (c.get('evidence') or ''):
            errors.append(f'{cid} C-5：缺 evidence')

    trans = T.get('objects') or []
    for o in trans:
        oid = o.get('obj', '?')
        states = o.get('states') or []
        term = set(o.get('terminal') or [])
        used = set()
        for t in o.get('transitions') or []:
            f, to = t.get('from'), t.get('to')
            if not t.get('guard'):
                errors.append(f'{oid} T-1：{f}→{to} 缺 guard（或写 free）')
            for s in (f, to):
                if s == '*':
                    continue
                used.add(s)
                if s not in states:
                    errors.append(f'{oid} T-5：跃迁端点 "{s}" 不在 states 列表')
            if f in term:
                errors.append(f'{oid} T-2：终态 {f} 仍有出边（更正应走冲销，不靠跃迁表达）')
        orphan = [s for s in states if s not in used]
        if orphan:
            errors.append(f'{oid} T-4：孤儿状态（无进出边）：{orphan}')
        unreachable_note = [s for s in states if s not in used]
        if unreachable_note and not o.get('notes'):
            warns.append(f'{oid}：{unreachable_note} 无跃迁且无 notes 说明')

    # P-1：流程步骤 → Command
    steps_p = HERE / 'process-steps.proposal.yaml'
    state_changing = []
    for o in trans:
        for t in o.get('transitions') or []:
            if t.get('guard') and t.get('guard') != 'free':
                state_changing.append((o.get('obj'), t.get('from'), t.get('to')))
    if steps_p.exists():
        data = load_yaml(steps_p)
        steps = data.get('steps') or []
        covered = {(x.get('obj'), x.get('from'), x.get('to')) for x in steps if x.get('command')}
        missing = [sc for sc in state_changing if sc not in covered]
        if missing:
            for m in missing[:10]:
                errors.append(f'P-1：{m[0]} 的 {m[1]}→{m[2]} 有约束但没有流程步骤声明 command')
    else:
        warns.append(f'P-1 未覆盖：{len(state_changing)} 处「有约束的状态跃迁」尚无 process-steps.proposal.yaml 登记（不影响本图，标注为待建）')
    result = {'concerns': len(concerns), 'objects_with_transitions': len(trans),
              'state_changing_transitions': len(state_changing),
              'errors': errors, 'warnings': warns, 'ok': not errors}
    if a.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
    else:
        print(f"关注点 {len(concerns)} 条｜跃迁表对象 {len(trans)} 个")
        print(f"错误 {len(errors)}｜提醒 {len(warns)}")
        for e in errors:
            print('  ✗', e)
        for w in warns:
            print('  !', w)
        print('结论：', 'PASS ✅' if not errors else 'FAIL ❌')
    sys.exit(0 if not errors else 1)


if __name__ == '__main__':
    main()
