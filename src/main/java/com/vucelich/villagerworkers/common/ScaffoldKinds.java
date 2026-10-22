package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a builder may stand on when the next block is out of reach.
 * The default order is andesite scaffolding, then vanilla scaffolding, then spare blocks.
 */
public enum ScaffoldKinds {
    ANDESITE("create:andesite_scaffolding"),
    VANILLA("minecraft:scaffolding"),
    SPARE("");

    private final String itemId;

    ScaffoldKinds(String itemId) {
        this.itemId = itemId;
    }

    public String itemId() {
        return itemId;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<ScaffoldKinds> defaults() {
        return List.of(ANDESITE, VANILLA, SPARE);
    }

    public static ScaffoldKinds byId(String id) {
        if (id == null) {
            return null;
        }
        for (ScaffoldKinds kind : values()) {
            if (kind.id().equals(id.toLowerCase(Locale.ROOT))) {
                return kind;
            }
        }
        return null;
    }

    /** Empty or unknown text uses the default order. Plain blocks drop scaffolding items. */
    public static List<ScaffoldKinds> parse(String text, boolean plainBlocks) {
        List<ScaffoldKinds> parsed = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(",")) {
                ScaffoldKinds kind = byId(part.trim());
                if (kind != null && !parsed.contains(kind)) {
                    parsed.add(kind);
                }
            }
        }
        if (parsed.isEmpty()) {
            parsed.addAll(defaults());
        }
        if (!plainBlocks) {
            return List.copyOf(parsed);
        }
        List<ScaffoldKinds> blocks = new ArrayList<>();
        for (ScaffoldKinds kind : parsed) {
            if (kind == SPARE) {
                blocks.add(kind);
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(SPARE);
        }
        return List.copyOf(blocks);
    }

    public static String format(List<ScaffoldKinds> order) {
        if (order == null || order.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ScaffoldKinds kind : order) {
            if (kind == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(kind.id());
        }
        return builder.toString();
    }
}
