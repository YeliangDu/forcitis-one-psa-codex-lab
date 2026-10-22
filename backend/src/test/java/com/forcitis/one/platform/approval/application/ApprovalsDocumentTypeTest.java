package com.forcitis.one.platform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.RegisteredDocumentTypes;
import com.forcitis.one.TestIds;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The approval refuses in its own codes: the numbering module's code for an unknown type never reaches a caller of the approval. */
class ApprovalsDocumentTypeTest {

    private final RegisteredDocumentTypes registered = mock(RegisteredDocumentTypes.class);
    private final com.forcitis.one.platform.approval.domain.ApprovalEngine engine = mock(com.forcitis.one.platform.approval.domain.ApprovalEngine.class);
    private final Approvals approvals = new Approvals(null, engine, null, null, null, registered);

    {
        org.mockito.Mockito.when(engine.documentTypeOf(ProcessKey.COST_TIMESHEET_APPROVAL)).thenReturn(new DocumentTypeCode("TIMESHEET"));
    }

    private ApprovalStartRequest request(String type) {
        return new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, new DocumentReference(new DocumentTypeCode(type), new DocumentId(UUID.randomUUID())),
                new com.forcitis.one.kernel.LegalEntityId(TestIds.uuid(1)), TestAccounts.employee(1), new RoundNumber(1), UUID.randomUUID(), Set.of(), List.of(),
                new ContentHash("hash"), new ApprovalSummary("summary"));
    }

    @Test
    @DisplayName("a type the process does not approve is NO_PROCESS, and the registry is not even asked")
    void mismatchIsNoProcess() {
        assertThatThrownBy(() -> approvals.requireAcceptedDocumentType(request("QUOTE")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NO_PROCESS));
        org.mockito.Mockito.verifyNoInteractions(registered);
    }

    @Test
    @DisplayName("the numbering module's unregistered-type code becomes the approval's own code")
    void unregisteredIsTranslated() {
        doThrow(new DomainException(NumberingError.PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED, ErrorKind.UNPROCESSABLE, "not registered"))
                .when(registered).requireRegistered(any());

        assertThatThrownBy(() -> approvals.requireAcceptedDocumentType(request("TIMESHEET")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_DOCUMENT_TYPE_UNREGISTERED));
    }

    @Test
    @DisplayName("any other failure of the registry passes through unchanged, and a registered matching type passes")
    void otherFailuresPassThroughAndAMatchingTypePasses() {
        DomainException other = new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND, ErrorKind.NOT_FOUND, "other");
        doThrow(other).when(registered).requireRegistered(any());
        assertThatThrownBy(() -> approvals.requireAcceptedDocumentType(request("TIMESHEET"))).isSameAs(other);

        org.mockito.Mockito.reset(registered);
        assertThatCode(() -> approvals.requireAcceptedDocumentType(request("TIMESHEET"))).doesNotThrowAnyException();
    }
}
