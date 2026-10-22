package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ActionPace;
import com.vucelich.villagerworkers.common.BlockEntitySafety;
import com.vucelich.villagerworkers.common.BuilderConfig;
import com.vucelich.villagerworkers.common.PlacementRules;
import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.StructurePlan;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Places a gzipped vanilla structure from linked chests and the worker inventory.
 * One placement per tick by default. Out of reach cells get a temporary block, removed when the job stops.
 * Aeronautics ships saved unassembled are ordinary blocks in the structure, so they place the same way.
 */
public final class StructureBuilder {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final int RECHECK_TICKS = 100;
    private static boolean timing;
    private static int warmupLeft;
    private static long nanos;
    private static int samples;

    private static final class Session {
        String key = "";
        List<StructureBlockInfo> cells = List.of();
        int cursor;
        long cooldownUntil;
        long recheckAt = Long.MIN_VALUE;
        boolean told;
        BlockPos walkTo;
        BlockPos stuckPos;
        long stuckSince = Long.MIN_VALUE;
        boolean stuckRetried;
    }

    private enum Step {
        CONTINUE,
        HOLD,
        PLACED
    }

    private StructureBuilder() {}

    public static void forget(UUID villagerId) {
        SESSIONS.remove(villagerId);
        Scaffolding.forget(villagerId);
    }

    public static void clear() {
        SESSIONS.clear();
        StructureLibrary.clear();
        StructureMaterials.clear();
        Scaffolding.clear();
        timing = false;
    }

    /** Times working structure ticks. The first {@code warmup} samples are discarded. */
    public static void startTiming(int warmup) {
        timing = true;
        warmupLeft = Math.max(0, warmup);
        nanos = 0L;
        samples = 0;
    }

    public static void stopTiming() {
        timing = false;
    }

    public static int timingSamples() {
        return samples;
    }

    /** Average milliseconds for ten working ticks, from the samples taken so far. */
    public static double averageMillisForTen() {
        if (samples <= 0) {
            return 0.0;
        }
        return (nanos / (double) samples) * 10.0 / 1_000_000.0;
    }

    static void freezeWhileHeld(UUID villagerId) {
        Session session = SESSIONS.get(villagerId);
        if (session == null) {
            return;
        }
        if (session.cooldownUntil != 0L) {
            session.cooldownUntil++;
        }
        if (session.recheckAt != Long.MIN_VALUE) {
            session.recheckAt++;
        }
        if (session.stuckSince != Long.MIN_VALUE) {
            session.stuckSince++;
        }
    }

