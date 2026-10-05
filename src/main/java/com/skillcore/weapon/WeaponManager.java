package com.skillcore.weapon;

import com.skillcore.weapon.SkillTrigger;
import com.skillcore.utils.CooldownUtils;
import com.skillcore.utils.MessageUtils;
import com.skillcore.utils.SoundUtils;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器施法管理 — 只有冷却系统，无法力。
 * <p>
 * 统一派发左键 / 右键 / Shift 组合 / 双击 Shift / 按住 Shift / 松开 Shift。
 */
public final class WeaponManager {

    private final WeaponRegistry registry;
    private final WeaponFactory factory;
    private boolean debug;

    /** 技能类是否覆写某触发方法（反射缓存，避免每次事件都反射）。 */
    private final Map<String, Boolean> overrideCache = new ConcurrentHashMap<>();

    /** 冷却提示节流：player -> 上次提示时间(ms)，避免冷却中狂按导致刷屏/音效轰炸。 */
    private final Map<UUID, Long> lastCooldownPrompt = new ConcurrentHashMap<>();

    public WeaponManager(WeaponRegistry registry, WeaponFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    public WeaponRegistry registry() { return registry; }
    public WeaponFactory factory() { return factory; }
    public void setDebug(boolean debug) { this.debug = debug; }

    /**
     * 兼容旧调用：左键/右键 + 潜行。
     */
    public boolean triggerHeld(Player player, boolean leftClick, boolean sneak) {
        SkillTrigger trigger;
        if (leftClick) {
            trigger = sneak ? SkillTrigger.SHIFT_LEFT_CLICK : SkillTrigger.LEFT_CLICK;
        } else {
            trigger = sneak ? SkillTrigger.SHIFT_RIGHT_CLICK : SkillTrigger.RIGHT_CLICK;
        }
        return triggerHeld(player, trigger);
    }

    /**
     * 用主手武器按指定触发释放技能。
     */
    public boolean triggerHeld(Player player, SkillTrigger trigger) {
        if (player == null || trigger == null) return false;
        ItemStack item = player.getInventory().getItemInMainHand();
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) return false;

        WeaponRegistry.Entry entry = registry.getEntry(weaponId).orElse(null);
        if (entry == null) return false;

        boolean leftClick = trigger == SkillTrigger.LEFT_CLICK || trigger == SkillTrigger.SHIFT_LEFT_CLICK;
        WeaponSkill skill = entry.resolve(leftClick);
        if (skill == null) return false;

        // 双击/按住/松开：技能未实现则不触发，避免空放进入冷却
        if (!supportsTrigger(skill, trigger)) {
            return false;
        }

        return cast(entry.weapon(), skill, player, trigger, item);
    }

    /**
     * 完整释放流程（冷却检查 → 执行 → 冷却启动）。
     */
    public boolean cast(
            SkillWeapon weapon,
            WeaponSkill skill,
            Player player,
            SkillTrigger trigger,
            ItemStack item
    ) {
        if (weapon == null || skill == null || player == null || trigger == null) return false;

        boolean leftClick = trigger == SkillTrigger.LEFT_CLICK || trigger == SkillTrigger.SHIFT_LEFT_CLICK;
        boolean sneak = trigger.isShift();
        boolean bypass = bypassCooldown(player);
        String cdKey = cooldownKey(weapon.id(), trigger);

        // 1. 先查冷却：冷却中直接返回，不做射线检测/上下文构造（性能优化）
        if (!bypass && CooldownUtils.isOnCooldown(player.getUniqueId(), cdKey)) {
            promptCooldown(player, cdKey);
            return true;
        }

        // 2. 构造上下文：只做一次实体射线，瞄准点复用该结果（避免重复 raycast）
        double aimRange = weapon.stats().aimRange();
        LivingEntity target = com.skillcore.utils.AimUtils.raycastEntity(player, aimRange);
        WeaponContext ctx = new WeaponContext(
                player, weapon, target,
                resolveAimLocation(player, aimRange, target),
                item, leftClick, sneak
        );

        if (!skill.canUse(ctx)) {
            return false;
        }

        // 3. 执行技能（仅当手持该武器时 WeaponInputListener 才会调到这里）
        try {
            dispatch(skill, ctx, trigger);
        } catch (Exception ex) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger()
                    .severe("Weapon skill error [" + weapon.id() + "/" + skill.getType() + "/" + trigger + "]: " + ex);
            ex.printStackTrace();
            return false;
        }

        // 4. 冷却启动（松开 Shift 不启动冷却）
        if (startsCooldown(trigger) && !bypass && weapon.stats().cooldown() > 0) {
            CooldownUtils.start(player.getUniqueId(), cdKey, weapon.stats().cooldown());
        }

