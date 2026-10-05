package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：突刺伤害 — 右键向前突刺并斩击路径上的敌人。
 */
@WeaponSkillInfo(id = "DASH_DAMAGE", aliases = {"DASH"}, description = "突刺伤害")
public final class DashDamageSkill extends AbstractWeaponSkill {

    public DashDamageSkill() {
        super("DASH_DAMAGE");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        dashSlash(ctx);
        ctx.castFx();
    }
}
