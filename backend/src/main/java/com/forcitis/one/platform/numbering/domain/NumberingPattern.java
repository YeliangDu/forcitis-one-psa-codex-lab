package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.kernel.ValueObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admin-defined template, e.g. "{TYPE}-{LE}-{yyyyMM}-{seq:4}". Validated on construction so an invalid rule can never be saved:
 * exactly one {seq:N} (N 1..12), only known tokens, and the date part must be at least as fine as the reset policy
 * (otherwise two periods would produce the same number).
 */
@ValueObject
public record NumberingPattern(String value, ResetPolicy resetPolicy) {

    /** Placeholder for the registered document type code (ADR-0010 decision 5), e.g. QT for a quote. */
    public static final String TYPE_TOKEN = "TYPE";
    /** Default template of the unified new format (ADR-0010 decision 5). */
    public static final String DEFAULT_TEMPLATE = "{TYPE}-{LE}-{yyyyMM}-{seq:4}";

    private static final Pattern TOKEN = Pattern.compile("\\{([^}]*)}");
    private static final Pattern SEQ = Pattern.compile("seq:(\\d{1,2})");

    public NumberingPattern {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(resetPolicy, "resetPolicy");
        int sequences = 0;
        boolean hasYear = false;
        boolean hasMonth = false;
        boolean hasDay = false;
        Matcher m = TOKEN.matcher(value);
        while (m.find()) {
            String token = m.group(1);
            Matcher seq = SEQ.matcher(token);
            if (seq.matches()) {
                int width = Integer.parseInt(seq.group(1));
                if (width < 1 || width > 12) {
                    throw new IllegalArgumentException("{seq:N} width must be 1..12");
                }
                sequences++;
            } else if (token.equals("LE") || token.equals(TYPE_TOKEN)) {
                continue;
            } else if (token.matches("(yyyy|yy|MM|dd|yyyyMM|yyyyMMdd|yyMMdd)")) {
                hasYear |= token.contains("yy");
                hasMonth |= token.contains("MM");
                hasDay |= token.contains("dd");
            } else {
                throw new IllegalArgumentException("Unknown token {" + token + "} in numbering pattern " + value);
            }
        }
        if (sequences != 1) {
            throw new IllegalArgumentException("Numbering pattern needs exactly one {seq:N}: " + value);
        }
        boolean distinguishesPeriod = switch (resetPolicy) {
            case NEVER -> true;
            case YEARLY -> hasYear;
            case MONTHLY -> hasYear && hasMonth;
            case DAILY -> hasYear && hasMonth && hasDay;
        };
        if (!distinguishesPeriod) {
            throw new IllegalArgumentException("Pattern " + value + " resets " + resetPolicy
                    + " but does not contain the date parts that tell periods apart (numbers would repeat)");
        }
    }

    /** Highest sequence the {seq:N} placeholder can hold (10^N - 1); issuing beyond it fails. */
    public long capacity() {
        Matcher m = TOKEN.matcher(value);
        while (m.find()) {
            Matcher seq = SEQ.matcher(m.group(1));
            if (seq.matches()) {
                return (long) Math.pow(10, Integer.parseInt(seq.group(1))) - 1;
            }
        }
        throw new IllegalStateException("validated pattern without {seq:N}: " + value);
    }

    /** Whether the template contains the document type code placeholder. */
    public boolean usesTypeCode() {
        return TOKEN.matcher(value).results().anyMatch(m -> m.group(1).equals(TYPE_TOKEN));
    }

    /** @param typeCode the registered code of the document type; may be null only when the template has no {TYPE} */
    public String render(String typeCode, LegalEntityId legalEntity, LocalDate date, long sequence) {
        Matcher m = TOKEN.matcher(value);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            Matcher seq = SEQ.matcher(token);
            String replacement;
            if (seq.matches()) {
                int width = Integer.parseInt(seq.group(1));
                String digits = Long.toString(sequence);
                if (digits.length() > width) {
                    throw new IllegalStateException("Sequence " + sequence + " overflows {seq:" + width + "} in " + value);
                }
                replacement = "0".repeat(width - digits.length()) + digits;
            } else if (token.equals("LE")) {
                replacement = Long.toString(legalEntity.value());
            } else if (token.equals(TYPE_TOKEN)) {
                if (typeCode == null) {
                    throw new IllegalStateException("Pattern " + value + " needs a document type code but none is registered");
                }
                replacement = typeCode;
            } else {
                replacement = date.format(DateTimeFormatter.ofPattern(token));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
