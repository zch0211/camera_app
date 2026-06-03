package com.camera.app.alert.entity;

public enum AlertOperationType {
    CREATE,
    CONFIRM,
    MARK_FALSE_POSITIVE,
    RESOLVE,
    IGNORE,
    COMMENT,
    /** Automatic re-trigger: existing active alert aggregated, triggerCount++ */
    RETRIGGER
}
