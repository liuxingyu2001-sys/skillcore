package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：吸血强击 — 单体强击，吸血比例由配置 {@code lifesteal} 决定。
 */
@WeaponSkillInfo(id = "LIFESTEAL_STRIKE", description = "吸血强击")
public final class LifestealStrikeSkill extends AbstractWeaponSkill {

    public LifestealStrikeSkill() {
        super("LIFESTEAL_STRIKE");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        strike(ctx);
    }
}
