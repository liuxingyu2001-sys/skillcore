package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：冲锋突刺 — 突进到目标并造成伤害。
 */
@WeaponSkillInfo(id = "CHARGE", description = "冲锋突刺")
public final class ChargeSkill extends AbstractWeaponSkill {

    public ChargeSkill() {
        super("CHARGE");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        chargeStrike(ctx);
    }
}
