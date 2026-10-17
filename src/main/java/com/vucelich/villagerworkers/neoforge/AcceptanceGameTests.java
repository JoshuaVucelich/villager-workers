package com.vucelich.villagerworkers.neoforge;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.foundation.utility.CreatePaths;
import com.vucelich.villagerworkers.common.LumberjackConfig;
import com.vucelich.villagerworkers.common.MinerConfig;
import com.vucelich.villagerworkers.common.MinerMode;
import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.TrustedPlayer;
import com.vucelich.villagerworkers.common.VillagerWorkers;
import com.vucelich.villagerworkers.common.WorkArea;
import com.vucelich.villagerworkers.common.WorkNotice;
import com.vucelich.villagerworkers.common.WorkOrigin;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerJob;
import com.vucelich.villagerworkers.neoforge.MenuIds;
import com.vucelich.villagerworkers.neoforge.ModItems;
import com.vucelich.villagerworkers.neoforge.ModPayloads.AddTrustedPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.RemoveTrustedPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.TransferOwnerPayload;
import com.vucelich.villagerworkers.neoforge.ModPayloads.UpdateLumberjackPayload;
import com.vucelich.villagerworkers.neoforge.OwnerNotices;
import com.vucelich.villagerworkers.neoforge.SetupSessions;
import com.vucelich.villagerworkers.neoforge.WorkerAttachments;
import com.vucelich.villagerworkers.neoforge.WorkerMenu;
import com.vucelich.villagerworkers.neoforge.WorkerServer;
import com.vucelich.villagerworkers.neoforge.WorkerTicker;
import com.vucelich.villagerworkers.neoforge.WorkerView;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Milestone checks that need a running world. Each method is one acceptance case.
 */
