"""One in-memory first launch, made only from a validated native reply."""
from runtime_store import need


class FirstLaunch:
    def __init__(self):
        self.grant=None
        self.consumed_revision=None

    def issue(self, *, thread_id, receipt_id, connection, epoch, owner):
        need(self.grant is None and self.consumed_revision is None
             and all(type(x) is str and x for x in (thread_id,receipt_id,epoch))
             and connection is not None and type(owner) is tuple and len(owner)==3,
             'first-launch-issuance-not-allowed')
        self.grant=(thread_id,receipt_id,connection,epoch,owner)

    def authorize(self, *, thread_id, connection, epoch, owner, revision, prior_dispatch, has_persisted):
        need(prior_dispatch is None and type(revision) is int and revision>0
             and self.consumed_revision is None,'first-dispatch-already-consumed')
        if self.grant is None:
            need(has_persisted is True,'first-launch-receipt-not-held')
            return 'persisted'
        tid,receipt,channel,issued_epoch,issued_owner=self.grant
        need(tid==thread_id and channel is connection and issued_epoch==epoch
             and issued_owner==owner and bool(receipt),'first-launch-binding-mismatch')
        self.consumed_revision=revision
        return 'first'


def require_stable(*, completed, persisted):
    need(completed is True and persisted is True,'stable-requires-native-durable-completion')


def require_resume(*, phase, persisted):
    need(phase in ('provisional','stable') and persisted is True,'resume-requires-persisted-native-thread')
