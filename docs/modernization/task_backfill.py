#!/usr/bin/env python3
"""补登建议：按任务号在 PR 标题、提交说明、分支名里的引用，推断各任务片段的状态，只写建议、标「待确认」，不改片段。

规则（用户 2026-10-21，intent 2026-10-21-213907）：任务号出现在已合入 origin/main 的提交或合并说明里，或片段写的分支已合入 → 完成；
有提交、分支或未关闭的 PR 但没合入 → 进行中；没有任何痕迹 → 已派。片段当前是阻塞或作废的，不覆盖（人工标注）。
只算改了 tasks/ 以外文件的提交（登记片段的提交本身不算工作）；合并提交的说明（GitHub 的合并说明含 PR 标题）算合入证据。
输出按会话分组的清单，交 one-coordinator 分发给各负责会话确认；本脚本不联网：PR 标题来自 --prs-json（`gh pr list --state all --limit 500 --json number,title,headRefName,state,mergedAt` 的输出），--gh 在你明确要求时代为运行这条 gh 命令。
用法：task_backfill.py [--ref origin/main] [--prs-json 文件 | --gh] [--session one-xxx] [--out 文件]  |  task_backfill.py --self-test
"""
import collections
import json
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_tasks  # noqa: E402

BASE = Path(__file__).resolve().parent
KEEP = ('阻塞', '作废')
NO_TRACE = '没有找到任务号的任何痕迹'
NOT_WORK = ':(exclude)docs/modernization/tasks'


def git(base, *args):
    result = subprocess.run(['git', '-C', str(base), '-c', 'core.quotepath=off', *args], capture_output=True, text=True)
    return result.stdout if result.returncode == 0 else ''


def evidence(base, task, ref, prs, branches, merged):
    """(suggested status, evidence lines) for one fragment."""
    ident = task['任务号']
    proof, merged_hit, open_hit = [], False, False
    in_main = git(base, 'log', ref, '--fixed-strings', '--grep', ident, '--format=%h %s', '--', '.', NOT_WORK)
    if in_main.strip():
        merged_hit = True
        proof.append(f'已在 {ref} 的提交：' + in_main.splitlines()[0][:100])
    merges = git(base, 'log', ref, '--merges', '--fixed-strings', '--grep', ident, '--format=%h %s')
    if merges.strip():
        merged_hit = True
        proof.append('合并说明带任务号：' + merges.splitlines()[0][:100])
    elsewhere = git(base, 'log', '--all', '--fixed-strings', '--grep', ident, '--format=%h %s', '--', '.', NOT_WORK)
    if elsewhere.strip() and not merged_hit:
        open_hit = True
        proof.append(f'有提交但未合入 {ref}：' + elsewhere.splitlines()[0][:100])
    for name in sorted(branches):
        if ident.lower() in name.lower():
            proof.append(f'分支名带任务号：{name}')
            if name in merged:
                merged_hit = True
            else:
                open_hit = True
    branch = task['分支']
    if branch and branch != check_tasks.UNKNOWN:
        if branch in merged:
            merged_hit = True
            proof.append(f'片段的分支 {branch} 已合入')
        elif branch in branches:
            open_hit = True
            proof.append(f'片段的分支 {branch} 在远端，未合入')
    for pr in prs:
        if ident in pr.get('title', ''):
            state = 'MERGED' if pr.get('mergedAt') or pr.get('state') == 'MERGED' else pr.get('state', '')
            proof.append(f'PR #{pr.get("number")}（{state}）{pr.get("title", "")[:60]}')
            if state == 'MERGED':
                merged_hit = True
            elif state == 'OPEN':
                open_hit = True
    if task['状态'] in KEEP:
        return task['状态'], proof + ['人工标注的阻塞或作废，不覆盖']
    if merged_hit:
        return '完成', proof
    if open_hit:
        return '进行中', proof
    return '已派', proof or [NO_TRACE]


def suggestions(base, tasks, ref='origin/main', prs=(), only_session=None):
    """[(task, current status, suggested status, evidence lines)] for fragments whose current status differs from the suggestion."""
    branches = {b.removeprefix('origin/') for b in git(base, 'branch', '-r', '--format=%(refname:short)').split()}
    merged = check_tasks.merged_branches(base, ref) or set()
    rows, unverifiable = [], []
    for _, task in tasks:
        if only_session and task['会话'] != only_session:
            continue
        status, proof = evidence(base, task, ref, prs, branches, merged)
        current = check_tasks.LEGACY_STATUS.get(task['状态'], task['状态'])
        if current in ('完成', '作废') and proof == [NO_TRACE]:   # PRs often name a PR number, not the card: a finished card without a trace is not sent back to 已派, only counted
            unverifiable.append(task)
        elif current != status:
            rows.append((task, task['状态'], status, proof))
    return rows, unverifiable


