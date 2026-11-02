package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.ChangeEmployeeProfile;
import com.forcitis.one.organization.workforce.api.Gender;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The whole new profile: a field left out is cleared (replace, not patch). There is no employee code; a later effectiveDate is refused. */
record ChangeEmployeeProfileRequest(@Nullable String displayName, @Nullable Gender gender, @Nullable String email, @Nullable String wecomUserId,
                                    @Nullable LocalDate effectiveDate) {

    ChangeEmployeeProfile toCommand(UUID employeeId) {
        if (displayName == null) {
            throw new InvalidRequestException("displayName is required");
        }
        if (wecomUserId != null && wecomUserId.isBlank()) {
            throw new InvalidRequestException("wecomUserId must not be blank; leave it out to have none");
        }
        return new ChangeEmployeeProfile(new EmployeeId(employeeId), displayName, gender, email, wecomUserId == null ? null : new WecomUserId(wecomUserId), effectiveDate);
    }
}
