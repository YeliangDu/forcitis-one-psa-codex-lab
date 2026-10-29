package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The reason of a correction is stored as typed, so the entry measures the original value against the column's 200. */
class CorrectLegalEntityAttributionReasonLengthTest {
    static final RuntimeException REACHED_SUBJECT_LOOKUP = new RuntimeException("past the reason check");

    final LegalEntityAttributionRepository attributions = mock(LegalEntityAttributionRepository.class);
    final AttributionSubjects subjects = mock(AttributionSubjects.class);
    final CallerPermissions caller = mock(CallerPermissions.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final CorrectLegalEntityAttribution correct = new CorrectLegalEntityAttribution(attributions, subjects, mock(DomainEvents.class), caller,
            mock(AuditLog.class), mock(OwnAttributionGuard.class), currentUser);
    final UUID subjectId = UUID.randomUUID();

    CorrectLegalEntityAttributionReasonLengthTest() {
        when(caller.holds(CorrectLegalEntityAttribution.PERMISSION)).thenReturn(true);
        when(currentUser.userId()).thenReturn(TestAccounts.account(1001));
        when(subjects.require(any(), any())).thenThrow(REACHED_SUBJECT_LOOKUP);
    }

    private void run(String reason) {
        correct.execute(AttributionKind.EMPLOYEE, subjectId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), reason,
                ExpectedVersion.of(1));
    }

    @Test
    @DisplayName("200 characters pass the reason check")
    void atTheLimitPasses() {
        assertThatThrownBy(() -> run("x".repeat(200))).isSameAs(REACHED_SUBJECT_LOOKUP);
    }

    @Test
    @DisplayName("201 characters are refused with 422 before anything is read")
    void overTheLimitIsRefused() {
        assertThatThrownBy(() -> run("x".repeat(201)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED);
                    org.assertj.core.api.Assertions.assertThat(e.kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
                });
        verifyNoInteractions(attributions);
        verify(subjects, org.mockito.Mockito.never()).require(any(), any());
    }

    @Test
    @DisplayName("200 code points including supplementary characters pass although UTF-16 is longer")
    void codePointsAreCountedNotUtf16Units() {
        String atLimit = "\uD83D\uDE00".repeat(200);
        assertThatThrownBy(() -> run(atLimit)).isSameAs(REACHED_SUBJECT_LOOKUP);
        String overLimit = "\uD83D\uDE00".repeat(201);
        assertThatThrownBy(() -> run(overLimit)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("surrounding blanks count, since the stored value is the original")
    void blanksCount() {
        assertThatThrownBy(() -> run(" ".repeat(100) + "x" + " ".repeat(100)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED);
                    org.assertj.core.api.Assertions.assertThat(e.kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
                });
        verifyNoInteractions(attributions);
        verify(subjects, org.mockito.Mockito.never()).require(any(), any());
    }
}
