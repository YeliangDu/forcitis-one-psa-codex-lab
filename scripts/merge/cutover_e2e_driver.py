"""Actual child orchestration only; fixture material/IPC/effects are test-owned.

No synthetic receipt/result producer or alternate queue/journal algorithm.
Unbound independently pinned material fails by name before launching any source.
"""
import json
from pathlib import Path
import sqlite3


OLD = 'scripts/merge/mq_lab_cutover_old.py'
RECOVERY = 'scripts/merge/mq_lab_cutover_recovery.py'
SUCCESSOR = 'scripts/merge/mq_lab_cutover_successor.py'


def need(ok, reason):
    if not ok:
        raise AssertionError(reason)


def run_cutover_scenario(fixture):
    try:
        from cutover_fixture_runtime import bind_scenario
    except ImportError as error:
        raise AssertionError('E2E_LOCKED_SOURCE_INVENTORY_UNBOUND') from error
    runtime = bind_scenario(fixture)
    root = Path(fixture['root'])
    need(root.is_absolute() and root.resolve(strict=True) == root
         and root == Path(runtime.root), 'E2E_RUNTIME_NOT_EXACT_SCRATCH')
    host = fixture.get('host_probe') or runtime.host_probe
    recovery = fixture.get('recovery_probe') or runtime.recovery_probe
    need(host.test.root == root and recovery.host is host, 'E2E_TEST_OWNED_PROBE_MISMATCH')
    old_paths, new_paths = tuple(runtime.source_paths('old')), tuple(runtime.source_paths('new'))
    need(OLD in old_paths and RECOVERY in old_paths and SUCCESSOR in new_paths,
         'E2E_LOCKED_SOURCE_INVENTORY_UNBOUND')
    # source_root materializes already pinned O/H/M objects; it must not seed
    # code after O/H or select inventory from this driver's/candidate's result.
    old_root = runtime.source_root('old', fixture['old_base'])
    request = runtime.request_file
    fault = fixture['fault']
    interrupted = fault in ('lost-response-restart', 'crash-after-send')
    with runtime.receiver(host, recovery) as receiver:
        old_pid = host.launch('old', old_root, OLD, request, fixture['old_base'], old_paths)
        receiver.register_peer('old', old_pid)
        runtime.release_child('old', old_pid)
        if interrupted:
            # Receiver owns CAS->kill->communicate. Do not race it with another
            # communicate or invent an old-exited flag in the candidate process.
            receiver.wait_for_old_reaped(timeout=30)
            need('old' in host.reaped, 'E2E_OLD_NOT_ACTUALLY_REAPED')
            recovery.launch_recovery(old_root, RECOVERY, request, fixture['old_base'], old_paths)
            observed = recovery.verify_recovery()
            return dict(action='hold', protected_send_count=host.cas_count, ordinary_send_count=0,
                        readback_count=len(observed['git_reads']),
                        restart_trace=str(recovery.recovery[1].relative_to(root)))
        old_profile = host.wait('old')
        if host.cas_count == 0:
            return dict(action='hold', protected_send_count=0, ordinary_send_count=0)
        need(host.cas_count == 1, 'E2E_PROTECTED_EFFECT_NOT_EXACTLY_ONCE')
        merge = runtime.actual_main()  # Fixed read port; never a requested SHA.
        new_root = runtime.source_root('new', merge)
        new_pid = host.launch('successor', new_root, SUCCESSOR, request, merge, new_paths)
        receiver.register_peer('successor', new_pid)
        runtime.release_child('successor', new_pid)
        new_profile = host.wait('successor')
        # Read actual durable state, not the child stdout's action or counters.
        database = Path(fixture['common_dir']) / 'merge-queue/releases.sqlite3'
        with sqlite3.connect(database.as_uri() + '?mode=ro', uri=True) as db:
            bindings = db.execute('SELECT phase,adopted_main FROM upgrade_bindings').fetchall()
            ordinary = db.execute("SELECT number,status,merge_sha FROM releases "
                                  "WHERE mode='ordinary' AND number>5").fetchall()
        final_main = runtime.actual_main()
        confirmed = (bindings == [('adopted', merge)] and len(ordinary) == 1
                     and ordinary[0][1:] == ('confirmed', final_main) and final_main != merge)
        readbacks = sum(call['function'] == 'read_merged'
                        for profile in (old_profile, new_profile) for call in profile['calls'])
        old_trace = host.children['old'][1].relative_to(root)
        new_trace = host.children['successor'][1].relative_to(root)
        proof = Path(runtime.proof_trace)
        need(proof.resolve(strict=True).is_relative_to(root), 'E2E_PROOF_TRACE_OUTSIDE_SCRATCH')
        return dict(action='ordinary-next-confirmed' if confirmed else 'hold',
                    protected_send_count=host.cas_count, ordinary_send_count=1 if confirmed else 0,
                    successor=merge, ordinary_merge=final_main if confirmed else None,
                    readback_count=readbacks, restart_trace=str(new_trace),
                    new_loader_trace=str(new_trace), lock_trace=str(old_trace),
                    proof_trace=str(proof.relative_to(root)))
