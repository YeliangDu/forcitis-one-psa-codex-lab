package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The lookups for an external system read confirmed references only: pending and rejected ones are never used to talk to one. */
public interface SourceReferenceQueries {

    /** For a kind without a period; the confirmed reference of a kind with a period is found by {@link #findSourceId} on a day. */
    Optional<OwnObjectId> findOwnId(ExternalSource system, SourceObjectKind kind, SourceObjectId sourceId);

    /** {@link #findOwnId} for many source objects in one go; the source ids without a confirmed reference are not in the result. */
    Map<SourceObjectId, OwnObjectId> findOwnIds(ExternalSource system, SourceObjectKind kind, Collection<SourceObjectId> sourceIds);

    /** The source id of the confirmed reference in effect on the day. */
    Optional<SourceObjectId> findSourceId(ExternalSource system, SourceObjectKind kind, OwnObjectId ownId, LocalDate on);

    /** Rows of every status in the order of their ids, the ones after {@code after} first; a null status or own id leaves that filter off. */
    List<SourceReference> list(ExternalSource system, SourceObjectKind kind, @Nullable ConfirmationStatus status, @Nullable OwnObjectId ownId,
                               @Nullable SourceReferenceId after, int limit);
}
