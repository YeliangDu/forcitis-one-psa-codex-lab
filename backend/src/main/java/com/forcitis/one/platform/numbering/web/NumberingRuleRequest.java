package com.forcitis.one.platform.numbering.web;

import java.time.LocalDate;

record NumberingRuleRequest(String pattern, String resetPolicy, long startValue, LocalDate effectiveFrom) {
}
