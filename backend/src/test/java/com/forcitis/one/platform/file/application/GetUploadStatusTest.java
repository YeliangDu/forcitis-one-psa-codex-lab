package com.forcitis.one.platform.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetUploadStatusTest {
    final StoredFileRepository files = mock(StoredFileRepository.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final GetUploadStatus status = new GetUploadStatus(files, currentUser);

    @Test
    @DisplayName("ADR-0060 the scan state of an upload is asked as the signed-in person; another person's upload answers not found")
    void asksAsTheSignedInPerson() {
        FileId own = new FileId(UUID.randomUUID());
        FileId foreign = new FileId(UUID.randomUUID());
        when(currentUser.userId()).thenReturn(TestAccounts.account(1));
        when(files.unboundStatusStoredBy(own, TestAccounts.account(1).value())).thenReturn(Optional.of(FileStatus.QUARANTINED));
        when(files.unboundStatusStoredBy(foreign, TestAccounts.account(1).value())).thenReturn(Optional.empty());

        assertThat(status.execute(own)).isEqualTo(FileStatus.QUARANTINED);
        assertThatThrownBy(() -> status.execute(foreign)).isInstanceOfSatisfying(DomainException.class,
                refusal -> assertThat(refusal.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_FOUND));
    }
}
