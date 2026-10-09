package com.vucelich.villagerworkers.neoforge;

import com.mojang.logging.LogUtils;
import com.vucelich.villagerworkers.common.VillagerWorkers;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * NeoForge entrypoint. Gameplay is specified in {@code docs/DESIGN.md} and is
 * not implemented yet.
 */
@Mod(VillagerWorkers.MOD_ID)
public final class VillagerWorkersMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public VillagerWorkersMod() {
        LOGGER.info("Villager Workers ({}) loaded; hiring and jobs are not implemented yet", VillagerWorkers.MOD_ID);
    }
}
