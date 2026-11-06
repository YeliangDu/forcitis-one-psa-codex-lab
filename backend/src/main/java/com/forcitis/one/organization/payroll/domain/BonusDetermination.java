package com.forcitis.one.organization.payroll.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Money;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.payroll.api.BonusInputApprovalProcess;
import com.forcitis.one.organization.payroll.api.BonusInputId;
import com.forcitis.one.organization.payroll.api.BonusInputStatus;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One approved bonus input (BonusDetermination, PY3 record). It is created only from a completed approval, so it is ACTIVE from the start; the one change is the
 * void. The rules of {@link #check} are the table's CHECKs: a malformed approval is refused here, before the database would refuse it.
 */
public final class BonusDetermination {
    private static final Pattern REASON_KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");
    private static final String CURRENCY = "CNY";

    private final BonusInputId id;
    private final EmploymentRelationshipId employmentRelationshipId;
    private final LegalEntityId payingLegalEntityId;
    private final LocalDate payrollMonthStartDate;
    private final Money bonus;
    private final String reasonKey;
    private final ApprovalInstanceId approvalInstanceId;
    private final EmployeeId enteredByEmployeeId;
    private final Instant createdAt;
    private BonusInputStatus status;
    private @Nullable UserId voidedByUserId;
    private @Nullable Instant voidedAt;

    private BonusDetermination(BonusInputId id, EmploymentRelationshipId employmentRelationshipId, LegalEntityId payingLegalEntityId, LocalDate payrollMonthStartDate,
            Money bonus, String reasonKey, ApprovalInstanceId approvalInstanceId, EmployeeId enteredByEmployeeId, Instant createdAt,
            BonusInputStatus status, @Nullable UserId voidedByUserId, @Nullable Instant voidedAt) {
        this.id = id;
        this.employmentRelationshipId = employmentRelationshipId;
        this.payingLegalEntityId = payingLegalEntityId;
        this.payrollMonthStartDate = payrollMonthStartDate;
        this.bonus = bonus;
        this.reasonKey = reasonKey;
        this.approvalInstanceId = approvalInstanceId;
        this.enteredByEmployeeId = enteredByEmployeeId;
        this.createdAt = createdAt;
        this.status = status;
        this.voidedByUserId = voidedByUserId;
        this.voidedAt = voidedAt;
    }

    /** The entry rules, shared by the request and the recording of its approval. Never puts the amount or the reason into the message (SENSITIVE). */
    public static void check(LocalDate payrollMonthStartDate, Money bonus, String reasonKey) {
        if (payrollMonthStartDate.getDayOfMonth() != 1) {
            throw invalid("the payroll month is the first day of the month");
        }
        if (!bonus.isPositive() || bonus.amount().precision() - bonus.amount().scale() > 17) {
            throw invalid("the bonus amount is positive with at most 17 integer digits");
        }
        if (!CURRENCY.equals(bonus.currency().getCurrencyCode())) {
            throw invalid("the bonus currency is CNY");
        }
        if (!REASON_KEY.matcher(reasonKey).matches()) {
            throw invalid("the reason key is a capital-letter key of 2 to 40 characters");
        }
    }

    /** Reads the five form fields of the approval; each must be present once. */
    public static BonusDetermination approved(BonusInputId id, LegalEntityId payingLegalEntityId, EmployeeId enteredBy, ApprovalInstanceId approval,
            Map<String, List<String>> fields, Instant now) {
        try {
            EmploymentRelationshipId relationship = new EmploymentRelationshipId(UUID.fromString(single(fields, BonusInputApprovalProcess.EMPLOYMENT_RELATIONSHIP_ID)));
            LocalDate month = LocalDate.parse(single(fields, BonusInputApprovalProcess.PAYROLL_MONTH_START_DATE));
            Money bonus = Money.of(single(fields, BonusInputApprovalProcess.BONUS_AMOUNT), single(fields, BonusInputApprovalProcess.BONUS_CURRENCY));
            String reason = single(fields, BonusInputApprovalProcess.REASON_KEY);
            check(month, bonus, reason);
            return new BonusDetermination(id, relationship, payingLegalEntityId, month, bonus, reason, approval, enteredBy, now, BonusInputStatus.ACTIVE, null, null);
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw invalid("the approval's form fields are not a valid bonus input");
        }
    }

    public static BonusDetermination restore(BonusInputId id, EmploymentRelationshipId relationship, LegalEntityId payingLegalEntityId, LocalDate month,
            Money bonus, String reasonKey, ApprovalInstanceId approval, EmployeeId enteredBy, Instant createdAt, BonusInputStatus status,
            @Nullable UserId voidedBy, @Nullable Instant voidedAt) {
        return new BonusDetermination(id, relationship, payingLegalEntityId, month, bonus, reasonKey, approval, enteredBy, createdAt, status, voidedBy, voidedAt);
    }

    /** ACTIVE to VOIDED, once. */
    public void voidBy(UserId by, Instant now) {
        if (status == BonusInputStatus.VOIDED) {
            throw new DomainException(PayrollError.ORGANIZATION_PAYROLL_BONUS_INPUT_ALREADY_VOIDED, ErrorKind.CONFLICT, "the bonus input " + id + " is already voided");
        }
        status = BonusInputStatus.VOIDED;
        voidedByUserId = by;
        voidedAt = now;
    }

    private static String single(Map<String, List<String>> fields, String name) {
        List<String> values = fields.getOrDefault(name, List.of());
        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("the field " + name + " needs exactly one value");
        }
        return values.getFirst();
    }

    private static DomainException invalid(String message) {
        return new DomainException(PayrollError.ORGANIZATION_PAYROLL_BONUS_INPUT_INVALID, ErrorKind.UNPROCESSABLE, message);
    }

    public BonusInputId id() {
        return id;
    }

    public EmploymentRelationshipId employmentRelationshipId() {
        return employmentRelationshipId;
    }

    public LegalEntityId payingLegalEntityId() {
        return payingLegalEntityId;
    }

    public LocalDate payrollMonthStartDate() {
        return payrollMonthStartDate;
    }

    public Money bonus() {
        return bonus;
    }

    public String reasonKey() {
        return reasonKey;
    }

    public ApprovalInstanceId approvalInstanceId() {
        return approvalInstanceId;
    }

    public EmployeeId enteredByEmployeeId() {
        return enteredByEmployeeId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public BonusInputStatus status() {
        return status;
    }

    public @Nullable UserId voidedByUserId() {
        return voidedByUserId;
    }

    public @Nullable Instant voidedAt() {
        return voidedAt;
    }
}
