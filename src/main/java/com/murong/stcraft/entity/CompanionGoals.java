package com.murong.stcraft.entity;

import com.murong.stcraft.Config;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.murong.stcraft.Tuning.*;

public final class CompanionGoals {
    private CompanionGoals() {}

    private static double sq(double d) { return d * d; }

    /** Пока открыт чат, стоит и смотрит на игрока. */
    public static class Talk extends Goal {
        private final CompanionEntity c;

        public Talk(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        //@Override public boolean canUse() { return c.isTalking(); }
        //@Override public boolean canContinueToUse() { return c.isTalking(); }
        @Override public boolean canUse() { return c.isTalking() && c.getTarget() == null; }
        @Override public boolean canContinueToUse() { return c.isTalking() && c.getTarget() == null; }
        @Override public void start() { c.getNavigation().stop(); }

        @Override
        public void tick() {
            Player p = c.level().getNearestPlayer(c, 8);
            if (p != null) c.getLookControl().setLookAt(p, 30.0F, 30.0F);
        }
    }

    /** Идёт за игроком как приручённый волк: трогается дальше 5, подходит до 2.5, бежит дальше 10. */
    public static class Follow extends Goal {
        private final CompanionEntity c;
        private Player target;
        private int recalc;

        public Follow(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!c.isFollowing() || c.isTalking()) return false;
            Player p = c.followPlayer();
            if (p == null || p.isSpectator()) return false;
            if (c.distanceToSqr(p) < sq(FOLLOW_START)) return false;
            target = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (!c.isFollowing() || c.isTalking() || target == null || !target.isAlive()) return false;
            double stop = Math.min(FOLLOW_STOP, FOLLOW_START);
            return c.distanceToSqr(target) > sq(stop);
        }

        @Override public void start() { recalc = 0; }

        @Override
        public void stop() {
            target = null;
            c.getNavigation().stop();
        }

        @Override
        public void tick() {
            c.getLookControl().setLookAt(target, 10.0F, c.getMaxHeadXRot());
            if (--recalc <= 0) {
                recalc = adjustedTickDelay(10);
                double speed = c.distanceToSqr(target) > sq(RUN_DISTANCE) ? 1.4 : 1.0;
                c.getNavigation().moveTo(target, speed);
            }
        }
    }

    /** Гуляет только когда не идёт за игроком и не разговаривает. */
    public static class Stroll extends WaterAvoidingRandomStrollGoal
    {
        private final CompanionEntity c;

        public Stroll(CompanionEntity c, double speed)
        {
            super(c, speed);
            this.c = c;
        }

        @Override public boolean canUse() { return c.isFree() && !c.isTalking() && super.canUse(); }
        @Override public boolean canContinueToUse() { return c.isFree() && !c.isTalking() && super.canContinueToUse(); }
    }

    /** Возвращается к дому только в режиме FREE. */
    public static class ReturnHome extends MoveTowardsRestrictionGoal
    {
        private final CompanionEntity c;

        public ReturnHome(CompanionEntity c, double speed)
        {
            super(c, speed);
            this.c = c;
        }

        @Override public boolean canUse() { return c.isFree() && !c.isTalking() && c.getTarget() == null && super.canUse(); }
    }

    /** Подходит к вещи, которую бросил игрок (режимы FREE и FOLLOW; в STAY стоит на месте). */
    public static class Fetch extends Goal {
        private final CompanionEntity c;
        private ItemEntity target;
        private int recalc;

        public Fetch(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            double range = GIFT_RANGE;
            if (range <= 0 || c.isStaying() || c.isTalking()) return false;
            target = c.findGift(range);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && !target.isRemoved() && !c.isStaying() && !c.isTalking()
                    && c.distanceToSqr(target) < sq(GIFT_RANGE + 2);
        }

        @Override public void start() { recalc = 0; }

        @Override
        public void stop() {
            target = null;
            c.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (--recalc <= 0) {
                recalc = adjustedTickDelay(10);
                c.getNavigation().moveTo(target, 1.1);
            }
        }
    }

    /** Ближний бой любым оружием, трезубец вплотную. */
    public static class Melee extends MeleeAttackGoal {
        private final CompanionEntity c;

        public Melee(CompanionEntity c) {
            super(c, 1.2, true);
            this.c = c;
        }

        private boolean applies() {
            LivingEntity t = c.getTarget();
            return t != null && !c.shouldShoot(t);
        }

