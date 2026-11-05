package com.forcitis.one.organization.attendance.application;

import java.time.LocalDate;

/** The standard minutes of one working day when it differs from the version's ordinary or adjusted minutes. */
public record DateOverrideEntry(LocalDate workDate, int minutes, String reason) {
}
