package com.murong.stcraft.entity;

import com.murong.stcraft.Tuning;
import com.murong.stcraft.network.ChatLine;
import com.murong.stcraft.tavern.ChatService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;

public class CompanionEntity extends PathfinderMob
{
    private static final EntityDataAccessor<String> AVATAR = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FOLLOWING = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos home = BlockPos.ZERO;

    public String getSkinSource() { return entityData.get(SKIN); }
    public void setSkinSource(String s) { entityData.set(SKIN, s); }
    public boolean isSlim() { return entityData.get(SLIM); }
    public void setSlim(boolean slim) { entityData.set(SLIM, slim); }

    private UUID followTarget;

    public boolean isFollowing() { return entityData.get(FOLLOWING); }
    public UUID getFollowTarget() { return followTarget; }

    public void setFollowTarget(UUID id)
    {
        followTarget = id;
        entityData.set(FOLLOWING, id != null);
    }

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
    protected void registerGoals()
    {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 8.0F));
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
        builder.define(FOLLOWING, false);
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
        setFollowTarget(tag.hasUUID("Follow") ? tag.getUUID("Follow") : null);

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
}