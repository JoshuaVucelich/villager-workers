package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One temporary block saved with the worker.
 * {@code state} is the SNBT of the block that was replaced. The game reads it back after a restart.
 */
public record ScaffoldPost(int x, int y, int z, String state, String itemId) {
    public static final Codec<ScaffoldPost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(ScaffoldPost::x),
            Codec.INT.fieldOf("y").forGetter(ScaffoldPost::y),
            Codec.INT.fieldOf("z").forGetter(ScaffoldPost::z),
            Codec.STRING.fieldOf("state").forGetter(ScaffoldPost::state),
            Codec.STRING.fieldOf("item").forGetter(ScaffoldPost::itemId)
    ).apply(instance, ScaffoldPost::new));

    public ScaffoldPost {
        if (state == null) {
            state = "";
        }
        if (itemId == null) {
            itemId = "";
        }
    }
}
