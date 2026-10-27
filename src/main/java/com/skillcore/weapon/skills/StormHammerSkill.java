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
    /** 右键小跳的逐 tick 清理任务（按玩家记录，退出 / 重载时可取消）。 */
    private final Map<UUID, BukkitTask> hopTasks = new ConcurrentHashMap<>();

    public StormHammerSkill() {
        super("STORM_HAMMER");
    }

    // ------------------------------------------------------------------
    // 无目标技能：未锁定任何目标也能释放（以自身为落点的 AOE）
    // ------------------------------------------------------------------

    @Override
    public boolean canUse(WeaponContext ctx) {
        return ctx != null && ctx.player() != null && ctx.player().isOnline()
                && !ctx.player().isDead()
                // 砸地过程中不允许再次释放：否则会白扣冷却但不产生任何效果
                && !slamming.containsKey(ctx.player().getUniqueId());
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
            UUID id = player.getUniqueId();
            BukkitTask old = hopTasks.remove(id);
            if (old != null) {
                old.cancel();
            }
            BukkitTask task = new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || player.isDead() || ++ticks > 8) {
                        hopTasks.remove(id, this);
                        cancel();
                        return;
                    }
                    VulcanHelper.clearMovementViolations(player);
                    StormHammerFx.hopTrail(player.getLocation());
                }
            }.runTaskTimer(plugin, 1L, 1L);
            hopTasks.put(id, task);
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
        // 砸地 AOE 是技能范围伤害，走 aoe-damage-ratio 溅射（默认 100%），
        // 不能直接调用 ctx.damage（那是“全额单体伤害”，会让每把技能的 AOE 都变成满伤）。
        double ratio = stats.aoeDamageRatio() > 0 ? stats.aoeDamageRatio() : 1.0;
        for (LivingEntity target : targets) {
            ctx.damageCustom(target, stats.damage() * ratio);
            DisplacementUtils.knockbackFrom(target, player, stats.knockbackStrength(), 0.35);
            // 风爆：把周围敌人向上弹飞（在击退结果上叠加竖直速度）
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
        // （技能正拿着这把武器，直接复制一份给显示物，避免显示物与主手物品共享实例）
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held != null && held.getType() != Material.AIR) {
            Effects.display(held.clone(), 1.8f, 24).play(center);
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
        for (BukkitTask task : hopTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        hopTasks.clear();
    }

    /** 玩家退出时立刻取消砸地与小跳任务（不触发落地伤害）。 */
    @Override
    public void onQuit(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        SlamState state = slamming.remove(id);
        if (state != null) {
            cancel(state);
        }
        BukkitTask hop = hopTasks.remove(id);
        if (hop != null) {
            hop.cancel();
        }
    }
}
