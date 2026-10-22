package com.vucelich.villagerworkers.common;

import java.util.List;

/**
 * Resolves a typed player name against the players the server currently has online.
 * The client never supplies the UUID that is stored.
 */
public final class OnlineNames {
    private OnlineNames() {}

    public static TrustedPlayer find(String typed, List<TrustedPlayer> online) {
        if (typed == null || online == null) {
            return null;
        }
        String trimmed = typed.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (TrustedPlayer player : online) {
            if (player != null && player.name() != null && player.name().equalsIgnoreCase(trimmed)) {
                return player;
            }
        }
        return null;
    }
}
