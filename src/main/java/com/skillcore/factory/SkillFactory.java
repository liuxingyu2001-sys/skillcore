package com.skillcore.factory;

import com.skillcore.api.ActiveSkill;
import com.skillcore.api.Skill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.model.SkillDefinition;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.LifestealUtils;
import com.skillcore.utils.PercentageDamageUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Factory pattern for creating and registering skills.
 * <p>
 * Supports:
 * <ul>
 *   <li>Register custom skill constructors by type name</li>
 *   <li>Create skills from {@link SkillDefinition}</li>
 *   <li>Default configurable {@link GenericActiveSkill} implementation</li>
 * </ul>
 */
public final class SkillFactory {

    /** type-name -> constructor from definition */
    private final Map<String, Function<SkillDefinition, Skill>> constructors = new ConcurrentHashMap<>();

    /** Built-in generic skill constructors */
    private static final String TYPE_DAMAGE = "DAMAGE";
    private static final String TYPE_PERCENT_DAMAGE = "PERCENT_DAMAGE";
    private static final String TYPE_LIFESTEAL = "LIFESTEAL";

    public SkillFactory() {
        registerDefaults();
    }

    private void registerDefaults() {
        constructors.put(TYPE_DAMAGE, GenericActiveSkill::new);
        constructors.put(TYPE_PERCENT_DAMAGE, GenericActiveSkill::new);
        constructors.put(TYPE_LIFESTEAL, GenericActiveSkill::new);
        // any definition type falls back to generic
        constructors.put("ACTIVE", GenericActiveSkill::new);
        constructors.put("ULTIMATE", GenericActiveSkill::new);
    }

    /**
     * Register a custom skill constructor for a type name.
     * Example: {@code registerType("FIREBALL", def -> new FireballSkill(def));}
     */
    public void registerType(String typeName, Function<SkillDefinition, Skill> constructor) {
        if (typeName == null || constructor == null) {
            return;
        }
        constructors.put(typeName.toUpperCase(), constructor);
    }

    public boolean hasType(String typeName) {
        return typeName != null && constructors.containsKey(typeName.toUpperCase());
    }

    /**
     * Create a skill from definition. Uses skill-type or extras "skill-type" key,
     * otherwise falls back to generic active skill.
     */
    public Skill create(SkillDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        String customType = definition.getExtraString("skill-type", null);
        if (customType != null) {
            Function<SkillDefinition, Skill> ctor = constructors.get(customType.toUpperCase());
            if (ctor != null) {
                return ctor.apply(definition);
            }
        }
        Function<SkillDefinition, Skill> ctor = constructors.get(definition.getType().name());
        if (ctor != null) {
            return ctor.apply(definition);
        }
        return new GenericActiveSkill(definition);
    }

    /**
     * Create and register into a registry in one call.
     */
    public Skill createAndRegister(SkillRegistry registry, SkillDefinition definition) {
        Skill skill = create(definition);
        registry.register(skill);
        return skill;
    }

