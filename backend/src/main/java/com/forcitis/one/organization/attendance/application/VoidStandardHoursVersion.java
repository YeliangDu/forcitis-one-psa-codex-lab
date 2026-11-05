package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.domain.StandardHours;
import com.forcitis.one.organization.attendance.domain.StandardHoursRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Takes back a version that has not started yet; the database reopens the version it had closed. */
@Service
public class VoidStandardHoursVersion {
    private final StandardHoursRepository versions;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final BusinessZone zone;

    VoidStandardHoursVersion(StandardHoursRepository versions, AuditLog audit, CurrentUser currentUser, Clock clock, BusinessZone zone) {
        this.versions = versions;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public void execute(UUID standardHoursVersionId, String reason) {
        StandardHours version = versions.find(standardHoursVersionId).orElseThrow(() -> new DomainException(
                AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_FOUND, ErrorKind.NOT_FOUND, "no such standard hours version"));
        UserId by = currentUser.userId();
        version.voidVersion(zone.today(clock), versions.tail(version.legalEntityId()).latestInEffectFrom(), reason, by, clock.instant());
        versions.saveVoided(version);
        audit.record(new AuditEntry("organization.attendance.standard-hours.voided", by, null, AuditSubject.without(AuditSubjectKind.OPERATION),
                "standardHoursVersionId=" + standardHoursVersionId));
    }
}
