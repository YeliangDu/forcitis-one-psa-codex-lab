package com.forcitis.one.platform.integration.infrastructure.filestore;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Without this bean the file service cannot start; tests bring an in-memory store. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BusinessFileStoreProperties.class)
@ConditionalOnProperty(prefix = "forcitis.platform.integration.business-file-store", name = "enabled", havingValue = "true")
class BusinessFileStoreConfiguration {

    @Bean
    OssBusinessFileStore businessFileStore(BusinessFileStoreProperties properties, Clock clock) {
        OSS writer = new OSSClientBuilder().build(properties.endpoint(), properties.writer().credentials("writer"));
        OSS signer = new OSSClientBuilder().build(properties.endpoint(), properties.signer().credentials("signer"));
        OSS quarantineReader = new OSSClientBuilder().build(properties.endpoint(), properties.quarantineReader().credentials("quarantine reader"));
        return new OssBusinessFileStore(writer, signer, quarantineReader, properties, clock);
    }
}
