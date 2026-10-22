package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnershipTest {
    private final UUID primary = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID third = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID trusted = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID stranger = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void hireStartsWithOneOwnerAndCapsAtEight() {
        WorkerData data = WorkerData.hire(primary);
        assertEquals(1, data.ownerCount());
        assertEquals(List.of(primary), data.allOwners());
        assertEquals(primary, data.originalOwner());
        List<UUID> extra = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            extra.add(UUID.nameUUIDFromBytes(("owner-" + i).getBytes()));
        }
        assertTrue(data.addOwner(second));
        assertTrue(data.addOwner(third));
        assertEquals(3, data.ownerCount());
        for (UUID id : extra) {
            data.addOwner(id);
        }
        assertEquals(SetupLimits.MAX_OWNERS, data.ownerCount());
        assertFalse(data.addOwner(stranger));
    }

    @Test
    void threeOwnersMayDoEveryActionAndTrustedStillCannot() {
        int allows = 0;
        for (UUID actor : List.of(primary, second, third)) {
            for (WorkerAction action : WorkerAction.values()) {
                if (WorkerPermissions.allows(primary, Set.of(second, third), Set.of(trusted), actor, action)) {
                    allows++;
                }
            }
        }
        assertEquals(7, WorkerAction.values().length);
        assertEquals(21, allows);
        int trustedAllows = 0;
        int trustedDenies = 0;
        for (WorkerAction action : WorkerAction.values()) {
            if (WorkerPermissions.allows(primary, Set.of(second, third), Set.of(trusted), trusted, action)) {
                trustedAllows++;
            } else {
                trustedDenies++;
            }
        }
        assertEquals(4, trustedAllows);
        assertEquals(3, trustedDenies);
        for (WorkerAction action : WorkerAction.values()) {
            assertFalse(WorkerPermissions.allows(primary, Set.of(second, third), Set.of(trusted), stranger, action));
        }
    }

    @Test
    void addingATrustedPlayerMakesThemAnOwnerAndDropsTrust() {
        WorkerData data = WorkerData.hire(primary);
        assertTrue(data.addTrusted(new TrustedPlayer(trusted, "Ada")));
        assertEquals(1, data.trusted().size());
        assertTrue(data.addOwner(trusted));
        assertEquals(2, data.ownerCount());
        assertEquals(0, data.trusted().size());
        assertTrue(data.isOwner(trusted));
    }

    @Test
    void removeCoOwnerLeavesThePrimary() {
        WorkerData data = WorkerData.hire(primary);
        data.addOwner(second);
        data.addOwner(third);
        assertFalse(data.removeCoOwner(primary));
        assertEquals(3, data.ownerCount());
        assertTrue(data.removeCoOwner(second));
        assertEquals(List.of(primary, third), data.allOwners());
        assertFalse(data.removeCoOwner(second));
    }

    @Test
    void soleTransferLeavesOneOwnerAndDoesNotTrustTheOthers() {
        WorkerData data = WorkerData.hire(primary);
        data.addOwner(second);
        data.addOwner(third);
        data.addTrusted(new TrustedPlayer(trusted, "Ada"));
        data.transferSoleTo(second);
        assertEquals(1, data.ownerCount());
        assertEquals(second, data.owner());
        assertEquals(primary, data.originalOwner());
        assertFalse(data.isOwner(primary));
        assertFalse(data.isOwner(third));
        assertFalse(data.trustedIds().contains(primary));
        assertFalse(data.trustedIds().contains(third));
        assertFalse(data.trustedIds().contains(second));
        assertTrue(data.trustedIds().contains(trusted));
    }

    @Test
    void threeOwnersReloadAndALegacySaveStaysOneOwner() {
        WorkerData data = WorkerData.hire(primary);
        data.addOwner(second);
        data.addOwner(third);
        WorkerData again = WorkerData.CODEC
                .parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow())
                .getOrThrow();
        assertEquals(List.of(primary, second, third), again.allOwners());
        assertEquals(primary, again.originalOwner());
        String legacy = "{\"owner\":\"" + primary + "\",\"job\":\"miner\"}";
        WorkerData old = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(legacy)).getOrThrow();
        assertEquals(1, old.ownerCount());
        assertEquals(primary, old.owner());
        assertEquals(primary, old.originalOwner());
    }

    @Test
    void missingOriginalOwnerUsesTheFirstListedOwner() {
        String json = "{\"owner\":\"" + second + "\",\"owners\":[\"" + primary + "\",\"" + second + "\"],\"job\":\"miner\"}";
        WorkerData data = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(primary, data.owner());
        assertEquals(List.of(primary, second), data.allOwners());
        assertEquals(primary, data.originalOwner());
    }

    @Test
    void explicitOriginalOwnerSurvivesSoleTransfer() {
        WorkerData data = WorkerData.hire(primary);
        data.addOwner(second);
        data.transferSoleTo(second);
        String encoded = WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().toString();
        assertTrue(encoded.contains("original_owner"));
        assertTrue(encoded.contains(primary.toString()));
        WorkerData again = WorkerData.CODEC
                .parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow())
                .getOrThrow();
        assertEquals(second, again.owner());
        assertEquals(1, again.ownerCount());
        assertEquals(primary, again.originalOwner());
    }
}
