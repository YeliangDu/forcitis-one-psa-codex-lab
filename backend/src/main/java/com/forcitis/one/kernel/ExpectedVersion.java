package com.forcitis.one.kernel;

/**
 * The version a write is based on, from If-Match (a number in quotes, the ETag the GET returned) or, for a resource that does not
 * exist yet, If-None-Match: *. Without either the write is refused with 428; a different version with 412.
 */
@ValueObject
public record ExpectedVersion(long value, boolean mustNotExist) {

    public static ExpectedVersion of(long value) {
        return new ExpectedVersion(value, false);
    }

    public static ExpectedVersion absent() {
        return new ExpectedVersion(0, true);
    }

    public static ExpectedVersion fromHeaders(String ifMatch, String ifNoneMatch) {
        if (ifMatch != null && !ifMatch.isBlank()) {
            String text = ifMatch.trim();
            if (text.startsWith("W/")) {
                text = text.substring(2);
            }
            if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length() - 1);
            }
            try {
                return of(Long.parseLong(text));
            } catch (NumberFormatException malformed) {
                throw new VersionMismatchException("If-Match is not a version this server issued");
            }
        }
        if (ifNoneMatch != null && ifNoneMatch.trim().equals("*")) {
            return absent();
        }
        throw new VersionRequiredException("the write must carry If-Match with the version of the resource");
    }

    /** actual is the stored version, or null when the resource does not exist yet. */
    public void check(Long actual) {
        boolean ok = mustNotExist ? actual == null : actual != null && actual == value;
        if (!ok) {
            throw new VersionMismatchException("the resource is not at the version the write is based on");
        }
    }

    public static String etag(long version) {
        return "\"" + version + "\"";
    }
}
