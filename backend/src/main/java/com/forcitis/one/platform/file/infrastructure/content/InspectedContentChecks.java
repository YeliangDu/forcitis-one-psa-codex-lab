package com.forcitis.one.platform.file.infrastructure.content;

import com.forcitis.one.platform.file.domain.ContentChecks;
import com.forcitis.one.platform.file.domain.ContentFacts;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
class InspectedContentChecks implements ContentChecks {
    private final ContentInspector inspector = new ContentInspector();

    @Override
    public ContentFacts check(Path stagedFile) {
        ContentInspector.Inspection inspection = inspector.inspect(stagedFile);
        return new ContentFacts(inspection.mediaType(), inspection.encrypted(), inspection.containsMacros());
    }
}
