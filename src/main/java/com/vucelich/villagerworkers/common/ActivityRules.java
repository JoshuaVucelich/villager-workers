package com.vucelich.villagerworkers.common;

import java.util.Optional;

/**
 * How Start, Stop, Come, and Resume change the saved activity. The builder does not start in this milestone.
 */
public final class ActivityRules {
    private ActivityRules() {}

    public static Optional<WorkerActivity> afterStart(WorkerJob job) {
        if (job == WorkerJob.BUILDER) {
            return Optional.empty();
        }
        if (job == null || job == WorkerJob.NONE) {
            return Optional.of(WorkerActivity.IDLE);
        }
        return Optional.of(WorkerActivity.WORKING);
    }

    public static Optional<WorkerActivity> afterResume(WorkerJob job) {
        return afterStart(job);
    }

    public static WorkerActivity afterStop() {
        return WorkerActivity.STOPPED;
    }

    public static WorkerActivity afterCome() {
        return WorkerActivity.COMING;
    }
}
