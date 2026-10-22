package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.StatusText;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerProblem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WorkerTicker {
    public static final long PROBLEM_COOLDOWN = 1200L;

    private WorkerTicker() {}

    public static void tick(Villager villager, WorkerData data) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        WorkerAttachments.applyLook(villager, data);
        if (!data.sleepTracked()) {
            data.setSleepTracked(true);
            data.setWasSleeping(villager.isSleeping());
            villager.getSleepingPos().ifPresent(data::setPendingBed);
        }
        noteWake(villager, data);
        if (data.activity() == WorkerActivity.WORKING || data.activity() == WorkerActivity.COMING) {
            suppressSleep(villager, data);
            noteWake(villager, data);
        }
        applyDawn(level, data);
        boolean held = WorkerHold.postTick(villager);
        if (held) {
            freezeAlerts(data);
        } else if (villager.tickCount % 20 == 0) {
            clearMissingBed(villager, data);
            alert(villager, data, ProblemCode.HUNGRY, hungry(villager));
            alert(villager, data, ProblemCode.HURT, hurt(villager));
            OwnerNotices.flushPending(villager, data);
        }
        if (!held) {
            JobRunner.tick(villager, data);
        }
    }

    /** A minute of menu time does not make a hungry or hurt line due. */
    private static void freezeAlerts(WorkerData data) {
        if (data.hungryNotifiedAt() != Long.MIN_VALUE) {
            data.setHungryNotifiedAt(data.hungryNotifiedAt() + 1L);
        }
        if (data.hurtNotifiedAt() != Long.MIN_VALUE) {
            data.setHurtNotifiedAt(data.hurtNotifiedAt() + 1L);
        }
    }

    public static boolean hungry(Villager villager) {
        return foodCount(villager) == 0 && villager.getHealth() < villager.getMaxHealth();
    }

    public static boolean hurt(Villager villager) {
        return villager.getHealth() <= villager.getMaxHealth() / 2.0F;
    }

    private static void noteWake(Villager villager, WorkerData data) {
        boolean sleeping = villager.isSleeping();
        if (sleeping) {
            villager.getSleepingPos().ifPresent(data::setPendingBed);
        }
        // A working or coming villager is pulled out of bed. That is a missed night, not a finished sleep.
        boolean mayCountSleep = data.activity() == WorkerActivity.IDLE || data.activity() == WorkerActivity.STOPPED;
        if (mayCountSleep && data.wasSleeping() && !sleeping) {
            BlockPos bed = data.pendingBed();
            if (bed != null && villager.level().isLoaded(bed) && villager.level().getBlockState(bed).is(BlockTags.BEDS)) {
                data.setSleptLastNight(true);
                data.setHomeBed(GlobalPos.of(villager.level().dimension(), bed));
            }
        }
        data.setWasSleeping(sleeping);
    }

    private static void suppressSleep(Villager villager, WorkerData data) {
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }
        var brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.LAST_WOKEN, villager.level().getGameTime());
        if (brain.isActive(Activity.REST)) {
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    private static void applyDawn(ServerLevel level, WorkerData data) {
        long day = level.getDayTime() / 24000L;
        if (data.lastDawnDay() == Long.MIN_VALUE) {
            data.setLastDawnDay(day);
            return;
        }
        if (day > data.lastDawnDay()) {
            data.setRested(data.sleptLastNight());
            data.setSleptLastNight(false);
            data.setLastDawnDay(day);
        }
    }

    private static void clearMissingBed(Villager villager, WorkerData data) {
        GlobalPos home = data.homeBed();
        if (home == null || villager.getServer() == null) {
            return;
        }
        ServerLevel bedLevel = villager.getServer().getLevel(home.dimension());
        if (bedLevel != null && bedLevel.isLoaded(home.pos()) && !bedLevel.getBlockState(home.pos()).is(BlockTags.BEDS)) {
            data.setHomeBed(null);
        }
    }

    private static void alert(Villager villager, WorkerData data, ProblemCode code, boolean condition) {
        if (!condition) {
            setPending(data, code, false);
            return;
        }
        long now = villager.level().getGameTime();
        long last = notifiedAt(data, code);
        boolean due = last == Long.MIN_VALUE || now - last >= PROBLEM_COOLDOWN;
        boolean pending = pending(data, code);
        ServerPlayer owner = owner(villager, data);
        if (owner != null && (due || pending)) {
            String name = villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString();
            owner.sendSystemMessage(Component.literal(StatusText.ownerAlert(name, code)));
            setNotified(data, code, now);
            setPending(data, code, false);
            rememberCondition(data, code);
        } else if (owner == null && due && !pending) {
            setPending(data, code, true);
            rememberCondition(data, code);
        }
    }

    /** Hunger and injury stay on the status line, but they must not erase a work problem. */
    private static void rememberCondition(WorkerData data, ProblemCode code) {
        ProblemCode current = data.problem().code();
        if (current == ProblemCode.NONE || current == ProblemCode.HUNGRY || current == ProblemCode.HURT) {
            data.setProblem(new WorkerProblem(code, ""));
        }
    }

    private static ServerPlayer owner(Villager villager, WorkerData data) {
        if (villager.getServer() == null) {
            return null;
        }
        return villager.getServer().getPlayerList().getPlayer(data.owner());
    }

    private static int foodCount(Villager villager) {
        int count = 0;
        var inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.BREAD) || stack.is(Items.POTATO) || stack.is(Items.CARROT) || stack.is(Items.BEETROOT)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static long notifiedAt(WorkerData data, ProblemCode code) {
        return code == ProblemCode.HURT ? data.hurtNotifiedAt() : data.hungryNotifiedAt();
    }

    private static void setNotified(WorkerData data, ProblemCode code, long time) {
        if (code == ProblemCode.HURT) {
            data.setHurtNotifiedAt(time);
        } else {
            data.setHungryNotifiedAt(time);
        }
    }

    private static boolean pending(WorkerData data, ProblemCode code) {
        return code == ProblemCode.HURT ? data.hurtPending() : data.hungryPending();
    }

    private static void setPending(WorkerData data, ProblemCode code, boolean pending) {
        if (code == ProblemCode.HURT) {
            data.setHurtPending(pending);
        } else {
            data.setHungryPending(pending);
        }
    }
}
