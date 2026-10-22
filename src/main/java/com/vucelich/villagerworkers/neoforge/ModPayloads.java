package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.SetupLimits;
import com.vucelich.villagerworkers.common.VillagerWorkers;
import com.vucelich.villagerworkers.neoforge.client.ClientScreens;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(HireChoicePayload.TYPE, HireChoicePayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.chooseHire(context.player(), payload.villagerId(), payload.hire()));
        registrar.playToServer(UpdateLumberjackPayload.TYPE, UpdateLumberjackPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.updateLumberjack(context.player(), payload));
        registrar.playToServer(UpdateMinerPayload.TYPE, UpdateMinerPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.updateMiner(context.player(), payload));
        registrar.playToServer(UpdateBuilderPayload.TYPE, UpdateBuilderPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.touchBuilder(context.player(), payload.villagerId()));
        registrar.playToServer(UpdateWhitelistPayload.TYPE, UpdateWhitelistPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.updateWhitelist(context.player(), payload));
        registrar.playToServer(AddTrustedPayload.TYPE, AddTrustedPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.addTrusted(context.player(), payload));
        registrar.playToServer(RemoveTrustedPayload.TYPE, RemoveTrustedPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.removeTrusted(context.player(), payload));
        registrar.playToServer(TransferOwnerPayload.TYPE, TransferOwnerPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.transfer(context.player(), payload));
        registrar.playToServer(AddOwnerPayload.TYPE, AddOwnerPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.addOwner(context.player(), payload));
        registrar.playToServer(RemoveOwnerPayload.TYPE, RemoveOwnerPayload.STREAM_CODEC, (payload, context) ->
                WorkerServer.removeOwner(context.player(), payload));
        registrar.playToClient(OpenHireScreenPayload.TYPE, OpenHireScreenPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.openHire(payload.villagerId(), payload.name());
            }
        });
        registrar.playToClient(WorkerSnapshotPayload.TYPE, WorkerSnapshotPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.applySnapshot(payload.view());
            }
        });
        registrar.playToClient(PermissionsSnapshotPayload.TYPE, PermissionsSnapshotPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.applyPermissions(payload.view());
            }
        });
        registrar.playToServer(AssignCrewPayload.TYPE, AssignCrewPayload.STREAM_CODEC, (payload, context) ->
                CrewService.assign(serverPlayer(context.player()), payload.workerId(), payload.foremanId()));
        registrar.playToServer(UnassignCrewPayload.TYPE, UnassignCrewPayload.STREAM_CODEC, (payload, context) ->
                CrewService.unassign(serverPlayer(context.player()), payload.workerId()));
        registrar.playToServer(MoveCrewPayload.TYPE, MoveCrewPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = serverPlayer(context.player());
            if (payload.toPlayer()) {
                CrewService.giveToPlayer(player, payload.workerId(), payload.playerName());
            } else {
                CrewService.move(player, payload.workerId(), payload.foremanId(), false, null);
            }
        });
        registrar.playToServer(CrewOrderPayload.TYPE, CrewOrderPayload.STREAM_CODEC, (payload, context) ->
                CrewService.order(serverPlayer(context.player()), payload.foremanId(), payload.targetId(), payload.ticked(), payload.orderId()));
        registrar.playToServer(SetOverridePayload.TYPE, SetOverridePayload.STREAM_CODEC, (payload, context) ->
                CrewService.setOverride(serverPlayer(context.player()), payload.workerId(), payload.override()));
        registrar.playToClient(CrewSnapshotPayload.TYPE, CrewSnapshotPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.applyCrew(payload.view());
            }
        });
    }

    public record HireChoicePayload(UUID villagerId, boolean hire) implements CustomPacketPayload {
        public static final Type<HireChoicePayload> TYPE = ModPayloads.payloadType("hire_choice");
        public static final StreamCodec<RegistryFriendlyByteBuf, HireChoicePayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, HireChoicePayload::villagerId,
                ByteBufCodecs.BOOL, HireChoicePayload::hire,
                HireChoicePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenHireScreenPayload(UUID villagerId, String name) implements CustomPacketPayload {
        public static final Type<OpenHireScreenPayload> TYPE = ModPayloads.payloadType("open_hire_screen");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenHireScreenPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, OpenHireScreenPayload::villagerId,
                ByteBufCodecs.STRING_UTF8, OpenHireScreenPayload::name,
                OpenHireScreenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateLumberjackPayload(UUID villagerId, boolean clearLeaves, boolean replant) implements CustomPacketPayload {
        public static final Type<UpdateLumberjackPayload> TYPE = ModPayloads.payloadType("update_lumberjack");
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateLumberjackPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, UpdateLumberjackPayload::villagerId,
                ByteBufCodecs.BOOL, UpdateLumberjackPayload::clearLeaves,
                ByteBufCodecs.BOOL, UpdateLumberjackPayload::replant,
                UpdateLumberjackPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateMinerPayload(
            UUID villagerId,
            String mode,
            int width,
            int height,
            int length,
            String wallBlock,
            int branchSpacing,
            int branchLength,
            int shaftWidth,
            int shaftDepth,
            boolean shaftUp,
            String valuables) implements CustomPacketPayload {
        public static final Type<UpdateMinerPayload> TYPE = ModPayloads.payloadType("update_miner");
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateMinerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUUID(payload.villagerId);
                    buf.writeUtf(payload.mode);
                    buf.writeVarInt(payload.width);
                    buf.writeVarInt(payload.height);
                    buf.writeVarInt(payload.length);
                    buf.writeUtf(payload.wallBlock);
                    buf.writeVarInt(payload.branchSpacing);
                    buf.writeVarInt(payload.branchLength);
                    buf.writeVarInt(payload.shaftWidth);
                    buf.writeVarInt(payload.shaftDepth);
                    buf.writeBoolean(payload.shaftUp);
                    buf.writeUtf(payload.valuables);
                },
                buf -> new UpdateMinerPayload(
                        buf.readUUID(),
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
                        buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateBuilderPayload(UUID villagerId) implements CustomPacketPayload {
        public static final Type<UpdateBuilderPayload> TYPE = ModPayloads.payloadType("update_builder");
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateBuilderPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeUUID(payload.villagerId()),
                buf -> new UpdateBuilderPayload(buf.readUUID()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateWhitelistPayload(UUID villagerId, List<String> ids) implements CustomPacketPayload {
        public static final Type<UpdateWhitelistPayload> TYPE = ModPayloads.payloadType("update_whitelist");
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateWhitelistPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, UpdateWhitelistPayload::villagerId,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), UpdateWhitelistPayload::ids,
                UpdateWhitelistPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AddTrustedPayload(UUID villagerId, String playerName) implements CustomPacketPayload {
        public static final Type<AddTrustedPayload> TYPE = ModPayloads.payloadType("add_trusted");
        public static final StreamCodec<RegistryFriendlyByteBuf, AddTrustedPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, AddTrustedPayload::villagerId,
                ByteBufCodecs.STRING_UTF8, AddTrustedPayload::playerName,
                AddTrustedPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RemoveTrustedPayload(UUID villagerId, UUID trustedId) implements CustomPacketPayload {
        public static final Type<RemoveTrustedPayload> TYPE = ModPayloads.payloadType("remove_trusted");
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveTrustedPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, RemoveTrustedPayload::villagerId,
                net.minecraft.core.UUIDUtil.STREAM_CODEC, RemoveTrustedPayload::trustedId,
                RemoveTrustedPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AddOwnerPayload(UUID villagerId, String playerName) implements CustomPacketPayload {
        public static final Type<AddOwnerPayload> TYPE = ModPayloads.payloadType("add_owner");
        public static final StreamCodec<RegistryFriendlyByteBuf, AddOwnerPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, AddOwnerPayload::villagerId,
                ByteBufCodecs.STRING_UTF8, AddOwnerPayload::playerName,
                AddOwnerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RemoveOwnerPayload(UUID villagerId, UUID ownerId) implements CustomPacketPayload {
        public static final Type<RemoveOwnerPayload> TYPE = ModPayloads.payloadType("remove_owner");
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveOwnerPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, RemoveOwnerPayload::villagerId,
                net.minecraft.core.UUIDUtil.STREAM_CODEC, RemoveOwnerPayload::ownerId,
                RemoveOwnerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TransferOwnerPayload(UUID villagerId, UUID newOwner) implements CustomPacketPayload {
        public static final Type<TransferOwnerPayload> TYPE = ModPayloads.payloadType("transfer_owner");
        public static final StreamCodec<RegistryFriendlyByteBuf, TransferOwnerPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, TransferOwnerPayload::villagerId,
                net.minecraft.core.UUIDUtil.STREAM_CODEC, TransferOwnerPayload::newOwner,
                TransferOwnerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WorkerSnapshotPayload(WorkerView view) implements CustomPacketPayload {
        public static final Type<WorkerSnapshotPayload> TYPE = ModPayloads.payloadType("worker_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkerSnapshotPayload> STREAM_CODEC =
                WorkerView.STREAM_CODEC.map(WorkerSnapshotPayload::new, WorkerSnapshotPayload::view);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PermissionsSnapshotPayload(PermissionsView view) implements CustomPacketPayload {
        public static final Type<PermissionsSnapshotPayload> TYPE = ModPayloads.payloadType("permissions_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, PermissionsSnapshotPayload> STREAM_CODEC =
                PermissionsView.STREAM_CODEC.map(PermissionsSnapshotPayload::new, PermissionsSnapshotPayload::view);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PermissionsView(String ownerName, List<Person> owners, List<Person> trusted, List<Person> online) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PermissionsView> STREAM_CODEC = StreamCodec.of(
                (buf, view) -> {
                    buf.writeUtf(view.ownerName);
                    writePeople(buf, view.owners);
                    writePeople(buf, view.trusted);
                    writePeople(buf, view.online);
                },
                buf -> new PermissionsView(buf.readUtf(), readPeople(buf), readPeople(buf), readPeople(buf)));

        private static void writePeople(RegistryFriendlyByteBuf buf, List<Person> people) {
            buf.writeVarInt(people.size());
            for (Person person : people) {
                buf.writeUUID(person.id());
                buf.writeUtf(person.name());
            }
        }

        private static List<Person> readPeople(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<Person> people = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                people.add(new Person(buf.readUUID(), buf.readUtf()));
            }
            return List.copyOf(people);
        }
    }

    public record Person(UUID id, String name) {}

    public record AssignCrewPayload(UUID workerId, UUID foremanId) implements CustomPacketPayload {
        public static final Type<AssignCrewPayload> TYPE = ModPayloads.payloadType("assign_crew");
        public static final StreamCodec<RegistryFriendlyByteBuf, AssignCrewPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, AssignCrewPayload::workerId,
                net.minecraft.core.UUIDUtil.STREAM_CODEC, AssignCrewPayload::foremanId,
                AssignCrewPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UnassignCrewPayload(UUID workerId) implements CustomPacketPayload {
        public static final Type<UnassignCrewPayload> TYPE = ModPayloads.payloadType("unassign_crew");
        public static final StreamCodec<RegistryFriendlyByteBuf, UnassignCrewPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, UnassignCrewPayload::workerId,
                UnassignCrewPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MoveCrewPayload(UUID workerId, UUID foremanId, boolean toPlayer, String playerName) implements CustomPacketPayload {
        public static final Type<MoveCrewPayload> TYPE = ModPayloads.payloadType("move_crew");
        public static final StreamCodec<RegistryFriendlyByteBuf, MoveCrewPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUUID(payload.workerId);
                    buf.writeUUID(payload.foremanId);
                    buf.writeBoolean(payload.toPlayer);
                    buf.writeUtf(payload.playerName);
                },
                buf -> new MoveCrewPayload(buf.readUUID(), buf.readUUID(), buf.readBoolean(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CrewOrderPayload(UUID foremanId, String targetId, List<UUID> ticked, String orderId) implements CustomPacketPayload {
        public static final Type<CrewOrderPayload> TYPE = ModPayloads.payloadType("crew_order");
        public static final StreamCodec<RegistryFriendlyByteBuf, CrewOrderPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUUID(payload.foremanId);
                    buf.writeUtf(payload.targetId);
                    buf.writeVarInt(payload.ticked.size());
                    for (UUID id : payload.ticked) {
                        buf.writeUUID(id);
                    }
                    buf.writeUtf(payload.orderId);
                },
                buf -> {
                    UUID foremanId = buf.readUUID();
                    String targetId = buf.readUtf();
                    int size = buf.readVarInt();
                    if (size < 0 || size > SetupLimits.MAX_CREW + 1) {
                        throw new io.netty.handler.codec.DecoderException("crew tick list");
                    }
                    List<UUID> ticked = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        ticked.add(buf.readUUID());
                    }
                    return new CrewOrderPayload(foremanId, targetId, List.copyOf(ticked), buf.readUtf());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetOverridePayload(UUID workerId, boolean override) implements CustomPacketPayload {
        public static final Type<SetOverridePayload> TYPE = ModPayloads.payloadType("set_override");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetOverridePayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, SetOverridePayload::workerId,
                ByteBufCodecs.BOOL, SetOverridePayload::override,
                SetOverridePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CrewSnapshotPayload(CrewView view) implements CustomPacketPayload {
        public static final Type<CrewSnapshotPayload> TYPE = ModPayloads.payloadType("crew_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, CrewSnapshotPayload> STREAM_CODEC =
                CrewView.STREAM_CODEC.map(CrewSnapshotPayload::new, CrewSnapshotPayload::view);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CrewView(
            UUID villagerId,
            boolean foreman,
            boolean overrideForeman,
            String assignedName,
            List<Person> foremen,
            List<Person> unassigned,
            List<Person> crew) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CrewView> STREAM_CODEC = StreamCodec.of(
                (buf, view) -> {
                    buf.writeUUID(view.villagerId);
                    buf.writeBoolean(view.foreman);
                    buf.writeBoolean(view.overrideForeman);
                    buf.writeUtf(view.assignedName);
                    writePeople(buf, view.foremen);
                    writePeople(buf, view.unassigned);
                    writePeople(buf, view.crew);
                },
                buf -> new CrewView(
                        buf.readUUID(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readUtf(),
                        readPeople(buf),
                        readPeople(buf),
                        readPeople(buf)));

        private static void writePeople(RegistryFriendlyByteBuf buf, List<Person> people) {
            buf.writeVarInt(people.size());
            for (Person person : people) {
                buf.writeUUID(person.id());
                buf.writeUtf(person.name());
            }
        }

        private static List<Person> readPeople(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > 32) {
                throw new io.netty.handler.codec.DecoderException("crew list");
            }
            List<Person> people = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                people.add(new Person(buf.readUUID(), buf.readUtf()));
            }
            return List.copyOf(people);
        }
    }

    private static ServerPlayer serverPlayer(net.minecraft.world.entity.player.Player player) {
        return (ServerPlayer) player;
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VillagerWorkers.MOD_ID, path));
    }
}
