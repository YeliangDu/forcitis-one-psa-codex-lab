#!/usr/bin/env python3
"""GOV-1017-COO-01：检查 tasks/*.md 并生成 tasks/TASKS.md，禁止手改总表。

默认检查并生成；--check 只检查；--self-test 离线自测。
PR 标题取 --pr-title 或 GITHUB_EVENT_PATH，仅告警；本脚本不调用 gh、不联网。
用时统一为非负分钟数（可写 12、12m、1h30m，约数加「约」）；未知字段写「待核」。
日报机器区保留片段的 Git 登记与返工变更时间，首次历史返工不冒充当日返工。
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
KINDS = ('设计', '编码', '测试', '取证', '复审', '确认页', '文档')
TASK_ID = re.compile(r'(NAM|CON|ARC|RUL|DAT|IMP|CI|OPS|GOV)-(\d{4})-([A-Z][A-Z0-9]{2})-(\d{2})')
UNKNOWN = '待核'
MACHINE = '<!-- TASKS-DATA v1 -->'


def parse_task(path, text):
    text = text.lstrip('\ufeff').replace('\r\n', '\n')
    front = re.match(r'\A---[ \t]*\n(.*?)\n---[ \t]*(?:\n|\Z)', text, re.S)
    if not front:
        raise ValueError(f'{path}：须以完整的 --- 键值头开始')
    fields = {}
    for line in front[1].splitlines():
        key, sep, value = line.partition(':')
        key, value = key.strip(), value.strip()
        if not sep or key not in FIELDS or key in fields:
            raise ValueError(f'{path}：键未知、重复或缺冒号：{key}')
        if value.startswith(('"', "'")):
            if len(value) < 2 or value[-1] != value[0]:
                raise ValueError(f'{path}：引号未闭合：{key}')
            value = value[1:-1]
        fields[key] = value or UNKNOWN
    if set(fields) != set(FIELDS):
        raise ValueError(f'{path}：缺少字段 {"、".join(k for k in FIELDS if k not in fields)}')
    return fields


def minutes(value):
    if value == UNKNOWN:
        return None
    value = value.removeprefix('约')
    if re.fullmatch(r'\d+(?:\.\d+)?', value):
        return float(value)
    match = re.fullmatch(r'(?:(\d+(?:\.\d+)?)h)?(?:(\d+(?:\.\d+)?)m)?', value)
    if not match or not any(match.groups()):
        raise ValueError('用时须为非负分钟数、12m、1h30m 或待核')
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
        if task['bc'] not in bcs | {'跨域'}:
            errors.append(f'{tag}：bc 须取 glossary/bcs.yaml 的 id 或跨域：{task["bc"]}')
        if task['种类'] not in KINDS:
            errors.append(f'{tag}：种类须取 {"/".join(KINDS)}')
        if not re.fullmatch(r'\[[^\[\]·]+·[^\[\]]+\]\s+\S.*', task['任务名']):
            errors.append(f'{tag}：任务名须为 [主线·bc] 动作 + 对象')
        if task['会话'] != UNKNOWN and not re.fullmatch(r'one-[a-z0-9]+(?:-[a-z0-9]+)*', task['会话']):
            errors.append(f'{tag}：会话须为 one-* 或待核')
        for key, parse in (('返工轮次', rounds), ('用时', minutes)):
            try:
                parse(task[key])
            except ValueError as error:
                errors.append(f'{tag}：{error}')
    return errors


def collect(base):
    folder = base / 'tasks'
    paths = sorted(folder.glob('*.md'))
    paths = [p for p in paths if p.name != 'TASKS.md' and not p.name.startswith('_')]
    return [(p, parse_task(p, p.read_text(encoding='utf-8'))) for p in paths]


def git(root, *args):
    result = subprocess.run(['git', '-C', str(root), *args], capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f'任务登记 Git 证据读取失败：git {args[0]}：{result.stderr.strip()}')
    return result.stdout


def history(root, path):
    rel = path.relative_to(root).as_posix()
    revisions = git(root, 'log', '--first-parent', '--reverse', '--format=%H%x1f%cI', 'HEAD', '--', rel)
    events, previous = [], None
    for line in revisions.splitlines():
        oid, time = line.split('\x1f')
        task = parse_task(path, git(root, 'show', f'{oid}:{rel}'))
        current = rounds(task['返工轮次'])
        delta = current - previous if current is not None and previous is not None and current >= previous else None
        if not events and current == 0:
            delta = 0
        events.append(dict(时间=time, 会话=task['会话'], 执行者与模型=task['执行者与模型'], 新增=not events, 返工增量=delta))
        previous = current
    return events


def payload(tasks, root):
    data, digests = [], {}
    for path, task in tasks:
        pending = bool(git(root, 'status', '--porcelain', '--', path.relative_to(root).as_posix()).strip())
        data.append(dict(task, 记录=history(root, path), 未提交=pending))
        digests[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    return dict(version=1, 片段摘要=digests, tasks=data)


def metric(tasks, key, parse):
    values = [parse(task[key]) for task in tasks]
    approximate = '约' if any(task[key].startswith('约') for task in tasks) else ''
    return f'{approximate}{sum(v for v in values if v is not None):g}（待核 {values.count(None)} 张）'


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
    out += ['## 卡片明细\n', '| ' + ' | '.join(FIELDS) + ' |', '|' + '|'.join('---' for _ in FIELDS) + '|']
    for path, task in tasks:
        cells = [str(task[k]).replace('|', '／') for k in FIELDS]
        cells[0] = f'[{cells[0]}]({path.name})'
        out.append('| ' + ' | '.join(cells) + ' |')
    out += ['', '## 日报机器数据\n', '日报读下方 JSON；按片段首次提交时刻计新登记卡，返工按后续累计值之差计。首次登记的历史返工及未知基数无法分配到日期。未提交片段无时间证据，不编造派工时刻。\n', MACHINE, '```json', json.dumps(data, ensure_ascii=False, indent=2), '```', '']
    return '\n'.join(out)


def pr_warning(title):
    if not any(TASK_ID.fullmatch(token) for token in re.findall(r'[A-Z][A-Z0-9]*-\d{4}-[A-Z0-9]+-\d+', title)):
        return '警告：PR 标题未带合规任务号，请补 <主线码>-<MMDD>-<会话码>-<两位序号>（当前不失败）'
    return None


def main(argv):
    import os
    title, check = None, False
    while argv:
        arg = argv.pop(0)
        if arg == '--check':
            check = True
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
    tasks = collect(BASE)
    errors = validate(tasks, bcs)
    if errors:
        raise ValueError('\n'.join(errors))
    if not check:
        root = Path(git(BASE, 'rev-parse', '--show-toplevel').strip())
        (BASE / 'tasks/TASKS.md').write_text(render(tasks, payload(tasks, root)), encoding='utf-8')
    print(f'check_tasks：{len(tasks)} 张片段通过' + ('，已生成 tasks/TASKS.md' if not check else ''))


def self_test():
    task = dict(zip(FIELDS, ('NAM-1017-GLO-03', '[命名·跨域] 登记名词', 'NAM', '跨域', '文档', 'one-glossary', 'Codex gpt-6.1-sol xhigh', '进行中', '待核', '待核', '2', '1h30m', 'N5')))
    path = Path(task['任务号'] + '.md')
    text = '---\n' + '\n'.join(f'{k}: {v}' for k, v in task.items()) + '\n---\n正文\n'
    assert parse_task(path, text) == task
    assert parse_task(path, text.replace(task['任务名'], '"' + task['任务名'] + '"')) == task
    assert not validate([(path, task)], set())
    for key, value, message in (('任务号', 'N5', '任务号须为'), ('任务号', 'NAM-1332-GLO-03', '有效月日'), ('主线', 'CON', '主线码一致'), ('bc', 'missing', 'bc 须取'), ('种类', '其他', '种类须取'), ('任务名', '登记名词', '任务名须为'), ('返工轮次', '-1', '非负整数'), ('用时', 'nan', '用时须为')):
        assert any(message in e for e in validate([(path, dict(task, **{key: value}))], set())), (key, value)
    assert not validate([(path, dict(task, bc='kernel'))], {'kernel'})
    assert any('重复' in e for e in validate([(path, task), (path, task)], set()))
    assert any('片段须放' in e for e in validate([(Path('wrong.md'), task)], set()))
    for bad in (text.replace('种类: 文档\n', ''), text.replace('种类: 文档', '种类: 文档\n种类: 文档'), text.replace('---\n正文', '正文')):
        try:
            parse_task(path, bad)
        except ValueError:
            continue
        raise AssertionError('无效片段未拦下')
    assert minutes('90') == minutes('90m') == minutes('1h30m') == 90
    assert minutes('约40m') == 40
    assert minutes(UNKNOWN) is None and rounds(UNKNOWN) is None
    assert pr_warning('feat: 不带编号') and not pr_warning('feat: NAM-1017-GLO-03 登记名词')
    data = dict(version=1, tasks=[task], 片段摘要={})
    output = render([(path, task)], data)
    assert all(f'按{k}汇总' in output for k in ('主线', 'bc', '会话', '状态'))
    assert '90（待核 0 张）' in output and json.loads(output.split('```json\n')[1].split('\n```')[0]) == data
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
        assert history(base, fragment)[0]['新增'] and history(base, fragment)[0]['返工增量'] == 0
        fragment.write_text(text.replace('返工轮次: 2', '返工轮次: 3'), encoding='utf-8')
        command('add', 'tasks/' + path.name)
        command('commit', '-qm', '更新轮次', time='2026-10-16T12:00:00+02:00')
        events = history(base, fragment)
        assert len(events) == 2 and events[1]['返工增量'] == 3 and not events[1]['新增']
        current = collect(base)
        result = payload(current, base)
        assert result['tasks'][0]['记录'] == events and not result['tasks'][0]['未提交']
        fragment.write_text(text, encoding='utf-8')
        assert payload(collect(base), base)['tasks'][0]['未提交'], '未提交变化不能当成已登记历史'
    print('check_tasks --self-test：通过（格式、bc、负向样例、告警、生成与 Git 轮次记录）')


if __name__ == '__main__':
    try:
        self_test() if sys.argv[1:] == ['--self-test'] else main(sys.argv[1:])
    except (ValueError, OSError, KeyError, ImportError) as error:
        sys.exit(f'check_tasks 失败：{error}')
