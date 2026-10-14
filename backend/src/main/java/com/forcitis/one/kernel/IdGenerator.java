package com.forcitis.one.kernel;

import java.util.UUID;

/** The one place a new surrogate key is made, so tests can inject known values and the key format can change without touching callers. */
public interface IdGenerator {

    UUID next();
}
