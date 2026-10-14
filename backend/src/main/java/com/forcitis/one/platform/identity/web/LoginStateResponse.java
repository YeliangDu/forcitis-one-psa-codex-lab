package com.forcitis.one.platform.identity.web;

/** The one-time state to put into the WeCom authorisation URL and send back with the code. */
record LoginStateResponse(String state) {
}
