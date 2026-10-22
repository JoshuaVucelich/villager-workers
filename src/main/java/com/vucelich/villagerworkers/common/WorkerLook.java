package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The small bit of worker state tracking clients need in order to draw a hat.
 * The trusted list is not included.
 */
public record WorkerLook(boolean foreman, int crew, String name) {
    public static final Codec<WorkerLook> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("foreman", false).forGetter(WorkerLook::foreman),
            Codec.INT.optionalFieldOf("crew", 0).forGetter(WorkerLook::crew),
            Codec.STRING.optionalFieldOf("name", "").forGetter(WorkerLook::name)
    ).apply(instance, WorkerLook::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerLook> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WorkerLook::foreman,
            ByteBufCodecs.VAR_INT, WorkerLook::crew,
            ByteBufCodecs.STRING_UTF8, WorkerLook::name,
            WorkerLook::new);

    public static WorkerLook from(WorkerData data, String name) {
        return new WorkerLook(data != null && data.foreman(), data == null ? 0 : data.crewSize(), name == null ? "" : name);
    }
}
