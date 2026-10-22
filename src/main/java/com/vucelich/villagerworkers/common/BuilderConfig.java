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
        Optional<BlockPos> structureOrigin,
        List<SchematicPiece> pieces,
        int layerMin,
        int layerMax) {
    /** No layer filter. The worker builds every layer in the pieces. */
    public static final int ALL_LAYERS = Integer.MIN_VALUE;

    public static final Codec<BuilderConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("schematic_anchor").forGetter(BuilderConfig::schematicAnchor),
            BlockPos.CODEC.listOf().optionalFieldOf("material_chests", List.of()).forGetter(BuilderConfig::materialChests),
            BlockPos.CODEC.optionalFieldOf("structure_origin").forGetter(BuilderConfig::structureOrigin),
            SchematicPiece.CODEC.listOf().optionalFieldOf("pieces", List.of()).forGetter(BuilderConfig::pieces),
            Codec.INT.optionalFieldOf("layer_min", ALL_LAYERS).forGetter(BuilderConfig::layerMin),
            Codec.INT.optionalFieldOf("layer_max", ALL_LAYERS).forGetter(BuilderConfig::layerMax)
    ).apply(instance, BuilderConfig::new));

    public BuilderConfig {
        if (schematicAnchor == null) {
            schematicAnchor = Optional.empty();
        }
        if (structureOrigin == null) {
            structureOrigin = Optional.empty();
        }
        materialChests = materialChests == null ? List.of() : List.copyOf(materialChests);
        pieces = pieces == null ? List.of() : List.copyOf(pieces);
    }

    public BuilderConfig(Optional<BlockPos> schematicAnchor, List<BlockPos> materialChests) {
        this(schematicAnchor, materialChests, Optional.empty(), List.of(), ALL_LAYERS, ALL_LAYERS);
    }

    public static BuilderConfig defaults() {
        return new BuilderConfig(Optional.empty(), List.of());
    }

    public boolean hasStructure() {
        return structureOrigin.isPresent() && !pieces.isEmpty();
    }

    public boolean limitsLayers() {
        return layerMin != ALL_LAYERS && layerMax != ALL_LAYERS && layerMin <= layerMax;
    }

    public BuilderConfig withSchematic(Optional<BlockPos> schematicAnchor) {
        return new BuilderConfig(schematicAnchor, materialChests, structureOrigin, pieces, layerMin, layerMax);
    }

    public BuilderConfig withStructureOrigin(Optional<BlockPos> structureOrigin) {
        return new BuilderConfig(schematicAnchor, materialChests, structureOrigin, pieces, layerMin, layerMax);
    }

    public BuilderConfig withPieces(List<SchematicPiece> pieces) {
        return new BuilderConfig(schematicAnchor, materialChests, structureOrigin, pieces, layerMin, layerMax);
    }

    public BuilderConfig withLayers(int layerMin, int layerMax) {
        return new BuilderConfig(schematicAnchor, materialChests, structureOrigin, pieces, layerMin, layerMax);
    }

    public BuilderConfig withMaterialChest(BlockPos pos) {
        List<BlockPos> next = new ArrayList<>();
        for (BlockPos existing : materialChests) {
            if (!existing.equals(pos)) {
                next.add(existing);
            }
        }
        if (next.size() >= SetupLimits.MAX_MATERIAL_CHESTS) {
            next.remove(0);
        }
        next.add(pos);
        return new BuilderConfig(schematicAnchor, List.copyOf(next), structureOrigin, pieces, layerMin, layerMax);
    }
}
