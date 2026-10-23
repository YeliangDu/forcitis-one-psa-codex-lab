package com.forcitis.one.platform.file.web;

import com.forcitis.one.platform.identity.api.Permission;

final class FilePermissions {
    static final String CREATE = "hasAuthority('" + Permission.Codes.PLATFORM_FILE_FILE_CREATE + "')";
    static final String READ = "hasAuthority('" + Permission.Codes.PLATFORM_FILE_FILE_READ + "')";

    private FilePermissions() {
    }
}
