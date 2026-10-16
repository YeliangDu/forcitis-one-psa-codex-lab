package com.forcitis.one.platform.identity.infrastructure.startup;

import com.forcitis.one.platform.identity.api.SingleOperatorMode;

record SingleOperatorModeSettings(boolean enabled) implements SingleOperatorMode {
}
