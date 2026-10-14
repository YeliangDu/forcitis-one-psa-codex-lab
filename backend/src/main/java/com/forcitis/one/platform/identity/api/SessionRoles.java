package com.forcitis.one.platform.identity.api;

/**
 * Role-like marks of the session itself, next to the permissions its roles give. ORDINARY is given only to a session that has been
 * confirmed and is not in the administrator mode (nor an emergency session): what is for the ordinary working day, such as handling
 * approvals, asks for it with hasRole (I-11).
 */
public interface SessionRoles {

    String ORDINARY = "ORDINARY_SESSION";
}
