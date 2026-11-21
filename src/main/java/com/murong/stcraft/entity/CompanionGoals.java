package com.murong.stcraft.entity;

import com.murong.stcraft.Config;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

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

        @Override public boolean canUse() { return c.isTalking(); }
        @Override public boolean canContinueToUse() { return c.isTalking(); }
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

        @Override public boolean canUse() { return c.isFree() && !c.isTalking() && super.canUse(); }
    }
}