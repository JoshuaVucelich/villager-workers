package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.CrewCommands;
import com.vucelich.villagerworkers.common.CrewRules;
import com.vucelich.villagerworkers.common.SetupLimits;
import com.vucelich.villagerworkers.common.TrustedPlayer;
import com.vucelich.villagerworkers.common.WorkerAction;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.neoforge.ModPayloads.CrewView;
import com.vucelich.villagerworkers.neoforge.ModPayloads.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** Crew membership changes happen when a screen is used, not on a ship tick. */
public final class CrewService {
    private CrewService() {}

    public static void bindRules(Villager villager, WorkerData data) {
        data.popRules();
        if (data.overrideForeman() || data.crewForeman() == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(data.crewForeman());
        if (entity instanceof Villager foreman) {
            WorkerAttachments.get(foreman)
                    .filter(WorkerData::foreman)
                    .ifPresent(boss -> data.pushRules(CrewRules.effective(data, boss)));
        }
    }

    public static void send(ServerPlayer player, Villager villager, WorkerData data) {
        if (data.foreman()) {
            adoptLoadedMembers(player.serverLevel(), villager, data);
            WorkerLooks.refresh(villager, data);
        }
        PacketDistributor.sendToPlayer(player, new ModPayloads.CrewSnapshotPayload(capture(player, villager, data)));
    }

    public static void assign(ServerPlayer player, UUID workerId, UUID foremanId) {
        Villager worker = loaded(player, workerId);
        Villager foreman = loaded(player, foremanId);
        WorkerData workerData = editable(player, worker);
        WorkerData foremanData = editable(player, foreman);
        if (workerData == null || foremanData == null) {
            return;
        }
        if (workerData.foreman() || !foremanData.foreman()) {
            player.sendSystemMessage(Component.literal("That worker cannot join that foreman."));
            return;
        }
        if (foreman.getUUID().equals(workerData.crewForeman())) {
            player.sendSystemMessage(Component.literal(name(worker) + " is already in that crew."));
            return;
        }
        if (!foremanData.crewMembers().contains(worker.getUUID())
                && foremanData.crewMembers().size() >= SetupLimits.MAX_CREW) {
            player.sendSystemMessage(Component.literal("That crew is full."));
            return;
        }
        if (workerData.crewForeman() != null) {
            forgetPrevious(player, worker, workerData);
        }
        if (!CrewCommands.assign(workerData, worker.getUUID(), foremanData, foreman.getUUID())) {
            player.sendSystemMessage(Component.literal("That worker cannot join that foreman."));
            return;
        }
        WorkerLooks.refresh(foreman, foremanData);
        player.sendSystemMessage(Component.literal(name(worker) + " joined " + name(foreman) + " (" + foremanData.crewSize() + " crew)."));
        send(player, menuVillager(player, worker, foreman), menuData(player, workerData, foremanData));
    }

    public static void unassign(ServerPlayer player, UUID workerId) {
        Villager worker = loaded(player, workerId);
        WorkerData workerData = editable(player, worker);
        if (workerData == null) {
            return;
        }
        Villager foreman = workerData.crewForeman() == null ? null : loaded(player, workerData.crewForeman());
        WorkerData foremanData = foreman == null ? null : WorkerAttachments.get(foreman).orElse(null);
        CrewCommands.unassign(workerData, worker.getUUID(), foremanData);
        if (foreman != null && foremanData != null) {
            WorkerLooks.refresh(foreman, foremanData);
        }
        player.sendSystemMessage(Component.literal(name(worker) + " is unassigned."));
        send(player, menuVillager(player, worker, foreman), menuData(player, workerData, foremanData));
    }

    public static void giveToPlayer(ServerPlayer player, UUID workerId, String playerName) {
        ServerPlayer target = null;
        if (playerName != null) {
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                if (online.getGameProfile().getName().equalsIgnoreCase(playerName)) {
                    target = online;
                    break;
                }
            }
        }
        move(player, workerId, null, true, target == null ? null : target.getUUID());
    }

    public static void promote(ServerPlayer player, Villager villager, WorkerData data) {
        forgetPrevious(player, villager, data);
        data.setForeman(true);
        WorkerLooks.refresh(villager, data);
    }

