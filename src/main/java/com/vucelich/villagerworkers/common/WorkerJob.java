package com.vucelich.villagerworkers.common;

import java.util.Locale;

public enum WorkerJob {
    NONE,
    LUMBERJACK,
    MINER,
    BUILDER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkerJob byId(String id) {
        if (id == null) {
            return NONE;
        }
        for (WorkerJob job : values()) {
            if (job.id().equals(id.toLowerCase(Locale.ROOT))) {
                return job;
            }
        }
        return NONE;
    }
}
