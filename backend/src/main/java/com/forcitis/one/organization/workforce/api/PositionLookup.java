package com.forcitis.one.organization.workforce.api;

/** Whether a position can be named by a new document today; reads the position version in force today. */
public interface PositionLookup {
    /** True only for a known position whose version in force today is {@code ACTIVE}; disabled, deleted and unknown answer false. */
    boolean isActive(PositionId positionId);
}