        if (debug) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger().info(
                    player.getName() + " used weapon " + weapon.id()
                            + " skill=" + skill.getType() + " trigger=" + trigger);
        }
        return true;
    }

    /**
     * 兼容旧调用：左键/右键 + 潜行。
     */
    public boolean cast(
            SkillWeapon weapon,
            WeaponSkill skill,
            Player player,
            boolean leftClick,
            boolean sneak,
            ItemStack item
    ) {
        SkillTrigger trigger;
        if (leftClick) {
            trigger = sneak ? SkillTrigger.SHIFT_LEFT_CLICK : SkillTrigger.LEFT_CLICK;
        } else {
            trigger = sneak ? SkillTrigger.SHIFT_RIGHT_CLICK : SkillTrigger.RIGHT_CLICK;
        }
        return cast(weapon, skill, player, trigger, item);
    }

    private void dispatch(WeaponSkill skill, WeaponContext ctx, SkillTrigger trigger) {
        switch (trigger) {
            case LEFT_CLICK -> skill.onLeftClick(ctx);
            case RIGHT_CLICK -> skill.onRightClick(ctx);
            case SHIFT_LEFT_CLICK -> skill.onShiftLeftClick(ctx);
            case SHIFT_RIGHT_CLICK -> skill.onShiftRightClick(ctx);
            case DOUBLE_SHIFT -> skill.onDoubleShift(ctx);
            case HOLD_SHIFT -> skill.onHoldShift(ctx);
            case RELEASE_SHIFT -> skill.onReleaseShift(ctx);
            case BOTH_CLICK -> skill.onRightClick(ctx);
            case COMMAND -> {
            }
        }
    }

    private boolean startsCooldown(SkillTrigger trigger) {
        // 按住/松开 Shift 属于同一次施法的阶段，冷却由主动触发（点击/双击）启动
        return switch (trigger) {
            case LEFT_CLICK, RIGHT_CLICK, SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK,
                 DOUBLE_SHIFT, BOTH_CLICK -> true;
            case HOLD_SHIFT, RELEASE_SHIFT, COMMAND -> false;
        };
    }

    /**
     * 技能是否覆写了该触发对应的钩子（默认方法不算）。
     */
    private boolean supportsTrigger(WeaponSkill skill, SkillTrigger trigger) {
        String method = switch (trigger) {
            case DOUBLE_SHIFT -> "onDoubleShift";
            case HOLD_SHIFT -> "onHoldShift";
            case RELEASE_SHIFT -> "onReleaseShift";
            default -> null;
        };
        if (method == null) return true;
        String cacheKey = skill.getClass().getName() + "#" + method;
        return overrideCache.computeIfAbsent(cacheKey, k -> {
            try {
                Method m = skill.getClass().getMethod(method, WeaponContext.class);
                return m.getDeclaringClass() != WeaponSkill.class;
            } catch (NoSuchMethodException ex) {
                return false;
            }
        });
    }

    private String cooldownKey(String weaponId, SkillTrigger trigger) {
        return "weapon:" + weaponId + ":" + trigger.name();
    }

    /**
     * 是否绕过冷却 — 默认关闭（所有人都吃冷却，包括 OP）。
     * 如需给测试员开绕过，在 config.yml 开启 {@code cooldown.bypass-permission}
     * 并授予 {@code skillcore.bypass.cooldown}。
     */
    private boolean bypassCooldown(Player player) {
        boolean enabled = com.skillcore.SkillCorePlugin.getInstance()
                .getConfigManager().getConfig()
                .getBoolean("cooldown.bypass-permission", false);
        return enabled && player.hasPermission("skillcore.bypass.cooldown");
    }

    /**
     * 计算瞄准点：优先复用已射中的实体（避免重复 raycast），否则取方块命中点或视线尽头。
     */
    private static Location resolveAimLocation(Player player, double maxDistance, LivingEntity target) {
        if (target != null) {
            return target.getLocation().add(0, target.getHeight() / 2.0, 0);
        }
        Location blockHit = com.skillcore.utils.AimUtils.raycastBlock(player, maxDistance);
        if (blockHit != null) {
            return blockHit;
        }
        return player.getEyeLocation().add(player.getLocation().getDirection().multiply(maxDistance));
    }

    /**
     * 冷却提示（动作栏 + 音效），按 {@code cooldown.prompt-interval-ms} 节流，
     * 避免冷却中狂按导致提示刷屏 / 音效轰炸。
     */
    private void promptCooldown(Player player, String cdKey) {
        long now = System.currentTimeMillis();
        Long last = lastCooldownPrompt.get(player.getUniqueId());
        if (last != null && now - last < promptIntervalMs()) {
            return;
        }
        lastCooldownPrompt.put(player.getUniqueId(), now);
        double remain = CooldownUtils.getRemaining(player.getUniqueId(), cdKey);
        MessageUtils.sendActionBar(player, "&c冷却中 &f" + String.format("%.1f", remain) + "s");
        SoundUtils.cooldown(player);
    }

    private int promptIntervalMs() {
        try {
            return com.skillcore.SkillCorePlugin.getInstance().getConfigManager()
                    .getConfig().getInt("cooldown.prompt-interval-ms", 750);
        } catch (Exception ex) {
            return 750;
        }
    }

    /** 玩家退出时清理提示节流状态。 */
    public void clearPlayerState(UUID playerId) {
        if (playerId != null) {
            lastCooldownPrompt.remove(playerId);
        }
    }

    /**
     * 剩余冷却（秒）。
     */
    public double getCooldownRemaining(Player player, String weaponId, boolean leftClick, boolean sneak) {
        SkillTrigger trigger;
        if (leftClick) {
            trigger = sneak ? SkillTrigger.SHIFT_LEFT_CLICK : SkillTrigger.LEFT_CLICK;
        } else {
            trigger = sneak ? SkillTrigger.SHIFT_RIGHT_CLICK : SkillTrigger.RIGHT_CLICK;
        }
        return getCooldownRemaining(player, weaponId, trigger);
    }

    public double getCooldownRemaining(Player player, String weaponId, SkillTrigger trigger) {
        return CooldownUtils.getRemaining(player.getUniqueId(), cooldownKey(weaponId, trigger));
    }

    /**
     * 清除冷却。
     */
    public void clearCooldown(Player player, String weaponId) {
        CooldownUtils.clearAll(player.getUniqueId());
    }

    /**
     * 受击时通知武器技能（反伤类）。
     */
    public void notifyDamaged(Player player, LivingEntity attacker, double damage) {
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) return;
        registry.getEntry(weaponId).ifPresent(entry -> {
            WeaponSkill skill = entry.resolve(false);
            if (skill != null) {
                WeaponContext ctx = new WeaponContext(
                        player, entry.weapon(), attacker,
                        attacker.getLocation(),
                        player.getInventory().getItemInMainHand(),
                        false, false);
                skill.onDamaged(ctx, attacker, damage);
            }
        });
    }

    /**
     * 清理所有武器技能持有的状态（插件禁用 / 重载时）。
     */
    public void cleanup() {
        registry.cleanup();
        overrideCache.clear();
        lastCooldownPrompt.clear();
    }

    /**
     * 发放武器给玩家（lore 已填入当前数值）。
     */
    public ItemStack giveWeapon(Player player, String weaponId) {
        SkillWeapon weapon = registry.get(weaponId);
        if (weapon == null) return null;
        ItemStack item = weapon.toItemStack();
        player.getInventory().addItem(item);
        return item;
    }

    /**
     * 刷新玩家背包中所有指定武器的 lore（管理员改配置后手动更新）。
     *
     * @return 更新数量
     */
    public int refreshLore(Player player, String weaponId) {
        if (player == null) return 0;
        SkillWeapon weapon = registry.get(weaponId);
        if (weapon == null) return 0;
        var plugin = com.skillcore.SkillCorePlugin.getInstance();
        int updated = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id != null && id.equalsIgnoreCase(weaponId)) {
                if (WeaponItems.updateLore(item, weapon)) updated++;
            }
        }
        // 盔甲等
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id != null && id.equalsIgnoreCase(weaponId)) {
                if (WeaponItems.updateLore(item, weapon)) updated++;
            }
        }
        player.updateInventory();
        return updated;
    }

    /**
     * 刷新玩家背包内全部技能武器的 lore。
     */
    public int refreshAllLore(Player player) {
        if (player == null) return 0;
        var plugin = com.skillcore.SkillCorePlugin.getInstance();
        int updated = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id == null) continue;
            SkillWeapon weapon = registry.get(id);
            if (weapon != null && WeaponItems.updateLore(item, weapon)) {
                updated++;
            }
        }
        player.updateInventory();
        return updated;
    }

    /**
     * 刷新所有在线玩家身上指定武器的 lore。
     */
    public int refreshLoreAllPlayers(String weaponId) {
        int total = 0;
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            total += refreshLore(p, weaponId);
        }
        return total;
    }

    /**
     * 手持的是否是指定武器。
     */
    public boolean isHolding(Player player, String weaponId) {
        String held = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        return held != null && held.equalsIgnoreCase(weaponId);
    }
}
