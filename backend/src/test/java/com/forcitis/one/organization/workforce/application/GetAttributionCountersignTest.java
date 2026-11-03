package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.CountersignReadQueries;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("#910 S3 the row of a request shows the reason of a withdrawal request and none for any other request")
class GetAttributionCountersignTest {
    private final CountersignReadAccess access = mock(CountersignReadAccess.class);
    private final GetAttributionCountersign get = new GetAttributionCountersign(access, mock(CountersignReadQueries.class), mock(AccountEmployees.class));
    private final AttributionCountersignId id = new AttributionCountersignId(UUID.fromString("00000000-0000-7000-8000-0000000000a1"));

    private String reasonShownFor(String requestReason) {
        AttributionCountersign row = mock(AttributionCountersign.class);
        when(row.requestReason()).thenReturn(requestReason);
        OrgStructureCommand command = mock(OrgStructureCommand.class);
        when(command.id()).thenReturn(new OrgStructureCommandId(UUID.fromString("00000000-0000-7000-8000-0000000000b1")));
        when(command.kind()).thenReturn(OrgStructureCommandKind.MERGE);
        when(command.status()).thenReturn(OrgCommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN);
        when(command.effectiveDate()).thenReturn(LocalDate.parse("2026-12-01"));
        when(access.ofRequest(id)).thenReturn(new CountersignReadAccess.Readable(row, List.of(), command, null));
        return get.execute(id).command().withdrawalReason();
    }

    @Test
    void aWithdrawalRequestShowsItsReason() {
        assertThat(reasonShownFor("synthetic-withdrawal-reason-55")).isEqualTo("synthetic-withdrawal-reason-55");
    }

    @Test
    void aChangeRequestShowsNoReason() {
        assertThat(reasonShownFor(null)).isNull();
    }
}
