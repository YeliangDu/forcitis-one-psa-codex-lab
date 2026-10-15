package com.forcitis.one.kernel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A list that is not paged because the table is small stays small: the day it outgrows the cap it fails and raises an alert. */
public final class SmallTable {
    private static final Logger LOG = LoggerFactory.getLogger(SmallTable.class);

    private SmallTable() {
    }

    public static void requireWithinCap(String list, int rows) {
        if (rows > ListLimits.SMALL_TABLE_MAX) {
            LOG.error("ALERT kernel the unpaged list {} holds {} rows, more than the cap of {}; it needs paging", list, rows, ListLimits.SMALL_TABLE_MAX);
            throw new IllegalStateException("the unpaged list " + list + " holds more than " + ListLimits.SMALL_TABLE_MAX + " rows");
        }
    }
}
