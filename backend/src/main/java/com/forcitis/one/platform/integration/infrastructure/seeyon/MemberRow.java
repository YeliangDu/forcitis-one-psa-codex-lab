package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.SourceGender;

/** A person as the view has it, under the source's own ids. */
record MemberRow(long memberId, String code, String name, long unitId, Long postId, Long supervisorId, String loginName, String email,
                 SourceGender gender, WecomUserId wecomUserId, boolean feeEarner, boolean internalFlagMissing) {
}
