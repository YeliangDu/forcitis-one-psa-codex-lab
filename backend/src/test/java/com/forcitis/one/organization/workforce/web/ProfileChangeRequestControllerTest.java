package com.forcitis.one.organization.workforce.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequests;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("#980 the reject reason is refused at the web edge (400), never handed to the use case or the database")
class ProfileChangeRequestControllerTest {
    static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-0000000000c1");

    final ProfileChangeRequests requests = mock(ProfileChangeRequests.class);
    final ProfileChangeRequestController controller = new ProfileChangeRequestController(requests);

    @Test
    void aReasonThatIsMissingBlankTooLongOrHoldsNulIsInvalid() {
        for (String bad : new String[] {null, "", "   ", "x".repeat(501), "has a nul \u0000 inside"}) {
            assertThatThrownBy(() -> controller.reject(ID, new RejectProfileChangeRequest(bad), "\"0\"")).isInstanceOf(InvalidRequestException.class);
        }
        verifyNoInteractions(requests);
    }
}
