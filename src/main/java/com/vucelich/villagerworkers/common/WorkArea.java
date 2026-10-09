package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record WorkArea(ResourceLocation dimension, BlockPos min, BlockPos max) {
    public static final Codec<WorkArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(WorkArea::dimension),
            BlockPos.CODEC.fieldOf("min").forGetter(WorkArea::min),
            BlockPos.CODEC.fieldOf("max").forGetter(WorkArea::max)
    ).apply(instance, WorkArea::new));

    public WorkArea normalized() {
        return new WorkArea(
                dimension,
                new BlockPos(
                        Math.min(min.getX(), max.getX()),
                        Math.min(min.getY(), max.getY()),
                        Math.min(min.getZ(), max.getZ())),
                new BlockPos(
                        Math.max(min.getX(), max.getX()),
                        Math.max(min.getY(), max.getY()),
                        Math.max(min.getZ(), max.getZ())));
    }

    public String format() {
        WorkArea area = normalized();
        return area.dimension + " " + area.min.getX() + " " + area.min.getY() + " " + area.min.getZ()
                + " -> " + area.max.getX() + " " + area.max.getY() + " " + area.max.getZ();
    }
}
