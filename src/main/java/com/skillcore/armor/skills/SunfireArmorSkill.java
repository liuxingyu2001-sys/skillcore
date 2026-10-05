package com.skillcore.armor.skills;

import com.skillcore.SkillCorePlugin;
import com.skillcore.armor.ArmorContext;
import com.skillcore.armor.ArmorSkill;
import com.skillcore.armor.annotation.ArmorSkillInfo;
import com.skillcore.utils.AreaUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.SkillDamageUtils;
import com.skillcore.utils.TargetFilter;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日炎之甲 — 穿戴整套后，周围敌人每 tick 灼伤 + 脚下烈焰光环粒子环。
 * <p>
 * 灼伤伤害 = 固定值（{@code sunfire-flat}）+ 目标最大生命百分比（{@code sunfire-percent-max}）。
 * 烈焰光环由独立的视觉任务绘制（{@code sunfire-ring-*}），不参与伤害计算。
 */
@ArmorSkillInfo(id = "SUNFIRE", aliases = {"SUNFIRE_ARMOR", "BURN_AURA"})
public final class SunfireArmorSkill implements ArmorSkill {

    /** 每个玩家的运行状态（伤害任务 + 光环视觉任务）。 */
    private static final class State {
        BukkitTask burnTask;
        BukkitTask ringTask;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "SUNFIRE";
    }

    @Override
    public void onEquip(ArmorContext ctx) {
        Player player = ctx.player();
        State state = states.computeIfAbsent(player.getUniqueId(), uuid -> new State());
        var plugin = SkillCorePlugin.getInstance();

        if (state.burnTask == null) {
            long intervalTicks = Math.max(1L,
                    (long) ctx.stats().customDouble("sunfire-interval-ticks", 20.0));
            state.burnTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                    () -> burn(player, ctx), 0L, intervalTicks);
        }

        if (state.ringTask == null) {
            long ringIntervalTicks = Math.max(1L,
                    (long) ctx.stats().customDouble("sunfire-ring-interval-ticks", 5.0));
            state.ringTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                    () -> flameRing(player, ctx), 0L, ringIntervalTicks);
        }
    }

    @Override
    public void onUnequip(ArmorContext ctx) {
        stop(ctx.player());
    }

    @Override
    public void cleanup() {
        for (State state : states.values()) {
            cancel(state);
        }
        states.clear();
    }

    /** 配置键默认值（与下方 custom* 读取的默认值保持一致，供缺失键自动补全）。 */
    @Override
    public java.util.Map<String, Object> defaults() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("sunfire-flat", 1.0);
        map.put("sunfire-percent-max", 0.02);
        map.put("sunfire-radius", 4.0);
        map.put("sunfire-interval-ticks", 20.0);
        map.put("sunfire-particle", "FLAME");
        map.put("sunfire-ring", true);
        map.put("sunfire-ring-radius", 2.0);
        map.put("sunfire-ring-y", 0.3);
        map.put("sunfire-ring-points", 32);
        map.put("sunfire-ring-interval-ticks", 5.0);
        map.put("sunfire-ring-particle", "FLAME");
        return map;
    }

    private void stop(Player player) {
        State state = states.remove(player.getUniqueId());
        if (state != null) {
            cancel(state);
        }
    }

    private void cancel(State state) {
        if (state.burnTask != null) {
            state.burnTask.cancel();
            state.burnTask = null;
        }
        if (state.ringTask != null) {
            state.ringTask.cancel();
            state.ringTask = null;
        }
    }

    // ------------------------------------------------------------------
    // 灼伤
    // ------------------------------------------------------------------

    private void burn(Player player, ArmorContext ctx) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        double radius = Math.max(0.5, ctx.stats().customDouble("sunfire-radius", 4.0));
        double flat = Math.max(0.0, ctx.stats().customDouble("sunfire-flat", 1.0));
        double percentMax = Math.max(0.0, ctx.stats().customDouble("sunfire-percent-max", 0.02));
        Particle particle = parseParticle(ctx.stats().customString("sunfire-particle", "FLAME"));

        var targets = TargetFilter.filter(player, AreaUtils.getSphere(player.getLocation(), radius));
        for (LivingEntity target : targets) {
            double damage = flat + DamageUtils.getMaxHealth(target) * percentMax;
            SkillDamageUtils.damage(target, damage, player, "SUNFIRE");
            ParticleUtils.hitMarker(target, particle);
        }
    }

    // ------------------------------------------------------------------
    // 烈焰光环
    // ------------------------------------------------------------------

    private void flameRing(Player player, ArmorContext ctx) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (!ctx.stats().customBoolean("sunfire-ring", true)) {
            return;
        }
        double radius = Math.max(0.5, ctx.stats().customDouble("sunfire-ring-radius", 2.0));
        double y = ctx.stats().customDouble("sunfire-ring-y", 0.3);
        int points = Math.max(8, ctx.stats().customInt("sunfire-ring-points", 32));
        Particle particle = parseParticle(ctx.stats().customString("sunfire-ring-particle", "FLAME"));

        Location center = player.getLocation().clone().add(0, y, 0);
        // 外环（烈焰）
        ParticleUtils.circle(center, radius, particle, points);
        // 内环（更密，形成双层光环）
        ParticleUtils.circle(center, radius * 0.6, particle, Math.max(6, points / 2));
    }

    private static Particle parseParticle(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Particle.FLAME;
        }
        try {
            return Particle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return Particle.FLAME;
        }
    }
}
