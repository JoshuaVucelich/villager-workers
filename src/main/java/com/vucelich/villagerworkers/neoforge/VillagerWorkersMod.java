package com.vucelich.villagerworkers.neoforge;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.vucelich.villagerworkers.common.VillagerWorkers;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.neoforge.client.ClientModEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(VillagerWorkers.MOD_ID)
public final class VillagerWorkersMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public VillagerWorkersMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, WorkerSettings.SPEC);
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        WorkerAttachments.ATTACHMENTS.register(modBus);
        modBus.addListener(ModPayloads::register);
        modBus.addListener(VillagerWorkersMod::addWandToTools);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientModEvents::registerScreens);
            modBus.addListener(ClientModEvents::registerLayers);
        }
        NeoForge.EVENT_BUS.addListener(WorkerServer::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, WorkerServer::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(WorkerServer::onTickBefore);
        NeoForge.EVENT_BUS.addListener(WorkerServer::onTick);
        NeoForge.EVENT_BUS.addListener(WorkerServer::onChat);
        NeoForge.EVENT_BUS.addListener(WorkerServer::onLogout);
        NeoForge.EVENT_BUS.addListener(WorkerServer::onLeaveLevel);
        WorkerData.setCarryDecodeWarning(detail ->
                LOGGER.warn("Could not decode a carry item for a worker; that slot was cleared: {}", detail));
        NeoForge.EVENT_BUS.addListener(JobRunner::onDeath);
        NeoForge.EVENT_BUS.addListener(JobRunner::onConvert);
        NeoForge.EVENT_BUS.addListener(VillagerWorkersMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(AssemblyGuards::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VillagerWorkersMod::onServerStopped);
        NeoForge.EVENT_BUS.addListener(WorkerHold::onServerStopped);
        LOGGER.info("Villager Workers ({}) loaded", VillagerWorkers.MOD_ID);
        logJarSha();
    }

    private static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        CreateLimits.apply(WorkerSettings.createSchematicUploadKb());
    }

    private static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        StructureBuilder.clear();
    }

    /** Dedicated server and client both log this so a release jar can be checked after boot. */
    private static void logJarSha() {
        try {
            var source = VillagerWorkersMod.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                LOGGER.info("Villager Workers jar sha256: unavailable");
                return;
            }
            Path path = Path.of(source.getLocation().toURI());
            if (!Files.isRegularFile(path)) {
                LOGGER.info("Villager Workers jar sha256: unavailable");
                return;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            LOGGER.info("Villager Workers jar sha256: {}", HexFormat.of().formatHex(digest.digest()));
        } catch (Exception ex) {
            LOGGER.info("Villager Workers jar sha256: unavailable");
        }
    }

    private static void addWandToTools(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.AREA_WAND);
            event.accept(ModItems.HARD_HAT);
            event.accept(ModItems.FOREMAN_HAT);
            event.accept(ModItems.CLIPBOARD);
            event.accept(ModItems.BLUEPRINT_ROLL);
        }
    }
}
