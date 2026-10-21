package com.forcitis.one;

import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;

/** Outside the platform package on purpose: PlatformFileContentCallersTest must catch it. */
class ProbePlatformModuleSubject {
    FileSubject subject = new FileSubject(FileSubjectKind.PLATFORM_MODULE, "platform.signing");
}
