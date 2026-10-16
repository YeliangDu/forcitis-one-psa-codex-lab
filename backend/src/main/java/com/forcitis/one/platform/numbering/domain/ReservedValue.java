package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.platform.numbering.api.NumberingCounterId;

/** The value reserved and the counter row it was reserved on. */
public record ReservedValue(long value, NumberingCounterId counterId) {
}
