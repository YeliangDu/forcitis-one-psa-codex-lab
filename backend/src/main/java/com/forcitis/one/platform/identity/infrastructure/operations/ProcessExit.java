package com.forcitis.one.platform.identity.infrastructure.operations;

/** How the operations process ends; a test supplies its own so that it can look at the finished context. */
interface ProcessExit {

    void exit(int code);
}
