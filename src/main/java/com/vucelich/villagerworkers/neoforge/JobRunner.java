package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ActionPace;
import com.vucelich.villagerworkers.common.BreakOutcome;
import com.vucelich.villagerworkers.common.BreakRules;
import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.SaplingKinds;
import com.vucelich.villagerworkers.common.WorkVolumes;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerJob;
import com.vucelich.villagerworkers.common.WorkerProblem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Lumberjack and miner work, plus walking over when someone says come.
 * The builder does not run here. Breaking goes through {@link BreakRules} and the owner's break event.
 */
public final class JobRunner {
    private static final Map<UUID, Runtime> RUNTIMES = new ConcurrentHashMap<>();
    private static final int SCAN_BUDGET = 128;
    private static final int TREE_LIMIT = 128;

    private enum Phase {
        LOGS,
        LEAVES,
        WALL,
        DONE
    }

    private static final class Runtime {
        String signature = "";
        Phase phase = Phase.LOGS;
        int cursor;
        boolean foundThisPass;
        BlockPos breaking;
        float progress;
        long cooldownUntil;
        BlockPos walkTo;
        boolean controlling;
        BlockPos stuckPos;
        long stuckSince = Long.MIN_VALUE;
        boolean stuckRetried;
        List<BlockPos> tree = List.of();
        int treeIndex;
        BlockPos stump;
        String stumpItem = "";
        List<BlockPos> walls = List.of();
        boolean wallsReady;
        long rescanAt;
        boolean leftWork;
        boolean shipGuardNoted;
    }

    private JobRunner() {}

