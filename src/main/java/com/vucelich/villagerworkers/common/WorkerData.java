package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Saved worker state. Create it with {@link #hire(UUID)} and store it with the loader attachment.
 * There is no default instance: reading a missing attachment must not hire the villager.
 */
public final class WorkerData {
    /** The loader points this at its logger. Unit tests leave it empty. */
    private static Consumer<String> carryDecodeWarning = detail -> {};

    /**
     * Reads {@code carry} only when the key is present, so unit tests can load a 0.4.6 save
     * without bootstrapping the item registry. An empty inventory is omitted on write.
     */
    private static final MapCodec<List<ItemStack>> CARRY_FIELD = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString("carry"));
        }

        @Override
        public <T> DataResult<List<ItemStack>> decode(DynamicOps<T> ops, MapLike<T> input) {
            T raw = input.get("carry");
            if (raw == null) {
                return DataResult.success(List.of());
            }
            DataResult<List<ItemStack>> parsed;
            try {
                parsed = ItemStack.OPTIONAL_CODEC.listOf().parse(ops, raw);
            } catch (RuntimeException ex) {
                parsed = DataResult.error(ex::getMessage);
            }
            if (parsed.result().isPresent()) {
                return parsed;
            }
            // One unknown item must not reject the rest of the worker.
            return ops.getStream(raw).map(stream -> {
                List<ItemStack> stacks = new ArrayList<>();
                stream.forEach(element -> {
                    ItemStack stack;
                    try {
                        DataResult<ItemStack> one = ItemStack.OPTIONAL_CODEC.parse(ops, element);
                        Optional<ItemStack> decoded = one.result();
                        if (decoded.isEmpty()) {
                            warnCarry(one.error().map(DataResult.Error::message).orElse("unknown item"));
                            stack = ItemStack.EMPTY;
                        } else {
                            stack = decoded.get();
                        }
                    } catch (RuntimeException ex) {
                        warnCarry(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                        stack = ItemStack.EMPTY;
                    }
                    stacks.add(stack);
                });
                return stacks;
            });
        }

        @Override
        public <T> RecordBuilder<T> encode(List<ItemStack> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            if (input == null || input.isEmpty()) {
                return prefix;
            }
            return prefix.add("carry", input, ItemStack.OPTIONAL_CODEC.listOf());
        }
    };

    /**
     * Saved body. Extra owners are a top-level {@code owners} list added by {@link #CODEC}, so this record stays
     * within Mojang's 16-field limit and a 0.4.9 save that only has {@code owner} still loads.
     */
    private static final Codec<WorkerData> STORED = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(WorkerData::owner),
            TrustedPlayer.CODEC.listOf().optionalFieldOf("trusted", List.of()).forGetter(WorkerData::trusted),
            Codec.STRING.xmap(WorkerJob::byId, WorkerJob::id).optionalFieldOf("job", WorkerJob.NONE).forGetter(WorkerData::job),
            Codec.STRING.xmap(WorkerActivity::byId, WorkerActivity::id).optionalFieldOf("activity", WorkerActivity.IDLE).forGetter(WorkerData::activity),
            Codec.BOOL.optionalFieldOf("rested", false).forGetter(WorkerData::rested),
            Codec.BOOL.optionalFieldOf("slept_last_night", false).forGetter(WorkerData::sleptLastNight),
            Codec.LONG.optionalFieldOf("last_dawn_day", Long.MIN_VALUE).forGetter(WorkerData::lastDawnDay),
            GlobalPos.CODEC.optionalFieldOf("home_bed").forGetter(data -> Optional.ofNullable(data.homeBed())),
            LumberjackConfig.CODEC.optionalFieldOf("lumberjack", LumberjackConfig.defaults()).forGetter(WorkerData::lumberjack),
            MinerConfig.CODEC.optionalFieldOf("miner", MinerConfig.defaults()).forGetter(WorkerData::miner),
            BuilderConfig.CODEC.optionalFieldOf("builder", BuilderConfig.defaults()).forGetter(WorkerData::builder),
            ResourceLocation.CODEC.listOf().optionalFieldOf("break_whitelist", List.of()).forGetter(WorkerData::breakWhitelist),
            WorkerProblem.CODEC.optionalFieldOf("problem", WorkerProblem.NONE).forGetter(WorkerData::problem),
            UUIDUtil.STRING_CODEC.optionalFieldOf("come_target").forGetter(data -> Optional.ofNullable(data.comeTarget())),
            AlertBookkeeping.CODEC.optionalFieldOf("alerts", AlertBookkeeping.NONE).forGetter(WorkerData::alerts),
            CARRY_FIELD.forGetter(WorkerData::carryForCodec)
    ).apply(instance, WorkerData::fromCodec));

    public static final Codec<WorkerData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<com.mojang.datafixers.util.Pair<WorkerData, T>> decode(DynamicOps<T> ops, T input) {
            return STORED.decode(ops, input).flatMap(pair -> ops.getMap(input).flatMap(map -> {
                WorkerData data = pair.getFirst();
                T rawOwners = map.get("owners");
                DataResult<List<UUID>> owners = rawOwners == null
                        ? DataResult.success(List.of())
                        : UUIDUtil.STRING_CODEC.listOf().parse(ops, rawOwners);
                if (owners.error().isPresent()) {
                    return DataResult.error(() -> owners.error().get().message());
                }
                data.setOwners(owners.result().orElse(List.of()));
                T rawFounder = map.get("original_owner");
                if (rawFounder == null) {
                    data.adoptOriginalOwner(data.owner());
                    return DataResult.success(pair);
                }
                DataResult<UUID> founder = UUIDUtil.STRING_CODEC.parse(ops, rawFounder);
                if (founder.error().isPresent() || founder.result().isEmpty()) {
                    data.adoptOriginalOwner(data.owner());
                    return DataResult.success(pair);
                }
                data.adoptOriginalOwner(founder.result().get());
                return DataResult.success(pair);
            }));
        }

        @Override
        public <T> DataResult<T> encode(WorkerData input, DynamicOps<T> ops, T prefix) {
            return STORED.encode(input, ops, prefix).flatMap(encoded -> {
                DataResult<T> body = DataResult.success(encoded);
                if (input.originalOwner() != null) {
                    body = UUIDUtil.STRING_CODEC.encodeStart(ops, input.originalOwner())
                            .flatMap(value -> ops.mergeToMap(encoded, ops.createString("original_owner"), value));
                }
                return body.flatMap(written -> {
                    List<UUID> owners = input.allOwners();
                    if (owners.size() <= 1) {
                        return DataResult.success(written);
                    }
                    return UUIDUtil.STRING_CODEC.listOf().encodeStart(ops, owners)
                            .flatMap(value -> ops.mergeToMap(written, ops.createString("owners"), value));
                });
            });
        }
    };

    // Mojang's record codec accepts at most 16 fields. Rank, crew, and the extra owner list live beside it.

    private UUID owner;
    /**
     * The player who hired this worker. Sole transfer does not change it.
     * Absent on a 0.4.9 or early 0.5 save, which adopts the first owner.
     */
    private UUID originalOwner;
    /** Owners besides {@link #owner}. The full set is capped at {@link SetupLimits#MAX_OWNERS}. */
    private final List<UUID> coOwners = new ArrayList<>();
    private final List<TrustedPlayer> trusted = new ArrayList<>();
    private WorkerJob job = WorkerJob.NONE;
    private WorkerActivity activity = WorkerActivity.IDLE;
    private boolean rested;
    private boolean sleptLastNight;
    private long lastDawnDay = Long.MIN_VALUE;
    private GlobalPos homeBed;
    private LumberjackConfig lumberjack = LumberjackConfig.defaults();
    private MinerConfig miner = MinerConfig.defaults();
    private BuilderConfig builder = BuilderConfig.defaults();
    private final List<ResourceLocation> breakWhitelist = new ArrayList<>();
    private WorkerProblem problem = WorkerProblem.NONE;
    private WorkerRank rank = WorkerRank.WORKER;
    /** Villager UUID of the foreman this worker reports to. Empty when unassigned. */
    private UUID foreman;
    /** When true, crew rules on the foreman do not replace this worker's own rules. */
    private boolean rulesOverride;
    /** Comma-separated scaffold kinds. Empty means the default order. */
    private String scaffoldOrder = "";
    private boolean plainBlocks;
    /** Temporary blocks still standing. Saved so a restart removes only these. */
    private final List<ScaffoldPost> scaffoldPosts = new ArrayList<>();
    private UUID comeTarget;
    private final List<WorkNotice> notices = new ArrayList<>();
    private long hungryNotifiedAt = Long.MIN_VALUE;
    private long hurtNotifiedAt = Long.MIN_VALUE;
    private boolean hungryPending;
    private boolean hurtPending;
    /** 27 carry slots, then one tool slot. Absent on older saves, which load as empty. */
    public static final int CARRY_SLOTS = 27;
    public static final int TOOL_SLOT = 27;
    public static final int CARRY_SIZE = 28;
    /** Null means empty, so a worker with nothing in it does not touch the item registry. */
    private final ItemStack[] carry = new ItemStack[CARRY_SIZE];

    /** Not saved. Used to notice the villager waking up. */
    private boolean sleepTracked;
    private boolean wasSleeping;
    private BlockPos pendingBed;

    private WorkerData() {}

    /** Logs one warning per carry slot that cannot be decoded, without failing the rest of the save. */
    public static void setCarryDecodeWarning(Consumer<String> warning) {
        carryDecodeWarning = warning == null ? detail -> {} : warning;
    }

    private static void warnCarry(String detail) {
        carryDecodeWarning.accept(detail == null || detail.isBlank() ? "unknown item" : detail);
    }

    public static WorkerData hire(UUID owner) {
        WorkerData data = new WorkerData();
        data.owner = owner;
        data.originalOwner = owner;
        data.job = WorkerJob.NONE;
        data.activity = WorkerActivity.IDLE;
        data.rested = false;
        data.sleptLastNight = false;
        data.homeBed = null;
        data.lumberjack = LumberjackConfig.defaults();
        data.miner = MinerConfig.defaults();
        data.builder = BuilderConfig.defaults();
        data.problem = WorkerProblem.NONE;
        return data;
    }

    private static WorkerData fromCodec(
            UUID owner,
            List<TrustedPlayer> trusted,
            WorkerJob job,
            WorkerActivity activity,
            boolean rested,
            boolean sleptLastNight,
            long lastDawnDay,
            Optional<GlobalPos> homeBed,
            LumberjackConfig lumberjack,
            MinerConfig miner,
            BuilderConfig builder,
            List<ResourceLocation> breakWhitelist,
            WorkerProblem problem,
            Optional<UUID> comeTarget,
            AlertBookkeeping alerts,
            List<ItemStack> carryStacks) {
        WorkerData data = new WorkerData();
        data.owner = owner;
        data.originalOwner = owner;
        data.trusted.addAll(trusted);
        data.job = job;
        data.activity = activity;
        data.rested = rested;
        data.sleptLastNight = sleptLastNight;
        data.lastDawnDay = lastDawnDay;
        data.homeBed = homeBed.orElse(null);
        data.lumberjack = lumberjack;
        data.miner = miner.sanitized();
        data.builder = builder;
        data.breakWhitelist.addAll(breakWhitelist);
        data.problem = problem;
        data.rank = WorkerRank.byId(alerts.rank());
        data.foreman = alerts.foreman() == null ? null : alerts.foreman().orElse(null);
        data.rulesOverride = alerts.rulesOverride();
        data.scaffoldOrder = alerts.scaffoldOrder() == null ? "" : alerts.scaffoldOrder();
        data.plainBlocks = alerts.plainBlocks();
        if (alerts.scaffoldPosts() != null) {
            data.scaffoldPosts.addAll(alerts.scaffoldPosts());
        }
        data.comeTarget = comeTarget.orElse(null);
        if (alerts.notices() != null) {
            data.notices.addAll(alerts.notices());
        }
        data.hungryNotifiedAt = alerts.hungryNotifiedAt();
        data.hurtNotifiedAt = alerts.hurtNotifiedAt();
        data.hungryPending = alerts.hungryPending();
        data.hurtPending = alerts.hurtPending();
        data.readCarry(carryStacks);
        return data;
    }

    private List<ItemStack> carryForCodec() {
        for (int slot = 0; slot < CARRY_SIZE; slot++) {
            ItemStack stack = carry[slot];
            if (stack != null && !stack.isEmpty()) {
                List<ItemStack> copy = new ArrayList<>(CARRY_SIZE);
                for (int index = 0; index < CARRY_SIZE; index++) {
                    ItemStack one = carry[index];
                    copy.add(one == null ? ItemStack.EMPTY : one.copy());
                }
                return copy;
            }
        }
        return List.of();
    }

    private void readCarry(List<ItemStack> stacks) {
        for (int slot = 0; slot < CARRY_SIZE; slot++) {
            ItemStack stack = stacks != null && slot < stacks.size() ? stacks.get(slot) : null;
            carry[slot] = stack == null || stack.isEmpty() ? null : stack.copy();
        }
    }

    public boolean carryIsEmpty() {
        for (int slot = 0; slot < CARRY_SIZE; slot++) {
            ItemStack stack = carry[slot];
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public ItemStack carry(int slot) {
        if (slot < 0 || slot >= CARRY_SIZE || carry[slot] == null) {
            return ItemStack.EMPTY;
        }
        return carry[slot];
    }

    public void setCarry(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CARRY_SIZE) {
            return;
        }
        carry[slot] = stack == null || stack.isEmpty() ? null : stack.copy();
    }

    /** Puts a stack into the 27 carry slots. The tool slot is left alone. Returns what did not fit. */
    public ItemStack insertCarry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack left = stack.copy();
        for (int slot = 0; slot < CARRY_SLOTS && !left.isEmpty(); slot++) {
            ItemStack existing = carry[slot];
            if (existing == null || existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, left)) {
                continue;
            }
            int room = Math.min(left.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (room <= 0) {
                continue;
            }
            existing.grow(room);
            left.shrink(room);
        }
        for (int slot = 0; slot < CARRY_SLOTS && !left.isEmpty(); slot++) {
            if (carry[slot] != null && !carry[slot].isEmpty()) {
                continue;
            }
            int move = Math.min(left.getMaxStackSize(), left.getCount());
            carry[slot] = left.split(move);
        }
        return left;
    }

    public int carryCount(net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < CARRY_SLOTS; slot++) {
            ItemStack stack = carry[slot];
            if (stack != null && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private AlertBookkeeping alerts() {
        return new AlertBookkeeping(
                hungryNotifiedAt,
                hurtNotifiedAt,
                hungryPending,
                hurtPending,
                List.copyOf(notices),
                rank.id(),
                Optional.ofNullable(foreman),
                rulesOverride,
                scaffoldOrder,
                plainBlocks,
                List.copyOf(scaffoldPosts));
    }

    /**
     * Hunger, injury, work notices, rank, crew, and temporary blocks.
     * Nested so the worker codec stays within Mojang's 16-field record limit.
     */
    public record AlertBookkeeping(
            long hungryNotifiedAt,
            long hurtNotifiedAt,
            boolean hungryPending,
            boolean hurtPending,
            List<WorkNotice> notices,
            String rank,
            Optional<UUID> foreman,
            boolean rulesOverride,
            String scaffoldOrder,
            boolean plainBlocks,
            List<ScaffoldPost> scaffoldPosts) {
        public static final AlertBookkeeping NONE = new AlertBookkeeping(
                Long.MIN_VALUE,
                Long.MIN_VALUE,
                false,
                false,
                List.of(),
                WorkerRank.WORKER.id(),
                Optional.empty(),
                false,
                "",
                false,
                List.of());

        public static final Codec<AlertBookkeeping> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("hungry_notified_at", Long.MIN_VALUE).forGetter(AlertBookkeeping::hungryNotifiedAt),
                Codec.LONG.optionalFieldOf("hurt_notified_at", Long.MIN_VALUE).forGetter(AlertBookkeeping::hurtNotifiedAt),
                Codec.BOOL.optionalFieldOf("hungry_pending", false).forGetter(AlertBookkeeping::hungryPending),
                Codec.BOOL.optionalFieldOf("hurt_pending", false).forGetter(AlertBookkeeping::hurtPending),
                WorkNotice.CODEC.listOf().optionalFieldOf("notices", List.of()).forGetter(AlertBookkeeping::notices),
                Codec.STRING.optionalFieldOf("rank", WorkerRank.WORKER.id()).forGetter(AlertBookkeeping::rank),
                UUIDUtil.STRING_CODEC.optionalFieldOf("foreman").forGetter(AlertBookkeeping::foreman),
                Codec.BOOL.optionalFieldOf("rules_override", false).forGetter(AlertBookkeeping::rulesOverride),
                Codec.STRING.optionalFieldOf("scaffold_order", "").forGetter(AlertBookkeeping::scaffoldOrder),
                Codec.BOOL.optionalFieldOf("plain_blocks", false).forGetter(AlertBookkeeping::plainBlocks),
                ScaffoldPost.CODEC.listOf().optionalFieldOf("scaffold_posts", List.of()).forGetter(AlertBookkeeping::scaffoldPosts)
        ).apply(instance, AlertBookkeeping::new));
    }

    public List<ScaffoldPost> scaffoldPosts() {
        return List.copyOf(scaffoldPosts);
    }

    public void setScaffoldPosts(List<ScaffoldPost> posts) {
        scaffoldPosts.clear();
        if (posts != null) {
            scaffoldPosts.addAll(posts);
        }
    }

    public WorkerRank rank() {
        return rank == null ? WorkerRank.WORKER : rank;
    }

    public void setRank(WorkerRank rank) {
        this.rank = rank == null ? WorkerRank.WORKER : rank;
    }

    public UUID foreman() {
        return foreman;
    }

    public void setForeman(UUID foreman) {
        this.foreman = foreman;
    }

    public boolean rulesOverride() {
        return rulesOverride;
    }

    public void setRulesOverride(boolean rulesOverride) {
        this.rulesOverride = rulesOverride;
    }

    public String scaffoldOrder() {
        return scaffoldOrder == null ? "" : scaffoldOrder;
    }

    public void setScaffoldOrder(String scaffoldOrder) {
        this.scaffoldOrder = scaffoldOrder == null ? "" : scaffoldOrder;
    }

    public boolean plainBlocks() {
        return plainBlocks;
    }

    public void setPlainBlocks(boolean plainBlocks) {
        this.plainBlocks = plainBlocks;
    }

    public UUID owner() {
        return owner;
    }

    /** The hiring player. Sole transfer leaves this in place. */
    public UUID originalOwner() {
        return originalOwner;
    }

    private void adoptOriginalOwner(UUID id) {
        this.originalOwner = id;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public boolean isOwner(UUID id) {
        return id != null && (id.equals(owner) || coOwners.contains(id));
    }

    public List<UUID> coOwners() {
        return List.copyOf(coOwners);
    }

    public Set<UUID> coOwnerIds() {
        return new LinkedHashSet<>(coOwners);
    }

    /** Primary owner first, then co-owners. At most {@link SetupLimits#MAX_OWNERS}. */
    public List<UUID> allOwners() {
        List<UUID> ids = new ArrayList<>();
        if (owner != null) {
            ids.add(owner);
        }
        for (UUID id : coOwners) {
            if (id != null && !ids.contains(id) && ids.size() < SetupLimits.MAX_OWNERS) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    public int ownerCount() {
        return allOwners().size();
    }

    /**
     * Replaces the owner set. The first id is the primary owner. Empty means "leave the current primary",
     * which is how a legacy save with only {@code owner} reloads.
     */
    public void setOwners(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<UUID> unique = new ArrayList<>();
        for (UUID id : ids) {
            if (id != null && !unique.contains(id) && unique.size() < SetupLimits.MAX_OWNERS) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        this.owner = unique.get(0);
        coOwners.clear();
        for (int i = 1; i < unique.size(); i++) {
            coOwners.add(unique.get(i));
        }
        for (UUID id : unique) {
            removeTrusted(id);
        }
    }

    /** Drops a co-owner. The primary owner is left in place. */
    public boolean removeCoOwner(UUID id) {
        if (id == null || id.equals(owner)) {
            return false;
        }
        return coOwners.remove(id);
    }

    /** Adds an owner. A trusted player with this id is removed from the trusted set. */
    public boolean addOwner(UUID id) {
        if (id == null || isOwner(id) || ownerCount() >= SetupLimits.MAX_OWNERS) {
            return false;
        }
        removeTrusted(id);
        coOwners.add(id);
        return true;
    }

    /** One owner remains. Previous owners are not added to the trusted set. The original owner stays. */
    public void transferSoleTo(UUID newOwner) {
        if (newOwner == null) {
            return;
        }
        removeTrusted(newOwner);
        this.owner = newOwner;
        coOwners.clear();
    }

    public List<TrustedPlayer> trusted() {
        return List.copyOf(trusted);
    }

    public Set<UUID> trustedIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TrustedPlayer player : trusted) {
            ids.add(player.id());
        }
        return ids;
    }

    public boolean addTrusted(TrustedPlayer player) {
        if (player == null || player.id().equals(owner) || trustedIds().contains(player.id())) {
            return false;
        }
        if (trusted.size() >= SetupLimits.MAX_TRUSTED) {
            return false;
        }
        trusted.add(player);
        return true;
    }

    public boolean removeTrusted(UUID id) {
        return trusted.removeIf(player -> player.id().equals(id));
    }

    public WorkerJob job() {
        return job;
    }

    public void setJob(WorkerJob job) {
        this.job = job == null ? WorkerJob.NONE : job;
    }

    public WorkerActivity activity() {
        return activity;
    }

    public void setActivity(WorkerActivity activity) {
        this.activity = activity == null ? WorkerActivity.IDLE : activity;
        if (this.activity != WorkerActivity.COMING) {
            this.comeTarget = null;
        }
    }

    public UUID comeTarget() {
        return comeTarget;
    }

    public void comeTo(UUID player) {
        this.activity = WorkerActivity.COMING;
        this.comeTarget = player;
    }

    public List<WorkNotice> notices() {
        return List.copyOf(notices);
    }

    public WorkNotice notice(ProblemCode code) {
        if (code == null) {
            return null;
        }
        for (WorkNotice notice : notices) {
            if (code.id().equals(notice.code())) {
                return notice;
            }
        }
        return null;
    }

    public void putNotice(WorkNotice notice) {
        if (notice == null) {
            return;
        }
        notices.removeIf(existing -> existing.code().equals(notice.code()));
        notices.add(notice);
    }

    public List<BlockPos> linkedPositions() {
        List<BlockPos> positions = new ArrayList<>();
        lumberjack.logChest().ifPresent(positions::add);
        lumberjack.saplingChest().ifPresent(positions::add);
        lumberjack.toolChest().ifPresent(positions::add);
        miner.valuablesChest().ifPresent(positions::add);
        miner.regularChest().ifPresent(positions::add);
        miner.toolChest().ifPresent(positions::add);
        builder.schematicAnchor().ifPresent(positions::add);
        builder.structureOrigin().ifPresent(positions::add);
        positions.addAll(builder.materialChests());
        return positions;
    }

    public boolean rested() {
        return rested;
    }

    public void setRested(boolean rested) {
        this.rested = rested;
    }

    public boolean sleptLastNight() {
        return sleptLastNight;
    }

    public void setSleptLastNight(boolean sleptLastNight) {
        this.sleptLastNight = sleptLastNight;
    }

    public long lastDawnDay() {
        return lastDawnDay;
    }

    public void setLastDawnDay(long lastDawnDay) {
        this.lastDawnDay = lastDawnDay;
    }

    public GlobalPos homeBed() {
        return homeBed;
    }

    public void setHomeBed(GlobalPos homeBed) {
        this.homeBed = homeBed;
    }

    public LumberjackConfig lumberjack() {
        return lumberjack;
    }

    public void setLumberjack(LumberjackConfig lumberjack) {
        this.lumberjack = lumberjack == null ? LumberjackConfig.defaults() : lumberjack;
    }

    public MinerConfig miner() {
        return miner;
    }

    public void setMiner(MinerConfig miner) {
        this.miner = miner == null ? MinerConfig.defaults() : miner.sanitized();
    }

    public BuilderConfig builder() {
        return builder;
    }

    public void setBuilder(BuilderConfig builder) {
        this.builder = builder == null ? BuilderConfig.defaults() : builder;
    }

    public List<ResourceLocation> breakWhitelist() {
        return List.copyOf(breakWhitelist);
    }

    public void setBreakWhitelist(List<ResourceLocation> ids) {
        breakWhitelist.clear();
        if (ids == null) {
            return;
        }
        for (ResourceLocation id : ids) {
            if (breakWhitelist.size() >= SetupLimits.MAX_LIST) {
                break;
            }
            if (!breakWhitelist.contains(id)) {
                breakWhitelist.add(id);
            }
        }
    }

    public WorkerProblem problem() {
        return problem;
    }

    public void setProblem(WorkerProblem problem) {
        this.problem = problem == null ? WorkerProblem.NONE : problem;
    }

    public long hungryNotifiedAt() {
        return hungryNotifiedAt;
    }

    public void setHungryNotifiedAt(long hungryNotifiedAt) {
        this.hungryNotifiedAt = hungryNotifiedAt;
    }

    public long hurtNotifiedAt() {
        return hurtNotifiedAt;
    }

    public void setHurtNotifiedAt(long hurtNotifiedAt) {
        this.hurtNotifiedAt = hurtNotifiedAt;
    }

    public boolean hungryPending() {
        return hungryPending;
    }

    public void setHungryPending(boolean hungryPending) {
        this.hungryPending = hungryPending;
    }

    public boolean hurtPending() {
        return hurtPending;
    }

    public void setHurtPending(boolean hurtPending) {
        this.hurtPending = hurtPending;
    }

    public boolean sleepTracked() {
        return sleepTracked;
    }

    public void setSleepTracked(boolean sleepTracked) {
        this.sleepTracked = sleepTracked;
    }

    public boolean wasSleeping() {
        return wasSleeping;
    }

    public void setWasSleeping(boolean wasSleeping) {
        this.wasSleeping = wasSleeping;
    }

    public BlockPos pendingBed() {
        return pendingBed;
    }

    public void setPendingBed(BlockPos pendingBed) {
        this.pendingBed = pendingBed;
    }
}
