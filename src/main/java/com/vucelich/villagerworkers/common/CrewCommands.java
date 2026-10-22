package com.vucelich.villagerworkers.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Who a foreman's work, stay, and follow buttons apply to.
 */
public final class CrewCommands {
    public enum Target {
        FOREMAN,
        CREW,
        TICKED;

        public static Target byId(String id) {
            if (id == null) {
                return FOREMAN;
            }
            for (Target target : values()) {
                if (target.name().equalsIgnoreCase(id)) {
                    return target;
                }
            }
            return FOREMAN;
        }
    }

    public enum Order {
        WORK,
        STAY,
        FOLLOW,
        JOB_NONE,
        JOB_LUMBERJACK,
        JOB_MINER,
        JOB_BUILDER;

        public static Order byId(String id) {
            if (id == null) {
                return STAY;
            }
            for (Order order : values()) {
                if (order.name().equalsIgnoreCase(id)) {
                    return order;
                }
            }
            return STAY;
        }
    }

    private CrewCommands() {}

    public static boolean assign(WorkerData worker, UUID workerId, WorkerData foreman, UUID foremanId) {
        if (worker == null || foreman == null || workerId == null || foremanId == null) {
            return false;
        }
        if (worker.foreman() || !foreman.foreman() || workerId.equals(foremanId)) {
            return false;
        }
        if (foremanId.equals(worker.crewForeman())) {
            return false;
        }
        if (!foreman.crewMembers().contains(workerId) && foreman.crewMembers().size() >= SetupLimits.MAX_CREW) {
            return false;
        }
        worker.setCrewForeman(foremanId);
        worker.setOverrideForeman(worker.overrideForeman());
        foreman.addCrewMember(workerId);
        return true;
    }

    public static boolean unassign(WorkerData worker, UUID workerId, WorkerData foreman) {
        if (worker == null || workerId == null) {
            return false;
        }
        worker.setCrewForeman(null);
        if (foreman != null) {
            foreman.removeCrewMember(workerId);
        }
        return true;
    }

    public static List<UUID> select(UUID foremanId, List<UUID> crew, Target target, List<UUID> ticked) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        List<UUID> members = crew == null ? List.of() : crew;
        List<UUID> picked = ticked == null ? List.of() : ticked;
        switch (target == null ? Target.FOREMAN : target) {
            case FOREMAN -> {
                if (foremanId != null) {
                    ids.add(foremanId);
                }
            }
            case CREW -> {
                if (foremanId != null) {
                    ids.add(foremanId);
                }
                ids.addAll(members);
            }
            case TICKED -> {
                for (UUID id : picked) {
                    if (id != null && (id.equals(foremanId) || members.contains(id))) {
                        ids.add(id);
                    }
                }
            }
        }
        return List.copyOf(ids);
    }

    public static void apply(WorkerData data, Order order, UUID player, boolean createLoaded) {
        if (data == null || order == null) {
            return;
        }
        switch (order) {
            case WORK -> data.setActivity(ActivityRules.afterStart(data.job(), createLoaded).orElse(WorkerActivity.STOPPED));
            case STAY -> data.setActivity(ActivityRules.afterStop());
            case FOLLOW -> {
                if (player != null) {
                    data.comeTo(player);
                }
            }
            case JOB_NONE -> data.setJob(WorkerJob.NONE);
            case JOB_LUMBERJACK -> data.setJob(WorkerJob.LUMBERJACK);
            case JOB_MINER -> data.setJob(WorkerJob.MINER);
            case JOB_BUILDER -> data.setJob(WorkerJob.BUILDER);
        }
    }

    public static List<WorkerActivity> activities(List<WorkerData> data) {
        List<WorkerActivity> activities = new ArrayList<>();
        for (WorkerData worker : data) {
            activities.add(worker.activity());
        }
        return activities;
    }
}
