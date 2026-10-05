package com.skillcore.weapon.skills;

import com.skillcore.utils.ReflectDamageUtils;
import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;
import org.bukkit.entity.LivingEntity;

/**
 * 通用：反伤 — 右键开启反伤 buff，受击时按比例反弹伤害。
 */
@WeaponSkillInfo(id = "THORNS", description = "反伤")
public final class ThornsSkill extends AbstractWeaponSkill {

    public ThornsSkill() {
        super("THORNS");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        ReflectDamageUtils.addReflectBuff(
                ctx.player(), ctx.stats().reflectPercent(), ctx.stats().reflectFlat(), 200);
        ctx.castFx();
    }

    @Override
    public void onDamaged(WeaponContext ctx, LivingEntity attacker, double damage) {
        ReflectDamageUtils.reflect(
                ctx.player(), attacker, damage, ctx.stats().reflectPercent(), ctx.stats().reflectFlat());
    }
}
