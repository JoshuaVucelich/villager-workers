package com.vucelich.villagerworkers.common;

import java.util.Locale;

/** A hired villager is a worker. Promoting one makes a foreman. */
public enum WorkerRank {
    WORKER,
    FOREMAN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkerRank byId(String id) {
        if (id != null && FOREMAN.id().equals(id.toLowerCase(Locale.ROOT))) {
            return FOREMAN;
        }
        return WORKER;
    }
}
