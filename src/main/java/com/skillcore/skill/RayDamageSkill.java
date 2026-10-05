package com.skillcore.skill;

import com.skillcore.api.AbstractSkill;
import com.skillcore.api.ActiveSkill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.model.SkillDefinition;
import com.skillcore.utils.AimUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.ParticleUtils;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 瞄准技能 — 射线命中并造成伤害.
 */
public class RayDamageSkill extends AbstractSkill implements ActiveSkill {

    public RayDamageSkill(SkillDefinition definition) {
        super(definition);
    }

    @Override
    public SkillResult cast(SkillContext context) {
        LivingEntity target = context.hasTarget()
                ? context.getTarget()
                : aimTarget(context, definition.getExtraDouble("ray-distance", 16.0));

        if (target == null) {
            return SkillResult.NO_TARGET;
        }

        double dealt = dealDamage(context.retarget(target));
        applyLifesteal(context, dealt);

        // visual line
        if (context.getCaster() instanceof Player player) {
            ParticleUtils.line(player.getEyeLocation(), target.getEyeLocation(), Particle.CRIT, 0.4);
        }
        ParticleUtils.hitMarker(target, Particle.CRIT);
        return SkillResult.SUCCESS;
    }
}
