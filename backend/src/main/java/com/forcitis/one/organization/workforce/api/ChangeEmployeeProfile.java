package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** The whole new profile (replace, not patch); a null effectiveDate means today, a later one is refused. There is no employee code here. */
public record ChangeEmployeeProfile(EmployeeId employeeId, String displayName, @Nullable Gender gender, @Nullable String email,
                                    @Nullable WecomUserId wecomUserId, @Nullable LocalDate effectiveDate) {
}
