package com.forcitis.one.organization.workforce.domain;

/** The two version tables a personnel command writes. */
public enum VersionTable {
    EMPLOYEE("employee_versions"),
    ASSIGNMENT("assignment_versions");

    private final String tableName;

    VersionTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
