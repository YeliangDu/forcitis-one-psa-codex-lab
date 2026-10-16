#!/usr/bin/env python3
"""FORCITIS one · C-6 · 老系统只读导出的中转（运维机上运行，阿里云 Python SDK）。

老系统应用服务器上用 LegacyReadOnlyProbe 分页查询（READ COMMITTED，每页压缩后经云助手输出带回），在本进程内存中拼成文件，
再经云助手 SendFile 分块写到新 ECS 的 /data/forcitis-one/import/c6/<日期>/（root 0700，文件 root 0600），在 ECS 上合并并核对 SHA256。
本进程不写磁盘、不打印数据内容，只打印页号、行数与 SHA256。凭据取自 aliyun CLI 配置 forcitis-production 的临时凭据（ops_credentials：不足 15 分钟时等过期后自动换新）。
用法：c6_relay.py roles-calendar <日期 YYYYMMDD>
      c6_relay.py replay <日期 YYYYMMDD>
"""
import base64, gzip, hashlib, io, json, os, sys, time
from pathlib import Path

from alibabacloud_ecs20140526 import models as em

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ops_credentials import MIN_SECONDS, ecs_client, require_committed  # noqa: E402

REGION = 'cn-hangzhou'
LEGACY_SERVER = 'i-bp1b34hcl0g1cnkdtrb9'
ONE_SERVER = 'i-bp122xlpar8w2uylmal2'
SCRIPTS = Path(__file__).resolve().parent
CHUNK = 24000

require_committed(__file__, sys.argv[0])


