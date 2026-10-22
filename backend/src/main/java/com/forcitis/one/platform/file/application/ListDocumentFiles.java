package com.forcitis.one.platform.file.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.file.api.StoredFileSummary;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import java.util.List;

public final class ListDocumentFiles {
    private final FileBindingRepository bindings;

    public ListDocumentFiles(FileBindingRepository bindings) {
        this.bindings = bindings;
    }

    public List<StoredFileSummary> list(DocumentReference document) {
        return bindings.listByDocument(document);
    }
}
