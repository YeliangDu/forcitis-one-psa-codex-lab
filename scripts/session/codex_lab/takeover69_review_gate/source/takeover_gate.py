"""Evidence packet shape gate for review only. No transport, writer, or authority."""
import re


def _sha(value, width):
    return isinstance(value, str) and re.fullmatch('[a-f0-9]{'+str(width)+'}', value) is not None


def _seq(value):
    return type(value) is int and value >= 0


def plan(packet, observed):
    hold = {'action': 'HOLD'}
    if type(packet) is not dict or type(observed) is not dict:
        return hold
    if any(type(x.get('number')) is not int or x['number'] != 69 for x in (packet, observed)):
        return hold
    exact = {k: packet.get(k) for k in ('number', 'head', 'base')}
    if not all(_sha(exact[k], 40) and exact[k] == observed.get(k) for k in ('head', 'base')):
        return hold
    if not _sha(packet.get('C'), 40) or packet.get('authorization') != dict(
        exact, purpose='takeover-preparation', scope='operator-review-only'):
        return hold
    control = observed.get('trusted_current_C')
    if type(control) is not dict or control.get('trusted') is not True or control.get('sha') != packet['C'] or any(
        control.get(k) != exact[k] for k in ('head','base')
    ):
        return hold
    for key, seq in (('queue','revision'), ('journal','sequence')):
        p = packet.get(key)
        if type(p) is not dict or set(p) != {seq,'sha256'} or not _seq(p[seq]) or not _sha(p['sha256'],64) or p != observed.get(key):
            return hold
    lock = packet.get('lock')
    if type(lock) is not dict or set(lock) != {'state','generation'} or not _seq(lock['generation']) or lock['state'] != 'free' or lock != observed.get('lock'):
        return hold
    old = observed.get('old_sender')
    server = observed.get('server_exclusive')
    terminal = type(old) is dict and old.get('state') == 'terminal' and old.get('authoritative') is True
    exclusive = (type(server) is dict and server.get('held') is True and server.get('authority') == 'server'
                 and type(server.get('number')) is int and all(server.get(k)==v for k,v in exact.items()))
    if not (terminal or exclusive) or observed.get('unknown') != []:
        return hold
    return {'action':'READY_FOR_OPERATOR_REVIEW'}
