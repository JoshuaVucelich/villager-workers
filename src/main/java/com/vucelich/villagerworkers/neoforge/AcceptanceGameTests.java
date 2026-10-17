package com.vucelich.villagerworkers.neoforge;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicExport;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.foundation.utility.CreatePaths;
import com.vucelich.villagerworkers.common.LumberjackConfig;
import com.vucelich.villagerworkers.common.MinerConfig;
import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.VillagerWorkers;
import com.vucelich.villagerworkers.common.WorkArea;
import com.vucelich.villagerworkers.common.WorkOrigin;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerJob;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game checks for the milestone acceptance criteria. These run on the NeoForge game test server.
 */
@GameTestHolder(VillagerWorkers.MOD_ID)
@PrefixGameTestTemplate(false)
public class AcceptanceGameTests {
    private static final Set<BlockPos> CANCEL_BREAKS = ConcurrentHashMap.newKeySet();
    private static final String[] PACK_BLOCKS = {
        "create:shaft",
        "create:track",
        "createbigcannons:cast_iron_block",
        "createbigcannons:cast_iron_cannon_barrel",
        "railways:track_oak",
        "numismatics:vendor",
        "numismatics:andesite_depositor",
        "aeronautics:propeller_bearing",
        "aeronautics:andesite_propeller",
        "simulated:auger_shaft"
    };

