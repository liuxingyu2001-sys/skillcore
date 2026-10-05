package com.skillcore.armor;

/**
 * 盔甲技能接口 — 未来给整套盔甲绑定的主动 / 被动逻辑。
 * <p>
 * 与 {@code com.skillcore.weapon.WeaponSkill} 对应：集齐整套后由
 * {@link ArmorManager} 调用生命周期钩子（onEquip / onUnequip / onTick）。
 * 目前仅定义接口与上下文，尚未实现具体盔甲技能。
 */
public interface ArmorSkill {

    /** 技能类型 key，与 armor/<id>.yml / 工厂注册一致。 */
    String getType();

    /** 玩家集齐整套（穿戴完成）时触发。 */
    default void onEquip(ArmorContext ctx) {
    }

    /** 玩家不再满足整套条件（脱下一件 / 退出 / 重载）时触发。 */
    default void onUnequip(ArmorContext ctx) {
    }

    /** 穿戴期间每 tick 触发（可选，实现者自行控制性能）。 */
    default void onTick(ArmorContext ctx) {
    }

    /** 插件禁用 / 重载时清理该技能持有的状态。 */
    default void cleanup() {
    }

    /**
     * 该技能的配置键默认值（用于缺失键自动补全）。
     * <p>
     * 返回的键会被写入 {@code set-bonus:} 段，与技能代码里 {@code custom*()} 读的默认值保持一致。
     */
    default java.util.Map<String, Object> defaults() {
        return java.util.Map.of();
    }
}
