package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ActionPace;
import com.vucelich.villagerworkers.common.CrewLogic;
import com.vucelich.villagerworkers.common.ProblemCode;
import com.vucelich.villagerworkers.common.ScaffoldKinds;
import com.vucelich.villagerworkers.common.ScaffoldPost;
import com.vucelich.villagerworkers.common.SetupLimits;
import com.vucelich.villagerworkers.common.WorkerActivity;
import com.vucelich.villagerworkers.common.WorkerData;
import com.vucelich.villagerworkers.common.WorkerProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Temporary blocks a builder stands on when the next schematic cell is out of reach.
 * Each placed block is remembered and put back when the build finishes.
 * This does not scan ships. The ship check runs only for the one block about to be placed.
 */
public final class Scaffolding {
    private static final Set<String> VALUABLE = Set.copyOf(SetupLimits.DEFAULT_VALUABLE_IDS);
    private static final Map<UUID, List<Post>> POSTS = new ConcurrentHashMap<>();
    /** Positions of temporary blocks. Lookup stays one hash check per schematic cell. */
    private static final Set<BlockPos> POST_AT = ConcurrentHashMap.newKeySet();
    /** Worker data for the villagers that have placed a temporary block this session. */
    private static final Map<UUID, WorkerData> DATA = new ConcurrentHashMap<>();
    /** Villagers whose saved posts have already been copied into memory. */
    private static final Set<UUID> HYDRATED = ConcurrentHashMap.newKeySet();
    /** How many scaffolding items were placed. Tests read this. Server stop does not zero it. */
    private static int scaffoldingPlaced;
    /** How many of those placements were Create andesite scaffolding. */
    private static int andesitePlaced;

    /** previousSnbt and itemId are stored once so a later save does not encode every post again. */
    private record Post(BlockPos pos, BlockState previous, Item item, String previousSnbt, String itemId) {}

    private Scaffolding() {}

    public static void forget(UUID villagerId) {
        if (villagerId == null) {
            return;
        }
        List<Post> posts = POSTS.remove(villagerId);
        if (posts == null) {
            return;
        }
        for (Post post : posts) {
            POST_AT.remove(post.pos);
        }
        HYDRATED.remove(villagerId);
        DATA.remove(villagerId);
    }

    public static void clear() {
        POSTS.clear();
        POST_AT.clear();
        DATA.clear();
        HYDRATED.clear();
    }

    /**
     * Drops this villager's in-memory posts without changing the save.
     * The next {@link #bind} reads them again, which is what a restart does.
     */
    public static void unload(UUID villagerId) {
        if (villagerId == null) {
            return;
        }
        List<Post> posts = POSTS.remove(villagerId);
        if (posts != null) {
            for (Post post : posts) {
                POST_AT.remove(post.pos);
            }
        }
        HYDRATED.remove(villagerId);
        DATA.remove(villagerId);
    }

    /**
     * Copies saved temporary blocks into memory once per load.
     * Later calls keep the same memory list and only refresh the data reference.
     */
    public static void bind(Villager villager, WorkerData data) {
        if (villager == null || data == null) {
            return;
        }
        UUID id = villager.getUUID();
        DATA.put(id, data);
        if (!HYDRATED.add(id)) {
            return;
        }
        List<ScaffoldPost> saved = data.scaffoldPosts();
        if (saved.isEmpty()) {
            return;
        }
        List<Post> posts = new ArrayList<>();
        for (ScaffoldPost one : saved) {
            if (one == null || one.itemId() == null || one.itemId().isBlank()) {
                continue;
            }
            ResourceLocation key;
            try {
                key = ResourceLocation.parse(one.itemId());
            } catch (RuntimeException ex) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            if (!(item instanceof BlockItem) || item == Items.AIR || one.state() == null || one.state().isBlank()) {
                continue;
            }
            BlockState previous;
            try {
                CompoundTag tag = TagParser.parseTag(one.state());
                previous = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
            } catch (Exception ex) {
                continue;
            }
            BlockPos pos = new BlockPos(one.x(), one.y(), one.z()).immutable();
            posts.add(new Post(pos, previous, item, one.state(), one.itemId()));
            POST_AT.add(pos);
        }
        if (!posts.isEmpty()) {
            POSTS.put(id, posts);
        }
    }

