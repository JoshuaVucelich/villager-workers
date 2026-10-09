package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.MinerMode;
import com.vucelich.villagerworkers.common.WorkArea;
import com.vucelich.villagerworkers.common.WorkOrigin;
import com.vucelich.villagerworkers.common.WorkerAction;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerJob;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived "mark area" and "link chest" sessions. The menu button that arms them is permission checked.
 * The later click can happen after the screen is closed, which is the only way to point at a block.
 */
public final class SetupSessions {
    public enum LinkTarget {
        LOG,
        SAPLING,
        LUMBER_TOOL,
        VALUABLES,
        REGULAR,
        MINER_TOOL,
        SCHEMATIC,
        MATERIAL
    }

    private record Session(UUID villagerId, boolean origin, LinkTarget link, BlockPos firstCorner, ResourceLocation dimension, long expiresAt) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final long TTL_MS = 120_000L;
    private static final double CHEST_RANGE_SQR = 64.0;

    private SetupSessions() {}

    public static void clear(UUID playerId) {
        if (playerId != null) {
            SESSIONS.remove(playerId);
        }
    }

    public static void armMark(ServerPlayer player, Villager villager, WorkerData data) {
        boolean origin = data.job() == WorkerJob.MINER && data.miner().mode() != MinerMode.CLEAR_BOX;
        boolean area = data.job() == WorkerJob.LUMBERJACK || (data.job() == WorkerJob.MINER && data.miner().mode() == MinerMode.CLEAR_BOX);
        if (!origin && !area) {
            player.sendSystemMessage(Component.literal("Pick the lumberjack, or a miner mode, before marking."));
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(villager.getUUID(), origin, null, null, null, System.currentTimeMillis() + TTL_MS));
        player.sendSystemMessage(Component.literal(origin
                ? "Right-click the start block with the area wand."
                : "Right-click two corners with the area wand."));
    }

    public static void armLink(ServerPlayer player, Villager villager, LinkTarget target) {
        SESSIONS.put(player.getUUID(), new Session(villager.getUUID(), false, target, null, null, System.currentTimeMillis() + TTL_MS));
        String hint = target == LinkTarget.SCHEMATIC
                ? "Right-click a Create schematic block within 8 blocks of the worker."
                : "Right-click a chest or barrel within 8 blocks of the worker.";
        player.sendSystemMessage(Component.literal(hint));
    }

    public static InteractionResult useWand(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        Session session = fresh(player.getUUID());
        if (session == null || session.link != null) {
            player.sendSystemMessage(Component.literal("Select Mark area on a worker first."));
            return InteractionResult.SUCCESS;
        }
        Villager villager = WorkerServer.findWorker(player, session.villagerId);
        if (villager == null) {
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("That worker is not here."));
            return InteractionResult.SUCCESS;
        }
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        if (data == null || !WorkerServer.allows(data, player, WorkerAction.EDIT_SETUP)) {
            player.sendSystemMessage(Component.literal("You cannot do that."));
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        ResourceLocation dimension = player.level().dimension().location();
        if (session.origin) {
            data.setMiner(data.miner().withOrigin(Optional.of(new WorkOrigin(dimension, pos, player.getDirection().getName()))));
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Origin saved."));
        } else if (session.firstCorner == null) {
            SESSIONS.put(player.getUUID(), new Session(session.villagerId, false, null, pos, dimension, session.expiresAt));
            player.sendSystemMessage(Component.literal("First corner set. Right-click the opposite corner."));
        } else {
            if (session.dimension != null && !session.dimension.equals(dimension)) {
                player.sendSystemMessage(Component.literal("Both corners must be in the same dimension."));
                return InteractionResult.SUCCESS;
            }
            WorkArea area = new WorkArea(dimension, session.firstCorner, pos).normalized();
            if (data.job() == WorkerJob.MINER) {
                data.setMiner(data.miner().withArea(Optional.of(area)));
            } else {
                data.setLumberjack(data.lumberjack().withArea(Optional.of(area)));
            }
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Area saved."));
        }
        WorkerServer.refresh(player, villager);
        return InteractionResult.SUCCESS;
    }

