package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record TrustedPlayer(UUID id, String name) {
    public static final Codec<TrustedPlayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(TrustedPlayer::id),
            Codec.STRING.fieldOf("name").forGetter(TrustedPlayer::name)
    ).apply(instance, TrustedPlayer::new));
}
