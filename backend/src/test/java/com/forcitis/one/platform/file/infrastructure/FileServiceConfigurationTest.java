package com.forcitis.one.platform.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.file.api.PlatformFileContent;
import com.forcitis.one.platform.file.application.BindFile;
import com.forcitis.one.platform.file.application.GetUploadStatus;
import com.forcitis.one.platform.file.application.IssueDownloadLink;
import com.forcitis.one.platform.file.application.StoreContent;
import com.forcitis.one.platform.file.application.UploadFile;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.ContentChecks;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import com.forcitis.one.platform.file.domain.StoreAudit;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.MalwareScanner;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

/** Unit 4 security review, item 4: with the file service on, a missing scanner or bucket stops the start-up; off, nothing is stored. */
class FileServiceConfigurationTest {
    @TempDir
    Path staging;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(FileServiceConfiguration.class)
                .withBean(ContentChecks.class, () -> mock(ContentChecks.class)).withBean(StoredFileRepository.class, () -> mock(StoredFileRepository.class))
                .withBean(StoreAudit.class, () -> mock(StoreAudit.class))
                .withBean(FileBindingRepository.class, () -> mock(FileBindingRepository.class)).withBean(BindingAudit.class, () -> mock(BindingAudit.class)).withBean(IdGenerator.class, () -> UUID::randomUUID)
                .withBean(Clock.class, com.forcitis.one.BusinessDay::fixedClock).withBean(BusinessZone.class, () -> new BusinessZone(ZoneId.of("Asia/Shanghai"))).withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new).withBean(CurrentUser.class, () -> mock(CurrentUser.class))
                .withPropertyValues("forcitis.platform.file.store.enabled=true", "forcitis.platform.file.store.environment-prefix=dev",
                        "forcitis.platform.file.store.max-file-size=100MB", "forcitis.platform.file.store.allowed-media-types=application/pdf",
                        "forcitis.platform.file.store.staging-root=" + staging, "forcitis.platform.file.store.upload-deadline=PT10M", "forcitis.platform.file.store.staging-sweep-age=PT1H",
                        "forcitis.platform.file.store.staging-sweep-interval=PT10M", "forcitis.platform.file.store.download-link-validity=PT5M",
                        "forcitis.platform.file.store.download-link-maximum-validity=PT15M", "forcitis.platform.file.store.unbound-expiry-age=P2D",
                        "forcitis.platform.file.store.one-time-link-unbound-expiry-age=P16D", "forcitis.platform.file.store.unbound-expiry-interval=PT10M");
    }

    private static BusinessFileStore bucket() {
        BusinessFileStore store = mock(BusinessFileStore.class);
        org.mockito.Mockito.when(store.maxLinkValidity()).thenReturn(java.time.Duration.ofMinutes(15));
        return store;
    }

    @Test
    void aLinkCeilingAboveWhatTheBucketSignsDoesNotStart() {
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).withBean(BusinessFileStore.class, () -> {
            BusinessFileStore store = mock(BusinessFileStore.class);
            org.mockito.Mockito.when(store.maxLinkValidity()).thenReturn(java.time.Duration.ofMinutes(10));
            return store;
        }).run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("exceeds what the business file bucket signs"));
    }

    @Test
    void onWithBothPortsStores() {
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).withBean(BusinessFileStore.class, FileServiceConfigurationTest::bucket)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(StoreContent.class).hasSingleBean(BindFile.class)
                        .hasSingleBean(IssueDownloadLink.class).hasSingleBean(PlatformFileContent.class).hasSingleBean(GetUploadStatus.class)
                        .hasSingleBean(UploadFile.class));
    }

    @Test
    void onWithoutTheScannerOrTheBucketDoesNotStart() {
        runner().withBean(BusinessFileStore.class, FileServiceConfigurationTest::bucket).run(context -> assertThat(context).hasFailed());
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aSizeAboveWhatClamAvScansDoesNotStart() {
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).withBean(BusinessFileStore.class, FileServiceConfigurationTest::bucket)
                .withPropertyValues("forcitis.platform.file.store.max-file-size=3GB").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aSweepAgeNotAboveTheUploadDeadlineDoesNotStart() {
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).withBean(BusinessFileStore.class, FileServiceConfigurationTest::bucket)
                .withPropertyValues("forcitis.platform.file.store.staging-sweep-age=PT10M").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aDownloadLinkLongerThanFifteenMinutesOrADefaultAboveTheMaximumDoesNotStart() {
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).withBean(BusinessFileStore.class, FileServiceConfigurationTest::bucket)
                .withPropertyValues("forcitis.platform.file.store.download-link-maximum-validity=PT16M").run(context -> assertThat(context).hasFailed());
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).withBean(BusinessFileStore.class, FileServiceConfigurationTest::bucket)
                .withPropertyValues("forcitis.platform.file.store.download-link-maximum-validity=PT8M", "forcitis.platform.file.store.download-link-validity=PT10M")
                .run(context -> assertThat(context).hasFailed());
        runner().withBean(MalwareScanner.class, () -> mock(MalwareScanner.class)).withBean(BusinessFileStore.class, FileServiceConfigurationTest::bucket)
                .withPropertyValues("forcitis.platform.file.store.download-link-validity=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void offStoresNothing() {
        new ApplicationContextRunner().withUserConfiguration(FileServiceConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(StoreContent.class).doesNotHaveBean(PlatformFileContent.class)
                        .doesNotHaveBean(UploadFile.class));
    }
}
