package com.skillcore.weapon.skills;

import com.skillcore.utils.ReflectDamageUtils;
import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：反伤 — 右键开启反伤 buff，持续期间受击按比例反弹伤害。
 * <p>
 * 反弹逻辑统一由 {@code CombatListener} 读取 buff 后处理；
 * 这里不要额外实现 {@code onDamaged}，否则同一次受击会反弹两次。
 */
@WeaponSkillInfo(id = "THORNS", description = "反伤")
public final class ThornsSkill extends AbstractWeaponSkill {

    public ThornsSkill() {
        super("THORNS");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        int duration = Math.max(0, ctx.stats().reflectDurationTicks());
        if (duration == 0 || (ctx.stats().reflectPercent() <= 0 && ctx.stats().reflectFlat() <= 0)) {
            return;
        }
        ReflectDamageUtils.addReflectBuff(
                ctx.player(), ctx.stats().reflectPercent(), ctx.stats().reflectFlat(), duration);
        ctx.castFx();
    }
}