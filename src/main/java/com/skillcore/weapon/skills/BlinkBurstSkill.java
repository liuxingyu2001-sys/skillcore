package com.skillcore.weapon.skills;

import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;

/**
 * 通用：闪现爆发 — 向前闪现并以瞄准点为中心 AOE。
 */
@WeaponSkillInfo(id = "BLINK_BURST", description = "闪现爆发")
public final class BlinkBurstSkill extends AbstractWeaponSkill {

    public BlinkBurstSkill() {
        super("BLINK_BURST");
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        blinkBurst(ctx);
        ctx.castFx();
    }
}
