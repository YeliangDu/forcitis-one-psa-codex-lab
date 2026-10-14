package com.forcitis.one.kernel;

/**
 * The version a write is based on, from If-Match (a strong tag in quotes: the ETag the GET returned). A resource that is not stored yet
 * is at version 0, so its first write is sent with If-Match: "0". Without the header the write is refused with 428, with a weak or
 * unreadable tag with 400, and with a different version with 412.
 */
@ValueObject
public record ExpectedVersion(long value) {

    public static ExpectedVersion of(long value) {
        return new ExpectedVersion(value);
    }

    public static ExpectedVersion fromHeader(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new VersionRequiredException("the write must carry If-Match with the version of the resource");
        }
        String text = ifMatch.trim();
        if (text.length() < 2 || !text.startsWith("\"") || !text.endsWith("\"")) {
            throw new VersionMalformedException("If-Match must be a strong entity tag in quotes, as the ETag of the resource");
        }
        try {
            long value = Long.parseLong(text.substring(1, text.length() - 1));
            if (value < 0) {
                throw new NumberFormatException(text);
            }
            return of(value);
        } catch (NumberFormatException malformed) {
            throw new VersionMalformedException("If-Match is not a version this server issued");
        }
    }

    /** actual is the stored version, or null when the resource does not exist yet. */
    public void check(Long actual) {
        if ((actual == null ? 0 : actual) != value) {
            throw new VersionMismatchException("the resource is not at the version the write is based on");
        }
    }

    public static String etag(long version) {
        return "\"" + version + "\"";
    }
}