def render(result):
    rows, unverifiable = result
    out = ['# 任务片段状态补登建议（待确认）', '', '由 `docs/modernization/task_backfill.py` 按任务号在提交、分支、PR 里的引用推断，只是建议，**不改片段**；各负责会话核对后自己改片段的「状态」与「最后更新」。', '']
    if unverifiable:
        out += [f'另有 {len(unverifiable)} 张状态已是完成或作废、但在提交、分支、PR 里找不到任务号的痕迹（多半 PR 只写了 PR 号），无法核实，不在下面的清单里：' + '、'.join(t['任务号'] for t in unverifiable[:40]) + ('…' if len(unverifiable) > 40 else ''), '']
    if not rows:
        return '\n'.join(out + ['没有需要改的片段。', ''])
    out += [f'共 {len(rows)} 张片段的状态与证据对不上。', '']
    by_session = collections.defaultdict(list)
    for row in rows:
        by_session[row[0]['会话']].append(row)
    for session in sorted(by_session):
        out += [f'## {session}（{len(by_session[session])} 张）', '', '| 任务号 | 当前状态 | 建议状态（待确认） | 证据 |', '|---|---|---|---|']
        for task, current, suggested, proof in sorted(by_session[session], key=lambda r: r[0]['任务号']):
            out.append(f'| {task["任务号"]} | {current} | {suggested} | {"；".join(proof).replace("|", "／")} |')
        out.append('')
    return '\n'.join(out)


def main(argv):
    ref, prs_file, use_gh, session, out_file = 'origin/main', None, False, None, None
    while argv:
        arg = argv.pop(0)
        if arg == '--ref' and argv:
            ref = argv.pop(0)
        elif arg == '--prs-json' and argv:
            prs_file = argv.pop(0)
        elif arg == '--gh':
            use_gh = True
        elif arg == '--session' and argv:
            session = argv.pop(0)
        elif arg == '--out' and argv:
            out_file = argv.pop(0)
        else:
            raise ValueError(f'未知参数：{arg}')
    prs = []
    if prs_file:
        prs = json.loads(Path(prs_file).read_text(encoding='utf-8'))
    elif use_gh:
        result = subprocess.run(['gh', 'pr', 'list', '--state', 'all', '--limit', '500', '--json', 'number,title,headRefName,state,mergedAt'], capture_output=True, text=True)
        if result.returncode:
            raise ValueError('gh pr list 失败：' + result.stderr.strip()[:200])
        prs = json.loads(result.stdout)
    text = render(suggestions(BASE, check_tasks.collect(BASE), ref, prs, session))
    if out_file:
        Path(out_file).write_text(text, encoding='utf-8')
    else:
        print(text)


