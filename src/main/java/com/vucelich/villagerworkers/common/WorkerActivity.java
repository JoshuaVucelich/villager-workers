package com.vucelich.villagerworkers.common;

import java.util.Locale;

public enum WorkerActivity {
    IDLE,
    WORKING,
    COMING,
    STOPPED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkerActivity byId(String id) {
        if (id == null) {
            return IDLE;
        }
        for (WorkerActivity activity : values()) {
            if (activity.id().equals(id.toLowerCase(Locale.ROOT))) {
                return activity;
            }
        }
        return IDLE;
    }
}
