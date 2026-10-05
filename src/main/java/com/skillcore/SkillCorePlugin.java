package com.skillcore;

import com.skillcore.command.SkillCoreCommand;
import com.skillcore.config.ConfigManager;
import com.skillcore.listener.CombatListener;
import com.skillcore.listener.WeaponInputListener;
import com.skillcore.weapon.WeaponFactory;
import com.skillcore.weapon.WeaponManager;
import com.skillcore.weapon.WeaponRegistry;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SkillCore — 技能武器插件。
 * <p>
 * 规则：技能和武器绑定，只有手持对应武器才能使用技能。
 * 给玩家技能 = 直接发放制作好的武器，无需任何绑定/解锁。
 * <pre>
 * /sc give &lt;weaponId&gt; [player]   发放技能武器
 * /sc weapons                    列出武器
 * </pre>
 * 新武器: 写 WeaponSkill + @WeaponSkillInfo 自动注册 + skills/ 目录下建一个 yml 填数值。
 */
public final class SkillCorePlugin extends JavaPlugin {

    private static SkillCorePlugin instance;

    private ConfigManager configManager;
    private CombatListener combatListener;

    private WeaponFactory weaponFactory;
    private WeaponRegistry weaponRegistry;
    private WeaponManager weaponManager;
    private WeaponInputListener weaponInputListener;
    private com.skillcore.dummy.TestDummyManager testDummyManager;
    private com.skillcore.armor.ArmorFactory armorFactory;
    private com.skillcore.armor.ArmorRegistry armorRegistry;
    private com.skillcore.armor.ArmorManager armorManager;
    private com.skillcore.armor.ArmorListener armorListener;
    private org.bukkit.scheduler.BukkitTask armorTicker;
    private int armorTickCounter;
    private org.bukkit.scheduler.BukkitTask cooldownCleanupTask;

    public static SkillCorePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.load();
        configManager.startPolling();

        weaponFactory = new WeaponFactory();
        weaponRegistry = new WeaponRegistry();
        weaponManager = new WeaponManager(weaponRegistry, weaponFactory);
        weaponManager.setDebug(configManager.isDebug());
        registerWeaponSkills();
        int weaponCount = configManager.loadWeapons(weaponFactory, weaponRegistry);

        // 盔甲套装：armor/ 目录底层框架（集齐整套后被动加成生效）
        armorFactory = new com.skillcore.armor.ArmorFactory();
        armorFactory.autoRegister(this, com.skillcore.armor.ArmorFactory.DEFAULT_SKILL_PACKAGE);
        armorRegistry = new com.skillcore.armor.ArmorRegistry();
        int armorCount = configManager.loadArmors(armorFactory, armorRegistry);
        armorManager = new com.skillcore.armor.ArmorManager(this, armorRegistry, armorFactory);
        armorListener = new com.skillcore.armor.ArmorListener(this, armorManager);
        getServer().getPluginManager().registerEvents(armorListener, this);
        startArmorTicker();
        startCooldownCleanup();

        // 只监听技能武器左右键 — 没有武器就没有技能
        combatListener = new CombatListener(this);
        weaponInputListener = new WeaponInputListener(this, weaponManager);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(weaponInputListener, this);

        SkillCoreCommand command = new SkillCoreCommand(this);
        if (getCommand("skillcore") != null) {
            getCommand("skillcore").setExecutor(command);
            getCommand("skillcore").setTabCompleter(command);
        }

        registerPlaceholders();

        testDummyManager = new com.skillcore.dummy.TestDummyManager(this);
        getServer().getPluginManager().registerEvents(testDummyManager, this);

