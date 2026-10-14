package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;

/** The assignee once somebody has claimed, otherwise any candidate; the engine's own complete() checks nothing (I-7). */
public final class TaskActors {

    private TaskActors() {
    }

    public static boolean mayAct(TaskView task, UserId actor) {
        if (actor == null) {
            return false;
        }
        return task.assignee().map(actor::equals).orElseGet(() -> task.candidates().contains(actor));
    }
}
