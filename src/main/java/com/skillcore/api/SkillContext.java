package com.skillcore.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Immutable-ish execution context passed into every skill cast.
 * Carries caster, target, origin, hit location and free-form payload.
 */
public final class SkillContext {

    private final LivingEntity caster;
    private final LivingEntity target;
    private final Location origin;
    private final Location targetLocation;
    private final ItemStack itemInHand;
    private final double power;
    private final Object payload;

    private SkillContext(Builder builder) {
        this.caster = builder.caster;
        this.target = builder.target;
        this.origin = builder.origin;
        this.targetLocation = builder.targetLocation;
        this.itemInHand = builder.itemInHand;
        this.power = builder.power;
        this.payload = builder.payload;
    }

    public LivingEntity getCaster() {
        return caster;
    }

    public Player getPlayerCaster() {
        return caster instanceof Player player ? player : null;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public Location getOrigin() {
        return origin == null ? caster.getLocation() : origin;
    }

    public Location getTargetLocation() {
        return targetLocation != null ? targetLocation
                : (target != null ? target.getLocation() : getOrigin());
    }

    public ItemStack getItemInHand() {
        return itemInHand;
    }

    /** Free-form power / damage multiplier / charge level. Default 1.0 */
    public double getPower() {
        return power;
    }

    public Object getPayload() {
        return payload;
    }

    @SuppressWarnings("unchecked")
    public <T> T getPayload(Class<T> type) {
        return type.isInstance(payload) ? (T) payload : null;
    }

    public boolean hasTarget() {
        return target != null && target.isValid() && !target.isDead();
    }

    public SkillContext retarget(LivingEntity newTarget) {
        return builder(this).target(newTarget).build();
    }

    public SkillContext withPower(double newPower) {
        return builder(this).power(newPower).build();
    }

    public static Builder builder(LivingEntity caster) {
        return new Builder(caster);
    }

    public static Builder builder(SkillContext source) {
        return new Builder(source.caster)
                .target(source.target)
                .origin(source.origin)
                .targetLocation(source.targetLocation)
                .itemInHand(source.itemInHand)
                .power(source.power)
                .payload(source.payload);
    }

    public static final class Builder {
        private final LivingEntity caster;
        private LivingEntity target;
        private Location origin;
        private Location targetLocation;
        private ItemStack itemInHand;
        private double power = 1.0;
        private Object payload;

        private Builder(LivingEntity caster) {
            this.caster = Objects.requireNonNull(caster, "caster");
        }

        public Builder target(LivingEntity target) {
            this.target = target;
            return this;
        }

        public Builder target(Entity entity) {
            this.target = entity instanceof LivingEntity living ? living : null;
            return this;
        }

        public Builder origin(Location origin) {
            this.origin = origin;
            return this;
        }

        public Builder targetLocation(Location targetLocation) {
            this.targetLocation = targetLocation;
            return this;
        }

        public Builder itemInHand(ItemStack itemInHand) {
            this.itemInHand = itemInHand;
            return this;
        }

        public Builder power(double power) {
            this.power = power;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public SkillContext build() {
            return new SkillContext(this);
        }
    }
}
