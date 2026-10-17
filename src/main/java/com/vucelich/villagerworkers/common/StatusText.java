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
        return line(name, job, activity, rested, health, maxHealth, hungry, hurt, WorkerProblem.NONE);
    }

    public static String line(
            String name,
            WorkerJob job,
            WorkerActivity activity,
            boolean rested,
            int health,
            int maxHealth,
            boolean hungry,
            boolean hurt,
            WorkerProblem work) {
        String who = name == null || name.isBlank() ? "villager" : name;
        String rest = rested ? "rested" : "not rested";
        return "[" + who + "] " + (job == null ? WorkerJob.NONE : job).id()
                + ", " + (activity == null ? WorkerActivity.IDLE : activity).id()
                + ", " + rest
                + ", " + health + "/" + maxHealth + " health. "
                + problem(hungry, hurt, work);
    }

    public static String problem(boolean hungry, boolean hurt) {
        return problem(hungry, hurt, WorkerProblem.NONE);
    }

    public static String problem(boolean hungry, boolean hurt, WorkerProblem work) {
        String live = liveProblem(hungry, hurt);
        if (work == null || !work.active() || work.code() == ProblemCode.HUNGRY || work.code() == ProblemCode.HURT) {
            return live;
        }
        String extra = describe(work);
        if ("No problems.".equals(live)) {
            return extra;
        }
        return live + ". " + extra;
    }

    public static String ownerAlert(String name, ProblemCode code) {
        return ownerAlert(name, code, "");
    }

    public static String ownerAlert(String name, ProblemCode code, String subject) {
        String who = name == null || name.isBlank() ? "villager" : name;
        String item = subject == null || subject.isBlank() ? "materials" : subject;
        return switch (code) {
            case HUNGRY -> "[" + who + "] is hungry.";
            case HURT -> "[" + who + "] is hurt.";
            case BROKEN_TOOL -> "[" + who + "] needs a new tool.";
            case OUT_OF_MATERIALS -> "[" + who + "] is out of " + item + ".";
            case MISSING_CHEST -> "[" + who + "] is missing " + (item.equals("materials") ? "a chest" : item) + ".";
            case NO_ACCESS -> "[" + who + "] cannot open that container.";
            case CHEST_FULL -> "[" + who + "] has a full chest.";
            case STUCK -> "[" + who + "] is stuck.";
            case BLOCKED -> "[" + who + "] is blocked at " + item + ".";
            case SHIP_GUARD -> "[" + who + "] will not dig because the ship check failed.";
            case NONE -> "[" + who + "] has no problem.";
        };
    }

    private static String liveProblem(boolean hungry, boolean hurt) {
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

    private static String describe(WorkerProblem work) {
        String subject = work.subject() == null ? "" : work.subject();
        return switch (work.code()) {
            case OUT_OF_MATERIALS -> "out of " + (subject.isBlank() ? "materials" : subject);
            case BLOCKED -> "blocked at " + subject;
            case BROKEN_TOOL -> "needs a tool";
            case MISSING_CHEST -> subject.isBlank() ? "missing chest" : "missing " + subject;
            case NO_ACCESS -> "cannot open a container";
            case CHEST_FULL -> "chest full";
            case STUCK -> "stuck";
            case SHIP_GUARD -> "ship check failed";
            default -> work.code().id().replace('_', ' ');
        };
    }
}