def self_test():
    def fragment(ident, session='one-quality', status='进行中', branch='待核'):
        fields = dict(任务号=ident, 任务名='[流程治理·跨域] 做事', 主线=ident.split('-')[0], bc='跨域', 种类='文档', 会话=session, 执行者与模型='sonnet', 状态=status, 分支=branch, 提交='待核', 返工轮次='0', 用时='待核', 旧编号='待核')
        return '---\n' + '\n'.join(f'{k}: {v}' for k, v in fields.items()) + '\n---\n正文\n'

    with tempfile.TemporaryDirectory() as raw:
        base = Path(raw) / 'work'
        (base / 'docs/modernization/tasks').mkdir(parents=True)

        def run(*args):
            result = subprocess.run(['git', '-C', str(base), '-c', 'commit.gpgsign=false', '-c', 'user.name=t', '-c', 'user.email=t@t', *args], capture_output=True, text=True)
            assert result.returncode == 0, (args, result.stderr)
            return result.stdout

        def commit(message, name=None):
            if name:
                (base / name).write_text(name)
            run('add', '.')
            run('commit', '-q', '--allow-empty', '-m', message)

        run('init', '-q', '-b', 'main')
        cards = {'GOV-1021-QUA-01': dict(), 'GOV-1021-QUA-02': dict(status='已派'), 'GOV-1021-QUA-03': dict(status='已派'), 'GOV-1021-QUA-04': dict(status='阻塞'),
                 'GOV-1021-QUA-05': dict(status='已合并'), 'GOV-1021-QUA-06': dict(status='已派', branch='claude/side'), 'GOV-1021-QUA-07': dict(status='已派'), 'GOV-1021-QUA-08': dict(status='完成'),
                 'GOV-1021-COO-01': dict(status='已派', session='one-coordinator')}
        folder = base / 'docs/modernization/tasks'
        for ident, kw in cards.items():
            (folder / f'{ident}.md').write_text(fragment(ident, **kw), encoding='utf-8')
        commit('登记 ' + ' '.join(cards))   # registering a card is no work
        commit('feat: GOV-1021-QUA-01 做了', 'work1.txt')
        run('checkout', '-q', '-b', 'wip')
        commit('feat: GOV-1021-QUA-02 做到一半', 'work2.txt')
        run('checkout', '-q', 'main')
        run('checkout', '-q', '-b', 'claude/side')
        commit('无任务号的提交', 'work3.txt')
        run('checkout', '-q', 'main')
        run('checkout', '-q', '-b', 'gov-1021-qua-07-branch')
        commit('空提交')
        run('checkout', '-q', 'main')
        commit('Merge pull request #9 from x/y\n\nGOV-1021-QUA-05 合并说明里的任务号', 'work4.txt')
        remote = Path(raw) / 'remote.git'
        subprocess.run(['git', 'init', '-q', '--bare', str(remote)], check=True)
        run('remote', 'add', 'origin', str(remote))
        run('push', '-q', 'origin', 'main', 'wip', 'claude/side', 'gov-1021-qua-07-branch')
        run('fetch', '-q', 'origin')

        tasks = check_tasks.collect(base / 'docs/modernization')
        prs = [{'number': 12, 'title': 'GOV-1021-COO-01 派工', 'state': 'MERGED', 'mergedAt': '2026-10-21T00:00:00Z'}]
        listed, unverifiable = suggestions(base, tasks, 'origin/main', prs)
        rows = {t['任务号']: (cur, new, proof) for t, cur, new, proof in listed}
        assert rows['GOV-1021-QUA-01'][1] == '完成', rows['GOV-1021-QUA-01']
        assert rows['GOV-1021-QUA-02'][1] == '进行中' and '未合入' in ' '.join(rows['GOV-1021-QUA-02'][2]), '有提交没合入'
        assert 'GOV-1021-QUA-03' not in rows, '已派且没有痕迹：与建议一致，不列'
        assert 'GOV-1021-QUA-04' not in rows, '阻塞不覆盖'
        assert 'GOV-1021-QUA-05' not in rows, '存量的已合并按完成算，合并说明里有任务号'
        assert rows['GOV-1021-QUA-06'][1] == '进行中' and '未合入' in ' '.join(rows['GOV-1021-QUA-06'][2]), '片段写的分支在远端未合入'
        assert rows['GOV-1021-QUA-07'][1] == '进行中' and '分支名带任务号' in ' '.join(rows['GOV-1021-QUA-07'][2]), '分支名带任务号'
        assert 'GOV-1021-QUA-08' not in rows and [t['任务号'] for t in unverifiable] == ['GOV-1021-QUA-08'], '状态写完成而没有任何痕迹：不降级，只计数'
        assert rows['GOV-1021-COO-01'][1] == '完成' and 'PR #12' in ' '.join(rows['GOV-1021-COO-01'][2]), 'PR 标题'
        assert {t['会话'] for t, *_ in suggestions(base, tasks, 'origin/main', prs, 'one-coordinator')[0]} == {'one-coordinator'}, '按会话过滤'
        text = render(suggestions(base, tasks, 'origin/main', []))
        assert '待确认' in text and '## one-quality' in text and '不改片段' in text and 'GOV-1021-QUA-08' in text.split('## ')[0], '无法核实的卡要点名'
        assert (folder / 'GOV-1021-QUA-01.md').read_text(encoding='utf-8') == fragment('GOV-1021-QUA-01'), '片段原样不动'
        assert '没有需要改的片段' in render(([], []))
    print('task_backfill --self-test：通过（合入、未合入、无痕迹、阻塞不覆盖、登记提交不算、分支名、PR 标题、按会话分组）')


if __name__ == '__main__':
    try:
        self_test() if sys.argv[1:] == ['--self-test'] else main(sys.argv[1:])
    except (ValueError, OSError, KeyError, ImportError) as error:
        sys.exit(f'task_backfill 失败：{error}')
