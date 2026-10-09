package com.vucelich.villagerworkers.common;

import java.util.Locale;

public enum MinerMode {
    CLEAR_BOX,
    TUNNEL,
    BRANCH,
    SHAFT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MinerMode next() {
        MinerMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static MinerMode byId(String id) {
        if (id == null) {
            return CLEAR_BOX;
        }
        for (MinerMode mode : values()) {
            if (mode.id().equals(id.toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        return CLEAR_BOX;
    }
}
