package com.forcitis.one.kernel;

/** The only place the sizes of lists, batches, exports and imports are written down (NAMING_STANDARD A-3, A-6). */
public final class ListLimits {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;
    public static final int BATCH_MAX = 100;
    public static final int EXPORT_MAX = 10_000;
    public static final int IMPORT_MAX = 5_000;
    public static final int SMALL_TABLE_MAX = 1_000;

    private ListLimits() {
    }
}