@GameTestHolder(VillagerWorkers.MOD_ID)
@PrefixGameTestTemplate(false)
public class AcceptanceGameTests {
    private static final AtomicInteger NAMES = new AtomicInteger();
    /** Present only when that addon is on the game test classpath. Missing ids are skipped. */
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

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void nameTagHireNamedThenWorker(GameTestHelper helper) {
        RecordingPlayer player = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Temp");
        stand(player, villager);
        nameTag(player, villager, "Birch");
        helper.runAfterDelay(2, () -> {
            WorkerServer.chooseHire(player, villager.getUUID(), false);
            helper.assertFalse(WorkerAttachments.isWorker(villager), "Named writes no attachment");
            nameTag(player, villager, "Birch");
            helper.runAfterDelay(2, () -> {
                WorkerServer.chooseHire(player, villager.getUUID(), true);
                WorkerData data = WorkerAttachments.get(villager).orElse(null);
                helper.assertTrue(data != null, "hired");
                helper.assertTrue(player.getUUID().equals(data.owner()), "owner");
                helper.assertTrue(data.job() == WorkerJob.NONE, "job");
                helper.assertTrue(data.activity() == WorkerActivity.IDLE, "activity");
                helper.assertTrue(data.trusted().isEmpty(), "trusted");
                helper.assertFalse(data.rested(), "rested");
                helper.assertTrue("Birch".equals(villager.getCustomName().getString()), "name");
                done(helper, player);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void babyNameTagDoesNotHire(GameTestHelper helper) {
        RecordingPlayer player = player(helper);
        Villager baby = villager(helper, 2, 1, 2, "Kid");
        baby.setBaby(true);
        stand(player, baby);
        nameTag(player, baby, "Kid");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue("Kid".equals(baby.getCustomName().getString()), "renamed");
            helper.assertFalse(WorkerAttachments.isWorker(baby), "baby is not a worker");
            player.messages.clear();
            WorkerServer.chooseHire(player, baby.getUUID(), true);
            helper.assertFalse(WorkerAttachments.isWorker(baby), "forged hire");
            helper.assertTrue(said(player, "expired"), player.messages.toString());
            done(helper, player);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void renamingAWorkerKeepsTheOwner(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        RecordingPlayer other = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        WorkerData data = hire(villager, owner);
        stand(other, villager);
        nameTag(other, villager, "Other");
        helper.runAfterDelay(2, () -> {
            other.messages.clear();
            WorkerServer.chooseHire(other, villager.getUUID(), true);
            helper.assertTrue(owner.getUUID().equals(data.owner()), "owner unchanged");
            helper.assertTrue("Other".equals(villager.getCustomName().getString()), "renamed");
            helper.assertTrue(said(other, "expired") || said(other, "Already"), other.messages.toString());
            done(helper, owner, other);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void forgedHirePacketsAreRejected(GameTestHelper helper) {
        RecordingPlayer player = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        Villager other = villager(helper, 4, 1, 2, "Other");
        stand(player, villager);
        player.messages.clear();
        WorkerServer.chooseHire(player, villager.getUUID(), true);
        helper.assertFalse(WorkerAttachments.isWorker(villager), "no pending hire");
        helper.assertTrue(said(player, "expired"), player.messages.toString());

        nameTag(player, villager, "Birch");
        helper.runAfterDelay(2, () -> {
            player.messages.clear();
            WorkerServer.chooseHire(player, other.getUUID(), true);
            helper.assertFalse(WorkerAttachments.isWorker(villager), "wrong villager");
            helper.assertFalse(WorkerAttachments.isWorker(other), "wrong villager hired");
            player.setPos(player.getX() + 30, player.getY(), player.getZ());
            player.messages.clear();
            WorkerServer.chooseHire(player, villager.getUUID(), true);
            helper.assertFalse(WorkerAttachments.isWorker(villager), "too far");
            helper.assertTrue(said(player, "expired"), player.messages.toString());
            done(helper, player);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void expiredHireOfferIsRejected(GameTestHelper helper) {
        RecordingPlayer player = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        stand(player, villager);
        nameTag(player, villager, "Birch");
        helper.runAfterDelay(2, () -> {
            agePendingHire(player);
            player.messages.clear();
            WorkerServer.chooseHire(player, villager.getUUID(), true);
            helper.assertFalse(WorkerAttachments.isWorker(villager), "expired hire");
            helper.assertTrue(said(player, "expired"), player.messages.toString());
            done(helper, player);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void permissionsAndForgedMutations(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        RecordingPlayer friend = player(helper);
        RecordingPlayer stranger = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        WorkerData data = hire(villager, owner);
        stand(owner, villager);
        stand(friend, villager);
        stand(stranger, villager);

        stranger.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertFalse(stranger.containerMenu instanceof WorkerMenu, "stranger screen");
        owner.setShiftKeyDown(true);
        owner.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertFalse(owner.containerMenu instanceof WorkerMenu, "sneak keeps vanilla");
        owner.setShiftKeyDown(false);
        owner.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.containerMenu instanceof WorkerMenu, "owner screen");

        stranger.messages.clear();
        WorkerServer.updateLumberjack(stranger, new UpdateLumberjackPayload(villager.getUUID(), true, true));
        helper.assertFalse(data.lumberjack().clearLeaves(), "forged setup");
        helper.assertTrue(said(stranger, "You cannot do that."), stranger.messages.toString());

        owner.messages.clear();
        WorkerServer.addTrusted(owner, new AddTrustedPayload(villager.getUUID(), "NobodyHere"));
        helper.assertTrue(data.trusted().isEmpty(), "offline name");
        helper.assertTrue(said(owner, "not online"), owner.messages.toString());

        WorkerServer.addTrusted(owner, new AddTrustedPayload(villager.getUUID(), friend.getGameProfile().getName()));
        helper.assertTrue(data.trustedIds().contains(friend.getUUID()), "trusted added");
        friend.interactOn(villager, InteractionHand.MAIN_HAND);
        helper.assertTrue(friend.containerMenu instanceof WorkerMenu, "trusted screen");

        friend.messages.clear();
        WorkerServer.removeTrusted(friend, new RemoveTrustedPayload(villager.getUUID(), friend.getUUID()));
        helper.assertTrue(data.trustedIds().contains(friend.getUUID()), "trusted cannot edit the list");
        WorkerServer.transfer(friend, new TransferOwnerPayload(villager.getUUID(), stranger.getUUID()));
        helper.assertTrue(owner.getUUID().equals(data.owner()), "trusted cannot transfer");
        friend.containerMenu.clickMenuButton(friend, MenuIds.SHOW_PERMISSIONS);
        helper.assertTrue(said(friend, "You cannot do that."), friend.messages.toString());

        WorkerServer.transfer(owner, new TransferOwnerPayload(villager.getUUID(), UUID.randomUUID()));
        helper.assertTrue(owner.getUUID().equals(data.owner()), "offline transfer");
        WorkerServer.transfer(owner, new TransferOwnerPayload(villager.getUUID(), friend.getUUID()));
        helper.assertTrue(friend.getUUID().equals(data.owner()), "new owner");
        helper.assertFalse(data.trustedIds().contains(owner.getUUID()), "previous owner is not trusted");
        openWorker(owner, villager);
        owner.messages.clear();
        WorkerServer.transfer(owner, new TransferOwnerPayload(villager.getUUID(), stranger.getUUID()));
        helper.assertTrue(friend.getUUID().equals(data.owner()), "old owner cannot transfer");

        openWorker(friend, villager);
        WorkerServer.removeTrusted(friend, new RemoveTrustedPayload(villager.getUUID(), owner.getUUID()));
        data.addTrusted(new TrustedPlayer(owner.getUUID(), owner.getGameProfile().getName()));
        WorkerServer.removeTrusted(friend, new RemoveTrustedPayload(villager.getUUID(), owner.getUUID()));
        helper.assertFalse(data.trustedIds().contains(owner.getUUID()), "owner can remove an offline trusted player");
        done(helper, owner, friend, stranger);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void menuButtonsSaveJobAndRejectDistantPackets(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        WorkerData data = hire(villager, owner);
        ServerLevel level = helper.getLevel();
        BlockPos mark = helper.absolutePos(new BlockPos(3, 1, 2));
        data.setLumberjack(data.lumberjack().withArea(Optional.of(area(level, mark, mark))));
        stand(owner, villager);
        openWorker(owner, villager);
        owner.containerMenu.clickMenuButton(owner, MenuIds.JOB_LUMBERJACK);
        owner.containerMenu.clickMenuButton(owner, MenuIds.START);
        helper.assertTrue(data.activity() == WorkerActivity.WORKING, "start");
        owner.containerMenu.clickMenuButton(owner, MenuIds.STOP);
        helper.assertTrue(data.activity() == WorkerActivity.STOPPED, "stop");
        owner.containerMenu.clickMenuButton(owner, MenuIds.COME);
        helper.assertTrue(data.activity() == WorkerActivity.COMING, "come");
        owner.containerMenu.clickMenuButton(owner, MenuIds.JOB_MINER);
        owner.containerMenu.clickMenuButton(owner, MenuIds.JOB_LUMBERJACK);
        helper.assertTrue(data.lumberjack().area().isPresent(), "setup survives a job switch");
        owner.containerMenu.clickMenuButton(owner, MenuIds.JOB_NONE);
        owner.containerMenu.clickMenuButton(owner, MenuIds.START);
        helper.assertTrue(data.activity() == WorkerActivity.IDLE, "start with no job");

        owner.setPos(owner.getX() + 20, owner.getY(), owner.getZ());
        owner.messages.clear();
        boolean changed = owner.containerMenu.clickMenuButton(owner, MenuIds.JOB_MINER);
        helper.assertFalse(changed, "distant button");
        helper.assertTrue(data.job() == WorkerJob.NONE, "job unchanged");
        helper.assertTrue(said(owner, "You cannot do that."), owner.messages.toString());
        done(helper, owner);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void wandAndChestLinks(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        WorkerData data = hire(villager, owner);
        data.setJob(WorkerJob.LUMBERJACK);
        stand(owner, villager);
        openWorker(owner, villager);
        ServerLevel level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos second = helper.absolutePos(new BlockPos(4, 1, 3));
        level.setBlockAndUpdate(first, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(second, Blocks.STONE.defaultBlockState());
        owner.containerMenu.clickMenuButton(owner, MenuIds.MARK);
        ItemStack wand = new ItemStack(ModItems.AREA_WAND.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, wand);
        helper.assertTrue(useWand(owner, first).consumesAction(), "first corner");
        helper.assertTrue(useWand(owner, second).consumesAction(), "second corner");
        helper.assertTrue(data.lumberjack().area().isPresent(), "area saved");
        helper.assertTrue(level.getBlockState(first).is(Blocks.STONE), "wand does not break");
        helper.assertTrue(level.getBlockState(second).is(Blocks.STONE), "wand does not place");
        helper.assertTrue(owner.getMainHandItem().getCount() == 1, "wand is kept");

        BlockPos chest = barrel(helper, 3, 1, 1);
        owner.containerMenu.clickMenuButton(owner, MenuIds.LINK_LOG);
        helper.assertTrue(SetupSessions.useLink(owner, chest), "link chest");
        helper.assertTrue(data.lumberjack().logChest().orElse(null) != null && data.lumberjack().logChest().get().equals(chest), "log chest");

        Block casing = createBlock("create:andesite_casing");
        BlockPos machine = helper.absolutePos(new BlockPos(4, 1, 1));
        level.setBlockAndUpdate(machine, casing.defaultBlockState());
        owner.containerMenu.clickMenuButton(owner, MenuIds.LINK_SAPLING);
        SetupSessions.useLink(owner, machine);
        helper.assertTrue(data.lumberjack().saplingChest().isEmpty(), "create block is not a chest");

        BlockPos far = helper.absolutePos(new BlockPos(2, 1, 2)).offset(0, 0, 12);
        level.setBlockAndUpdate(far, Blocks.BARREL.defaultBlockState());
        owner.containerMenu.clickMenuButton(owner, MenuIds.LINK_SAPLING);
        SetupSessions.useLink(owner, far);
        helper.assertTrue(data.lumberjack().saplingChest().isEmpty(), "chest past 8 blocks");
        done(helper, owner);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void savedWorkerReloadsAndUnhiredDoesNot(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        RecordingPlayer friend = player(helper);
        Villager hired = villager(helper, 2, 1, 2, "Birch");
        Villager plain = villager(helper, 4, 1, 2, "Plain");
        WorkerData data = hire(hired, owner);
        data.addTrusted(new TrustedPlayer(friend.getUUID(), friend.getGameProfile().getName()));
        data.setJob(WorkerJob.MINER);
        data.setActivity(WorkerActivity.STOPPED);
        data.setRested(true);
        ServerLevel level = helper.getLevel();
        BlockPos bed = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState());
        data.setHomeBed(GlobalPos.of(level.dimension(), bed));
        List<net.minecraft.resources.ResourceLocation> valuables = List.of(ResourceLocation.parse("minecraft:diamond"));
        data.setMiner(data.miner().withNumbers(
                MinerMode.TUNNEL, 1, 2, 4, "", 4, 2, 2, 3, false, valuables));

        Villager loaded = reload(level, hired);
        WorkerData again = WorkerAttachments.get(loaded).orElse(null);
        helper.assertTrue(again != null, "attachment reloaded");
        helper.assertTrue(owner.getUUID().equals(again.owner()), "owner");
        helper.assertTrue(again.trustedIds().contains(friend.getUUID()), "trusted");
        helper.assertTrue(again.job() == WorkerJob.MINER, "job");
        helper.assertTrue(again.activity() == WorkerActivity.STOPPED, "activity");
        helper.assertTrue(again.rested(), "rested");
        helper.assertTrue(again.homeBed() != null && again.homeBed().pos().equals(bed), "bed");
        helper.assertTrue(again.miner().mode() == MinerMode.TUNNEL, "miner mode");
        helper.assertTrue(again.miner().valuables().equals(valuables), "valuables");

        Villager plainLoaded = reload(level, plain);
        helper.assertFalse(WorkerAttachments.isWorker(plainLoaded), "unhired stays unhired");
        done(helper, owner, friend);
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void deathDropsTheToolAndNoWorkerData(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        hire(villager, owner);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        BlockPos where = villager.blockPosition();
        villager.kill();
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.isRemoved(), "removed after the death animation");
            boolean axe = false;
            for (ItemEntity item : itemsNear(helper.getLevel(), where, 8)) {
                CompoundTag tag = item.saveWithoutId(new CompoundTag());
                helper.assertFalse(tag.toString().contains("villager_workers:worker"), "drop has worker data");
                if (item.getItem().is(Items.DIAMOND_AXE)) {
                    axe = true;
                }
            }
            helper.assertTrue(axe, "axe dropped");
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 20)
    public static void babyOfWorkersIsNotAWorker(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager left = villager(helper, 2, 1, 2, "Left");
        Villager right = villager(helper, 3, 1, 2, "Right");
        hire(left, owner);
        hire(right, owner);
        Villager child = left.getBreedOffspring(helper.getLevel(), right);
        helper.assertFalse(WorkerAttachments.isWorker(child), "baby has no attachment");
        helper.assertFalse(child.getUUID().equals(left.getUUID()), "new entity");
        done(helper, owner);
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void chatCommandsNamesAndDistance(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        RecordingPlayer stranger = player(helper);
        Villager bob = villager(helper, 2, 1, 2, "Bob");
        Villager longer = villager(helper, 3, 1, 2, "Bob the Builder");
        WorkerData bobData = hire(bob, owner);
        WorkerData longData = hire(longer, owner);
        bobData.setJob(WorkerJob.LUMBERJACK);
        longData.setJob(WorkerJob.LUMBERJACK);
        bobData.setActivity(WorkerActivity.WORKING);
        longData.setActivity(WorkerActivity.WORKING);
        stand(owner, bob);
        stand(stranger, bob);

        ServerChatEvent ignored = chat(owner, "hello");
        WorkerServer.onChat(ignored);
        helper.assertFalse(ignored.isCanceled(), "ordinary chat is not cancelled");
        helper.assertTrue(bobData.activity() == WorkerActivity.WORKING, "ordinary chat");

        ServerChatEvent spaced = chat(owner, "@Bob the Builder stop");
        WorkerServer.onChat(spaced);
        helper.assertFalse(spaced.isCanceled(), "command chat is not cancelled");
        helper.assertTrue(longData.activity() == WorkerActivity.STOPPED, "longest name");
        helper.assertTrue(bobData.activity() == WorkerActivity.WORKING, "shorter name left alone");

        WorkerServer.onChat(chat(owner, "@Bob come"));
        helper.assertTrue(bobData.activity() == WorkerActivity.COMING, "short name");
        helper.assertTrue(longData.activity() == WorkerActivity.STOPPED, "long name stays");

        bobData.setActivity(WorkerActivity.WORKING);
        WorkerServer.onChat(chat(owner, "@BobtheBuilder come"));
        helper.assertTrue(bobData.activity() == WorkerActivity.WORKING, "name needs a boundary");

        owner.messages.clear();
        WorkerServer.onChat(chat(owner, "@Bob dance"));
        helper.assertTrue(said(owner, "Unknown command"), owner.messages.toString());
        helper.assertTrue(bobData.activity() == WorkerActivity.WORKING, "unknown verb");

        owner.messages.clear();
        WorkerServer.onChat(chat(owner, "@Bob status"));
        helper.assertTrue(said(owner, "lumberjack"), owner.messages.toString());
        helper.assertTrue(said(owner, "health"), owner.messages.toString());

        stranger.messages.clear();
        WorkerServer.onChat(chat(stranger, "@Bob stop"));
        helper.assertTrue(bobData.activity() == WorkerActivity.WORKING, "stranger cannot command");
        helper.assertTrue(said(stranger, "You cannot command Bob."), stranger.messages.toString());

        Villager twin = villager(helper, 4, 1, 2, "Bob");
        WorkerData twinData = hire(twin, owner);
        twinData.setActivity(WorkerActivity.WORKING);
        bobData.setActivity(WorkerActivity.WORKING);
        owner.messages.clear();
        WorkerServer.onChat(chat(owner, "@Bob stop"));
        helper.assertTrue(said(owner, "More than one worker is named Bob."), owner.messages.toString());
        helper.assertTrue(bobData.activity() == WorkerActivity.WORKING, "ambiguous");
        helper.assertTrue(twinData.activity() == WorkerActivity.WORKING, "ambiguous twin");

        ServerLevel level = helper.getLevel();
        BlockPos farPos = helper.absolutePos(new BlockPos(144, 1, 0));
        level.setChunkForced(farPos.getX() >> 4, farPos.getZ() >> 4, true);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(level.isPositionEntityTicking(farPos), "far chunk is ticking");
            Villager far = EntityType.VILLAGER.create(level);
            far.setCustomName(Component.literal("Farmer"));
            far.setPersistenceRequired();
            far.setNoAi(true);
            far.setNoGravity(true);
            far.setPos(farPos.getX() + 0.5, farPos.getY(), farPos.getZ() + 0.5);
            boolean added = level.addFreshEntity(far);
            WorkerData farData = hire(far, owner);
            farData.setActivity(WorkerActivity.WORKING);
            helper.assertTrue(added && level.getEntity(far.getUUID()) == far, "far worker is loaded");
            helper.assertTrue(far.distanceToSqr(owner) > 128.0 * 128.0, "loaded worker is farther than 128 blocks");
            WorkerServer.onChat(chat(owner, "@Farmer stop"));
            helper.assertTrue(farData.activity() == WorkerActivity.STOPPED, "chat reaches a loaded worker across the dimension");
            done(helper, owner, stranger);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 400)
    public static void lumberjackCutsReplantsAndStops(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 3, "Birch");
        WorkerData data = hire(villager, owner);
        ServerLevel level = helper.getLevel();
        BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos later = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos outside = helper.absolutePos(new BlockPos(5, 1, 2));
        BlockPos leafIn = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos leafOut = helper.absolutePos(new BlockPos(5, 2, 2));
        level.setBlockAndUpdate(inside.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(inside, Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(outside, Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(leafIn, Blocks.OAK_LEAVES.defaultBlockState());
        level.setBlockAndUpdate(leafOut, Blocks.OAK_LEAVES.defaultBlockState());
        BlockPos logs = barrel(helper, 1, 1, 2);
        BlockPos saplings = barrel(helper, 1, 1, 3, new ItemStack(Items.OAK_SAPLING, 4));
        BlockPos tools = barrel(helper, 1, 1, 4, new ItemStack(Items.DIAMOND_AXE));
        data.setJob(WorkerJob.LUMBERJACK);
        data.setActivity(WorkerActivity.WORKING);
        data.setRested(true);
        data.setLumberjack(new LumberjackConfig(
                Optional.of(area(level, inside, new BlockPos(later.getX(), leafIn.getY(), later.getZ()))),
                true,
                true,
                Optional.of(logs),
                Optional.of(saplings),
                Optional.of(tools)));
        boolean[] planted = {false};
        long[] readyAt = {-1L};
        helper.onEachTick(() -> {
            if (!planted[0] && level.getBlockState(inside).is(Blocks.OAK_SAPLING) && level.getBlockState(leafIn).isAir()) {
                planted[0] = true;
                readyAt[0] = level.getGameTime();
                level.setBlockAndUpdate(later, Blocks.OAK_LOG.defaultBlockState());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(planted[0], "sapling planted");
            helper.assertTrue(level.getGameTime() > readyAt[0] + 40L, "waited past a finished pass");
            helper.assertTrue(level.getBlockState(later).is(Blocks.OAK_LOG), "finished pass leaves a later log");
            helper.assertTrue(level.getBlockState(outside).is(Blocks.OAK_LOG), "log outside the box");
            helper.assertTrue(level.getBlockState(leafIn).isAir(), "leaf inside");
            helper.assertTrue(level.getBlockState(leafOut).is(Blocks.OAK_LEAVES), "leaf outside");
            helper.assertTrue(count(level, logs, Items.OAK_LOG) >= 1, "logs hauled");
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void missingOrBrokenAxePauses(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        Villager missing = villager(helper, 1, 1, 1, "Missing");
        Villager broken = villager(helper, 4, 1, 1, "Broken");
        WorkerData missingData = lumberReady(helper, missing, owner, new BlockPos(1, 1, 2), Items.STICK);
        WorkerData brokenData = lumberReady(helper, broken, owner, new BlockPos(4, 1, 2), null);
        ItemStack worn = new ItemStack(Items.DIAMOND_AXE);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        put(helper.getLevel(), brokenData.lumberjack().toolChest().orElseThrow(), worn);
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(1, 1, 2)), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(4, 1, 2)), Blocks.OAK_LOG.defaultBlockState());
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(missingData.activity() == WorkerActivity.STOPPED, "missing axe pauses");
            helper.assertTrue(missingData.problem().code() == ProblemCode.BROKEN_TOOL, "missing tool problem");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(1, 1, 2))).is(Blocks.OAK_LOG), "log remains");
            helper.assertTrue(brokenData.activity() == WorkerActivity.STOPPED, "worn axe pauses");
            helper.assertTrue(brokenData.problem().code() == ProblemCode.BROKEN_TOOL, "worn tool problem");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(4, 1, 2))).is(Blocks.OAK_LOG), "worn log remains");
            helper.assertTrue(said(owner, "needs a new tool"), owner.messages.toString());
            done(helper, owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void fullChestPausesAndKeepsTheDrop(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 3, "Birch");
        WorkerData data = hire(villager, owner);
        ServerLevel level = helper.getLevel();
        BlockPos log = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
        BlockPos logs = barrel(helper, 1, 1, 2);
        fill(level, logs, Items.COBBLESTONE);
        BlockPos tools = barrel(helper, 1, 1, 3, new ItemStack(Items.DIAMOND_AXE));
        BlockPos saplings = barrel(helper, 1, 1, 4);
        data.setJob(WorkerJob.LUMBERJACK);
        data.setActivity(WorkerActivity.WORKING);
        data.setRested(true);
        data.setLumberjack(new LumberjackConfig(
                Optional.of(area(level, log, log)), false, false, Optional.of(logs), Optional.of(saplings), Optional.of(tools)));
        helper.succeedWhen(() -> {
            helper.assertTrue(data.activity() == WorkerActivity.STOPPED, "paused");
            helper.assertTrue(data.problem().code() == ProblemCode.CHEST_FULL, "full");
            helper.assertTrue(said(owner, "full chest"), owner.messages.toString());
            helper.assertTrue(droppedNear(level, log, Items.OAK_LOG), "drop was kept");
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void stoppedAndComingDoNotBreak(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager stopped = villager(helper, 1, 1, 1, "Stopped");
        Villager coming = villager(helper, 4, 1, 1, "Coming");
        WorkerData stoppedData = lumberReady(helper, stopped, owner, new BlockPos(1, 1, 2), Items.DIAMOND_AXE);
        WorkerData comingData = lumberReady(helper, coming, owner, new BlockPos(4, 1, 2), Items.DIAMOND_AXE);
        stoppedData.setActivity(WorkerActivity.STOPPED);
        comingData.comeTo(owner.getUUID());
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(1, 1, 2)), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(4, 1, 2)), Blocks.OAK_LOG.defaultBlockState());
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(1, 1, 2))).is(Blocks.OAK_LOG), "stopped");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(4, 1, 2))).is(Blocks.OAK_LOG), "coming");
            done(helper, owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 400)
    public static void minerModesValuablesAndProtectedBlocks(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        Block casing = createBlock("create:andesite_casing");
        Villager clearer = villager(helper, 1, 1, 4, "Clear");
        WorkerData clear = hire(clearer, owner);
        BlockPos stone = helper.absolutePos(new BlockPos(1, 1, 3));
        BlockPos bedrock = helper.absolutePos(new BlockPos(2, 1, 3));
        BlockPos machine = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos chestPos = helper.absolutePos(new BlockPos(2, 2, 3));
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
        level.setBlockAndUpdate(machine, casing.defaultBlockState());
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        Container protectedChest = (Container) level.getBlockEntity(chestPos);
        protectedChest.setItem(0, new ItemStack(Items.DIAMOND));
        BlockPos regular = barrel(helper, 0, 1, 3);
        BlockPos valuables = barrel(helper, 0, 1, 4);
        BlockPos tools = barrel(helper, 0, 1, 5, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos ore = helper.absolutePos(new BlockPos(2, 1, 4));
        level.setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
        clear.setJob(WorkerJob.MINER);
        clear.setActivity(WorkerActivity.WORKING);
        clear.setRested(true);
        WorkArea box = area(level, stone, new BlockPos(ore.getX(), machine.getY(), ore.getZ()));
        List<BlockPos> packed = new ArrayList<>();
        List<ResourceLocation> packedIds = new ArrayList<>();
        int[][] slots = {
            {3, 1, 3}, {4, 1, 3}, {5, 1, 3}, {6, 1, 3},
            {3, 1, 4}, {4, 1, 4}, {5, 1, 4},
            {3, 2, 3}, {4, 2, 3}, {5, 2, 3}
        };
        int slot = 0;
        for (String id : PACK_BLOCKS) {
            ResourceLocation key = ResourceLocation.parse(id);
            if (!BuiltInRegistries.BLOCK.containsKey(key) || slot >= slots.length) {
                continue;
            }
            BlockPos pos = helper.absolutePos(new BlockPos(slots[slot][0], slots[slot][1], slots[slot][2]));
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(key).defaultBlockState());
            packed.add(pos);
            packedIds.add(key);
            slot++;
        }
        BlockPos rail = helper.absolutePos(new BlockPos(6, 2, 4));
        level.setBlockAndUpdate(rail, Blocks.RAIL.defaultBlockState());
        VillagerWorkersMod.LOGGER.info("Protected pack blocks placed: {}", packedIds);
        box = area(level, stone, helper.absolutePos(new BlockPos(6, 2, 4)));
        clear.setMiner(miner(MinerMode.CLEAR_BOX, box, null, 1, 2, 1, "", 4, 1, 1, 1, false, regular, valuables, tools));

        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(stone).isAir(), "stone cleared");
            helper.assertTrue(level.getBlockState(ore).isAir(), "ore cleared");
            helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock");
            helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST), "chest");
            helper.assertTrue(count(level, chestPos, Items.DIAMOND) == 1, "chest contents");
            helper.assertTrue(count(level, valuables, Items.DIAMOND) >= 1, "diamond routed");
            helper.assertTrue(count(level, regular, Items.COBBLESTONE) >= 1, "cobble routed");
            if (clear.breakWhitelist().isEmpty()) {
                helper.assertTrue(level.getBlockState(machine).is(casing), "create block");
                clear.setBreakWhitelist(List.of(
                        BuiltInRegistries.BLOCK.getKey(casing),
                        BuiltInRegistries.BLOCK.getKey(Blocks.BEDROCK)));
                helper.fail("waiting to break the whitelisted casing");
            }
            helper.assertTrue(level.getBlockState(machine).isAir(), "whitelisted create block");
            helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock stays even if whitelisted");
            helper.assertTrue(level.getBlockState(rail).is(Blocks.RAIL), "rail");
            for (int i = 0; i < packed.size(); i++) {
                helper.assertTrue(
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(packed.get(i)).getBlock()).equals(packedIds.get(i)),
                        "packed block " + packedIds.get(i));
            }
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 400)
    public static void tunnelBranchAndShaft(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        Block casing = createBlock("create:andesite_casing");
        Villager tunneler = villager(helper, 1, 1, 1, "Tunnel");
        WorkerData tunnel = hire(tunneler, owner);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 1));
        level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin.east().above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin.south(), casing.defaultBlockState());
        level.setBlockAndUpdate(origin.south().south(), Blocks.STONE.defaultBlockState());
        BlockPos regular = barrel(helper, 0, 1, 1, new ItemStack(Items.COBBLESTONE, 32));
        BlockPos valuables = barrel(helper, 0, 2, 1);
        BlockPos tools = barrel(helper, 0, 1, 0, new ItemStack(Items.DIAMOND_PICKAXE));
        tunnel.setJob(WorkerJob.MINER);
        tunnel.setActivity(WorkerActivity.WORKING);
        tunnel.setRested(true);
        tunnel.setMiner(miner(
                MinerMode.TUNNEL,
                null,
                new WorkOrigin(level.dimension().location(), origin, "east"),
                1,
                2,
                2,
                "minecraft:cobblestone",
                4,
                1,
                1,
                1,
                false,
                regular,
                valuables,
                tools));
        BlockPos floor = origin.below();
        Block floorBefore = level.getBlockState(floor).getBlock();
        BlockPos outsideWall = origin.south().south();

        Villager brancher = villager(helper, 1, 1, 5, "Branch");
        WorkerData branch = hire(brancher, owner);
        BlockPos branchOrigin = helper.absolutePos(new BlockPos(2, 1, 5));
        level.setBlockAndUpdate(branchOrigin.south(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(branchOrigin.east().east().south(), Blocks.STONE.defaultBlockState());
        BlockPos branchTools = barrel(helper, 0, 1, 5, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos branchRegular = barrel(helper, 0, 2, 5);
        BlockPos branchValuables = barrel(helper, 0, 1, 6);
        branch.setJob(WorkerJob.MINER);
        branch.setActivity(WorkerActivity.WORKING);
        branch.setRested(true);
        branch.setMiner(miner(
                MinerMode.BRANCH,
                null,
                new WorkOrigin(level.dimension().location(), branchOrigin, "east"),
                1,
                2,
                6,
                "",
                2,
                1,
                1,
                1,
                false,
                branchRegular,
                branchValuables,
                branchTools));

        Villager shafter = villager(helper, 5, 1, 1, "Shaft");
        WorkerData shaft = hire(shafter, owner);
        BlockPos shaftOrigin = helper.absolutePos(new BlockPos(6, 1, 1));
        level.setBlockAndUpdate(shaftOrigin, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(shaftOrigin.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(shaftOrigin.above(), Blocks.COBBLESTONE.defaultBlockState());
        BlockPos shaftTools = barrel(helper, 5, 1, 0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos shaftRegular = barrel(helper, 6, 1, 0);
        BlockPos shaftValuables = barrel(helper, 7, 1, 0);
        shaft.setJob(WorkerJob.MINER);
        shaft.setActivity(WorkerActivity.WORKING);
        shaft.setRested(true);
        shaft.setMiner(miner(
                MinerMode.SHAFT,
                null,
                new WorkOrigin(level.dimension().location(), shaftOrigin, "north"),
                1,
                2,
                1,
                "",
                4,
                1,
                1,
                2,
                false,
                shaftRegular,
                shaftValuables,
                shaftTools));

        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(origin).isAir(), "tunnel start");
            helper.assertTrue(level.getBlockState(origin.east().above()).isAir(), "tunnel end");
            helper.assertTrue(level.getBlockState(origin.south()).is(casing), "create block on the wall stays");
            helper.assertTrue(level.getBlockState(outsideWall).is(Blocks.STONE), "beyond the wall stays");
            helper.assertTrue(level.getBlockState(floor).is(floorBefore), "floor is not filled");
            helper.assertTrue(
                    level.getBlockState(origin.north()).is(Blocks.COBBLESTONE)
                            || level.getBlockState(origin.above().above()).is(Blocks.COBBLESTONE),
                    "a wall was placed");
            helper.assertTrue(level.getBlockState(branchOrigin.south()).is(Blocks.STONE), "no branch in the first interval");
            helper.assertTrue(level.getBlockState(branchOrigin.east().east().south()).isAir(), "branch was dug");
            helper.assertTrue(level.getBlockState(shaftOrigin).isAir(), "shaft top");
            helper.assertTrue(level.getBlockState(shaftOrigin.below()).isAir(), "shaft down");
            helper.assertTrue(level.getBlockState(shaftOrigin.above()).is(Blocks.COBBLESTONE), "shaft does not go up");
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void restedWorkerBreaksSooner(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        Villager fast = villager(helper, 1, 1, 1, "Fast");
        Villager slow = villager(helper, 5, 1, 1, "Slow");
        WorkerData fastData = minerAt(helper, fast, owner, new BlockPos(2, 1, 1), true);
        WorkerData slowData = minerAt(helper, slow, owner, new BlockPos(6, 1, 1), false);
        long[] fastTick = {-1};
        long[] slowTick = {-1};
        BlockPos fastStone = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos slowStone = helper.absolutePos(new BlockPos(6, 1, 1));
        helper.onEachTick(() -> {
            if (fastTick[0] < 0 && level.getBlockState(fastStone).isAir()) {
                fastTick[0] = helper.getTick();
            }
            if (slowTick[0] < 0 && level.getBlockState(slowStone).isAir()) {
                slowTick[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fastData.rested() && !slowData.rested(), "flags");
            helper.assertTrue(fastTick[0] > 0 && slowTick[0] > 0, "waiting");
            helper.assertTrue(fastTick[0] < slowTick[0], "rested " + fastTick[0] + " unrested " + slowTick[0]);
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 500)
    public static void stuckWorkerPausesAfterOneRetry(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Stuck");
        WorkerData data = hire(villager, owner);
        data.comeTo(owner.getUUID());
        owner.setPos(villager.getX() + 6, villager.getY(), villager.getZ());
        ServerLevel level = helper.getLevel();
        BlockPos feet = villager.blockPosition();
        for (BlockPos pos : List.of(feet.north(), feet.south(), feet.east(), feet.west(), feet.above())) {
            level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(data.activity() == WorkerActivity.STOPPED, "paused");
            helper.assertTrue(data.problem().code() == ProblemCode.STUCK, "stuck");
            helper.assertTrue(said(owner, "stuck"), owner.messages.toString());
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void problemsAreRateLimitedAndDeliveredOnLogin(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        RecordingPlayer friend = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        WorkerData data = hire(villager, owner);
        data.addTrusted(new TrustedPlayer(friend.getUUID(), friend.getGameProfile().getName()));
        data.setActivity(WorkerActivity.WORKING);
        data.setJob(WorkerJob.LUMBERJACK);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        villager.setHealth(1.0F);
        int before = owner.messages.size();
        villager.tickCount = 0;
        WorkerTicker.tick(villager, data);
        int hungryAndHurt = owner.messages.size() - before;
        helper.assertTrue(hungryAndHurt >= 2, "hungry and hurt " + owner.messages);
        helper.assertTrue(data.activity() == WorkerActivity.WORKING, "still working");
        WorkerTicker.tick(villager, data);
        helper.assertTrue(owner.messages.size() == before + hungryAndHurt, "second tick is quiet");
        helper.assertTrue(friend.messages.isEmpty(), "trusted players are not told");

        UUID realOwner = data.owner();
        data.setOwner(UUID.randomUUID());
        OwnerNotices.report(villager, data, ProblemCode.CHEST_FULL, "");
        WorkNotice pending = data.notice(ProblemCode.CHEST_FULL);
        helper.assertTrue(pending != null && pending.pending(), "stored while offline");
        int mid = owner.messages.size();
        data.setOwner(realOwner);
        OwnerNotices.flushPending(villager, data);
        helper.assertTrue(owner.messages.size() > mid, "delivered when the owner is back");
        helper.assertTrue(said(owner, "full chest"), owner.messages.toString());
        helper.assertFalse(data.notice(ProblemCode.CHEST_FULL).pending(), "pending cleared");

        owner.messages.clear();
        data.putNotice(new WorkNotice(ProblemCode.STUCK.id(), villager.level().getGameTime(), false, ""));
        OwnerNotices.report(villager, data, ProblemCode.STUCK, "");
        helper.assertTrue(owner.messages.isEmpty(), "same code inside a minute");
        data.putNotice(new WorkNotice(ProblemCode.STUCK.id(), villager.level().getGameTime() - 1200, false, ""));
        OwnerNotices.report(villager, data, ProblemCode.STUCK, "");
        helper.assertTrue(said(owner, "stuck"), "sent again after a minute");
        done(helper, owner, friend);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void sleepAndDawn(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Birch");
        WorkerData data = hire(villager, owner);
        ServerLevel level = helper.getLevel();
        BlockPos bed = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState());
        data.setActivity(WorkerActivity.WORKING);
        data.setWasSleeping(true);
        data.setPendingBed(bed);
        data.setSleptLastNight(false);
        WorkerTicker.tick(villager, data);
        helper.assertFalse(data.sleptLastNight(), "a forced wake is not a night's sleep");

        data.setActivity(WorkerActivity.IDLE);
        data.setWasSleeping(true);
        data.setPendingBed(bed);
        data.setSleptLastNight(false);
        WorkerTicker.tick(villager, data);
        helper.assertTrue(data.sleptLastNight(), "idle wake counts");
        helper.assertTrue(data.homeBed() != null && data.homeBed().pos().equals(bed), "home bed");

        data.setLastDawnDay(0);
        long previousTime = level.getDayTime();
        level.setDayTime(previousTime + 24000L);
        WorkerTicker.tick(villager, data);
        level.setDayTime(previousTime);
        helper.assertTrue(data.rested(), "dawn copies the night");
        helper.assertFalse(data.sleptLastNight(), "night flag cleared");

        level.setBlockAndUpdate(bed, Blocks.AIR.defaultBlockState());
        villager.tickCount = 20;
        WorkerTicker.tick(villager, data);
        helper.assertTrue(data.homeBed() == null, "missing bed cleared");
        done(helper, owner);
    }

    @GameTest(template = "platform", timeoutTicks = 600)
    public static void builderPlacesSchematicAndDoesNotBreak(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlockAndUpdate(source, Blocks.COBBLESTONE.defaultBlockState());
        level.setBlockAndUpdate(source.east(), Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(anchor.east(), Blocks.AIR.defaultBlockState());
        BlockPos outside = anchor.south();
        level.setBlockAndUpdate(outside, Blocks.AIR.defaultBlockState());
        String file = "house-" + owner.getGameProfile().getName() + ".nbt";
        try {
            writeSchematic(level, owner.getGameProfile().getName(), file, source, new BlockPos(2, 1, 1));
        } catch (IOException exception) {
            helper.fail(exception.getMessage());
            return;
        }
        ItemStack schematic = SchematicItem.create(level, file, owner.getGameProfile().getName());
        schematic.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        schematic.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor);
        BlockPos cannonPos = helper.absolutePos(new BlockPos(5, 1, 3));
        Block cannonBlock = createBlock("create:schematicannon");
        level.setBlockAndUpdate(cannonPos, cannonBlock.defaultBlockState());
        SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
        cannon.inventory.setStackInSlot(0, schematic.copy());
        int fuel = cannon.remainingFuel;
        Object state = cannon.state;
        int cannonPlaced = cannon.blocksPlaced;
        List<ItemStack> before = snapshot(cannon);

        Villager villager = villager(helper, 3, 1, 4, "Builder");
        WorkerData data = hire(villager, owner);
        BlockPos materials = barrel(helper, 3, 1, 5, new ItemStack(Items.COBBLESTONE, 8), new ItemStack(Items.OAK_PLANKS, 8));
        data.setJob(WorkerJob.BUILDER);
        data.setActivity(WorkerActivity.WORKING);
        data.setRested(true);
        data.setBuilder(data.builder().withSchematic(Optional.of(cannonPos)).withMaterialChest(materials));
        boolean[] stopped = {false};
        helper.onEachTick(() -> {
            if (!stopped[0] && level.getBlockState(anchor).is(Blocks.COBBLESTONE) && level.getBlockState(anchor.east()).isAir()) {
                data.setActivity(WorkerActivity.STOPPED);
                stopped[0] = true;
            }
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(stopped[0], "first block placed");
            helper.assertTrue(level.getBlockState(anchor.east()).isAir(), "stop holds the next block");
            data.setActivity(WorkerActivity.WORKING);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(anchor).is(Blocks.COBBLESTONE), "cobble placed");
            helper.assertTrue(level.getBlockState(anchor.east()).is(Blocks.OAK_PLANKS), "planks placed");
            helper.assertTrue(level.getBlockState(outside).isAir(), "outside the bounds");
            helper.assertTrue(count(level, materials, Items.COBBLESTONE) < 8, "cobble consumed");
            helper.assertTrue(cannon.remainingFuel == fuel, "fuel");
            helper.assertTrue(cannon.blocksPlaced == cannonPlaced, "cannon did not place");
            helper.assertTrue(
                    cannon.state == state
                            || cannon.state == SchematicannonBlockEntity.State.STOPPED
                            || cannon.state == SchematicannonBlockEntity.State.PAUSED,
                    "cannon was not started: " + cannon.state);
            helper.assertTrue(sameItems(before, snapshot(cannon)), "cannon inventory");
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void builderSkipsSolidBlocksAndWaitsForMaterials(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        Block casing = createBlock("create:andesite_casing");
        BlockPos source = helper.absolutePos(new BlockPos(0, 1, 6));
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 6));
        level.setBlockAndUpdate(source, Blocks.COBBLESTONE.defaultBlockState());
        level.setBlockAndUpdate(anchor, casing.defaultBlockState());
        String file = "blocked-" + owner.getGameProfile().getName() + ".nbt";
        try {
            writeSchematic(level, owner.getGameProfile().getName(), file, source, new BlockPos(1, 1, 1));
        } catch (IOException exception) {
            helper.fail(exception.getMessage());
            return;
        }
        ItemStack schematic = SchematicItem.create(level, file, owner.getGameProfile().getName());
        schematic.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        schematic.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor);
        BlockPos cannonPos = helper.absolutePos(new BlockPos(5, 1, 6));
        level.setBlockAndUpdate(cannonPos, createBlock("create:schematicannon").defaultBlockState());
        SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
        cannon.inventory.setStackInSlot(0, schematic.copy());
        Villager villager = villager(helper, 3, 1, 7, "Blocked");
        WorkerData data = hire(villager, owner);
        BlockPos materials = barrel(helper, 4, 1, 7);
        data.setJob(WorkerJob.BUILDER);
        data.setActivity(WorkerActivity.WORKING);
        data.setBuilder(data.builder().withSchematic(Optional.of(cannonPos)).withMaterialChest(materials));
        boolean[] blocked = {false};
        helper.onEachTick(() -> {
            if (!blocked[0] && data.problem().code() == ProblemCode.BLOCKED) {
                blocked[0] = true;
            }
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(level.getBlockState(anchor).is(casing), "create block stays");
            helper.assertTrue(blocked[0] || data.activity() == WorkerActivity.STOPPED, "noticed or waiting");
            put(level, materials, new ItemStack(Items.COBBLESTONE, 4));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(anchor).is(casing), "never broken");
            helper.assertTrue(said(owner, "blocked") || data.problem().code() == ProblemCode.BLOCKED, owner.messages.toString());
            cleanup(owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void builderWithoutASchematicPauses(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        Villager villager = villager(helper, 2, 1, 2, "Builder");
        WorkerData data = hire(villager, owner);
        data.setJob(WorkerJob.BUILDER);
        stand(owner, villager);
        openWorker(owner, villager);
        owner.containerMenu.clickMenuButton(owner, MenuIds.START);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(data.activity() == WorkerActivity.STOPPED, "paused");
            helper.assertTrue(
                    data.problem().code() == ProblemCode.MISSING_CHEST,
                    "problem " + data.problem().code());
            done(helper, owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void severalWorkersStayInsideATick(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < 4; i++) {
            Villager crew = villager(helper, 1 + i, 1, 1, "Crew" + i);
            WorkerData data = hire(crew, owner);
            BlockPos dirt = helper.absolutePos(new BlockPos(1 + i, 1, 2));
            level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
            BlockPos tools = barrel(helper, 1 + i, 2, 1, new ItemStack(Items.IRON_PICKAXE));
            BlockPos regular = barrel(helper, 1 + i, 2, 2);
            BlockPos valuables = barrel(helper, 1 + i, 2, 3);
            data.setJob(WorkerJob.MINER);
            data.setRested(true);
            data.setMiner(miner(
                    MinerMode.CLEAR_BOX,
                    area(level, dirt, dirt),
                    null,
                    1,
                    2,
                    1,
                    "",
                    4,
                    1,
                    1,
                    1,
                    false,
                    regular,
                    valuables,
                    tools));
            data.setActivity(WorkerActivity.WORKING);
        }
        helper.runAtTickTime(100, () -> {
            double millis = level.getServer().getAverageTickTimeNanos() / 1_000_000.0;
            VillagerWorkersMod.LOGGER.info("Average server tick with four workers: {} ms", millis);
            helper.assertTrue(millis < 50.0, "tick time " + millis + " ms");
            done(helper, owner);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void workersLeaveAssembledContraptionsAlone(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        ServerLevel level = helper.getLevel();
        Villager worker = villager(helper, 2, 1, 3, "Rigger");
        WorkerData data = hire(worker, owner);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos wall = origin.north();
        BlockPos ceiling = origin.above().above();
        level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin.above(), Blocks.STONE.defaultBlockState());
        BearingContraption contraption = new BearingContraption(false, Direction.UP);
        contraption.anchor = origin.immutable();
        AABB cover = new AABB(wall).minmax(new AABB(ceiling)).minmax(new AABB(origin.above()));
        contraption.bounds = cover;
        OrientedContraptionEntity entity = OrientedContraptionEntity.create(level, contraption, Direction.UP);
        entity.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        entity.setBoundingBox(cover);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(level.addFreshEntity(entity), "contraption is loaded");
        BlockPos regular = barrel(helper, 0, 1, 1, new ItemStack(Items.COBBLESTONE, 32));
        BlockPos valuables = barrel(helper, 0, 2, 1);
        BlockPos tools = barrel(helper, 0, 1, 0, new ItemStack(Items.DIAMOND_PICKAXE));
        data.setJob(WorkerJob.MINER);
        data.setActivity(WorkerActivity.WORKING);
        data.setRested(true);
        data.setMiner(miner(
                MinerMode.TUNNEL,
                null,
                new WorkOrigin(level.dimension().location(), origin, "east"),
                1,
                2,
                1,
                "minecraft:cobblestone",
                4,
                1,
                1,
                1,
                false,
                regular,
                valuables,
                tools));
        helper.onEachTick(() -> {
            if (!entity.isAlive()) {
                return;
            }
            entity.setBoundingBox(cover);
            entity.setDeltaMovement(Vec3.ZERO);
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(entity.isAlive(), "contraption stayed assembled");
            helper.assertTrue(entity.getBoundingBox().intersects(new AABB(origin)), "contraption covers the stone");
            helper.assertTrue(level.getBlockState(origin).is(Blocks.STONE), "worker did not break stone on the contraption");
            helper.assertTrue(level.getBlockState(origin.above()).is(Blocks.STONE), "worker did not break the block above");
            helper.assertTrue(!level.getBlockState(wall).is(Blocks.COBBLESTONE), "worker did not place a wall on the contraption");
            helper.assertTrue(!level.getBlockState(ceiling).is(Blocks.COBBLESTONE), "worker did not place a ceiling on the contraption");
            done(helper, owner);
        });
    }

    private static WorkerData minerAt(GameTestHelper helper, Villager villager, ServerPlayer owner, BlockPos relativeStone, boolean rested) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(relativeStone);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        BlockPos regular = barrel(helper, relativeStone.getX(), 2, relativeStone.getZ());
        BlockPos valuables = barrel(helper, relativeStone.getX(), 2, Math.min(7, relativeStone.getZ() + 1));
        BlockPos tools = barrel(helper, Math.max(0, relativeStone.getX() - 1), 1, relativeStone.getZ(), new ItemStack(Items.WOODEN_PICKAXE));
        WorkerData data = hire(villager, owner);
        data.setJob(WorkerJob.MINER);
        data.setActivity(WorkerActivity.WORKING);
        data.setRested(rested);
        data.setLastDawnDay(Long.MAX_VALUE);
        data.setMiner(miner(MinerMode.CLEAR_BOX, area(level, stone, stone), null, 1, 2, 1, "", 4, 1, 1, 1, false, regular, valuables, tools));
        return data;
    }

    private static WorkerData lumberReady(GameTestHelper helper, Villager villager, ServerPlayer owner, BlockPos relativeLog, Item tool) {
        WorkerData data = hire(villager, owner);
        ServerLevel level = helper.getLevel();
        BlockPos log = helper.absolutePos(relativeLog);
        BlockPos logs = barrel(helper, relativeLog.getX(), 2, relativeLog.getZ());
        BlockPos saplings = barrel(helper, relativeLog.getX(), 2, Math.min(7, relativeLog.getZ() + 1));
        ItemStack toolStack = tool == null ? ItemStack.EMPTY : new ItemStack(tool);
        BlockPos tools = barrel(helper, Math.max(0, relativeLog.getX() - 1), 1, relativeLog.getZ(), toolStack);
        data.setJob(WorkerJob.LUMBERJACK);
        data.setActivity(WorkerActivity.WORKING);
        data.setRested(true);
        data.setLumberjack(new LumberjackConfig(
                Optional.of(area(level, log, log)), false, false, Optional.of(logs), Optional.of(saplings), Optional.of(tools)));
        return data;
    }

    private static MinerConfig miner(
            MinerMode mode,
            WorkArea box,
            WorkOrigin origin,
            int width,
            int height,
            int length,
            String wall,
            int spacing,
            int branchLength,
            int shaftWidth,
            int shaftDepth,
            boolean shaftUp,
            BlockPos regular,
            BlockPos valuables,
            BlockPos tools) {
        return new MinerConfig(
                mode,
                Optional.ofNullable(box),
                Optional.ofNullable(origin),
                width,
                height,
                length,
                wall,
                spacing,
                branchLength,
                shaftWidth,
                shaftDepth,
                shaftUp,
                MinerConfig.defaultValuables(),
                Optional.of(valuables),
                Optional.of(regular),
                Optional.of(tools));
    }

    private static void writeSchematic(ServerLevel level, String owner, String file, BlockPos origin, BlockPos size) throws IOException {
        java.nio.file.Path dir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner);
        Files.createDirectories(dir);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, size, false, null);
        NbtIo.writeCompressed(template.save(new CompoundTag()), dir.resolve(file));
    }

    private static List<ItemStack> snapshot(SchematicannonBlockEntity cannon) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < cannon.inventory.getSlots(); slot++) {
            stacks.add(cannon.inventory.getStackInSlot(slot).copy());
        }
        return stacks;
    }

    private static boolean sameItems(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(left.get(i), right.get(i)) || left.get(i).getCount() != right.get(i).getCount()) {
                return false;
            }
        }
        return true;
    }

    private static Block createBlock(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalStateException("Missing block " + id);
        }
        return BuiltInRegistries.BLOCK.get(key);
    }

    private static Villager reload(ServerLevel level, Villager villager) {
        CompoundTag tag = villager.saveWithoutId(new CompoundTag());
        Villager loaded = EntityType.VILLAGER.create(level);
        loaded.load(tag);
        return loaded;
    }

    private static WorkArea area(ServerLevel level, BlockPos min, BlockPos max) {
        return new WorkArea(level.dimension().location(), min, max);
    }

    private static BlockPos barrel(GameTestHelper helper, int x, int y, int z, ItemStack... stacks) {
        helper.setBlock(x, y, z, Blocks.BARREL);
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        Container container = (Container) helper.getLevel().getBlockEntity(pos);
        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                container.setItem(i, stacks[i]);
            }
        }
        return pos;
    }

    private static void put(ServerLevel level, BlockPos pos, ItemStack stack) {
        ((Container) level.getBlockEntity(pos)).setItem(0, stack.copy());
    }

    private static void fill(ServerLevel level, BlockPos pos, Item item) {
        Container container = (Container) level.getBlockEntity(pos);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, new ItemStack(item, 64));
        }
    }

    private static int count(ServerLevel level, BlockPos pos, Item item) {
        Container container = (Container) level.getBlockEntity(pos);
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static InteractionResult useWand(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void openWorker(ServerPlayer player, Villager villager) {
        WorkerData data = WorkerAttachments.get(villager).orElseThrow();
        WorkerView view = WorkerView.capture(villager, data, player);
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, ignored) -> new WorkerMenu(id, inventory, villager, view),
                        Component.literal(view.name())),
                buf -> WorkerView.STREAM_CODEC.encode(buf, view));
    }

    private static WorkerData hire(Villager villager, ServerPlayer owner) {
        WorkerData data = WorkerData.hire(owner.getUUID());
        WorkerAttachments.hire(villager, data);
        return data;
    }

    private static Villager villager(GameTestHelper helper, int x, int y, int z, String name) {
        Villager villager = helper.spawn(EntityType.VILLAGER, x, y, z);
        villager.setCustomName(Component.literal(name));
        villager.setPersistenceRequired();
        villager.setNoAi(true);
        villager.setNoGravity(true);
        return villager;
    }

    private static void nameTag(ServerPlayer player, Villager villager, String name) {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        player.setItemInHand(InteractionHand.MAIN_HAND, tag);
        player.interactOn(villager, InteractionHand.MAIN_HAND);
    }

    private static void stand(ServerPlayer player, Villager villager) {
        player.setPos(villager.getX(), villager.getY(), villager.getZ());
    }

    private static ServerChatEvent chat(ServerPlayer player, String text) {
        return new ServerChatEvent(player, text, Component.literal(text));
    }

    private static void agePendingHire(ServerPlayer player) {
        WorkerServer.expirePendingHire(player.getUUID());
    }

    private static boolean said(RecordingPlayer player, String part) {
        for (String message : player.messages) {
            if (message.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemEntity> items(ServerLevel level) {
        List<ItemEntity> found = new ArrayList<>();
        level.getEntities().get(EntityType.ITEM, item -> {
            found.add(item);
            return net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
        });
        return found;
    }

    private static boolean droppedNear(ServerLevel level, BlockPos pos, Item item) {
        for (ItemEntity entity : itemsNear(level, pos, 8)) {
            if (entity.getItem().is(item)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemEntity> itemsNear(ServerLevel level, BlockPos pos, int radius) {
        List<ItemEntity> found = new ArrayList<>();
        int limit = radius * radius;
        for (ItemEntity entity : items(level)) {
            if (entity.blockPosition().distSqr(pos) <= limit) {
                found.add(entity);
            }
        }
        return found;
    }

    private static void cleanup(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (player != null) {
                player.server.getPlayerList().remove(player);
            }
        }
    }

    private static void done(GameTestHelper helper, ServerPlayer... players) {
        cleanup(players);
        helper.succeed();
    }

    private static RecordingPlayer player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "Ow" + Integer.toHexString(NAMES.incrementAndGet());
        GameProfile profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        RecordingPlayer player = new RecordingPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // Create broadcasts custom payloads to every player. A bare connection rejects those and crashes the server.
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static final class RecordingPlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private RecordingPlayer(
                net.minecraft.server.MinecraftServer server,
                ServerLevel level,
                com.mojang.authlib.GameProfile profile,
                net.minecraft.server.level.ClientInformation information) {
            super(server, level, profile, information);
        }

        @Override
        public void sendSystemMessage(Component message) {
            this.messages.add(message.getString());
            super.sendSystemMessage(message);
        }

        @Override
        public void sendSystemMessage(Component message, boolean bypassHiddenChat) {
            this.messages.add(message.getString());
            super.sendSystemMessage(message, bypassHiddenChat);
        }
    }
}
