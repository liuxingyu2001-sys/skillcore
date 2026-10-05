package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：横扫 AOE — 以目标为中心溅射伤害。
 */
@WeaponSkillInfo(id = "SWEEP", description = "横扫 AOE")
public final class SweepSkill extends AbstractWeaponSkill {

    public SweepSkill() {
        super("SWEEP");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        sweep(ctx);
        ctx.castFx();
    }
}
