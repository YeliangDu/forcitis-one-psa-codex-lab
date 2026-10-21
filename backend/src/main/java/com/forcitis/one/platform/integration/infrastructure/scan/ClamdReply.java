package com.forcitis.one.platform.integration.infrastructure.scan;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ScanOutcome;
import com.forcitis.one.platform.integration.api.ScanVerdict;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Reads clamd's answers to zVERSION and zSCAN; anything that is not a decision is unavailable (ADR-0070 section 2 item 6). A reply of
 * several lines (AllMatchScan, which clamd.conf keeps off) is unavailable too, so one verdict never hides another.
 */
final class ClamdReply {
    private static final DateTimeFormatter BUILD_TIME = DateTimeFormatter.ofPattern("MMM d HH:mm:ss yyyy", Locale.ROOT);

    record Definitions(String engineVersion, String definitionsVersion, Instant builtAt) {

        void requireFresh(Clock clock, Duration maxAge) {
            Duration age = Duration.between(builtAt, clock.instant());
            if (age.compareTo(Duration.ofMinutes(-10)) < 0) {
                throw new ExternalSystemUnavailableException("the malware definitions claim a build time in the future; check clamd-zone");
            }
            if (age.compareTo(maxAge) > 0) {
                throw new ExternalSystemUnavailableException("the malware definitions " + definitionsVersion + " are " + age.toHours()
                        + " hours old, more than " + maxAge.toHours());
            }
        }
    }

    private ClamdReply() {
    }

    /** "ClamAV 1.4.1/27435/Tue Oct 21 08:23:45 2026"; the build time is in the zone of the clamd host, the weekday is skipped. */
    static Definitions version(String reply, ZoneId clamdZone) {
        String[] parts = reply.split("/", 3);
        if (parts.length != 3 || !parts[0].startsWith("ClamAV ") || parts[1].isBlank()) {
            throw new ExternalSystemUnavailableException("clamd reported no loaded definitions: " + reply);
        }
        try {
            String[] words = parts[2].trim().replaceAll(" +", " ").split(" ", 2);
            LocalDateTime built = LocalDateTime.parse(words.length == 2 ? words[1] : "", BUILD_TIME);
            return new Definitions(parts[0].trim(), parts[1].trim(), built.atZone(clamdZone).toInstant());
        } catch (DateTimeParseException e) {
            throw new ExternalSystemUnavailableException("clamd reported an unreadable definitions date: " + reply, e);
        }
    }

    static ScanVerdict scan(String path, String reply, Definitions definitions) {
        String prefix = path + ": ";
        if (reply.indexOf('\n') >= 0 || !reply.startsWith(prefix)) {
            throw new ExternalSystemUnavailableException("clamd answered for another file: " + reply);
        }
        String result = reply.substring(prefix.length());
        if (result.equals("OK")) {
            return new ScanVerdict(ScanOutcome.CLEAN, null, definitions.engineVersion(), definitions.definitionsVersion());
        }
        if (result.endsWith(" FOUND")) {
            String signature = result.substring(0, result.length() - " FOUND".length()).trim();
            if (signature.isEmpty() || signature.startsWith("Heuristics.Limits.Exceeded")) {
                throw new ExternalSystemUnavailableException("clamd could not finish the scan: " + result);
            }
            if (signature.startsWith("Heuristics.Encrypted.")) {
                return new ScanVerdict(ScanOutcome.ENCRYPTED, null, definitions.engineVersion(), definitions.definitionsVersion());
            }
            return new ScanVerdict(ScanOutcome.INFECTED, signature, definitions.engineVersion(), definitions.definitionsVersion());
        }
        throw new ExternalSystemUnavailableException("clamd could not scan the file: " + result);
    }
}
