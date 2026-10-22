package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who is on a crew, which villagers a foreman command hits, and how a crew rule is copied.
 * This does not load chunks or pathfind.
 */
public final class CrewLogic {
    public enum Scope {
        FOREMAN,
        CREW,
        TICKED;

        public static Scope byId(String id) {
            if (id == null) {
                return FOREMAN;
            }
            for (Scope scope : values()) {
                if (scope.name().equalsIgnoreCase(id)) {
                    return scope;
                }
            }
            return FOREMAN;
        }
    }

    private CrewLogic() {}

    /** A foreman is not a crew member, and a worker is not their own foreman. */
    public static boolean canAssign(WorkerRank workerRank, UUID workerId, UUID foremanId) {
        if (workerRank == WorkerRank.FOREMAN || workerId == null || foremanId == null) {
            return false;
        }
        return !workerId.equals(foremanId);
    }

    /**
     * Copies the foreman's scaffold rule onto members that have not opted out.
     * An opted-out member keeps their own order and plain-blocks flag.
     */
    public static int applyScaffoldRule(WorkerData foreman, List<WorkerData> crew) {
        if (foreman == null || crew == null) {
            return 0;
        }
        int updated = 0;
        for (WorkerData member : crew) {
            if (member == null || member.rulesOverride()) {
                continue;
            }
            member.setScaffoldOrder(foreman.scaffoldOrder());
            member.setPlainBlocks(foreman.plainBlocks());
            updated++;
        }
        return updated;
    }

    public static List<ScaffoldKinds> effectiveScaffold(WorkerData worker, WorkerData foreman) {
        if (worker == null) {
            return ScaffoldKinds.defaults();
        }
        if (!worker.rulesOverride() && foreman != null && worker.foreman() != null) {
            return ScaffoldKinds.parse(foreman.scaffoldOrder(), foreman.plainBlocks());
        }
        return ScaffoldKinds.parse(worker.scaffoldOrder(), worker.plainBlocks());
    }

    /**
     * Foreman only, the foreman plus the crew, or the ticked ids that are actually on that crew.
     * A ticked id that is not the foreman and not in the crew is dropped.
     */
    public static List<UUID> targets(Scope scope, UUID foremanId, List<UUID> crew, List<UUID> ticked) {
        List<UUID> result = new ArrayList<>();
        if (foremanId == null) {
            return result;
        }
        if (scope == null || scope == Scope.FOREMAN) {
            result.add(foremanId);
            return result;
        }
        if (scope == Scope.CREW) {
            result.add(foremanId);
            if (crew != null) {
                for (UUID id : crew) {
                    if (id != null && !result.contains(id)) {
                        result.add(id);
                    }
                }
            }
            return result;
        }
        if (ticked != null) {
            for (UUID id : ticked) {
                if (id == null || result.contains(id)) {
                    continue;
                }
                boolean onCrew = foremanId.equals(id) || (crew != null && crew.contains(id));
                if (onCrew) {
                    result.add(id);
                }
            }
        }
        return result;
    }
}
