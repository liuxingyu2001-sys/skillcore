package com.skillcore.skill;

import com.skillcore.api.AbstractSkill;
import com.skillcore.api.ActiveSkill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.model.SkillDefinition;
import com.skillcore.utils.DisplacementUtils;

/**
 * 位移技能 — 向视角方向冲刺.
 */
public class DashSkill extends AbstractSkill implements ActiveSkill {

    public DashSkill(SkillDefinition definition) {
        super(definition);
    }

    @Override
    public SkillResult cast(SkillContext context) {
        double speed = definition.getExtraDouble("dash-speed", 1.5);
        double y = definition.getExtraDouble("dash-y", 0.2);
        dash(context, speed, y);
        return SkillResult.SUCCESS;
    }
}
