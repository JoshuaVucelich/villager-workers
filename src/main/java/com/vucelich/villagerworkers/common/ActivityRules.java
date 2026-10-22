package com.vucelich.villagerworkers.common;

import java.util.Optional;

/**
 * How Start, Stop, Come, and Resume change the saved activity.
 * The builder starts when Create is available or the worker already has a schematic file. The caller passes that flag.
 */
public final class ActivityRules {
    private ActivityRules() {}

    public static Optional<WorkerActivity> afterStart(WorkerJob job, boolean builderEnabled) {
        if (job == WorkerJob.BUILDER) {
            return builderEnabled ? Optional.of(WorkerActivity.WORKING) : Optional.empty();
        }
        if (job == null || job == WorkerJob.NONE) {
            return Optional.of(WorkerActivity.IDLE);
        }
        return Optional.of(WorkerActivity.WORKING);
    }

    public static Optional<WorkerActivity> afterResume(WorkerJob job, boolean builderEnabled) {
        return afterStart(job, builderEnabled);
    }

    public static WorkerActivity afterStop() {
        return WorkerActivity.STOPPED;
    }

    public static WorkerActivity afterCome() {
        return WorkerActivity.COMING;
    }
}
