package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SetupLimits {
    public static final List<String> DEFAULT_VALUABLE_IDS = List.of(
            "minecraft:diamond",
            "minecraft:emerald",
            "minecraft:raw_gold",
            "minecraft:raw_iron",
            "minecraft:raw_copper",
            "minecraft:lapis_lazuli",
            "minecraft:redstone",
            "minecraft:coal",
            "minecraft:ancient_debris",
            "minecraft:quartz",
            "minecraft:amethyst_shard");

    public static final int MAX_LIST = 64;
    public static final int MAX_MATERIAL_CHESTS = 8;
    public static final int MAX_TRUSTED = 16;
    public static final int MAX_OWNERS = 8;

    private SetupLimits() {}

    public static int tunnelWidth(int value) {
        int clamped = clamp(value, 1, 7);
        if (clamped % 2 == 0) {
            clamped -= 1;
        }
        return Math.max(1, clamped);
    }

    public static int tunnelHeight(int value) {
        return clamp(value, 2, 5);
    }

    public static int tunnelLength(int value) {
        return clamp(value, 1, 128);
    }

    public static int branchSpacing(int value) {
        return clamp(value, 2, 16);
    }

    public static int branchLength(int value) {
        return clamp(value, 1, 64);
    }

    public static int shaftWidth(int value) {
        return clamp(value, 1, 5);
    }

    public static int shaftDepth(int value) {
        return clamp(value, 1, 64);
    }

    public static List<String> resourceIds(List<String> raw, boolean emptyBecomesDefault) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String entry : raw) {
                if (out.size() >= MAX_LIST) {
                    break;
                }
                String normalized = normalizeId(entry);
                if (normalized != null && !out.contains(normalized)) {
                    out.add(normalized);
                }
            }
        }
        if (out.isEmpty() && emptyBecomesDefault) {
            return DEFAULT_VALUABLE_IDS;
        }
        return List.copyOf(out);
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon >= trimmed.length() - 1 || trimmed.indexOf(' ') >= 0) {
            return null;
        }
        String namespace = trimmed.substring(0, colon);
        String path = trimmed.substring(colon + 1);
        if (!namespace.chars().allMatch(SetupLimits::isIdChar) || !path.chars().allMatch(SetupLimits::isPathChar)) {
            return null;
        }
        return trimmed;
    }

    private static boolean isIdChar(int ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '.' || ch == '-';
    }

    private static boolean isPathChar(int ch) {
        return isIdChar(ch) || ch == '/';
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
