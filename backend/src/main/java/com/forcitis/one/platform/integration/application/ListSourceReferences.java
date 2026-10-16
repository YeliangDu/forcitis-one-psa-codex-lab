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
import com.forcitis.one.platform.integration.domain.ListedSourceReference;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferencePosition;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /** Oldest first by the moment the rows were made, then by id; the cursor is the moment and id of the row the previous page ended with. */
    @Transactional(readOnly = true)
    public CursorPage<SourceReferenceView> execute(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, @Nullable ConfirmationStatus status,
                                                   @Nullable UUID ownId, PageRequest page) {
        if (!SourceReference.isRegistrationAllowed(sourceSystem, sourceObjectKind)) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE,
                    sourceObjectKind + " references of " + sourceSystem + " are not listed: the sync or the import writes them");
        }
        List<ListedSourceReference> found = queries.list(sourceSystem, sourceObjectKind, status, ownId == null ? null : new OwnObjectId(ownId), after(page), page.limit() + 1);
        List<ListedSourceReference> shown = found.stream().limit(page.limit()).toList();
        List<SourceReferenceView> items = shown.stream().map(listed -> SourceReferenceView.of(listed.reference())).toList();
        String next = found.size() > items.size() ? encode(shown.getLast().position()) : null;
        return CursorPage.of(items, next);
    }

    private static String encode(SourceReferencePosition position) {
        return PageRequest.encode(LIST, ChronoUnit.MICROS.between(Instant.EPOCH, position.createdAt()) + "|" + position.id());
    }

    private static @Nullable SourceReferencePosition after(PageRequest page) {
        if (page.cursorValue() == null) {
            return null;
        }
        try {
            String[] parts = page.cursorValue().split("\\|");
            return new SourceReferencePosition(Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS),
                    new SourceReferenceId(Uuids.parseCanonical(parts[1])));
        } catch (RuntimeException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
    }
}
