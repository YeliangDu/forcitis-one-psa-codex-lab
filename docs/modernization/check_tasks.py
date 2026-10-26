#!/usr/bin/env python3
"""GOV-1017-COO-01：检查 tasks/*.md 并生成 tasks/TASKS.md，禁止手改总表。

默认检查并生成；--check 只检查；--self-test 离线自测。
PR 标题取 --pr-title 或 GITHUB_EVENT_PATH，仅告警；本脚本不调用 gh、不联网。
用时统一为非负分钟数（可写 12、12m、1h30m，约数加「约」）；未知字段写「待核」。
总表只取片段内容；日报独立读取 origin/main 的登记与返工历史。
"""
import collections
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile

BASE = Path(__file__).resolve().parent
FIELDS = ('任务号', '任务名', '主线', 'bc', '种类', '会话', '执行者与模型', '状态', '分支', '提交', '返工轮次', '用时', '旧编号')
LANES = dict(NAM='命名', CON='施工图', ARC='架构整合', RUL='规则', DAT='数据', IMP='代码实现', CI='合并与 CI', OPS='运维', GOV='流程治理')
SESSION_CODES = ('GLO', 'AFI', 'ARE', 'APE', 'ARC', 'PAR', 'NX5', 'QUA', 'RUL', 'DAT', 'OPS', 'MRG', 'COO', 'ADM')
STATUSES = ('待派', '已派', '进行中', '复审中', '返工中', '待验收', '已完成', '已提交', '已合并', '阻塞', '已取消', '待核', '完成', '作废')
# 现行取值（用户 2026-10-21，intent 2026-10-21-213907）：STATUSES 里其余的是存量写法，先报告、不失败；LEGACY_STATUS 给出对应的现行取值
FRAGMENT_STATUSES = ('已派', '进行中', '阻塞', '完成', '作废')
LEGACY_STATUS = {'待派': '已派', '复审中': '进行中', '返工中': '进行中', '待验收': '进行中', '已完成': '完成', '已提交': '完成', '已合并': '完成', '已取消': '作废'}
STALE_HOURS = 12
KINDS = ('设计', '编码', '测试', '取证', '复审', '确认页', '文档')
TASK_ID = re.compile(r'(' + '|'.join(LANES) + r')-(\d{4})-(' + '|'.join(SESSION_CODES) + r')-(\d{2})')
UNKNOWN = '待核'
MACHINE = '<!-- TASKS-DATA v1 -->'
BACKFILL_DATE = datetime.date(2026, 10, 17)


def parse_task(path, text, warnings=None, *, historical=False):
    text = text.lstrip('\ufeff').replace('\r\n', '\n')
    front = re.match(r'\A---[ \t]*\n(.*?)\n---[ \t]*(?:\n|\Z)', text, re.S)
    if not front:
        raise ValueError(f'{path}：须以完整的 --- 键值头开始')
    fields = {}
    required = ('会话', '执行者与模型', '返工轮次') if historical else FIELDS
    allowed = (*required, '来源') if historical else (*FIELDS, '来源', '最后更新')
    for line in front[1].splitlines():
        key, sep, value = line.partition(':')
        key, value = key.strip(), value.strip()
        if key == '执行者':
            key = '执行者与模型'
            if warnings is not None:
                warnings.append(f'{path}：执行者是执行者与模型的兼容别名，请后续统一字段名')
        if historical and key not in allowed:
            continue
        if not sep or key not in allowed or key in fields:
            raise ValueError(f'{path}：键未知、重复或缺冒号：{key}')
        if value.startswith(('"', "'")):
            if len(value) < 2 or value[-1] != value[0]:
                raise ValueError(f'{path}：引号未闭合：{key}')
            value = value[1:-1]
        fields[key] = value or UNKNOWN
    if not set(required) <= fields.keys():
        raise ValueError(f'{path}：缺少字段 {"、".join(k for k in required if k not in fields)}')
    return fields


