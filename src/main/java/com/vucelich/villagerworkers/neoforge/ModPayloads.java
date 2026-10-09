package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.VillagerWorkers;
import com.vucelich.villagerworkers.neoforge.client.ClientScreens;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public record PermissionsView(String ownerName, List<Person> trusted, List<Person> online) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PermissionsView> STREAM_CODEC = StreamCodec.of(
                (buf, view) -> {
                    buf.writeUtf(view.ownerName);
                    writePeople(buf, view.trusted);
                    writePeople(buf, view.online);
                },
                buf -> new PermissionsView(buf.readUtf(), readPeople(buf), readPeople(buf)));

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

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VillagerWorkers.MOD_ID, path));
    }
}
