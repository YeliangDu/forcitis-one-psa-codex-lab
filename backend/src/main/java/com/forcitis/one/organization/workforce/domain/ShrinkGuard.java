package com.forcitis.one.organization.workforce.domain;

/**
 * Shrink protection (RULE-358/366): when the source returns clearly fewer active org units or employed people than the
 * snapshot holds, the run is aborted instead of mass-revoking and mass-separating. Integer arithmetic, no rounding.
 */
public final class ShrinkGuard {

    private ShrinkGuard() {
    }

    /** held = true means: do not write, record the run as HELD and alert. */
    public record Verdict(boolean held, String reason) {
    }

    /**
     * @param maxDropPercent largest allowed drop, e.g. 20: incoming * 100 &lt; previous * (100 - 20) holds the run;
     *                       a previous count of 0 (first run) never holds
     */
    public static Verdict evaluate(RunStats stats, int maxDropPercent) {
        StringBuilder reason = new StringBuilder();
        if (shrunk(stats.incomingActiveOrgUnitCount(), stats.previousActiveOrgUnitCount(), maxDropPercent)) {
            reason.append("active org units ").append(stats.previousActiveOrgUnitCount()).append(" -> ")
                    .append(stats.incomingActiveOrgUnitCount()).append(" (drop over ").append(maxDropPercent).append("%)");
        }
        if (shrunk(stats.incomingEmployedCount(), stats.previousEmployedCount(), maxDropPercent)) {
            if (!reason.isEmpty()) {
                reason.append("; ");
            }
            reason.append("employed people ").append(stats.previousEmployedCount()).append(" -> ")
                    .append(stats.incomingEmployedCount()).append(" (drop over ").append(maxDropPercent).append("%)");
        }
        return new Verdict(!reason.isEmpty(), reason.toString());
    }

    private static boolean shrunk(int incoming, int previous, int maxDropPercent) {
        return previous > 0 && (long) incoming * 100 < (long) previous * (100 - maxDropPercent);
    }
}
