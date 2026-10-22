package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.StatusText;
import com.vucelich.villagerworkers.common.WorkerData;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public record WorkerView(
        java.util.UUID villagerId,
        int entityId,
        String name,
        int health,
        int maxHealth,
        String job,
        String activity,
        boolean rested,
        String problem,
        boolean viewerIsOwner,
        boolean viewerIsOriginalOwner,
        boolean createLoaded,
        String areaText,
        boolean clearLeaves,
        boolean replant,
        String logChest,
        String saplingChest,
        String lumberToolChest,
        String minerMode,
        String originText,
        int width,
        int height,
        int length,
        String wallBlock,
        int branchSpacing,
        int branchLength,
        int shaftWidth,
        int shaftDepth,
        boolean shaftUp,
        String valuables,
        String valuablesChest,
        String regularChest,
        String minerToolChest,
        String whitelist,
        String schematic,
        String materialChests,
        boolean useValuables,
        String rank,
        String foremanLabel,
        String foremenLine,
        String unassignedLine,
        String crewLine,
        String scaffoldOrder,
        boolean plainBlocks,
        boolean rulesOverride,
        String structureText,
        String materialsText) {
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerView> STREAM_CODEC = StreamCodec.of(
            (buf, view) -> view.write(buf),
            WorkerView::read);

    public static WorkerView capture(Villager villager, WorkerData data, Player viewer) {
        boolean hungry = WorkerTicker.hungry(villager);
        boolean hurt = WorkerTicker.hurt(villager);
        var lumber = data.lumberjack();
        var miner = data.miner();
        var builder = data.builder();
        String area = (data.job() == com.vucelich.villagerworkers.common.WorkerJob.MINER ? miner.area() : lumber.area())
                .map(a -> a.format())
                .orElse("not marked");
        WorkerServer.CrewDirectory crew = WorkerServer.directory(villager, data, viewer);
        return new WorkerView(
                villager.getUUID(),
                villager.getId(),
                villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString(),
                (int) villager.getHealth(),
                (int) villager.getMaxHealth(),
                data.job().id(),
                data.activity().id(),
                data.rested(),
                StatusText.problem(hungry, hurt, data.problem()),
                data.isOwner(viewer.getUUID()),
                data.originalOwner() != null && data.originalOwner().equals(viewer.getUUID()),
                ModList.get().isLoaded("create"),
                area,
                lumber.clearLeaves(),
                lumber.replantSaplings(),
                pos(lumber.logChest()),
                pos(lumber.saplingChest()),
                pos(lumber.toolChest()),
                miner.mode().id(),
                miner.origin().map(o -> o.format()).orElse("not marked"),
                miner.width(),
                miner.height(),
                miner.length(),
                miner.wallBlock() == null ? "" : miner.wallBlock(),
                miner.branchSpacing(),
                miner.branchLength(),
                miner.shaftWidth(),
                miner.shaftDepth(),
                miner.shaftUp(),
                miner.valuables().stream().map(id -> id.toString()).collect(Collectors.joining(", ")),
                pos(miner.valuablesChest()),
                pos(miner.regularChest()),
                pos(miner.toolChest()),
                data.breakWhitelist().stream().map(id -> id.toString()).collect(Collectors.joining(", ")),
                pos(builder.schematicAnchor()),
                builder.materialChests().isEmpty()
                        ? "none"
                        : builder.materialChests().stream().map(WorkerView::pos).collect(Collectors.joining(", ")),
                miner.useValuables(),
                data.rank().id(),
                crew.foremanLabel(),
                crew.foremen(),
                crew.unassigned(),
                crew.crew(),
                data.scaffoldOrder(),
                data.plainBlocks(),
                data.rulesOverride(),
                structureLine(builder),
                villager.level() instanceof net.minecraft.server.level.ServerLevel server
                        ? StructureMaterials.summarize(server, builder)
                        : "no structure file");
    }

    private static String structureLine(com.vucelich.villagerworkers.common.BuilderConfig builder) {
        if (!builder.hasStructure()) {
            return "no structure file";
        }
        String layers = builder.limitsLayers() ? ", layers " + builder.layerMin() + "-" + builder.layerMax() : "";
        return "origin " + pos(builder.structureOrigin()) + ", " + builder.pieces().size() + " pieces" + layers;
    }

    private static String pos(Optional<BlockPos> pos) {
        return pos.map(WorkerView::pos).orElse("none");
    }

    private static String pos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(villagerId);
        buf.writeVarInt(entityId);
        buf.writeUtf(name);
        buf.writeVarInt(health);
        buf.writeVarInt(maxHealth);
        buf.writeUtf(job);
        buf.writeUtf(activity);
        buf.writeBoolean(rested);
        buf.writeUtf(problem);
        buf.writeBoolean(viewerIsOwner);
        buf.writeBoolean(viewerIsOriginalOwner);
        buf.writeBoolean(createLoaded);
        buf.writeUtf(areaText);
        buf.writeBoolean(clearLeaves);
        buf.writeBoolean(replant);
        buf.writeUtf(logChest);
        buf.writeUtf(saplingChest);
        buf.writeUtf(lumberToolChest);
        buf.writeUtf(minerMode);
        buf.writeUtf(originText);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        buf.writeVarInt(length);
        buf.writeUtf(wallBlock);
        buf.writeVarInt(branchSpacing);
        buf.writeVarInt(branchLength);
        buf.writeVarInt(shaftWidth);
        buf.writeVarInt(shaftDepth);
        buf.writeBoolean(shaftUp);
        buf.writeUtf(valuables);
        buf.writeUtf(valuablesChest);
        buf.writeUtf(regularChest);
        buf.writeUtf(minerToolChest);
        buf.writeUtf(whitelist);
        buf.writeUtf(schematic);
        buf.writeUtf(materialChests);
        buf.writeBoolean(useValuables);
        buf.writeUtf(rank);
        buf.writeUtf(foremanLabel);
        buf.writeUtf(foremenLine);
        buf.writeUtf(unassignedLine);
        buf.writeUtf(crewLine);
        buf.writeUtf(scaffoldOrder);
        buf.writeBoolean(plainBlocks);
        buf.writeBoolean(rulesOverride);
        buf.writeUtf(structureText);
        buf.writeUtf(materialsText);
    }

    public static WorkerView read(RegistryFriendlyByteBuf buf) {
        return new WorkerView(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf());
    }
}
