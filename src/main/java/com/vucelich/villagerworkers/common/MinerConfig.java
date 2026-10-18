package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
        Optional<BlockPos> toolChest,
        boolean useValuables) {
    /**
     * The valuables list and the on/off flag share one codec field so the record stays within
     * the 16-field codec limit. A missing flag means on, which is what a 0.4.6 save contains.
     */
    private record ValuableSettings(List<ResourceLocation> ids, boolean use) {}

    private static final MapCodec<ValuableSettings> VALUABLE_SETTINGS = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString("valuables"), ops.createString("use_valuables"));
        }

        @Override
        public <T> DataResult<ValuableSettings> decode(DynamicOps<T> ops, MapLike<T> input) {
            List<ResourceLocation> ids = defaultValuables();
            T rawIds = input.get("valuables");
            if (rawIds != null) {
                DataResult<List<ResourceLocation>> parsed = ResourceLocation.CODEC.listOf().parse(ops, rawIds);
                if (parsed.result().isEmpty()) {
                    return DataResult.error(() -> parsed.error().map(error -> error.message()).orElse("valuables"));
                }
                ids = parsed.result().get();
            }
            boolean use = true;
            T rawUse = input.get("use_valuables");
            if (rawUse != null) {
                DataResult<Boolean> parsed = Codec.BOOL.parse(ops, rawUse);
                if (parsed.result().isEmpty()) {
                    return DataResult.error(() -> parsed.error().map(error -> error.message()).orElse("use_valuables"));
                }
                use = parsed.result().get();
            }
            return DataResult.success(new ValuableSettings(ids, use));
        }

        @Override
        public <T> RecordBuilder<T> encode(ValuableSettings input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            prefix.add("valuables", input.ids(), ResourceLocation.CODEC.listOf());
            if (!input.use()) {
                prefix.add("use_valuables", false, Codec.BOOL);
            }
            return prefix;
        }
    };

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
            VALUABLE_SETTINGS.forGetter(config -> new ValuableSettings(config.valuables(), config.useValuables())),
            BlockPos.CODEC.optionalFieldOf("valuables_chest").forGetter(MinerConfig::valuablesChest),
            BlockPos.CODEC.optionalFieldOf("regular_chest").forGetter(MinerConfig::regularChest),
            BlockPos.CODEC.optionalFieldOf("tool_chest").forGetter(MinerConfig::toolChest)
    ).apply(instance, (mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, settings, valuablesChest, regularChest, toolChest) ->
            new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, settings.ids(), valuablesChest, regularChest, toolChest, settings.use())));

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
                Optional.empty(),
                true);
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
                toolChest,
                useValuables);
    }

    public MinerConfig withMode(MinerMode mode) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest, useValuables);
    }

    public MinerConfig withArea(Optional<WorkArea> area) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest, useValuables);
    }

    public MinerConfig withOrigin(Optional<WorkOrigin> origin) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest, useValuables);
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
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest, useValuables).sanitized();
    }

    public MinerConfig withUseValuables(boolean useValuables) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest, useValuables);
    }

    public MinerConfig withValuables(List<ResourceLocation> valuables) {
        return new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, toolChest, useValuables).sanitized();
    }

    public MinerConfig withChest(ChestSlot slot, Optional<BlockPos> pos) {
        return switch (slot) {
            case VALUABLES -> new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, pos, regularChest, toolChest, useValuables);
            case REGULAR -> new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, pos, toolChest, useValuables);
            case TOOL -> new MinerConfig(mode, area, origin, width, height, length, wallBlock, branchSpacing, branchLength, shaftWidth, shaftDepth, shaftUp, valuables, valuablesChest, regularChest, pos, useValuables);
        };
    }

    public enum ChestSlot {
        VALUABLES,
        REGULAR,
        TOOL
    }
}
