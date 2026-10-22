package com.forcitis.one.platform.integration.infrastructure.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class OssBusinessFileStoreTest {
    static final String ID = "0199f0c4-0000-7000-8000-000000000001";
    static final StoredObjectKey STAGING = new StoredObjectKey("dev/staging/user/" + ID);
    static final StoredObjectKey FORMAL = new StoredObjectKey("dev/files/2026/10/" + ID);
    static final StoredObjectKey QUARANTINE = new StoredObjectKey("dev/quarantine/" + ID);
    static final String MD5 = "9a0364b9e99bb480dd25e1f0284c8555";
    static final String SHA = "ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73";

    private static com.aliyun.oss.model.PutObjectResult putResult(String etag) {
        com.aliyun.oss.model.PutObjectResult result = new com.aliyun.oss.model.PutObjectResult();
        result.setETag("\"" + etag.toUpperCase() + "\"");
        return result;
    }

    @TempDir
    Path directory;

    private final OSS writer = mock(OSS.class);
    private final OSS signer = mock(OSS.class);
    private final OSS quarantineReader = mock(OSS.class);
    private final BusinessFileStoreProperties properties = new BusinessFileStoreProperties(true, "files-dev", "cn-hangzhou", "dev", true,
            Duration.ofMinutes(15), new BusinessFileStoreProperties.Identity(null, null, "file-writer"),
            new BusinessFileStoreProperties.Identity("signer-key", "signer-secret", null),
            new BusinessFileStoreProperties.Identity("reader-key", "reader-secret", null));
    private final OssBusinessFileStore store = new OssBusinessFileStore(writer, signer, quarantineReader, properties,
            Clock.fixed(Instant.parse("2026-10-21T02:00:00Z"), ZoneOffset.UTC));

    private static ObjectMetadata stored(String md5, String sha, long length) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setHeader("ETag", "\"" + md5.toUpperCase() + "\"");
        metadata.setHeader("Content-Length", length);
        metadata.addUserMetadata(OssBusinessFileStore.SHA256, sha);
        return metadata;
    }

    @Test
    void putWritesWithoutOverwriteThenReadsBack() throws Exception {
        Path file = Files.writeString(directory.resolve("f"), "content");
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenReturn(stored(MD5, SHA, 7));

        store.put(STAGING, file, SHA, 7);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(writer).putObject(request.capture());
        assertThat(request.getValue().getMetadata().getRawMetadata()).containsEntry("x-oss-forbid-overwrite", "true");
        assertThat(request.getValue().getMetadata().getUserMetadata()).containsEntry("sha256", SHA);
        verifyNoInteractions(signer, quarantineReader);
    }

    @Test
    void aReadBackThatDiffersFails() throws Exception {
        Path file = Files.writeString(directory.resolve("f"), "content");
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenReturn(stored(MD5, "0".repeat(64), 7));
        assertThatThrownBy(() -> store.put(STAGING, file, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    void aStagedFileThatChangedAfterTheScanIsNotWritten() throws Exception {
        Path file = Files.writeString(directory.resolve("f"), "swapped");
        assertThatThrownBy(() -> store.put(STAGING, file, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class)
                .hasMessageContaining("changed after it was scanned");
        verify(writer, never()).putObject(any(PutObjectRequest.class));
    }

    @Test
    void quarantineIsConfirmedByTheWritesETagWithoutReadingIt() throws Exception {
        Path file = Files.writeString(directory.resolve("f"), "content");
        when(writer.putObject(any(PutObjectRequest.class))).thenReturn(putResult(MD5));
        store.put(QUARANTINE, file, SHA, 7);
        verify(writer, never()).getObjectMetadata(any(String.class), any(String.class));
        when(writer.putObject(any(PutObjectRequest.class))).thenReturn(putResult("0".repeat(32)));
        assertThatThrownBy(() -> store.put(QUARANTINE, file, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    void contentReachesAFormalKeyByPromoteOnly() throws Exception {
        Path file = Files.writeString(directory.resolve("f"), "content");
        assertThatThrownBy(() -> store.put(FORMAL, file, SHA, 7)).isInstanceOf(IllegalArgumentException.class);
        verify(writer, never()).putObject(any(PutObjectRequest.class));
    }

    @Test
    void promoteCopiesWithoutOverwriteToTheSameFilesFormalKey() {
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenReturn(stored(MD5, SHA, 7));
        when(writer.getObjectMetadata("files-dev", FORMAL.value())).thenReturn(stored(MD5, SHA, 7));

        store.promote(STAGING, FORMAL, SHA, 7);

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(writer).copyObject(copy.capture());
        assertThat(copy.getValue().getHeaders()).containsEntry("x-oss-forbid-overwrite", "true");
        assertThatThrownBy(() -> store.promote(STAGING, new StoredObjectKey("dev/files/2026/10/0199f0c4-0000-7000-8000-000000000002"), SHA, 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.promote(QUARANTINE, FORMAL, SHA, 7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anExistingFormalObjectIsNeverOverwrittenAndCountsAsDoneOnlyWithTheSameContent() {
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenReturn(stored(MD5, SHA, 7));
        com.aliyun.oss.OSSException exists = new com.aliyun.oss.OSSException("exists", "FileAlreadyExists", null, null, null, null, null);
        when(writer.copyObject(any(CopyObjectRequest.class))).thenThrow(exists);
        when(writer.getObjectMetadata("files-dev", FORMAL.value())).thenReturn(stored(MD5, SHA, 7));
        store.promote(STAGING, FORMAL, SHA, 7);
        when(writer.getObjectMetadata("files-dev", FORMAL.value())).thenReturn(stored("1".repeat(32), "1".repeat(64), 9));
        assertThatThrownBy(() -> store.promote(STAGING, FORMAL, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    void aRetryAfterTheStagingObjectExpiredCountsAsDoneWhenTheFormalObjectIsIntact() {
        com.aliyun.oss.OSSException gone = new com.aliyun.oss.OSSException("gone", "NoSuchKey", null, null, null, null, null);
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenThrow(gone);
        when(writer.getObjectMetadata("files-dev", FORMAL.value())).thenReturn(stored(MD5, SHA, 7));

        store.promote(STAGING, FORMAL, SHA, 7);

        verify(writer, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void aRetryAfterTheStagingObjectExpiredFailsWhenTheFormalObjectIsMissingOrDiffers() {
        com.aliyun.oss.OSSException gone = new com.aliyun.oss.OSSException("gone", "NoSuchKey", null, null, null, null, null);
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenThrow(gone);
        when(writer.getObjectMetadata("files-dev", FORMAL.value())).thenThrow(gone);
        assertThatThrownBy(() -> store.promote(STAGING, FORMAL, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class);
        org.mockito.Mockito.doReturn(stored("1".repeat(32), "1".repeat(64), 9)).when(writer).getObjectMetadata("files-dev", FORMAL.value());
        assertThatThrownBy(() -> store.promote(STAGING, FORMAL, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class);
    }

    @Test
    void anErrorOtherThanNoSuchKeyOnTheStagingObjectDoesNotTakeTheRetryShortcut() {
        com.aliyun.oss.OSSException denied = new com.aliyun.oss.OSSException("denied", "AccessDenied", null, null, null, null, null);
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenThrow(denied);
        assertThatThrownBy(() -> store.promote(STAGING, FORMAL, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class);
        verify(writer, never()).getObjectMetadata("files-dev", FORMAL.value());
    }

    @Test
    void aFailingSinkPassesThroughReadAsThrown() {
        OSSObject object = new OSSObject();
        object.setObjectContent(new java.io.ByteArrayInputStream("content".getBytes()));
        when(writer.getObject("files-dev", FORMAL.value())).thenReturn(object);
        java.io.OutputStream broken = new java.io.OutputStream() {
            @Override
            public void write(int b) {
                throw new java.io.UncheckedIOException(new java.io.IOException("reader went away"));
            }
        };
        assertThatThrownBy(() -> store.read(FORMAL, broken)).isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    void maxLinkValidityIsThePropertiesCeiling() {
        assertThat(store.maxLinkValidity()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void aStagingObjectThatDiffersIsNotPromoted() {
        when(writer.getObjectMetadata("files-dev", STAGING.value())).thenReturn(stored(MD5, "0".repeat(64), 7));
        assertThatThrownBy(() -> store.promote(STAGING, FORMAL, SHA, 7)).isInstanceOf(ExternalSystemUnavailableException.class);
        verify(writer, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void onlyFormalKeysArePresignedBySignerAsAttachment() throws Exception {
        when(signer.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(URI.create("https://files-dev.oss/x?sig").toURL());

        assertThat(store.presignDownload(FORMAL, Duration.ofMinutes(5), "合同 v2.pdf", "application/pdf")).hasHost("files-dev.oss");

        ArgumentCaptor<GeneratePresignedUrlRequest> request = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(signer).generatePresignedUrl(request.capture());
        assertThat(request.getValue().getResponseHeaders().getContentDisposition()).startsWith("attachment; ")
                .contains("filename*=UTF-8''%E5%90%88%E5%90%8C%20v2.pdf");
        assertThat(request.getValue().getResponseHeaders().getContentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().getExpiration().toInstant()).isEqualTo(Instant.parse("2026-10-21T02:05:00Z"));
        assertThatThrownBy(() -> store.presignDownload(STAGING, Duration.ofMinutes(5), "a", "a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.presignDownload(QUARANTINE, Duration.ofMinutes(5), "a", "a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.presignDownload(FORMAL, Duration.ofMinutes(16), "a", "a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.presignDownload(FORMAL, Duration.ofMinutes(5), "a", "text/html\r\nSet-Cookie: x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(OssBusinessFileStore.attachment("a*\"b\r\n.pdf")).doesNotContain("\r").doesNotContain("\n").contains("%2A").startsWith("attachment; filename=\"a__b__.pdf\"");
        verifyNoInteractions(writer, quarantineReader);
    }

    @Test
    void readUsesTheIdentityOfTheKeysAreaAndRefusesStaging() {
        OSSObject object = new OSSObject();
        object.setObjectContent(new ByteArrayInputStream("content".getBytes()));
        when(quarantineReader.getObject("files-dev", QUARANTINE.value())).thenReturn(object);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        store.read(QUARANTINE, sink);

        assertThat(sink.toString()).isEqualTo("content");
        verifyNoInteractions(writer, signer);
        assertThatThrownBy(() -> store.read(STAGING, sink)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.read(new StoredObjectKey("prod/files/2026/10/" + ID), sink)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.read(new StoredObjectKey("dev/files/2026/13/" + ID), sink)).isInstanceOf(IllegalArgumentException.class);
    }
}
