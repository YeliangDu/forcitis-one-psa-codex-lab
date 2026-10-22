package com.forcitis.one.platform.file.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.PlatformFileContent;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.BoundContent;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The read is audited before the first byte goes out. The hash is known only at the end, so a mismatch fails the read after the sink has had
 * the bytes: the caller discards what it received whenever this throws. A failing sink (the reader went away) surfaces as an
 * UncheckedIOException, not as a storage fault.
 */
public final class ReadFileContent implements PlatformFileContent {
    private static final Logger LOG = LoggerFactory.getLogger(ReadFileContent.class);

    private final FileBindingRepository bindings;
    private final BusinessFileStore store;
    private final BindingAudit audit;
    private final Counter mismatches;

    public ReadFileContent(FileBindingRepository bindings, BusinessFileStore store, BindingAudit audit, MeterRegistry meters) {
        this.bindings = bindings;
        this.store = store;
        this.audit = audit;
        this.mismatches = Counter.builder("forcitis.platform.file.content.hash.mismatches").register(meters);
    }

    @Override
    public void readContent(FileId file, DocumentReference document, FileSubject requester, OutputStream sink) {
        StoreContent.requireSubject(requester);
        BoundContent content = bindings.boundContent(file, document)
                .orElseThrow(() -> FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT.refusal("the file is not linked to this document"));
        Availability.require(content.status());
        audit.contentRead(file, document, requester);
        HashingSink hashing = new HashingSink(sink);
        store.read(new StoredObjectKey(content.objectKey()), hashing);
        if (hashing.count != content.size() || !hashing.hex().equals(content.contentHash().value())) {
            LOG.error("ALERT stored file {} no longer matches its SHA-256", file);
            mismatches.increment();
            audit.hashMismatch(file, document, requester, content.contentHash());
            throw FileError.PLATFORM_FILE_HASH_MISMATCH.refusal("the stored content does not match its hash; discard what was read");
        }
    }

    private static final class HashingSink extends FilterOutputStream {
        private final MessageDigest digest;
        private long count;

        HashingSink(OutputStream sink) {
            super(sink);
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void write(int b) throws IOException {
            try {
                out.write(b);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            digest.update((byte) b);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                out.write(bytes, offset, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            digest.update(bytes, offset, length);
            count += length;
        }

        String hex() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
