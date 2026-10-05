package com.skillcore.weapon.skills;

import com.skillcore.SkillCorePlugin;
import com.skillcore.effect.Effects;
import com.skillcore.utils.AreaUtils;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.TargetFilter;
import com.skillcore.utils.VulcanHelper;
import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风暴战锤（GJB2377 定制）。
 * <ul>
 *   <li>右键：向上小跳（少量向前位移）</li>
 *   <li>Shift+右键：向上跃起 → 下砸地面，对范围内敌人造成 AOE 伤害</li>
 * </ul>
 * 全程速度位移（无瞬移）。特效分「跃起 / 上升 / 下砸 / 落地」四段渲染（见 {@link StormHammerFx}）。
 */
@WeaponSkillInfo(id = "STORM_HAMMER", aliases = {"STORM_HAMMER_HOP", "STORM_HAMMER_SLAM"})
public final class StormHammerSkill extends AbstractWeaponSkill {

    private static final class SlamState {
        int ticks;
        BukkitTask task;
    }

    private final Map<UUID, SlamState> slamming = new ConcurrentHashMap<>();

    public StormHammerSkill() {
        super("STORM_HAMMER");
    }

    // ------------------------------------------------------------------
    // 无目标技能：未锁定任何目标也能释放（以自身为落点的 AOE）
    // ------------------------------------------------------------------

    @Override
    public boolean canUse(WeaponContext ctx) {
        return ctx != null && ctx.player() != null && ctx.player().isOnline() && !ctx.player().isDead();
    }

    // ------------------------------------------------------------------
    // 右键：向上小跳
    // ------------------------------------------------------------------

    @Override
    public void onRightClick(WeaponContext ctx) {
        Player player = ctx.player();
        var stats = ctx.stats();

        Vector dir = player.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) {
            dir = new Vector(0, 0, 1);
        }
        Vector velocity = dir.normalize().multiply(stats.hopForward()).setY(stats.hopVelocity());
        player.setVelocity(velocity);

        VulcanHelper.clearMovementViolations(player);
        ctx.play(StormHammerFx.hopBurst());

        // 位移后若干 tick 持续清零移动类检测 VL，并附带风之拖尾
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin != null) {
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || ++ticks > 8) {
                        cancel();
                        return;
                    }
                    VulcanHelper.clearMovementViolations(player);
                    StormHammerFx.hopTrail(player.getLocation());
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    // ------------------------------------------------------------------
    // Shift+右键：跃起砸地
    // ------------------------------------------------------------------

    @Override
    public void onShiftRightClick(WeaponContext ctx) {
        Player player = ctx.player();
        UUID id = player.getUniqueId();
        if (slamming.containsKey(id)) {
            return;
        }

        VulcanHelper.clearMovementViolations(player);
        ctx.play(StormHammerFx.castBurst());

        SlamState state = new SlamState();
        slamming.put(id, state);

        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null) {
            slamming.remove(id);
            return;
        }
        state.task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> tick(ctx, player, state), 1L, 1L);
    }

    private void tick(WeaponContext ctx, Player player, SlamState state) {
        state.ticks++;
        if (!player.isOnline() || player.isDead()) {
            finish(ctx, player, state, false);
            return;
        }
        VulcanHelper.clearMovementViolations(player);
        var stats = ctx.stats();
        int apex = stats.slamApexTicks();

        if (state.ticks == 1) {
            // 向上跃起（纯速度）
            player.setVelocity(new Vector(0, stats.slamLaunchVelocity(), 0));
        }

        if (state.ticks > apex) {
            // 达到顶点后强制下砸
            player.setVelocity(new Vector(0, stats.slamDownVelocity(), 0));
            StormHammerFx.descentTick(player.getLocation(), state.ticks);
        } else {
            StormHammerFx.ascendTick(player.getLocation(), state.ticks);
        }

        boolean landed = player.isOnGround() && state.ticks > 4;
        boolean timeout = state.ticks > stats.slamTimeoutTicks();
        if (landed || timeout) {
            finish(ctx, player, state, true);
        }
    }

    private void finish(WeaponContext ctx, Player player, SlamState state, boolean doImpact) {
        cancel(state);
        slamming.remove(player.getUniqueId());
        if (doImpact) {
            impact(ctx, player);
        }
    }

    private void impact(WeaponContext ctx, Player player) {
        var stats = ctx.stats();
        Location center = player.getLocation();
        var targets = TargetFilter.filter(player,
                AreaUtils.getSphere(center, stats.aoeRadius(),
                        e -> TargetFilter.isAttackable(player, e)));
        for (LivingEntity target : targets) {
            ctx.damage(target);
            DisplacementUtils.knockbackFrom(target, player, stats.knockbackStrength(), 0.35);
            // 风爆：把周围敌人向上弹飞
            if (stats.slamUppercutVelocity() > 0) {
                Vector v = target.getVelocity();
                target.setVelocity(v.setY(stats.slamUppercutVelocity()));
            }
        }

        // 风爆反冲：自己小幅向上弹起
        if (stats.slamSelfBounce() > 0) {
            player.setVelocity(player.getVelocity().setY(stats.slamSelfBounce()));
        }

        StormHammerFx.impact(stats.aoeRadius()).play(center);

        // 落地瞬间把手中战锤放大显示在冲击中心，作为「锤击」具象
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.AIR) {
            Effects.display(held, 1.8f, 24).play(center);
        }
    }

    private void cancel(SlamState state) {
        if (state.task != null) {
            state.task.cancel();
            state.task = null;
        }
    }

    @Override
    public void cleanup() {
        for (SlamState state : slamming.values()) {
            cancel(state);
        }
        slamming.clear();
    }
}
