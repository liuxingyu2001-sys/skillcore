package com.skillcore.weapon;

/**
 * 技能武器技能接口 — 每把武器的技能逻辑实现这个。
 * <p>
 * 以后写「利刃突刺」这类技能武器，只需实现对应点击方法，
 * 数值全部从 {@link WeaponContext#stats()} 读，不要写死。
 */
public interface WeaponSkill {

    /** 技能类型 key，与 skills.yml / 工厂注册一致。 */
    String getType();

    /**
     * 右键触发。绝大多数武器技能写在这里。
     */
    default void onRightClick(WeaponContext ctx) {
    }

    /**
     * 左键触发。
     */
    default void onLeftClick(WeaponContext ctx) {
    }

    /**
     * Shift + 右键。
     */
    default void onShiftRightClick(WeaponContext ctx) {
        onRightClick(ctx);
    }

    /**
     * Shift + 左键。
     */
    default void onShiftLeftClick(WeaponContext ctx) {
        onLeftClick(ctx);
    }

    /**
     * 命中目标后（近战附带效果）。
     */
    default void onHit(WeaponContext ctx, org.bukkit.entity.LivingEntity victim, double damage) {
    }

    /**
     * 击杀目标后。
     */
    default void onKill(WeaponContext ctx, org.bukkit.entity.LivingEntity victim) {
    }

    /**
     * 受击时（反击/反伤类）。
     */
    default void onDamaged(WeaponContext ctx, org.bukkit.entity.LivingEntity attacker, double damage) {
    }

    /**
     * 是否可以释放（冷却外的额外条件）。
     */
    default boolean canUse(WeaponContext ctx) {
        return ctx != null && ctx.player() != null && ctx.weapon() != null;
    }
}