        @Override public boolean canUse() { return applies() && super.canUse(); }
        @Override public boolean canContinueToUse() { return applies() && super.canContinueToUse(); }
    }

    /** Лук, арбалет, трезубец издали: держит дистанцию и стреляет по таймеру. */
    public static class Ranged extends Goal {
        private final CompanionEntity c;
        private int cooldown;
        private int recalc;

        public Ranged(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean applies() {
            LivingEntity t = c.getTarget();
            return t != null && t.isAlive() && c.shouldShoot(t);
        }

        @Override public boolean canUse() { return applies(); }
        @Override public boolean canContinueToUse() { return applies(); }

        @Override
        public void start() {
            cooldown = 10;
            recalc = 0;
            c.setShooting(true);
        }

        @Override
        public void stop() {
            c.setShooting(false);
            c.stopUsingItem();
            c.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = c.getTarget();
            if (t == null) return;
            double d2 = c.distanceToSqr(t);
            boolean sees = c.getSensing().hasLineOfSight(t);
            c.getLookControl().setLookAt(t, 30.0F, 30.0F);

            if (c.weaponStyle() == CompanionEntity.Style.BOW && cooldown <= 20 && !c.isUsingItem())
            {
                c.startUsingItem(InteractionHand.MAIN_HAND);
            }

            if (!c.isStaying() && --recalc <= 0) {
                recalc = 10;
                if (d2 < 25.0) { // слишком близко: отступаем
                    Vec3 away = DefaultRandomPos.getPosAway(c, 8, 4, t.position());
                    if (away != null) c.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                } else if (!sees || d2 > 18.0 * 18.0) {
                    c.getNavigation().moveTo(t, 1.0);
                } else {
                    c.getNavigation().stop();
                }
            }
            if (--cooldown <= 0 && sees && d2 < 24.0 * 24.0) {
                c.shoot(t);
                c.stopUsingItem();
                cooldown = c.shootInterval();
            }
        }
    }

    /** Защищает друзей: кто их ударил и кого они ударили (если враждебный). */
    public static class DefendFriends extends TargetGoal {
        private final CompanionEntity c;
        private final Map<UUID, Integer> seenBy = new HashMap<>();
        private final Map<UUID, Integer> seenHit = new HashMap<>();
        private LivingEntity candidate;

        public DefendFriends(CompanionEntity c) {
            super(c, false);
            this.c = c;
        }

        @Override
        public boolean canUse() {
            for (Player f : c.friends()) {
                LivingEntity attacker = f.getLastHurtByMob();
                int ts = f.getLastHurtByMobTimestamp();
                if (attacker != null && ts != seenBy.getOrDefault(f.getUUID(), -1)) {
                    seenBy.put(f.getUUID(), ts);
                    if (canAttack(attacker, TargetingConditions.DEFAULT)) {
                        candidate = attacker;
                        return true;
                    }
                }
                LivingEntity victim = f.getLastHurtMob();
                int ts2 = f.getLastHurtMobTimestamp();
                if (victim instanceof Enemy && ts2 != seenHit.getOrDefault(f.getUUID(), -1)) {
                    seenHit.put(f.getUUID(), ts2);
                    if (canAttack(victim, TargetingConditions.DEFAULT)) {
                        candidate = victim;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void start() {
            c.setTarget(candidate);
            super.start();
        }
    }

    /** Сам нападает на враждебных мобов рядом (кроме крипера, эндермена, пиглинов). */
    public static class AttackHostiles extends NearestAttackableTargetGoal<Mob> {
        private final CompanionEntity c;

        public AttackHostiles(CompanionEntity c) {
            super(c, Mob.class, 10, true, false, CompanionGoals::isHostileTarget);
            this.c = c;
        }

        @Override public boolean canUse() { return c.isAttackingMobs() && !c.isStaying() && super.canUse(); }
        @Override public boolean canContinueToUse() { return c.isAttackingMobs() && super.canContinueToUse(); }
    }

    private static boolean isHostileTarget(LivingEntity e)
    {
        return e instanceof Enemy && !(e instanceof Bat) && !(e instanceof Creeper) && !(e instanceof EnderMan) && !(e instanceof ZombifiedPiglin) && !(e instanceof AbstractPiglin);
    }

    /** Горит или замерзает: бежит к воде или прочь. */
    public static class Escape extends PanicGoal {
        public Escape(PathfinderMob mob, double speed) { super(mob, speed); }
        @Override protected boolean shouldPanic() { return mob.isOnFire() || mob.isFreezing(); }
    }
}