    public static int scaffoldingPlaced() {
        return scaffoldingPlaced;
    }

    public static int andesitePlaced() {
        return andesitePlaced;
    }

    public static void resetPlaced() {
        scaffoldingPlaced = 0;
        andesitePlaced = 0;
    }

    public static int standing(UUID villagerId) {
        List<Post> posts = POSTS.get(villagerId);
        return posts == null ? 0 : posts.size();
    }

    /** One step toward the cell: place a block under the next footing, or climb onto one already placed. */
    public static boolean advance(ServerLevel level, Villager villager, WorkerData data, BlockPos target, List<Container> chests) {
        bind(villager, data);
        BlockPos feet = villager.blockPosition();
        BlockPos next = nextFeet(feet, target);
        BlockPos post = next.below();
        if (!level.isLoaded(post) || !level.getWorldBorder().isWithinBounds(post)) {
            pause(level, villager, data, chests, ProblemCode.STUCK, "");
            return false;
        }
        if (ours(villager, post) || isPost(post) || !level.getBlockState(post).canBeReplaced()) {
            climb(villager, next);
            return true;
        }
        if (villager.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(post)) > ActionPace.REACH_SQR) {
            pause(level, villager, data, chests, ProblemCode.STUCK, "");
            return false;
        }
        Item item = chooseItem(level, villager, data, chests, post);
        if (item == null) {
            pause(level, villager, data, chests, ProblemCode.NEED_SCAFFOLD, "scaffolding or spare blocks");
            return false;
        }
        if (AssemblyGuards.occupied(level, post)) {
            pause(level, villager, data, chests, ProblemCode.BLOCKED, post.getX() + " " + post.getY() + " " + post.getZ());
            return false;
        }
        ServerPlayer owner = owner(villager, data);
        if (owner == null) {
            return false;
        }
        ItemStack taken = take(chests, data, item);
        if (taken.isEmpty()) {
            pause(level, villager, data, chests, ProblemCode.NEED_SCAFFOLD, "scaffolding or spare blocks");
            return false;
        }
        Block block = ((BlockItem) item).getBlock();
        BlockState previous = level.getBlockState(post);
        BlockState placed = stateFor(level, post, block);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, post);
        if (!level.setBlock(post, placed, Block.UPDATE_ALL) || EventHooks.onBlockPlace(owner, snapshot, Direction.UP)) {
            level.setBlock(post, previous, Block.UPDATE_ALL);
            giveBack(chests, data, villager, taken);
            return false;
        }
        BlockPos saved = post.immutable();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        POSTS.computeIfAbsent(villager.getUUID(), id -> new ArrayList<>())
                .add(new Post(saved, previous, item, NbtUtils.writeBlockState(previous).toString(), itemId));
        POST_AT.add(saved);
        write(villager.getUUID());
        touch(level, villager.getUUID());
        if (isScaffoldItem(item)) {
            scaffoldingPlaced++;
            if (BuiltInRegistries.ITEM.getKey(item).toString().equals(ScaffoldKinds.ANDESITE.itemId())) {
                andesitePlaced++;
            }
        }
        climb(villager, next);
        return true;
    }

    public static boolean isPost(BlockPos pos) {
        return pos != null && POST_AT.contains(pos);
    }

    /**
     * The temporary block is already the schematic block. Keep it and do not return the item.
     * Putting the old state back would erase a finished part of the build.
     */
    public static void claim(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return;
        }
        for (var entry : POSTS.entrySet()) {
            List<Post> posts = entry.getValue();
            for (int i = posts.size() - 1; i >= 0; i--) {
                if (posts.get(i).pos.equals(pos)) {
                    posts.remove(i);
                    POST_AT.remove(pos);
                    write(entry.getKey());
                    touch(level, entry.getKey());
                    return;
                }
            }
        }
    }

    /** Takes down whoever's post is at this cell and returns the item, so the real block can be placed. */
    public static void liftAt(ServerLevel level, BlockPos pos, List<Container> chests, Villager fallback) {
        for (var entry : POSTS.entrySet()) {
            List<Post> posts = entry.getValue();
            for (int i = posts.size() - 1; i >= 0; i--) {
                Post post = posts.get(i);
                if (!post.pos.equals(pos)) {
                    continue;
                }
                posts.remove(i);
                POST_AT.remove(pos);
                write(entry.getKey());
                touch(level, entry.getKey());
                Villager owner = fallback;
                WorkerData data = null;
                Entity entity = level.getEntity(entry.getKey());
                if (entity instanceof Villager villager) {
                    owner = villager;
                    data = WorkerAttachments.get(villager).orElse(null);
                }
                if (data == null && fallback != null) {
                    data = WorkerAttachments.get(fallback).orElse(null);
                }
                if (owner != null && data != null) {
                    restore(level, owner, data, chests, post);
                }
                return;
            }
        }
    }

    /** Puts every temporary block back and returns the items. Blocks we did not place are left alone. */
    public static void finish(ServerLevel level, Villager villager, WorkerData data, List<Container> chests) {
        bind(villager, data);
        List<Post> posts = POSTS.remove(villager.getUUID());
        write(villager.getUUID());
        touch(level, villager.getUUID());
        if (posts == null) {
            return;
        }
        for (int i = posts.size() - 1; i >= 0; i--) {
            Post post = posts.get(i);
            POST_AT.remove(post.pos);
            restore(level, villager, data, chests, post);
        }
    }

    private static void write(UUID villagerId) {
        WorkerData data = DATA.get(villagerId);
        if (data == null) {
            return;
        }
        List<Post> posts = POSTS.get(villagerId);
        if (posts == null || posts.isEmpty()) {
            data.setScaffoldPosts(List.of());
            return;
        }
        List<ScaffoldPost> saved = new ArrayList<>(posts.size());
        for (Post post : posts) {
            saved.add(new ScaffoldPost(
                    post.pos.getX(), post.pos.getY(), post.pos.getZ(), post.previousSnbt, post.itemId));
        }
        data.setScaffoldPosts(saved);
    }

    private static void touch(ServerLevel level, UUID villagerId) {
        if (level == null || villagerId == null) {
            return;
        }
        WorkerData data = DATA.get(villagerId);
        if (data == null) {
            return;
        }
        Entity entity = level.getEntity(villagerId);
        if (entity instanceof Villager villager && villager.getExistingDataOrNull(WorkerAttachments.WORKER.get()) != data) {
            villager.setData(WorkerAttachments.WORKER, data);
        }
    }

    private static void restore(ServerLevel level, Villager villager, WorkerData data, List<Container> chests, Post post) {
        if (!(post.item instanceof BlockItem blockItem)) {
            return;
        }
        BlockState now = level.getBlockState(post.pos);
        if (!now.is(blockItem.getBlock())) {
            // A scaffold that lost its support breaks itself and drops the item. Pick that drop up.
            recoverDrop(level, villager, data, chests, post);
            return;
        }
        level.setBlock(post.pos, post.previous, Block.UPDATE_ALL);
        giveBack(chests, data, villager, new ItemStack(post.item));
    }

    private static void recoverDrop(ServerLevel level, Villager villager, WorkerData data, List<Container> chests, Post post) {
        AABB box = new AABB(post.pos).inflate(16.0, 24.0, 16.0);
        if (!(post.item instanceof BlockItem blockItem)) {
            return;
        }
        for (FallingBlockEntity falling : level.getEntitiesOfClass(FallingBlockEntity.class, box)) {
            if (!falling.getBlockState().is(blockItem.getBlock())) {
                continue;
            }
            falling.discard();
            giveBack(chests, data, villager, new ItemStack(post.item));
            return;
        }
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(post.item))) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack taken = stack.split(1);
            if (stack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(stack);
            }
            giveBack(chests, data, villager, taken);
            return;
        }
    }

    private static boolean ours(Villager villager, BlockPos pos) {
        List<Post> posts = POSTS.get(villager.getUUID());
        if (posts == null) {
            return false;
        }
        for (Post post : posts) {
            if (post.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos nextFeet(BlockPos feet, BlockPos target) {
        int dx = target.getX() - feet.getX();
        int dy = target.getY() - feet.getY();
        int dz = target.getZ() - feet.getZ();
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax + az > 2) {
            if (ax >= az) {
                return feet.offset(Integer.signum(dx), 0, 0);
            }
            return feet.offset(0, 0, Integer.signum(dz));
        }
        if (dy > 1) {
            return feet.above();
        }
        if (dy < -1) {
            return feet.below();
        }
        if (ax + az > 0) {
            if (ax >= az) {
                return feet.offset(Integer.signum(dx), 0, 0);
            }
            return feet.offset(0, 0, Integer.signum(dz));
        }
        return dy >= 0 ? feet.above() : feet.below();
    }

    private static void climb(Villager villager, BlockPos feet) {
        villager.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        villager.getNavigation().stop();
    }

    private static Item chooseItem(ServerLevel level, Villager villager, WorkerData data, List<Container> chests, BlockPos pos) {
        WorkerData foreman = foreman(level, data);
        List<ScaffoldKinds> order = CrewLogic.effectiveScaffold(data, foreman);
        int looked = 0;
        int budget = WorkerSettings.scaffoldSearchBudget();
        for (ScaffoldKinds kind : order) {
            if (looked++ >= budget) {
                break;
            }
            if (kind == ScaffoldKinds.ANDESITE && !ModList.get().isLoaded("create")) {
                continue;
            }
            if (kind == ScaffoldKinds.SPARE) {
                Item spare = spareBlock(chests, data, budget);
                if (spare != null) {
                    return spare;
                }
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind.itemId()));
            if (item == Items.AIR || !(item instanceof BlockItem blockItem) || count(chests, data, item) <= 0) {
                continue;
            }
            Block block = blockItem.getBlock();
            if (block.defaultBlockState().hasProperty(BlockStateProperties.STABILITY_DISTANCE)
                    && supportDistance(level, pos, block) >= 7) {
                continue;
            }
            return item;
        }
        return null;
    }

    /**
     * Same support rule vanilla scaffolding uses. A tower on a sturdy block stays.
     * A piece seven steps from support is not placed, so it cannot fall into the build.
     */
    private static int supportDistance(ServerLevel level, BlockPos pos, Block block) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        int best = 7;
        if (under.is(block) && under.hasProperty(BlockStateProperties.STABILITY_DISTANCE)) {
            best = under.getValue(BlockStateProperties.STABILITY_DISTANCE);
        } else if (under.isFaceSturdy(level, below, Direction.UP)) {
            return 0;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState side = level.getBlockState(pos.relative(direction));
            if (side.is(block) && side.hasProperty(BlockStateProperties.STABILITY_DISTANCE)) {
                best = Math.min(best, side.getValue(BlockStateProperties.STABILITY_DISTANCE) + 1);
            }
        }
        return best;
    }

    private static BlockState stateFor(ServerLevel level, BlockPos pos, Block block) {
        BlockState state = block.defaultBlockState();
        if (!state.hasProperty(BlockStateProperties.STABILITY_DISTANCE)) {
            return state;
        }
        int distance = supportDistance(level, pos, block);
        state = state.setValue(BlockStateProperties.STABILITY_DISTANCE, Math.min(distance, 6));
        if (state.hasProperty(BlockStateProperties.BOTTOM)) {
            boolean bottom = distance > 0 && !level.getBlockState(pos.below()).is(block);
            state = state.setValue(BlockStateProperties.BOTTOM, bottom);
        }
        return state;
    }

    private static boolean isScaffoldItem(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return id.equals(ScaffoldKinds.VANILLA.itemId()) || id.equals(ScaffoldKinds.ANDESITE.itemId());
    }

    private static Item spareBlock(List<Container> chests, WorkerData data, int budget) {
        int considered = 0;
        int looked = 0;
        int cap = Math.max(budget, budget * 8);
        if (chests != null) {
            for (Container chest : chests) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (looked++ >= cap) {
                        return null;
                    }
                    ItemStack stack = chest.getItem(slot);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    if (considered++ >= budget) {
                        return null;
                    }
                    Item item = spare(stack);
                    if (item != null) {
                        return item;
                    }
                }
            }
        }
        for (int slot = 0; slot < WorkerData.CARRY_SLOTS; slot++) {
            if (looked++ >= cap) {
                return null;
            }
            ItemStack stack = data.carry(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (considered++ >= budget) {
                return null;
            }
            Item item = spare(stack);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private static Item spare(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() instanceof Fallable) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (VALUABLE.contains(id) || id.equals(ScaffoldKinds.ANDESITE.itemId()) || id.equals(ScaffoldKinds.VANILLA.itemId())) {
            return null;
        }
        return stack.getItem();
    }

    private static WorkerData foreman(ServerLevel level, WorkerData data) {
        if (data.foreman() == null || data.rulesOverride()) {
            return null;
        }
        Entity entity = level.getEntity(data.foreman());
        if (entity instanceof Villager villager) {
            return WorkerAttachments.get(villager).orElse(null);
        }
        return null;
    }

    private static int count(List<Container> chests, WorkerData data, Item item) {
        int total = data.carryCount(item);
        if (chests == null) {
            return total;
        }
        for (Container chest : chests) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static ItemStack take(List<Container> chests, WorkerData data, Item item) {
        if (chests != null) {
            for (Container chest : chests) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    ItemStack stack = chest.getItem(slot);
                    if (!stack.is(item)) {
                        continue;
                    }
                    ItemStack taken = stack.split(1);
                    chest.setItem(slot, stack);
                    chest.setChanged();
                    return taken;
                }
            }
        }
        for (int slot = 0; slot < WorkerData.CARRY_SLOTS; slot++) {
            ItemStack stack = data.carry(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            ItemStack taken = stack.split(1);
            data.setCarry(slot, stack);
            return taken;
        }
        return ItemStack.EMPTY;
    }

    private static void giveBack(List<Container> chests, WorkerData data, Villager villager, ItemStack stack) {
        ItemStack left = stack.copy();
        if (chests != null) {
            for (Container chest : chests) {
                left = ContainerAccess.insert(chest, left);
                if (left.isEmpty()) {
                    return;
                }
            }
        }
        left = data.insertCarry(left);
        if (!left.isEmpty()) {
            villager.spawnAtLocation(left);
        }
    }

    private static void pause(
            ServerLevel level, Villager villager, WorkerData data, List<Container> chests, ProblemCode code, String subject) {
        data.setActivity(WorkerActivity.STOPPED);
        data.setProblem(new WorkerProblem(code, subject == null ? "" : subject));
        villager.getNavigation().stop();
        finish(level, villager, data, chests);
        if (villager.getServer() == null) {
            return;
        }
        String name = villager.getCustomName() == null ? villager.getName().getString() : villager.getCustomName().getString();
        String line = com.vucelich.villagerworkers.common.StatusText.ownerAlert(name, code, subject);
        for (UUID id : data.allOwners()) {
            ServerPlayer player = villager.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
            }
        }
    }

    private static ServerPlayer owner(Villager villager, WorkerData data) {
        if (villager.getServer() == null) {
            return null;
        }
        return villager.getServer().getPlayerList().getPlayer(data.owner());
    }
}
