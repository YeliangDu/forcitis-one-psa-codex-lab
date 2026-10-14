package com.forcitis.one.platform.integration.infrastructure.archive;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.forcitis.one.platform.integration.api.ArchiveVault;
import com.forcitis.one.platform.integration.api.ObjectStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Off only where the archive jobs are off (tests, which bring their own ArchiveVault); otherwise a missing or wrong setting (bucket, region outside mainland China, keys) stops the start-up. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArchiveStoreProperties.class)
@ConditionalOnProperty(prefix = "forcitis.platform.integration.archive-store", name = "enabled", havingValue = "true", matchIfMissing = true)
class ArchiveStoreConfiguration {

    @Bean(destroyMethod = "shutdown")
    OSS archiveStoreClient(ArchiveStoreProperties properties) {
        return new OSSClientBuilder().build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret());
    }

    @Bean
    ObjectStore archiveObjectStore(OSS archiveStoreClient, ArchiveStoreProperties properties) {
        return new OssObjectStore(archiveStoreClient, properties);
    }

    @Bean
    ArchiveVault archiveVault(ObjectStore store, ArchiveStoreProperties properties, Clock clock) {
        return new ObjectStoreArchiveVault(store, properties.localDirectory(), properties.prefix(), clock);
    }
}