    /**
     * Fluent builder for programmatic skill creation (no YAML).
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private String description = "";
        private com.skillcore.api.SkillType type = com.skillcore.api.SkillType.ACTIVE;
        private com.skillcore.api.SkillTrigger trigger = com.skillcore.api.SkillTrigger.RIGHT_CLICK;
        private double cooldown = 1.0;
        private double manaCost = 0;
        private String permission;
        private double baseDamage;
        private double scaling;
        private double critChance = 0.1;
        private double critMultiplier = 1.5;
        private double armorPen = 0;
        private double percentMax;
        private double lifesteal;
        private SkillDefinition.DamageProfile damage = SkillDefinition.DamageProfile.EMPTY;

        private Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder type(com.skillcore.api.SkillType type) {
            this.type = type;
            return this;
        }

        public Builder trigger(com.skillcore.api.SkillTrigger trigger) {
            this.trigger = trigger;
            return this;
        }

        public Builder cooldown(double seconds) {
            this.cooldown = seconds;
            return this;
        }

        public Builder manaCost(double cost) {
            this.manaCost = cost;
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder damage(double base, double scaling) {
            this.baseDamage = base;
            this.scaling = scaling;
            return this;
        }

        public Builder critical(double chance, double multiplier) {
            this.critChance = chance;
            this.critMultiplier = multiplier;
            return this;
        }

        public Builder armorPenetration(double pen) {
            this.armorPen = pen;
            return this;
        }

        public Builder percentMaxHealth(double percent) {
            this.percentMax = percent;
            return this;
        }

        public Builder lifesteal(double percent) {
            this.lifesteal = percent;
            return this;
        }

        public SkillDefinition buildDefinition() {
            this.damage = new SkillDefinition.DamageProfile(
                    baseDamage, scaling, critChance, critMultiplier, armorPen,
                    percentMax, 0, 0, 0
            );
            return new SkillDefinition(
                    id, displayName, description, type, trigger,
                    cooldown, manaCost, permission,
                    org.bukkit.Material.PAPER,
                    java.util.List.of(),
                    damage,
                    Map.of("lifesteal", lifesteal)
            );
        }

        public Skill build() {
            return new GenericActiveSkill(buildDefinition());
        }
    }

    /**
     * Default generic active skill driven entirely by {@link SkillDefinition}.
     * Subclass or replace via {@link #registerType} for custom behaviours.
     */
    public static class GenericActiveSkill implements ActiveSkill {

        protected final SkillDefinition definition;

        public GenericActiveSkill(SkillDefinition definition) {
            this.definition = definition;
        }

        public SkillDefinition getDefinition() {
            return definition;
        }

        @Override
        public String getId() {
            return definition.getId();
        }

        @Override
        public String getDisplayName() {
            return definition.getDisplayName();
        }

        @Override
        public com.skillcore.api.SkillTrigger getTrigger() {
            return definition.getTrigger();
        }

        @Override
        public double getCooldown() {
            return definition.getCooldown();
        }

        @Override
        public double getManaCost() {
            return definition.getManaCost();
        }

        @Override
        public String getPermission() {
            return definition.getPermission();
        }

        @Override
        public String getDescription() {
            return definition.getDescription();
        }

        @Override
        public boolean canCast(SkillContext context) {
            return context != null && context.getCaster() != null && DamageUtils.isAlive(context.getCaster());
        }

        @Override
        public SkillResult cast(SkillContext context) {
            SkillResult validate = validate(context);
            if (!validate.isSuccess()) {
                onFail(context, validate);
                return validate;
            }
            double power = context.getPower();
            var dmg = definition.getDamage();

            LivingEntityDamage(context, dmg, power);

            double lifesteal = definition.getExtraDouble("lifesteal", 0);
            if (lifesteal > 0 && context.hasTarget()) {
                LifestealUtils.healByDamagePercent(context.getCaster(), dmg.base(), lifesteal);
            }

            onSuccess(context);
            return SkillResult.SUCCESS;
        }

        private void LivingEntityDamage(SkillContext context, SkillDefinition.DamageProfile dmg, double power) {
            if (!context.hasTarget()) {
                return;
            }
            var target = context.getTarget();
            var caster = context.getCaster();
            double damage = DamageUtils.calculateFinalDamage(
                    dmg.base(), dmg.scaling(), power,
                    dmg.criticalChance(), dmg.criticalMultiplier(),
                    dmg.armorPenetration(), target
            );
            // percentage components
            if (dmg.percentMaxHealth() > 0) {
                damage += PercentageDamageUtils.ofMaxHealthCapped(target, dmg.percentMaxHealth(), dmg.maxPercentDamage());
            }
            if (dmg.percentCurrentHealth() > 0) {
                damage += PercentageDamageUtils.ofCurrentHealth(target, dmg.percentCurrentHealth());
            }
            if (dmg.percentMissingHealth() > 0) {
                damage += PercentageDamageUtils.ofMissingHealth(target, dmg.percentMissingHealth());
            }
            if (damage > 0) {
                DamageUtils.damage(target, damage, caster);
            }
        }
    }
}
