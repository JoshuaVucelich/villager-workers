package com.vucelich.villagerworkers.common;

import java.util.Locale;

/**
 * What a worker may stand on while building. Plain ignores scaffolding even when it is available.
 * Create's andesite scaffolding block id is create:andesite_scaffolding.
 */
public enum ScaffoldPreference {
    VANILLA("minecraft:scaffolding"),
    ANDESITE("create:andesite_scaffolding"),
    PLAIN("spare blocks");

    public static final String VANILLA_ID = "minecraft:scaffolding";
    public static final String ANDESITE_ID = "create:andesite_scaffolding";

    private final String needed;

    ScaffoldPreference(String needed) {
        this.needed = needed;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String needed() {
        return needed;
    }

    public ScaffoldPreference next() {
        return switch (this) {
            case VANILLA -> ANDESITE;
            case ANDESITE -> PLAIN;
            case PLAIN -> VANILLA;
        };
    }

    public static ScaffoldPreference byId(String id) {
        if (id == null) {
            return VANILLA;
        }
        for (ScaffoldPreference preference : values()) {
            if (preference.id().equals(id.toLowerCase(Locale.ROOT))) {
                return preference;
            }
        }
        return VANILLA;
    }
}
