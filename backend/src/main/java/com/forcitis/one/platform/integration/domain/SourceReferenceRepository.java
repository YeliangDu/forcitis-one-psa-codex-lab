package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.util.Optional;

/** A change applies only to the version and the status the reference was loaded with; a stale write throws a concurrent-update refusal. */
public interface SourceReferenceRepository {

    void insert(SourceReference unsaved);

    /** False when a confirmed reference for the same source object exists; nothing is written then. */
    boolean insertIfAbsent(SourceReference allocated);

    Optional<SourceReference> findForUpdate(SourceReferenceId id);

    /** The confirmed reference of the own object that has no end yet; locks it until the surrounding transaction ends. */
    Optional<SourceReference> findOpenConfirmedForUpdate(ExternalSource system, SourceObjectKind kind, OwnObjectId ownId);

    /** Refuses with a conflict when the confirmed references would then overlap on a source id or an own id. */
    void save(SourceReference reference);
}
