"""Independent generated synthetic raw snapshots; no candidate-returned bytes."""
import copy
import hashlib
import json


def encoded(value):
    return (json.dumps(value,sort_keys=True,separators=(',',':'),allow_nan=False)+'\n').encode()


def scope_key(key, scope):
    return key, json.dumps(scope,sort_keys=True,separators=(',',':'),allow_nan=False)


class ScenarioLedger:
    def __init__(self):
        self.records, self.reads, self.audit = {}, [], []

    def freeze(self, key, scope, value, binary=False):
        raw = value if binary else encoded(value)
        token = scope_key(key,scope)
        if token in self.records:
            raise AssertionError('E2E_DUPLICATE_RAW_SCOPE')
        self.records[token]=(bytes(raw),hashlib.sha256(raw).hexdigest())
        return dict(key=key,scope=copy.deepcopy(scope))

    def read(self, key, scope):
        token=scope_key(key,scope)
        if token not in self.records:
            raise AssertionError('E2E_RAW_SCOPE_OR_RECORD_UNBOUND: '+repr(token))
        raw,digest=self.records[token]
        if hashlib.sha256(raw).hexdigest()!=digest:
            raise AssertionError('E2E_FROZEN_RAW_BYTES_CHANGED')
        self.reads.append((key,digest))
        self.audit.append(dict(key=key,scope=json.loads(token[1]),sha256=digest))
        return raw

    def require_complete(self, keys):
        if not set(keys)<={key for key,_ in self.reads}:
            raise AssertionError('E2E_MANDATORY_RAW_NOT_CONSUMED')
        return {key:digest for key,digest in self.reads}
