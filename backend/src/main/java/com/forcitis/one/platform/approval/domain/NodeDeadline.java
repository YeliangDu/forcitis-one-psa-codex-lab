package com.forcitis.one.platform.approval.domain;

/**
 * The parameters of a node's deadline, both or neither (D-1a-84): deadlineWorkdays counts from the workday the task was created,
 * escalationWorkdays from the day the reminder goes out.
 */
public record NodeDeadline(int deadlineWorkdays, int escalationWorkdays) {

    public NodeDeadline {
        if (deadlineWorkdays < 1 || escalationWorkdays < 1) {
            throw new IllegalArgumentException("a deadline and an escalation interval are at least one workday: " + deadlineWorkdays + "/"
                    + escalationWorkdays);
        }
    }
}
