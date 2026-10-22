package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.StatusText;
import com.vucelich.villagerworkers.common.WorkNotice;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerProblem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Owner-only problem lines, at most once a minute per code, with one pending line while offline. */
public final class OwnerNotices {
    private OwnerNotices() {}

    public static void report(Villager villager, WorkerData data, ProblemCode code, String subject) {
        if (villager == null || data == null || code == null || code == ProblemCode.NONE) {
            return;
        }
        String text = subject == null ? "" : subject;
        long now = villager.level().getGameTime();
        WorkNotice existing = data.notice(code);
        long last = existing == null ? Long.MIN_VALUE : existing.notifiedAt();
        boolean pending = existing != null && existing.pending();
        boolean due = last == Long.MIN_VALUE || now - last >= WorkerTicker.PROBLEM_COOLDOWN;
        if (code != ProblemCode.HUNGRY && code != ProblemCode.HURT) {
            data.setProblem(new WorkerProblem(code, text));
        }
        java.util.List<ServerPlayer> owners = OnlineOwners.all(villager, data);
        if (!owners.isEmpty() && (due || pending)) {
            String name = villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString();
            Component line = Component.literal(StatusText.ownerAlert(name, code, text));
            for (ServerPlayer owner : owners) {
                owner.sendSystemMessage(line);
            }
            data.putNotice(new WorkNotice(code.id(), now, false, text));
        } else if (owners.isEmpty() && due && !pending) {
            data.putNotice(new WorkNotice(code.id(), last, true, text));
        }
    }

    public static void flushPending(Villager villager, WorkerData data) {
        if (OnlineOwners.all(villager, data).isEmpty()) {
            return;
        }
        for (WorkNotice notice : data.notices()) {
            if (!notice.pending()) {
                continue;
            }
            ProblemCode code = ProblemCode.byId(notice.code());
            if (code == ProblemCode.NONE || code == ProblemCode.HUNGRY || code == ProblemCode.HURT) {
                continue;
            }
            report(villager, data, code, notice.subject());
        }
    }

}
