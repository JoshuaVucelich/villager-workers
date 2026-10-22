package com.vucelich.villagerworkers.neoforge;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
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
        NeoForge.EVENT_BUS.addListener(VillagerWorkersMod::onEntityJoin);
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

    private static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.npc.Villager villager) {
            WorkerAttachments.get(villager).ifPresent(data -> Scaffolding.bind(villager, data));
        }
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
            Path path = modJar();
            if (path == null || !Files.isRegularFile(path)) {
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

    /**
     * Dev runs load from a classes directory. A dedicated server loads the mod through a union URL
     * such as union:/mods/example.jar%2312!/ so the plain file URI is not enough.
     */
    private static Path modJar() {
        var domain = VillagerWorkersMod.class.getProtectionDomain();
        if (domain == null || domain.getCodeSource() == null) {
            return null;
        }
        URL url = domain.getCodeSource().getLocation();
        if (url == null) {
            return null;
        }
        try {
            Path direct = Path.of(url.toURI());
            if (Files.isRegularFile(direct)) {
                return direct;
            }
        } catch (Exception ignored) {
            // The loader URL is not a file URI. The text form is parsed below.
        }
        String text = url.toString();
        int bang = text.indexOf('!');
        if (bang >= 0) {
            text = text.substring(0, bang);
        }
        int marker = text.indexOf("%23");
        if (marker >= 0) {
            text = text.substring(0, marker);
        }
        marker = text.indexOf('#');
        if (marker >= 0) {
            text = text.substring(0, marker);
        }
        if (text.startsWith("union:")) {
            text = text.substring("union:".length());
        }
        if (text.startsWith("jar:")) {
            text = text.substring("jar:".length());
        }
        try {
            if (text.startsWith("file:")) {
                Path path = Path.of(URI.create(text));
                return Files.isRegularFile(path) ? path : null;
            }
        } catch (Exception ignored) {
            return null;
        }
        if (text.startsWith("/")) {
            Path path = Path.of(text);
            return Files.isRegularFile(path) ? path : null;
        }
        return null;
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
