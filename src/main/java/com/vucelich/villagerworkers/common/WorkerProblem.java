package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record WorkerProblem(ProblemCode code, String subject) {
    public static final WorkerProblem NONE = new WorkerProblem(ProblemCode.NONE, "");

    public static final Codec<WorkerProblem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(ProblemCode::byId, ProblemCode::id).optionalFieldOf("code", ProblemCode.NONE).forGetter(WorkerProblem::code),
            Codec.STRING.optionalFieldOf("subject", "").forGetter(WorkerProblem::subject)
    ).apply(instance, WorkerProblem::new));

    public boolean active() {
        return code != null && code != ProblemCode.NONE;
    }
}
