package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.web.NotFromQuery;

/** Sample for ModuleDependencyTest: the marker used by identity outside infrastructure.security is reported (CS-5.9). */
@NotFromQuery(source = "the session")
final class WebMarkerOutsideSecurity {
}
