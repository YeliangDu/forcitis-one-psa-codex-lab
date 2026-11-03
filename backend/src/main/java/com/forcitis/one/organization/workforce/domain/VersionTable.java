package com.forcitis.one.organization.workforce.domain;

/** The version tables a personnel command writes; the legal entity table holds the employee's own attribution, which a command may set (ADR-0073 item 3.2). */
public enum VersionTable {
    EMPLOYEE("employee_versions"),
    ASSIGNMENT("assignment_versions"),
    LEGAL_ENTITY("employee_legal_entities");

    private final String tableName;

    VersionTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
