package com.skillcore.skill;

import com.skillcore.api.AbstractSkill;
import com.skillcore.api.ActiveSkill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.model.SkillDefinition;
import com.skillcore.utils.DisplacementUtils;

/**
 * 位移技能 — 闪现 / 瞬移.
 */
public class BlinkSkill extends AbstractSkill implements ActiveSkill {

    public BlinkSkill(SkillDefinition definition) {
        super(definition);
    }

    @Override
    public SkillResult cast(SkillContext context) {
        double distance = definition.getExtraDouble("blink-distance", 8.0);
        var result = DisplacementUtils.blinkForward(context.getCaster(), distance);
        return result != null ? SkillResult.SUCCESS : SkillResult.FAILED;
    }
}
