"""Independent strict consistency check of supplied trace against TEST-owned pins."""
import hashlib
import json

FIELDS = {'repository_id', 'base', 'head', 'source_commit', 'old_manifest', 'new_manifest',
          'run_id', 'attempt', 'latest_run_id', 'latest_attempt', 'job_id', 'upload_step',
          'artifact_id', 'zip_sha256', 'receipt_sha256', 'lanes', 'reviews'}


def verify(raw, pins):
    def unique(pairs):
        out = {}
        for key, value in pairs:
            if key in out:
                raise AssertionError('PROOF_TRACE_DUPLICATE_KEY')
            out[key] = value
        return out
    if not isinstance(raw, bytes) or not 0 < len(raw) <= 65536:
        raise AssertionError('PROOF_TRACE_INVALID_BYTES')
    value = json.loads(raw, object_pairs_hook=unique,
                       parse_constant=lambda x: (_ for _ in ()).throw(AssertionError('PROOF_NONFINITE')))
    if type(value) is not dict or set(value) != FIELDS or set(pins) != FIELDS:
        raise AssertionError('PROOF_TRACE_SCHEMA_OR_TRUST_PINS_MISSING')
    # Exact typed equality prevents True==1 and candidate expected-value flags.
    def exact(left, right):
        if type(left) is not type(right):
            return False
        if isinstance(left, dict):
            return set(left) == set(right) and all(exact(left[k], right[k]) for k in left)
        if isinstance(left, list):
            return len(left) == len(right) and all(exact(a, b) for a, b in zip(left, right))
        return left == right
    if not exact(value, pins):
        raise AssertionError('PROOF_TRACE_NOT_EXACT_TRUSTED_NATIVE_EVIDENCE')
    if (value['source_commit'] == value['head'] or value['run_id'] != value['latest_run_id']
            or value['attempt'] != value['latest_attempt']):
        raise AssertionError('PROOF_SOURCE_OR_LATEST_ATTEMPT_INVALID')
    if value['lanes'] != {name: 'passed' for name in ('light', 'secret', 'shadow', 'frontend', 'backend')}:
        raise AssertionError('PROOF_REQUIRED_LANES_INCOMPLETE')
    reviews = value['reviews']
    if (len(reviews) != 2 or {r['kind'] for r in reviews} != {'首轮', '终审'}
            or len({r['resident'] for r in reviews}) != 2
            or any(r['head'] != value['head'] or r['verdict'] != 'pass' for r in reviews)):
        raise AssertionError('PROOF_CURRENT_INDEPENDENT_REVIEWS_REQUIRED')
    return hashlib.sha256(raw).hexdigest()
