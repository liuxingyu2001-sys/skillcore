package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：单体强击 — 左右键均可触发一次完整连击。
 */
@WeaponSkillInfo(id = "STRIKE", description = "单体强击")
public final class StrikeSkill extends AbstractWeaponSkill {

    public StrikeSkill() {
        super("STRIKE");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        strike(ctx);
    }

    @Override
    public void onLeftClick(WeaponContext ctx) {
        strike(ctx);
    }
}
