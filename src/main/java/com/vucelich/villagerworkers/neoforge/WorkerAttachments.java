package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.VillagerWorkers;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerLook;
import java.util.Optional;
import java.util.function.Supplier;
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
     * Hat color and the optional foreman label. Synced to tracking clients. The trusted list stays on {@link #WORKER}.
     */
    public static final Supplier<AttachmentType<WorkerLook>> LOOK = ATTACHMENTS.register("look", () -> AttachmentType
            .builder(() -> new WorkerLook(false, 0, ""))
            .serialize(WorkerLook.CODEC)
            .copyHandler((look, holder, provider) -> null)
            .sync(WorkerLook.STREAM_CODEC)
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
    }
}
