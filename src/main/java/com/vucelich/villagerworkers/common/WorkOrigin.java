package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record WorkOrigin(ResourceLocation dimension, BlockPos pos, String facing) {
    public static final Codec<WorkOrigin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(WorkOrigin::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(WorkOrigin::pos),
            Codec.STRING.fieldOf("facing").forGetter(WorkOrigin::facing)
    ).apply(instance, WorkOrigin::new));

    public String format() {
        return dimension + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " facing " + facing;
    }
}
