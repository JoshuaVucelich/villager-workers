package com.vucelich.villagerworkers.neoforge;

import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.vucelich.villagerworkers.common.ActionPace;
import com.vucelich.villagerworkers.common.PlacementRules;
import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/** Places a linked Create schematic from the material chests. It does not break blocks. */
public final class CreateBuilder {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final int SCAN_BUDGET = 128;
    private static final int RECHECK_TICKS = 100;

    private static final class Session {
        String key = "";
        SchematicPrinter printer;
        boolean hold;
        long cooldownUntil;
        long recheckAt = Long.MIN_VALUE;
        BlockPos walkTo;
        BlockPos stuckPos;
        long stuckSince = Long.MIN_VALUE;
        boolean stuckRetried;
    }

    private CreateBuilder() {}

    public static void forget(UUID villagerId) {
        SESSIONS.remove(villagerId);
    }

    /** Held menu time does not spend the place cooldown, the materials recheck, or the stuck timer. */
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
        BlockPos cannon = data.builder().schematicAnchor().orElse(null);
        ItemStack blueprint = cannon == null ? null : CreateSchematics.blueprint(level, cannon);
        if (blueprint == null) {
            pause(villager, data, ProblemCode.MISSING_CHEST, "a schematic");
            return;
        }
        String key = CreateSchematics.key(blueprint);
        if (session.printer == null || !key.equals(session.key)) {
            session.printer = CreateSchematics.load(level, blueprint);
            session.key = key;
            session.hold = false;
            if (session.printer == null || !session.printer.isLoaded() || session.printer.isErrored() || session.printer.isWorldEmpty()) {
                pause(villager, data, ProblemCode.MISSING_CHEST, "a schematic");
                return;
            }
        }
        for (int step = 0; step < SCAN_BUDGET; step++) {
            if (!session.hold) {
                if (!session.printer.advanceCurrentPos()) {
                    session.walkTo = null;
                    villager.getNavigation().stop();
                    data.setActivity(WorkerActivity.STOPPED);
                    clearSolved(data);
                    return;
                }
            }
            if (session.printer.getPrintStage() == SchematicPrinter.PrintStage.ENTITIES) {
                session.hold = false;
                continue;
            }
            BlockPos target = session.printer.getCurrentTarget();
            if (target == null || !level.isLoaded(target) || !level.getWorldBorder().isWithinBounds(target)) {
                session.hold = false;
                continue;
            }
            BlockState wanted = wanted(session.printer);
            if (wanted == null) {
                session.hold = false;
                continue;
            }
            if (!inReach(villager, target)) {
                session.hold = true;
                session.walkTo = target.immutable();
                walk(villager, session.walkTo);
                stuck(level, villager, data, session);
                return;
            }
            session.walkTo = null;
            session.stuckSince = Long.MIN_VALUE;
            Step stepResult = act(level, villager, data, session, target, wanted);
            if (stepResult == Step.HOLD) {
                session.hold = true;
                return;
            }
            session.hold = false;
            if (stepResult == Step.PLACED) {
                session.cooldownUntil = level.getGameTime() + ActionPace.ticksBetweenActions(data.rested());
                clearSolved(data);
                return;
            }
        }
    }

    private enum Step {
        CONTINUE,
        HOLD,
        PLACED
    }

    private static Step act(
            ServerLevel level, Villager villager, WorkerData data, Session session, BlockPos target, BlockState wanted) {
        BlockState world = level.getBlockState(target);
        boolean matches = world.equals(wanted);
        ItemRequirement requirement = session.printer.getCurrentRequirement();
        boolean needsItem = requirement != null && !requirement.isEmpty() && !requirement.isInvalid();
        ServerPlayer ownerPlayer = owner(villager, data);
        if (needsItem && ownerPlayer == null) {
            return Step.HOLD;
        }
        List<Container> chests = chests(level, data, ownerPlayer);
        boolean hasMaterials = !needsItem || (chests != null && hasAll(chests, requirement));
        PlacementRules.Choice choice = PlacementRules.choose(matches, wanted.isAir(), world.canBeReplaced(), hasMaterials);
        if (requirement != null && requirement.isInvalid() && !matches) {
            choice = PlacementRules.Choice.BLOCKED;
        }
        if (choice == PlacementRules.Choice.PLACE && AssemblyGuards.occupied(level, target)) {
            choice = PlacementRules.Choice.BLOCKED;
        }
        return switch (choice) {
            case SKIP -> Step.CONTINUE;
            case BLOCKED -> {
                OwnerNotices.report(villager, data, ProblemCode.BLOCKED, target.getX() + " " + target.getY() + " " + target.getZ());
                yield Step.CONTINUE;
            }
            case MISSING -> {
                ProblemCode access = chestProblem(level, data, ownerPlayer);
                if (access == ProblemCode.NO_ACCESS) {
                    pause(villager, data, ProblemCode.NO_ACCESS, "");
                } else if (chests == null) {
                    pause(villager, data, ProblemCode.MISSING_CHEST, "a material chest");
                } else {
                    String item = firstMissing(requirement);
                    pause(villager, data, ProblemCode.OUT_OF_MATERIALS, item);
                    session.recheckAt = level.getGameTime() + RECHECK_TICKS;
                }
                yield Step.HOLD;
            }
            case PLACE -> {
                if (!inReach(villager, target)) {
                    session.walkTo = target.immutable();
                    walk(villager, session.walkTo);
                    yield Step.HOLD;
                }
                Step placed = place(level, villager, data, chests, requirement, target, wanted);
                if (placed == Step.HOLD && !inReach(villager, target)) {
                    session.walkTo = target.immutable();
                    walk(villager, session.walkTo);
                }
                yield placed;
            }
        };
    }

    private static Step place(
            ServerLevel level,
            Villager villager,
            WorkerData data,
            List<Container> chests,
            ItemRequirement requirement,
            BlockPos target,
            BlockState wanted) {
        ServerPlayer owner = owner(villager, data);
        if (owner == null) {
            return Step.HOLD;
        }
        List<ItemStack> taken = new ArrayList<>();
        if (requirement != null && !requirement.isEmpty()) {
            for (ItemRequirement.StackRequirement required : requirement.getRequiredItems()) {
                ItemStack got = take(chests, required, required.stack.getCount());
                if (got.getCount() < required.stack.getCount()) {
                    refund(villager, chests, taken);
                    if (!got.isEmpty()) {
                        refund(villager, chests, List.of(got));
                    }
                    pause(villager, data, ProblemCode.OUT_OF_MATERIALS, BuiltInRegistries.ITEM.getKey(required.stack.getItem()).toString());
                    return Step.HOLD;
                }
                taken.add(got);
            }
        }
        if (!inReach(villager, target)) {
            refund(villager, chests, taken);
            return Step.HOLD;
        }
        if (AssemblyGuards.occupied(level, target)) {
            refund(villager, chests, taken);
            OwnerNotices.report(villager, data, ProblemCode.BLOCKED, target.getX() + " " + target.getY() + " " + target.getZ());
            return Step.CONTINUE;
        }
        BlockState previous = level.getBlockState(target);
        var snapshot = BlockSnapshot.create(level.dimension(), level, target);
        if (!level.setBlock(target, wanted, Block.UPDATE_ALL)
                || EventHooks.onBlockPlace(owner, snapshot, net.minecraft.core.Direction.DOWN)) {
            level.setBlock(target, previous, Block.UPDATE_ALL);
            refund(villager, chests, taken);
            return Step.CONTINUE;
        }
        return Step.PLACED;
    }

    private static void recheck(ServerLevel level, Villager villager, WorkerData data, Session session) {
        long now = level.getGameTime();
        if (session.recheckAt != Long.MIN_VALUE && now < session.recheckAt) {
            return;
        }
        session.recheckAt = now + RECHECK_TICKS;
        if (session.printer == null || session.printer.getCurrentTarget() == null) {
            return;
        }
        ItemRequirement requirement = session.printer.getCurrentRequirement();
        ServerPlayer ownerPlayer = owner(villager, data);
        if (ownerPlayer == null) {
            return;
        }
        List<Container> chests = chests(level, data, ownerPlayer);
        if (chests != null && hasAll(chests, requirement)) {
            data.setActivity(WorkerActivity.WORKING);
            clearSolved(data);
        }
    }

    private static BlockState wanted(SchematicPrinter printer) {
        BlockState[] found = new BlockState[1];
        printer.handleCurrentTarget((pos, state, blockEntity) -> found[0] = state, (pos, entity) -> {});
        return found[0];
    }

    private static List<Container> chests(ServerLevel level, WorkerData data, ServerPlayer owner) {
        if (data.builder().materialChests().isEmpty() || owner == null) {
            return null;
        }
        List<Container> open = new ArrayList<>();
        for (BlockPos pos : data.builder().materialChests()) {
            Container chest = ContainerAccess.open(level, pos, owner);
            if (chest != null) {
                open.add(chest);
            }
        }
        return open.isEmpty() ? null : open;
    }

    /** None of the material chests opened. A lock or claim is {@code NO_ACCESS}; an empty link is {@code MISSING_CHEST}. */
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

    private static boolean hasAll(List<Container> chests, ItemRequirement requirement) {
        if (requirement == null || requirement.isEmpty() || requirement.isInvalid()) {
            return requirement == null || requirement.isEmpty();
        }
        for (ItemRequirement.StackRequirement required : requirement.getRequiredItems()) {
            if (count(chests, required) < required.stack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(ItemRequirement.StackRequirement required, ItemStack stack) {
        return required.matches(stack);
    }

    private static int count(List<Container> chests, ItemRequirement.StackRequirement wanted) {
        int total = 0;
        for (Container chest : chests) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (matches(wanted, stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static ItemStack take(List<Container> chests, ItemRequirement.StackRequirement wanted, int amount) {
        ItemStack taken = wanted.stack.copyWithCount(0);
        int left = amount;
        for (Container chest : chests) {
            for (int slot = 0; slot < chest.getContainerSize() && left > 0; slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!wanted.matches(stack)) {
                    continue;
                }
                int move = Math.min(left, stack.getCount());
                ItemStack split = stack.split(move);
                taken.grow(split.getCount());
                chest.setItem(slot, stack);
                chest.setChanged();
                left -= move;
            }
        }
        return taken;
    }

    private static void refund(Villager villager, List<Container> chests, List<ItemStack> taken) {
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
                villager.spawnAtLocation(left);
            }
        }
    }

    private static String firstMissing(ItemRequirement requirement) {
        if (requirement == null || requirement.getRequiredItems().isEmpty()) {
            return "materials";
        }
        return BuiltInRegistries.ITEM.getKey(requirement.getRequiredItems().get(0).stack.getItem()).toString();
    }

    private static void pause(Villager villager, WorkerData data, ProblemCode code, String subject) {
        data.setActivity(WorkerActivity.STOPPED);
        OwnerNotices.report(villager, data, code, subject);
        villager.getNavigation().stop();
    }

    private static void clearSolved(WorkerData data) {
        ProblemCode code = data.problem().code();
        if (code != ProblemCode.NONE && code != ProblemCode.HUNGRY && code != ProblemCode.HURT) {
            data.setProblem(com.vucelich.villagerworkers.common.WorkerProblem.NONE);
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
        pause(villager, data, ProblemCode.STUCK, "");
    }

    private static ServerPlayer owner(Villager villager, WorkerData data) {
        if (villager.getServer() == null) {
            return null;
        }
        return villager.getServer().getPlayerList().getPlayer(data.owner());
    }
}