def minutes(value):
    if value == UNKNOWN:
        return None
    value = value.removeprefix('约').strip()
    if match := re.fullmatch(r'(\d+(?:\.\d+)?)\s*分钟(?:（[^（）\n]*）)?', value):
        return float(match[1])
    if re.fullmatch(r'\d+(?:\.\d+)?', value):
        return float(value)
    match = re.fullmatch(r'(?:(\d+(?:\.\d+)?)h)?(?:(\d+(?:\.\d+)?)m)?', value)
    if not match or not any(match.groups()):
        raise ValueError('用时须为非负分钟数、约 N 分钟、12m、1h30m 或待核')
    return float(match[1] or 0) * 60 + float(match[2] or 0)


def rounds(value):
    if value == UNKNOWN:
        return None
    if not re.fullmatch(r'\d+', value):
        raise ValueError('返工轮次须为非负整数或待核')
    return int(value)


def validate(tasks, bcs):
    errors, seen = [], set()
    for path, task in tasks:
        tag = str(path)
        match = TASK_ID.fullmatch(task['任务号'])
        if not match:
            errors.append(f'{tag}：任务号须为 <主线码>-<MMDD>-<会话码>-<两位序号>')
        else:
            try:
                datetime.datetime.strptime('2000' + match[2], '%Y%m%d')
            except ValueError:
                errors.append(f'{tag}：任务号 MMDD 不是有效月日')
            if task['主线'] != match[1]:
                errors.append(f'{tag}：主线须与任务号主线码一致')
        if path.stem != task['任务号']:
            errors.append(f'{tag}：片段须放在 tasks/{task["任务号"]}.md')
        if task['任务号'] in seen:
            errors.append(f'{tag}：任务号重复')
        seen.add(task['任务号'])
        if task['主线'] not in LANES:
            errors.append(f'{tag}：主线须取 {"/".join(LANES)}')
        if '来源' in task and task['来源'] != '回填':
            errors.append(f'{tag}：来源可省略，填写时须为回填')
        if task['bc'] not in bcs | {'跨域'}:
            errors.append(f'{tag}：bc 须取 glossary/bcs.yaml 的 id 或跨域：{task["bc"]}')
        if task['种类'] not in KINDS:
            errors.append(f'{tag}：种类须取 {"/".join(KINDS)}')
        if not re.fullmatch(r'\[[^\[\]·]+·[^\[\]]+\]\s+\S.*', task['任务名']):
            errors.append(f'{tag}：任务名须为 [主线·bc] 动作 + 对象')
        if task['会话'] != UNKNOWN and not re.fullmatch(r'one-[a-z0-9]+(?:-[a-z0-9]+)*', task['会话']):
            errors.append(f'{tag}：会话须为 one-* 或待核')
        if '最后更新' in task and task['最后更新'] != UNKNOWN and updated_at(task['最后更新']) is None:
            errors.append(f'{tag}：最后更新须为 ISO 日期或时刻（如 2026-10-21T21:30:00+02:00）或待核')
        for key, parse in (('返工轮次', rounds), ('用时', minutes)):
            try:
                parse(task[key])
            except ValueError as error:
                errors.append(f'{tag}：{error}')
    return errors


def task_warnings(tasks):
    warnings = []
    for path, task in tasks:
        name = re.fullmatch(r'\[([^\[\]·]+)·([^\[\]]+)\]\s+\S.*', task['任务名'])
        if name and (name[1] != LANES.get(task['主线']) or name[2] != task['bc']):
            warnings.append(f'{path}：任务名的主线中文名或 bc 与字段不一致，应为 [{LANES.get(task["主线"], task["主线"])}·{task["bc"]}]')
        if task['状态'] not in STATUSES:
            warnings.append(f'{path}：状态须取 {"/".join(FRAGMENT_STATUSES)}（存量先警告）')
    legacy = sorted({t['状态'] for _, t in tasks if t['状态'] in LEGACY_STATUS})
    if legacy:
        count = sum(t['状态'] in LEGACY_STATUS for _, t in tasks)
        warnings.append(f'{count} 张片段仍用存量状态值（{"、".join(f"{v}→{LEGACY_STATUS[v]}" for v in legacy)}）；状态现行取值 {"/".join(FRAGMENT_STATUSES)}，由负责会话改为现行取值')
    return warnings


def updated_at(value):
    """The moment a 最后更新 value names (a date counts as the end of that day, so a card updated "today" is never stale at noon), local time when no zone is given; None when it is neither an ISO date nor an ISO date-time."""
    try:
        moment = datetime.datetime.fromisoformat(value) if 'T' in value or ' ' in value else datetime.datetime.combine(datetime.date.fromisoformat(value), datetime.time(23, 59, 59))
    except ValueError:
        return None
    return moment if moment.tzinfo else moment.astimezone()


