"""Test-owned trace pins from verified phase material and synthetic raw builder."""
from types import SimpleNamespace
from locked_phase_setup import CONTEXTS,phase_manifest,need
from synthetic_inputs import build
import hashlib


def trusted_pins(test):
    context=CONTEXTS.get(str(test.root))
    need(context is not None,'E2E_TRACE_PINS_REQUIRE_PRE_OH_VERIFIED_SOURCE')
    old=phase_manifest(context,'old',test.old)[1]
    new=phase_manifest(context,'new',test.candidate)[1]
    model=SimpleNamespace(test=test,merge=None,manifests=dict(old=old,new=new),fault=None)
    value=build(model)
    return dict(repository_id=1449415666,base=test.old,head=test.candidate,source_commit=test.old,
        old_manifest=old['digest'],new_manifest=new['digest'],run_id=700,attempt=2,
        latest_run_id=700,latest_attempt=2,job_id=800,upload_step=4,artifact_id=900,
        zip_sha256=value['candidate_pins']['zip_sha256'],receipt_sha256=value['candidate_pins']['receipt_sha256'],
        lanes={name:'passed' for name in ('light','secret','shadow','frontend','backend')},
        reviews=[dict(id=row['id'],kind=kind,resident=role,head=test.candidate,verdict='pass',body_sha256=value['review_hashes'][kind])
            for row,kind,role in zip(value['rows'],['首轮','终审'],['one-design','one-test'])])
