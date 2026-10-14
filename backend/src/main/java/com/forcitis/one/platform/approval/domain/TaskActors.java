package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;

/**
 * Who may act on a task (I-7, RULE-276): the assignee once somebody has claimed it, otherwise any candidate.
 * Decided here, on the server, for every task operation (claim, decide, return); the engine's own complete() checks nothing.
 */
public final class TaskActors {

    private TaskActors() {
    }

    public static boolean mayAct(TaskView task, UserId actor) {
        if (actor == null) {
            return false;
        }
        return task.isClaimed() ? task.assignee().equals(actor) : task.candidates().contains(actor);
    }
}
