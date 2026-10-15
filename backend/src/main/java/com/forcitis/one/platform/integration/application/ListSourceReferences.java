package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.IntegrationError;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the approver finds to decide and what the legal entity page shows: only the category written by hand, so the synchronised ones stay closed. */
@Service
public class ListSourceReferences {
    private static final String LIST = "source-references";

    private final SourceReferenceQueries queries;

    ListSourceReferences(SourceReferenceQueries queries) {
        this.queries = queries;
    }

    /** In the order of the ids (time-ordered); the cursor is the id of the row the previous page ended with. */
    @Transactional(readOnly = true)
    public CursorPage<SourceReferenceView> execute(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, @Nullable ConfirmationStatus status,
                                                   @Nullable UUID ownId, PageRequest page) {
        if (!SourceReference.isRegistrationAllowed(sourceSystem, sourceObjectKind)) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE,
                    sourceObjectKind + " references of " + sourceSystem + " are not listed: the sync or the import writes them");
        }
        List<SourceReference> found = queries.list(sourceSystem, sourceObjectKind, status, ownId == null ? null : new OwnObjectId(ownId), after(page), page.limit() + 1);
        List<SourceReferenceView> items = found.stream().limit(page.limit()).map(SourceReferenceView::of).toList();
        String next = found.size() > items.size() ? PageRequest.encode(LIST, items.getLast().sourceReferenceId().toString()) : null;
        return CursorPage.of(items, next);
    }

    private static @Nullable SourceReferenceId after(PageRequest page) {
        if (page.cursorValue() == null) {
            return null;
        }
        try {
            return new SourceReferenceId(Uuids.parseCanonical(page.cursorValue()));
        } catch (RuntimeException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
    }
}
