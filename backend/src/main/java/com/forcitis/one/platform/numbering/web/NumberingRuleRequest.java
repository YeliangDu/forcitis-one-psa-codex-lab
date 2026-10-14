package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.numbering.api.ResetPolicy;
import java.time.LocalDate;

record NumberingRuleRequest(String pattern, ResetPolicy resetPolicy, long startValue, LocalDate effectiveFrom) {
}
