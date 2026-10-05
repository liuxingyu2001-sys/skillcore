package com.skillcore.skill;

import com.skillcore.SkillCorePlugin;
import com.skillcore.api.PassiveSkill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.model.SkillDefinition;
import com.skillcore.utils.ReflectDamageUtils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 反伤被动 — 受击时反弹伤害.
 */
public class ThornsPassiveSkill extends com.skillcore.api.AbstractSkill implements PassiveSkill {

    public ThornsPassiveSkill(SkillDefinition definition) {
        super(definition);
    }

    @Override
    public Class<? extends Event> getHandledEvent() {
        return EntityDamageByEntityEvent.class;
    }

    @Override
    public void onEvent(Event event, SkillContext context) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)) {
            return;
        }
        LivingEntity victim = damageEvent.getEntity() instanceof LivingEntity living ? living : null;
        if (victim == null) {
            return;
        }
        double percent = definition.getExtraDouble("reflect-percent", 0.20);
        double flat = definition.getExtraDouble("reflect-flat", 0);
        ReflectDamageUtils.reflectFromEvent(damageEvent, victim, percent, flat);
    }

    @Override
    public boolean canCast(SkillContext context) {
        return true;
    }

    @Override
    public SkillResult cast(SkillContext context) {
        // passive: casting is a no-op; effect is event-driven
        if (context != null && context.getCaster() != null) {
            double percent = definition.getExtraDouble("reflect-percent", 0.20);
            double flat = definition.getExtraDouble("reflect-flat", 0);
            long ticks = (long) definition.getExtraDouble("duration-ticks", 200);
            ReflectDamageUtils.addReflectBuff(context.getCaster(), percent, flat, ticks);
        }
        return SkillResult.SUCCESS;
    }
}
