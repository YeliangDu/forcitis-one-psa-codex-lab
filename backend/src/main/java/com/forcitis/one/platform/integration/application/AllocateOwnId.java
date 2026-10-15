package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import com.forcitis.one.platform.integration.domain.SourceReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The own id of a source object, made on first sight. Own transaction: the reference is committed before the caller writes anything that
 * uses the id, and stays when that write fails, so the next run finds the same id. Concurrent first sights meet in the insert, which does
 * nothing for the second one, and both read the one row back.
 */
@Service
class AllocateOwnId {
    private final SourceReferenceRepository references;
    private final SourceReferenceQueries queries;
    private final IdGenerator ids;

    AllocateOwnId(SourceReferenceRepository references, SourceReferenceQueries queries, IdGenerator ids) {
        this.references = references;
        this.queries = queries;
        this.ids = ids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OwnObjectId execute(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, SourceObjectId sourceId) {
        references.insertIfAbsent(SourceReference.allocate(new SourceReferenceId(ids.next()), sourceSystem, sourceObjectKind, sourceId,
                new OwnObjectId(ids.next())));
        return queries.findOwnId(sourceSystem, sourceObjectKind, sourceId).orElseThrow(() -> new IllegalStateException(
                "the " + sourceObjectKind + " reference of " + sourceSystem + " vanished after it was allocated"));
    }
}
