package com.vucelich.villagerworkers.common;

/**
 * Something a player can try to do to a worker. The server checks this before it changes saved data.
 */
public enum WorkerAction {
    /** Open the worker screen or look at status. */
    OPEN_SCREEN,
    /** Start, stop, come, or resume. */
    SET_ACTIVITY,
    /** Job, setup, wand, chests, whitelist, and valuables. */
    EDIT_SETUP,
    /** {@code @Name} chat commands. */
    CHAT,
    /** Add or remove a trusted player. */
    EDIT_TRUSTED,
    /** Give ownership to someone else. */
    TRANSFER_OWNER,
    /** Read the trusted list. */
    VIEW_PERMISSIONS
}
