package com.forcitis.one.platform.integration.infrastructure.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.integration.infrastructure.filestore.BusinessFileStoreProperties.Identity;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BusinessFileStorePropertiesTest {
    static final Identity WRITER = new Identity(null, null, "file-writer");
    static final Identity SIGNER = new Identity("signer-key", "signer-secret", null);
    static final Identity READER = new Identity("reader-key", "reader-secret", null);
    static final Duration LINK = Duration.ofMinutes(15);

    @Test
    void offNeedsNothing() {
        assertThat(new BusinessFileStoreProperties(false, null, null, null, true, null, null, null, null).enabled()).isFalse();
    }

    @Test
    void onNeedsAMainlandBucketAndThreeDifferentIdentities() {
        assertThat(new BusinessFileStoreProperties(true, "b", "cn-hangzhou", "dev", true, LINK, WRITER, SIGNER, READER).endpoint())
                .isEqualTo("https://oss-cn-hangzhou-internal.aliyuncs.com");
        assertThatThrownBy(() -> new BusinessFileStoreProperties(true, "b", "ap-southeast-1", "dev", true, LINK, WRITER, SIGNER, READER))
                .hasMessageContaining("mainland");
        assertThatThrownBy(() -> new BusinessFileStoreProperties(true, "b", "cn-hangzhou", "dev", true, Duration.ofMinutes(16), WRITER, SIGNER, READER))
                .hasMessageContaining("15 minutes");
        assertThatThrownBy(() -> new BusinessFileStoreProperties(true, "b", "cn-hangzhou", "dev", true, LINK, WRITER, SIGNER, SIGNER))
                .hasMessageContaining("three different identities");
        assertThatThrownBy(() -> new BusinessFileStoreProperties(true, "b", "cn-hangzhou", "dev", true, LINK, WRITER, null, READER))
                .hasMessageContaining("signer");
        assertThatThrownBy(() -> new BusinessFileStoreProperties(true, "b", "cn-hangzhou", "Dev/", true, LINK, WRITER, SIGNER, READER))
                .hasMessageContaining("environment-prefix");
    }

    @Test
    void secretsDoNotPrint() {
        assertThat(SIGNER.toString()).doesNotContain("signer-key").doesNotContain("signer-secret");
        assertThat(new BusinessFileStoreProperties(true, "b", "cn-hangzhou", "dev", true, LINK, WRITER, SIGNER, READER).toString())
                .doesNotContain("signer-secret").doesNotContain("reader-secret");
    }
}
