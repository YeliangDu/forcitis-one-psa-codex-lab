package com.forcitis.one.platform.file.infrastructure;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.file.application.FileKeys;
import com.forcitis.one.platform.file.application.StoreContent;
import com.forcitis.one.platform.file.domain.ContentChecks;
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
