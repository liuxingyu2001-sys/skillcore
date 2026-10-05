package com.skillcore.api;

import com.skillcore.model.SkillDefinition;
import com.skillcore.utils.AimUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.LifestealUtils;
import com.skillcore.utils.PercentageDamageUtils;
import com.skillcore.utils.PotionUtils;
import com.skillcore.utils.ReflectDamageUtils;

/**
 * Shared abstract base for custom skills. Extend and override {@link #cast}.
 */
public abstract class AbstractSkill implements Skill {

    protected final SkillDefinition definition;

    protected AbstractSkill(SkillDefinition definition) {
        this.definition = definition;
    }

    public SkillDefinition getDefinition() {
        return definition;
    }

    @Override
    public String getId() {
        return definition.getId();
    }

    @Override
    public String getDisplayName() {
        return definition.getDisplayName();
    }

    @Override
    public com.skillcore.api.SkillType getType() {
        return definition.getType();
    }

    @Override
    public com.skillcore.api.SkillTrigger getTrigger() {
        return definition.getTrigger();
    }

    @Override
    public double getCooldown() {
        return definition.getCooldown();
    }

    @Override
    public double getManaCost() {
        return definition.getManaCost();
    }

    @Override
    public String getPermission() {
        return definition.getPermission();
    }

    @Override
    public String getDescription() {
        return definition.getDescription();
    }

    @Override
    public boolean canCast(SkillContext context) {
        return context != null && DamageUtils.isAlive(context.getCaster());
    }

    // ------------------------------------------------------------------
    // Shared effect helpers so concrete skills stay short
    // ------------------------------------------------------------------

    protected double dealDamage(SkillContext context, double base, double scaling) {
        if (!context.hasTarget()) {
            return 0;
        }
        var dmg = definition.getDamage();
        double damage = DamageUtils.calculateFinalDamage(
                base, scaling, context.getPower(),
                dmg.criticalChance(), dmg.criticalMultiplier(),
                dmg.armorPenetration(), context.getTarget()
        );
        if (damage > 0) {
            DamageUtils.damage(context.getTarget(), damage, context.getCaster());
        }
        return damage;
    }

    protected double dealDamage(SkillContext context) {
        var dmg = definition.getDamage();
        double damage = DamageUtils.calculateFinalDamage(
                dmg.base(), dmg.scaling(), context.getPower(),
                dmg.criticalChance(), dmg.criticalMultiplier(),
                dmg.armorPenetration(), context.hasTarget() ? context.getTarget() : null
        );
        if (context.hasTarget()) {
            if (dmg.percentMaxHealth() > 0) {
                damage += PercentageDamageUtils.ofMaxHealthCapped(
                        context.getTarget(), dmg.percentMaxHealth(), dmg.maxPercentDamage());
            }
            if (dmg.percentCurrentHealth() > 0) {
                damage += PercentageDamageUtils.ofCurrentHealth(context.getTarget(), dmg.percentCurrentHealth());
            }
            if (dmg.percentMissingHealth() > 0) {
                damage += PercentageDamageUtils.ofMissingHealth(context.getTarget(), dmg.percentMissingHealth());
            }
            if (damage > 0) {
                DamageUtils.damage(context.getTarget(), damage, context.getCaster());
            }
        }
        return damage;
    }

    protected void applyLifesteal(SkillContext context, double damageDealt) {
        double percent = definition.getExtraDouble("lifesteal", 0);
        if (percent > 0) {
            LifestealUtils.healByDamagePercent(context.getCaster(), damageDealt, percent);
        }
    }

    protected void dash(SkillContext context, double speed, double y) {
        DisplacementUtils.dash(context.getCaster(), speed, y);
        DisplacementUtils.playDashEffect(context.getCaster().getLocation());
    }

    protected void blinkForward(SkillContext context, double distance) {
        DisplacementUtils.blinkForward(context.getCaster(), distance);
    }

    protected org.bukkit.entity.LivingEntity aimTarget(SkillContext context, double maxDistance) {
        if (context.hasTarget()) {
            return context.getTarget();
        }
        if (context.getCaster() instanceof org.bukkit.entity.Player player) {
            return AimUtils.raycastEntity(player, maxDistance);
        }
        return AimUtils.getNearest(context.getCaster(), maxDistance);
    }

    protected void applyReflectBuff(SkillContext context, double percent, double flat, long ticks) {
        ReflectDamageUtils.addReflectBuff(context.getCaster(), percent, flat, ticks);
    }

    protected void applyStun(SkillContext context, int ticks) {
        if (context.hasTarget()) {
            PotionUtils.stun(context.getTarget(), ticks);
        }
    }
}
