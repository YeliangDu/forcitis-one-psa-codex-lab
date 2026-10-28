#!/usr/bin/env python3
"""Composes asyncapi.yaml (AsyncAPI 3) from the per-module fragments in this folder.

A fragment is any events/<name>.yaml except asyncapi.yaml and files starting with "_". It holds components.messages: one message per
domain event, whose x-event block carries what the guards (EventCatalogueTest) check against the code:
  eventType      <subdomain>.<bc>.<EventName>
  version        schemaVersion
  class          fully qualified record in the module's events package
  aggregate      aggregateType of the envelope
  idempotencyKey eventId
  publisher      <subdomain>.<bc>
  consumers      modules with an @ApplicationModuleListener on the event; platform.audit is listed for every audited event
  noConsumer     reason, mandatory when nobody but the audit trail consumes the event ("暂无消费方" and why it is published now)
  delivery       delivery guarantee
  formsFact      true when the event announces a business fact
  businessDate   which date goes into the envelope's businessDate, or "无：<reason>"
  audit          "audited" or "not-audited: <reason>" (the record then carries @NotAudited)
  publisherTest  a test class that exercises the publisher of the event
Run from the repository root:  python3 scripts/codegen/build_asyncapi.py [--check]
--check compares instead of writing and exits 1 when asyncapi.yaml is out of date.
"""
import pathlib
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = ROOT / 'docs' / 'modernization' / 'events'
TARGET = HERE / 'asyncapi.yaml'
SYSTEM_ACTORS = ROOT / 'backend' / 'architecture' / 'system-actors.txt'


def system_actors():
    actors = []
    for row in SYSTEM_ACTORS.read_text().splitlines():
        if not row.strip() or row.startswith('#'):
            continue
        name, uuid, module, reason = (field.strip() for field in row.split('|'))
        actors.append({'name': name, 'actorId': uuid, 'module': module, 'description': reason})
    return actors

ENVELOPE = {
    'type': 'object',
    'description': '所有领域事件共用的信封，字段定义见 proposals/EVENT_ANALYTICS_FIELDS.md（以第 6 节为准）；负载只放业务字段，只含 ID 与值对象',
    'required': ['eventId', 'eventType', 'schemaVersion', 'occurredAt', 'aggregateType', 'aggregateId', 'aggregateVersion',
                 'correlationId', 'actorType', 'actorId', 'relatedAggregates'],
    'properties': {
        'eventId': {'type': 'string', 'format': 'uuid', 'description': 'UUIDv7，兼作消费方的幂等键'},
        'eventType': {'type': 'string', 'description': '<子域>.<BC>.<事件名>'},
        'schemaVersion': {'type': 'integer', 'minimum': 1},
        'occurredAt': {'type': 'string', 'format': 'date-time', 'description': '取自注入的 Clock；不用于期间归属'},
        'aggregateType': {'type': 'string'},
        'aggregateId': {'type': 'string', 'format': 'uuid', 'description': '该聚合自己的主键'},
        'aggregateVersion': {'type': 'integer', 'format': 'int64', 'description': '按聚合计数的事件序号，同一事务内连续编号'},
        'correlationId': {'type': 'string', 'format': 'uuid', 'description': '同一业务操作的全部事件共用；来自 X-Correlation-Id'},
        'causationId': {'type': 'string', 'format': 'uuid', 'nullable': True, 'description': '直接上游事件的 eventId，根事件为空'},
        'actorType': {'type': 'string', 'enum': ['USER', 'SYSTEM']},
        'actorId': {'type': 'string', 'format': 'uuid', 'description': 'USER 为账号的用户 ID；SYSTEM 为 x-system-actors 中声明的作业或监听器的 UUIDv5（backend/architecture/system-actors.txt）；不带姓名'},
        'legalEntityId': {'type': 'string', 'format': 'uuid', 'nullable': True, 'description': '属于某个法人的聚合必填；只有平台级事件为空'},
        'businessDate': {'type': 'string', 'format': 'date', 'nullable': True, 'description': '按 Asia/Shanghai 截日；取哪个日期见各事件的 x-event.businessDate'},
        'accountingPeriod': {'type': 'string', 'nullable': True, 'description': '只由过账管道写入'},
        'relatedAggregates': {'type': 'array', 'items': {'type': 'object', 'properties': {'aggregateType': {'type': 'string'},
                                                                                         'aggregateId': {'type': 'string', 'format': 'uuid'}}}},
    },
}


def fragments():
    return sorted(p for p in HERE.glob('*.yaml') if p.name != 'asyncapi.yaml' and not p.name.startswith('_'))


def channel_key(message_name):
    return message_name[0].lower() + message_name[1:]


def compose():
    messages = {}
    for path in fragments():
        document = yaml.safe_load(path.read_text()) or {}
        for name, message in (document.get('components', {}).get('messages', {}) or {}).items():
            if name in messages:
                raise SystemExit(f'{path.name}: message {name} is defined in two fragments')
            messages[name] = message
    channels, operations, composed = {}, {}, {}
    for name, message in messages.items():
        event = message['x-event']
        payload = message['payload']
        composed[name] = {**{k: v for k, v in message.items() if k != 'payload'},
                          'payload': {'type': 'object', 'required': ['envelope'] + list(payload.get('properties', {})),
                                      'properties': {'envelope': {'$ref': '#/components/schemas/EventEnvelope'},
                                                     **payload.get('properties', {})}}}
        channels[channel_key(name)] = {'address': event['eventType'], 'messages': {name: {'$ref': f'#/components/messages/{name}'}}}
        operations['publish' + name] = {'action': 'send', 'channel': {'$ref': f'#/channels/{channel_key(name)}'},
                                        'summary': f"{event['publisher']} 发布",
                                        'messages': [{'$ref': f'#/channels/{channel_key(name)}/messages/{name}'}]}
        for consumer in event.get('consumers', []):
            operations[f"handle{name}In{''.join(part.capitalize() for part in consumer.split('.'))}"] = {
                'action': 'receive', 'channel': {'$ref': f'#/channels/{channel_key(name)}'}, 'summary': f'{consumer} 消费',
                'messages': [{'$ref': f'#/channels/{channel_key(name)}/messages/{name}'}]}
    return {
        'asyncapi': '3.0.0',
        'info': {'title': 'FORCITIS one 领域事件目录', 'version': '1.0.0',
                 'description': '由 events/*.yaml 分片合成（build_asyncapi.py），不要手工编辑；事件由聚合记录，与业务写入同事务交给事件发布登记表。'},
        'defaultContentType': 'application/json',
        'channels': channels,
        'operations': operations,
        'components': {'schemas': {'EventEnvelope': ENVELOPE}, 'messages': composed},
        'x-system-actors': system_actors(),
    }


def main():
    text = '# 由 build_asyncapi.py 从 events/*.yaml 分片合成；修改分片后重新运行，EventCatalogueTest 会校验两者一致。\n' + yaml.safe_dump(
        compose(), sort_keys=False, allow_unicode=True, width=1000)
    if '--check' in sys.argv:
        if not TARGET.exists() or TARGET.read_text() != text:
            print('asyncapi.yaml is out of date: run python3 scripts/codegen/build_asyncapi.py')
            return 1
        return 0
    TARGET.write_text(text)
    return 0


if __name__ == '__main__':
    sys.exit(main())
