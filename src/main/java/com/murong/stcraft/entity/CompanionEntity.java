package com.murong.stcraft.entity;

import com.murong.stcraft.Tuning;
import com.murong.stcraft.network.ChatLine;
import com.murong.stcraft.tavern.ChatService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.murong.stcraft.Tuning.*;

public class CompanionEntity extends PathfinderMob
{
    public enum Style { MELEE, BOW, CROSSBOW, TRIDENT, THROWN }
    public static final int FREE = 0, STAY = 1, FOLLOW = 2;
    private static final EntityDataAccessor<String> AVATAR = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHILD = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOOTING = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    public static final TicketType<ChunkPos> TICKET = TicketType.create("stcraft_companion", Comparator.comparingLong(ChunkPos::toLong), 40);
    private static final EntityDataAccessor<Boolean> INVULN = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ATTACK_MOBS = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos home = BlockPos.ZERO;
    private ResourceLocation homeDim;

    private BlockPos spot = BlockPos.ZERO;
    private ResourceLocation spotDim;
    private int goHomeTicks;

    private int talkTicks;
    private int otherLevelChecks;
    private int hurtCooldown;

    public String getSkinSource() { return entityData.get(SKIN); }
    public void setSkinSource(String s) { entityData.set(SKIN, s); }
    public boolean isSlim() { return entityData.get(SLIM); }
    public void setSlim(boolean slim) { entityData.set(SLIM, slim); }
    public void setChild(boolean child) { entityData.set(CHILD, child); }

    @Override
    public boolean isBaby() { return entityData.get(CHILD); }

    private UUID followTarget;

    public int getMode() { return entityData.get(MODE); }
    public boolean isFollowing() { return getMode() == FOLLOW; }
    public boolean isStaying() { return getMode() == STAY; }
    public boolean isFree() { return getMode() == FREE; }
    public UUID getFollowTarget() { return followTarget; }

    public boolean isFriendlyInvulnerable() { return entityData.get(INVULN); }
    public void setFriendlyInvulnerable(boolean b) { entityData.set(INVULN, b); }

    public boolean isAttackingMobs() { return entityData.get(ATTACK_MOBS); }
    public void setAttackMobs(boolean b) { entityData.set(ATTACK_MOBS, b); }

    public boolean isShooting() { return entityData.get(SHOOTING); }
    public void setShooting(boolean b) { entityData.set(SHOOTING, b); }

    private boolean isInSpotLevel()
    {
        return spotDim == null || spotDim.equals(level().dimension().location());
    }

    /** По просьбе игрока: через 3 секунды телепортируется домой. */
    public void goHome()
    {
        followTarget = null;
        entityData.set(MODE, FREE);
        goHomeTicks = 60;
        updateRestriction();
    }

    public void follow(UUID player) {
        followTarget = player;
        entityData.set(MODE, FOLLOW);
        goHomeTicks = 0;
        setTarget(null);
        updateRestriction();
    }

    public void stay() {
        followTarget = null;
        entityData.set(MODE, STAY);
        goHomeTicks = 0;
        setTarget(null);
        getNavigation().stop();
        updateRestriction();
    }

    public void release() {
        followTarget = null;
        entityData.set(MODE, FREE);
        goHomeTicks = 0;
        spot = blockPosition();
        spotDim = level().dimension().location();
        updateRestriction();
    }

    public boolean isTalking() { return talkTicks > 0; }
    public void setTalking(boolean open) { talkTicks = open ? 60 : 0; }

