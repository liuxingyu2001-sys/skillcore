package com.skillcore.weapon.skills;

import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.SkillDamageUtils;
import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日炎之剑 — 纯被动：普通攻击附带「固定伤害 + 目标最大生命百分比伤害」。
 * <p>
 * 数值从 {@code stats} 读取：{@code damage}（固定伤害）、{@code percent-max-health}（最大生命百分比）、
 * {@code on-hit-interval}（秒，默认 0.5，0 = 每次命中都触发）。
 * <p>
 * 不覆写左右键，命中间隔静默计时（不弹冷却提示），
 * 配置文件里 {@code cooldown: 0} 避免主动空点产生冷却提示。
 */
@WeaponSkillInfo(id = "SUNFIRE_BLADE", aliases = {"SUNFIRE_SWORD"}, description = "日炎之剑：攻击附带固定伤害 + 最大生命百分比")
public final class SunfireBladeSkill extends AbstractWeaponSkill {

    /** 每个玩家上次触发的时间戳（技能实例全服共享，按玩家分开间隔）。 */
    private final Map<UUID, Long> lastProc = new ConcurrentHashMap<>();

    public SunfireBladeSkill() {
        super("SUNFIRE_BLADE");
    }

    @Override
    public void onHit(WeaponContext ctx, LivingEntity victim, double damage) {
        Player player = ctx.player();
        if (player == null || victim == null || !DamageUtils.isAlive(victim)) {
            return;
        }
        double flat = ctx.stats().damage();
        double percent = ctx.stats().percentMaxHealth();
        if (flat <= 0 && percent <= 0) {
            return;
        }
        long intervalMs = (long) (Math.max(0.0, ctx.stats().onHitIntervalSeconds()) * 1000.0);
        long now = System.currentTimeMillis();
        Long last = lastProc.get(player.getUniqueId());
        if (intervalMs > 0 && last != null && now - last < intervalMs) {
            return;
        }
        lastProc.put(player.getUniqueId(), now);

        double bonus = flat + DamageUtils.getMaxHealth(victim) * percent;
        SkillDamageUtils.damage(victim, bonus, player, ctx.skillSource());
        ParticleUtils.hitMarker(victim, Particle.FLAME);
    }

    @Override
    public void cleanup() {
        lastProc.clear();
    }
}
