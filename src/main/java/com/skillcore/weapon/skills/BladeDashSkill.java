package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 利刃突刺 — 向前突刺并斩击路径上的敌人，附带吸血与减速。
 */
@WeaponSkillInfo(id = "BLADE_DASH", description = "利刃突刺（突刺+吸血+减速）")
public final class BladeDashSkill extends AbstractWeaponSkill {

    public BladeDashSkill() {
        super("BLADE_DASH");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        dashSlash(ctx);
        ctx.slowTarget();
        ctx.castFx();
    }
}
