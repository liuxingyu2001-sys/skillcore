package com.skillcore.listener;

import com.skillcore.SkillCorePlugin;
import com.skillcore.api.Skill;
import com.skillcore.api.SkillContext;
import com.skillcore.api.SkillResult;
import com.skillcore.api.SkillTrigger;
import com.skillcore.factory.SkillRegistry;
import com.skillcore.manager.SkillManager;
import com.skillcore.utils.MessageUtils;
import com.skillcore.utils.SoundUtils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Left-click / right-click skill input listener.
 * <p>
 * Routes player clicks to skills via {@link SkillTrigger} binding.
 * Also supports per-player bound skill ids (via {@link #bindSkill}).
 */
public final class SkillInputListener implements Listener {

    private final SkillCorePlugin plugin;
    private final SkillManager skillManager;
    private final SkillRegistry registry;

    /**
     * Explicit per-player key binding: player -> trigger -> skillId
     */
    private final Map<UUID, Map<SkillTrigger, String>> bindings = new HashMap<>();

    public SkillInputListener(SkillCorePlugin plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.registry = skillManager.getRegistry();
    }

    // ------------------------------------------------------------------
    // Bindings
    // ------------------------------------------------------------------

    /**
     * Bind a skill to a specific trigger for a player (skill bar style).
     */
    public void bindSkill(UUID playerId, SkillTrigger trigger, String skillId) {
        bindings.computeIfAbsent(playerId, k -> new HashMap<>()).put(trigger, skillId);
    }

    public void unbind(UUID playerId, SkillTrigger trigger) {
        Map<SkillTrigger, String> map = bindings.get(playerId);
        if (map != null) {
            map.remove(trigger);
        }
    }

    public void clearBindings(UUID playerId) {
        bindings.remove(playerId);
    }

    public String getBoundSkill(UUID playerId, SkillTrigger trigger) {
        Map<SkillTrigger, String> map = bindings.get(playerId);
        return map == null ? null : map.get(trigger);
    }

    // ------------------------------------------------------------------
    // Click handling
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // only main hand to avoid double-fire
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        if (!leftClick && !rightClick) {
            return;
        }

        Player player = event.getPlayer();
        boolean sneak = player.isSneaking();
        SkillTrigger trigger = resolveTrigger(leftClick, sneak);
        if (trigger == null) {
            return;
        }

        Optional<Skill> skillOpt = findSkill(player, trigger);
        if (skillOpt.isEmpty()) {
            return;
        }

        Skill skill = skillOpt.get();
        ItemStack item = event.getItem();
        LivingEntity target = com.skillcore.utils.AimUtils.raycastEntity(player, 6.0);

        SkillContext context = SkillContext.builder(player)
                .target(target)
                .itemInHand(item)
                .origin(player.getEyeLocation())
                .targetLocation(target != null
                        ? target.getLocation()
                        : com.skillcore.utils.AimUtils.getAimLocation(player, 12.0))
                .build();

        SkillResult result = skillManager.cast(skill, context);
        handleResult(player, skill, result);

        // cancel block interaction when a skill actually fired
        if (result == SkillResult.SUCCESS || result == SkillResult.ON_COOLDOWN || result == SkillResult.NO_MANA) {
            if (event.hasItem()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Right-click / left-click on entity.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        boolean sneak = player.isSneaking();
        // entity interact is always "right click"
        SkillTrigger trigger = resolveTrigger(false, sneak);
        if (trigger == null) {
            return;
        }

        Optional<Skill> skillOpt = findSkill(player, trigger);
        if (skillOpt.isEmpty()) {
            return;
        }

        Skill skill = skillOpt.get();
        LivingEntity target = event.getRightClicked() instanceof LivingEntity living ? living : null;

        SkillContext context = SkillContext.builder(player)
                .target(target)
                .itemInHand(player.getInventory().getItemInMainHand())
                .origin(player.getEyeLocation())
                .build();

        SkillResult result = skillManager.cast(skill, context);
        handleResult(player, skill, result);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SkillTrigger resolveTrigger(boolean leftClick, boolean sneak) {
        if (leftClick && sneak) {
            return SkillTrigger.SHIFT_LEFT_CLICK;
        }
        if (!leftClick && sneak) {
            return SkillTrigger.SHIFT_RIGHT_CLICK;
        }
        if (leftClick) {
            return SkillTrigger.LEFT_CLICK;
        }
        return SkillTrigger.RIGHT_CLICK;
    }

    /**
     * Find skill: explicit binding first, then registry by trigger.
     */
    private Optional<Skill> findSkill(Player player, SkillTrigger trigger) {
        String bound = getBoundSkill(player.getUniqueId(), trigger);
        if (bound != null) {
            Optional<Skill> skill = registry.get(bound);
            if (skill.isPresent()) {
                return skill;
            }
        }
        // fall back: first registered skill for this trigger
        return registry.firstByTrigger(trigger);
    }

    private void handleResult(Player player, Skill skill, SkillResult result) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        switch (result) {
            case SUCCESS -> {
                if (plugin.getConfig().getBoolean("messages.skill-used.enabled", true)) {
                    String msg = plugin.getConfig().getString("messages.skill-used",
                            "&a你使用了 &e{skill}&a!");
                    MessageUtils.sendPrefixed(player, prefix,
                            MessageUtils.placeholder(msg, "skill", skill.getDisplayName()));
                }
                SoundUtils.castSuccess(player);
                if (plugin.getConfig().getBoolean("logging.log-skill-cast", true)) {
                    plugin.getLogger().info(player.getName() + " cast " + skill.getId());
                }
            }
            case ON_COOLDOWN -> {
                String msg = plugin.getConfig().getString("messages.skill-cooldown",
                        "&c技能冷却中: &e{skill} &7({remaining}s)");
                double remaining = skillManager.getCooldownRemaining(player, skill.getId());
                MessageUtils.sendPrefixed(player, prefix,
                        MessageUtils.placeholder(msg,
                                "skill", skill.getDisplayName(),
                                "remaining", com.skillcore.utils.MathUtils.format1(remaining)));
            }
            case NO_MANA -> {
                String msg = plugin.getConfig().getString("messages.skill-no-mana",
                        "&c法力不足: &e{skill} &7({cost} 需要)");
                MessageUtils.sendPrefixed(player, prefix,
                        MessageUtils.placeholder(msg,
                                "skill", skill.getDisplayName(),
                                "cost", String.valueOf(skill.getManaCost()),
                                "current", String.valueOf((int) skillManager.getMana(player))));
            }
            case NO_PERMISSION -> {
                String msg = plugin.getConfig().getString("messages.no-permission",
                        "&c你没有权限使用该技能.");
                MessageUtils.send(player, prefix + msg);
            }
            case NO_TARGET -> {
                MessageUtils.sendActionBar(player, "&c没有目标");
            }
            default -> {
                // silent fail
            }
        }
    }
}