def merged_branches(base=BASE, ref='origin/main'):
    """Branch names merged into `ref` (without the origin/ prefix), None when git cannot say."""
    result = subprocess.run(['git', 'branch', '-r', '--merged', ref, '--format=%(refname:short)'], cwd=base, capture_output=True, text=True)
    if result.returncode:
        return None
    return {name.removeprefix('origin/') for name in result.stdout.split()}


def stale_report(tasks, now, merged, hours=STALE_HOURS):
    """Lines of the stale-register report: 进行中 or 阻塞 not updated for more than `hours` (or never), and a branch that is in main while the status is not 完成/作废. Report only."""
    lines = []
    shared = {b for b, n in collections.Counter(t['分支'] for _, t in tasks).items() if n > 1}   # one branch for several cards (or a branch used again after its merge) says nothing about one of them
    for path, task in tasks:
        status = task['状态']
        done = status in ('完成', '作废') or LEGACY_STATUS.get(status) in ('完成', '作废')
        if LEGACY_STATUS.get(status, status) in ('进行中', '阻塞'):
            stamp = updated_at(task['最后更新']) if task.get('最后更新', UNKNOWN) != UNKNOWN else None
            if stamp is None:
                lines.append(f'{task["任务号"]}（{task["会话"]}）状态 {status}，未填最后更新')
            elif now - stamp > datetime.timedelta(hours=hours):
                lines.append(f'{task["任务号"]}（{task["会话"]}）状态 {status}，最后更新 {task["最后更新"]}，已超过 {hours} 小时')
        branch = task['分支']
        if merged and branch in merged and branch not in shared and not done:
            lines.append(f'{task["任务号"]}（{task["会话"]}）分支 {branch} 已合入 main，状态仍是 {status}')
    return lines


def collect(base, warnings=None):
    folder = base / 'tasks'
    paths = sorted(folder.glob('*.md'))
    paths = [p for p in paths if p.name != 'TASKS.md' and not p.name.startswith('_')]
    return [(p, parse_task(p, p.read_text(encoding='utf-8'), warnings)) for p in paths]


def backfill_warnings(tasks, base=BASE):
    warnings = []
    for path, task in tasks:
        if task.get('来源') != '回填':
            continue
        result = subprocess.run(['git', 'log', '--no-renames', '--diff-filter=A', '--format=%cI', '--', str(path.relative_to(base))],
                                cwd=base, capture_output=True, text=True)
        dates = result.stdout.splitlines()
        if result.returncode or not dates:
            warnings.append(f'{path}：回填片段首次入库日期待核，无法核对 {BACKFILL_DATE} 截止规则')
        elif datetime.date.fromisoformat(dates[-1][:10]) > BACKFILL_DATE:
            warnings.append(f'{path}：首次入库日期 {dates[-1][:10]} 晚于回填登记截止日期 {BACKFILL_DATE}，新卡不可标回填')
    return warnings


def payload(tasks):
    return dict(version=1,
                片段摘要={path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path, _ in tasks},
                tasks=[task for _, task in tasks])


def metric(tasks, key, parse):
    values = [parse(task[key]) for task in tasks]
    approximate = '约' if any(task[key].startswith('约') for task in tasks) else ''
    known = [v for v in values if v is not None]
    total = f'{approximate}{sum(known):g}' if known or not values else UNKNOWN
    return f'{total}（待核 {values.count(None)} 张）'


