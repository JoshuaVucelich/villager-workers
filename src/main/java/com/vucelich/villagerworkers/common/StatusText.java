package com.vucelich.villagerworkers.common;

public final class StatusText {
    private StatusText() {}

    public static String line(
            String name,
            WorkerJob job,
            WorkerActivity activity,
            boolean rested,
            int health,
            int maxHealth,
            boolean hungry,
            boolean hurt) {
        String who = name == null || name.isBlank() ? "villager" : name;
        String rest = rested ? "rested" : "not rested";
        return "[" + who + "] " + (job == null ? WorkerJob.NONE : job).id()
                + ", " + (activity == null ? WorkerActivity.IDLE : activity).id()
                + ", " + rest
                + ", " + health + "/" + maxHealth + " health. "
                + problem(hungry, hurt);
    }

    public static String problem(boolean hungry, boolean hurt) {
        if (hungry && hurt) {
            return "hungry, hurt";
        }
        if (hungry) {
            return "hungry";
        }
        if (hurt) {
            return "hurt";
        }
        return "No problems.";
    }

    public static String ownerAlert(String name, ProblemCode code) {
        String who = name == null || name.isBlank() ? "villager" : name;
        return switch (code) {
            case HUNGRY -> "[" + who + "] is hungry.";
            case HURT -> "[" + who + "] is hurt.";
            default -> "[" + who + "] " + code.id().replace('_', ' ') + ".";
        };
    }
}
