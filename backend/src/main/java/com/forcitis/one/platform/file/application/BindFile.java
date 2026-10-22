package com.forcitis.one.platform.file.application;

import com.forcitis.one.platform.file.api.BindRequest;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.PersonalInformationMark;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.BindingTarget;
import com.forcitis.one.platform.file.domain.Classification;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import com.forcitis.one.platform.file.domain.FilePreservation;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import com.forcitis.one.kernel.BusinessZone;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Links a file to a document (ADR-0060 sections 2 and 5): the first link copies it from staging to its formal key, and only a committed link
 * makes it downloadable. The copy runs between two checks under the row lock rather than inside one, so no transaction waits on the bucket;
 * a copy that is retried, or raced by another first link, finds the same formal object and counts as done, also once the staged copy has
 * expired. Called inside the caller's transaction it joins it, and the row lock then lasts through the copy.
 */
public final class BindFile {
    private static final Logger LOG = LoggerFactory.getLogger(BindFile.class);

    private final FileBindingRepository bindings;
    private final BusinessFileStore store;
    private final BindingAudit audit;
    private final FileKeys keys;
    private final Clock clock;
    private final BusinessZone zone;
    private final TransactionTemplate transaction;
    private final Counter unlinkedCopies;

    public BindFile(FileBindingRepository bindings, BusinessFileStore store, BindingAudit audit, FileKeys keys, Clock clock, BusinessZone zone,
                    PlatformTransactionManager transactions, MeterRegistry meters) {
        this.bindings = bindings;
        this.store = store;
        this.audit = audit;
        this.keys = keys;
        this.clock = clock;
        this.zone = zone;
        this.transaction = new TransactionTemplate(transactions);
        // Counts only the failed links that left a copy at the formal key; refusals before the copy are not counted here.
        this.unlinkedCopies = Counter.builder("forcitis.platform.file.binding.failures").register(meters);
    }

    public void bind(BindRequest request) {
        StoreContent.requireSubject(request.boundBy());
        if (request.personalInformation() == PersonalInformationMark.SPI) {
            throw FileError.PLATFORM_FILE_SPI_NOT_ACCEPTED.refusal("files with sensitive personal information are not accepted yet");
        }
        Classification classification = new Classification(request.accessLevel(), request.personalInformation());
        Decision first = transaction.execute(tx -> decide(request, classification));
        if (first == null) {
            return;
        }
        boolean copied = !first.target().bound();
        if (copied) {
            BindingTarget target = first.target();
            store.promote(keys.staging(target.id(), target.uploader().kind()), new StoredObjectKey(target.objectKey()), target.contentHash().value(),
                    target.size());
        }
        try {
            link(request, classification);
        } catch (RuntimeException e) {
            if (copied) {
                LOG.error("ALERT file {} was copied to its formal key but not linked; a retry links it, otherwise the object stays unlinked", request.file());
                unlinkedCopies.increment();
            }
            throw e;
        }
    }

    private void link(BindRequest request, Classification classification) {
        transaction.executeWithoutResult(tx -> {
            Decision decision = decide(request, classification);
            if (decision == null) {
                return;
            }
            bindings.insertBinding(request.file(), request.document(), classification, request.boundBy(), clock.instant());
            decision.categoryVersion().ifPresent(version ->
                    bindings.insertFirstPreservation(request.file(), version, request.preservationAnchor(), clock.instant()));
            audit.bound(request.file(), request.document(), classification, request.boundBy());
        });
    }

    /** Null when the same link already exists; the category version is present only for a file's first preservation row. */
    private Decision decide(BindRequest request, Classification classification) {
        BindingTarget target = bindings.lockForBinding(request.file())
                .orElseThrow(() -> FileError.PLATFORM_FILE_NOT_FOUND.refusal("the file does not exist"));
        // The module owning the new document cannot judge the documents the file is already on, so only the uploader or a platform module may
        // link it: anyone else who learns its id gets the answer for a file that does not exist.
        if (!target.uploader().equals(request.boundBy()) && request.boundBy().kind() != FileSubjectKind.PLATFORM_MODULE) {
            throw FileError.PLATFORM_FILE_NOT_FOUND.refusal("the file does not exist");
        }
        Availability.require(target.status());
        Optional<Classification> existing = bindings.classification(request.file(), request.document());
        if (existing.isPresent()) {
            if (existing.get().equals(classification)) {
                return null;
            }
            throw FileError.PLATFORM_FILE_BINDING_CONFLICT.refusal("the file is already linked to this document with another classification");
        }
        if (target.previous().isPresent() && bindings.classification(target.previous().get(), request.document()).isEmpty()) {
            throw FileError.PLATFORM_FILE_PREVIOUS_VERSION_NOT_BOUND.refusal("the previous version is not linked to this document");
        }
        Optional<FilePreservation> preservation = bindings.preservation(request.file());
        if (preservation.isPresent()) {
            if (!preservation.get().equals(new FilePreservation(request.preservationCategory(), request.preservationAnchor()))) {
                throw FileError.PLATFORM_FILE_BINDING_CONFLICT.refusal("the file already has another preservation category or anchor");
            }
            return new Decision(target, Optional.empty());
        }
        UUID version = bindings.currentCategoryVersion(request.preservationCategory(), zone.dateOf(clock.instant()))
                .orElseThrow(() -> FileError.PLATFORM_FILE_PRESERVATION_CATEGORY_UNKNOWN.refusal("the preservation category does not exist"));
        return new Decision(target, Optional.of(version));
    }

    private record Decision(BindingTarget target, Optional<UUID> categoryVersion) {
    }
}