    public static void demote(Villager villager, WorkerData data) {
        if (!(villager.level() instanceof ServerLevel level)) {
            data.setForeman(false);
            WorkerLooks.refresh(villager, data);
            return;
        }
        for (UUID id : List.copyOf(data.crewMembers())) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Villager member) {
                WorkerAttachments.get(member).ifPresent(memberData -> CrewCommands.unassign(memberData, id, data));
            } else {
                data.removeCrewMember(id);
            }
        }
        data.setForeman(false);
        WorkerLooks.refresh(villager, data);
    }

    public static void move(ServerPlayer player, UUID workerId, UUID newForemanId, boolean toPlayer, UUID playerId) {
        Villager worker = loaded(player, workerId);
        WorkerData workerData = editable(player, worker);
        if (workerData == null) {
            return;
        }
        if (toPlayer) {
            if (!WorkerServer.allows(workerData, player, WorkerAction.TRANSFER_OWNER)) {
                player.sendSystemMessage(Component.literal("Only an owner can give this worker away."));
                return;
            }
            ServerPlayer target = player.server.getPlayerList().getPlayer(playerId);
            if (target == null || target.level() != worker.level()) {
                player.sendSystemMessage(Component.literal("That player must be online in this dimension."));
                return;
            }
            forgetPrevious(player, worker, workerData);
            workerData.transferSole(new TrustedPlayer(target.getUUID(), target.getGameProfile().getName()));
            player.sendSystemMessage(Component.literal(name(worker) + " now belongs only to " + target.getGameProfile().getName() + "."));
            target.sendSystemMessage(Component.literal(name(worker) + " is now yours alone."));
            return;
        }
        assign(player, workerId, newForemanId);
    }

    public static void order(ServerPlayer player, UUID foremanId, String targetId, List<UUID> ticked, String orderId) {
        Villager foreman = loaded(player, foremanId);
        WorkerData foremanData = editable(player, foreman);
        if (foremanData == null || !foremanData.foreman()) {
            player.sendSystemMessage(Component.literal("That villager is not a foreman."));
            return;
        }
        CrewCommands.Target target = CrewCommands.Target.byId(targetId);
        CrewCommands.Order order = CrewCommands.Order.byId(orderId);
        boolean create = ModList.get().isLoaded("create");
        List<UUID> selected = CrewCommands.select(foreman.getUUID(), foremanData.crewMembers(), target, ticked);
        int changed = 0;
        for (UUID id : selected) {
            Villager member = id.equals(foreman.getUUID()) ? foreman : loaded(player, id);
            WorkerData data = member == null ? null : WorkerAttachments.get(member).orElse(null);
            if (data == null || !WorkerServer.allows(data, player, WorkerAction.SET_ACTIVITY)) {
                continue;
            }
            if (order.name().startsWith("JOB") && !WorkerServer.allows(data, player, WorkerAction.EDIT_SETUP)) {
                continue;
            }
            CrewCommands.apply(data, order, player.getUUID(), create);
            changed++;
        }
        player.sendSystemMessage(Component.literal("Applied " + order.name().toLowerCase(java.util.Locale.ROOT) + " to " + changed + " worker" + (changed == 1 ? "" : "s") + "."));
        send(player, foreman, foremanData);
    }

    public static void setOverride(ServerPlayer player, UUID workerId, boolean override) {
        Villager worker = loaded(player, workerId);
        WorkerData data = editable(player, worker);
        if (data == null) {
            return;
        }
        data.setOverrideForeman(override);
        player.sendSystemMessage(Component.literal(override ? "This worker uses their own rules." : "This worker uses the foreman's rules."));
        send(player, worker, data);
    }

    private static void adoptLoadedMembers(ServerLevel level, Villager foreman, WorkerData data) {
        List<UUID> stale = new ArrayList<>();
        for (UUID id : data.crewMembers()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Villager other && WorkerAttachments.isWorker(other)) {
                WorkerData otherData = WorkerAttachments.get(other).orElse(null);
                if (otherData == null || !foreman.getUUID().equals(otherData.crewForeman())) {
                    stale.add(id);
                }
            }
        }
        for (UUID id : stale) {
            data.removeCrewMember(id);
        }
        for (Villager other : level.getEntitiesOfClass(Villager.class, reach(level), WorkerAttachments::isWorker)) {
            WorkerData otherData = WorkerAttachments.get(other).orElse(null);
            if (otherData == null || otherData.foreman()) {
                continue;
            }
            if (foreman.getUUID().equals(otherData.crewForeman())) {
                data.addCrewMember(other.getUUID());
            }
        }
    }

    private static void forgetPrevious(ServerPlayer player, Villager worker, WorkerData workerData) {
        if (workerData.crewForeman() == null) {
            return;
        }
        Villager previous = loaded(player, workerData.crewForeman());
        if (previous == null) {
            workerData.setCrewForeman(null);
            return;
        }
        WorkerAttachments.get(previous).ifPresent(data -> CrewCommands.unassign(workerData, worker.getUUID(), data));
        WorkerAttachments.get(previous).ifPresent(data -> WorkerLooks.refresh(previous, data));
    }

    private static CrewView capture(ServerPlayer player, Villager villager, WorkerData data) {
        List<Person> foremen = new ArrayList<>();
        List<Person> unassigned = new ArrayList<>();
        for (Villager other : player.serverLevel().getEntitiesOfClass(Villager.class, reach(player.serverLevel()), WorkerAttachments::isWorker)) {
            WorkerData otherData = WorkerAttachments.get(other).orElse(null);
            if (otherData == null || !WorkerServer.allows(otherData, player, WorkerAction.OPEN_SCREEN)) {
                continue;
            }
            Person person = new Person(other.getUUID(), name(other));
            if (otherData.foreman()) {
                foremen.add(person);
            } else if (otherData.crewForeman() == null && !other.getUUID().equals(villager.getUUID())) {
                unassigned.add(person);
            }
        }
        List<Person> crew = new ArrayList<>();
        String assigned = "none";
        if (data.foreman()) {
            for (UUID id : data.crewMembers()) {
                Villager member = loaded(player, id);
                crew.add(new Person(id, member == null ? id.toString().substring(0, 8) : name(member)));
            }
        } else if (data.crewForeman() != null) {
            Villager boss = loaded(player, data.crewForeman());
            assigned = boss == null ? data.crewForeman().toString().substring(0, 8) : name(boss);
        }
        return new CrewView(
                villager.getUUID(),
                data.foreman(),
                data.overrideForeman(),
                assigned,
                cap(foremen, 8),
                cap(unassigned, 8),
                cap(crew, SetupLimits.MAX_CREW));
    }

    private static List<Person> cap(List<Person> people, int max) {
        if (people.size() <= max) {
            return List.copyOf(people);
        }
        return List.copyOf(people.subList(0, max));
    }

    private static AABB reach(ServerLevel level) {
        return new AABB(-3.0E7, level.getMinBuildHeight(), -3.0E7, 3.0E7, level.getMaxBuildHeight(), 3.0E7);
    }

    private static Villager loaded(ServerPlayer player, UUID id) {
        if (id == null) {
            return null;
        }
        Entity entity = player.serverLevel().getEntity(id);
        return entity instanceof Villager villager && WorkerAttachments.isWorker(villager) ? villager : null;
    }

    private static WorkerData editable(ServerPlayer player, Villager villager) {
        if (villager == null) {
            player.sendSystemMessage(Component.literal("That worker is not loaded."));
            return null;
        }
        WorkerData data = WorkerAttachments.get(villager).orElse(null);
        if (data == null || !WorkerServer.allows(data, player, WorkerAction.EDIT_SETUP)) {
            player.sendSystemMessage(Component.literal("You cannot change that worker."));
            return null;
        }
        return data;
    }

    private static Villager menuVillager(ServerPlayer player, Villager worker, Villager foreman) {
        if (player.containerMenu instanceof WorkerMenu menu && menu.villager() != null) {
            return menu.villager();
        }
        return foreman != null ? foreman : worker;
    }

    private static WorkerData menuData(ServerPlayer player, WorkerData workerData, WorkerData foremanData) {
        Villager menu = menuVillager(player, null, null);
        if (menu != null) {
            WorkerData data = WorkerAttachments.get(menu).orElse(null);
            if (data != null) {
                return data;
            }
        }
        return foremanData != null ? foremanData : workerData;
    }

    private static String name(Villager villager) {
        return villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString();
    }
}
