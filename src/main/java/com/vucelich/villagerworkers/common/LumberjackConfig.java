package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record LumberjackConfig(
        Optional<WorkArea> area,
        boolean clearLeaves,
        boolean replantSaplings,
        Optional<BlockPos> logChest,
        Optional<BlockPos> saplingChest,
        Optional<BlockPos> toolChest) {
    public static final Codec<LumberjackConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorkArea.CODEC.optionalFieldOf("area").forGetter(LumberjackConfig::area),
            Codec.BOOL.optionalFieldOf("clear_leaves", false).forGetter(LumberjackConfig::clearLeaves),
            Codec.BOOL.optionalFieldOf("replant_saplings", false).forGetter(LumberjackConfig::replantSaplings),
            BlockPos.CODEC.optionalFieldOf("log_chest").forGetter(LumberjackConfig::logChest),
            BlockPos.CODEC.optionalFieldOf("sapling_chest").forGetter(LumberjackConfig::saplingChest),
            BlockPos.CODEC.optionalFieldOf("tool_chest").forGetter(LumberjackConfig::toolChest)
    ).apply(instance, LumberjackConfig::new));

    public static LumberjackConfig defaults() {
        return new LumberjackConfig(Optional.empty(), false, false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public LumberjackConfig withFlags(boolean clearLeaves, boolean replantSaplings) {
        return new LumberjackConfig(area, clearLeaves, replantSaplings, logChest, saplingChest, toolChest);
    }

    public LumberjackConfig withArea(Optional<WorkArea> area) {
        return new LumberjackConfig(area, clearLeaves, replantSaplings, logChest, saplingChest, toolChest);
    }

    public LumberjackConfig withChest(ChestSlot slot, Optional<BlockPos> pos) {
        return switch (slot) {
            case LOG -> new LumberjackConfig(area, clearLeaves, replantSaplings, pos, saplingChest, toolChest);
            case SAPLING -> new LumberjackConfig(area, clearLeaves, replantSaplings, logChest, pos, toolChest);
            case TOOL -> new LumberjackConfig(area, clearLeaves, replantSaplings, logChest, saplingChest, pos);
        };
    }

    public enum ChestSlot {
        LOG,
        SAPLING,
        TOOL
    }
}
