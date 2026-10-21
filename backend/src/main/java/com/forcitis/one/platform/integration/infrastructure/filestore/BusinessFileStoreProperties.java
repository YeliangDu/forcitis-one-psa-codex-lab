package com.forcitis.one.platform.integration.infrastructure.filestore;

import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyuncs.exceptions.ClientException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The business file bucket (ADR-0060 section 6): mainland China region, one bucket per environment, keys under environmentPrefix. Three
 * RAM identities that must differ (ADR-0070 section 7): the writer puts, copies and reads formal keys; the signer only presigns downloads
 * and is denied staging and quarantine; the quarantine reader only reads quarantine. The bucket and identities are created by the user and
 * never appear in the repository. Off until the file service is wired; once on, every value is required.
 */
@ConfigurationProperties("forcitis.platform.integration.business-file-store")
record BusinessFileStoreProperties(@DefaultValue("false") boolean enabled, String bucket, String region, String environmentPrefix,
                                   @DefaultValue("true") boolean internalEndpoint, Duration maxLinkValidity, Identity writer,
                                   Identity signer, Identity quarantineReader) {

    static final String PREFIX = "forcitis.platform.integration.business-file-store.";
    static final Set<String> MAINLAND_REGIONS = Set.of("cn-qingdao", "cn-beijing", "cn-zhangjiakou", "cn-huhehaote", "cn-wulanchabu",
            "cn-hangzhou", "cn-shanghai", "cn-nanjing", "cn-fuzhou", "cn-shenzhen", "cn-heyuan", "cn-guangzhou", "cn-chengdu");

    /** An AccessKey pair, or the ECS instance RAM role (one identity at most can use it). */
    record Identity(String accessKeyId, String accessKeySecret, String ecsRoleName) {

        CredentialsProvider credentials(String which) {
            try {
                if (!blank(accessKeyId)) {
                    return CredentialsProviderFactory.newDefaultCredentialProvider(accessKeyId, accessKeySecret);
                }
                return CredentialsProviderFactory.newInstanceProfileCredentialsProvider(ecsRoleName);
            } catch (ClientException e) {
                throw new IllegalStateException("no credentials for the business file store " + which, e);
            }
        }

        String principal() {
            return blank(accessKeyId) ? "role:" + ecsRoleName : "key:" + accessKeyId;
        }

        @Override
        public String toString() {
            return "Identity[" + (blank(accessKeyId) ? "ecsRoleName=" + ecsRoleName : "accessKeyId=***") + "]";
        }
    }

    BusinessFileStoreProperties {
        if (enabled) {
            require(bucket, "bucket");
            require(environmentPrefix, "environment-prefix");
            if (!environmentPrefix.matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException(PREFIX + "environment-prefix is lower-case letters, digits and hyphens");
            }
            if (region == null || !MAINLAND_REGIONS.contains(region)) {
                throw new IllegalArgumentException(PREFIX + "region must be a mainland China region " + MAINLAND_REGIONS + ", was " + region);
            }
            if (maxLinkValidity == null || maxLinkValidity.isNegative() || maxLinkValidity.isZero() || maxLinkValidity.compareTo(Duration.ofMinutes(15)) > 0) {
                throw new IllegalArgumentException(PREFIX + "max-link-validity is required and at most 15 minutes (ADR-0060 section 2)");
            }
            identity(writer, "writer");
            identity(signer, "signer");
            identity(quarantineReader, "quarantine-reader");
            if (new HashSet<>(List.of(writer.principal(), signer.principal(), quarantineReader.principal())).size() != 3) {
                throw new IllegalArgumentException(PREFIX + "writer, signer and quarantine-reader must be three different identities (ADR-0070 section 7)");
            }
        }
    }

    String endpoint() {
        return "https://oss-" + region + (internalEndpoint ? "-internal" : "") + ".aliyuncs.com";
    }

    /** Download links are opened by browsers, so they are signed for the public endpoint. */
    String publicEndpoint() {
        return "https://oss-" + region + ".aliyuncs.com";
    }

    private static void identity(Identity identity, String key) {
        if (identity == null || blank(identity.accessKeyId()) == blank(identity.ecsRoleName())
                || blank(identity.accessKeyId()) != blank(identity.accessKeySecret())) {
            throw new IllegalArgumentException(PREFIX + key + " needs either access-key-id with access-key-secret, or ecs-role-name");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void require(String value, String key) {
        if (blank(value)) {
            throw new IllegalArgumentException(PREFIX + key + " is required");
        }
    }
}
