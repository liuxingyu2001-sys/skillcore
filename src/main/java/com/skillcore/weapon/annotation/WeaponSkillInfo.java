package com.skillcore.weapon.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个武器技能实现，交由 {@link com.skillcore.weapon.WeaponFactory} 自动扫描注册。
 * <pre>
 * {@literal @}WeaponSkillInfo(id = "BLADE_DASH", aliases = {"DASH_DAMAGE"})
 * public final class BladeDashSkill extends AbstractWeaponSkill {
 *     public BladeDashSkill() { super("BLADE_DASH"); }
 *     {@literal @}Override public void onRightClick(WeaponContext ctx) { ... }
 * }
 * </pre>
 * 只要类放在被扫描的包（默认 {@code com.skillcore.weapon.skills}）下并带此注解，
 * 无需再手动 {@code registerSkill}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WeaponSkillInfo {

    /** 技能类型 key，对应 skills/*.yml 的 right-skill / left-skill。 */
    String id();

    /** 别名（多个 key 指向同一实现）。 */
    String[] aliases() default {};

    /** 说明（仅用于日志/文档）。 */
    String description() default "";

    /** 是否启用。 */
    boolean enabled() default true;
}
