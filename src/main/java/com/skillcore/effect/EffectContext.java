package com.skillcore.effect;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * 特效执行上下文 — 携带位置、朝向、施法者、目标与强度。
 * <p>
 * 大多数情况下特效只需要位置：
 * <pre>
 * Effects.ring(Particle.END_ROD, 2.0, 24).play(player.getLocation());
 * </pre>
 */
public final class EffectContext {

    private final Location location;
    private final Vector direction;
    private final LivingEntity caster;
    private final LivingEntity target;
    private final double power;
    private final float scale;

    private EffectContext(Location location, Vector direction, LivingEntity caster,
                          LivingEntity target, double power, float scale) {
        this.location = location == null ? null : location.clone();
        this.direction = direction == null ? new Vector(0, 1, 0) : direction.clone().normalize();
        this.caster = caster;
        this.target = target;
        this.power = power;
        this.scale = scale;
    }

    public static EffectContext of(Location location) {
        return new EffectContext(location, null, null, null, 1.0, 1.0f);
    }

    public static EffectContext of(Location location, Vector direction) {
        return new EffectContext(location, direction, null, null, 1.0, 1.0f);
    }

    public EffectContext withDirection(Vector direction) {
        return new EffectContext(location, direction, caster, target, power, scale);
    }

    public EffectContext withCaster(LivingEntity caster) {
        return new EffectContext(location, direction, caster, target, power, scale);
    }

    public EffectContext withTarget(LivingEntity target) {
        return new EffectContext(location, direction, caster, target, power, scale);
    }

    public EffectContext withPower(double power) {
        return new EffectContext(location, direction, caster, target, power, scale);
    }

    public EffectContext withScale(float scale) {
        return new EffectContext(location, direction, caster, target, power, scale);
    }

    public Location location() {
        return location;
    }

    public World world() {
        return location == null ? null : location.getWorld();
    }

    public Vector direction() {
        return direction;
    }

    public LivingEntity caster() {
        return caster;
    }

    public LivingEntity target() {
        return target;
    }

    public double power() {
        return power;
    }

    public float scale() {
        return scale;
    }

    public boolean valid() {
        return location != null && location.getWorld() != null;
    }
}
