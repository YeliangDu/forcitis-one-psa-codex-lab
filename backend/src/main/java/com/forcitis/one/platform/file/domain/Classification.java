package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.AccessLevel;
import com.forcitis.one.platform.file.api.PersonalInformationMark;

public record Classification(AccessLevel accessLevel, PersonalInformationMark personalInformation) {
}
