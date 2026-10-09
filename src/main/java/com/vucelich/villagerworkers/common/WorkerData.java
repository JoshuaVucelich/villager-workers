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
            AlertBookkeeping.CODEC.optionalFieldOf("alerts", AlertBookkeeping.NONE).forGetter(WorkerData::alerts)
    ).apply(instance, WorkerData::fromCodec));

    private UUID owner;
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
    private long hungryNotifiedAt = Long.MIN_VALUE;
    private long hurtNotifiedAt = Long.MIN_VALUE;
    private boolean hungryPending;
    private boolean hurtPending;

    /** Not saved. Used to notice the villager waking up. */
    private boolean sleepTracked;
    private boolean wasSleeping;
    private BlockPos pendingBed;

    private WorkerData() {}

    public static WorkerData hire(UUID owner) {
        WorkerData data = new WorkerData();
        data.owner = owner;
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
            AlertBookkeeping alerts) {
        WorkerData data = new WorkerData();
        data.owner = owner;
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
        data.hungryNotifiedAt = alerts.hungryNotifiedAt();
        data.hurtNotifiedAt = alerts.hurtNotifiedAt();
        data.hungryPending = alerts.hungryPending();
        data.hurtPending = alerts.hurtPending();
        return data;
    }

    private AlertBookkeeping alerts() {
        return new AlertBookkeeping(hungryNotifiedAt, hurtNotifiedAt, hungryPending, hurtPending);
    }

    /**
     * Hunger and injury notices, nested so the worker codec stays within Mojang's 16-field record limit.
     */
    public record AlertBookkeeping(long hungryNotifiedAt, long hurtNotifiedAt, boolean hungryPending, boolean hurtPending) {
        public static final AlertBookkeeping NONE = new AlertBookkeeping(Long.MIN_VALUE, Long.MIN_VALUE, false, false);

        public static final Codec<AlertBookkeeping> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("hungry_notified_at", Long.MIN_VALUE).forGetter(AlertBookkeeping::hungryNotifiedAt),
                Codec.LONG.optionalFieldOf("hurt_notified_at", Long.MIN_VALUE).forGetter(AlertBookkeeping::hurtNotifiedAt),
                Codec.BOOL.optionalFieldOf("hungry_pending", false).forGetter(AlertBookkeeping::hungryPending),
                Codec.BOOL.optionalFieldOf("hurt_pending", false).forGetter(AlertBookkeeping::hurtPending)
        ).apply(instance, AlertBookkeeping::new));
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
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
