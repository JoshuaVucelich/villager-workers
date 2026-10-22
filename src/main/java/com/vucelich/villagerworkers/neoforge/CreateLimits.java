package com.vucelich.villagerworkers.neoforge;

import net.neoforged.fml.ModList;

/**
 * Raises Create's schematic upload cap when the pack asks for more than the 256 KB default.
 * Reflection keeps this class loadable when Create is absent. The default does not touch Create.
 * maxSchematicPacketSize is also raised, up to Create's own 32767 byte cap, or a larger file still uploads in tiny chunks.
 */
public final class CreateLimits {
    private CreateLimits() {}

    public static void apply(int kilobytes) {
        if (kilobytes <= 256 || !ModList.get().isLoaded("create")) {
            return;
        }
        try {
            Class<?> configs = Class.forName("com.simibubi.create.infrastructure.config.AllConfigs");
            Object server = configs.getMethod("server").invoke(null);
            Object schematics = server.getClass().getField("schematics").get(server);
            setAtLeast(schematics, "maxTotalSchematicSize", kilobytes);
            int packet = Math.min(kilobytes * 1024, 32767);
            setAtLeast(schematics, "maxSchematicPacketSize", packet);
            VillagerWorkersMod.LOGGER.info("Create schematic upload limit is {} KB.", kilobytes);
        } catch (Throwable ex) {
            VillagerWorkersMod.LOGGER.warn("Could not raise Create schematic upload limit: {}", ex.toString());
        }
    }

    private static void setAtLeast(Object schematics, String field, int value) throws ReflectiveOperationException {
        Object limit = schematics.getClass().getField(field).get(schematics);
        Object current = limit.getClass().getMethod("get").invoke(limit);
        int now = current instanceof Integer number ? number : 0;
        if (now >= value) {
            return;
        }
        limit.getClass().getMethod("set", Object.class).invoke(limit, Integer.valueOf(value));
    }
}
