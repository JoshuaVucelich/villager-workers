package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One owner-facing problem and when it was last sent. */
public record WorkNotice(String code, long notifiedAt, boolean pending, String subject) {
    public static final Codec<WorkNotice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("code").forGetter(WorkNotice::code),
            Codec.LONG.optionalFieldOf("notified_at", Long.MIN_VALUE).forGetter(WorkNotice::notifiedAt),
            Codec.BOOL.optionalFieldOf("pending", false).forGetter(WorkNotice::pending),
            Codec.STRING.optionalFieldOf("subject", "").forGetter(WorkNotice::subject)
    ).apply(instance, WorkNotice::new));

    public WorkNotice {
        if (code == null) {
            code = "";
        }
        if (subject == null) {
            subject = "";
        }
    }
}
