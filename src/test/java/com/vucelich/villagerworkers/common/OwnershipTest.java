package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class OwnershipTest {
    private final UUID primary = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID third = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID trusted = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID stranger = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void hireStartsWithOneOwner() {
        WorkerData data = WorkerData.hire(primary);
        assertEquals(1, data.ownerCount());
        assertEquals(Set.of(primary), data.ownerIds());
        assertEquals(8, SetupLimits.MAX_OWNERS);
    }

    @Test
    void threeOwnersMayDoEveryOwnerAction() {
        WorkerData data = WorkerData.hire(primary);
        assertTrue(data.addOwner(new TrustedPlayer(second, "Second")));
        assertTrue(data.addOwner(new TrustedPlayer(third, "Third")));
        assertEquals(3, data.ownerCount());
        int allows = 0;
        for (UUID owner : List.of(primary, second, third)) {
            for (WorkerAction action : WorkerAction.values()) {
                assertTrue(WorkerPermissions.allows(data.ownerIds(), data.trustedIds(), owner, action), action.name());
                allows++;
            }
        }
        assertEquals(21, allows);
    }

    @Test
    void trustedPlayerStillCannotChangeOwnership() {
        WorkerData data = WorkerData.hire(primary);
        assertTrue(data.addTrusted(new TrustedPlayer(trusted, "Helper")));
        int allowed = 0;
        int denied = 0;
        for (WorkerAction action : WorkerAction.values()) {
            if (WorkerPermissions.allows(data.ownerIds(), data.trustedIds(), trusted, action)) {
                allowed++;
            } else {
                denied++;
            }
        }
        assertEquals(4, allowed);
        assertEquals(3, denied);
        assertFalse(WorkerPermissions.allows(data.ownerIds(), data.trustedIds(), trusted, WorkerAction.TRANSFER_OWNER));
        for (WorkerAction action : WorkerAction.values()) {
            assertFalse(WorkerPermissions.allows(data.ownerIds(), data.trustedIds(), stranger, action), action.name());
        }
    }

    @Test
    void soleTransferLeavesOneOwnerAndDoesNotTrustTheOthers() {
        WorkerData data = WorkerData.hire(primary);
        data.addOwner(new TrustedPlayer(second, "Second"));
        data.addOwner(new TrustedPlayer(third, "Third"));
        data.transferSole(new TrustedPlayer(second, "Second"));
        assertEquals(1, data.ownerCount());
        assertEquals(Set.of(second), data.ownerIds());
        assertFalse(data.isOwner(primary));
        assertFalse(data.isOwner(third));
        assertTrue(data.trustedIds().isEmpty());
        assertFalse(data.trustedIds().contains(primary));
        assertFalse(data.trustedIds().contains(third));
    }

    @Test
    void promotingATrustedPlayerMovesThemOutOfTheTrustedSet() {
        WorkerData data = WorkerData.hire(primary);
        assertTrue(data.addTrusted(new TrustedPlayer(second, "Second")));
        assertEquals(1, data.trustedIds().size());
        assertTrue(data.addOwner(new TrustedPlayer(second, "Second")));
        assertEquals(2, data.ownerCount());
        assertEquals(0, data.trustedIds().size());
    }

    @Test
    void ownerListStopsAtEight() {
        WorkerData data = WorkerData.hire(primary);
        for (int i = 1; i < SetupLimits.MAX_OWNERS; i++) {
            assertTrue(data.addOwner(new TrustedPlayer(new UUID(0L, i), "P" + i)));
        }
        assertEquals(8, data.ownerCount());
        assertFalse(data.addOwner(new TrustedPlayer(new UUID(0L, 99), "Extra")));
        assertEquals(8, data.ownerCount());
    }

    @Test
    void codecRoundTripKeepsThreeOwnersAndLegacySaveKeepsOne() {
        WorkerData data = WorkerData.hire(primary);
        data.rememberOwnerName(primary, "Ada");
        assertTrue(data.addOwner(new TrustedPlayer(second, "Bea")));
        assertTrue(data.addOwner(new TrustedPlayer(third, "Cam")));
        Tag saved = WorkerData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        WorkerData loaded = WorkerData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        assertEquals(3, loaded.ownerCount());
        assertEquals(data.ownerIds(), loaded.ownerIds());

        CompoundTag legacy = new CompoundTag();
        legacy.putString("owner", primary.toString());
        WorkerData migrated = WorkerData.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertEquals(1, migrated.ownerCount());
        assertEquals(primary, migrated.owner());
        assertTrue(migrated.isOwner(primary));
    }

    @Test
    void offlineNameDoesNotResolve() {
        List<TrustedPlayer> online = List.of(
                new TrustedPlayer(primary, "Ada"),
                new TrustedPlayer(second, "Bea"));
        assertEquals(second, OnlineNames.find("bea", online).id());
        assertEquals(null, OnlineNames.find("Cam", online));
        assertEquals(null, OnlineNames.find("  ", online));
    }
}
