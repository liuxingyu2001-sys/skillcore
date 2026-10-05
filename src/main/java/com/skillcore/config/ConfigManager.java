package com.skillcore.config;

import com.skillcore.SkillCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * 配置加载 — 只加载 config.yml + weapons.yml。
 * 技能武器是唯一技能来源，无独立技能绑定。
 */
public final class ConfigManager {

    private final SkillCorePlugin plugin;
    private FileConfiguration config;
    private FileConfiguration weaponsConfig;

    public ConfigManager(SkillCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        File weaponsFile = new File(plugin.getDataFolder(), "weapons.yml");
        if (!weaponsFile.exists()) {
            plugin.saveResource("weapons.yml", false);
        }
        this.weaponsConfig = YamlConfiguration.loadConfiguration(weaponsFile);
        try (InputStream stream = plugin.getResource("weapons.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                weaponsConfig.setDefaults(defaults);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load weapons.yml defaults", ex);
        }
    }

    public void reload() {
        load();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getWeaponsConfig() {
        return weaponsConfig;
    }

    /**
     * 从 weapons.yml 加载全部技能武器。
     */
    public int loadWeapons(com.skillcore.weapon.WeaponFactory factory,
                           com.skillcore.weapon.WeaponRegistry registry) {
        registry.clear();
        ConfigurationSection root = weaponsConfig.getConfigurationSection("weapons");
        if (root == null) {
            plugin.getLogger().warning("No 'weapons' section found in weapons.yml");
            return 0;
        }
        int count = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                var weapon = factory.parse(id, section);
                var rightSkill = factory.createSkill(weapon.rightSkillType(), weapon);
                var leftSkill = weapon.leftSkillType() != null
                        ? factory.createSkill(weapon.leftSkillType(), weapon)
                        : null;
                registry.register(weapon, rightSkill, leftSkill);
                count++;
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load weapon: " + id, ex);
            }
        }
        plugin.getLogger().info("Loaded " + count + " skill weapon(s) from weapons.yml");
        return count;
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    public String getMessage(String path, String def) {
        return config.getString("messages." + path, def);
    }

    public String getPrefix() {
        return config.getString("messages.prefix", "");
    }
}