def run(instance, script, timeout=300):
    ecs = ecs_client(REGION, max(MIN_SECONDS, timeout + 120))
    invoke = ecs.run_command(em.RunCommandRequest(region_id=REGION, instance_id=[instance], type='RunShellScript',
                                                  command_content=script, timeout=timeout, keep_command=False,
                                                  name='one-ops-c6')).body.invoke_id
    for _ in range(timeout // 2 + 10):
        time.sleep(2)
        body = ecs.describe_invocation_results(em.DescribeInvocationResultsRequest(region_id=REGION, invoke_id=invoke)).body
        result = body.invocation.invocation_results.invocation_result[0]
        if result.invocation_status in ('Success', 'Failed', 'Timeout', 'Error', 'Aborted'):
            output = base64.b64decode(result.output or '').decode()
            if result.invocation_status != 'Success':
                cause = [l for l in output.splitlines() if 'Exception' in l or 'Error' in l][:3]
                raise SystemExit(f'{invoke} {result.invocation_status}: ' + (' | '.join(cause) or output[-500:]))
            return invoke, output
    raise SystemExit(f'{invoke} did not finish')


def legacy_query(sql):
    """Returns (invoke id, list of result sets); each result set is (header, rows)."""
    packed = lambda data: base64.b64encode(gzip.compress(data, 9)).decode()
    script = (
        "set -eu; w=$(mktemp -d /tmp/one-c6.XXXXXX); r=$(mktemp /tmp/one-c6-run.XXXXXX); trap 'rm -rf \"$w\" \"$r\"' EXIT\n"
        f"echo '{packed((SCRIPTS / 'LegacyReadOnlyProbe.java').read_bytes())}' | base64 -d | gunzip > $w/LegacyReadOnlyProbe.java\n"
        f"echo '{packed(sql.encode())}' | base64 -d | gunzip > $w/q.sql\n"
        f"echo '{packed((SCRIPTS / 'legacy_probe_run.sh').read_bytes())}' | base64 -d | gunzip > $r\n"
        "bash $r $w --gzip-base64\n")
    if len(script) > 16000:
        raise SystemExit('command too large for cloud assistant')
    invoke, output = run(LEGACY_SERVER, script)
    if len(output) >= 24000:
        raise SystemExit(f'{invoke}: output reached the cloud assistant limit and was truncated; make pages smaller: {sql[:80]}')
    text = gzip.decompress(base64.b64decode(output.strip().splitlines()[-1])).decode()
    sets, current = [], None
    for line in text.splitlines():
        if line.startswith('## '):
            current = None
        elif line.startswith('# rows='):
            if 'truncated' in line:
                raise SystemExit('page hit max rows; make pages smaller')
        elif current is None:
            current = (line.split('\t'), [])
            sets.append(current)
        else:
            current[1].append(line.split('\t'))
    return invoke, sets


def paged(sql_template, keys, page_size, label):
    """Keyset paging: sql_template has {top} and {after} placeholders; keys are the ORDER BY columns (numeric)."""
    rows, header, last, invokes = [], None, None, []
    while True:
        after = '1=1' if last is None else _after(keys, last)
        invoke, sets = legacy_query(sql_template.format(top=page_size, after=after))
        invokes.append(invoke)
        header, page = sets[0]
        rows += page
        print(f'{label}: page {len(invokes)} rows {len(page)} ({invoke})')
        if len(page) < page_size:
            return header, rows, invokes
        last = [page[-1][header.index(k.split('.')[-1])] for k in keys]


def _after(keys, values):
    clauses = []
    for i in range(len(keys)):
        eq = [f'{keys[j]} = {int(values[j])}' for j in range(i)]
        clauses.append('(' + ' AND '.join(eq + [f'{keys[i]} > {int(values[i])}']) + ')')
    return '(' + ' OR '.join(clauses) + ')'


def deliver(date, name, data: bytes):
    """Writes data to the new server through SendFile chunks and checks SHA256 there."""
    digest = hashlib.sha256(data).hexdigest()
    target = f'/data/forcitis-one/import/c6/{date}'
    parts_dir = f'{target}/.parts-{name}'
    run(ONE_SERVER, f'set -eu; install -d -o root -g root -m 0700 /data/forcitis-one/import /data/forcitis-one/import/c6 {target} {parts_dir}; rm -f {parts_dir}/*')
    encoded = base64.b64encode(gzip.compress(data, 9)).decode()
    chunks = [encoded[i:i + CHUNK] for i in range(0, len(encoded), CHUNK)]
    for index, chunk in enumerate(chunks):
        invoke = ecs_client(REGION).send_file(em.SendFileRequest(region_id=REGION, instance_id=[ONE_SERVER], name=f'{index:05d}',
                                                  target_dir=parts_dir, content=chunk, content_type='PlainText',
                                                  file_owner='root', file_group='root', file_mode='0600', overwrite=True,
                                                  description='one-ops c6 part')).body.invoke_id
        _wait_send(invoke)
    _, out = run(ONE_SERVER, f'set -eu; umask 077; cat {parts_dir}/* | tr -d "\\n" | base64 -d | gunzip > {target}/{name}; '
                             f'chmod 0600 {target}/{name}; rm -rf {parts_dir}; sha256sum {target}/{name} | cut -c1-64')
    if out.strip() != digest:
        raise SystemExit(f'{name}: checksum mismatch on server')
    print(f'{name}: {len(data)} bytes sha256 {digest} delivered in {len(chunks)} parts')
    return digest


def _wait_send(invoke):
    ecs = ecs_client(REGION)
    for _ in range(60):
        time.sleep(1)
        inv = ecs.describe_send_file_results(em.DescribeSendFileResultsRequest(region_id=REGION, invoke_id=invoke)).body.invocations.invocation[0]
        if inv.invocation_status == 'Success':
            return
        if inv.invocation_status in ('Failed', 'PartialFailed'):
            raise SystemExit(f'send file {invoke} failed')
    raise SystemExit(f'send file {invoke} did not finish')


def roles_and_calendar(date):
    manifest = {'date': date, 'channel': 'A (legacy datasource, read-only connection, READ COMMITTED)', 'files': {}}

    roles_h, roles, inv1 = paged(
        "SELECT TOP {top} r.id, r.name, r.code, r.data_scope, r.data_scope_dept_ids, r.status FROM dbo.system_role r "
        "WHERE r.deleted = 0 AND {after} ORDER BY r.id", ['r.id'], 1000, 'roles')
    hold_h, holdings, inv2 = paged(
        "SELECT TOP {top} ur.user_id, ur.role_id FROM dbo.system_user_role ur JOIN dbo.system_role r ON r.id = ur.role_id AND r.deleted = 0 "
        "WHERE ur.deleted = 0 AND {after} ORDER BY ur.role_id, ur.user_id", ['ur.role_id', 'ur.user_id'], 700, 'holdings')
    grant_h, grants, inv3 = paged(
        "SELECT TOP {top} rm.role_id, rm.menu_id, m.type, m.permission, m.component FROM dbo.system_role_menu rm "
        "JOIN dbo.system_menu m ON m.id = rm.menu_id AND m.deleted = 0 JOIN dbo.system_role r ON r.id = rm.role_id AND r.deleted = 0 "
        "WHERE rm.deleted = 0 AND {after} ORDER BY rm.role_id, rm.menu_id", ['rm.role_id', 'rm.menu_id'], 500, 'grants')
    _, counts = legacy_query(
        "SELECT (SELECT COUNT(*) FROM dbo.system_role WHERE deleted = 0) AS roles, "
        "(SELECT COUNT(*) FROM dbo.system_user_role WHERE deleted = 0) AS holdings, "
        "(SELECT COUNT(*) FROM dbo.system_role_menu WHERE deleted = 0) AS grants")
    source = dict(zip(counts[0][0], map(int, counts[0][1][0])))
    as_dicts = lambda h, rows: [dict(zip(h, (None if v == 'NULL' else v for v in r))) for r in rows]
    roles_doc = {'exportedAt': date, 'source': 'legacy system, read-only', 'roles': as_dicts(roles_h, roles),
                 'holdings': as_dicts(hold_h, holdings), 'grants': as_dicts(grant_h, grants)}
    data = json.dumps(roles_doc, ensure_ascii=False, indent=1).encode()
    manifest['files']['legacy-roles.json'] = {
        'rows': {'roles': len(roles), 'holdings': len(holdings), 'grants': len(grants)},
        'sourceCounts': source, 'invokes': inv1 + inv2 + inv3, 'sha256': deliver(date, 'legacy-roles.json', data)}

    calendar_rows, cal_h, cal_inv = [], None, []
    for start, end in (('20160101', '20200101'), ('20200101', '20240101'), ('20240101', '20270101')):
        invoke, sets = legacy_query(
            "SELECT CAST(c.[datetime] AS date) AS calendar_date, c.workday, CASE "
            "WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 AND c.workday = 0 THEN 'HOLIDAY' "
            "WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 >= 5 AND c.workday = 1 THEN 'MAKE_UP_WORKDAY' END AS day_kind "
            f"FROM dbo.psa_calendar c WHERE c.[datetime] >= '{start}' AND c.[datetime] < '{end}' ORDER BY c.[datetime]")
        cal_h, page = sets[0]
        calendar_rows += page
        cal_inv.append(invoke)
        print(f'calendar {start[:4]}-{int(end[:4]) - 1}: rows {len(page)} ({invoke})')
    dates = [r[0] for r in calendar_rows]
    if len(dates) != len(set(dates)):
        raise SystemExit('calendar has duplicate dates')
    csv = io.StringIO()
    csv.write(','.join(cal_h) + '\n')
    for r in calendar_rows:
        csv.write(','.join('' if v == 'NULL' else v for v in r) + '\n')
    manifest['files']['legacy-calendar.csv'] = {
        'rows': len(calendar_rows), 'invokes': cal_inv,
        'byYear': {y: sum(1 for d in dates if d.startswith(y)) for y in sorted({d[:4] for d in dates})},
        'sha256': deliver(date, 'legacy-calendar.csv', csv.getvalue().encode())}

    checks = {'roles': (len(roles), source['roles']), 'holdings': (len(holdings), source['holdings']),
              'grants': (len(grants), source['grants']), 'calendar': (len(calendar_rows), 4018)}
    manifest['reconciliation'] = {k: {'exported': a, 'expected': b, 'ok': a == b} for k, (a, b) in checks.items()}
    return manifest


TIMESHEET_DEFINITION = "TIMESHEET:15:550b8de2-b652-11f1-8b75-06ef8154adb3"
SAMPLE_PER_MONTH = 250
# Legacy approval node keys of TIMESHEET v15 (bpm_task_assign_rule, 2026-10-15); the initiator node Activity_19wn7op is not replayed (M3).
NODES = {
    'Activity_0g7oxfy': ('projectManagerReview', {'type': 'FORM_FIELD_USERS', 'field': 'projectManagerId'}),
    # 导出文件保持旧格式（regionalHead* 即现在的部门负责人 orgUnitHead*）：回放命令的 LegacyVocabulary 把旧名映射成新名，已导出的 20261015 文件因此不用重做
    'Activity_01vbv92': ('regionalHeadReview', {'type': 'FORM_FIELD_USERS', 'field': 'regionalHeadId'}),
    'Activity_0hwqdkm': ('supervisorReview', {'type': 'INITIATOR_SUPERVISOR', 'level': '1'}),
}
FORM_FIELDS = {'manager_id': 'projectManagerId', 'area_leader': 'regionalHeadId'}
# RULE-254: the legacy rule service returns this hard-coded user when no form field yields an approver (legacy BpmTaskAssignRuleServiceImpl).
LEGACY_FALLBACK_USER_IDS = [-3924403404092652719]
# Replay-only stand-in for the process administrator (A-4 not appointed); not a real user and never used outside replay.
REPLAY_PROCESS_ADMINISTRATOR = -1


def java_string_list(raw: bytes):
    """Decodes a Java-serialized java.util.ArrayList<String>; fails on anything else."""
    if raw[:4] != b'\xac\xed\x00\x05' or b'java.util.ArrayList' not in raw:
        raise ValueError('not a serialized ArrayList')
    pos = raw.index(b'\x77\x04') + 6
    size = int.from_bytes(raw[pos - 10:pos - 6], 'big') if raw[pos - 10 - 2:pos - 10] == b'\x78\x70' else None
    handles, values = {}, []
    next_handle = 0x7e0002
    while raw[pos] != 0x78:
        tag = raw[pos]
        if tag == 0x74:
            length = int.from_bytes(raw[pos + 1:pos + 3], 'big')
            value = raw[pos + 3:pos + 3 + length].decode('utf-8')
            handles[next_handle] = value
            next_handle += 1
            values.append(value)
            pos += 3 + length
        elif tag == 0x71:
            values.append(handles[int.from_bytes(raw[pos + 1:pos + 5], 'big')])
            pos += 5
        elif tag == 0x70:
            pos += 1
        else:
            raise ValueError(f'unexpected element tag {tag:#x}')
    if size is not None and size != len(values):
        raise ValueError('element count does not match list size')
    return values


def java_integer_list(raw: bytes):
    """Decodes a Java-serialized java.util.ArrayList<Integer>; fails on anything else."""
    if raw[:4] != b'\xac\xed\x00\x05' or b'java.util.ArrayList' not in raw:
        raise ValueError('not a serialized ArrayList')
    start = raw.index(b'\x77\x04') + 6
    size = int.from_bytes(raw[start - 10:start - 6], 'big')
    values, pos = [], start
    if raw[pos] == 0x78:
        return values
    if raw[pos:pos + 2] != b'\x73\x72' or b'java.lang.Integer' not in raw[pos:pos + 40]:
        raise ValueError('first element is not an Integer')
    pos = raw.index(b'\x78\x70', raw.index(b'java.lang.Number', pos)) + 2
    values.append(int.from_bytes(raw[pos:pos + 4], 'big', signed=True))
    pos += 4
    while raw[pos:pos + 2] == b'\x73\x71':
        values.append(int.from_bytes(raw[pos + 6:pos + 10], 'big', signed=True))
        pos += 10
    if raw[pos] != 0x78 or len(values) != size:
        raise ValueError('unexpected list layout')
    return values


def _in_list(ids):
    return ','.join("'" + i.replace("'", "") + "'" for i in ids)


def replay(date):
    manifest = {'date': date, 'channel': 'A (legacy datasource, read-only connection, READ COMMITTED)', 'definition': TIMESHEET_DEFINITION}
    finished, _, inv = [], None, []
    last_end = None
    while True:
        cond = '' if last_end is None else f" AND (h.END_TIME_ > '{last_end[0]}' OR (h.END_TIME_ = '{last_end[0]}' AND h.ID_ > '{last_end[1]}'))"
        invoke, sets = legacy_query(
            "SELECT TOP 300 h.ID_ AS instance_id, CONVERT(varchar(23), h.END_TIME_, 121) AS ended FROM ACT_HI_PROCINST h "
            "JOIN bpm_process_instance_ext e ON e.process_instance_id = h.ID_ COLLATE DATABASE_DEFAULT AND e.deleted = '0' "
            f"WHERE h.PROC_DEF_ID_ = '{TIMESHEET_DEFINITION}' AND h.END_TIME_ IS NOT NULL AND e.status = 2 AND e.result = 2 "
            f"AND h.END_TIME_ >= DATEADD(day, -90, GETDATE()){cond} ORDER BY h.END_TIME_, h.ID_")
        inv.append(invoke)
        page = sets[0][1]
        finished += page
        print(f'finished instances: page {len(inv)} rows {len(page)} ({invoke})')
        if len(page) < 300:
            break
        last_end = (page[-1][1], page[-1][0])
    by_month = {}
    for instance_id, ended in finished:
        by_month.setdefault(ended[:7], []).append(instance_id)
    sample = []
    for month, ids in sorted(by_month.items()):
        step = max(1, len(ids) / SAMPLE_PER_MONTH)
        picked = [ids[int(i * step)] for i in range(min(SAMPLE_PER_MONTH, len(ids)))]
        sample += picked
        print(f'{month}: finished {len(ids)} sampled {len(picked)}')
    manifest['population'] = {m: len(v) for m, v in sorted(by_month.items())}
    manifest['sampleRule'] = 'v15 instances approved and finished in the last 90 days; per month up to 250, evenly spaced by end time; unfinished excluded (no complete approver sequence)'

    instances, invokes, missing_types, undecodable = {}, [], 0, 0
    for start in range(0, len(sample), 5):
        batch = sample[start:start + 5]
        ids = _in_list(batch)
        invoke, sets = legacy_query(
            f"SELECT h.ID_ AS instance_id, h.START_USER_ID_ AS initiator FROM ACT_HI_PROCINST h WHERE h.ID_ IN ({ids});"
            "SELECT v.PROC_INST_ID_ AS instance_id, b.BYTES_ AS bytes FROM ACT_HI_VARINST v "
            f"JOIN ACT_GE_BYTEARRAY b ON b.ID_ = v.BYTEARRAY_ID_ WHERE v.PROC_INST_ID_ IN ({ids}) AND v.NAME_ = 'manHourType';"
            "SELECT v.PROC_INST_ID_ AS instance_id, v.NAME_ AS name, b.BYTES_ AS bytes FROM ACT_HI_VARINST v "
            f"JOIN ACT_GE_BYTEARRAY b ON b.ID_ = v.BYTEARRAY_ID_ WHERE v.PROC_INST_ID_ IN ({ids}) AND v.NAME_ IN ('manager_id', 'area_leader');"
            "SELECT t.process_instance_id AS instance_id, t.task_def_key, t.assignee_user_id FROM bpm_task_ext t "
            f"WHERE t.process_instance_id IN ({ids}) AND t.deleted = '0' AND t.result IN (2, 3) "
            "AND t.task_def_key IN ('Activity_0g7oxfy', 'Activity_01vbv92', 'Activity_0hwqdkm')")
        invokes.append(invoke)
        (_, starts), (_, types), (_, variables), (_, tasks) = sets
        for instance_id, initiator in starts:
            instances[instance_id] = {'instanceId': instance_id, 'initiatorId': int(initiator), 'legacyHourTypes': [],
                                      'formFields': {}, 'variables': {}, 'nodes': {}}
        for instance_id, raw in types:
            try:
                instances[instance_id]['legacyHourTypes'] += java_integer_list(bytes.fromhex(raw))
            except ValueError:
                undecodable += 1
        for instance_id, name, raw in variables:
            try:
                instances[instance_id]['formFields'][FORM_FIELDS[name]] = java_string_list(bytes.fromhex(raw))
            except ValueError:
                undecodable += 1
        for instance_id, key, assignee in tasks:
            node_key, rule = NODES[key]
            node = instances[instance_id]['nodes'].setdefault(node_key, {'nodeKey': node_key, 'rule': rule, 'actualApprovers': []})
            if assignee != 'NULL' and int(assignee) not in node['actualApprovers']:
                node['actualApprovers'].append(int(assignee))
        print(f'instances {start + len(batch)}/{len(sample)} ({invoke})')
    for instance in instances.values():
        instance['legacyHourTypes'] = sorted(set(instance['legacyHourTypes']))
        missing_types += not instance['legacyHourTypes']
        instance['nodes'] = list(instance['nodes'].values())

    emp_h, employees, inv_e = paged(
        "SELECT TOP {top} u.id, u.dept_id, u.leader_user_id, u.status, u.deleted FROM dbo.system_users u WHERE {after} ORDER BY u.id",
        ['u.id'], 400, 'employees')
    org_h, org_units, inv_o = paged(
        "SELECT TOP {top} d.id, d.parent_id, d.leader_user_id, d.ar_user_id, d.status, d.deleted FROM dbo.system_dept d WHERE {after} ORDER BY d.id",
        ['d.id'], 400, 'org units')
    num = lambda v: None if v in ('NULL', '', '0') else int(v)
    snapshot = {
        'employees': [{'userId': int(r[0]), 'orgUnitId': num(r[1]), 'supervisorUserId': num(r[2]),
                       'employed': r[3] == '0' and r[4] in ('0', 'false')} for r in employees],
        'orgUnits': [{'orgUnitId': int(r[0]), 'parentOrgUnitId': num(r[1]), 'headUserId': num(r[2]),
                      'regionalReceivablesManagerId': num(r[3]), 'active': r[4] == '0' and r[5] in ('0', 'false')} for r in org_units],
        'roleHolders': {},
    }
    if any(e['userId'] == REPLAY_PROCESS_ADMINISTRATOR for e in snapshot['employees']):
        raise SystemExit('replay process administrator stand-in collides with a real user id')
    export = {'formatVersion': 1, 'processKey': 'TIMESHEET', 'processAdministratorUserId': REPLAY_PROCESS_ADMINISTRATOR,
              'legacyFallbackUserIds': LEGACY_FALLBACK_USER_IDS, 'snapshot': snapshot,
              'instances': [instances[i] for i in sample if i in instances]}
    data = json.dumps(export, ensure_ascii=False).encode()
    manifest['files'] = {'timesheet-export.json': {
        'instances': len(export['instances']), 'employees': len(employees), 'orgUnits': len(org_units),
        'instancesWithoutHourTypes': missing_types, 'undecodableFormFields': undecodable,
        'nodes': sum(len(i['nodes']) for i in export['instances']),
        'invokes': inv + invokes + inv_e + inv_o, 'sha256': deliver(date, 'timesheet-export.json', data)}}
    manifest['reconciliation'] = {'sample': {'exported': len(export['instances']), 'expected': len(sample), 'ok': len(export['instances']) == len(sample)}}
    manifest['limitations'] = [
        'snapshot is the organisation as of the export date, not as of each instance (legacy tables keep no history)',
        'legacyHourTypes come from the process variable manHourType that the legacy gateways evaluated',
        'processAdministratorUserId is the replay-only stand-in -1 (A-4 not appointed), so the fallback-to-administrator path can be compared; not a real user',
        'legacyFallbackUserIds holds the hard-coded fallback user id of RULE-254 taken from the legacy code (id only)',
    ]
    return manifest


if __name__ == '__main__':
    if len(sys.argv) != 3 or sys.argv[1] not in ('roles-calendar', 'replay'):
        raise SystemExit(__doc__)
    kind, date = sys.argv[1], sys.argv[2]
    manifest = roles_and_calendar(date) if kind == 'roles-calendar' else replay(date)
    deliver(date, f'manifest-{kind}.json', json.dumps(manifest, ensure_ascii=False, indent=1).encode())
    print(json.dumps({k: v for k, v in manifest.items() if k != 'files'}, ensure_ascii=False))
    for name, entry in manifest['files'].items():
        print(name, {k: v for k, v in entry.items() if k not in ('invokes', 'sha256')}, entry['sha256'])
