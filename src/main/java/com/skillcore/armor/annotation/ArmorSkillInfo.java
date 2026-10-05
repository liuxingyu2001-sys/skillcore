package com.skillcore.armor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个盔甲技能实现，交由 {@code ArmorFactory.autoRegister} 自动扫描注册。
 * <pre>
 * {@literal @}ArmorSkillInfo(id = "SUNFIRE", aliases = {"BURN_AURA"})
 * public final class SunfireArmorSkill implements ArmorSkill {
 *     public SunfireArmorSkill() {}
 *     {@literal @}Override public void onEquip(ArmorContext ctx) { ... }
 * }
 * </pre>
 * 只要类放在被扫描的包（默认 {@code com.skillcore.armor.skills}）下并带此注解，
 * 无需再手动 {@code registerSkill}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ArmorSkillInfo {

    /** 技能类型 key，对应 armor/<id>.yml 的 skill。 */
    String id();

    /** 别名（多个 key 指向同一实现）。 */
    String[] aliases() default {};

    /** 说明（仅用于日志/文档）。 */
    String description() default "";

    /** 是否启用。 */
    boolean enabled() default true;
}
