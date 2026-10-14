package com.forcitis.one.platform.integration.api;

/** What the object store confirmed about an archived object: its key, size and SHA-256 (lower-case hex) as it holds them. */
public record ArchivedObject(String key, long size, String sha256) {
}
