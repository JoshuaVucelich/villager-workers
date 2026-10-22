package com.vucelich.villagerworkers.common;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * Saved worker state. Create it with {@link #hire(UUID)} and store it with the loader attachment.
 * There is no default instance: reading a missing attachment must not hire the villager.
 */
public final class WorkerData {
    public static final Codec<WorkerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
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
            TrustedPlayer.CODEC.listOf().optionalFieldOf("owners", List.of()).forGetter(WorkerData::persistedOwners)
    ).apply(instance, WorkerData::fromCodec));

    private UUID owner;
    private final List<TrustedPlayer> owners = new ArrayList<>();
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
    private UUID comeTarget;
    private final List<WorkNotice> notices = new ArrayList<>();
    private long hungryNotifiedAt = Long.MIN_VALUE;
    private long hurtNotifiedAt = Long.MIN_VALUE;
    private boolean hungryPending;
    private boolean hurtPending;
    private boolean foreman;
    private int crewSize;

    /** Not saved. Used to notice the villager waking up. */
    private boolean sleepTracked;
    private boolean wasSleeping;
    private BlockPos pendingBed;

    private WorkerData() {}

    public static WorkerData hire(UUID owner) {
        WorkerData data = new WorkerData();
        data.owner = owner;
        if (owner != null) {
            data.owners.add(new TrustedPlayer(owner, ""));
        }
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
            List<TrustedPlayer> owners) {
        WorkerData data = new WorkerData();
        data.owner = owner;
        data.owners.addAll(owners);
        data.ensurePrimaryOwner();
        data.trusted.addAll(trusted);
        data.trusted.removeIf(player -> data.isOwner(player.id()));
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
        data.comeTarget = comeTarget.orElse(null);
        if (alerts.notices() != null) {
            data.notices.addAll(alerts.notices());
        }
        data.hungryNotifiedAt = alerts.hungryNotifiedAt();
        data.hurtNotifiedAt = alerts.hurtNotifiedAt();
        data.hungryPending = alerts.hungryPending();
        data.hurtPending = alerts.hurtPending();
        data.foreman = alerts.foreman();
        data.crewSize = alerts.crewSize();
        return data;
    }

    private AlertBookkeeping alerts() {
        return new AlertBookkeeping(
                hungryNotifiedAt, hurtNotifiedAt, hungryPending, hurtPending, List.copyOf(notices), foreman, crewSize);
    }

    /**
     * Hunger, injury, work notices, and the foreman flag. Nested so the worker codec stays within Mojang's 16-field record limit.
     */
    public record AlertBookkeeping(
            long hungryNotifiedAt,
            long hurtNotifiedAt,
            boolean hungryPending,
            boolean hurtPending,
            List<WorkNotice> notices,
            boolean foreman,
            int crewSize) {
        public static final AlertBookkeeping NONE =
                new AlertBookkeeping(Long.MIN_VALUE, Long.MIN_VALUE, false, false, List.of(), false, 0);

        public static final Codec<AlertBookkeeping> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("hungry_notified_at", Long.MIN_VALUE).forGetter(AlertBookkeeping::hungryNotifiedAt),
                Codec.LONG.optionalFieldOf("hurt_notified_at", Long.MIN_VALUE).forGetter(AlertBookkeeping::hurtNotifiedAt),
                Codec.BOOL.optionalFieldOf("hungry_pending", false).forGetter(AlertBookkeeping::hungryPending),
                Codec.BOOL.optionalFieldOf("hurt_pending", false).forGetter(AlertBookkeeping::hurtPending),
                WorkNotice.CODEC.listOf().optionalFieldOf("notices", List.of()).forGetter(AlertBookkeeping::notices),
                Codec.BOOL.optionalFieldOf("foreman", false).forGetter(AlertBookkeeping::foreman),
                Codec.INT.optionalFieldOf("crew_size", 0).forGetter(AlertBookkeeping::crewSize)
        ).apply(instance, AlertBookkeeping::new));
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        ensurePrimaryOwner();
    }

    public List<TrustedPlayer> owners() {
        List<TrustedPlayer> copy = new ArrayList<>(owners.size() + 1);
        boolean sawPrimary = false;
        for (TrustedPlayer player : owners) {
            if (player.id().equals(owner)) {
                sawPrimary = true;
            }
            copy.add(player);
        }
        if (!sawPrimary && owner != null) {
            copy.add(0, new TrustedPlayer(owner, ""));
        }
        return List.copyOf(copy);
    }

    public Set<UUID> ownerIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TrustedPlayer player : owners()) {
            ids.add(player.id());
        }
        return ids;
    }

    public boolean isOwner(UUID id) {
        return id != null && ownerIds().contains(id);
    }

    public int ownerCount() {
        return ownerIds().size();
    }

    /**
     * Adds another owner. The primary owner field stays put. A player who was trusted becomes an owner instead.
     */
    public boolean addOwner(TrustedPlayer player) {
        if (player == null || player.id() == null || isOwner(player.id())) {
            return false;
        }
        if (ownerCount() >= SetupLimits.MAX_OWNERS) {
            return false;
        }
        removeTrusted(player.id());
        owners.add(player);
        return true;
    }

    /** Drops one owner. The last owner cannot be removed. */
    public boolean removeOwner(UUID id) {
        if (!isOwner(id) || ownerCount() <= 1) {
            return false;
        }
        owners.removeIf(player -> player.id().equals(id));
        if (id.equals(owner)) {
            owner = owners.get(0).id();
        }
        return true;
    }

    /**
     * Leaves exactly one owner. Previous owners are not added to the trusted set.
     */
    public void transferSole(TrustedPlayer player) {
        if (player == null || player.id() == null) {
            return;
        }
        owner = player.id();
        owners.clear();
        owners.add(player);
        removeTrusted(player.id());
    }

    public void rememberOwnerName(UUID id, String name) {
        if (id == null || name == null || name.isBlank()) {
            return;
        }
        for (int i = 0; i < owners.size(); i++) {
            TrustedPlayer player = owners.get(i);
            if (player.id().equals(id)) {
                owners.set(i, new TrustedPlayer(id, name));
                return;
            }
        }
    }

    private List<TrustedPlayer> persistedOwners() {
        return owners();
    }

    private void ensurePrimaryOwner() {
        if (owner == null) {
            return;
        }
        for (TrustedPlayer player : owners) {
            if (player.id().equals(owner)) {
                return;
            }
        }
        owners.add(0, new TrustedPlayer(owner, ""));
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
        if (player == null || isOwner(player.id()) || trustedIds().contains(player.id())) {
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

    public boolean foreman() {
        return foreman;
    }

    /** Cosmetic rank only. It does not put an item in the villager's inventory or hands. */
    public void setForeman(boolean foreman) {
        this.foreman = foreman;
    }

    public int crewSize() {
        return crewSize;
    }

    public void setCrewSize(int crewSize) {
        this.crewSize = Math.max(0, crewSize);
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
