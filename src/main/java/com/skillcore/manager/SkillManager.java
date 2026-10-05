package com.skillcore.manager;

import com.skillcore.api.Skill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.factory.SkillRegistry;
import com.skillcore.utils.CooldownUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.SoundUtils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level skill casting manager: validation, cooldown, mana, dispatch.
 */
public final class SkillManager {

    private final SkillRegistry registry;
    /** Simple mana store: uuid -> mana */
    private final Map<UUID, Double> mana = new ConcurrentHashMap<>();
    /** Simple max mana store */
    private final Map<UUID, Double> maxMana = new ConcurrentHashMap<>();

    private boolean debug;

    public SkillManager(SkillRegistry registry) {
        this.registry = registry;
    }

    public SkillRegistry getRegistry() {
        return registry;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Cast a skill by id with context.
     */
    public SkillResult cast(String skillId, SkillContext context) {
        Skill skill = registry.getOrNull(skillId);
        if (skill == null) {
            return SkillResult.FAILED;
        }
        return cast(skill, context);
    }

    /**
     * Full cast pipeline.
     */
    public SkillResult cast(Skill skill, SkillContext context) {
        if (skill == null || context == null) {
            return SkillResult.FAILED;
        }
        LivingEntity caster = context.getCaster();
        if (!DamageUtils.isAlive(caster)) {
            return SkillResult.FAILED;
        }

        // permission
        if (skill.getPermission() != null && !skill.getPermission().isEmpty()) {
            if (caster instanceof Player player && !player.hasPermission(skill.getPermission())) {
                return SkillResult.NO_PERMISSION;
            }
        }

        // cooldown (players only by default)
        if (caster instanceof Player player) {
            if (!player.hasPermission("skillcore.bypass.cooldown")
                    && CooldownUtils.isOnCooldown(player.getUniqueId(), skill.getId())) {
                SoundUtils.cooldown(player);
                return SkillResult.ON_COOLDOWN;
            }

            // mana
            double cost = skill.getManaCost();
            if (cost > 0) {
                double current = getMana(player);
                if (current < cost) {
                    SoundUtils.castFail(player);
                    return SkillResult.NO_MANA;
                }
            }
        }

        // skill-level validation
        if (!skill.canCast(context)) {
            return SkillResult.FAILED;
        }

        SkillResult result = skill.cast(context);

        if (result.isSuccess()) {
            if (caster instanceof Player player) {
                // consume mana + start cooldown
                double cost = skill.getManaCost();
                if (cost > 0) {
                    setMana(player, Math.max(0, getMana(player) - cost));
                }
                if (!player.hasPermission("skillcore.bypass.cooldown") && skill.getCooldown() > 0) {
                    CooldownUtils.start(player.getUniqueId(), skill.getId(), skill.getCooldown());
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Mana system (simple built-in resource)
    // ------------------------------------------------------------------

    public double getMana(Player player) {
        return mana.getOrDefault(player.getUniqueId(), getMaxMana(player));
    }

    public void setMana(Player player, double value) {
        mana.put(player.getUniqueId(), Math.max(0, Math.min(getMaxMana(player), value)));
    }

    public double getMaxMana(Player player) {
        return maxMana.getOrDefault(player.getUniqueId(), 100.0);
    }

    public void setMaxMana(Player player, double value) {
        maxMana.put(player.getUniqueId(), Math.max(1, value));
        if (getMana(player) > value) {
            setMana(player, value);
        }
    }

    public void addMana(Player player, double amount) {
        setMana(player, getMana(player) + amount);
    }

    public boolean hasMana(Player player, double amount) {
        return getMana(player) >= amount;
    }

    // ------------------------------------------------------------------
    // Cooldown access
    // ------------------------------------------------------------------

    public double getCooldownRemaining(Player player, String skillId) {
        return CooldownUtils.getRemaining(player.getUniqueId(), skillId);
    }

    public void clearCooldown(Player player, String skillId) {
        CooldownUtils.clear(player.getUniqueId(), skillId);
    }

    public void clearAllCooldowns(Player player) {
        CooldownUtils.clearAll(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Convenience cast helpers
    // ------------------------------------------------------------------

    /**
     * Cast with a target only.
     */
    public SkillResult castOn(Player caster, LivingEntity target, String skillId) {
        SkillContext context = SkillContext.builder(caster)
                .target(target)
                .itemInHand(caster.getInventory().getItemInMainHand())
                .build();
        return cast(skillId, context);
    }

    public SkillResult castAt(Player caster, org.bukkit.Location location, String skillId) {
        SkillContext context = SkillContext.builder(caster)
                .targetLocation(location)
                .itemInHand(caster.getInventory().getItemInMainHand())
                .build();
        return cast(skillId, context);
    }
}
