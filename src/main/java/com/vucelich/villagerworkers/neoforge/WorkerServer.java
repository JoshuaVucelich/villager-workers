package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ActivityRules;
import com.vucelich.villagerworkers.common.ChatCommands;
import com.vucelich.villagerworkers.common.MinerConfig;
import com.vucelich.villagerworkers.common.MinerMode;
import com.vucelich.villagerworkers.common.SetupLimits;
import com.vucelich.villagerworkers.common.ValuableCatalog;
import com.vucelich.villagerworkers.common.StatusText;
import com.vucelich.villagerworkers.common.TrustedPlayer;
import com.vucelich.villagerworkers.common.WorkerAction;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerJob;
import com.vucelich.villagerworkers.common.WorkerPermissions;
import com.vucelich.villagerworkers.neoforge.ModPayloads.AddTrustedPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.PermissionsView;
import com.vucelich.villagerworkers.neoforge.ModPayloads.Person;
import com.vucelich.villagerworkers.neoforge.ModPayloads.RemoveTrustedPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.TransferOwnerPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.UpdateLumberjackPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.UpdateMinerPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.UpdateWhitelistPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.WorkerSnapshotPayload;
import com.vucelich.villagerworkers.neoforge.SetupSessions.LinkTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WorkerServer {
    private static final long HIRE_TIMEOUT_MS = 60_000L;
    private static final List<String> WALLS = List.of(
            "",
            "minecraft:cobblestone",
            "minecraft:stone",
            "minecraft:oak_planks",
            "minecraft:deepslate",
            "minecraft:cobbled_deepslate");
    private static final java.util.Map<UUID, PendingHire> PENDING_HIRES = new java.util.concurrent.ConcurrentHashMap<>();

    private record PendingHire(UUID villagerId, long expiresAt) {}

    /** Ages a pending hire so tests can check the timeout without waiting a minute. */
    static void expirePendingHire(UUID playerId) {
        PendingHire pending = PENDING_HIRES.remove(playerId);
        if (pending != null) {
            PENDING_HIRES.put(playerId, new PendingHire(pending.villagerId(), System.currentTimeMillis() - 5_000L));
        }
    }

    private WorkerServer() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager villager)) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.is(Items.NAME_TAG)) {
            Component intended = stack.get(DataComponents.CUSTOM_NAME);
            if (intended != null && !villager.isBaby() && !WorkerAttachments.isWorker(villager)) {
                scheduleHireOffer(player, villager, intended.getString());
            }
            return;
        }
        if (player.isSecondaryUseActive() || !WorkerAttachments.isWorker(villager)) {
            return;
        }
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        if (data == null || !allows(data, player, WorkerAction.OPEN_SCREEN) || player.distanceToSqr(villager) > WorkerMenu.RANGE_SQR) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        openMenu(player, villager, data);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (SetupSessions.useLink(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static void onTickBefore(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()) {
            return;
        }
        if (WorkerAttachments.isWorker(villager)) {
            WorkerHold.preTick(villager);
        }
    }

    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()) {
            return;
        }
        WorkerAttachments.get(villager).ifPresent(data -> WorkerTicker.tick(villager, data));
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PENDING_HIRES.remove(id);
        SetupSessions.clear(id);
        WorkerHold.releasePlayer(id);
    }

    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Villager villager && WorkerAttachments.isWorker(villager)) {
            WorkerHold.forget(villager.getUUID());
            JobRunner.onDiscarded(villager);
        }
    }

    public static void onChat(ServerChatEvent event) {
        String raw = event.getRawText();
        if (raw == null || !raw.startsWith("@")) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<Villager> workers = loadedWorkers(level);
        List<String> names = new ArrayList<>(workers.size());
        for (Villager villager : workers) {
            names.add(displayName(villager));
        }
        Optional<ChatCommands.Match> found = ChatCommands.match(raw, names);
        if (found.isEmpty()) {
            return;
        }
        ChatCommands.Match match = found.get();
        if (match.ambiguous()) {
            player.sendSystemMessage(Component.literal("More than one worker is named " + match.name() + "."));
            return;
        }
        Villager villager = workers.get(match.workerIndexes().get(0));
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        if (data == null || !allows(data, player, WorkerAction.CHAT)) {
            player.sendSystemMessage(Component.literal("You cannot command " + match.name() + "."));
            return;
        }
        if (match.verb().isEmpty()) {
            player.sendSystemMessage(Component.literal("Unknown command. Use come, stop, resume, or status."));
            return;
        }
        switch (match.verb().get()) {
            case COME -> data.comeTo(player.getUUID());
            case STOP -> data.setActivity(ActivityRules.afterStop());
            case RESUME -> {
                Optional<WorkerActivity> next = ActivityRules.afterResume(data.job(), net.neoforged.fml.ModList.get().isLoaded("create"));
                if (next.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Create is required before this worker can build."));
                    return;
                }
                data.setActivity(next.get());
            }
            case STATUS -> {
                player.sendSystemMessage(Component.literal(statusLine(villager, data)));
                return;
            }
        }
        player.sendSystemMessage(Component.literal(statusLine(villager, data)));
    }

    public static void chooseHire(Player player, UUID villagerId, boolean hire) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PendingHire pending = PENDING_HIRES.get(serverPlayer.getUUID());
        long now = System.currentTimeMillis();
        if (pending == null || !pending.villagerId.equals(villagerId) || now > pending.expiresAt) {
            if (pending != null && now > pending.expiresAt) {
                PENDING_HIRES.remove(serverPlayer.getUUID());
            }
            serverPlayer.sendSystemMessage(Component.literal("That hire offer expired."));
            return;
        }
        Villager villager = findWorkerEntity(serverPlayer, villagerId);
        if (!(villager instanceof Villager) || serverPlayer.distanceToSqr(villager) > WorkerMenu.RANGE_SQR) {
            serverPlayer.sendSystemMessage(Component.literal("That hire offer expired."));
            return;
        }
        PENDING_HIRES.remove(serverPlayer.getUUID());
        if (!hire) {
            serverPlayer.sendSystemMessage(Component.literal("Kept as a named villager."));
            return;
        }
        if (WorkerAttachments.isWorker(villager)) {
            serverPlayer.sendSystemMessage(Component.literal("Already a worker."));
            return;
        }
        WorkerAttachments.hire(villager, WorkerData.hire(serverPlayer.getUUID()));
        serverPlayer.sendSystemMessage(Component.literal("Hired. You own this worker."));
    }

    public static boolean handleButton(Player player, WorkerMenu menu, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Villager villager = menu.villager();
        WorkerData data = guarded(serverPlayer, villager, WorkerAction.OPEN_SCREEN);
        if (data == null) {
            return false;
        }
        boolean setup = id != MenuIds.START && id != MenuIds.STOP && id != MenuIds.COME && id != MenuIds.SHOW_PERMISSIONS;
        WorkerAction action = switch (id) {
            case MenuIds.START, MenuIds.STOP, MenuIds.COME -> WorkerAction.SET_ACTIVITY;
            case MenuIds.SHOW_PERMISSIONS -> WorkerAction.VIEW_PERMISSIONS;
            default -> WorkerAction.EDIT_SETUP;
        };
        if (setup && id != MenuIds.SHOW_PERMISSIONS) {
            action = WorkerAction.EDIT_SETUP;
        }
        if (!allows(data, serverPlayer, action)) {
            deny(serverPlayer);
            return false;
        }
        switch (id) {
            case MenuIds.START -> {
                if (!applyActivity(serverPlayer, data, ActivityRules.afterStart(data.job(), net.neoforged.fml.ModList.get().isLoaded("create")))) {
                    return false;
                }
            }
            case MenuIds.STOP -> data.setActivity(ActivityRules.afterStop());
            case MenuIds.COME -> data.comeTo(serverPlayer.getUUID());
            case MenuIds.JOB_NONE -> data.setJob(WorkerJob.NONE);
            case MenuIds.JOB_LUMBERJACK -> data.setJob(WorkerJob.LUMBERJACK);
            case MenuIds.JOB_MINER -> data.setJob(WorkerJob.MINER);
            case MenuIds.JOB_BUILDER -> data.setJob(WorkerJob.BUILDER);
            case MenuIds.TOGGLE_LEAVES -> data.setLumberjack(data.lumberjack().withFlags(!data.lumberjack().clearLeaves(), data.lumberjack().replantSaplings()));
            case MenuIds.TOGGLE_REPLANT -> data.setLumberjack(data.lumberjack().withFlags(data.lumberjack().clearLeaves(), !data.lumberjack().replantSaplings()));
            case MenuIds.MARK -> SetupSessions.armMark(serverPlayer, villager, data);
            case MenuIds.LINK_LOG -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.LOG);
            case MenuIds.LINK_SAPLING -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.SAPLING);
            case MenuIds.LINK_LUMBER_TOOL -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.LUMBER_TOOL);
            case MenuIds.LINK_VALUABLES -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.VALUABLES);
            case MenuIds.LINK_REGULAR -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.REGULAR);
            case MenuIds.LINK_MINER_TOOL -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.MINER_TOOL);
            case MenuIds.LINK_SCHEMATIC -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.SCHEMATIC);
            case MenuIds.LINK_MATERIAL -> SetupSessions.armLink(serverPlayer, villager, LinkTarget.MATERIAL);
            case MenuIds.SHOW_PERMISSIONS -> {
                sendPermissions(serverPlayer, villager, data);
                return true;
            }
            case MenuIds.CYCLE_MODE -> data.setMiner(data.miner().withMode(data.miner().mode().next()));
            case MenuIds.WIDTH_DOWN -> stepMiner(data, -2, 0, 0, 0, 0, 0, 0);
            case MenuIds.WIDTH_UP -> stepMiner(data, 2, 0, 0, 0, 0, 0, 0);
            case MenuIds.HEIGHT_DOWN -> stepMiner(data, 0, -1, 0, 0, 0, 0, 0);
            case MenuIds.HEIGHT_UP -> stepMiner(data, 0, 1, 0, 0, 0, 0, 0);
            case MenuIds.LENGTH_DOWN -> stepMiner(data, 0, 0, -1, 0, 0, 0, 0);
            case MenuIds.LENGTH_UP -> stepMiner(data, 0, 0, 1, 0, 0, 0, 0);
            case MenuIds.BRANCH_SPACING_DOWN -> stepMiner(data, 0, 0, 0, -1, 0, 0, 0);
            case MenuIds.BRANCH_SPACING_UP -> stepMiner(data, 0, 0, 0, 1, 0, 0, 0);
            case MenuIds.BRANCH_LENGTH_DOWN -> stepMiner(data, 0, 0, 0, 0, -1, 0, 0);
            case MenuIds.BRANCH_LENGTH_UP -> stepMiner(data, 0, 0, 0, 0, 1, 0, 0);
            case MenuIds.SHAFT_WIDTH_DOWN -> stepMiner(data, 0, 0, 0, 0, 0, -1, 0);
            case MenuIds.SHAFT_WIDTH_UP -> stepMiner(data, 0, 0, 0, 0, 0, 1, 0);
            case MenuIds.SHAFT_DEPTH_DOWN -> stepMiner(data, 0, 0, 0, 0, 0, 0, -1);
            case MenuIds.SHAFT_DEPTH_UP -> stepMiner(data, 0, 0, 0, 0, 0, 0, 1);
            case MenuIds.CYCLE_WALL -> cycleWall(data);
            case MenuIds.TOGGLE_SHAFT -> {
                MinerConfig miner = data.miner();
                data.setMiner(miner.withNumbers(
                        miner.mode(),
                        miner.width(),
                        miner.height(),
                        miner.length(),
                        miner.wallBlock(),
                        miner.branchSpacing(),
                        miner.branchLength(),
                        miner.shaftWidth(),
                        miner.shaftDepth(),
                        !miner.shaftUp(),
                        miner.valuables()));
            }
            case MenuIds.TOGGLE_VALUABLES -> data.setMiner(data.miner().withUseValuables(!data.miner().useValuables()));
            case MenuIds.WHITELIST_HELD -> {
                if (!toggleHeldBlock(serverPlayer, data)) {
                    return true;
                }
            }
            case MenuIds.WHITELIST_CLEAR -> {
                data.setBreakWhitelist(List.of());
                serverPlayer.sendSystemMessage(Component.literal("Extra blocks cleared."));
            }
            default -> {
                if (id < MenuIds.VALUABLE_BASE) {
                    return false;
                }
                if (!toggleValuable(data, id - MenuIds.VALUABLE_BASE)) {
                    return false;
                }
            }
        }
        refresh(serverPlayer, villager);
        return true;
    }

    private static void stepMiner(
            WorkerData data, int dWidth, int dHeight, int dLength, int dSpacing, int dBranch, int dShaftWidth, int dShaftDepth) {
        MinerConfig miner = data.miner();
        data.setMiner(miner.withNumbers(
                miner.mode(),
                miner.width() + dWidth,
                miner.height() + dHeight,
                miner.length() + dLength,
                miner.wallBlock(),
                miner.branchSpacing() + dSpacing,
                miner.branchLength() + dBranch,
                miner.shaftWidth() + dShaftWidth,
                miner.shaftDepth() + dShaftDepth,
                miner.shaftUp(),
                miner.valuables()));
    }

    private static void cycleWall(WorkerData data) {
        MinerConfig miner = data.miner();
        String current = miner.wallBlock() == null ? "" : miner.wallBlock();
        int index = WALLS.indexOf(current);
        String next = WALLS.get((Math.max(index, 0) + 1) % WALLS.size());
        data.setMiner(miner.withNumbers(
                miner.mode(),
                miner.width(),
                miner.height(),
                miner.length(),
                next,
                miner.branchSpacing(),
                miner.branchLength(),
                miner.shaftWidth(),
                miner.shaftDepth(),
                miner.shaftUp(),
                miner.valuables()));
    }

    private static boolean toggleValuable(WorkerData data, int index) {
        List<ResourceLocation> catalog = ValuableCatalog.entries();
        if (index < 0 || index >= catalog.size()) {
            return false;
        }
        ResourceLocation chosen = catalog.get(index);
        List<ResourceLocation> current = new ArrayList<>(data.miner().valuables());
        if (!current.remove(chosen)) {
            if (current.size() >= SetupLimits.MAX_LIST) {
                return false;
            }
            current.add(chosen);
        }
        data.setMiner(data.miner().withValuables(current));
        return true;
    }

    /** @return false when the player was told to hold a block and nothing changed */
    private static boolean toggleHeldBlock(ServerPlayer player, WorkerData data) {
        ItemStack held = player.getMainHandItem();
        Block block = Block.byItem(held.getItem());
        if (held.isEmpty() || block == Blocks.AIR) {
            player.sendSystemMessage(Component.literal("Hold a block, then press this again."));
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        List<ResourceLocation> ids = new ArrayList<>(data.breakWhitelist());
        if (!ids.remove(blockId)) {
            if (ids.size() >= SetupLimits.MAX_LIST) {
                player.sendSystemMessage(Component.literal("That list is full."));
                return false;
            }
            ids.add(blockId);
            player.sendSystemMessage(Component.literal("This worker may break " + blockId + "."));
        } else {
            player.sendSystemMessage(Component.literal("Removed " + blockId + " from the extra blocks."));
        }
        data.setBreakWhitelist(ids);
        return true;
    }

    public static void updateLumberjack(Player player, UpdateLumberjackPayload payload) {
        WorkerData data = menuWorker(player, payload.villagerId(), WorkerAction.EDIT_SETUP);
        if (data == null) {
            return;
        }
        data.setLumberjack(data.lumberjack().withFlags(payload.clearLeaves(), payload.replant()));
        refresh((ServerPlayer) player, ((WorkerMenu) player.containerMenu).villager());
    }

    public static void updateMiner(Player player, UpdateMinerPayload payload) {
        WorkerData data = menuWorker(player, payload.villagerId(), WorkerAction.EDIT_SETUP);
        if (data == null) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String part : payload.valuables().split(",")) {
            if (!part.isBlank()) {
                ids.add(part);
            }
        }
        List<ResourceLocation> valuables = SetupLimits.resourceIds(ids, false).stream().map(ResourceLocation::parse).toList();
        data.setMiner(data.miner().withNumbers(
                MinerMode.byId(payload.mode()),
                payload.width(),
                payload.height(),
                payload.length(),
                payload.wallBlock(),
                payload.branchSpacing(),
                payload.branchLength(),
                payload.shaftWidth(),
                payload.shaftDepth(),
                payload.shaftUp(),
                valuables));
        refresh((ServerPlayer) player, ((WorkerMenu) player.containerMenu).villager());
    }

    public static void touchBuilder(Player player, UUID villagerId) {
        WorkerData data = menuWorker(player, villagerId, WorkerAction.EDIT_SETUP);
        if (data == null) {
            return;
        }
        refresh((ServerPlayer) player, ((WorkerMenu) player.containerMenu).villager());
    }

    public static void updateWhitelist(Player player, UpdateWhitelistPayload payload) {
        WorkerData data = menuWorker(player, payload.villagerId(), WorkerAction.EDIT_SETUP);
        if (data == null) {
            return;
        }
        List<ResourceLocation> ids = SetupLimits.resourceIds(payload.ids(), false).stream().map(ResourceLocation::parse).toList();
        data.setBreakWhitelist(ids);
        ((ServerPlayer) player).sendSystemMessage(Component.literal("Whitelist saved (" + ids.size() + ")."));
        refresh((ServerPlayer) player, ((WorkerMenu) player.containerMenu).villager());
    }

    public static void addTrusted(Player player, AddTrustedPayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Villager villager = menuVillager(serverPlayer, payload.villagerId());
        WorkerData data = guarded(serverPlayer, villager, WorkerAction.EDIT_TRUSTED);
        if (data == null) {
            return;
        }
        ServerPlayer match = null;
        for (ServerPlayer online : serverPlayer.server.getPlayerList().getPlayers()) {
            if (online.getGameProfile().getName().equalsIgnoreCase(payload.playerName().trim())) {
                match = online;
                break;
            }
        }
        if (match == null) {
            serverPlayer.sendSystemMessage(Component.literal("That player is not online."));
            return;
        }
        if (match.getUUID().equals(data.owner())) {
            serverPlayer.sendSystemMessage(Component.literal("You already own this worker."));
            return;
        }
        if (!data.addTrusted(new TrustedPlayer(match.getUUID(), match.getGameProfile().getName()))) {
            serverPlayer.sendSystemMessage(Component.literal("That player is already trusted, or the list is full."));
            return;
        }
        serverPlayer.sendSystemMessage(Component.literal(match.getGameProfile().getName() + " can control this worker."));
        sendPermissions(serverPlayer, villager, data);
    }

    public static void removeTrusted(Player player, RemoveTrustedPayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Villager villager = menuVillager(serverPlayer, payload.villagerId());
        WorkerData data = guarded(serverPlayer, villager, WorkerAction.EDIT_TRUSTED);
        if (data == null) {
            return;
        }
        if (!data.removeTrusted(payload.trustedId())) {
            serverPlayer.sendSystemMessage(Component.literal("That player is not trusted."));
            return;
        }
        sendPermissions(serverPlayer, villager, data);
    }

    public static void transfer(Player player, TransferOwnerPayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Villager villager = menuVillager(serverPlayer, payload.villagerId());
        WorkerData data = guarded(serverPlayer, villager, WorkerAction.TRANSFER_OWNER);
        if (data == null) {
            return;
        }
        ServerPlayer target = serverPlayer.server.getPlayerList().getPlayer(payload.newOwner());
        if (target == null) {
            serverPlayer.sendSystemMessage(Component.literal("That player is not online."));
            return;
        }
        if (target.level() != villager.level()) {
            serverPlayer.sendSystemMessage(Component.literal("That player must be in the same dimension."));
            return;
        }
        if (target.getUUID().equals(data.owner())) {
            serverPlayer.sendSystemMessage(Component.literal("You already own this worker."));
            return;
        }
        data.removeTrusted(target.getUUID());
        data.setOwner(target.getUUID());
        String workerName = displayName(villager);
        serverPlayer.sendSystemMessage(Component.literal("Ownership of " + workerName + " was transferred."));
        target.sendSystemMessage(Component.literal(workerName + " is now your worker."));
        serverPlayer.closeContainer();
    }

    public static boolean allows(WorkerData data, ServerPlayer player, WorkerAction action) {
        return WorkerPermissions.allows(data.owner(), data.trustedIds(), player.getUUID(), action);
    }

    public static Villager findWorker(ServerPlayer player, UUID villagerId) {
        Entity entity = player.serverLevel().getEntity(villagerId);
        if (entity instanceof Villager villager && WorkerAttachments.isWorker(villager)) {
            return villager;
        }
        return null;
    }

    public static void refresh(ServerPlayer player, Villager villager) {
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        if (data == null || !(player.containerMenu instanceof WorkerMenu menu) || menu.villager() != villager) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new WorkerSnapshotPayload(WorkerView.capture(villager, data, player)));
    }

    static void openMenu(ServerPlayer player, Villager villager, WorkerData data) {
        WorkerView view = WorkerView.capture(villager, data, player);
        OptionalInt opened = player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new WorkerMenu(id, inventory, villager, view),
                Component.literal(view.name())), buf -> WorkerView.STREAM_CODEC.encode(buf, view));
        if (opened.isPresent()) {
            WorkerHold.watch(villager, player);
        }
    }

    private static void scheduleHireOffer(ServerPlayer player, Villager villager, String intendedName) {
        UUID playerId = player.getUUID();
        UUID villagerId = villager.getUUID();
        net.minecraft.server.MinecraftServer server = player.server;
        // Vanilla applies the name tag after this event returns. Queue the next tick, or the name is still the old one.
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online == null) {
                return;
            }
            Entity entity = online.serverLevel().getEntity(villagerId);
            if (!(entity instanceof Villager named) || named.isBaby() || WorkerAttachments.isWorker(named)) {
                return;
            }
            Component current = named.getCustomName();
            if (current == null || !current.getString().equals(intendedName)) {
                return;
            }
            PENDING_HIRES.put(playerId, new PendingHire(villagerId, System.currentTimeMillis() + HIRE_TIMEOUT_MS));
            PacketDistributor.sendToPlayer(online, new ModPayloads.OpenHireScreenPayload(villagerId, current.getString()));
        }));
    }

    private static Villager findWorkerEntity(ServerPlayer player, UUID villagerId) {
        Entity entity = player.serverLevel().getEntity(villagerId);
        return entity instanceof Villager villager ? villager : null;
    }

    private static WorkerData menuWorker(Player player, UUID villagerId, WorkerAction action) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        return guarded(serverPlayer, menuVillager(serverPlayer, villagerId), action);
    }

    private static Villager menuVillager(ServerPlayer player, UUID villagerId) {
        if (!(player.containerMenu instanceof WorkerMenu menu) || menu.villager() == null) {
            deny(player);
            return null;
        }
        Villager villager = menu.villager();
        if (!villager.getUUID().equals(villagerId)) {
            deny(player);
            return null;
        }
        return villager;
    }

    private static WorkerData guarded(ServerPlayer player, Villager villager, WorkerAction action) {
        if (villager == null || !villager.isAlive() || player.level() != villager.level() || player.distanceToSqr(villager) > WorkerMenu.RANGE_SQR) {
            deny(player);
            return null;
        }
        if (!(player.containerMenu instanceof WorkerMenu menu) || menu.villager() != villager) {
            deny(player);
            return null;
        }
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        if (data == null || !allows(data, player, action)) {
            deny(player);
            return null;
        }
        return data;
    }

    private static boolean applyActivity(ServerPlayer player, WorkerData data, Optional<WorkerActivity> next) {
        if (next.isEmpty()) {
            player.sendSystemMessage(Component.literal("Create is required before this worker can build."));
            return false;
        }
        data.setActivity(next.get());
        return true;
    }

    private static void sendPermissions(ServerPlayer player, Villager villager, WorkerData data) {
        List<Person> trusted = data.trusted().stream().map(entry -> new Person(entry.id(), entry.name())).toList();
        List<Person> online = new ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other.level() == villager.level()) {
                online.add(new Person(other.getUUID(), other.getGameProfile().getName()));
            }
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.PermissionsSnapshotPayload(
                new PermissionsView(ownerName(player, data.owner()), trusted, online)));
    }

    private static String ownerName(ServerPlayer player, UUID owner) {
        ServerPlayer online = player.server.getPlayerList().getPlayer(owner);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return player.server.getProfileCache()
                .get(owner)
                .map(profile -> profile.getName())
                .orElse("unknown");
    }

    /** Chat works at any distance while the worker is loaded in the sender's dimension. */
    private static List<Villager> loadedWorkers(ServerLevel level) {
        List<Villager> workers = new ArrayList<>();
        level.getEntities().get(net.minecraft.world.entity.EntityType.VILLAGER, villager -> {
            if (WorkerAttachments.isWorker(villager)) {
                workers.add(villager);
            }
            return net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
        });
        return workers;
    }

    private static String displayName(Villager villager) {
        return villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString();
    }

    private static String statusLine(Villager villager, WorkerData data) {
        return StatusText.line(
                displayName(villager),
                data.job(),
                data.activity(),
                data.rested(),
                (int) villager.getHealth(),
                (int) villager.getMaxHealth(),
                WorkerTicker.hungry(villager),
                WorkerTicker.hurt(villager),
                data.problem());
    }

    private static void deny(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("You cannot do that."));
    }
}
