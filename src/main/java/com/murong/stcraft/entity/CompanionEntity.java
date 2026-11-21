package com.murong.stcraft.entity;

import com.murong.stcraft.Tuning;
import com.murong.stcraft.network.ChatLine;
import com.murong.stcraft.tavern.ChatService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.murong.stcraft.Tuning.*;

public class CompanionEntity extends PathfinderMob
{
    public static final int FREE = 0, STAY = 1, FOLLOW = 2;
    private static final EntityDataAccessor<String> AVATAR = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    public static final TicketType<ChunkPos> TICKET = TicketType.create("stcraft_companion", Comparator.comparingLong(ChunkPos::toLong), 40);

    private BlockPos home = BlockPos.ZERO;
    private ResourceLocation homeDim;
    private int releaseTicks;
    private int talkTicks;
    private int otherLevelChecks;

    public String getSkinSource() { return entityData.get(SKIN); }
    public void setSkinSource(String s) { entityData.set(SKIN, s); }
    public boolean isSlim() { return entityData.get(SLIM); }
    public void setSlim(boolean slim) { entityData.set(SLIM, slim); }

    private UUID followTarget;

    public int getMode() { return entityData.get(MODE); }
    public boolean isFollowing() { return getMode() == FOLLOW; }
    public boolean isStaying() { return getMode() == STAY; }
    public boolean isFree() { return getMode() == FREE; }
    public UUID getFollowTarget() { return followTarget; }

    public void follow(UUID player) {
        followTarget = player;
        entityData.set(MODE, FOLLOW);
        updateRestriction();
    }

    public void stay() {
        followTarget = null;
        entityData.set(MODE, STAY);
        getNavigation().stop();
        updateRestriction();
    }

    public void release() {
        followTarget = null;
        entityData.set(MODE, FREE);
        releaseTicks = 0;
        updateRestriction();
    }

    public boolean isTalking() { return talkTicks > 0; }
    public void setTalking(boolean open) { talkTicks = open ? 60 : 0; }

