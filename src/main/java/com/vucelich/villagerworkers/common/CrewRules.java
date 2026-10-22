package com.vucelich.villagerworkers.common;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * A foreman's saved job and setup apply to the crew unless that worker overrides them.
 */
public final class CrewRules {
    public record View(
            WorkerJob job,
            LumberjackConfig lumberjack,
            MinerConfig miner,
            BuilderConfig builder,
            List<ResourceLocation> whitelist) {
    }

    private CrewRules() {}

    public static View effective(WorkerData worker, WorkerData foreman) {
        if (worker == null) {
            throw new IllegalArgumentException("worker");
        }
        if (worker.overrideForeman() || foreman == null || !foreman.foreman()) {
            return own(worker);
        }
        return own(foreman);
    }

    public static View own(WorkerData data) {
        return new View(data.job(), data.lumberjack(), data.miner(), data.builder(), data.breakWhitelist());
    }

    /** How many of the five rule fields match the expected source. */
    public static int matchingFields(View actual, View expected) {
        int matches = 0;
        if (actual.job() == expected.job()) {
            matches++;
        }
        if (actual.lumberjack().equals(expected.lumberjack())) {
            matches++;
        }
        if (actual.miner().equals(expected.miner())) {
            matches++;
        }
        if (actual.builder().equals(expected.builder())) {
            matches++;
        }
        if (actual.whitelist().equals(expected.whitelist())) {
            matches++;
        }
        return matches;
    }
}
