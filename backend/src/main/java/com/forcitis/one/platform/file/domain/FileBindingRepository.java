package com.forcitis.one.platform.file.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.PreservationCategoryCode;
import com.forcitis.one.platform.file.api.StoredFileSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Links, holds and the reads behind them; every write joins the caller's transaction. */
public interface FileBindingRepository {

    /** Locks the file row until the transaction ends, so two links of one file are decided one after the other. */
    Optional<BindingTarget> lockForBinding(FileId file);

    Optional<Classification> classification(FileId file, DocumentReference document);

    Optional<FilePreservation> preservation(FileId file);

    /** The confirmed version in effect on the day with the highest number, else the newest other one; empty when the category does not exist. */
    Optional<UUID> currentCategoryVersion(PreservationCategoryCode category, LocalDate day);

    void insertBinding(FileId file, DocumentReference document, Classification classification, FileSubject boundBy, Instant at);

    /** The first preservation row: the anchor's event has not happened, so no start date and no expiry yet. */
    void insertFirstPreservation(FileId file, UUID categoryVersion, DocumentReference anchor, Instant at);

    Optional<Reason> holdReason(FileId file, DocumentReference initiator);

    void insertHold(FileId file, DocumentReference initiator, FileSubject heldBy, Reason reason, Instant at);

    Optional<BoundContent> boundContent(FileId file, DocumentReference document);

    List<StoredFileSummary> listByDocument(DocumentReference document);
}