    public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level)
    {
        super(type, level);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        getNavigation().setCanFloat(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CompanionGoals.Talk(this));
        goalSelector.addGoal(2, new CompanionGoals.Follow(this));
        goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        goalSelector.addGoal(4, new CompanionGoals.ReturnHome(this, 1.0));
        goalSelector.addGoal(5, new CompanionGoals.Stroll(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    private final Map<UUID, List<ChatLine>> histories = new HashMap<>();
    private boolean busy;

    public List<ChatLine> history(UUID player) {
        return histories.computeIfAbsent(player, k -> new ArrayList<>());
    }

    public void addHistory(UUID player, ChatLine l) {
        List<ChatLine> h = history(player);
        h.add(l);
        while (h.size() > Tuning.HISTORY_MAX) h.remove(0);
    }

    public boolean isBusy() { return busy; }
    public void setBusy(boolean b) { busy = b; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AVATAR, "");
        builder.define(SKIN, "");
        builder.define(SLIM, false);
        builder.define(MODE, FREE);
    }

    public String getAvatar() { return entityData.get(AVATAR); }
    public BlockPos getHome() { return home; }

    public void bind(String avatar, String name)
    {
        entityData.set(AVATAR, avatar);
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
        setPersistenceRequired();
        home = blockPosition();
        homeDim = level().dimension().location();
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString("Avatar", getAvatar());
        tag.putLong("Home", home.asLong());
        tag.putString("Skin", getSkinSource());
        tag.putBoolean("Slim", isSlim());
        tag.putInt("Mode", getMode());
        if (homeDim != null) tag.putString("HomeDim", homeDim.toString());
        if (followTarget != null) tag.putUUID("Follow", followTarget);

        CompoundTag chats = new CompoundTag();
        for (var e : histories.entrySet())
        {
            ListTag list = new ListTag();
            for (ChatLine l : e.getValue())
            {
                CompoundTag t = new CompoundTag();
                t.putInt("r", l.role());
                t.putString("t", l.text());

                if (!l.args().isEmpty())
                {
                    ListTag a = new ListTag();
                    for (String s : l.args()) a.add(StringTag.valueOf(s));
                    t.put("a", a);
                }
                list.add(t);
            }
            chats.put(e.getKey().toString(), list);
        }
        tag.put("Chats", chats);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        entityData.set(AVATAR, tag.getString("Avatar"));
        home = BlockPos.of(tag.getLong("Home"));
        entityData.set(SKIN, tag.getString("Skin"));
        entityData.set(SLIM, tag.getBoolean("Slim"));

        followTarget = tag.hasUUID("Follow") ? tag.getUUID("Follow") : null;
        entityData.set(MODE, tag.contains("Mode") ? tag.getInt("Mode") : (followTarget != null ? FOLLOW : FREE));
        if (tag.contains("HomeDim")) homeDim = ResourceLocation.parse(tag.getString("HomeDim"));

        histories.clear();
        CompoundTag chats = tag.getCompound("Chats");
        for (String key : chats.getAllKeys())
        {
            List<ChatLine> h = new ArrayList<>();
            for (Tag t : chats.getList(key, Tag.TAG_COMPOUND))
            {
                CompoundTag ct = (CompoundTag) t;
                ListTag a = ct.getList("a", Tag.TAG_STRING);
                List<String> args = new ArrayList<>();
                for (int i = 0; i < a.size(); i++) args.add(a.getString(i));
                h.add(new ChatLine(ct.getInt("r"), ct.getString("t"), args));
            }
            histories.put(UUID.fromString(key), h);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
            if (!level().isClientSide && player instanceof ServerPlayer sp) {
                ChatService.open(sp, this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    public void setHome(BlockPos pos)
    {
        home = pos;
        homeDim = level().dimension().location();
        updateRestriction();
    }

    private boolean isInHomeLevel() {
        return homeDim == null || homeDim.equals(level().dimension().location());
    }

    public Player followPlayer() {
        return followTarget == null ? null : level().getPlayerByUUID(followTarget);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || getAvatar().isEmpty()) return;
        if (talkTicks > 0) talkTicks--;
        if (tickCount % 20 == 0) updateRestriction();
        if (tickCount % 10 == 0) modeTick();
    }

    /** Идёт за игроком: гуляет только возле дома; следует: ограничение снято. */
    private void updateRestriction() {
        if (getMode() == FREE && isInHomeLevel()) {
            restrictTo(home, HOME_RADIUS);
        } else if (hasRestriction()) {
            clearRestriction();
        }
    }

    private void modeTick() {
        if (!(level() instanceof ServerLevel sl)) return;

        if (getMode() == FOLLOW) {
            if (followTarget == null) return;
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(followTarget);
            if (p == null) return; // игрок не в сети

            ChunkPos cp = chunkPosition();
            sl.getChunkSource().addRegionTicket(TICKET, cp, 3, cp);

            if (p.level() == level()) {
                otherLevelChecks = 0;
                double far = TELEPORT_DISTANCE;
                if (distanceToSqr(p) > far * far) teleportNear(p);
            } else if (++otherLevelChecks >= 4) {
                teleportNear(p);
            }
        } else if (getMode() == FREE && TELEPORT_HOME) {
            double far = HOME_TELEPORT_DISTANCE;
            boolean tooFar = !isInHomeLevel() || distanceToSqr(Vec3.atCenterOf(home)) > far * far;
            if (!tooFar) releaseTicks = 0;
            else if (++releaseTicks >= 6) teleportHome(sl); // ~3 секунды
        }
    }

    private void teleportHome(ServerLevel sl) {
        ResourceKey<Level> key = homeDim == null ? sl.dimension() : ResourceKey.create(Registries.DIMENSION, homeDim);
        ServerLevel target = sl.getServer().getLevel(key);
        if (target == null) return;
        releaseTicks = 0;
        getNavigation().stop();
        teleportTo(target, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, Set.of(), getYRot(), getXRot());
    }

    private void teleportNear(ServerPlayer p) {
        ServerLevel target = p.serverLevel();
        getNavigation().stop();
        for (int i = 0; i < 12; i++) {
            double x = p.getX() + getRandom().nextIntBetweenInclusive(-3, 3);
            double y = p.getY() + getRandom().nextIntBetweenInclusive(-1, 1);
            double z = p.getZ() + getRandom().nextIntBetweenInclusive(-3, 3);
            if (canStandAt(target, x, y, z)) {
                teleportTo(target, x, y, z, Set.of(), getYRot(), getXRot());
                return;
            }
        }
        teleportTo(target, p.getX(), p.getY(), p.getZ(), Set.of(), getYRot(), getXRot());
    }

    private boolean canStandAt(ServerLevel lvl, double x, double y, double z) {
        BlockPos below = BlockPos.containing(x, y, z).below();
        if (!lvl.getBlockState(below).isFaceSturdy(lvl, below, Direction.UP)) return false;
        return lvl.noCollision(this, getDimensions(getPose()).makeBoundingBox(x, y, z));
    }
}