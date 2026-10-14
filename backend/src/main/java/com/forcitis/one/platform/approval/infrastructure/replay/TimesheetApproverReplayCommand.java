package com.forcitis.one.platform.approval.infrastructure.replay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;

/** No Spring, no database; exit status 1 when the report contains DEFECTs. */
final class TimesheetApproverReplayCommand {

    private TimesheetApproverReplayCommand() {
    }

    static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: TimesheetApproverReplayCommand <export.json> <output-directory>");
        }
        Path export = Path.of(args[0]);
        Path outputDirectory = Path.of(args[1]);
        JsonMapper mapper = ReplayExport.mapper();
        ReplayReport report = TimesheetApproverReplay.replay(mapper.readValue(Files.readString(export), ReplayExport.class));
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("replay-report.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        Files.writeString(outputDirectory.resolve("replay-report.md"), report.toMarkdown());
        if (report.hasDefects()) {
            throw new IllegalStateException(report.count(ReplayVerdict.DEFECT) + " unexplained difference(s); see "
                    + outputDirectory.resolve("replay-report.md"));
        }
    }
}
