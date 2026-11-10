package com.skillcore.weapon;

import com.skillcore.weapon.SkillTrigger;
import com.skillcore.utils.CooldownUtils;
import com.skillcore.utils.MessageUtils;
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
        String weaponId = WeaponItems.getWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), item);
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
        // 纯被动技能（没覆写对应点击钩子）不消耗点击、不进冷却、不取消交互事件
        if (!supportsTrigger(skill, trigger)) return false;

        boolean leftClick = trigger == SkillTrigger.LEFT_CLICK || trigger == SkillTrigger.SHIFT_LEFT_CLICK;
        boolean sneak = trigger.isShift();
        boolean bypass = bypassCooldown(player);
        // 冷却键 = 武器 + 键位 + 技能类型：左右键技能、不同技能之间都互不影响
        String cdKey = cooldownKey(weapon.id(), skill, leftClick);

        // 1. 先查冷却：冷却中直接返回，不做射线检测/上下文构造（性能优化）
        if (!bypass && CooldownUtils.isOnCooldown(player.getUniqueId(), cdKey)) {
            promptCooldown(player, cdKey, leftClick);
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
        // 左键技能走 cooldown-left，右键/双击/按住/松开走 cooldown-right，未配置时各自使用独立默认值
        if (startsCooldown(trigger) && !bypass) {
            double cd = cooldownFor(weapon.stats(), trigger);
            if (cd > 0) {
                CooldownUtils.start(player.getUniqueId(), cdKey, cd);
            }
        }

        if (debug) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger().info(
                    player.getName() + " used weapon " + weapon.id()
                            + " skill=" + skill.getType() + " trigger=" + trigger
                            + " cdKey=" + cdKey + " cd=" + cooldownFor(weapon.stats(), trigger));
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
     * <p>
     * 左右键也必须检查：把纯被动技能（例如只实现 {@code onHit} 的日炎之剑）绑到
     * {@code right-skill} 时，不检查就会「点了没效果，但事件被取消」——箱子/门/食物都用不了。
     */
    private boolean supportsTrigger(WeaponSkill skill, SkillTrigger trigger) {
        String method = switch (trigger) {
            case LEFT_CLICK -> "onLeftClick";
            case RIGHT_CLICK, BOTH_CLICK -> "onRightClick";
            case SHIFT_LEFT_CLICK -> "onShiftLeftClick";
            case SHIFT_RIGHT_CLICK -> "onShiftRightClick";
            case DOUBLE_SHIFT -> "onDoubleShift";
            case HOLD_SHIFT -> "onHoldShift";
            case RELEASE_SHIFT -> "onReleaseShift";
            case COMMAND -> null;
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

    /**
     * 冷却键：{@code weapon:<武器id>:<left|right>:<技能类型>}。
     * <p>
     * 键里同时带「键位」与「技能类型」，两个维度都必须独立：
     * <ul>
     *   <li><b>左右键互不影响</b>：左键技能的冷却不会挡住右键技能，反之亦然；</li>
     *   <li>同一键位下的不同技能互不影响（例如右键技能与 Shift+右键的另一个技能）；</li>
     *   <li>同一技能的不同触发方式（Shift+左键 / 左键）仍然是同一条记录，
     *       避免用 Shift 组合绕过冷却连续释放。</li>
     * </ul>
     */
    private static String cooldownKey(String weaponId, WeaponSkill skill, boolean leftClick) {
        String id = weaponId == null ? "" : weaponId.trim().toLowerCase(java.util.Locale.ROOT);
        String side = leftClick ? "left" : "right";
        String type = skill == null ? null : skill.getType();
        if (type == null || type.isBlank()) {
            type = side;
        }
        return "weapon:" + id + ":" + side + ":" + type.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 一把武器全部冷却键的前缀（清理 / 展示用）。 */
    private static String cooldownPrefix(String weaponId) {
        String id = weaponId == null ? "" : weaponId.trim().toLowerCase(java.util.Locale.ROOT);
        return "weapon:" + id + ":";
    }

    /**
     * 按触发键取对应技能的冷却秒数。
     * <p>
     * 左键（含 Shift+左键）走 {@code cooldown-left}，其余触发
     * （右键 / Shift+右键 / 双击 / 按住 / 松开）走 {@code cooldown-right}。
     * <b>左右键完全独立</b>：只有右键会回退到旧版共享键 {@code cooldown}（兼容老配置），
     * 左键未写 {@code cooldown-left} 就是 0 秒（无冷却），不会再借用右键的冷却值。
     */
    private double cooldownFor(WeaponStats stats, SkillTrigger trigger) {
        boolean leftClick = trigger == SkillTrigger.LEFT_CLICK || trigger == SkillTrigger.SHIFT_LEFT_CLICK;
        return leftClick ? stats.cooldownLeft() : stats.cooldownRight();
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
     * 冷却提示（纯动作栏文本，不发音效），按 {@code cooldown.prompt-interval-ms} 节流，
     * 避免冷却中狂按导致提示刷屏。
     * <p>
     * 文案里带上「左键 / 右键技能」，让玩家一眼看出是哪个技能在冷却
     * （两个技能各自计时，不会互相顶掉）。
     */
    private void promptCooldown(Player player, String cdKey, boolean leftClick) {
        long now = System.currentTimeMillis();
        Long last = lastCooldownPrompt.get(player.getUniqueId());
        if (last != null && now - last < promptIntervalMs()) {
            return;
        }
        lastCooldownPrompt.put(player.getUniqueId(), now);
        double remain = CooldownUtils.getRemaining(player.getUniqueId(), cdKey);
        MessageUtils.sendActionBar(player, (leftClick ? "&c左键技能冷却中 " : "&c右键技能冷却中 ")
                + "&f" + String.format(java.util.Locale.ROOT, "%.1f", remain) + "s");
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
        if (player == null || trigger == null) return 0.0;
        boolean leftClick = trigger == SkillTrigger.LEFT_CLICK || trigger == SkillTrigger.SHIFT_LEFT_CLICK;
        WeaponRegistry.Entry entry = registry.getEntry(weaponId).orElse(null);
        if (entry == null) return 0.0;
        WeaponSkill skill = entry.resolve(leftClick);
        if (skill == null) return 0.0;
        return CooldownUtils.getRemaining(player.getUniqueId(), cooldownKey(weaponId, skill, leftClick));
    }

    /**
     * 当前玩家在某把武器上的全部冷却快照：子键（如 {@code right:blood_moon_throw}）→ 剩余秒。
     * <p>
     * 给 {@code /sc cooldown <武器id>} 的「查看当前冷却」用，可以直接看到左右键是两条独立记录。
     */
    public Map<String, Double> cooldownSnapshot(Player player, String weaponId) {
        if (player == null) return Map.of();
        return CooldownUtils.snapshot(player.getUniqueId(), cooldownPrefix(weaponId));
    }

    /**
     * 清除指定武器左右两侧的全部冷却（玩家为 null 时忽略；武器 id 为空则清空该玩家所有冷却）。
     */
    public void clearCooldown(Player player, String weaponId) {
        if (player == null) return;
        String id = weaponId == null ? "" : weaponId.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty()) {
            CooldownUtils.clearAll(player.getUniqueId());
            return;
        }
        // 一把武器下可能有多条记录（左右键 × 技能类型），按前缀清干净
        CooldownUtils.clearByPrefix(player.getUniqueId(), cooldownPrefix(id));
    }

    /**
     * 重新读取指定武器的配置并替换注册表中的条目（{@code /sc cooldown} 写回后使用）。
     * <p>
     * 只替换该武器，不影响其他武器正在运行的状态。
     *
     * @return 是否替换成功
     */
    public boolean reloadWeapon(String weaponId) {
        if (weaponId == null) return false;
        var configManager = com.skillcore.SkillCorePlugin.getInstance().getConfigManager();
        if (!configManager.reloadSkillFile(weaponId)) return false;
        String key = weaponId.trim().toLowerCase(java.util.Locale.ROOT);
        var section = configManager.getSkillConfig(key);
        if (section == null) return false;
        try {
            SkillWeapon weapon = factory.parse(key, section);
            WeaponSkill right = factory.createSkill(weapon.rightSkillType(), weapon);
            WeaponSkill left = weapon.leftSkillType() != null
                    ? factory.createSkill(weapon.leftSkillType(), weapon)
                    : null;
            // register 覆盖同 id 条目时会自动清理旧实例的持久状态，这里不再重复 cleanup
            registry.register(weapon, right, left);
            // 冷却写回后必须清掉旧记录，否则 /sc cooldown <id> 0 看起来「生效了」但玩家仍在冷却
            clearCooldownEverywhere(key);
            return true;
        } catch (Exception ex) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger()
                    .severe("重载武器配置失败 [" + weaponId + "]: " + ex);
            return false;
        }
    }

    /** 清除所有在线玩家在这把武器上的全部冷却记录（左右键 × 技能类型）。 */
    private void clearCooldownEverywhere(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return;
        String prefix = cooldownPrefix(weaponId);
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            CooldownUtils.clearByPrefix(p.getUniqueId(), prefix);
        }
    }

    /**
     * 玩家退出时通知所有武器技能清理该玩家的状态（状态表等）。
     */
    public void notifyQuit(Player player) {
        if (player == null) return;
        // 先取消该玩家未结算的多段伤害任务，避免退出后仍在结算（且闭包还持有旧 stats）
        WeaponContext.cancelPendingHits(player.getUniqueId());
        for (WeaponRegistry.Entry entry : registry.all()) {
            safeOnQuit(entry.skill(), player);
            if (entry.leftSkill() != entry.skill()) {
                safeOnQuit(entry.leftSkill(), player);
            }
        }
    }

    private void safeOnQuit(WeaponSkill skill, Player player) {
        if (skill == null) return;
        try {
            skill.onQuit(player);
        } catch (Exception ex) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger()
                    .warning("Weapon skill quit cleanup error [" + skill.getType() + "]: " + ex);
        }
    }

    /**
     * 对一把武器的左右技能各执行一次（左右共用同一实例时只执行一次）。
     * <p>
     * 被动回调（onHit / onKill / onDamaged）不属于某一侧点击，
     * 左右技能都应该有机会响应。
     */
    private static void forEachSkill(WeaponRegistry.Entry entry, java.util.function.Consumer<WeaponSkill> action) {
        if (entry == null || action == null) return;
        WeaponSkill right = entry.skill();
        if (right != null) safePassive(right, action);
        WeaponSkill left = entry.leftSkill();
        if (left != null && left != right) safePassive(left, action);
    }

    /**
     * 被动回调统一包一层 try/catch。
     * <p>
     * 这些回调跑在 {@code EntityDamageByEntityEvent} / {@code EntityDeathEvent} 里，
     * 技能抛异常会连累同一事件里的吸血、反伤、减伤逻辑一起失效，还会刷屏。
     */
    private static void safePassive(WeaponSkill skill, java.util.function.Consumer<WeaponSkill> action) {
        try {
            action.accept(skill);
        } catch (Exception ex) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger()
                    .warning("Weapon skill callback error [" + skill.getType() + "]: " + ex);
        }
    }

    /**
     * 受击时通知武器技能（反伤类）。
     */
    public void notifyDamaged(Player player, LivingEntity attacker, double damage) {
        if (player == null || attacker == null || attacker.equals(player)) return;
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) return;
        registry.getEntry(weaponId).ifPresent(entry -> forEachSkill(entry, skill -> {
            WeaponContext ctx = new WeaponContext(
                    player, entry.weapon(), attacker,
                    attacker.getLocation(),
                    player.getInventory().getItemInMainHand(),
                    false, false);
            skill.onDamaged(ctx, attacker, damage);
        }));
    }

    /**
     * 普通近战命中时通知武器技能（命中附带效果，如日炎之剑）。
     * 调用方（CombatListener）会跳过技能伤害，避免技能命中重复触发。
     */
    public void notifyHit(Player player, LivingEntity victim, double damage) {
        if (player == null || victim == null || !victim.isValid()) return;
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) return;
        registry.getEntry(weaponId).ifPresent(entry -> forEachSkill(entry, skill -> {
            WeaponContext ctx = new WeaponContext(
                    player, entry.weapon(), victim,
                    victim.getLocation(),
                    player.getInventory().getItemInMainHand(),
                    false, false);
            skill.onHit(ctx, victim, damage);
        }));
    }

    /**
     * 击杀时通知武器技能。
     */
    public void notifyKill(Player player, LivingEntity victim) {
        if (player == null || victim == null) return;
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) return;
        registry.getEntry(weaponId).ifPresent(entry -> forEachSkill(entry, skill -> {
            WeaponContext ctx = new WeaponContext(
                    player, entry.weapon(), victim,
                    victim.getLocation(),
                    player.getInventory().getItemInMainHand(),
                    false, false);
            skill.onKill(ctx, victim);
        }));
    }

    /**
     * 清理所有武器技能持有的状态（插件禁用 / 重载时）。
     */
    public void cleanup() {
        registry.cleanup();
        WeaponContext.cancelAllPendingHits();
        overrideCache.clear();
        lastCooldownPrompt.clear();
    }

    /**
     * 发放武器给玩家（lore 已填入当前数值）。
     */
    public ItemStack giveWeapon(Player player, String weaponId) {
        if (player == null) return null;
        SkillWeapon weapon = registry.get(weaponId);
        if (weapon == null) return null;
        ItemStack item = weapon.toItemStack();
        // 背包满时把剩余物品掉落在脚下，避免“显示发放成功但物品消失”
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
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
        // PlayerInventory#getContents() 已包含盔甲槽与副手，只遍历一次即可
        // （getArmorContents() 每次返回新的镜像对象，身份去重对它无效，会重复计数）
        java.util.Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !seen.add(item)) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id != null && id.equalsIgnoreCase(weaponId)) {
                if (WeaponItems.updateLore(item, weapon)) updated++;
            }
        }
        player.updateInventory();
        return updated;
    }

    /**
     * 修复历史 glow 写法留下的物品：旧版用「隐藏附魔 海之眷顾 I + HIDE_ENCHANTS」实现发光，
     * 会把物品上所有附魔行一起藏掉（「不显示任何附魔」）。重建一次物品即可清掉遗留并恢复显示。
     *
     * @return 修复的物品数量
     */
    public int migrateLegacyGlowItems(Player player) {
        if (player == null) return 0;
        var plugin = com.skillcore.SkillCorePlugin.getInstance();
        int fixed = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!WeaponItems.isLegacyGlowItem(item)) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id == null) continue;
            SkillWeapon weapon = registry.get(id);
            if (weapon != null && WeaponItems.updateLore(item, weapon)) {
                fixed++;
            }
        }
        if (fixed > 0) {
            player.updateInventory();
        }
        return fixed;
    }

    /** 对所有在线玩家跑一次历史 glow 修复（插件启用时调用，玩家上线时也会各跑一次）。 */
    public int migrateLegacyGlowItemsAll() {
        int total = 0;
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            total += migrateLegacyGlowItems(player);
        }
        return total;
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
