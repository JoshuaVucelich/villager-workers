/**
 * Loader-agnostic core for Villager Workers.
 *
 * <p>Types in this package may use the JDK and Minecraft ({@code net.minecraft}).
 * They must not import a mod loader ({@code net.neoforged}, Fabric, or Forge).
 * Attachments, menus, screens, payloads, and game events are registered from the
 * loader package. Job rules, saved data, command parsing, and permission checks
 * belong here so another loader can reuse them later.
 */
package com.vucelich.villagerworkers.common;
