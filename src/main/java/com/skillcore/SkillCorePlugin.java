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
 * 新武器: 写 WeaponSkill + registerSkill + weapons.yml 填数值。
 */
public final class SkillCorePlugin extends JavaPlugin {

    private static SkillCorePlugin instance;

    private ConfigManager configManager;
    private CombatListener combatListener;

    private WeaponFactory weaponFactory;
    private WeaponRegistry weaponRegistry;
    private WeaponManager weaponManager;
    private WeaponInputListener weaponInputListener;

    public static SkillCorePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.load();

        weaponFactory = new WeaponFactory();
        weaponRegistry = new WeaponRegistry();
        weaponManager = new WeaponManager(weaponRegistry, weaponFactory);
        weaponManager.setDebug(configManager.isDebug());
        registerWeaponSkills();
        int weaponCount = configManager.loadWeapons(weaponFactory, weaponRegistry);

        // 只监听技能武器左右键 — 没有武器就没有技能
        combatListener = new CombatListener(this);
        weaponInputListener = new WeaponInputListener(weaponManager);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(weaponInputListener, this);

        SkillCoreCommand command = new SkillCoreCommand(this);
        if (getCommand("skillcore") != null) {
            getCommand("skillcore").setExecutor(command);
            getCommand("skillcore").setTabCompleter(command);
        }

        getLogger().info("SkillCore enabled. Skill weapons: " + weaponCount);
    }

    @Override
    public void onDisable() {
        getLogger().info("SkillCore disabled.");
        instance = null;
    }

    /**
     * 注册武器技能类型（以后新武器在这里加）。
     */
    private void registerWeaponSkills() {
        // 内置: STRIKE / DASH_DAMAGE / BLADE_DASH / SWEEP / CHARGE
        //        PERCENT_STRIKE / CONTROL_STRIKE / BLINK_BURST / LIFESTEAL_STRIKE / THORNS
        // weaponFactory.registerSkill("MY_TYPE", MyWeaponSkill::new);
    }

    public void reloadAll() {
        configManager.reload();
        configManager.loadWeapons(weaponFactory, weaponRegistry);
        weaponManager.setDebug(configManager.isDebug());
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
}
