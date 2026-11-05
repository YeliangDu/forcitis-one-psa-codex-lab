package com.forcitis.one.organization.attendance.domain;

import java.time.LocalDate;

/** The standard minutes of one working day when it differs from the version's ordinary or adjusted minutes. */
public record DateOverride(LocalDate workDate, int minutes, String reason) {
}
