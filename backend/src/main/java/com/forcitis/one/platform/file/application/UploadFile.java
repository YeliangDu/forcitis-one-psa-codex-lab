package com.forcitis.one.platform.file.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.api.StoreRequest;
import com.forcitis.one.platform.file.api.StoreResult;
import java.io.InputStream;

/** An upload through the web belongs to the signed-in person who made it; only that person sees its scan state (ADR-0060 section 6 item 1). */
public final class UploadFile {
    private final StoreContent store;
    private final CurrentUser currentUser;

    public UploadFile(StoreContent store, CurrentUser currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public StoreResult execute(InputStream content, String originalName, String declaredMediaType) {
        FileSubject uploader = new FileSubject(FileSubjectKind.USER, currentUser.userId().value().toString());
        return store.store(new StoreRequest(content, originalName, declaredMediaType, uploader, new Lineage.FirstVersion()));
    }
}
