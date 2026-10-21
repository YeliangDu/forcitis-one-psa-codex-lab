package com.forcitis.one.platform.file.api;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** The url is a credential until it expires: it goes to the requester only, never into logs or audit details. */
public record DownloadLink(URI url, Instant expiresAt) {

    public DownloadLink {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "DownloadLink[url=<hidden>, expiresAt=" + expiresAt + "]";
    }
}