    public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level)
    {
        super(type, level);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        getNavigation().setCanFloat(true);
        setPathfindingMalus(PathType.LAVA, -1.0F);          // лава непроходима
        setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);   // огонь, костёр
        setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);   // рядом с огнём
        setPathfindingMalus(PathType.DAMAGE_OTHER, 16.0F);  // кактус, ягодные кусты
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /*
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CompanionGoals.Talk(this));
        goalSelector.addGoal(2, new CompanionGoals.Fetch(this));
        goalSelector.addGoal(3, new CompanionGoals.Follow(this));
        goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new CompanionGoals.ReturnHome(this, 1.0));
        goalSelector.addGoal(6, new CompanionGoals.Stroll(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }*/

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new CompanionGoals.Escape(this, 1.4));
        goalSelector.addGoal(1, new CompanionGoals.Ranged(this));
        goalSelector.addGoal(2, new CompanionGoals.Melee(this));
        goalSelector.addGoal(3, new CompanionGoals.Talk(this));
        goalSelector.addGoal(4, new CompanionGoals.Fetch(this));
        goalSelector.addGoal(5, new CompanionGoals.Follow(this));
        goalSelector.addGoal(6, new OpenDoorGoal(this, true));
        goalSelector.addGoal(7, new CompanionGoals.ReturnHome(this, 1.0));
        goalSelector.addGoal(8, new CompanionGoals.Stroll(this, 0.8));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, Player.class, CompanionEntity.class));
        targetSelector.addGoal(2, new CompanionGoals.DefendFriends(this));
        targetSelector.addGoal(3, new CompanionGoals.AttackHostiles(this));
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
        builder.define(CHILD, false);
        builder.define(MODE, FREE);
        builder.define(INVULN, false);
        builder.define(ATTACK_MOBS, false);
        builder.define(SHOOTING, false);
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
        spot = home;
        spotDim = homeDim;
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
        if (homeDim != null) tag.putString("homeDim", homeDim.toString());
        tag.putLong("Spot", spot.asLong());
        if (spotDim != null) tag.putString("SpotDim", spotDim.toString());
        tag.putString("Skin", getSkinSource());
        tag.putBoolean("Slim", isSlim());
        tag.putBoolean("Child", isBaby());
        tag.putInt("Mode", getMode());
        tag.putBoolean("Invuln", isFriendlyInvulnerable());
        tag.putBoolean("AttackMobs", isAttackingMobs());
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
        homeDim = tag.contains("homeDim") ? ResourceLocation.parse(tag.getString("homeDim")) : homeDim;
        spot = tag.contains("Spot") ? BlockPos.of(tag.getLong("Spot")) : home;
        spotDim = tag.contains("SpotDim") ? ResourceLocation.parse(tag.getString("SpotDim")) : homeDim;
        entityData.set(SKIN, tag.getString("Skin"));
        entityData.set(SLIM, tag.getBoolean("Slim"));
        entityData.set(CHILD, tag.getBoolean("Child"));

        followTarget = tag.hasUUID("Follow") ? tag.getUUID("Follow") : null;
        entityData.set(MODE, tag.contains("Mode") ? tag.getInt("Mode") : (followTarget != null ? FOLLOW : FREE));
        entityData.set(INVULN, tag.getBoolean("Invuln"));
        entityData.set(ATTACK_MOBS, tag.getBoolean("AttackMobs"));

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
        if (player.getItemInHand(hand).isEmpty()) {
            if (player.isShiftKeyDown()) {
                if (canEdit(player)) {
                    if (!level().isClientSide) takeAll(player);
                    return InteractionResult.sidedSuccess(level().isClientSide);
                }
            } else {
                if (!level().isClientSide && player instanceof ServerPlayer sp) ChatService.open(sp, this);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    public void setHome(BlockPos pos) {
        home = pos;
        homeDim = level().dimension().location();
        spot = pos;
        spotDim = homeDim;
        updateRestriction();
    }

    public Player followPlayer() {
        return followTarget == null ? null : level().getPlayerByUUID(followTarget);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || getAvatar().isEmpty()) return;
        if (talkTicks > 0) talkTicks--;
        if (hurtCooldown > 0) hurtCooldown--;
        else if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
        if (tickCount % 20 == 0) updateRestriction();
        if (tickCount % 10 == 0) modeTick();
        if (tickCount % 5 == 0) scanForGifts();
    }

    /** Идёт за игроком: гуляет только возле дома; следует: ограничение снято. */
    private void updateRestriction() {
        if (getMode() == FREE && isInSpotLevel() && getTarget() == null) {
            restrictTo(spot, HOME_RADIUS);
        } else if (hasRestriction()) {
            clearRestriction();
        }
    }

    @Override
    public void setTarget(LivingEntity target)
    {
        super.setTarget(target);
        if (target != null && hasRestriction()) clearRestriction(); // в бою радиус дома не действует
    }

    private void modeTick() {
        if (!(level() instanceof ServerLevel sl)) return;

        if (goHomeTicks > 0) { // отправлен домой по просьбе игрока
            goHomeTicks -= 10;
            if (goHomeTicks <= 0) teleportHome(sl);
            return;
        }

        LivingEntity tgt = getTarget();
        if (tgt != null) {
            if (isStaying() && distanceToSqr(tgt) > 36.0) {
                setTarget(null);
            } else if (isFollowing()) {
                Player fp = followPlayer();
                double leash = COMBAT_LEASH;
                if (fp != null && distanceToSqr(fp) > leash * leash) setTarget(null);
            } else if (isFree() && isInSpotLevel()) {
                double leash = COMBAT_LEASH;
                if (distanceToSqr(Vec3.atCenterOf(spot)) > leash * leash) setTarget(null);
            }
        }

        if (getMode() != FOLLOW || followTarget == null) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(followTarget);
        if (p == null) return; // игрок не в сети

        ChunkPos cp = chunkPosition();
        sl.getChunkSource().addRegionTicket(TICKET, cp, 3, cp);

        if (p.level() == level()) {
            otherLevelChecks = 0;
            double far = TELEPORT_DISTANCE;
            if (getTarget() != null) far = Math.max(far, COMBAT_LEASH + 4);
            if (distanceToSqr(p) > far * far) teleportNear(p);
        } else if (++otherLevelChecks >= 4) {
            teleportNear(p);
        }
    }

    private void teleportHome(ServerLevel sl)
    {
        ResourceKey<Level> key = homeDim == null ? sl.dimension() : ResourceKey.create(Registries.DIMENSION, homeDim);
        ServerLevel target = sl.getServer().getLevel(key);
        if (target == null) return;
        spot = home;                 // новая точка стоянки: дом (до телепорта, чтобы сохранилось в данных)
        spotDim = homeDim;
        setTarget(null);
        getNavigation().stop();
        target.getChunkAt(home);     // подгрузить чанк дома, если игрока там нет
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

        BlockPos pos = BlockPos.containing(x, y, z);
        if (!lvl.getFluidState(pos).isEmpty() || lvl.getBlockState(pos).is(BlockTags.FIRE)
                || lvl.getBlockState(below).is(Blocks.MAGMA_BLOCK)) return false;

        return lvl.noCollision(this, getDimensions(getPose()).makeBoundingBox(x, y, z));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        if (CHILD.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    public static boolean canEdit(Player p)
    {
        return p.isCreative() || p.hasPermissions(2);
    }

    public static EquipmentSlot slotFor(ItemStack s)
    {
        if (s.isEmpty()) return null;

        EquipmentSlot es = s.getEquipmentSlot();      // NeoForge: предметы модов задают слот сами
        if (es == null) {
            Equipable eq = Equipable.get(s);          // ваниль: броня, вырезанная тыква, головы
            if (eq != null) es = eq.getEquipmentSlot();
        }

        if ((es == EquipmentSlot.HEAD || es == EquipmentSlot.CHEST || es == EquipmentSlot.LEGS || es == EquipmentSlot.FEET) && !s.is(Items.ELYTRA))
        {
            return es;
        }

        if (s.getItem() instanceof ShieldItem) return EquipmentSlot.OFFHAND;
        if (isWeapon(s)) return EquipmentSlot.MAINHAND;
        return null;
    }

    private static boolean isWeapon(ItemStack s)
    {
        if (s.getItem() instanceof ProjectileWeaponItem) return true;
        for (var e : s.getAttributeModifiers().modifiers())
        {
            if (e.attribute().is(Attributes.ATTACK_DAMAGE) && e.slot() == EquipmentSlotGroup.MAINHAND) return true;
        }
        return false;
    }

    private boolean isGift(ItemEntity ie) {
        return !ie.isRemoved() && ie.getOwner() instanceof Player
                && slotFor(ie.getItem()) != null && closestToItem(ie);
    }

    private boolean closestToItem(ItemEntity ie) {
        double mine = distanceToSqr(ie);
        for (CompanionEntity o : level().getEntitiesOfClass(CompanionEntity.class, ie.getBoundingBox().inflate(8))) {
            if (o == this || o.isDeadOrDying()) continue;
            double d = o.distanceToSqr(ie);
            boolean canTake = !o.isStaying() || d < 2.25; // стоящий на месте берёт только то, что рядом
            if (canTake && d < mine) return false;
        }
        return true;
    }

    public ItemEntity findGift(double range)
    {
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity ie : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(range, 2, range)))
        {
            if (!isGift(ie)) continue;
            double d = distanceToSqr(ie);
            if (d < bestDist) { bestDist = d; best = ie; }
        }
        return best;
    }

    private void scanForGifts()
    {
        if (isDeadOrDying()) return;
        for (ItemEntity ie : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.5, 0.5, 1.5))) {
            if (ie.hasPickUpDelay() || !isGift(ie)) continue;
            if (receive(ie, (Player) ie.getOwner())) return; // по одной вещи за проход
        }
    }

    private boolean receive(ItemEntity ie, Player giver) {
        ItemStack stack = ie.getItem();
        EquipmentSlot slot = slotFor(stack);
        if (slot == null) return false;

        ItemStack one = stack.copyWithCount(1);
        ItemStack old = getItemBySlot(slot).copy();
        stack.shrink(1);
        if (stack.isEmpty()) ie.discard();

        setItemSlot(slot, one);
        setDropChance(slot, 1.0F); // при смерти выпадает всё, что дал игрок
        if (giver instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("stcraft.msg.equip", getName(), one.getHoverName()), true);
        }
        if (!old.isEmpty()) {
            dropToward(old, giver);
            if (giver instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("stcraft.msg.return", getName(), old.getHoverName()), false);
            }
        }
        return true;
    }

    private void dropToward(ItemStack stack, Player to) {
        ItemEntity drop = new ItemEntity(level(), getX(), getEyeY() - 0.3, getZ(), stack);
        Vec3 dir = to.position().subtract(position());
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize().scale(0.25);
        drop.setDeltaMovement(dir.x, 0.2, dir.z);
        drop.setPickUpDelay(20);
        level().addFreshEntity(drop);
    }

    /** Креатив и операторы: Shift+ПКМ пустой рукой. */
    private void takeAll(Player player) {
        boolean any = false;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = getItemBySlot(s);
            if (st.isEmpty()) continue;
            ItemStack out = st.copy();
            setItemSlot(s, ItemStack.EMPTY);
            if (!player.getInventory().add(out)) player.drop(out, false);
            any = true;
        }
        if (any) player.displayClientMessage(Component.translatable("stcraft.msg.took_all", getName()), true);
    }

    /** Просьба из чата: [GIVE:id]. */
    public boolean giveItem(ServerPlayer to, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return false;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = getItemBySlot(s);
            if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).equals(rl)) {
                ItemStack out = st.copy();
                setItemSlot(s, ItemStack.EMPTY);
                dropToward(out, to);
                to.displayClientMessage(Component.translatable("stcraft.msg.return", getName(), out.getHoverName()), false);
                return true;
            }
        }
        return false;
    }

    /** Что на нём и в руках, для запроса к модели. */
    public String describeGear() {
        StringBuilder sb = new StringBuilder();
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = getItemBySlot(s);
            if (st.isEmpty()) continue;
            String label = switch (s) {
                case HEAD -> "head";
                case CHEST -> "chest";
                case LEGS -> "legs";
                case FEET -> "feet";
                case MAINHAND -> "right hand";
                case OFFHAND -> "left hand";
                default -> s.getName();
            };
            sb.append(label).append(": ").append(BuiltInRegistries.ITEM.getKey(st.getItem()))
                    .append(" (").append(st.getHoverName().getString()).append("); ");
        }
        return sb.isEmpty() ? "nothing" : sb.toString().trim();
    }

    public Style weaponStyle() {
        Item item = getMainHandItem().getItem();
        if (CompanionRanged.get(item) != null) return Style.THROWN;
        if (item instanceof BowItem) return Style.BOW;
        if (item instanceof CrossbowItem) return Style.CROSSBOW;
        if (item instanceof TridentItem) return Style.TRIDENT;
        return Style.MELEE;
    }

    /** Стрелять (true) или драться вплотную (false) с этой целью. */
    public boolean shouldShoot(LivingEntity target) {
        return switch (weaponStyle()) {
            case BOW, CROSSBOW -> true;
            case TRIDENT, THROWN -> distanceToSqr(target) > 25.0;
            default -> false;
        };
    }

    public int shootInterval() {
        CompanionRanged.Entry reg = CompanionRanged.get(getMainHandItem().getItem());
        if (reg != null) return reg.interval();
        return switch (weaponStyle()) {
            case BOW -> 20;
            case CROSSBOW -> 35;
            default -> 40;
        };
    }

    public void shoot(LivingEntity target) {
        ItemStack held = getMainHandItem();
        CompanionRanged.Entry reg = CompanionRanged.get(held.getItem());
        Style style = weaponStyle();

        Projectile proj;
        float speed;
        float gravity = 0.05F;
        SoundEvent sound = SoundEvents.ARROW_SHOOT;
        if (reg != null) {
            proj = reg.factory().create(level(), this, held.copy());
            speed = reg.speed();
            gravity = reg.gravity();
        } else if (style == Style.TRIDENT) {
            proj = new ThrownTrident(level(), this, held.copy());
            speed = 2.5F;
        } else {
            AbstractArrow arrow = EntityType.ARROW.create(level());
            if (arrow == null) return;
            arrow.setOwner(this);
            proj = arrow;
            speed = style == Style.CROSSBOW ? 3.15F : 2.5F;
            if (style == Style.CROSSBOW) sound = SoundEvents.CROSSBOW_SHOOT;
        }

        // упреждение по скорости цели и поправка на падение за время полёта
        double ox = getX(), oy = getEyeY() - 0.1, oz = getZ();
        double dx = target.getX() - ox;
        double dy = target.getY(0.5) - oy;
        double dz = target.getZ() - oz;
        Vec3 v = target.getDeltaMovement();
        double t = Math.sqrt(dx * dx + dz * dz) / speed;
        dx += v.x * t;
        dz += v.z * t;
        t = Math.sqrt(dx * dx + dz * dz) / speed;
        dy += gravity * 0.5 * t * (t + 1);

        proj.setPos(ox, oy, oz);
        if (proj instanceof AbstractArrow a) a.pickup = AbstractArrow.Pickup.DISALLOWED;
        proj.shoot(dx, dy, dz, speed, 1.5F);
        playSound(sound, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(proj);
        swing(InteractionHand.MAIN_HAND);
    }

    /** Кого он защищает: игрок, за которым идёт, и игроки из его команды рядом. */
    public List<Player> friends() {
        List<Player> out = new ArrayList<>();
        Player f = followPlayer();
        if (f != null) out.add(f);
        if (getTeam() != null) {
            for (Player p : level().players()) {
                if (p != f && p.isAlliedTo(this) && distanceToSqr(p) < 1024.0) out.add(p);
            }
        }
        return out;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof Player || target instanceof CompanionEntity) return false;
        if (target instanceof OwnableEntity o && o.getOwnerUUID() != null) return false; // питомцы
        return super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFriendlyInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Entity attacker = source.getEntity();
            if (attacker instanceof Player || attacker instanceof CompanionEntity) return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) hurtCooldown = REGEN_DELAY_TICKS * 20;
        return hurt;
    }
}