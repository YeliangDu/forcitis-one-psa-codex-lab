package com.forcitis.one.platform.file.infrastructure;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.file.application.BindFile;
import com.forcitis.one.platform.file.application.FileKeys;
import com.forcitis.one.platform.file.application.HoldFile;
import com.forcitis.one.platform.file.application.IssueDownloadLink;
import com.forcitis.one.platform.file.application.ListDocumentFiles;
import com.forcitis.one.platform.file.application.ReadFileContent;
import com.forcitis.one.platform.file.application.StoreContent;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.ContentChecks;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import com.forcitis.one.platform.file.domain.StoreAudit;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.MalwareScanner;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The scanner and the bucket are plain constructor dependencies, never optional: with the file service on and either missing, the
 * application does not start, so a switched-off scanner can never mean unscanned files (ADR-0060 section 2, unit 4 security review).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileServiceProperties.class)
@ConditionalOnProperty(prefix = "forcitis.platform.file.store", name = "enabled", havingValue = "true")
class FileServiceConfiguration {

    @Bean
    LocalScanStaging scanStaging(FileServiceProperties properties, Clock clock) {
        return new LocalScanStaging(properties.stagingRoot(), properties.maxFileSize().toBytes(), clock);
    }

    @Bean
    StoreContent storeContent(LocalScanStaging staging, MalwareScanner scanner, ContentChecks checks, BusinessFileStore store,
                              StoredFileRepository files, StoreAudit audit, FileServiceProperties properties, IdGenerator ids, Clock clock,
                              PlatformTransactionManager transactions, MeterRegistry meters) {
        return new StoreContent(staging, scanner, checks, store, files, audit, new FileKeys(properties.environmentPrefix()),
                new HashSet<>(properties.allowedMediaTypes()), ids, clock, transactions, meters);
    }

    @Bean
    BindFile bindFile(FileBindingRepository bindings, BusinessFileStore store, BindingAudit audit, FileServiceProperties properties, Clock clock,
                      BusinessZone zone, PlatformTransactionManager transactions, MeterRegistry meters) {
        return new BindFile(bindings, store, audit, new FileKeys(properties.environmentPrefix()), clock, zone, transactions, meters);
    }

    @Bean
    HoldFile holdFile(FileBindingRepository bindings, BindingAudit audit, Clock clock, PlatformTransactionManager transactions) {
        return new HoldFile(bindings, audit, clock, transactions);
    }

    @Bean
    IssueDownloadLink issueDownloadLink(FileBindingRepository bindings, BusinessFileStore store, BindingAudit audit, FileServiceProperties properties,
                                        Clock clock) {
        if (properties.downloadLinkMaximumValidity().compareTo(store.maxLinkValidity()) > 0) {
            throw new IllegalStateException(FileServiceProperties.PREFIX + "download-link-maximum-validity exceeds what the business file bucket signs ("
                    + store.maxLinkValidity() + ")");
        }
        return new IssueDownloadLink(bindings, store, audit, properties.downloadLinkValidity(), properties.downloadLinkMaximumValidity(), clock);
    }

    @Bean
    ListDocumentFiles listDocumentFiles(FileBindingRepository bindings) {
        return new ListDocumentFiles(bindings);
    }

    @Bean
    ReadFileContent readFileContent(FileBindingRepository bindings, BusinessFileStore store, BindingAudit audit, MeterRegistry meters) {
        return new ReadFileContent(bindings, store, audit, meters);
    }

    /**
     * The staging sweep runs on every instance by design and without a cluster lock (ADR-0070 section 3 item 3), so it is scheduled here
     * rather than as a ShedLock job.
     */
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService scanStagingSweep(LocalScanStaging staging, FileServiceProperties properties) {
        ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("platform-file-staging-sweep").factory());
        long every = properties.stagingSweepInterval().toMillis();
        sweeper.scheduleWithFixedDelay(() -> staging.sweep(properties.stagingSweepAge()), every, every, TimeUnit.MILLISECONDS);
        return sweeper;
    }
}