        getLogger().info("SkillCore enabled. Skill weapons: " + weaponCount + ", armor sets: " + armorCount);
    }

    /**
     * 盔甲周期任务：每 tick 派发盔甲技能 onTick，每 20 tick 兜底重算整套穿戴状态。
     * <p>
     * onTick 只遍历穿戴整套的玩家（activeSkills），空操作；穿戴检测通常由事件驱动，
     * 这里仅作兜底，且 getItemMeta 前先 hasItemMeta，成本很低。
     */
    private void startArmorTicker() {
        if (armorTicker != null) {
            armorTicker.cancel();
        }
        armorTickCounter = 0;
        armorTicker = getServer().getScheduler().runTaskTimer(this, () -> {
            if (armorManager == null) {
                return;
            }
            armorTickCounter++;
            armorManager.tickAllOnline();
            if (armorTickCounter % 20 == 0) {
                armorManager.recomputeAllOnline();
            }
        }, 1L, 1L);
    }

    /** 周期清理已过期的冷却记录，避免 COOLDOWNS 无限增长。 */
    private void startCooldownCleanup() {
        if (cooldownCleanupTask != null) {
            cooldownCleanupTask.cancel();
        }
        cooldownCleanupTask = getServer().getScheduler().runTaskTimer(this,
                com.skillcore.utils.CooldownUtils::cleanup, 20L * 60L, 20L * 60L);
    }

    /**
     * 注册 PlaceholderAPI 占位符（未安装 PAPI 时自动跳过）。
     */
    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new com.skillcore.hook.SkillCorePlaceholders(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        } catch (Throwable ex) {
            getLogger().warning("Failed to register PlaceholderAPI placeholders: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (configManager != null) {
            configManager.stopPolling();
        }
        if (weaponInputListener != null) {
            weaponInputListener.cleanup();
        }
        if (weaponManager != null) {
            weaponManager.cleanup();
        }
        if (armorTicker != null) {
            armorTicker.cancel();
            armorTicker = null;
        }
        if (cooldownCleanupTask != null) {
            cooldownCleanupTask.cancel();
            cooldownCleanupTask = null;
        }
        if (armorManager != null) {
            armorManager.clearAll();
        }
        com.skillcore.utils.CooldownUtils.cleanup();
        getLogger().info("SkillCore disabled.");
        instance = null;
    }

    /**
     * 注册武器技能类型。
     * <p>
     * 内置技能已在 {@link WeaponFactory} 构造时注册；这里再扫描
     * {@code com.skillcore.weapon.skills} 包，自动注册所有带
     * {@link com.skillcore.weapon.annotation.WeaponSkillInfo} 注解的技能类。
     */
    private void registerWeaponSkills() {
        weaponFactory.autoRegister(this, WeaponFactory.DEFAULT_SKILL_PACKAGE);
    }

    public void reloadAll() {
        if (weaponManager != null) {
            weaponManager.cleanup();
        }
        if (armorManager != null) {
            armorManager.clearAll();
        }
        configManager.reload();
        configManager.loadWeapons(weaponFactory, weaponRegistry);
        configManager.loadArmors(armorFactory, armorRegistry);
        weaponManager.setDebug(configManager.isDebug());
        if (armorManager != null) {
            armorManager.recomputeAllOnline();
        }
        if (testDummyManager != null) {
            testDummyManager.reload();
        }
        configManager.startPolling();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CombatListener getCombatListener() {
        return combatListener;
    }

    public WeaponFactory getWeaponFactory() {
        return weaponFactory;
    }

    public WeaponRegistry getWeaponRegistry() {
        return weaponRegistry;
    }

    public WeaponManager getWeaponManager() {
        return weaponManager;
    }

    public WeaponInputListener getWeaponInputListener() {
        return weaponInputListener;
    }

    public com.skillcore.dummy.TestDummyManager getTestDummyManager() {
        return testDummyManager;
    }

    public com.skillcore.armor.ArmorFactory getArmorFactory() {
        return armorFactory;
    }

    public com.skillcore.armor.ArmorRegistry getArmorRegistry() {
        return armorRegistry;
    }

    public com.skillcore.armor.ArmorManager getArmorManager() {
        return armorManager;
    }
}
