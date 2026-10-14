package com.forcitis.one.platform.integration.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.GetBucketWormResult;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.StorageClass;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ArchivedObject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OssObjectStoreTest {
    @TempDir
    Path directory;

    private final OSS client = mock(OSS.class);
    private final ArchiveStoreProperties properties = new ArchiveStoreProperties("archive-bucket", "cn-hangzhou", "one", Path.of("/data/archive"), 3650, true,
            "key-id", "key-secret");
    private final OssObjectStore store = new OssObjectStore(client, properties);

    private ObjectMetadata stored(String sha, StorageClass storageClass, long length) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setHeader("x-oss-storage-class", storageClass.toString());
        metadata.setHeader("Content-Length", length);
        metadata.addUserMetadata(OssObjectStore.SHA256, sha);
        return metadata;
    }

    @Test
    @DisplayName("an object is written once with archive storage class, no overwrite, Content-MD5 and the SHA-256 as metadata, then read back")
    void putWritesAnImmutableArchiveObject() throws Exception {
        Path file = Files.writeString(directory.resolve("a.gz"), "content");
        when(client.getObjectMetadata("archive-bucket", "one/process/k")).thenReturn(stored("abc", StorageClass.Archive, 7));

        ArchivedObject result = store.put("one/process/k", file, "abc");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture());
        ObjectMetadata sent = request.getValue().getMetadata();
        assertThat(sent.getRawMetadata()).containsEntry("x-oss-storage-class", "Archive").containsEntry("x-oss-forbid-overwrite", "true");
        assertThat(sent.getContentMD5()).isNotBlank();
        assertThat(sent.getUserMetadata()).containsEntry("sha256", "abc");
        assertThat(result).isEqualTo(new ArchivedObject("one/process/k", 7, "abc"));
    }

    @Test
    @DisplayName("writing the same object again is accepted when it exists; the metadata read back decides whether it is the same")
    void anExistingObjectIsReadBack() throws Exception {
        Path file = Files.writeString(directory.resolve("a.gz"), "content");
        OSSException exists = new OSSException("exists", "FileAlreadyExists", "r", "h", null, null, null);
        when(client.putObject(any(PutObjectRequest.class))).thenThrow(exists);
        when(client.getObjectMetadata("archive-bucket", "k")).thenReturn(stored("abc", StorageClass.Archive, 7));

        assertThat(store.put("k", file, "abc").sha256()).isEqualTo("abc");
    }

    @Test
    @DisplayName("an object that is not stored as Archive, or a failing upload, is an error")
    void failures() throws Exception {
        Path file = Files.writeString(directory.resolve("a.gz"), "content");
        when(client.getObjectMetadata("archive-bucket", "k")).thenReturn(stored("abc", StorageClass.Standard, 7));
        assertThatThrownBy(() -> store.put("k", file, "abc")).isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("not Archive");

        when(client.putObject(any(PutObjectRequest.class))).thenThrow(new OSSException("denied", "AccessDenied", "r", "h", null, null, null));
        assertThatThrownBy(() -> store.put("k2", file, "abc")).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    @DisplayName("the probe checks region and a locked WORM period, and starts and aborts a multipart upload instead of leaving an object in a WORM bucket")
    void probeLeavesNothingBehind() {
        GetBucketWormResult worm = new GetBucketWormResult();
        worm.setWormState("Locked");
        worm.setRetentionPeriodInDays(3650);
        when(client.getBucketLocation("archive-bucket")).thenReturn("oss-cn-hangzhou");
        when(client.getBucketWorm("archive-bucket")).thenReturn(worm);
        InitiateMultipartUploadResult upload = new InitiateMultipartUploadResult();
        upload.setUploadId("u1");
        when(client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class))).thenReturn(upload);

        store.probe();

        verify(client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("the probe fails for another region, a WORM policy that is not locked or too short")
    void probeRefusesAWrongBucket() {
        GetBucketWormResult worm = new GetBucketWormResult();
        worm.setWormState("InProgress");
        worm.setRetentionPeriodInDays(3650);
        when(client.getBucketLocation("archive-bucket")).thenReturn("oss-eu-central-1");
        assertThatThrownBy(store::probe).hasMessageContaining("oss-eu-central-1");

        when(client.getBucketLocation("archive-bucket")).thenReturn("oss-cn-hangzhou");
        when(client.getBucketWorm("archive-bucket")).thenReturn(worm);
        assertThatThrownBy(store::probe).hasMessageContaining("Locked");
        worm.setWormState("Locked");
        worm.setRetentionPeriodInDays(365);
        assertThatThrownBy(store::probe).hasMessageContaining("3650");
    }

    @Configuration
    @EnableConfigurationProperties(ArchiveStoreProperties.class)
    static class Enabled {
    }

    private static final String[] COMPLETE = {"forcitis.platform.integration.archive-store.bucket=b", "forcitis.platform.integration.archive-store.region=cn-hangzhou",
            "forcitis.platform.integration.archive-store.prefix=one", "forcitis.platform.integration.archive-store.local-directory=/data/archive",
            "forcitis.platform.integration.archive-store.worm-lock-days=3650", "forcitis.platform.integration.archive-store.access-key-id=i",
            "forcitis.platform.integration.archive-store.access-key-secret=s"};

    @Test
    @DisplayName("the archive store configuration is complete and in mainland China: any missing value, Frankfurt or another foreign region stops the start-up")
    void configurationIsRequiredAndDomestic() {
        var runner = new ApplicationContextRunner().withUserConfiguration(Enabled.class);
        runner.withPropertyValues(COMPLETE).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ArchiveStoreProperties.class).endpoint()).isEqualTo("https://oss-cn-hangzhou-internal.aliyuncs.com");
        });
        for (int missing = 0; missing < COMPLETE.length; missing++) {
            String[] partial = new String[COMPLETE.length - 1];
            int at = 0;
            for (int i = 0; i < COMPLETE.length; i++) {
                if (i != missing) {
                    partial[at++] = COMPLETE[i];
                }
            }
            runner.withPropertyValues(partial).run(context -> assertThat(context).hasFailed());
        }
        for (String region : new String[] {"eu-central-1", "cn-hongkong", "us-west-1", ""}) {
            String[] foreign = COMPLETE.clone();
            foreign[1] = "forcitis.platform.integration.archive-store.region=" + region;
            runner.withPropertyValues(foreign).run(context -> assertThat(context).as(region).hasFailed());
        }
        String[] relative = COMPLETE.clone();
        relative[3] = "forcitis.platform.integration.archive-store.local-directory=archive";
        runner.withPropertyValues(relative).run(context -> assertThat(context).hasFailed());
    }
}
