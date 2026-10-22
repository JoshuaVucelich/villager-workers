package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record BuilderConfig(
        Optional<BlockPos> schematicAnchor,
        List<BlockPos> materialChests,
        Optional<String> schematicFile,
        int slice,
        int slices) {
    public static final Codec<BuilderConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("schematic_anchor").forGetter(BuilderConfig::schematicAnchor),
            BlockPos.CODEC.listOf().optionalFieldOf("material_chests", List.of()).forGetter(BuilderConfig::materialChests),
            Codec.STRING.optionalFieldOf("schematic_file").forGetter(BuilderConfig::schematicFile),
            Codec.INT.optionalFieldOf("schematic_slice", 0).forGetter(BuilderConfig::slice),
            Codec.INT.optionalFieldOf("schematic_slices", 1).forGetter(BuilderConfig::slices)
    ).apply(instance, BuilderConfig::new));

    public BuilderConfig(Optional<BlockPos> schematicAnchor, List<BlockPos> materialChests) {
        this(schematicAnchor, materialChests, Optional.empty(), 0, 1);
    }

    public static BuilderConfig defaults() {
        return new BuilderConfig(Optional.empty(), List.of(), Optional.empty(), 0, 1);
    }

    public BuilderConfig withSchematic(Optional<BlockPos> schematicAnchor) {
        return new BuilderConfig(schematicAnchor, materialChests, schematicFile, slice, slices);
    }

    public BuilderConfig withFile(Optional<String> schematicFile) {
        return new BuilderConfig(schematicAnchor, materialChests, schematicFile, slice, slices);
    }

    public BuilderConfig withSlice(int slice, int slices) {
        int count = Math.max(1, slices);
        int index = Math.max(0, Math.min(slice, count - 1));
        return new BuilderConfig(schematicAnchor, materialChests, schematicFile, index, count);
    }

    public BuilderConfig withMaterialChest(BlockPos pos) {
        List<BlockPos> next = new ArrayList<>();
        if (materialChests != null) {
            for (BlockPos existing : materialChests) {
                if (!existing.equals(pos)) {
                    next.add(existing);
                }
            }
        }
        if (next.size() >= SetupLimits.MAX_MATERIAL_CHESTS) {
            next.remove(0);
        }
        next.add(pos);
        return new BuilderConfig(schematicAnchor, List.copyOf(next), schematicFile, slice, slices);
    }
}