    public static void tick(Villager villager, WorkerData data) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        Runtime runtime = RUNTIMES.computeIfAbsent(villager.getUUID(), id -> new Runtime());
        if (data.activity() == WorkerActivity.COMING) {
            runtime.leftWork = true;
            returnTool(villager, data);
            come(villager, data, runtime);
            return;
        }
        if (data.job() == WorkerJob.BUILDER) {
            if (ModList.get().isLoaded("create")) {
                CreateBuilder.tick(villager, data);
            } else if (data.activity() == WorkerActivity.WORKING) {
                data.setActivity(WorkerActivity.STOPPED);
                OwnerNotices.report(villager, data, ProblemCode.MISSING_CHEST, "Create");
            }
            if (data.activity() != WorkerActivity.WORKING) {
                release(villager, data, runtime);
            }
            return;
        }
        if (data.activity() != WorkerActivity.WORKING) {
            runtime.leftWork = true;
            release(villager, data, runtime);
            return;
        }
        boolean resume = runtime.leftWork;
        runtime.leftWork = false;
        String signature = signature(data);
        if (!signature.equals(runtime.signature)) {
            runtime.signature = signature;
            runtime.stump = null;
            runtime.stumpItem = "";
            restartPass(runtime);
        } else if (resume) {
            restartPass(runtime);
        }
        AssemblyGuards.poll(level);
        if (AssemblyGuards.shipGuardFailed() || AssemblyGuards.plotHandleBroken()) {
            if (!runtime.shipGuardNoted) {
                OwnerNotices.report(villager, data, ProblemCode.SHIP_GUARD, "");
                runtime.shipGuardNoted = true;
            }
        } else if (runtime.shipGuardNoted) {
            runtime.shipGuardNoted = false;
            if (data.problem().code() == ProblemCode.SHIP_GUARD) {
                data.setProblem(WorkerProblem.NONE);
            }
        }
        if (data.job() == WorkerJob.MINER && runtime.phase == Phase.LEAVES) {
            runtime.phase = Phase.LOGS;
        }
        if (level.getGameTime() < runtime.cooldownUntil) {
            if (runtime.walkTo != null) {
                walk(villager, runtime);
            }
            return;
        }
        Deposit delivered = deliverCarry(level, villager, data);
        if (delivered != Deposit.OK) {
            pauseForDeposit(villager, data, runtime, delivered);
            return;
        }
        if (!ensureTool(villager, data, runtime)) {
            return;
        }
        if (data.job() == WorkerJob.LUMBERJACK) {
            pickupSaplings(level, villager, data, runtime);
            if (data.activity() != WorkerActivity.WORKING) {
                return;
            }
        }
        if (runtime.breaking != null) {
            tickBreak(level, villager, data, runtime);
            return;
        }
        if (readyToPlant(runtime)) {
            plantStump(level, villager, data, runtime);
            return;
        }
        if (runtime.phase == Phase.DONE) {
            release(villager, data, runtime);
            long now = level.getGameTime();
            if (runtime.rescanAt == 0L) {
                int scanTicks = emptyScanTicks(data, runtime);
                runtime.rescanAt = now
                        + ActionPace.rescanDelay(scanTicks)
                        + ActionPace.rescanJitter(villager.getUUID());
            } else if (now >= runtime.rescanAt) {
                restartPass(runtime);
            }
            return;
        }
        BlockPos next = nextTarget(level, villager, data, runtime);
        if (next == null) {
            return;
        }
        if (inReach(villager, next)) {
            runtime.walkTo = null;
            runtime.breaking = next.immutable();
            runtime.progress = 0;
            runtime.controlling = true;
            tickBreak(level, villager, data, runtime);
        } else {
            runtime.walkTo = next.immutable();
            runtime.controlling = true;
            walk(villager, runtime);
            stuck(level, villager, data, runtime);
        }
    }

    /** Held menu time does not spend a cooldown, a rescan wait, or the stuck timer. */
    static void freezeWhileHeld(Villager villager) {
        Runtime runtime = RUNTIMES.get(villager.getUUID());
        if (runtime != null) {
            if (runtime.cooldownUntil != 0L) {
                runtime.cooldownUntil++;
            }
            if (runtime.rescanAt != 0L) {
                runtime.rescanAt++;
            }
            if (runtime.stuckSince != Long.MIN_VALUE) {
                runtime.stuckSince++;
            }
        }
        if (ModList.get().isLoaded("create")) {
            CreateBuilder.freezeWhileHeld(villager.getUUID());
        }
    }

    public static void onDeath(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !WorkerAttachments.isWorker(villager)) {
            return;
        }
        forgetRuntime(villager);
        dropCarried(villager, event.getDrops());
    }

    /** Lightning witches and zombie villagers replace the worker. The carry is dropped here when the conversion event still sees it. */
    public static void onConvert(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !WorkerAttachments.isWorker(villager)) {
            return;
        }
        forgetRuntime(villager);
        dropCarried(villager, null);
    }

    /**
     * A zombie conversion discards the villager before the conversion event, and other mods can discard one too.
     * Death uses {@link net.minecraft.world.entity.Entity.RemovalReason#KILLED} and unloads do not use this reason.
     * Items are spawned only while the server is running and the chunk is loaded, so a shutdown or an unloaded chunk
     * does not create an entity the level will not keep. Slots are cleared, so a later conversion drop does not repeat them.
     */
    public static void onDiscarded(Villager villager) {
        if (villager.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
            return;
        }
        forgetRuntime(villager);
        if (!(villager.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isRunning()) {
            return;
        }
        if (!level.isLoaded(villager.blockPosition())) {
            return;
        }
        dropCarried(villager, null);
    }

    private static void forgetRuntime(Villager villager) {
        RUNTIMES.remove(villager.getUUID());
        WorkerHold.forget(villager.getUUID());
        if (ModList.get().isLoaded("create")) {
            CreateBuilder.forget(villager.getUUID());
        }
    }

    /** Adds carried stacks, the tool slot, and the held item to {@code into}, or spawns them when {@code into} is null. */
    private static void dropCarried(Villager villager, Collection<ItemEntity> into) {
        WorkerData carried = WorkerAttachments.get(villager).orElse(null);
        if (carried != null) {
            for (int slot = 0; slot < WorkerData.CARRY_SIZE; slot++) {
                ItemStack carriedStack = carried.carry(slot);
                if (carriedStack.isEmpty()) {
                    continue;
                }
                giveDrop(villager, into, carriedStack.copy());
                carried.setCarry(slot, ItemStack.EMPTY);
            }
        }
        ItemStack hand = villager.getMainHandItem();
        if (hand.isEmpty()) {
            return;
        }
        giveDrop(villager, into, hand.copy());
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private static void giveDrop(Villager villager, Collection<ItemEntity> into, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity entity = new ItemEntity(villager.level(), villager.getX(), villager.getY(), villager.getZ(), stack);
        if (into != null) {
            into.add(entity);
            return;
        }
        if (villager.level() instanceof ServerLevel level) {
            level.addFreshEntity(entity);
        }
    }

    private static void come(Villager villager, WorkerData data, Runtime runtime) {
        if (villager.getServer() == null || data.comeTarget() == null) {
            release(villager, data, runtime);
            return;
        }
        ServerPlayer player = villager.getServer().getPlayerList().getPlayer(data.comeTarget());
        if (player == null || player.level() != villager.level()) {
            release(villager, data, runtime);
            return;
        }
        if (villager.distanceToSqr(player) <= ActionPace.COME_DISTANCE_SQR) {
            villager.getNavigation().stop();
            runtime.walkTo = null;
            runtime.controlling = false;
            runtime.stuckSince = Long.MIN_VALUE;
            return;
        }
        runtime.walkTo = player.blockPosition();
        runtime.controlling = true;
        walk(villager, runtime);
        stuck(villager.level() instanceof ServerLevel level ? level : null, villager, data, runtime);
    }

    private static BlockPos nextTarget(ServerLevel level, Villager villager, WorkerData data, Runtime runtime) {
        if (data.job() == WorkerJob.LUMBERJACK && runtime.phase == Phase.LOGS && runtime.treeIndex < runtime.tree.size()) {
            while (runtime.treeIndex < runtime.tree.size()) {
                BlockPos pos = runtime.tree.get(runtime.treeIndex);
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.LOGS) && legal(level, villager, data, pos, state, true)) {
                    return pos;
                }
                runtime.treeIndex++;
            }
            runtime.tree = List.of();
            runtime.treeIndex = 0;
            if (data.lumberjack().replantSaplings() && runtime.stump != null) {
                return null;
            }
        }
        if (runtime.phase == Phase.WALL) {
            return scanWalls(level, villager, data, runtime);
        }
        return scanDig(level, villager, data, runtime);
    }

    private static BlockPos scanDig(ServerLevel level, Villager villager, WorkerData data, Runtime runtime) {
        int count = WorkVolumes.digCount(data);
        if (count <= 0) {
            advance(data, runtime);
            return null;
        }
        for (int n = 0; n < SCAN_BUDGET; n++) {
            int index = runtime.cursor;
            runtime.cursor++;
            boolean wrapped = false;
            if (runtime.cursor >= count) {
                runtime.cursor = 0;
                wrapped = true;
            }
            BlockPos pos = WorkVolumes.digAt(data, index);
            if (pos != null && level.isLoaded(pos) && interesting(level, data, runtime, pos)) {
                runtime.foundThisPass = true;
                if (data.job() == WorkerJob.LUMBERJACK && runtime.phase == Phase.LOGS) {
                    collectTree(level, data, runtime, pos);
                    if (runtime.tree.isEmpty()) {
                        continue;
                    }
                    return runtime.tree.get(0);
                }
                return pos;
            }
            if (wrapped) {
                if (!runtime.foundThisPass) {
                    advance(data, runtime);
                    return null;
                }
                runtime.foundThisPass = false;
            }
        }
        return null;
    }

    private static BlockPos scanWalls(ServerLevel level, Villager villager, WorkerData data, Runtime runtime) {
        if (!runtime.wallsReady) {
            runtime.walls = WorkVolumes.walls(data);
            runtime.wallsReady = true;
        }
        int count = runtime.walls.size();
        if (count <= 0) {
            runtime.phase = Phase.DONE;
            return null;
        }
        for (int n = 0; n < SCAN_BUDGET; n++) {
            int index = runtime.cursor;
            runtime.cursor++;
            boolean wrapped = false;
            if (runtime.cursor >= count) {
                runtime.cursor = 0;
                wrapped = true;
            }
            BlockPos pos = runtime.walls.get(index);
            if (pos != null && level.isLoaded(pos) && needsWall(level, data, pos)) {
                runtime.foundThisPass = true;
                return pos;
            }
            if (wrapped) {
                if (!runtime.foundThisPass) {
                    runtime.phase = Phase.DONE;
                    return null;
                }
                runtime.foundThisPass = false;
            }
        }
        return null;
    }

    private static void restartPass(Runtime runtime) {
        BlockPos stump = runtime.stump;
        String stumpItem = runtime.stumpItem;
        runtime.phase = Phase.LOGS;
        runtime.cursor = 0;
        runtime.foundThisPass = false;
        runtime.breaking = null;
        runtime.progress = 0;
        runtime.tree = List.of();
        runtime.treeIndex = 0;
        runtime.walls = List.of();
        runtime.wallsReady = false;
        runtime.rescanAt = 0L;
        runtime.stump = stump;
        runtime.stumpItem = stumpItem == null ? "" : stumpItem;
    }

    /** Ticks an empty pass spends inside the scan budget, including a second leaves walk and any walls. */
    private static int emptyScanTicks(WorkerData data, Runtime runtime) {
        int cells = WorkVolumes.digCount(data);
        if (data.job() == WorkerJob.LUMBERJACK && data.lumberjack().clearLeaves()) {
            cells *= 2;
        }
        if (runtime.wallsReady) {
            cells += runtime.walls.size();
        }
        if (cells <= 0) {
            return 0;
        }
        return (cells + SCAN_BUDGET - 1) / SCAN_BUDGET;
    }

    private static void advance(WorkerData data, Runtime runtime) {
        runtime.cursor = 0;
        runtime.foundThisPass = false;
        runtime.breaking = null;
        if (data.job() == WorkerJob.LUMBERJACK && runtime.phase == Phase.LOGS) {
            runtime.phase = data.lumberjack().clearLeaves() ? Phase.LEAVES : Phase.DONE;
            return;
        }
        if (data.job() == WorkerJob.MINER && runtime.phase == Phase.LOGS) {
            runtime.phase = data.miner().wallBlock() == null || data.miner().wallBlock().isBlank() ? Phase.DONE : Phase.WALL;
            return;
        }
        runtime.phase = Phase.DONE;
    }

    private static boolean interesting(ServerLevel level, WorkerData data, Runtime runtime, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (data.job() == WorkerJob.LUMBERJACK) {
            if (runtime.phase == Phase.LOGS && !state.is(BlockTags.LOGS)) {
                return false;
            }
            if (runtime.phase == Phase.LEAVES && !state.is(BlockTags.LEAVES)) {
                return false;
            }
        }
        return legal(level, null, data, pos, state, true);
    }

    private static boolean needsWall(ServerLevel level, WorkerData data, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation wall = wallId(data);
        if (wall == null) {
            return false;
        }
        ResourceLocation current = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (wall.equals(current)) {
            return false;
        }
        if (state.isAir() || state.canBeReplaced()) {
            return !reserved(level, data, pos) && !AssemblyGuards.occupied(level, pos);
        }
        return legal(level, null, data, pos, state, true);
    }

    private static void tickBreak(ServerLevel level, Villager villager, WorkerData data, Runtime runtime) {
        BlockPos pos = runtime.breaking;
        if (pos == null || !level.isLoaded(pos) || !inReach(villager, pos)) {
            runtime.breaking = null;
            runtime.progress = 0;
            return;
        }
        BlockState state = level.getBlockState(pos);
        boolean wall = runtime.phase == Phase.WALL;
        if (wall) {
            if (!needsWall(level, data, pos)) {
                runtime.breaking = null;
                return;
            }
            if (state.isAir() || state.canBeReplaced()) {
                placeWall(level, villager, data, runtime, pos);
                return;
            }
        } else if (!interesting(level, data, runtime, pos)) {
            runtime.breaking = null;
            runtime.progress = 0;
            level.destroyBlockProgress(villager.getId(), pos, -1);
            return;
        }
        if (!legal(level, villager, data, pos, state, false)) {
            runtime.breaking = null;
            runtime.progress = 0;
            return;
        }
        ItemStack tool = villager.getItemInHand(InteractionHand.MAIN_HAND);
        if (!usable(tool)) {
            pause(villager, data, runtime, ProblemCode.BROKEN_TOOL, "");
            return;
        }
        if (!destinationReady(level, villager, data, runtime, state)) {
            return;
        }
        float hardness = state.getDestroySpeed(level, pos);
        float step = hardness == 0.0F
                ? 1.0F
                : toolSpeed(state, tool) / hardness / (tool.isCorrectToolForDrops(state) ? 30.0F : 100.0F) * ActionPace.breakMultiplier(data.rested());
        runtime.progress += step;
        level.destroyBlockProgress(villager.getId(), pos, Math.min(9, (int) (runtime.progress * 10)));
        if (runtime.progress < 1.0F) {
            return;
        }
        finishBreak(level, villager, data, runtime, pos, state, tool);
    }

    private static void finishBreak(
            ServerLevel level, Villager villager, WorkerData data, Runtime runtime, BlockPos pos, BlockState state, ItemStack tool) {
        BreakGate gate = ownerAllows(level, villager, data, pos, state);
        if (gate == BreakGate.WAIT) {
            return;
        }
        if (gate == BreakGate.SKIP) {
            runtime.breaking = null;
            runtime.progress = 0;
            level.destroyBlockProgress(villager.getId(), pos, -1);
            if (runtime.treeIndex < runtime.tree.size() && pos.equals(runtime.tree.get(runtime.treeIndex))) {
                runtime.treeIndex++;
            }
            return;
        }
        BlockEntity entity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        boolean tileDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        List<ItemStack> drops = tileDrops
                ? new ArrayList<>(Block.getDrops(state, level, pos, entity, villager, tool))
                : new ArrayList<>();
        List<ItemStack> spilled = takeSpilledContents(level, pos, state, entity, tileDrops, drops);
        if (state.is(BlockTags.LOGS)) {
            rememberStump(level, data, runtime, pos, state);
        }
        if (state.is(BlockTags.BEDS)) {
            clearOtherBedHalf(level, villager, data, pos, state);
        }
        ServerPlayer ownerPlayer = owner(villager, data);
        if (ownerPlayer != null && !creativeDestroyDupes(ownerPlayer, state)) {
            state.getBlock().playerWillDestroy(level, pos, state, ownerPlayer);
        }
        boolean originalRemains = level.getBlockState(pos).is(state.getBlock());
        boolean removed = originalRemains && level.destroyBlock(pos, false, villager);
        originalRemains = level.getBlockState(pos).is(state.getBlock());
        level.destroyBlockProgress(villager.getId(), pos, -1);
        if (BreakOutcome.shouldRestore(originalRemains) || !BreakOutcome.succeeded(removed, originalRemains)) {
            restoreSpilled(level, pos, entity, spilled);
            runtime.breaking = null;
            runtime.progress = 0;
            return;
        }
        if (!tileDrops) {
            spillLikeVanilla(level, pos, spilled);
        }
        if (tool.isDamageableItem()) {
            tool.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        }
        Deposit deposit = putDrops(level, villager, data, drops);
        runtime.breaking = null;
        runtime.progress = 0;
        runtime.cooldownUntil = level.getGameTime() + ActionPace.ticksBetweenActions(data.rested());
        runtime.stuckSince = Long.MIN_VALUE;
        if (runtime.treeIndex < runtime.tree.size() && pos.equals(runtime.tree.get(runtime.treeIndex))) {
            runtime.treeIndex++;
        }
        pauseForDeposit(villager, data, runtime, deposit);
    }

    private static void placeWall(ServerLevel level, Villager villager, WorkerData data, Runtime runtime, BlockPos pos) {
        ResourceLocation wall = wallId(data);
        if (wall == null) {
            runtime.breaking = null;
            return;
        }
        if (owner(villager, data) == null) {
            return;
        }
        BlockPos chestPos = data.miner().regularChest().orElse(null);
        if (!chestAvailable(level, villager, data, runtime, chestPos)) {
            return;
        }
        Container chest = openLinked(level, villager, data, chestPos);
        if (!wallItemAllowed(data, wall)) {
            pause(villager, data, runtime, ProblemCode.OUT_OF_MATERIALS, wall.toString());
            return;
        }
        if (!inReach(villager, pos)) {
            runtime.walkTo = pos.immutable();
            runtime.controlling = true;
            walk(villager, runtime);
            return;
        }
        BlockState here = level.getBlockState(pos);
        boolean open = here.isAir() || here.canBeReplaced();
        if (AssemblyGuards.occupied(level, pos) || reserved(level, data, pos) || (!open && !legal(level, villager, data, pos, here, false))) {
            runtime.breaking = null;
            return;
        }
        PlaceResult placed = takeAndPlace(level, villager, data, chest, wall, pos);
        if (placed == PlaceResult.WAIT) {
            if (!inReach(villager, pos)) {
                runtime.walkTo = pos.immutable();
                runtime.controlling = true;
                walk(villager, runtime);
            }
            return;
        }
        if (placed == PlaceResult.MISSING) {
            pause(villager, data, runtime, ProblemCode.OUT_OF_MATERIALS, wall.toString());
            return;
        }
        runtime.breaking = null;
        if (placed == PlaceResult.PLACED) {
            runtime.cooldownUntil = level.getGameTime() + ActionPace.ticksBetweenActions(data.rested());
            clearSolved(data);
        }
    }

    private static void plantStump(ServerLevel level, Villager villager, WorkerData data, Runtime runtime) {
        BlockPos pos = runtime.stump;
        String item = runtime.stumpItem;
        runtime.stump = null;
        runtime.stumpItem = "";
        if (pos == null || item == null || item.isBlank() || !level.isLoaded(pos)) {
            return;
        }
        if (!WorkVolumes.isDig(data, level.dimension().location(), pos)) {
            return;
        }
        BlockState ground = level.getBlockState(pos.below());
        BlockState here = level.getBlockState(pos);
        if (!ground.is(BlockTags.DIRT) || !(here.isAir() || here.canBeReplaced())) {
            return;
        }
        if (AssemblyGuards.occupied(level, pos) || reserved(level, data, pos)) {
            return;
        }
        if (!inReach(villager, pos)) {
            runtime.stump = pos;
            runtime.stumpItem = item;
            runtime.walkTo = pos.immutable();
            runtime.controlling = true;
            walk(villager, runtime);
            return;
        }
        if (owner(villager, data) == null) {
            runtime.stump = pos;
            runtime.stumpItem = item;
            return;
        }
        BlockPos chestPos = data.lumberjack().saplingChest().orElse(null);
        if (!chestAvailable(level, villager, data, runtime, chestPos)) {
            runtime.stump = pos;
            runtime.stumpItem = item;
            return;
        }
        Container chest = openLinked(level, villager, data, chestPos);
        ResourceLocation id = ResourceLocation.parse(item);
        PlaceResult placed = takeAndPlace(level, villager, data, chest, id, pos);
        if (placed == PlaceResult.WAIT) {
            runtime.stump = pos;
            runtime.stumpItem = item;
            if (!inReach(villager, pos)) {
                runtime.walkTo = pos.immutable();
                runtime.controlling = true;
                walk(villager, runtime);
            }
            return;
        }
        if (placed == PlaceResult.DENIED) {
            return;
        }
        if (placed != PlaceResult.PLACED) {
            pause(villager, data, runtime, ProblemCode.OUT_OF_MATERIALS, item);
            runtime.stump = pos;
            runtime.stumpItem = item;
            return;
        }
        runtime.cooldownUntil = level.getGameTime() + ActionPace.ticksBetweenActions(data.rested());
        clearSolved(data);
    }

    private static boolean readyToPlant(Runtime runtime) {
        return runtime.stump != null && (runtime.tree.isEmpty() || runtime.treeIndex >= runtime.tree.size());
    }

    private static void pickupSaplings(ServerLevel level, Villager villager, WorkerData data, Runtime runtime) {
        if (owner(villager, data) == null) {
            return;
        }
        AABB box = villager.getBoundingBox().inflate(1.0);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box, item -> isSapling(item.getItem()))) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            BlockPos chestPos = data.lumberjack().saplingChest().orElse(null);
            if (chestPos == null) {
                continue;
            }
            Deposit deposit = store(level, villager, data, chestPos, stack.copy());
            entity.discard();
            if (deposit != Deposit.OK) {
                pauseForDeposit(villager, data, runtime, deposit);
                return;
            }
        }
    }

    private static boolean wallItemAllowed(WorkerData data, ResourceLocation wall) {
        String id = wall.toString();
        if (BreakRules.GLOBAL_DENY.contains(BreakRules.path(id))) {
            return false;
        }
        boolean whitelisted = data.breakWhitelist().stream().anyMatch(entry -> entry.equals(wall));
        return whitelisted || !BreakRules.PROTECTED_NAMESPACES.contains(wall.getNamespace());
    }

    private enum PlaceResult {
        PLACED,
        MISSING,
        DENIED,
        WAIT
    }

    private static PlaceResult takeAndPlace(
            ServerLevel level, Villager villager, WorkerData data, Container chest, ResourceLocation itemId, BlockPos pos) {
        if (!level.isLoaded(pos) || !inReach(villager, pos)) {
            return PlaceResult.WAIT;
        }
        BlockState occupying = level.getBlockState(pos);
        boolean open = occupying.isAir() || occupying.canBeReplaced();
        if (AssemblyGuards.occupied(level, pos) || reserved(level, data, pos) || (!open && !legal(level, villager, data, pos, occupying, false))) {
            return PlaceResult.DENIED;
        }
        ItemStack taken = ContainerAccess.takeBest(chest, stack -> itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        if (taken.isEmpty()) {
            return PlaceResult.MISSING;
        }
        Block block = BuiltInRegistries.BLOCK.get(itemId);
        if (block == null || block.defaultBlockState().isAir()) {
            ContainerAccess.insert(chest, taken);
            return PlaceResult.MISSING;
        }
        ServerPlayer ownerPlayer = owner(villager, data);
        if (ownerPlayer == null) {
            ContainerAccess.insert(chest, taken);
            return PlaceResult.WAIT;
        }
        BlockState previous = level.getBlockState(pos);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL)) {
            ContainerAccess.insert(chest, taken);
            return PlaceResult.DENIED;
        }
        if (EventHooks.onBlockPlace(ownerPlayer, snapshot, net.minecraft.core.Direction.DOWN)) {
            level.setBlock(pos, previous, Block.UPDATE_ALL);
            ContainerAccess.insert(chest, taken);
            return PlaceResult.DENIED;
        }
        return PlaceResult.PLACED;
    }

    private static void rememberStump(ServerLevel level, WorkerData data, Runtime runtime, BlockPos pos, BlockState state) {
        if (!data.lumberjack().replantSaplings() || runtime.stump != null) {
            return;
        }
        if (!level.getBlockState(pos.below()).is(BlockTags.DIRT)) {
            return;
        }
        String sapling = SaplingKinds.saplingForLog(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (sapling == null) {
            return;
        }
        runtime.stump = pos.immutable();
        runtime.stumpItem = sapling;
    }

    private static void collectTree(ServerLevel level, WorkerData data, Runtime runtime, BlockPos start) {
        List<BlockPos> tree = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        String kind = SaplingKinds.saplingForLog(BuiltInRegistries.BLOCK.getKey(level.getBlockState(start).getBlock()).toString());
        queue.add(start.immutable());
        while (!queue.isEmpty() && tree.size() < TREE_LIMIT) {
            BlockPos pos = queue.removeFirst();
            BlockPos fixed = pos.immutable();
            if (!seen.add(fixed) || !level.isLoaded(fixed)) {
                continue;
            }
            BlockState state = level.getBlockState(fixed);
            if (!state.is(BlockTags.LOGS) || !WorkVolumes.isDig(data, level.dimension().location(), fixed)) {
                continue;
            }
            String other = SaplingKinds.saplingForLog(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            if (kind != null && !kind.equals(other)) {
                continue;
            }
            tree.add(fixed);
            for (var direction : net.minecraft.core.Direction.values()) {
                queue.add(fixed.relative(direction));
            }
        }
        runtime.tree = tree;
        runtime.treeIndex = 0;
        runtime.stump = null;
        runtime.stumpItem = "";
    }

    private static boolean ensureTool(Villager villager, WorkerData data, Runtime runtime) {
        boolean lumber = data.job() == WorkerJob.LUMBERJACK;
        ItemStack hand = villager.getMainHandItem();
        if (jobTool(lumber, hand) && usable(hand)) {
            return true;
        }
        ItemStack slotted = data.carry(WorkerData.TOOL_SLOT);
        if (jobTool(lumber, slotted) && usable(slotted)) {
            data.setCarry(WorkerData.TOOL_SLOT, ItemStack.EMPTY);
            equip(villager, data, slotted.copy());
            return true;
        }
        stowHand(villager, data);
        BlockPos chestPos = lumber ? data.lumberjack().toolChest().orElse(null) : data.miner().toolChest().orElse(null);
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        if (owner(villager, data) == null) {
            return false;
        }
        if (!chestAvailable(level, villager, data, runtime, chestPos)) {
            return false;
        }
        Container chest = openLinked(level, villager, data, chestPos);
        ItemStack tool = ContainerAccess.takeBest(chest, stack -> jobTool(lumber, stack));
        if (tool.isEmpty() || !usable(tool)) {
            if (!tool.isEmpty()) {
                ContainerAccess.insert(chest, tool);
            }
            pause(villager, data, runtime, ProblemCode.BROKEN_TOOL, "");
            return false;
        }
        equip(villager, data, tool);
        return true;
    }

    /** Puts {@code tool} in the hand. Whatever was there is kept in the tool slot, the carry slots, a chest, or the world. */
    private static void equip(Villager villager, WorkerData data, ItemStack tool) {
        ItemStack hand = villager.getMainHandItem();
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!hand.isEmpty()) {
            stow(villager, data, hand.copy());
        }
        villager.setItemSlot(EquipmentSlot.MAINHAND, tool);
    }

    private static void stowHand(Villager villager, WorkerData data) {
        ItemStack hand = villager.getMainHandItem();
        if (hand.isEmpty()) {
            return;
        }
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        stow(villager, data, hand.copy());
    }

    private static void stow(Villager villager, WorkerData data, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (isTool(stack) && !usable(stack)) {
            stack = insertToolChest(villager, data, stack);
            if (stack.isEmpty()) {
                return;
            }
        } else if (isTool(stack) && data.carry(WorkerData.TOOL_SLOT).isEmpty()) {
            data.setCarry(WorkerData.TOOL_SLOT, stack);
            return;
        }
        ItemStack left = data.insertCarry(stack);
        if (left.isEmpty()) {
            return;
        }
        if (villager.level() instanceof ServerLevel level) {
            BlockPos chestPos = data.job() == WorkerJob.MINER
                    ? data.miner().toolChest().orElse(data.lumberjack().toolChest().orElse(null))
                    : data.lumberjack().toolChest().orElse(data.miner().toolChest().orElse(null));
            Container chest = openLinked(level, villager, data, chestPos);
            if (chest != null) {
                left = ContainerAccess.insert(chest, left);
            }
        }
        if (!left.isEmpty()) {
            spawn(villager, left);
        }
    }

    /** Puts a worn tool in the tool chest. Returns whatever did not fit. */
    private static ItemStack insertToolChest(Villager villager, WorkerData data, ItemStack stack) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return stack;
        }
        BlockPos chestPos = data.job() == WorkerJob.MINER
                ? data.miner().toolChest().orElse(data.lumberjack().toolChest().orElse(null))
                : data.lumberjack().toolChest().orElse(data.miner().toolChest().orElse(null));
        Container chest = openLinked(level, villager, data, chestPos);
        if (chest == null) {
            return stack;
        }
        return ContainerAccess.insert(chest, stack);
    }

    private static boolean jobTool(boolean lumber, ItemStack stack) {
        return lumber ? stack.is(ItemTags.AXES) : stack.is(ItemTags.PICKAXES);
    }

    private static boolean destinationReady(ServerLevel level, Villager villager, WorkerData data, Runtime runtime, BlockState state) {
        if (owner(villager, data) == null) {
            return false;
        }
        if (data.job() == WorkerJob.LUMBERJACK) {
            return chestAvailable(level, villager, data, runtime, data.lumberjack().logChest().orElse(null));
        }
        if (!chestAvailable(level, villager, data, runtime, data.miner().regularChest().orElse(null))) {
            return false;
        }
        if (!data.miner().useValuables()) {
            return true;
        }
        return chestAvailable(level, villager, data, runtime, data.miner().valuablesChest().orElse(null));
    }

    private enum Deposit {
        OK,
        MISSING,
        NO_ACCESS,
        FULL
    }

    private static Deposit putDrops(ServerLevel level, Villager villager, WorkerData data, List<ItemStack> drops) {
        Deposit result = Deposit.OK;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemStack left = data.insertCarry(drop.copy());
            if (!left.isEmpty()) {
                spawn(villager, left);
                result = Deposit.FULL;
            }
        }
        Deposit flushed = deliverCarry(level, villager, data);
        return result == Deposit.OK ? flushed : result;
    }

    private static BlockPos chestFor(WorkerData data, ItemStack drop) {
        if (data.job() == WorkerJob.LUMBERJACK) {
            if (isSapling(drop)) {
                return data.lumberjack().saplingChest().orElse(null);
            }
            return data.lumberjack().logChest().orElse(null);
        }
        if (!data.miner().useValuables()) {
            return data.miner().regularChest().orElse(null);
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(drop.getItem());
        boolean valuable = data.miner().valuables().stream().anyMatch(id::equals);
        return valuable ? data.miner().valuablesChest().orElse(null) : data.miner().regularChest().orElse(null);
    }

    /** Moves carry slots into the linked chest for each stack. Leftovers stay in the carry slots. */
    private static Deposit deliverCarry(ServerLevel level, Villager villager, WorkerData data) {
        Deposit result = Deposit.OK;
        for (int slot = 0; slot < WorkerData.CARRY_SLOTS; slot++) {
            ItemStack stack = data.carry(slot);
            if (stack.isEmpty()) {
                continue;
            }
            BlockPos chestPos = chestFor(data, stack);
            if (chestPos == null) {
                result = Deposit.MISSING;
                continue;
            }
            ContainerAccess.Failure failure = ContainerAccess.failure(level, chestPos, owner(villager, data));
            if (failure == ContainerAccess.Failure.NO_ACCESS) {
                result = Deposit.NO_ACCESS;
                continue;
            }
            if (failure != ContainerAccess.Failure.NONE) {
                result = Deposit.MISSING;
                continue;
            }
            Container chest = openLinked(level, villager, data, chestPos);
            if (chest == null) {
                result = Deposit.MISSING;
                continue;
            }
            ItemStack left = ContainerAccess.insert(chest, stack);
            data.setCarry(slot, left);
            if (!left.isEmpty()) {
                result = Deposit.FULL;
            }
        }
        return result;
    }

    private static Deposit store(ServerLevel level, Villager villager, WorkerData data, BlockPos chestPos, ItemStack stack) {
        if (chestPos == null) {
            spawn(villager, stack);
            return Deposit.OK;
        }
        ContainerAccess.Failure failure = ContainerAccess.failure(level, chestPos, owner(villager, data));
        if (failure != ContainerAccess.Failure.NONE) {
            spawn(villager, stack);
            return failure == ContainerAccess.Failure.NO_ACCESS ? Deposit.NO_ACCESS : Deposit.MISSING;
        }
        Container chest = openLinked(level, villager, data, chestPos);
        if (chest == null) {
            spawn(villager, stack);
            return Deposit.MISSING;
        }
        ItemStack left = ContainerAccess.insert(chest, stack);
        if (!left.isEmpty()) {
            spawn(villager, left);
            return Deposit.FULL;
        }
        return Deposit.OK;
    }

    private enum BreakGate {
        ALLOW,
        /** Owner is offline. Keep the same cell and try again. */
        WAIT,
        /** Spawn protection, the world border, or a claim cancelled the break. */
        SKIP
    }

    private static BreakGate ownerAllows(ServerLevel level, Villager villager, WorkerData data, BlockPos pos, BlockState state) {
        ServerPlayer owner = owner(villager, data);
        if (owner == null) {
            return BreakGate.WAIT;
        }
        if (!level.mayInteract(owner, pos)) {
            return BreakGate.SKIP;
        }
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, owner);
        if (state.getBlock() instanceof GameMasterBlock && !owner.canUseGameMasterBlocks()) {
            event.setCanceled(true);
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return event.isCanceled() ? BreakGate.SKIP : BreakGate.ALLOW;
    }

    private static void clearSolved(WorkerData data) {
        ProblemCode code = data.problem().code();
        if (code != ProblemCode.NONE && code != ProblemCode.HUNGRY && code != ProblemCode.HURT) {
            data.setProblem(WorkerProblem.NONE);
        }
    }

    private static boolean legal(ServerLevel level, Villager villager, WorkerData data, BlockPos pos, BlockState state, boolean ignoreReach) {
        boolean reach = ignoreReach || (villager != null && inReach(villager, pos));
        boolean lumber = data.job() != WorkerJob.LUMBERJACK
                || state.is(BlockTags.LOGS)
                || (data.lumberjack().clearLeaves() && state.is(BlockTags.LEAVES));
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean whitelisted = data.breakWhitelist().stream().anyMatch(entry -> entry.toString().equals(id));
        boolean cheap = BreakRules.allow(
                data.activity() == WorkerActivity.WORKING && (data.job() == WorkerJob.LUMBERJACK || data.job() == WorkerJob.MINER),
                WorkVolumes.isDig(data, level.dimension().location(), pos) || WorkVolumes.isWall(data, level.dimension().location(), pos),
                reach,
                true,
                state.getDestroySpeed(level, pos),
                id,
                state.hasBlockEntity(),
                reserved(level, data, pos),
                whitelisted,
                lumber);
        if (!cheap) {
            return false;
        }
        return !AssemblyGuards.occupied(level, pos);
    }

    private static boolean reserved(ServerLevel level, WorkerData data, BlockPos pos) {
        if (homeBed(data, level, pos)) {
            return true;
        }
        for (BlockPos linked : data.linkedPositions()) {
            if (linked.equals(pos) || partner(level, linked).equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos partner(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return pos;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return pos;
    }

    private static boolean homeBed(WorkerData data, ServerLevel level, BlockPos pos) {
        if (data.homeBed() == null || !data.homeBed().dimension().equals(level.dimension())) {
            return false;
        }
        BlockPos bed = data.homeBed().pos();
        if (bed.equals(pos)) {
            return true;
        }
        if (!level.isLoaded(bed)) {
            return false;
        }
        BlockState state = level.getBlockState(bed);
        if (!state.is(BlockTags.BEDS) || !state.hasProperty(BedBlock.PART) || !state.hasProperty(BedBlock.FACING)) {
            return false;
        }
        BedPart part = state.getValue(BedBlock.PART);
        var facing = state.getValue(BedBlock.FACING);
        BlockPos other = part == BedPart.HEAD ? bed.relative(facing.getOpposite()) : bed.relative(facing);
        return other.equals(pos);
    }

    private static boolean inReach(Villager villager, BlockPos pos) {
        return villager.distanceToSqr(Vec3.atCenterOf(pos)) <= ActionPace.REACH_SQR;
    }

    private static void walk(Villager villager, Runtime runtime) {
        if (runtime.walkTo == null) {
            return;
        }
        Vec3 dest = Vec3.atCenterOf(runtime.walkTo);
        Vec3 from = villager.position();
        Vec3 delta = dest.subtract(from);
        double length = delta.length();
        if (length > 24.0) {
            dest = from.add(delta.scale(24.0 / length));
        }
        villager.getNavigation().moveTo(dest.x, dest.y, dest.z, 0.55);
    }

    private static void stuck(ServerLevel level, Villager villager, WorkerData data, Runtime runtime) {
        if (level == null || runtime.walkTo == null) {
            return;
        }
        long now = level.getGameTime();
        BlockPos here = villager.blockPosition();
        if (runtime.stuckPos == null || !runtime.stuckPos.equals(here)) {
            runtime.stuckPos = here;
            runtime.stuckSince = now;
            runtime.stuckRetried = false;
            if (data.problem().code() == ProblemCode.STUCK) {
                data.setProblem(WorkerProblem.NONE);
            }
            return;
        }
        if (runtime.stuckSince == Long.MIN_VALUE) {
            runtime.stuckSince = now;
            return;
        }
        if (now - runtime.stuckSince < ActionPace.STUCK_TICKS) {
            return;
        }
        if (!runtime.stuckRetried) {
            runtime.stuckRetried = true;
            runtime.stuckSince = now;
            villager.getNavigation().stop();
            walk(villager, runtime);
            return;
        }
        pause(villager, data, runtime, ProblemCode.STUCK, "");
    }

    private static void pause(Villager villager, WorkerData data, Runtime runtime, ProblemCode code, String subject) {
        BlockPos breaking = runtime.breaking;
        data.setActivity(WorkerActivity.STOPPED);
        OwnerNotices.report(villager, data, code, subject);
        returnTool(villager, data);
        runtime.breaking = null;
        runtime.walkTo = null;
        runtime.controlling = false;
        if (breaking != null && villager.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(villager.getId(), breaking, -1);
        }
    }

    private static void release(Villager villager, WorkerData data, Runtime runtime) {
        if (!runtime.controlling && runtime.walkTo == null) {
            returnTool(villager, data);
            return;
        }
        runtime.controlling = false;
        runtime.walkTo = null;
        runtime.breaking = null;
        villager.getNavigation().stop();
        returnTool(villager, data);
    }

    private static void returnTool(Villager villager, WorkerData data) {
        ItemStack hand = villager.getMainHandItem();
        if (!isTool(hand) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos chestPos = data.job() == WorkerJob.MINER
                ? data.miner().toolChest().orElse(data.lumberjack().toolChest().orElse(null))
                : data.lumberjack().toolChest().orElse(data.miner().toolChest().orElse(null));
        Container chest = openLinked(level, villager, data, chestPos);
        if (chest == null) {
            return;
        }
        ItemStack left = ContainerAccess.insert(chest, hand.copy());
        villager.setItemSlot(EquipmentSlot.MAINHAND, left);
    }

    private static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1);
    }

    private static boolean isTool(ItemStack stack) {
        return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES);
    }

    private static boolean isSapling(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.MANGROVE_PROPAGULE)
                || stack.is(Items.CRIMSON_FUNGUS)
                || stack.is(Items.WARPED_FUNGUS);
    }

    private static float toolSpeed(BlockState state, ItemStack tool) {
        float speed = tool.getDestroySpeed(state);
        return speed <= 0.0F ? 1.0F : speed;
    }

    private static ResourceLocation wallId(WorkerData data) {
        String raw = data.miner().wallBlock();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    private static void clearOtherBedHalf(ServerLevel level, Villager villager, WorkerData data, BlockPos pos, BlockState state) {
        if (!state.hasProperty(BedBlock.PART) || !state.hasProperty(BedBlock.FACING)) {
            return;
        }
        BedPart part = state.getValue(BedBlock.PART);
        var facing = state.getValue(BedBlock.FACING);
        BlockPos other = part == BedPart.HEAD ? pos.relative(facing.getOpposite()) : pos.relative(facing);
        BlockState otherState = level.getBlockState(other);
        if (!otherState.is(BlockTags.BEDS)) {
            return;
        }
        if (!legal(level, villager, data, other, otherState, false)) {
            return;
        }
        if (ownerAllows(level, villager, data, other, otherState) != BreakGate.ALLOW) {
            return;
        }
        level.setBlock(other, level.getFluidState(other).createLegacyBlock(), Block.UPDATE_ALL);
    }

    private static void spawn(Villager villager, ItemStack stack) {
        if (!stack.isEmpty()) {
            villager.spawnAtLocation(stack);
        }
    }

    private static ServerPlayer owner(Villager villager, WorkerData data) {
        if (villager.getServer() == null) {
            return null;
        }
        return villager.getServer().getPlayerList().getPlayer(data.owner());
    }

    private static String signature(WorkerData data) {
        return data.job().id()
                + "|" + data.miner().mode().id()
                + "|" + data.lumberjack().clearLeaves()
                + "|" + data.lumberjack().replantSaplings()
                + "|" + data.lumberjack().area().map(area -> area.format()).orElse("")
                + "|" + data.miner().area().map(area -> area.format()).orElse("")
                + "|" + data.miner().origin().map(origin -> origin.format()).orElse("")
                + "|" + data.miner().width()
                + "|" + data.miner().height()
                + "|" + data.miner().length()
                + "|" + data.miner().branchSpacing()
                + "|" + data.miner().branchLength()
                + "|" + data.miner().shaftWidth()
                + "|" + data.miner().shaftDepth()
                + "|" + data.miner().shaftUp()
                + "|" + data.miner().wallBlock();
    }

    private static Container openLinked(ServerLevel level, Villager villager, WorkerData data, BlockPos pos) {
        return ContainerAccess.open(level, pos, owner(villager, data));
    }

    private static boolean chestAvailable(
            ServerLevel level, Villager villager, WorkerData data, Runtime runtime, BlockPos pos) {
        if (pos == null) {
            pause(villager, data, runtime, ProblemCode.MISSING_CHEST, "");
            return false;
        }
        ContainerAccess.Failure failure = ContainerAccess.failure(level, pos, owner(villager, data));
        if (failure == ContainerAccess.Failure.NO_ACCESS) {
            pause(villager, data, runtime, ProblemCode.NO_ACCESS, "");
            return false;
        }
        if (failure != ContainerAccess.Failure.NONE) {
            pause(villager, data, runtime, ProblemCode.MISSING_CHEST, "");
            return false;
        }
        return true;
    }

    private static void pauseForDeposit(Villager villager, WorkerData data, Runtime runtime, Deposit deposit) {
        if (deposit == Deposit.OK) {
            clearSolved(data);
            return;
        }
        ProblemCode code = switch (deposit) {
            case NO_ACCESS -> ProblemCode.NO_ACCESS;
            case FULL -> ProblemCode.CHEST_FULL;
            case MISSING, OK -> ProblemCode.MISSING_CHEST;
        };
        pause(villager, data, runtime, code, "");
    }

    /**
     * Containers that drop their inventory from {@code onRemove} are emptied first. With tile drops on, those items
     * join the haul. With tile drops off they are spilled later, the way vanilla {@code Containers.dropContents} does,
     * and only after the break actually removes the block.
     */
    private static List<ItemStack> takeSpilledContents(
            ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity, boolean tileDrops, List<ItemStack> drops) {
        if (entity instanceof LecternBlockEntity lectern && state.getBlock() instanceof LecternBlock) {
            return takeLectern(level, pos, state, lectern, tileDrops, drops);
        }
        if (entity instanceof CampfireBlockEntity campfire && state.getBlock() instanceof CampfireBlock) {
            return takeItemList(campfire.getItems(), tileDrops, drops);
        }
        if (!(entity instanceof Container container) || !spillsContents(state.getBlock())) {
            return List.of();
        }
        List<ItemStack> spilled = new ArrayList<>();
        boolean any = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            spilled.add(stack.copy());
            if (stack.isEmpty()) {
                continue;
            }
            any = true;
            if (tileDrops) {
                drops.add(stack.copy());
            }
            container.setItem(slot, ItemStack.EMPTY);
        }
        if (any) {
            container.setChanged();
        }
        return any ? spilled : List.of();
    }

    private static List<ItemStack> takeLectern(
            ServerLevel level, BlockPos pos, BlockState state, LecternBlockEntity lectern, boolean tileDrops, List<ItemStack> drops) {
        ItemStack book = lectern.getBook();
        if (book.isEmpty()) {
            return List.of();
        }
        List<ItemStack> spilled = List.of(book.copy());
        lectern.clearContent();
        if (state.getValue(LecternBlock.HAS_BOOK)) {
            level.setBlock(pos, state.setValue(LecternBlock.HAS_BOOK, false), Block.UPDATE_CLIENTS);
        }
        if (tileDrops) {
            drops.add(book.copy());
        }
        return spilled;
    }

    private static List<ItemStack> takeItemList(NonNullList<ItemStack> items, boolean tileDrops, List<ItemStack> drops) {
        List<ItemStack> spilled = new ArrayList<>();
        boolean any = false;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            spilled.add(stack.copy());
            if (stack.isEmpty()) {
                continue;
            }
            any = true;
            if (tileDrops) {
                drops.add(stack.copy());
            }
            items.set(slot, ItemStack.EMPTY);
        }
        return any ? spilled : List.of();
    }

    private static void restoreSpilled(ServerLevel level, BlockPos pos, BlockEntity entity, List<ItemStack> spilled) {
        if (spilled.isEmpty()) {
            return;
        }
        if (entity instanceof LecternBlockEntity lectern) {
            ItemStack book = spilled.get(0).copy();
            lectern.setBook(book);
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() instanceof LecternBlock && now.hasProperty(LecternBlock.HAS_BOOK)) {
                level.setBlock(pos, now.setValue(LecternBlock.HAS_BOOK, !book.isEmpty()), Block.UPDATE_CLIENTS);
            }
            return;
        }
        if (entity instanceof CampfireBlockEntity campfire) {
            NonNullList<ItemStack> items = campfire.getItems();
            for (int slot = 0; slot < spilled.size() && slot < items.size(); slot++) {
                items.set(slot, spilled.get(slot).copy());
            }
            return;
        }
        if (entity instanceof Container container) {
            for (int slot = 0; slot < spilled.size() && slot < container.getContainerSize(); slot++) {
                container.setItem(slot, spilled.get(slot).copy());
            }
            container.setChanged();
        }
    }

    private static void spillLikeVanilla(ServerLevel level, BlockPos pos, List<ItemStack> spilled) {
        NonNullList<ItemStack> dumping = NonNullList.create();
        for (ItemStack stack : spilled) {
            if (!stack.isEmpty()) {
                dumping.add(stack.copy());
            }
        }
        if (!dumping.isEmpty()) {
            Containers.dropContents(level, pos, dumping);
        }
    }

    private static boolean spillsContents(Block block) {
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof CrafterBlock
                || block instanceof DispenserBlock
                || block instanceof HopperBlock
                || block instanceof JukeboxBlock
                || block instanceof LecternBlock
                || block instanceof ChiseledBookShelfBlock
                || block instanceof CampfireBlock
                || block instanceof DecoratedPotBlock;
    }

    /** Creative {@code playerWillDestroy} spawns a second shulker or beehive item on top of the loot drop. */
    private static boolean creativeDestroyDupes(ServerPlayer ownerPlayer, BlockState state) {
        if (!ownerPlayer.isCreative()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof ShulkerBoxBlock || block instanceof BeehiveBlock;
    }
}
