package com.forcitis.one.platform.file.api;

/** ADR-0043; SPI files are refused until U-14 is decided (ADR-0060 section 3). */
public enum PersonalInformationMark {
    NONE,
    PI,
    SPI
}
