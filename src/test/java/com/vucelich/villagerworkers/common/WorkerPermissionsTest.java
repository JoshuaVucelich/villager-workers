package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerPermissionsTest {
    private final UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID trusted = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID stranger = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void ownerMayDoEverything() {
        for (WorkerAction action : WorkerAction.values()) {
            assertTrue(WorkerPermissions.allows(owner, Set.of(trusted), owner, action), action.name());
        }
    }

    @Test
    void trustedMayOperateButNotChangeOwnership() {
        assertTrue(WorkerPermissions.allows(owner, Set.of(trusted), trusted, WorkerAction.OPEN_SCREEN));
        assertTrue(WorkerPermissions.allows(owner, Set.of(trusted), trusted, WorkerAction.SET_ACTIVITY));
        assertTrue(WorkerPermissions.allows(owner, Set.of(trusted), trusted, WorkerAction.EDIT_SETUP));
        assertTrue(WorkerPermissions.allows(owner, Set.of(trusted), trusted, WorkerAction.CHAT));
        assertFalse(WorkerPermissions.allows(owner, Set.of(trusted), trusted, WorkerAction.EDIT_TRUSTED));
        assertFalse(WorkerPermissions.allows(owner, Set.of(trusted), trusted, WorkerAction.TRANSFER_OWNER));
        assertFalse(WorkerPermissions.allows(owner, Set.of(trusted), trusted, WorkerAction.VIEW_PERMISSIONS));
    }

    @Test
    void strangerMayDoNothing() {
        for (WorkerAction action : WorkerAction.values()) {
            assertFalse(WorkerPermissions.allows(owner, Set.of(trusted), stranger, action), action.name());
        }
    }

    @Test
    void roleFollowsTheOwnerUuidNotTheClient() {
        assertEquals(WorkerRole.OWNER, WorkerPermissions.role(owner, Set.of(trusted), owner));
        assertEquals(WorkerRole.TRUSTED, WorkerPermissions.role(owner, Set.of(trusted), trusted));
        assertEquals(WorkerRole.NONE, WorkerPermissions.role(owner, Set.of(), trusted));
        assertEquals(WorkerRole.NONE, WorkerPermissions.role(owner, Set.of(trusted), null));
    }
}
