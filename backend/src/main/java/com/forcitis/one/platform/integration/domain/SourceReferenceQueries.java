package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.time.LocalDate;
import java.util.Optional;

/** Reads confirmed references only: pending and rejected ones are never used to talk to an external system. */
public interface SourceReferenceQueries {

    Optional<OwnObjectId> findOwnId(ExternalSource system, SourceObjectKind kind, SourceObjectId sourceId);

    /** The source id of the confirmed reference in effect on the day. */
    Optional<SourceObjectId> findSourceId(ExternalSource system, SourceObjectKind kind, OwnObjectId ownId, LocalDate on);
}
