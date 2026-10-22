package com.forcitis.one.platform.file.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.file.api.DownloadLink;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.BoundContent;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The owning module has authorised the requester; this checks the file is linked to that document and available, then signs its formal key
 * (ADR-0060 section 2). A file that does not exist and one linked elsewhere are refused alike, so ids cannot be probed through a document.
 */
public final class IssueDownloadLink {
    private final FileBindingRepository bindings;
    private final BusinessFileStore store;
    private final BindingAudit audit;
    private final Duration defaultValidity;
    private final Duration maximumValidity;
    private final Clock clock;

    public IssueDownloadLink(FileBindingRepository bindings, BusinessFileStore store, BindingAudit audit, Duration defaultValidity, Duration maximumValidity,
                             Clock clock) {
        this.bindings = bindings;
        this.store = store;
        this.audit = audit;
        this.defaultValidity = defaultValidity;
        this.maximumValidity = maximumValidity;
        this.clock = clock;
    }

    public DownloadLink issue(FileId file, DocumentReference document, FileSubject requester, Duration validity) {
        StoreContent.requireSubject(requester);
        Duration lifetime = validity == null ? defaultValidity : validity;
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw FileError.PLATFORM_FILE_LINK_VALIDITY_INVALID.refusal("a download link is valid for a positive time");
        }
        if (lifetime.compareTo(maximumValidity) > 0) {
            throw FileError.PLATFORM_FILE_LINK_VALIDITY_EXCEEDED.refusal("a download link is valid for at most " + maximumValidity);
        }
        BoundContent content = bindings.boundContent(file, document)
                .orElseThrow(() -> FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT.refusal("the file is not linked to this document"));
        Availability.require(content.status());
        Instant issuedAt = clock.instant();
        URI url = store.presignDownload(new StoredObjectKey(content.objectKey()), lifetime, content.originalName(), content.mediaType());
        audit.downloadLinkIssued(file, document, requester, lifetime);
        return new DownloadLink(url, issuedAt.plus(lifetime));
    }
}
