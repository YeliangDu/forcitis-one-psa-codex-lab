package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.numbering.api.ResetPolicy;

record NumberingRuleRequest(String pattern, ResetPolicy resetPolicy, long startValue) {
}
