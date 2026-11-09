package com.skillcore.weapon.skills;

import com.skillcore.utils.AimUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.LifestealUtils;
import com.skillcore.utils.MessageUtils;
import com.skillcore.utils.PotionUtils;
import com.skillcore.utils.SkillDamageUtils;
import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 血月之镰 · 左键「死神收割」。
 * <p>
 * 面前扇形镰斩（角度 / 半径 / 目标数全部走 {@code stats}）：
 * <ul>
 *   <li>命中目标吃完整武器伤害，并向施法者外侧击退 + 减速；</li>
 *   <li>目标带「血月印记」时引爆印记，额外吃 {@code damage × mark-bonus-ratio}；</li>
 *   <li>目标剩余生命低于 {@code execute-threshold} 时追加「处决」真实伤害
 *       （目标最大生命 × {@code execute-damage-percent}，直接走技能伤害出口、不吃护甲）；</li>
 *   <li>被动：普攻按 {@code mark-chance} 概率给目标挂印记，形成
 *       「普攻/回旋挂印 → 左键收割引爆」的连招。</li>
 * </ul>
 * 表现由 {@link BloodMoonScytheFx} 负责：三层新月刀光、命中血环、处决音爆。
 */
@WeaponSkillInfo(id = "SOUL_HARVEST", aliases = {"HARVEST"}, description = "扇形镰斩，处决残血并引爆血月印记")
public final class SoulHarvestSkill extends AbstractWeaponSkill {

    public SoulHarvestSkill() {
        super("SOUL_HARVEST");
    }

    @Override
    public void onLeftClick(WeaponContext ctx) {
        if (ctx == null || ctx.player() == null) return;
        Player player = ctx.player();
        var stats = ctx.stats();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone();

        // 挥砍动作 + 刀光：即使没打到人也有明确反馈
        player.swingMainHand();
        BloodMoonScytheFx.sweepCast(eye, direction, player.getLocation());

        List<LivingEntity> targets = AimUtils.getConeTargets(
                player, stats.sweepAngle(), stats.sweepRange());
        // 优先处理最近的：目标数封顶时不会只打到远处的「漏网之鱼」
        targets.sort(Comparator.comparingDouble(
                (LivingEntity entity) -> entity.getLocation().distanceSquared(player.getLocation())));
        int max = Math.max(1, stats.maxTargets());
        if (targets.size() > max) {
            targets = new ArrayList<>(targets.subList(0, max));
        }

        int hits = 0;
        int detonates = 0;
        int executes = 0;
        for (LivingEntity victim : targets) {
            double dealt = ctx.damageCustom(victim, stats.damage());
            if (dealt <= 0) continue;
            hits++;

            // 血月印记引爆：额外伤害
            if (BloodMoonMarks.consume(victim)) {
                detonates++;
                double bonusDamage = ctx.damageCustom(victim, stats.damage() * stats.markBonusRatio());
                if (bonusDamage > 0) {
                    BloodMoonScytheFx.detonate(victim.getLocation().add(0, victim.getHeight() * 0.5, 0));
                }
            }

            // 处决：残血目标追加最大生命百分比真实伤害（不吃护甲减免）
            if (stats.executeThreshold() > 0
                    && DamageUtils.getHealthPercent(victim) <= stats.executeThreshold()) {
                double executeDamage = DamageUtils.getMaxHealth(victim) * stats.executeDamagePercent();
                double real = SkillDamageUtils.damage(victim, executeDamage, player, ctx.skillSource());
                if (real > 0) {
                    executes++;
                    // 处决伤害不经过 ctx.damage，吸血需要单独补一次
                    LifestealUtils.healByDamagePercent(player, real, stats.lifesteal());
                    BloodMoonScytheFx.execute(victim.getLocation().add(0, victim.getHeight() * 0.5, 0));
                }
            }

            // 控制 + 打击感（目标可能已被处决击杀：死亡实体不再播受击动画 / 位移）
            Location hitLocation = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
            if (!victim.isDead()) {
                DisplacementUtils.knockback(victim, player.getLocation(), stats.knockbackStrength(), 0.28);
                PotionUtils.slowness(victim, stats.slowDurationTicks(), stats.slowAmplifier());
                victim.playHurtAnimation(0.0f);
                victim.setNoActionTicks(3);
            }
            BloodMoonScytheFx.sweepHit(hitLocation);
            if (victim instanceof Player victimPlayer) {
                // 交互反馈：被收割的玩家也能立刻知道发生了什么
                MessageUtils.sendActionBar(victimPlayer, "&8[&c血月&8] &7你被死神收割 &c-"
                        + (int) Math.ceil(dealt));
            }
        }

        sendResult(player, hits, detonates, executes);
    }

    /** 命中反馈：动作栏汇总，让玩家立刻知道这一刀打到了什么。 */
    private static void sendResult(Player player, int hits, int detonates, int executes) {
        if (hits <= 0) {
            MessageUtils.sendActionBar(player, "&8[&c血月&8] &7死神收割 &8· &7落空");
            return;
        }
        StringBuilder message = new StringBuilder("&8[&c血月&8] &7死神收割 &8· &c命中 &f")
                .append(hits).append(" &7人");
        if (detonates > 0) {
            message.append(" &8· &d引爆 &f").append(detonates);
        }
        if (executes > 0) {
            message.append(" &8· &4处决 &f").append(executes);
        }
        MessageUtils.sendActionBar(player, message.toString());
    }

    /**
     * 被动：普攻概率挂「血月印记」—— 与右键回旋形成两条挂印途径，
     * 让普攻也有参与感，而不是只有技能才有交互。
     */
    @Override
    public void onHit(WeaponContext ctx, LivingEntity victim, double damage) {
        if (ctx == null || victim == null || !victim.isValid() || victim.isDead()) return;
        var stats = ctx.stats();
        double chance = stats.markChance();
        if (chance <= 0 || Math.random() > chance) return;
        // 已有印记不重复刷发光/粒子，避免普攻连击时满屏同款特效
        if (BloodMoonMarks.isMarked(victim)) return;
        BloodMoonMarks.apply(victim, stats.markDurationTicks());
        BloodMoonScytheFx.markApplied(victim.getLocation());
    }

    /** 击杀：抽魂连线 + 回复（heal / heal-amount 配置）。 */
    @Override
    public void onKill(WeaponContext ctx, LivingEntity victim) {
        if (ctx == null || ctx.player() == null || victim == null) return;
        Player player = ctx.player();
        BloodMoonScytheFx.killSoul(
                victim.getLocation().add(0, victim.getHeight() * 0.5, 0),
                player.getEyeLocation());
        double healed = ctx.healSelf();
        if (healed > 0) {
            BloodMoonScytheFx.healBurst(player.getLocation().add(0, 1.0, 0));
        }
    }

    @Override
    public void cleanup() {
        // 印记是目标侧状态，重载/禁用时统一清掉，避免旧技能实例的印记影响新实例
        BloodMoonMarks.clear();
    }
}
