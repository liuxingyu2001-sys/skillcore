package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：控制打击 — 伤害并眩晕/定身/减速目标。
 */
@WeaponSkillInfo(id = "CONTROL_STRIKE", description = "控制打击")
public final class ControlStrikeSkill extends AbstractWeaponSkill {

    public ControlStrikeSkill() {
        super("CONTROL_STRIKE");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        controlStrike(ctx);
    }
}