    public static boolean useLink(ServerPlayer player, BlockPos pos) {
        Session session = fresh(player.getUUID());
        if (session == null || session.link == null) {
            return false;
        }
        Villager villager = WorkerServer.findWorker(player, session.villagerId);
        if (villager == null) {
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("That worker is not here."));
            return true;
        }
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        if (data == null || !WorkerServer.allows(data, player, WorkerAction.EDIT_SETUP)) {
            player.sendSystemMessage(Component.literal("You cannot do that."));
            return true;
        }
        if (player.level() != villager.level()) {
            player.sendSystemMessage(Component.literal("Stay in the worker's dimension."));
            return true;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(pos)) > CHEST_RANGE_SQR) {
            player.sendSystemMessage(Component.literal("That block is more than 8 blocks from the worker."));
            return true;
        }
        if (!player.level().mayInteract(player, pos)) {
            player.sendSystemMessage(Component.literal("You cannot interact with that block."));
            return true;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof BaseContainerBlockEntity container && !container.canOpen(player)) {
            return true;
        }
        BlockState state = player.level().getBlockState(pos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (session.link == LinkTarget.SCHEMATIC) {
            if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
                player.sendSystemMessage(Component.literal("Create is not installed, so there is no schematic to link."));
                return true;
            }
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                    || !CreateSchematics.containsSchematic(level, pos)) {
                player.sendSystemMessage(Component.literal("Link a schematicannon that already has a schematic."));
                return true;
            }
            data.setBuilder(data.builder().withSchematic(Optional.of(pos)));
            player.sendSystemMessage(Component.literal("Schematic linked."));
        } else if (!isLinkableChest(state, id)) {
            player.sendSystemMessage(Component.literal("That is not a chest or barrel."));
            return true;
        } else {
            linkChest(data, session.link, pos);
            player.sendSystemMessage(Component.literal("Chest linked."));
        }
        SESSIONS.remove(player.getUUID());
        WorkerServer.refresh(player, villager);
        return true;
    }

    private static boolean isLinkableChest(BlockState state, ResourceLocation id) {
        if ("create".equals(id.getNamespace())) {
            return false;
        }
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock;
    }

    private static void linkChest(WorkerData data, LinkTarget target, BlockPos pos) {
        Optional<BlockPos> linked = Optional.of(pos);
        switch (target) {
            case LOG -> data.setLumberjack(data.lumberjack().withChest(com.vucelich.villagerworkers.common.LumberjackConfig.ChestSlot.LOG, linked));
            case SAPLING -> data.setLumberjack(data.lumberjack().withChest(com.vucelich.villagerworkers.common.LumberjackConfig.ChestSlot.SAPLING, linked));
            case LUMBER_TOOL -> data.setLumberjack(data.lumberjack().withChest(com.vucelich.villagerworkers.common.LumberjackConfig.ChestSlot.TOOL, linked));
            case VALUABLES -> data.setMiner(data.miner().withChest(com.vucelich.villagerworkers.common.MinerConfig.ChestSlot.VALUABLES, linked));
            case REGULAR -> data.setMiner(data.miner().withChest(com.vucelich.villagerworkers.common.MinerConfig.ChestSlot.REGULAR, linked));
            case MINER_TOOL -> data.setMiner(data.miner().withChest(com.vucelich.villagerworkers.common.MinerConfig.ChestSlot.TOOL, linked));
            case MATERIAL -> data.setBuilder(data.builder().withMaterialChest(pos));
            case SCHEMATIC -> {
            }
        }
    }

    private static Session fresh(UUID playerId) {
        Session session = SESSIONS.get(playerId);
        if (session == null) {
            return null;
        }
        if (System.currentTimeMillis() > session.expiresAt) {
            SESSIONS.remove(playerId);
            return null;
        }
        return session;
    }
}
