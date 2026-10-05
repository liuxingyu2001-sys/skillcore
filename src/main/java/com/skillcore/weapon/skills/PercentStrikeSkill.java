package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：百分比生命打击 — 对高血量目标造成最大/已损生命百分比伤害。
 */
@WeaponSkillInfo(id = "PERCENT_STRIKE", description = "百分比生命打击")
public final class PercentStrikeSkill extends AbstractWeaponSkill {

    public PercentStrikeSkill() {
        super("PERCENT_STRIKE");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        percentStrike(ctx);
    }
}
