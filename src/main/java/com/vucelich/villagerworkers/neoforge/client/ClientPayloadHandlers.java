package com.vucelich.villagerworkers.neoforge.client;

import com.vucelich.villagerworkers.neoforge.ModPayloads;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Client-only payload handlers. Dedicated servers must not load this class. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                ModPayloads.OpenHireScreenPayload.TYPE,
                ModPayloads.OpenHireScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientScreens.openHire(payload.villagerId(), payload.name()));
        registrar.playToClient(
                ModPayloads.WorkerSnapshotPayload.TYPE,
                ModPayloads.WorkerSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientScreens.applySnapshot(payload.view()));
        registrar.playToClient(
                ModPayloads.PermissionsSnapshotPayload.TYPE,
                ModPayloads.PermissionsSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientScreens.applyPermissions(payload.view()));
    }
}
