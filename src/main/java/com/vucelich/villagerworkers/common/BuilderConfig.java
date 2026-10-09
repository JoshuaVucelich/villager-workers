package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record BuilderConfig(Optional<BlockPos> schematicAnchor, List<BlockPos> materialChests) {
    public static final Codec<BuilderConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("schematic_anchor").forGetter(BuilderConfig::schematicAnchor),
            BlockPos.CODEC.listOf().optionalFieldOf("material_chests", List.of()).forGetter(BuilderConfig::materialChests)
    ).apply(instance, BuilderConfig::new));

    public static BuilderConfig defaults() {
        return new BuilderConfig(Optional.empty(), List.of());
    }

    public BuilderConfig withSchematic(Optional<BlockPos> schematicAnchor) {
        return new BuilderConfig(schematicAnchor, materialChests);
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
        return new BuilderConfig(schematicAnchor, List.copyOf(next));
    }
}
