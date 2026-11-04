package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.api.StandardHoursMinutes;
import com.forcitis.one.organization.attendance.api.StandardHoursScopeKind;
import com.forcitis.one.organization.attendance.api.StandardHoursVersionId;
import com.forcitis.one.organization.attendance.api.StandardHoursVersionNumber;
import com.forcitis.one.organization.attendance.events.StandardHoursVersionPublished;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One version of the standard working minutes of one scope (a legal entity, or the DEFAULT scope when legalEntityId is null). A correction
 * is a new version; a version is never edited. Only a future latest version can be voided. The database repeats these rules as triggers.
 */
public final class StandardHours extends AggregateRoot {
    private static final int MAX_MINUTES = 1440;
    private static final int MAX_TEXT = 200;
    private static final int MAX_VOID_REASON = 500;

    private final UUID id;
    private final @Nullable LegalEntityId legalEntityId;
    private final int versionNumber;
    private final LocalDate effectiveFromDate;
    private final int workdayMinutes;
    private final int adjustedWorkdayMinutes;
    private final String basisRef;
    private final List<DateOverride> dateOverrides;
    private final UserId confirmedBy;
    private final Instant confirmedAt;
    private final AuditEntryId decisionAuditEntryId;
    private StandardHoursStatus status;
    private @Nullable UserId voidedBy;
    private @Nullable Instant voidedAt;
    private @Nullable String voidReason;

    private StandardHours(UUID id, @Nullable LegalEntityId legalEntityId, int versionNumber, StandardHoursStatus status, LocalDate effectiveFromDate,
            int workdayMinutes, int adjustedWorkdayMinutes, String basisRef, List<DateOverride> dateOverrides, UserId confirmedBy,
            Instant confirmedAt, AuditEntryId decisionAuditEntryId) {
        super(0, 0);
        this.id = Objects.requireNonNull(id, "id");
        this.legalEntityId = legalEntityId;
        this.versionNumber = versionNumber;
        this.status = status;
        this.effectiveFromDate = effectiveFromDate;
        this.workdayMinutes = workdayMinutes;
        this.adjustedWorkdayMinutes = adjustedWorkdayMinutes;
        this.basisRef = basisRef;
        this.dateOverrides = List.copyOf(dateOverrides);
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
        this.decisionAuditEntryId = decisionAuditEntryId;
    }

    /** The first version of a scope may start in the past; every later one starts after the latest and not before today. */
    public static StandardHours publish(UUID id, @Nullable LegalEntityId legalEntityId, StandardHoursScopeTail tail, LocalDate today,
            LocalDate effectiveFromDate, int workdayMinutes, int adjustedWorkdayMinutes, List<DateOverride> dateOverrides, String basisRef,
            UserId confirmedBy, Instant confirmedAt, AuditEntryId decisionAuditEntryId) {
        requireMinutes(workdayMinutes, "workdayMinutes");
        requireMinutes(adjustedWorkdayMinutes, "adjustedWorkdayMinutes");
        requireText(basisRef, "basisRef", MAX_TEXT);
        Set<LocalDate> dates = new HashSet<>();
        for (DateOverride override : dateOverrides) {
            requireMinutes(override.minutes(), "override minutes");
            requireText(override.reason(), "override reason", MAX_TEXT);
            if (override.workDate().isBefore(effectiveFromDate)) {
                throw invalid("an override date is before effectiveFromDate: " + override.workDate());
            }
            if (!dates.add(override.workDate())) {
                throw invalid("two overrides name the same date: " + override.workDate());
            }
        }
        LocalDate latest = tail.latestInEffectFrom();
        if (latest != null && !effectiveFromDate.isAfter(latest)) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP, ErrorKind.CONFLICT,
                    "effectiveFromDate must be later than the latest version of the scope: " + latest);
        }
        if (latest != null && effectiveFromDate.isBefore(today)) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_BACKDATED, ErrorKind.UNPROCESSABLE,
                    "a version after the first cannot start before today: " + effectiveFromDate);
        }
        StandardHours version = new StandardHours(id, legalEntityId, tail.lastVersionNumber() + 1, StandardHoursStatus.IN_EFFECT, effectiveFromDate,
                workdayMinutes, adjustedWorkdayMinutes, basisRef, dateOverrides, confirmedBy, confirmedAt, decisionAuditEntryId);
        version.record((envelopes, sequence) -> new StandardHoursVersionPublished(envelopes.wrap(new EventDraft(
                "organization.attendance.StandardHoursVersionPublished", 1, "StandardHours", id, sequence, legalEntityId, effectiveFromDate,
                List.of())), new StandardHoursVersionId(id),
                legalEntityId == null ? StandardHoursScopeKind.DEFAULT : StandardHoursScopeKind.LEGAL_ENTITY, legalEntityId, effectiveFromDate,
                new StandardHoursMinutes(workdayMinutes), new StandardHoursMinutes(adjustedWorkdayMinutes), new StandardHoursVersionNumber(version.versionNumber)));
        return version;
    }

    public static StandardHours restore(StandardHoursVersionRecord stored, AuditEntryId decisionAuditEntryId) {
        return new StandardHours(stored.id(), stored.legalEntityId(), stored.versionNumber(), stored.status(), stored.effectiveFromDate(),
                stored.workdayMinutes(), stored.adjustedWorkdayMinutes(), stored.basisRef(), stored.dateOverrides(), stored.confirmedBy(),
                stored.confirmedAt(), decisionAuditEntryId);
    }

    /** latestInEffectFrom is the start of the latest version in effect of this scope. */
    public void voidVersion(LocalDate today, @Nullable LocalDate latestInEffectFrom, String reason, UserId by, Instant at) {
        if (status != StandardHoursStatus.IN_EFFECT || !effectiveFromDate.isAfter(today) || !effectiveFromDate.equals(latestInEffectFrom)) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_VOIDABLE, ErrorKind.UNPROCESSABLE,
                    "only the latest version in effect that starts after today can be voided");
        }
        requireText(reason, "void reason", MAX_VOID_REASON);
        this.status = StandardHoursStatus.VOIDED;
        this.voidedBy = by;
        this.voidedAt = at;
        this.voidReason = reason;
    }

    private static void requireMinutes(int minutes, String name) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            throw invalid(name + " must be 1.." + MAX_MINUTES);
        }
    }

    private static void requireText(String text, String name, int max) {
        if (text.isEmpty() || text.length() > max || !text.equals(text.strip())) {
            throw invalid(name + " must be 1.." + max + " characters without surrounding blanks");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID, message);
    }

    public UUID id() {
        return id;
    }

    public @Nullable LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public int versionNumber() {
        return versionNumber;
    }

    public StandardHoursStatus status() {
        return status;
    }

    public LocalDate effectiveFromDate() {
        return effectiveFromDate;
    }

    public int workdayMinutes() {
        return workdayMinutes;
    }

    public int adjustedWorkdayMinutes() {
        return adjustedWorkdayMinutes;
    }

    public String basisRef() {
        return basisRef;
    }

    public List<DateOverride> dateOverrides() {
        return dateOverrides;
    }

    public UserId confirmedBy() {
        return confirmedBy;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public AuditEntryId decisionAuditEntryId() {
        return decisionAuditEntryId;
    }

    public @Nullable UserId voidedBy() {
        return voidedBy;
    }

    public @Nullable Instant voidedAt() {
        return voidedAt;
    }

    public @Nullable String voidReason() {
        return voidReason;
    }
}