    public static void tick(Villager villager, WorkerData data) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        boolean sample = timing && data.activity() == WorkerActivity.WORKING;
        long started = sample ? System.nanoTime() : 0L;
        try {
            tickLevel(level, villager, data);
        } finally {
            if (sample) {
                if (warmupLeft > 0) {
                    warmupLeft--;
                } else {
                    nanos += System.nanoTime() - started;
                    samples++;
                }
            }
        }
    }

    private static void tickLevel(ServerLevel level, Villager villager, WorkerData data) {
        Session session = SESSIONS.computeIfAbsent(villager.getUUID(), id -> new Session());
        if (data.activity() == WorkerActivity.STOPPED && data.problem().code() == ProblemCode.OUT_OF_MATERIALS) {
            recheck(level, villager, data, session);
            return;
        }
        if (data.activity() != WorkerActivity.WORKING) {
            return;
        }
        if (level.getGameTime() < session.cooldownUntil) {
            if (session.walkTo != null) {
                walk(villager, session.walkTo);
            }
            return;
        }
        if (!prepare(level, villager, data, session)) {
            return;
        }
        int budget = WorkerSettings.buildScanBudget();
        int limit = WorkerSettings.buildPlacementsPerTick();
        int placed = 0;
        int scanned = 0;
        while (session.cursor < session.cells.size() && scanned < budget && placed < limit) {
            StructureBlockInfo cell = session.cells.get(session.cursor);
            scanned++;
            Step step = act(level, villager, data, session, cell);
            if (step == Step.CONTINUE) {
                session.cursor++;
                continue;
            }
            if (step == Step.PLACED) {
                session.cursor++;
                placed++;
                session.walkTo = null;
                session.stuckSince = Long.MIN_VALUE;
                clearSolved(data);
                continue;
            }
            return;
        }
        if (session.cursor >= session.cells.size()) {
            session.walkTo = null;
            villager.getNavigation().stop();
            Scaffolding.finish(level, villager, data, chests(level, data, owner(villager, data)));
            data.setActivity(WorkerActivity.STOPPED);
            clearSolved(data);
            return;
        }
        if (placed > 0) {
            session.cooldownUntil = level.getGameTime() + interval(data);
        }
    }

    /**
     * Places every cell this worker is assigned, in one action.
     * Survival, adventure, and spectator place nothing.
     */
    public static int buildNow(ServerPlayer player, Villager villager, WorkerData data) {
        if (!(player.level() instanceof ServerLevel level) || villager == null || data == null) {
            return 0;
        }
        if (!creative(player)) {
            player.sendSystemMessage(Component.literal("Build now is only available in creative mode."));
            return 0;
        }
        if (!data.builder().hasStructure()) {
            player.sendSystemMessage(Component.literal("Read a piece book and mark an origin first."));
            return 0;
        }
        StructureLibrary.Loaded loaded = StructureLibrary.load(level, data.builder().pieces(), WorkerSettings.createSchematicUploadKb());
        if (!loaded.ok()) {
            player.sendSystemMessage(Component.literal(loaded.error()));
            return 0;
        }
        BlockPos origin = data.builder().structureOrigin().orElse(null);
        int placed = 0;
        for (StructureBlockInfo cell : loaded.blocks()) {
            if (!StructurePlan.inLayer(cell.pos().getY(), data.builder().layerMin(), data.builder().layerMax(), data.builder().limitsLayers())) {
                continue;
            }
            BlockPos target = origin.offset(cell.pos());
            if (placeCreative(level, player, target, cell)) {
                placed++;
            }
        }
        player.sendSystemMessage(Component.literal("Placed " + placed + " blocks."));
        return placed;
    }

    private static boolean prepare(ServerLevel level, Villager villager, WorkerData data, Session session) {
        BuilderConfig builder = data.builder();
        if (!builder.hasStructure()) {
            pause(villager, data, session, ProblemCode.MISSING_CHEST, "a structure file");
            return false;
        }
        StructureLibrary.Loaded loaded = StructureLibrary.load(level, builder.pieces(), WorkerSettings.createSchematicUploadKb());
        if (!loaded.ok()) {
            pause(villager, data, session, ProblemCode.MISSING_CHEST, loaded.error());
            return false;
        }
        if (!loaded.key().equals(session.key)) {
            List<StructureBlockInfo> cells = new ArrayList<>();
            for (StructureBlockInfo cell : loaded.blocks()) {
                if (StructurePlan.inLayer(cell.pos().getY(), builder.layerMin(), builder.layerMax(), builder.limitsLayers())) {
                    cells.add(cell);
                }
            }
            session.cells = cells;
            session.key = loaded.key();
            session.cursor = 0;
            session.told = false;
        }
        return true;
    }

    private static Step act(ServerLevel level, Villager villager, WorkerData data, Session session, StructureBlockInfo cell) {
        BlockPos origin = data.builder().structureOrigin().orElse(null);
        if (origin == null) {
            pause(villager, data, session, ProblemCode.MISSING_CHEST, "a structure origin");
            return Step.HOLD;
        }
        BlockPos target = origin.offset(cell.pos());
        if (!level.isLoaded(target) || !level.getWorldBorder().isWithinBounds(target)) {
            return Step.CONTINUE;
        }
        BlockState wanted = cell.state();
        BlockState world = level.getBlockState(target);
        StructureMaterials.Need need = StructureMaterials.required(wanted);
        if (need.invalid() && !world.equals(wanted)) {
            OwnerNotices.report(villager, data, ProblemCode.BLOCKED, target.getX() + " " + target.getY() + " " + target.getZ());
            return Step.CONTINUE;
        }
        ServerPlayer owner = owner(villager, data);
        List<Container> chests = chests(level, data, owner);
        boolean hasMaterials = hasAll(chests, data, need.consume());
        boolean replaceable = world.canBeReplaced() || Scaffolding.isPost(target);
        PlacementRules.Choice choice = PlacementRules.choose(world.equals(wanted), wanted.isAir(), replaceable, hasMaterials);
        if (choice == PlacementRules.Choice.PLACE && AssemblyGuards.occupied(level, target)) {
            choice = PlacementRules.Choice.BLOCKED;
        }
        return switch (choice) {
            case SKIP -> {
                if (world.equals(wanted) && Scaffolding.isPost(target)) {
                    Scaffolding.claim(level, target);
                }
                yield Step.CONTINUE;
            }
            case BLOCKED -> {
                OwnerNotices.report(villager, data, ProblemCode.BLOCKED, target.getX() + " " + target.getY() + " " + target.getZ());
                yield Step.CONTINUE;
            }
            case MISSING -> {
                if (owner == null) {
                    yield Step.HOLD;
                }
                ProblemCode access = chestProblem(level, data, owner);
                if (access == ProblemCode.NO_ACCESS && data.carryCount(firstItem(need.consume())) <= 0) {
                    pause(villager, data, session, ProblemCode.NO_ACCESS, "");
                } else if ((chests == null || chests.isEmpty()) && !hasAnyCarry(data, need.consume())) {
                    pause(villager, data, session, ProblemCode.MISSING_CHEST, "a material chest");
                } else {
                    pause(villager, data, session, ProblemCode.OUT_OF_MATERIALS, missingName(need.consume()));
                    session.recheckAt = level.getGameTime() + RECHECK_TICKS;
                }
                yield Step.HOLD;
            }
            case PLACE -> {
                if (owner == null) {
                    yield Step.HOLD;
                }
                if (!inReach(villager, target)) {
                    session.walkTo = target.immutable();
                    Scaffolding.advance(level, villager, data, target, chests);
                    yield Step.HOLD;
                }
                Scaffolding.liftAt(level, target, chests, villager);
                yield place(level, villager, data, session, chests, need.consume(), target, wanted, cell.nbt());
            }
        };
    }

    private static Step place(
            ServerLevel level,
            Villager villager,
            WorkerData data,
            Session session,
            List<Container> chests,
            List<ItemStack> need,
            BlockPos target,
            BlockState wanted,
            CompoundTag blockEntity) {
        ServerPlayer owner = owner(villager, data);
        if (owner == null || !inReach(villager, target)) {
            return Step.HOLD;
        }
        List<ItemStack> taken = new ArrayList<>();
        if (!takeAll(chests, data, need, taken)) {
            refund(villager, chests, data, taken);
            pause(villager, data, session, ProblemCode.OUT_OF_MATERIALS, missingName(need));
            session.recheckAt = level.getGameTime() + RECHECK_TICKS;
            return Step.HOLD;
        }
        if (AssemblyGuards.occupied(level, target)) {
            refund(villager, chests, data, taken);
            OwnerNotices.report(villager, data, ProblemCode.BLOCKED, target.getX() + " " + target.getY() + " " + target.getZ());
            return Step.CONTINUE;
        }
        BlockState previous = level.getBlockState(target);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
        if (!level.setBlock(target, wanted, Block.UPDATE_ALL) || EventHooks.onBlockPlace(owner, snapshot, net.minecraft.core.Direction.DOWN)) {
            level.setBlock(target, previous, Block.UPDATE_ALL);
            refund(villager, chests, data, taken);
            return Step.CONTINUE;
        }
        applyBlockEntity(level, target, blockEntity);
        return Step.PLACED;
    }

    private static boolean placeCreative(ServerLevel level, ServerPlayer player, BlockPos target, StructureBlockInfo cell) {
        if (!level.isLoaded(target) || !level.getWorldBorder().isWithinBounds(target)) {
            return false;
        }
        BlockState wanted = cell.state();
        BlockState world = level.getBlockState(target);
        if (world.equals(wanted) || wanted.isAir() || !world.canBeReplaced()) {
            return false;
        }
        if (AssemblyGuards.occupied(level, target)) {
            return false;
        }
        BlockState previous = world;
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
        if (!level.setBlock(target, wanted, Block.UPDATE_ALL) || EventHooks.onBlockPlace(player, snapshot, net.minecraft.core.Direction.DOWN)) {
            level.setBlock(target, previous, Block.UPDATE_ALL);
            return false;
        }
        applyBlockEntity(level, target, cell.nbt());
        return true;
    }

    static void applyBlockEntity(ServerLevel level, BlockPos target, CompoundTag source) {
        CompoundTag safe = BlockEntitySafety.copyIfSafe(source);
        if (safe == null) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(target);
        if (entity == null) {
            return;
        }
        safe.putInt("x", target.getX());
        safe.putInt("y", target.getY());
        safe.putInt("z", target.getZ());
        try {
            entity.loadWithComponents(safe, level.registryAccess());
            entity.setChanged();
        } catch (RuntimeException ignored) {
            // The block stays. Unsafe or unreadable data is not applied.
        }
    }

    private static void recheck(ServerLevel level, Villager villager, WorkerData data, Session session) {
        long now = level.getGameTime();
        if (session.recheckAt != Long.MIN_VALUE && now < session.recheckAt) {
            return;
        }
        session.recheckAt = now + RECHECK_TICKS;
        if (session.cursor >= session.cells.size()) {
            return;
        }
        StructureBlockInfo cell = session.cells.get(session.cursor);
        StructureMaterials.Need need = StructureMaterials.required(cell.state());
        ServerPlayer owner = owner(villager, data);
        if (owner == null) {
            return;
        }
        if (hasAll(chests(level, data, owner), data, need.consume())) {
            data.setActivity(WorkerActivity.WORKING);
            session.told = false;
            clearSolved(data);
        }
    }

    private static boolean creative(ServerPlayer player) {
        boolean creativeMode = player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE;
        return StructurePlan.instantAllowed(creativeMode, player.getAbilities().instabuild);
    }

    private static int interval(WorkerData data) {
        int configured = WorkerSettings.buildIntervalTicks();
        if (configured >= 0) {
            return configured;
        }
        return ActionPace.ticksBetweenActions(data.rested());
    }

    private static List<Container> chests(ServerLevel level, WorkerData data, ServerPlayer owner) {
        if (owner == null || data.builder().materialChests().isEmpty()) {
            return List.of();
        }
        List<Container> open = new ArrayList<>();
        for (BlockPos pos : data.builder().materialChests()) {
            Container chest = ContainerAccess.open(level, pos, owner);
            if (chest != null) {
                open.add(chest);
            }
        }
        return open;
    }

    private static ProblemCode chestProblem(ServerLevel level, WorkerData data, ServerPlayer owner) {
        boolean denied = false;
        for (BlockPos pos : data.builder().materialChests()) {
            ContainerAccess.Failure failure = ContainerAccess.failure(level, pos, owner);
            if (failure == ContainerAccess.Failure.NONE) {
                return null;
            }
            if (failure == ContainerAccess.Failure.NO_ACCESS) {
                denied = true;
            }
        }
        return denied ? ProblemCode.NO_ACCESS : ProblemCode.MISSING_CHEST;
    }

    private static boolean hasAll(List<Container> chests, WorkerData data, List<ItemStack> need) {
        if (need == null || need.isEmpty()) {
            return true;
        }
        for (ItemStack stack : need) {
            if (count(chests, data, stack.getItem()) < stack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAnyCarry(WorkerData data, List<ItemStack> need) {
        if (need == null) {
            return false;
        }
        for (ItemStack stack : need) {
            if (data.carryCount(stack.getItem()) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int count(List<Container> chests, WorkerData data, Item item) {
        int total = data.carryCount(item);
        if (chests == null) {
            return total;
        }
        for (Container chest : chests) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static boolean takeAll(List<Container> chests, WorkerData data, List<ItemStack> need, List<ItemStack> taken) {
        if (need == null) {
            return true;
        }
        for (ItemStack stack : need) {
            ItemStack got = take(chests, data, stack.getItem(), stack.getCount());
            if (!got.isEmpty()) {
                taken.add(got);
            }
            if (got.getCount() < stack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack take(List<Container> chests, WorkerData data, Item item, int amount) {
        ItemStack taken = new ItemStack(item, 0);
        int left = amount;
        if (chests != null) {
            for (Container chest : chests) {
                for (int slot = 0; slot < chest.getContainerSize() && left > 0; slot++) {
                    ItemStack stack = chest.getItem(slot);
                    if (!stack.is(item)) {
                        continue;
                    }
                    int move = Math.min(left, stack.getCount());
                    taken.grow(move);
                    stack.shrink(move);
                    chest.setItem(slot, stack);
                    chest.setChanged();
                    left -= move;
                }
            }
        }
        for (int slot = 0; slot < WorkerData.CARRY_SLOTS && left > 0; slot++) {
            ItemStack stack = data.carry(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int move = Math.min(left, stack.getCount());
            taken.grow(move);
            stack.shrink(move);
            data.setCarry(slot, stack);
            left -= move;
        }
        return taken;
    }

    private static void refund(Villager villager, List<Container> chests, WorkerData data, List<ItemStack> taken) {
        for (ItemStack stack : taken) {
            ItemStack left = stack.copy();
            if (chests != null) {
                for (Container chest : chests) {
                    left = ContainerAccess.insert(chest, left);
                    if (left.isEmpty()) {
                        break;
                    }
                }
            }
            if (!left.isEmpty()) {
                left = data.insertCarry(left);
            }
            if (!left.isEmpty()) {
                villager.spawnAtLocation(left);
            }
        }
    }

    private static Item firstItem(List<ItemStack> need) {
        if (need == null || need.isEmpty()) {
            return net.minecraft.world.item.Items.AIR;
        }
        return need.get(0).getItem();
    }

    private static String missingName(List<ItemStack> need) {
        if (need == null || need.isEmpty()) {
            return "materials";
        }
        return BuiltInRegistries.ITEM.getKey(need.get(0).getItem()).toString();
    }

    private static void pause(Villager villager, WorkerData data, Session session, ProblemCode code, String subject) {
        data.setActivity(WorkerActivity.STOPPED);
        data.setProblem(new WorkerProblem(code, subject == null ? "" : subject));
        villager.getNavigation().stop();
        if (villager.level() instanceof ServerLevel level) {
            Scaffolding.finish(level, villager, data, chests(level, data, owner(villager, data)));
        }
        if (session.told) {
            return;
        }
        session.told = true;
        String name = villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString();
        String line = com.vucelich.villagerworkers.common.StatusText.ownerAlert(name, code, subject);
        if (villager.getServer() == null) {
            return;
        }
        for (UUID id : data.allOwners()) {
            ServerPlayer player = villager.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.literal(line));
            }
        }
    }

    private static void clearSolved(WorkerData data) {
        ProblemCode code = data.problem().code();
        if (code != ProblemCode.NONE && code != ProblemCode.HUNGRY && code != ProblemCode.HURT) {
            data.setProblem(WorkerProblem.NONE);
        }
    }

    private static boolean inReach(Villager villager, BlockPos pos) {
        return villager.distanceToSqr(Vec3.atCenterOf(pos)) <= ActionPace.REACH_SQR;
    }

    private static void walk(Villager villager, BlockPos pos) {
        Vec3 dest = Vec3.atCenterOf(pos);
        Vec3 from = villager.position();
        Vec3 delta = dest.subtract(from);
        double length = delta.length();
        if (length > 24.0) {
            dest = from.add(delta.scale(24.0 / length));
        }
        villager.getNavigation().moveTo(dest.x, dest.y, dest.z, 0.55);
    }

    private static void stuck(ServerLevel level, Villager villager, WorkerData data, Session session) {
        long now = level.getGameTime();
        BlockPos here = villager.blockPosition();
        if (session.stuckPos == null || !session.stuckPos.equals(here)) {
            session.stuckPos = here;
            session.stuckSince = now;
            session.stuckRetried = false;
            return;
        }
        if (now - session.stuckSince < ActionPace.STUCK_TICKS) {
            return;
        }
        if (!session.stuckRetried) {
            session.stuckRetried = true;
            session.stuckSince = now;
            villager.getNavigation().stop();
            if (session.walkTo != null) {
                walk(villager, session.walkTo);
            }
            return;
        }
        pause(villager, data, session, ProblemCode.STUCK, "");
    }

    private static ServerPlayer owner(Villager villager, WorkerData data) {
        if (villager.getServer() == null) {
            return null;
        }
        return villager.getServer().getPlayerList().getPlayer(data.owner());
    }
}
