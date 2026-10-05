package com.skillcore.skill;

import com.skillcore.api.AbstractSkill;
import com.skillcore.api.ActiveSkill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.model.SkillDefinition;
import com.skillcore.utils.AimUtils;
import com.skillcore.utils.AreaUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.LifestealUtils;
import com.skillcore.utils.MathUtils;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.PercentageDamageUtils;
import com.skillcore.utils.PotionUtils;
import com.skillcore.utils.SoundUtils;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * 示例技能：展示如何用工具类快速写新技能。
 * <pre>
 * // 在 SkillCorePlugin.registerCustomSkills() 中注册：
 * skillFactory.registerType("SKILL_DEMO", SkillDemo::new);
 *
 * // skills.yml:
 * skill_demo:
 *   type: ACTIVE
 *   trigger: RIGHT_CLICK
 *   skill-type: SKILL_DEMO
 *   cooldown: 6.0
 *   mana-cost: 20
 *   damage:
 *     base: 10.0
 *     scaling: 1.5
 * </pre>
 */
public class SkillDemo extends AbstractSkill implements ActiveSkill {

    public SkillDemo(SkillDefinition definition) {
        super(definition);
    }

    @Override
    public SkillResult cast(SkillContext context) {
        // 1. 瞄准：优先上下文目标，否则射线拾取
        LivingEntity target = aimTarget(context, 12.0);
        if (target == null) {
            return SkillResult.NO_TARGET;
        }

        // 2. 伤害计算（暴击 + 护甲 + 护甲穿透）
        double dealt = dealDamage(context.retarget(target));

        // 3. 百分比伤害补充
        double bonus = PercentageDamageUtils.ofMaxHealthCapped(target, 0.05, 20);
        if (bonus > 0) {
            DamageUtils.damage(target, bonus, context.getCaster());
        }

        // 4. 吸血
        LifestealUtils.healByDamagePercent(context.getCaster(), dealt + bonus, 0.15);

        // 5. AOE：对周围敌人造成 40% 溅射
        List<LivingEntity> nearby = AreaUtils.getSphere(target.getLocation(), 3.5,
                e -> !e.equals(context.getCaster()) && !e.equals(target));
        for (LivingEntity splash : nearby) {
            DamageUtils.damage(splash, dealt * 0.4, context.getCaster());
        }

        // 6. 控制：减速
        PotionUtils.slowness(target, 60, 2);

        // 7. 位移：施法者小后撤
        com.skillcore.utils.DisplacementUtils.dash(context.getCaster(),
                context.getCaster().getLocation().getDirection().multiply(-1), 0.6, 0.1);

        // 8. 特效 + 音效
        ParticleUtils.line(context.getCaster().getEyeLocation(),
                target.getEyeLocation(), Particle.CRIT, 0.3);
        ParticleUtils.hitMarker(target, Particle.EXPLOSION);
        SoundUtils.explosion(target.getLocation());

        // 9. 暴击提示（如果伤害异常高）
        if (dealt > 25) {
            SoundUtils.critical(context.getPlayerCaster() != null
                    ? context.getPlayerCaster()
                    : null);
        }

        return SkillResult.SUCCESS;
    }

    @Override
    public boolean canCast(SkillContext context) {
        // 自定义前置条件示例
        return super.canCast(context)
                && MathUtils.chance(0.99); // 99% 可用
    }
}
