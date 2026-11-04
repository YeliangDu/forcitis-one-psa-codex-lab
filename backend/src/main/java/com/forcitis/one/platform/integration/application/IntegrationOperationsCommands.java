package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ExternalSubjectEmployees;
import com.forcitis.one.kernel.UserId;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * The read-only operations command of ADR-0033 section 3 item 6 (RULING-P3B-D8 / P3B-7): find-accounts-by-external-subject lists the
 * employee id and the user id of everyone an outside sign-in names, so that an operator can name the person by employee id in the
 * commands of platform.identity. It writes nothing, audits nothing and opens no account (ADR-0037 read-only).
 * Exit code: 0 found, 1 nobody found, 2 wrong usage.
 */
@Component
public class IntegrationOperationsCommands {

    static final String OPERATION = "operation";
    static final String FIND_ACCOUNTS = "find-accounts-by-external-subject";
    static final String PROVIDER_KIND = "provider-kind";
    static final String EXTERNAL_SUBJECT = "external-subject";

    private final ExternalSubjectEmployees employees;
    private final AccountEmployees accounts;

    IntegrationOperationsCommands(ExternalSubjectEmployees employees, AccountEmployees accounts) {
        this.employees = employees;
        this.accounts = accounts;
    }

    public int run(ApplicationArguments arguments, PrintStream out, PrintStream err) {
        String operation = single(arguments, OPERATION);
        if (!FIND_ACCOUNTS.equals(operation)) {
            err.println("usage: --operation=" + FIND_ACCOUNTS + " --provider-kind=<WECOM_USER_ID> --external-subject=<text>");
            return 2;
        }
        String kind = single(arguments, PROVIDER_KIND);
        String subject = single(arguments, EXTERNAL_SUBJECT);
        ProviderKind provider;
        try {
            provider = ProviderKind.valueOf(kind == null ? "" : kind);
        } catch (IllegalArgumentException unknown) {
            err.println("wrong usage: --provider-kind must be WECOM_USER_ID, was " + kind);
            return 2;
        }
        if (subject == null || subject.isBlank()) {
            err.println("wrong usage: --external-subject is required");
            return 2;
        }
        List<EmployeeId> found = switch (provider) {
            case WECOM_USER_ID -> employees.withMessagingAccountId(subject);
        };
        if (found.isEmpty()) {
            err.println("nobody found for " + provider.name().toLowerCase(Locale.ROOT) + " " + subject);
            return 1;
        }
        for (EmployeeId employee : found) {
            out.println("employee-id=" + employee + " user-id=" + accounts.accountOf(employee).map(UserId::toString).orElse("(no account)"));
        }
        return 0;
    }

    private static String single(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        if (values == null || values.size() != 1) {
            return null;
        }
        return values.getFirst();
    }
}
