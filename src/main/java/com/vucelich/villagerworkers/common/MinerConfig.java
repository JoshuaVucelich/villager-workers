package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record MinerConfig(
        MinerMode mode,
        Optional<WorkArea> area,
        Optional<WorkOrigin> origin,
        int width,
        int height,
        int length,
        String wallBlock,
        int branchSpacing,
        int branchLength,
        int shaftWidth,
        int shaftDepth,
        boolean shaftUp,
        List<ResourceLocation> valuables,
        Optional<BlockPos> valuablesChest,
        Optional<BlockPos> regularChest,
        Optional<BlockPos> toolChest) {
    public static final Codec<MinerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(MinerMode::byId, MinerMode::id).optionalFieldOf("mode", MinerMode.CLEAR_BOX).forGetter(MinerConfig::mode),
            WorkArea.CODEC.optionalFieldOf("area").forGetter(MinerConfig::area),
            WorkOrigin.CODEC.optionalFieldOf("origin").forGetter(MinerConfig::origin),
            Codec.INT.optionalFieldOf("width", 3).forGetter(MinerConfig::width),
            Codec.INT.optionalFieldOf("height", 3).forGetter(MinerConfig::height),
            Codec.INT.optionalFieldOf("length", 16).forGetter(MinerConfig::length),
            Codec.STRING.optionalFieldOf("wall_block", "").forGetter(MinerConfig::wallBlock),
            Codec.INT.optionalFieldOf("branch_spacing", 4).forGetter(MinerConfig::branchSpacing),
            Codec.INT.optionalFieldOf("branch_length", 8).forGetter(MinerConfig::branchLength),
            Codec.INT.optionalFieldOf("shaft_width", 2).forGetter(MinerConfig::shaftWidth),
            Codec.INT.optionalFieldOf("shaft_depth", 16).forGetter(MinerConfig::shaftDepth),
            Codec.BOOL.optionalFieldOf("shaft_up", false).forGetter(MinerConfig::shaftUp),
            ResourceLocation.CODEC.listOf().optionalFieldOf("valuables", defaultValuables()).forGetter(MinerConfig::valuables),
            BlockPos.CODEC.optionalFieldOf("valuables_chest").forGetter(MinerConfig::valuablesChest),
            BlockPos.CODEC.optionalFieldOf("regular_chest").forGetter(MinerConfig::regularChest),
            BlockPos.CODEC.optionalFieldOf("tool_chest").forGetter(MinerConfig::toolChest)
    ).apply(instance, MinerConfig::new));

    public static MinerConfig defaults() {
        return new MinerConfig(
                MinerMode.CLEAR_BOX,
                Optional.empty(),
                Optional.empty(),
                3,
                3,
                16,
                "",
                4,
                8,
                2,
                16,
                false,
                defaultValuables(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public static List<ResourceLocation> defaultValuables() {
        return SetupLimits.DEFAULT_VALUABLE_IDS.stream().map(ResourceLocation::parse).toList();
    }

    public MinerConfig sanitized() {
        String wall = wallBlock == null ? "" : wallBlock.trim();
        String normalizedWall = wall.isEmpty() ? "" : SetupLimits.normalizeId(wall);
        return new MinerConfig(
                mode == null ? MinerMode.CLEAR_BOX : mode,
                area,
                origin,
                SetupLimits.tunnelWidth(width),
                SetupLimits.tunnelHeight(height),
                SetupLimits.tunnelLength(length),
                normalizedWall == null ? "" : normalizedWall,
                SetupLimits.branchSpacing(branchSpacing),
                SetupLimits.branchLength(branchLength),
                SetupLimits.shaftWidth(shaftWidth),
                SetupLimits.shaftDepth(shaftDepth),
                shaftUp,
                valuables == null ? List.of() : valuables.stream().limit(SetupLimits.MAX_LIST).toList(),
                valuablesChest,
                regularChest,
                toolChest);
    }

    public MinerConfig withMode(MinerMode mode) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest);
    }

    public MinerConfig withArea(Optional<WorkArea> area) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest);
    }

    public MinerConfig withOrigin(Optional<WorkOrigin> origin) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest);
    }

    public MinerConfig withNumbers(
            MinerMode mode,
            int width,
            int height,
            int length,
            String wallBlock,
            int branchSpacing,
            int branchLength,
            int shaftWidth,
            int shaftDepth,
            boolean shaftUp,
            List<ResourceLocation> valuables) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest).sanitized();
    }

    public MinerConfig withChest(ChestSlot slot, Optional<BlockPos> pos) {
        return switch (slot) {
            case VALUABLES -> new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, pos, regularChest, toolChest);
            case REGULAR -> new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, pos, toolChest);
            case TOOL -> new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, pos);
        };
    }

    public enum ChestSlot {
        VALUABLES,
        REGULAR,
        TOOL
    }
}
