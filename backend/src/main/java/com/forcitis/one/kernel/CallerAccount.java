package com.forcitis.one.kernel;

/**
 * The account of the signed-in caller, for modules that may not depend on identity; implemented by identity. A session whose principal
 * is not an account id in its canonical form is refused as not signed in (ADR-0033 section 3 item 2).
 */
public interface CallerAccount {

    UserId userId();
}
