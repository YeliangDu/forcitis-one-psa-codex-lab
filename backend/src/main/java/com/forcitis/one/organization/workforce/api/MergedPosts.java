package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;

/** What one employed person holds in the merged unit on the merge day: whether the primary post is there, and how many concurrent posts. */
public record MergedPosts(EmployeeId employee, boolean primaryInUnit, int concurrentAssignmentCount) {
}
