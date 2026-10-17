package com.vucelich.villagerworkers.common;

import java.util.Locale;

public enum ProblemCode {
    NONE,
    HUNGRY,
    HURT,
    BROKEN_TOOL,
    OUT_OF_MATERIALS,
    MISSING_CHEST,
    NO_ACCESS,
    CHEST_FULL,
    STUCK,
    BLOCKED,
    SHIP_GUARD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ProblemCode byId(String id) {
        if (id == null) {
            return NONE;
        }
        for (ProblemCode code : values()) {
            if (code.id().equals(id.toLowerCase(Locale.ROOT))) {
                return code;
            }
        }
        return NONE;
    }
}