def render(tasks, data):
    values = [task for _, task in tasks]
    out = ['# 任务登记总表\n', '由 `../check_tasks.py` 经 `../build_all.py` 生成，不手改。未知值不按零计；用时单位为分钟。\n']
    out.append(f'共 {len(values)} 张；返工轮次 {metric(values, "返工轮次", rounds)}；用时 {metric(values, "用时", minutes)}。\n')
    for key in ('主线', 'bc', '会话', '状态'):
        out += [f'## 按{key}汇总\n', '| 分组 | 卡数 | 返工轮次 | 用时（分钟） |', '|---|---|---|---|']
        for group in sorted({t[key] for t in values}):
            rows = [t for t in values if t[key] == group]
            out.append(f'| {group} | {len(rows)} | {metric(rows, "返工轮次", rounds)} | {metric(rows, "用时", minutes)} |')
        out.append('')
    out += ['## 卡片明细\n', '| ' + ' | '.join((*FIELDS, '来源')) + ' |', '|' + '|'.join('---' for _ in (*FIELDS, '来源')) + '|']
    for path, task in tasks:
        cells = [str(task[k]).replace('|', '／') for k in FIELDS] + [task.get('来源', '派工')]
        cells[0] = f'[{cells[0]}]({path.name})'
        out.append('| ' + ' | '.join(cells) + ' |')
    out += ['', '## 片段机器数据\n', 'JSON 只含片段字段与内容摘要，不含 Git 元数据。日报独立读取 origin/main 的片段与首次登记、返工轮次变更历史；来源为回填的卡不计入日报两列。\n', MACHINE, '```json', json.dumps(data, ensure_ascii=False, indent=2), '```', '']
    return '\n'.join(out)


def pr_warning(title):
    if not any(TASK_ID.fullmatch(token) for token in re.findall(r'[A-Z][A-Z0-9]*-\d{4}-[A-Z0-9]+-\d+', title)):
        return '警告：PR 标题未带合规任务号，请补 <主线码>-<MMDD>-<会话码>-<两位序号>（当前不失败）'
    return None


def main(argv):
    import os
    title, check, now = None, False, None
    while argv:
        arg = argv.pop(0)
        if arg == '--check':
            check = True
        elif arg == '--now' and argv:
            now = updated_at(argv.pop(0))
            if now is None:
                raise ValueError('--now 须为 ISO 时刻')
        elif arg == '--pr-title' and argv:
            title = argv.pop(0)
        else:
            raise ValueError(f'未知参数：{arg}')
    event = os.environ.get('GITHUB_EVENT_PATH')
    if title is None and event:
        title = json.loads(Path(event).read_text(encoding='utf-8')).get('pull_request', {}).get('title')
    if title is not None:
        warning = pr_warning(title)
        if warning:
            print(warning, file=sys.stderr)
    import yaml
    bcs = {b['id'] for b in yaml.safe_load((BASE / 'glossary/bcs.yaml').read_text(encoding='utf-8'))['bcs']}
    warnings = []
    tasks = collect(BASE, warnings)
    for warning in warnings + task_warnings(tasks) + backfill_warnings(tasks):
        print('警告：' + warning, file=sys.stderr)
    errors = validate(tasks, bcs)
    if errors:
        raise ValueError('\n'.join(errors))
    merged = merged_branches()
    if merged is None:
        print('警告：读不到 origin/main，跳过「分支已合入」核对', file=sys.stderr)
    stale = stale_report(tasks, now or datetime.datetime.now().astimezone(), merged)
    if stale:
        print(f'陈旧报告（先报告档，不失败；{len(stale)} 条）：', file=sys.stderr)
        for line in stale[:30]:
            print('  ' + line, file=sys.stderr)
        if len(stale) > 30:
            print(f'  …另有 {len(stale) - 30} 条', file=sys.stderr)
    if not check:
        (BASE / 'tasks/TASKS.md').write_text(render(tasks, payload(tasks)), encoding='utf-8')
    print(f'check_tasks：{len(tasks)} 张片段通过' + ('，已生成 tasks/TASKS.md' if not check else ''))


