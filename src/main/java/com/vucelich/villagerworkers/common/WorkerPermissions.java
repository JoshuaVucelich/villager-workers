package com.vucelich.villagerworkers.common;

import java.util.Set;
import java.util.UUID;

/**
 * Who may do what. This does not look at the client, and it does not treat singleplayer as a special case.
 */
public final class WorkerPermissions {
    private WorkerPermissions() {}

    public static WorkerRole role(UUID owner, Set<UUID> trusted, UUID actor) {
        return role(owner, Set.of(), trusted, actor);
    }

    /** Co-owners have the same role as the primary owner. Trusted players do not. */
    public static WorkerRole role(UUID owner, Set<UUID> coOwners, Set<UUID> trusted, UUID actor) {
        if (actor == null) {
            return WorkerRole.NONE;
        }
        if (owner != null && owner.equals(actor)) {
            return WorkerRole.OWNER;
        }
        if (coOwners != null && coOwners.contains(actor)) {
            return WorkerRole.OWNER;
        }
        if (trusted != null && trusted.contains(actor)) {
            return WorkerRole.TRUSTED;
        }
        return WorkerRole.NONE;
    }

    public static boolean allows(WorkerRole role, WorkerAction action) {
        if (role == null || action == null || role == WorkerRole.NONE) {
            return false;
        }
        return switch (action) {
            case OPEN_SCREEN, SET_ACTIVITY, EDIT_SETUP, CHAT -> true;
            case EDIT_TRUSTED, TRANSFER_OWNER, VIEW_PERMISSIONS -> role == WorkerRole.OWNER;
        };
    }

    public static boolean allows(UUID owner, Set<UUID> trusted, UUID actor, WorkerAction action) {
        return allows(role(owner, trusted, actor), action);
    }

    public static boolean allows(UUID owner, Set<UUID> coOwners, Set<UUID> trusted, UUID actor, WorkerAction action) {
        return allows(role(owner, coOwners, trusted, actor), action);
    }
}