    static {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (CANCEL_BREAKS.contains(event.getPos())) {
                event.setCanceled(true);
            }
        });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 80)
    public static void hireWithNameTagSetsOwner(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        RecordingPlayer player = join(helper, "Hirer");
        stand(helper, player, 2, 1, 3);
        nameTag(player, "Birch");
        player.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(5, () -> {
            WorkerServer.chooseHire(player, villager.getUUID(), true);
            WorkerData data = WorkerAttachments.get(villager).orElse(null);
            helper.assertTrue(data != null, "hire did not create worker data");
            helper.assertTrue(player.getUUID().equals(data.owner()), "owner was not the naming player");
            helper.assertTrue(data.job() == WorkerJob.NONE, "job");
            helper.assertTrue(data.activity() == WorkerActivity.IDLE, "activity");
            helper.assertTrue(data.trusted().isEmpty(), "trusted set");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 80)
    public static void namedChoiceWritesNoAttachment(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        RecordingPlayer player = join(helper, "Namer");
        stand(helper, player, 2, 1, 3);
        nameTag(player, "JustNamed");
        player.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(5, () -> {
            WorkerServer.chooseHire(player, villager.getUUID(), false);
            helper.assertTrue(player.saw("Kept as a named villager"), "Named did not accept the offer");
            helper.assertFalse(WorkerAttachments.isWorker(villager), "Named stored an attachment");
            helper.assertTrue(villager.getCustomName() != null && "JustNamed".equals(villager.getCustomName().getString()), "name");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 40)
    public static void babyNameTagDoesNotHire(GameTestHelper helper) {
        Villager baby = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        baby.setBaby(true);
        RecordingPlayer player = join(helper, "BabyNamer");
        stand(helper, player, 2, 1, 3);
        nameTag(player, "Kid");
        player.interactOn(baby, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(WorkerAttachments.isWorker(baby), "baby became a worker");
            helper.assertTrue(baby.getCustomName() != null && "Kid".equals(baby.getCustomName().getString()), "baby was not renamed");
            WorkerServer.chooseHire(player, baby.getUUID(), true);
            helper.assertFalse(WorkerAttachments.isWorker(baby), "forged hire hired a baby");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 40)
    public static void forgedHirePacketsAreRejected(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setCustomName(Component.literal("Target"));
        RecordingPlayer player = join(helper, "Forger");
        stand(helper, player, 2, 1, 3);
        WorkerServer.chooseHire(player, villager.getUUID(), true);
        helper.assertFalse(WorkerAttachments.isWorker(villager), "hire without an offer succeeded");
        expirePending(player, villager.getUUID());
        WorkerServer.chooseHire(player, villager.getUUID(), true);
        helper.assertFalse(WorkerAttachments.isWorker(villager), "expired hire succeeded");

        nameTag(player, "Target");
        player.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(5, () -> {
            WorkerServer.chooseHire(player, UUID.randomUUID(), true);
            helper.assertFalse(WorkerAttachments.isWorker(villager), "mismatched villager id hired someone");
            stand(helper, player, 2, 1, 14);
            WorkerServer.chooseHire(player, villager.getUUID(), true);
            helper.assertFalse(WorkerAttachments.isWorker(villager), "hire from more than 6 blocks succeeded");
            stand(helper, player, 2, 1, 3);
            WorkerServer.chooseHire(player, villager.getUUID(), true);
            helper.assertTrue(WorkerAttachments.isWorker(villager), "a valid close hire was rejected");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 40)
    public static void trustedCannotChangeOwnership(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Owner");
        RecordingPlayer trusted = join(helper, "Trusted");
        RecordingPlayer stranger = join(helper, "Stranger");
        Villager villager = worker(helper, owner, "Crew", 2, 1, 2);
        stand(helper, owner, 2, 1, 3);
        stand(helper, trusted, 3, 1, 2);
        stand(helper, stranger, 2, 1, 4);
        openMenu(owner, villager);
        WorkerServer.addTrusted(owner, new ModPayloads.AddTrustedPayload(villager.getUUID(), "Trusted"));
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        helper.assertTrue(data.trustedIds().contains(trusted.getUUID()), "online player was not trusted");
        owner.closeContainer();

        openMenu(trusted, villager);
        UUID previous = data.owner();
        WorkerServer.transfer(trusted, new ModPayloads.TransferOwnerPayload(villager.getUUID(), stranger.getUUID()));
        WorkerServer.addTrusted(trusted, new ModPayloads.AddTrustedPayload(villager.getUUID(), "Stranger"));
        helper.assertTrue(previous.equals(data.owner()), "trusted player transferred ownership");
        helper.assertFalse(data.trustedIds().contains(stranger.getUUID()), "trusted player edited the trusted set");
        trusted.closeContainer();

        openMenu(stranger, villager);
        WorkerServer.transfer(stranger, new ModPayloads.TransferOwnerPayload(villager.getUUID(), stranger.getUUID()));
        helper.assertTrue(previous.equals(WorkerAttachments.get(villager).orElseThrow().owner()), "stranger transferred ownership");
        helper.succeed();
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 40)
    public static void workerScreenSavesJob(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Boss");
        Villager villager = worker(helper, owner, "Joiner", 2, 1, 2);
        stand(helper, owner, 2, 1, 3);
        owner.setShiftKeyDown(true);
        owner.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertFalse(owner.containerMenu instanceof WorkerMenu, "sneak click opened the worker screen");
        owner.setShiftKeyDown(false);
        owner.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.containerMenu instanceof WorkerMenu, "owner right-click did not open the worker screen");
        WorkerMenu menu = (WorkerMenu) owner.containerMenu;
        helper.assertTrue(menu.clickMenuButton(owner, MenuIds.JOB_LUMBERJACK), "job button");
        helper.assertTrue(WorkerAttachments.get(villager).orElseThrow().job() == WorkerJob.LUMBERJACK, "job was not saved");
        helper.assertTrue(menu.clickMenuButton(owner, MenuIds.START), "start");
        helper.assertTrue(WorkerAttachments.get(villager).orElseThrow().activity() == WorkerActivity.WORKING, "start");
        helper.assertTrue(menu.clickMenuButton(owner, MenuIds.STOP), "stop");
        helper.assertTrue(WorkerAttachments.get(villager).orElseThrow().activity() == WorkerActivity.STOPPED, "stop");
        helper.assertTrue(menu.clickMenuButton(owner, MenuIds.COME), "come");
        helper.assertTrue(WorkerAttachments.get(villager).orElseThrow().activity() == WorkerActivity.COMING, "come");
        helper.succeed();
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 40)
    public static void saveAndReloadKeepsWorker(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Saver");
        Villager villager = worker(helper, owner, "Saved", 2, 1, 2);
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        data.setJob(WorkerJob.MINER);
        data.setActivity(WorkerActivity.STOPPED);
        data.setRested(true);
        ResourceLocation dim = helper.getLevel().dimension().location();
        BlockPos chest = helper.absolutePos(new BlockPos(4, 1, 4));
        data.setLumberjack(data.lumberjack()
                .withArea(Optional.of(new WorkArea(dim, helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(3, 2, 3)))))
                .withFlags(true, true)
                .withChest(LumberjackConfig.ChestSlot.LOG, Optional.of(chest)));
        CompoundTag tag = new CompoundTag();
        villager.saveWithoutId(tag);
        Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
        loaded.load(tag);
        WorkerData again = WorkerAttachments.get(loaded).orElse(null);
        helper.assertTrue(again != null, "reload dropped the attachment");
        helper.assertTrue(owner.getUUID().equals(again.owner()), "owner");
        helper.assertTrue(again.job() == WorkerJob.MINER, "job");
        helper.assertTrue(again.activity() == WorkerActivity.STOPPED, "activity");
        helper.assertTrue(again.rested(), "rested");
        helper.assertTrue(again.lumberjack().clearLeaves() && again.lumberjack().replantSaplings(), "flags");
        helper.assertTrue(again.lumberjack().logChest().orElse(BlockPos.ZERO).equals(chest), "chest");
        Villager plain = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertFalse(WorkerAttachments.isWorker(plain), "unhired villager gained an attachment");
        helper.succeed();
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 80)
    public static void chatCommandsMatchNames(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Caller");
        RecordingPlayer stranger = join(helper, "Passer");
        Villager oak = worker(helper, owner, "Oak", 2, 1, 2);
        Villager oakLog = worker(helper, owner, "Oak Log", 4, 1, 2);
        worker(helper, owner, "Twin", 6, 1, 2);
        worker(helper, owner, "Twin", 8, 1, 2);
        stand(helper, owner, 3, 1, 4);
        stand(helper, stranger, 3, 1, 3);
        chat(owner, "hello @Oak come");
        helper.assertTrue(WorkerAttachments.get(oak).orElseThrow().activity() == WorkerActivity.IDLE, "chat that does not start with @ ran");
        chat(owner, "@Oak Log come");
        helper.assertTrue(WorkerAttachments.get(oakLog).orElseThrow().activity() == WorkerActivity.COMING, "spaced name");
        helper.assertTrue(WorkerAttachments.get(oak).orElseThrow().activity() == WorkerActivity.IDLE, "shorter name stole a longer command");
        chat(owner, "@Oak stop");
        helper.assertTrue(WorkerAttachments.get(oak).orElseThrow().activity() == WorkerActivity.STOPPED, "stop");
        chat(owner, "@Oak resume");
        helper.assertTrue(WorkerAttachments.get(oak).orElseThrow().activity() == WorkerActivity.IDLE, "resume with no job");
        WorkerAttachments.get(oak).orElseThrow().setJob(WorkerJob.LUMBERJACK);
        chat(owner, "@Oak resume");
        helper.assertTrue(WorkerAttachments.get(oak).orElseThrow().activity() == WorkerActivity.WORKING, "resume");
        chat(owner, "@Oak status");
        helper.assertTrue(owner.saw("lumberjack"), "status reply");
        chat(owner, "@Twin status");
        helper.assertTrue(owner.saw("More than one worker is named Twin"), "duplicate names");
        chat(stranger, "@Oak stop");
        helper.assertTrue(WorkerAttachments.get(oak).orElseThrow().activity() == WorkerActivity.WORKING, "stranger changed activity");
        helper.assertTrue(stranger.saw("You cannot command Oak"), "stranger denial");
        chat(owner, "@Oak dance");
        helper.assertTrue(owner.saw("Unknown command"), "unknown verb");
        helper.succeed();
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 400)
    public static void lumberjackCutsReplantsAndPauses(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Forester");
        stand(helper, owner, 2, 1, 8);
        Villager villager = worker(helper, owner, "Axel", 4, 1, 5);
        ServerLevel level = helper.getLevel();
        BlockPos inside = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos outside = helper.absolutePos(new BlockPos(8, 2, 4));
        BlockPos leaves = helper.absolutePos(new BlockPos(4, 3, 4));
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.DIRT);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(8, 1, 4), Blocks.DIRT);
        helper.setBlock(new BlockPos(8, 2, 4), Blocks.OAK_LOG);
        BlockPos logChest = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos saplingChest = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos toolChest = helper.absolutePos(new BlockPos(1, 1, 3));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.CHEST);
        container(helper, new BlockPos(1, 1, 2)).setItem(0, new ItemStack(Items.OAK_SAPLING, 4));
        container(helper, new BlockPos(1, 1, 3)).setItem(0, new ItemStack(Items.IRON_AXE));
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        data.setJob(WorkerJob.LUMBERJACK);
        data.setRested(true);
        ResourceLocation dim = level.dimension().location();
        data.setLumberjack(data.lumberjack()
                .withArea(Optional.of(new WorkArea(dim, helper.absolutePos(new BlockPos(4, 1, 4)), helper.absolutePos(new BlockPos(5, 3, 5)))))
                .withFlags(true, true)
                .withChest(LumberjackConfig.ChestSlot.LOG, Optional.of(logChest))
                .withChest(LumberjackConfig.ChestSlot.SAPLING, Optional.of(saplingChest))
                .withChest(LumberjackConfig.ChestSlot.TOOL, Optional.of(toolChest)));
        data.setActivity(WorkerActivity.WORKING);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(Blocks.OAK_SAPLING, new BlockPos(4, 2, 4));
                    helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(4, 3, 4));
                })
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(8, 2, 4));
                    helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(4, 3, 4));
                    helper.assertContainerContains(new BlockPos(1, 1, 1), Items.OAK_LOG);
                    helper.assertTrue(level.getBlockState(outside).is(Blocks.OAK_LOG), "outside log");
                    helper.assertTrue(level.getBlockState(inside).is(Blocks.OAK_SAPLING), "replant");
                    helper.assertTrue(level.getBlockState(leaves).isAir(), "leaves");
                    fill(helper, new BlockPos(1, 1, 1), new ItemStack(Items.COBBLESTONE, 64));
                    helper.setBlock(new BlockPos(5, 1, 4), Blocks.DIRT);
                    helper.setBlock(new BlockPos(5, 2, 4), Blocks.OAK_LOG);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        data.activity() == WorkerActivity.STOPPED && data.problem().code() == ProblemCode.CHEST_FULL,
                        "full chest did not pause"))
                .thenExecute(() -> {
                    helper.assertTrue(owner.saw("full chest"), "full chest message");
                    helper.assertTrue(!helper.getLevel().getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            villager.getBoundingBox().inflate(4),
                            item -> item.getItem().is(Items.OAK_LOG)).isEmpty()
                            || count(helper, new BlockPos(1, 1, 1), Items.OAK_LOG) > 0,
                            "drops were deleted");
                    container(helper, new BlockPos(1, 1, 3)).clearContent();
                    ItemStack worn = new ItemStack(Items.IRON_AXE);
                    worn.setDamageValue(worn.getMaxDamage() - 1);
                    container(helper, new BlockPos(1, 1, 3)).setItem(0, worn);
                    data.setActivity(WorkerActivity.WORKING);
                    data.setProblem(com.vucelich.villagerworkers.common.WorkerProblem.NONE);
                })
                .thenWaitUntil(() -> helper.assertTrue(data.activity() == WorkerActivity.STOPPED
                        && data.problem().code() == ProblemCode.BROKEN_TOOL, "worn axe did not pause"))
                .thenExecute(() -> {
                    helper.assertTrue(owner.saw("needs a new tool"), "tool message");
                    helper.succeed();
                });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 400)
    public static void minerRespectsVolumeAndProtectedBlocks(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Miner");
        stand(helper, owner, 2, 1, 10);
        Villager villager = worker(helper, owner, "Digger", 6, 1, 2);
        helper.setBlock(new BlockPos(8, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(9, 1, 1), Blocks.DIAMOND_ORE);
        helper.setBlock(new BlockPos(10, 1, 1), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(8, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(8, 1, 3), Blocks.RAIL);
        List<BlockPos> protectedPos = new ArrayList<>();
        protectedPos.add(helper.absolutePos(new BlockPos(10, 1, 1)));
        protectedPos.add(helper.absolutePos(new BlockPos(8, 1, 2)));
        protectedPos.add(helper.absolutePos(new BlockPos(8, 1, 3)));
        int[][] slots = {
            {11, 1}, {12, 1}, {13, 1}, {14, 1},
            {10, 2}, {11, 2}, {12, 2}, {13, 2}, {14, 2},
            {9, 4}, {10, 4}, {11, 4}, {12, 4}
        };
        int slot = 0;
        java.util.List<String> placed = new java.util.ArrayList<>();
        for (String id : PACK_BLOCKS) {
            Block block = blockOrNull(id);
            if (block == null || slot >= slots.length) {
                continue;
            }
            int x = slots[slot][0];
            int z = slots[slot][1];
            helper.setBlock(new BlockPos(x, 1, z), block.defaultBlockState());
            protectedPos.add(helper.absolutePos(new BlockPos(x, 1, z)));
            placed.add(id);
            slot++;
        }
        VillagerWorkersMod.LOGGER.info("Protected pack blocks placed: {}", placed);
        BlockPos regular = helper.absolutePos(new BlockPos(1, 1, 6));
        BlockPos valuables = helper.absolutePos(new BlockPos(1, 1, 7));
        BlockPos tools = helper.absolutePos(new BlockPos(1, 1, 8));
        helper.setBlock(new BlockPos(1, 1, 6), Blocks.BARREL);
        helper.setBlock(new BlockPos(1, 1, 7), Blocks.BARREL);
        helper.setBlock(new BlockPos(1, 1, 8), Blocks.BARREL);
        container(helper, new BlockPos(1, 1, 8)).setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        data.setJob(WorkerJob.MINER);
        data.setRested(true);
        ResourceLocation dim = helper.getLevel().dimension().location();
        data.setMiner(data.miner()
                .withMode(com.vucelich.villagerworkers.common.MinerMode.CLEAR_BOX)
                .withArea(Optional.of(new WorkArea(dim, helper.absolutePos(new BlockPos(8, 1, 1)), helper.absolutePos(new BlockPos(14, 1, 4)))))
                .withChest(MinerConfig.ChestSlot.REGULAR, Optional.of(regular))
                .withChest(MinerConfig.ChestSlot.VALUABLES, Optional.of(valuables))
                .withChest(MinerConfig.ChestSlot.TOOL, Optional.of(tools)));
        BlockPos denied = helper.absolutePos(new BlockPos(9, 1, 2));
        helper.setBlock(new BlockPos(9, 1, 2), Blocks.STONE);
        CANCEL_BREAKS.add(denied);
        data.setActivity(WorkerActivity.WORKING);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(8, 1, 1));
                    helper.assertBlockNotPresent(Blocks.DIAMOND_ORE, new BlockPos(9, 1, 1));
                    helper.assertContainerContains(new BlockPos(1, 1, 7), Items.DIAMOND);
                })
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(10, 1, 1));
                    helper.assertBlockPresent(Blocks.CHEST, new BlockPos(8, 1, 2));
                    helper.assertBlockPresent(Blocks.RAIL, new BlockPos(8, 1, 3));
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(9, 1, 2));
                    for (BlockPos pos : protectedPos) {
                        BlockState state = helper.getLevel().getBlockState(pos);
                        helper.assertFalse(state.isAir(), "protected block was broken: " + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                    }
                    helper.assertContainerContains(new BlockPos(1, 1, 6), Items.COBBLESTONE);
                    CANCEL_BREAKS.remove(denied);
                    helper.succeed();
                });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 300)
    public static void tunnelBranchAndShaftDig(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Tunneler");
        stand(helper, owner, 1, 1, 1);
        Villager villager = worker(helper, owner, "Borer", 2, 1, 3);
        for (int x = 3; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.DIRT);
            helper.setBlock(new BlockPos(x, 2, 2), Blocks.DIRT);
        }
        BlockPos tools = helper.absolutePos(new BlockPos(1, 1, 12));
        BlockPos regular = helper.absolutePos(new BlockPos(1, 1, 13));
        BlockPos valuables = helper.absolutePos(new BlockPos(1, 1, 14));
        helper.setBlock(new BlockPos(1, 1, 12), Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 1, 13), Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 1, 14), Blocks.CHEST);
        container(helper, new BlockPos(1, 1, 12)).setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        data.setJob(WorkerJob.MINER);
        data.setRested(true);
        ResourceLocation dim = helper.getLevel().dimension().location();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 2));
        data.setMiner(data.miner()
                .withMode(com.vucelich.villagerworkers.common.MinerMode.TUNNEL)
                .withOrigin(Optional.of(new WorkOrigin(dim, origin, "east")))
                .withNumbers(com.vucelich.villagerworkers.common.MinerMode.TUNNEL, 1, 2, 3, "", 4, 2, 2, 2, false, data.miner().valuables())
                .withChest(MinerConfig.ChestSlot.TOOL, Optional.of(tools))
                .withChest(MinerConfig.ChestSlot.REGULAR, Optional.of(regular))
                .withChest(MinerConfig.ChestSlot.VALUABLES, Optional.of(valuables)));
        data.setActivity(WorkerActivity.WORKING);
        BlockPos tunnelCell = helper.absolutePos(new BlockPos(5, 1, 2));
        helper.assertTrue(com.vucelich.villagerworkers.common.WorkVolumes.isDig(data, dim, tunnelCell), "tunnel cell is outside the dig volume");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockNotPresent(Blocks.DIRT, new BlockPos(5, 1, 2));
                    helper.assertBlockNotPresent(Blocks.DIRT, new BlockPos(3, 2, 2));
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 1200)
    public static void restedWorkerBreaksFaster(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Pacer");
        stand(helper, owner, 2, 1, 2);
        long[] ticks = new long[2];
        long[] started = new long[1];
        Villager villager = worker(helper, owner, "Pace", 5, 1, 4);
        prepareObsidian(helper, villager, owner, false, 6, 1, 4);
        started[0] = helper.getLevel().getGameTime();
        WorkerAttachments.get(villager).orElseThrow().setActivity(WorkerActivity.WORKING);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockNotPresent(Blocks.OBSIDIAN, new BlockPos(6, 1, 4)))
                .thenExecute(() -> {
                    ticks[0] = helper.getLevel().getGameTime() - started[0];
                    villager.discard();
                    Villager rested = worker(helper, owner, "PaceFast", 4, 1, 6);
                    prepareObsidian(helper, rested, owner, true, 8, 1, 6);
                    started[0] = helper.getLevel().getGameTime();
                    WorkerAttachments.get(rested).orElseThrow().setActivity(WorkerActivity.WORKING);
                })
                .thenWaitUntil(() -> helper.assertBlockNotPresent(Blocks.OBSIDIAN, new BlockPos(8, 1, 6)))
                .thenExecute(() -> {
                    ticks[1] = helper.getLevel().getGameTime() - started[0];
                    helper.assertTrue(ticks[1] < ticks[0], "rested ticks " + ticks[1] + " were not below unrested " + ticks[0]);
                    VillagerWorkersMod.LOGGER.info("Break ticks unrested={} rested={}", ticks[0], ticks[1]);
                    helper.succeed();
                });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 80)
    public static void problemsAreRateLimited(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Nurse");
        stand(helper, owner, 2, 1, 3);
        Villager villager = worker(helper, owner, "Hurt", 2, 1, 2);
        villager.setHealth(1.0F);
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    int first = owner.count("is hurt");
                    helper.assertTrue(first == 1, "expected one hurt line, saw " + first);
                })
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(owner.count("is hurt") == 1, "hurt line repeated inside a minute");
                    helper.succeed();
                });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 80)
    public static void offlineProblemArrivesOnLogin(GameTestHelper helper) {
        UUID ownerId = UUID.randomUUID();
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setCustomName(Component.literal("Waiter"));
        WorkerData data = WorkerData.hire(ownerId);
        WorkerAttachments.hire(villager, data);
        data.setJob(WorkerJob.MINER);
        OwnerNotices.report(villager, data, ProblemCode.CHEST_FULL, "");
        helper.assertTrue(data.notice(ProblemCode.CHEST_FULL) != null && data.notice(ProblemCode.CHEST_FULL).pending(), "offline notice was not kept");
        RecordingPlayer owner = join(helper, "Returner", ownerId);
        stand(helper, owner, 2, 1, 3);
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(owner.saw("full chest"), "pending line was not delivered on login");
                    helper.assertTrue(owner.count("full chest") == 1, "login delivered a backlog");
                    helper.succeed();
                });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 400)
    public static void builderPlacesFromChestsAndDoesNotBreak(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Builder");
        stand(helper, owner, 2, 1, 8);
        Villager villager = worker(helper, owner, "Mason", 4, 1, 6);
        BlockPos min = helper.absolutePos(new BlockPos(5, 1, 6));
        BlockPos max = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.COBBLESTONE);
        java.nio.file.Path schematicDir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner.getGameProfile().getName());
        try {
            Files.createDirectories(schematicDir);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        var exported = SchematicExport.saveSchematic(schematicDir, "vwbuild.nbt", true, helper.getLevel(), min, max);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.AIR);
        ItemStack blueprint = SchematicItem.create(helper.getLevel(), exported.fileName(), owner.getGameProfile().getName());
        blueprint.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        blueprint.set(AllDataComponents.SCHEMATIC_ANCHOR, min);
        Block cannonBlock = blockOrNull("create:schematicannon");
        helper.assertTrue(cannonBlock != null, "Create schematicannon is missing");
        helper.setBlock(new BlockPos(4, 1, 8), cannonBlock.defaultBlockState());
        SchematicannonBlockEntity cannon = helper.getBlockEntity(new BlockPos(4, 1, 8));
        cannon.inventory.setStackInSlot(0, blueprint);
        helper.setBlock(new BlockPos(5, 1, 6), blockOrNull("create:shaft") == null
                ? Blocks.OBSIDIAN.defaultBlockState()
                : blockOrNull("create:shaft").defaultBlockState());
        BlockState blocked = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(5, 1, 6)));
        helper.setBlock(new BlockPos(3, 1, 8), Blocks.CHEST);
        container(helper, new BlockPos(3, 1, 8)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        data.setJob(WorkerJob.BUILDER);
        data.setRested(true);
        data.setBuilder(data.builder()
                .withSchematic(Optional.of(helper.absolutePos(new BlockPos(4, 1, 8))))
                .withMaterialChest(helper.absolutePos(new BlockPos(3, 1, 8))));
        data.setActivity(WorkerActivity.WORKING);
        int fuelBefore = cannon.remainingFuel;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(6, 1, 6)))
                .thenExecute(() -> {
                    BlockState still = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(5, 1, 6)));
                    helper.assertTrue(still.equals(blocked), "builder broke a solid block");
                    helper.assertTrue(owner.saw("blocked"), "missing BLOCKED notice");
                    helper.assertTrue(cannon.remainingFuel == fuelBefore, "builder spent cannon fuel");
                    helper.succeed();
                });
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 40)
    public static void babyAndDeathLeakNoWorker(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Parent");
        Villager villager = worker(helper, owner, "Elder", 2, 1, 2);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        Villager other = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 1, 2));
        Villager baby = villager.getBreedOffspring(helper.getLevel(), other);
        helper.assertTrue(baby != null, "no offspring");
        helper.assertFalse(WorkerAttachments.isWorker(baby), "offspring inherited worker data");
        baby.setBaby(true);
        helper.assertFalse(WorkerAttachments.isWorker(baby), "a baby villager is a worker");
        ZombieVillager zombie = villager.convertTo(EntityType.ZOMBIE_VILLAGER, false);
        helper.assertTrue(zombie != null, "conversion failed");
        helper.assertFalse(zombie.hasData(WorkerAttachments.WORKER), "death copied worker data");
        helper.succeed();
    }

    @GameTest(templateNamespace = VillagerWorkers.MOD_ID, template = "platform", timeoutTicks = 250)
    public static void severalWorkersStayInsideATick(GameTestHelper helper) {
        RecordingPlayer owner = join(helper, "Foreman");
        stand(helper, owner, 1, 1, 1);
        for (int i = 0; i < 4; i++) {
            Villager villager = worker(helper, owner, "Crew" + i, 2 + i, 1, 4);
            helper.setBlock(new BlockPos(2 + i, 1, 6), Blocks.DIRT);
            BlockPos tools = helper.absolutePos(new BlockPos(2 + i, 1, 8));
            BlockPos regular = helper.absolutePos(new BlockPos(2 + i, 1, 9));
            BlockPos valuables = helper.absolutePos(new BlockPos(2 + i, 1, 10));
            helper.setBlock(new BlockPos(2 + i, 1, 8), Blocks.CHEST);
            helper.setBlock(new BlockPos(2 + i, 1, 9), Blocks.CHEST);
            helper.setBlock(new BlockPos(2 + i, 1, 10), Blocks.CHEST);
            container(helper, new BlockPos(2 + i, 1, 8)).setItem(0, new ItemStack(Items.IRON_PICKAXE));
            WorkerData data = WorkerAttachments.get(villager).orElseThrow();
            data.setJob(WorkerJob.MINER);
            data.setRested(true);
            ResourceLocation dim = helper.getLevel().dimension().location();
            data.setMiner(data.miner()
                    .withArea(Optional.of(new WorkArea(dim,
                            helper.absolutePos(new BlockPos(2 + i, 1, 6)),
                            helper.absolutePos(new BlockPos(2 + i, 1, 6)))))
                    .withChest(MinerConfig.ChestSlot.TOOL, Optional.of(tools))
                    .withChest(MinerConfig.ChestSlot.REGULAR, Optional.of(regular))
                    .withChest(MinerConfig.ChestSlot.VALUABLES, Optional.of(valuables)));
            data.setActivity(WorkerActivity.WORKING);
        }
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    long nanos = helper.getLevel().getServer().getAverageTickTimeNanos();
                    double millis = nanos / 1_000_000.0;
                    VillagerWorkersMod.LOGGER.info("Average server tick with four workers: {} ms", millis);
                    helper.assertTrue(millis < 50.0, "tick time " + millis + " ms");
                    helper.succeed();
                });
    }

    private static void prepareObsidian(GameTestHelper helper, Villager villager, ServerPlayer owner, boolean rested, int x, int y, int z) {
        helper.setBlock(new BlockPos(x, y, z), Blocks.OBSIDIAN);
        BlockPos tools = helper.absolutePos(new BlockPos(x, 1, 12));
        BlockPos regular = helper.absolutePos(new BlockPos(x, 1, 13));
        BlockPos valuables = helper.absolutePos(new BlockPos(x, 1, 14));
        helper.setBlock(new BlockPos(x, 1, 12), Blocks.CHEST);
        helper.setBlock(new BlockPos(x, 1, 13), Blocks.CHEST);
        helper.setBlock(new BlockPos(x, 1, 14), Blocks.CHEST);
        container(helper, new BlockPos(x, 1, 12)).setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        data.setJob(WorkerJob.MINER);
        data.setRested(rested);
        ResourceLocation dim = helper.getLevel().dimension().location();
        data.setMiner(data.miner()
                .withArea(Optional.of(new WorkArea(dim, helper.absolutePos(new BlockPos(x, y, z)), helper.absolutePos(new BlockPos(x, y, z)))))
                .withChest(MinerConfig.ChestSlot.TOOL, Optional.of(tools))
                .withChest(MinerConfig.ChestSlot.REGULAR, Optional.of(regular))
                .withChest(MinerConfig.ChestSlot.VALUABLES, Optional.of(valuables)));
    }

    private static Villager worker(GameTestHelper helper, ServerPlayer owner, String name, int x, int y, int z) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(x, y, z));
        villager.setNoGravity(true);
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        WorkerAttachments.hire(villager, WorkerData.hire(owner.getUUID()));
        return villager;
    }

    private static void openMenu(ServerPlayer player, Villager villager) {
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        WorkerView view = WorkerView.capture(villager, data, player);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new WorkerMenu(id, inventory, villager, view),
                Component.literal(view.name())));
    }

    private static void chat(ServerPlayer player, String text) {
        NeoForge.EVENT_BUS.post(new ServerChatEvent(player, text, Component.literal(text)));
    }

    private static void nameTag(ServerPlayer player, String name) {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        player.setItemSlot(EquipmentSlot.MAINHAND, tag);
    }

    private static BaseContainerBlockEntity container(GameTestHelper helper, BlockPos relative) {
        return (BaseContainerBlockEntity) helper.getBlockEntity(relative);
    }

    private static void fill(GameTestHelper helper, BlockPos relative, ItemStack stack) {
        BaseContainerBlockEntity container = container(helper, relative);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, stack.copy());
        }
    }

    private static int count(GameTestHelper helper, BlockPos relative, net.minecraft.world.item.Item item) {
        BaseContainerBlockEntity container = container(helper, relative);
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static Block blockOrNull(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(key);
        return block.defaultBlockState().isAir() ? null : block;
    }

    private static void stand(GameTestHelper helper, ServerPlayer player, int x, int y, int z) {
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, y, z + 0.5));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
    }

    @SuppressWarnings("unchecked")
    private static void expirePending(ServerPlayer player, UUID villagerId) {
        try {
            Class<?> pending = Class.forName("com.vucelich.villagerworkers.neoforge.WorkerServer$PendingHire");
            var constructor = pending.getDeclaredConstructor(UUID.class, long.class);
            constructor.setAccessible(true);
            Object entry = constructor.newInstance(villagerId, System.currentTimeMillis() - 1_000L);
            var field = WorkerServer.class.getDeclaredField("PENDING_HIRES");
            field.setAccessible(true);
            ((java.util.Map<UUID, Object>) field.get(null)).put(player.getUUID(), entry);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RecordingPlayer join(GameTestHelper helper, String name) {
        return join(helper, name, UUID.randomUUID());
    }

    private static RecordingPlayer join(GameTestHelper helper, String name, UUID id) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(id, name), false);
        RecordingPlayer player = new RecordingPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static final class RecordingPlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private RecordingPlayer(net.minecraft.server.MinecraftServer server, ServerLevel level, com.mojang.authlib.GameProfile profile, ClientInformation info) {
            super(server, level, profile, info);
        }

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
            super.sendSystemMessage(message);
        }

        private boolean saw(String fragment) {
            for (String message : messages) {
                if (message.contains(fragment)) {
                    return true;
                }
            }
            return false;
        }

        private int count(String fragment) {
            int total = 0;
            for (String message : messages) {
                if (message.contains(fragment)) {
                    total++;
                }
            }
            return total;
        }
    }
}
