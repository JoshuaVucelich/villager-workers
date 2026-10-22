package com.vucelich.villagerworkers.neoforge;

import com.mojang.serialization.Codec;
import com.vucelich.villagerworkers.common.VillagerWorkers;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerRank;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WorkerAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerWorkers.MOD_ID);

    public static final Supplier<AttachmentType<WorkerData>> WORKER = ATTACHMENTS.register("worker", () -> AttachmentType
            .<WorkerData>builder((IAttachmentHolder holder) -> {
                throw new IllegalStateException("Worker data is only created when a villager is hired");
            })
            .serialize(WorkerData.CODEC)
            .copyHandler((data, holder, provider) -> null)
            .build());

    /**
     * Rank only, synced so other players can see the hard hat. The trusted list stays on {@link #WORKER} and is not synced.
     */
    public static final Supplier<AttachmentType<String>> LOOK = ATTACHMENTS.register("look", () -> AttachmentType
            .<String>builder((IAttachmentHolder holder) -> {
                throw new IllegalStateException("A hard hat is only stored after a villager is hired");
            })
            .serialize(Codec.STRING)
            .sync(ByteBufCodecs.STRING_UTF8)
            .copyHandler((data, holder, provider) -> null)
            .build());

    private WorkerAttachments() {}

    public static boolean isWorker(Villager villager) {
        return villager.hasData(WORKER);
    }

    public static Optional<WorkerData> get(Villager villager) {
        if (!isWorker(villager)) {
            return Optional.empty();
        }
        return Optional.of(villager.getData(WORKER));
    }

    public static void hire(Villager villager, WorkerData data) {
        villager.setData(WORKER, data);
        applyLook(villager, data);
    }

    /** Writes the synced rank. Hiring, promoting, and the first tick of an older save all use this. */
    public static void applyLook(Villager villager, WorkerData data) {
        String id = (data == null ? WorkerRank.WORKER : data.rank()).id();
        if (!villager.hasData(LOOK) || !id.equals(villager.getData(LOOK))) {
            villager.setData(LOOK, id);
        }
    }
}