def self_test():
    task = dict(zip(FIELDS, ('NAM-1017-GLO-03', '[命名·跨域] 登记名词', 'NAM', '跨域', '文档', 'one-glossary', 'Codex gpt-6.1-sol xhigh', '进行中', '待核', '待核', '2', '1h30m', 'N5')))
    path = Path(task['任务号'] + '.md')
    text = '---\n' + '\n'.join(f'{k}: {v}' for k, v in task.items()) + '\n---\n正文\n'
    assert parse_task(path, text) == task
    for key in ('提交', '返工轮次', '用时', '旧编号'):
        blank = parse_task(path, text.replace(f'{key}: {task[key]}', f'{key}: '))
        assert blank[key] == UNKNOWN, f'{key} 空值须转待核'
    for key in FIELDS:
        try:
            parse_task(path, text.replace(f'{key}: {task[key]}\n', ''))
        except ValueError as error:
            assert '缺少字段' in str(error) and key in str(error)
        else:
            raise AssertionError(f'在途片段也不得缺少必填键 {key}')
    assert parse_task(path, text.replace(task['任务名'], '"' + task['任务名'] + '"')) == task
    warnings = []
    assert parse_task(path, text.replace('执行者与模型:', '执行者:'), warnings) == task
    assert len(warnings) == 1 and '兼容别名' in warnings[0]
    assert parse_task(path, text.replace('旧编号: N5', '来源: 回填\n旧编号: N5'))['来源'] == '回填'
    assert not validate([(path, task)], set())
    for key, value, message in (('任务号', 'N5', '任务号须为'), ('任务号', 'NAM-1332-GLO-03', '有效月日'), ('主线', 'CON', '主线码一致'), ('bc', 'missing', 'bc 须取'), ('种类', '其他', '种类须取'), ('任务名', '登记名词', '任务名须为'), ('返工轮次', '-1', '非负整数'), ('用时', 'nan', '用时须为')):
        assert any(message in e for e in validate([(path, dict(task, **{key: value}))], set())), (key, value)
    assert not validate([(path, dict(task, bc='kernel'))], {'kernel'})
    assert not validate([(path, dict(task, **{'来源': '回填'}))], set())
    assert any('来源' in e for e in validate([(path, dict(task, **{'来源': '错误'}))], set()))
    assert any('来源' in e for e in validate([(path, dict(task, 来源=UNKNOWN))], set())), '来源只允许回填'
    for code in SESSION_CODES:
        assert TASK_ID.fullmatch(f'NAM-1017-{code}-03')
    for code in LANES:
        assert TASK_ID.fullmatch(f'{code}-1017-GLO-03')
    for invalid in ('NAM-1017-XYZ-03', 'BAD-1017-GLO-03'):
        assert any('任务号须为' in e for e in validate([(path, dict(task, 任务号=invalid))], set()))
    assert not task_warnings([(path, task)])
    for change in ({'任务名': '[施工图·跨域] 登记名词'}, {'任务名': '[命名·kernel] 登记名词'}, {'状态': '新状态'}):
        changed = dict(task, **change)
        assert not validate([(path, changed)], set()) and len(task_warnings([(path, changed)])) == 1
    assert any('重复' in e for e in validate([(path, task), (path, task)], set()))
    assert any('片段须放' in e for e in validate([(Path('wrong.md'), task)], set()))
    for bad in (text.replace('种类: 文档\n', ''), text.replace('种类: 文档', '种类: 文档\n种类: 文档'), text.replace('---\n正文', '正文'), text.replace('执行者与模型:', '执行者: 待核\n执行者与模型:')):
        try:
            parse_task(path, bad)
        except ValueError:
            continue
        raise AssertionError('无效片段未拦下')
    assert minutes('90') == minutes('90m') == minutes('1h30m') == 90
    assert minutes('约40m') == 40
    assert minutes('约 25 分钟') == minutes('约25分钟') == minutes('25 分钟') == 25
    assert minutes('约 23 分钟（首轮 14 分钟，返工 8 分钟）') == 23
    assert minutes('约 10 分钟（第二次启动；首次因等标准输入空转 77 分钟）') == 10
    assert minutes(UNKNOWN) is None and rounds(UNKNOWN) is None
    assert pr_warning('feat: 不带编号') and not pr_warning('feat: NAM-1017-GLO-03 登记名词')
    data = dict(version=1, tasks=[task], 片段摘要={})
    output = render([(path, task)], data)
    assert all(f'按{k}汇总' in output for k in ('主线', 'bc', '会话', '状态'))
    assert '90（待核 0 张）' in output and json.loads(output.split('```json\n')[1].split('\n```')[0]) == data
    unknown = dict(task, **{'返工轮次': UNKNOWN, '用时': UNKNOWN})
    mixed = render([(path, task), (path, unknown)], data)
    assert '共 2 张；返工轮次 2（待核 1 张）；用时 90（待核 1 张）' in mixed, '合计须统计待核张数'
    assert metric([unknown], '返工轮次', rounds) == '待核（待核 1 张）', '整组待核不可显示 0'
    assert metric([dict(task, 用时='0')], '用时', minutes) == '0（待核 0 张）', '已知零仍显示 0'
    approximate = render([(path, dict(task, 用时='约40m')), (path, task), (path, unknown)], data)
    assert '共 3 张；返工轮次 4（待核 1 张）；用时 约130（待核 1 张）' in approximate, '总计保留约数标记'
    assert '| NAM | 3 | 4（待核 1 张） | 约130（待核 1 张） |' in approximate, '分组保留约数标记'
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        (base / 'tasks').mkdir()
        (base / 'tasks' / path).write_text(text, encoding='utf-8')
        (base / 'tasks/TASKS.md').write_text(output, encoding='utf-8')
        assert len(collect(base)) == 1, '总表不可再次当成片段'
        import os
        env = dict(os.environ, GIT_AUTHOR_NAME='test', GIT_AUTHOR_EMAIL='test@example.invalid', GIT_COMMITTER_NAME='test', GIT_COMMITTER_EMAIL='test@example.invalid')
        def command(*args, time=None):
            settings = dict(env, GIT_AUTHOR_DATE=time, GIT_COMMITTER_DATE=time) if time else env
            run = subprocess.run(['git', '-C', str(base), '-c', 'commit.gpgsign=false', *args], env=settings, capture_output=True, text=True)
            assert run.returncode == 0, run.stderr
        command('init', '-q', '-b', 'main')
        fragment = base / 'tasks' / path
        fragment.write_text(text.replace('返工轮次: 2', '返工轮次: 0'), encoding='utf-8')
        command('add', 'tasks/' + path.name)
        command('commit', '-qm', '初次登记', time='2026-10-16T08:00:00+02:00')
        backfill = [(fragment, dict(task, 来源='回填'))]
        assert not backfill_warnings(backfill, base), '截止日期前首次入库可回填'
        fragment.write_text(text.replace('返工轮次: 2', '返工轮次: 3'), encoding='utf-8')
        pending = render(collect(base), payload(collect(base)))
        command('add', 'tasks/' + path.name)
        command('commit', '-qm', '更新轮次', time='2026-10-16T12:00:00+02:00')
        current = collect(base)
        result = payload(current)
        assert result['tasks'] == [current[0][1]] and '记录' not in result['tasks'][0] and '未提交' not in result['tasks'][0]
        stable = render(current, result)
        assert stable == pending, '相同片段提交前后总表一致'
        (base / 'unrelated.txt').write_text('dirty', encoding='utf-8')
        assert render(collect(base), payload(collect(base))) == stable, '工作区脏净不影响总表'
        command('add', 'unrelated.txt')
        command('commit', '-qm', '无关普通提交', time='2026-10-16T13:00:00+02:00')
        assert render(collect(base), payload(collect(base))) == stable, '普通提交不影响总表'
        command('checkout', '-qb', 'side')
        command('commit', '--allow-empty', '-qm', '旁支', time='2026-10-16T14:00:00+02:00')
        command('checkout', '-q', 'main')
        command('merge', '--no-ff', '-qm', '合并提交', 'side', time='2026-10-16T15:00:00+02:00')
        assert render(collect(base), payload(collect(base))) == stable, '合并提交不影响总表'
        fragment.write_text(text, encoding='utf-8')
        assert render(collect(base), payload(collect(base))) != stable, '仅片段内容变化改变总表'
        late = base / 'tasks/NAM-1017-GLO-04.md'
        late.write_text(text.replace('NAM-1017-GLO-03', late.stem), encoding='utf-8')
        command('add', 'tasks/' + late.name)
        command('commit', '-qm', '截止当天新卡', time='2026-10-17T23:59:00+02:00')
        assert not backfill_warnings([(late, dict(task, 来源='回填'))], base), '截止当天可回填'
        later = base / 'tasks/NAM-1018-GLO-05.md'
        later.write_text(text.replace('NAM-1017-GLO-03', later.stem), encoding='utf-8')
        assert any('首次入库日期待核' in w for w in backfill_warnings([(later, dict(task, 来源='回填'))], base))
        command('add', 'tasks/' + later.name)
        command('commit', '-qm', '截止以后新卡', time='2026-10-18T00:01:00+02:00')
        assert any('新卡不可标回填' in w for w in backfill_warnings([(later, dict(task, 来源='回填'))], base)), '截止以后从旧模板复制的新卡须按新路径首次入库日警告'
        assert not backfill_warnings(backfill, base), '旧卡后续更新仍按首次入库日判断'
        late.unlink()
        later.unlink()
        (base / 'tasks/_RESULT.md').write_text('结果', encoding='utf-8')
        assert len(collect(base)) == 1, '结果文件不可当成片段'
    assert updated_at('2026-10-21') and updated_at('2026-10-21T21:30:00+02:00') and updated_at('2026-10-21 21:30') and updated_at('昨天') is None
    assert not validate([(path, dict(task, 最后更新='2026-10-21T21:30:00+02:00'))], set()) and not validate([(path, dict(task, 最后更新=UNKNOWN))], set())
    assert any('最后更新须为' in e for e in validate([(path, dict(task, 最后更新='昨天'))], set()))
    assert parse_task(path, text.replace('旧编号: N5', '旧编号: N5\n最后更新: 2026-10-21T21:30:00+02:00'))['最后更新'] == '2026-10-21T21:30:00+02:00'
    now = updated_at('2026-10-22T10:00:00+02:00')
    working = dict(task, 状态='进行中', 分支='claude/x')
    assert stale_report([(path, dict(working, 最后更新='2026-10-22T09:00:00+02:00'))], now, set()) == [], '一小时前更新不陈旧'
    assert stale_report([(path, dict(working, 最后更新='2026-10-21T22:00:00+02:00'))], now, set()) == [], '恰好十二小时不算超过'
    assert len(stale_report([(path, dict(working, 最后更新='2026-10-21T21:59:00+02:00'))], now, set())) == 1, '超过十二小时一分钟'
    assert len(stale_report([(path, dict(working, 最后更新='2026-10-21T21:00:00+02:00'))], now, set())) == 1, '超过十二小时'
    assert len(stale_report([(path, dict(working, 状态='阻塞', 最后更新='2026-10-20'))], now, set())) == 1, '阻塞同样算'
    assert len(stale_report([(path, working)], now, set())) == 1, '没填最后更新算陈旧'
    assert stale_report([(path, dict(working, 状态='完成'))], now, set()) == [] and stale_report([(path, dict(working, 状态='已派'))], now, set()) == [], '只看进行中与阻塞'
    assert stale_report([(path, dict(working, 状态='复审中'))], now, set()) != [], '存量的复审中按进行中算'
    assert len(stale_report([(path, dict(working, 状态='完成', 分支='claude/x'))], now, {'claude/x'})) == 0
    assert len(stale_report([(path, dict(working, 最后更新='2026-10-22T09:00:00+02:00'))], now, {'claude/x'})) == 1, '分支已合入而状态不是完成'
    twin = dict(working, 任务号='NAM-1017-GLO-04', 最后更新='2026-10-22T09:00:00+02:00')
    assert stale_report([(path, dict(working, 最后更新='2026-10-22T09:00:00+02:00')), (path, twin)], now, {'claude/x'}) == [], '几张卡共用一个分支：不凭分支判'
    assert updated_at('2026-10-22') > updated_at('2026-10-22T12:00:00') and updated_at('2026-10-22').hour == 23, '只写日期按当天结束算'
    assert len(stale_report([(path, dict(working, 状态='已派', 最后更新='2026-10-22T09:00:00+02:00'))], now, {'claude/x'})) == 1
    assert len(stale_report([(path, dict(working, 状态='已提交', 最后更新='2026-10-22T09:00:00+02:00'))], now, {'claude/x'})) == 0, '存量的已提交算完成'
    assert len(task_warnings([(path, dict(task, 状态='已合并')), (path, dict(task, 状态='待派'))])) == 1, '存量状态合成一条提示'
    assert '存量状态值' in task_warnings([(path, dict(task, 状态='已合并'))])[0]
    print('check_tasks --self-test：通过（格式、兼容字段、白名单、待核汇总、负向样例、生成与 Git 状态无关）')


if __name__ == '__main__':
    try:
        self_test() if sys.argv[1:] == ['--self-test'] else main(sys.argv[1:])
    except (ValueError, OSError, KeyError, ImportError) as error:
        sys.exit(f'check_tasks 失败：{error}')
