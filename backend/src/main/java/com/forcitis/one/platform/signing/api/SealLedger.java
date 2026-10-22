package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SealLedger {

    /**
     * The version in force at the sealing instant, cut to a date in the kernel BusinessZone. Throws PLATFORM_SIGNING_LEDGER_ENTRY_NOT_FOUND for an unknown entry and
     * PLATFORM_SIGNING_NO_VERSION_AT when the instant has no version (earlier than the first one); a stopped entry is the
     * caller's check on {@link SealLedgerEntryVersionView#active()} (ADR-0064 decision 3).
     */
    SealLedgerEntryVersionView versionAt(SealLedgerEntryId entry, Instant at);

    /** Entries of one legal entity that are in use on the date, ordered by seal type, for choosing a seal. */
    List<SealLedgerEntryView> activeEntries(LegalEntityId legalEntity, LocalDate on);

    /** The in-use entry of one legal entity and seal type on the date; the exclusion constraint allows at most one. */
    Optional<SealLedgerEntryView> activeEntry(LegalEntityId legalEntity, SealTypeCode sealType, LocalDate on);

    /** The confirmed seal type list in force on the date. */
    List<SealTypeView> sealTypes(LocalDate on);
}
