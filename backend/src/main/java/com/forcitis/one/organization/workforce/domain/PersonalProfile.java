package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** What the employee root holds about the person besides the code: the limits are those of the table's checks (ck_employees_display_name, ck_employees_email_address). */
public record PersonalProfile(String displayName, @Nullable Gender gender, @Nullable String email, @Nullable WecomUserId wecomUserId) {
    public static final int MAX_DISPLAY_NAME_LENGTH = 500;
    public static final int MAX_EMAIL_LENGTH = 100;

    public PersonalProfile {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw invalid("the name is required and at most " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        if (email != null && (email.isBlank() || email.length() > MAX_EMAIL_LENGTH)) {
            throw invalid("the email is at most " + MAX_EMAIL_LENGTH + " characters and not blank; leave it out to have none");
        }
    }

    /** The changed columns as before and after text for the audit, or empty when nothing differs. The texts name the columns that changed and nothing else. */
    public Optional<Change> changeFrom(PersonalProfile before) {
        List<String> was = new ArrayList<>();
        List<String> now = new ArrayList<>();
        note(was, now, "display_name", before.displayName, displayName);
        note(was, now, "gender", before.gender, gender);
        note(was, now, "email", before.email, email);
        note(was, now, "wecom_user_id", before.wecomUserId, wecomUserId);
        return was.isEmpty() ? Optional.empty() : Optional.of(new Change(String.join("; ", was), String.join("; ", now)));
    }

    private static void note(List<String> was, List<String> now, String column, @Nullable Object before, @Nullable Object after) {
        if (!Objects.equals(before, after)) {
            was.add(column + "=" + (before == null ? "(none)" : before));
            now.add(column + "=" + (after == null ? "(none)" : after));
        }
    }

    public record Change(String beforeText, String afterText) {
    }

    private static DomainException invalid(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_PROFILE_INVALID, ErrorKind.UNPROCESSABLE, message);
    }
}
