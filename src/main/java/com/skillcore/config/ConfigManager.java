package com.skillcore.config;

import com.skillcore.SkillCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * 配置加载 — config.yml + {@code skills/} 目录。
 * <p>
 * 技能配置采用「一个技能一个 yml」：每个文件 = 一把技能武器，
 * 统一放在 {@code skills/} 目录下（文件名即武器 id，也可用文件内 {@code id:} 覆盖）。
 * 只写该技能用到的数值键，不再自动填充大量无用默认键。
 * <p>
 * 支持 {@code shared_config_dir}：多台后端服务器共享同一份配置，
 * 并按 {@code config_poll_interval} 秒轮询文件变化自动重载。
 */
public final class ConfigManager {

    private final SkillCorePlugin plugin;
    private FileConfiguration config;

    /** id(小写) -> 技能文件配置 */
    private final Map<String, YamlConfiguration> skillConfigs = new LinkedHashMap<>();

    /** id(小写) -> 技能文件（用于回写配置，如 /sc cooldown） */
    private final Map<String, File> skillFiles = new LinkedHashMap<>();

    /** id(小写) -> 盔甲套装文件配置 */
    private final Map<String, YamlConfiguration> armorConfigs = new LinkedHashMap<>();

    /** id(小写) -> 盔甲套装文件 */
    private final Map<String, File> armorFiles = new LinkedHashMap<>();

    private File configFile;
    private File skillsDir;
    private File armorDir;
    private long configStamp;
    private long skillsStamp;
    private int skillsFileCount;
    private long armorStamp;
    private int armorFileCount;
    private String sharedDir = "";
    private BukkitTask pollTask;

    public ConfigManager(SkillCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        File dataFolder = plugin.getDataFolder();

        // 1. shared_config_dir 从本服 datafolder/config.yml 读取（bootstrap，不参与共享）
        File bootstrapFile = new File(dataFolder, "config.yml");
        String shared = YamlConfiguration.loadConfiguration(bootstrapFile)
                .getString("shared_config_dir", "").trim();
        this.sharedDir = shared;

        File baseDir = dataFolder;
        if (!shared.isEmpty()) {
            File dir = new File(shared);
            if (dir.exists() || dir.mkdirs()) {
                baseDir = dir;
            } else {
                plugin.getLogger().warning("无法创建 shared_config_dir: " + shared + "，回退到本服目录");
            }
        }

        this.configFile = new File(baseDir, "config.yml");
        if (!configFile.equals(bootstrapFile)) {
            copyIfMissing(bootstrapFile, configFile);
        }

        this.skillsDir = new File(baseDir, "skills");
        if (!skillsDir.exists() && !skillsDir.mkdirs()) {
            plugin.getLogger().warning("无法创建 skills 目录: " + skillsDir);
        }
        copyDefaultSkillFiles();

        this.armorDir = new File(baseDir, "armor");
        if (!armorDir.exists() && !armorDir.mkdirs()) {
            plugin.getLogger().warning("无法创建 armor 目录: " + armorDir);
        }
        copyDefaultArmorFiles();

        // 2. 加载 config.yml
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.configStamp = configFile.lastModified();
        com.skillcore.utils.TargetFilter.load(this.config);
        com.skillcore.effect.EffectSettings.load(this.config);

        // 3. 加载 skills/*.yml 与 armor/*.yml
        reloadSkills();
        reloadArmors();
    }

    /**
     * 首次运行把 jar 内置的 skills/*.yml 复制到 skills 目录（已存在则跳过）。
     */
    private void copyDefaultSkillFiles() {
        copyDefaultFiles("skills", skillsDir, "技能");
    }

    private void copyDefaultArmorFiles() {
        copyDefaultFiles("armor", armorDir, "盔甲");
    }

    /** 首次运行把 jar 内置的指定目录下 *.yml 复制到数据目录（已存在则跳过）。 */
    private void copyDefaultFiles(String dirPrefix, File targetDir, String label) {
        File source = pluginJarFile();
        if (source == null || targetDir == null) {
            return;
        }
        // 开发环境（target/classes 直接加载）时 source 是目录，不是 jar
        if (source.isDirectory()) {
            copyDefaultFilesFromDir(new File(source, dirPrefix), targetDir, dirPrefix, label);
            return;
        }
        if (!source.isFile()) {
            return;
        }
        String prefix = dirPrefix + "/";
        try (JarFile jar = new JarFile(source)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".yml")) {
                    continue;
                }
                String fileName = name.substring(prefix.length());
                File target = new File(targetDir, fileName);
                if (target.exists()) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, target.toPath());
                    plugin.getLogger().info("已复制默认" + label + "配置: " + prefix + fileName);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "复制内置" + label + "文件失败", ex);
        }
    }

    /** 从展开的目录（开发环境）复制默认配置。 */
    private void copyDefaultFilesFromDir(File sourceDir, File targetDir, String dirPrefix, String label) {
        if (sourceDir == null || !sourceDir.isDirectory()) {
            return;
        }
        File[] files = sourceDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            File target = new File(targetDir, file.getName());
            if (target.exists()) continue;
            try {
                Files.copy(file.toPath(), target.toPath());
                plugin.getLogger().info("已复制默认" + label + "配置: " + dirPrefix + "/" + file.getName());
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "复制内置" + label + "文件失败: " + file.getName(), ex);
            }
        }
    }

    /**
     * 重新读取 skills 目录下所有 yml。
     * <p>
     * 只读不写：缺失键一律用代码内置默认值，保持文件干净（只写用到的键）。
     */
    private void reloadSkills() {
        skillConfigs.clear();
        skillFiles.clear();
        File[] files = skillsDir == null ? new File[0]
                : skillsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        long stamp = 0;
        int count = 0;
        for (File file : files) {
            count++;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String fileName = file.getName();
            String base = fileName.substring(0, fileName.length() - 4);
            String id = normalizeId(cfg.getString("id"), base, file);
            if (id == null) continue;
            if (skillConfigs.containsKey(id)) {
                plugin.getLogger().warning("重复的技能 id: " + id + "（文件 " + fileName + " 覆盖了 "
                        + skillFiles.get(id).getName() + "）");
            }
            skillConfigs.put(id, cfg);
            skillFiles.put(id, file);
            stamp = Math.max(stamp, file.lastModified());
        }
        this.skillsStamp = stamp;
        this.skillsFileCount = count;
    }

    /**
     * 规范化配置里的 id：去空白、转小写；空值回退文件名。
     *
     * @return 规范化后的 id；文件名本身也非法时返回 null
     */
    private String normalizeId(String raw, String fallback, File file) {
        String id = raw == null || raw.isBlank() ? fallback : raw.trim();
        if (id == null || id.isBlank()) {
            plugin.getLogger().warning("跳过无法确定 id 的配置文件: " + file.getName());
            return null;
        }
        return id.toLowerCase(Locale.ROOT);
    }

    /**
     * 重新读取 armor 目录下所有 yml（只读不写，缺失键用内置默认值）。
     */
    private void reloadArmors() {
        armorConfigs.clear();
        armorFiles.clear();
        File[] files = armorDir == null ? new File[0]
                : armorDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        long stamp = 0;
        int count = 0;
        for (File file : files) {
            count++;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String fileName = file.getName();
            String base = fileName.substring(0, fileName.length() - 4);
            String id = normalizeId(cfg.getString("id"), base, file);
            if (id == null) continue;
            if (armorConfigs.containsKey(id)) {
                plugin.getLogger().warning("重复的盔甲套装 id: " + id + "（文件 " + fileName + " 覆盖了 "
                        + armorFiles.get(id).getName() + "）");
            }
            armorConfigs.put(id, cfg);
            armorFiles.put(id, file);
            stamp = Math.max(stamp, file.lastModified());
        }
        this.armorStamp = stamp;
        this.armorFileCount = count;
    }

    /** 当前 skills 目录的指纹（用于轮询检测变化）。 */
    private long currentSkillsStamp() {
        File[] files = skillsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        long stamp = 0;
        int count = 0;
        if (files != null) {
            for (File file : files) {
                stamp = Math.max(stamp, file.lastModified());
                count++;
            }
        }
        // 文件数量变化也计入，避免删除文件后 max(mtime) 不变
        return stamp ^ (count * 31L);
    }

    /** 当前 armor 目录的指纹（用于轮询检测变化）。 */
    private long currentArmorStamp() {
        File[] files = armorDir == null ? new File[0]
                : armorDir.listFiles((dir, name) -> name.endsWith(".yml"));
        long stamp = 0;
        int count = 0;
        if (files != null) {
            for (File file : files) {
                stamp = Math.max(stamp, file.lastModified());
                count++;
            }
        }
        return stamp ^ (count * 31L);
    }

    public void reload() {
        load();
    }

    /**
     * 启动共享配置轮询（{@code config_poll_interval} 秒；<= 0 关闭）。
     * 仅在本服启用了 {@code shared_config_dir} 时生效。
     */
    public void startPolling() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        if (sharedDir.isEmpty()) {
            return;
        }
        int interval = config.getInt("config_poll_interval", 10);
        if (interval <= 0) {
            return;
        }
        long period = interval * 20L;
        pollTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long cfg = configFile.lastModified();
            long sk = currentSkillsStamp();
            long ar = currentArmorStamp();
            if (cfg == configStamp && sk == skillsStamp && ar == armorStamp
                    && skillsFileCount == countSkillFiles()
                    && armorFileCount == countArmorFiles()) {
                return;
            }
            plugin.getLogger().info("Detected shared config change, reloading...");
            plugin.reloadAll();
            configStamp = configFile.lastModified();
            skillsStamp = currentSkillsStamp();
            skillsFileCount = countSkillFiles();
            armorStamp = currentArmorStamp();
            armorFileCount = countArmorFiles();
        }, period, period);
    }

    private int countSkillFiles() {
        File[] files = skillsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        return files == null ? 0 : files.length;
    }

    private int countArmorFiles() {
        File[] files = armorDir == null ? new File[0]
                : armorDir.listFiles((dir, name) -> name.endsWith(".yml"));
        return files.length;
    }

    public void stopPolling() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    /** 定位插件自身 jar 文件（用于复制内置 skills/*.yml）。 */
    private File pluginJarFile() {
        try {
            var url = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                return new File(url.toURI());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void copyIfMissing(File from, File to) {
        if (to.exists() || !from.exists()) {
            return;
        }
        try {
            Files.copy(from.toPath(), to.toPath());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "复制共享配置失败: " + to, ex);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public File getConfigFile() {
        return configFile;
    }

    public File getSkillsDir() {
        return skillsDir;
    }

    public File getArmorDir() {
        return armorDir;
    }

    /** 某个技能对应的 yml 文件（不存在返回 null）。 */
    public File getSkillFile(String id) {
        return id == null ? null : skillFiles.get(id.toLowerCase(Locale.ROOT));
    }

    /** 某个盔甲套装对应的 yml 文件（不存在返回 null）。 */
    public File getArmorFile(String id) {
        return id == null ? null : armorFiles.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * 回写某个技能的数值到它的 yml（有 stats: 段则写在 stats 下，否则写顶层），
     * 供 {@code /sc cooldown} 等命令使用。
     *
     * @return 是否成功
     */
    public boolean setSkillStat(String id, String key, Object value) {
        File file = getSkillFile(id);
        if (file == null || !file.isFile() || key == null || value == null) {
            return false;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = cfg.getConfigurationSection("stats") != null ? "stats." + key : key;
        cfg.set(path, value);
        try {
            cfg.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "保存技能配置失败: " + file, ex);
            return false;
        }
    }

    /**
     * 从 skills/ 目录加载全部技能武器。
     */
    public int loadWeapons(com.skillcore.weapon.WeaponFactory factory,
                           com.skillcore.weapon.WeaponRegistry registry) {
        registry.clear();
        int count = 0;
        for (Map.Entry<String, YamlConfiguration> entry : skillConfigs.entrySet()) {
            String id = entry.getKey();
            ConfigurationSection section = entry.getValue();
            try {
                var weapon = factory.parse(id, section);
                var rightSkill = factory.createSkill(weapon.rightSkillType(), weapon);
                var leftSkill = weapon.leftSkillType() != null
                        ? factory.createSkill(weapon.leftSkillType(), weapon)
                        : null;
                registry.register(weapon, rightSkill, leftSkill);
                count++;
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load skill: " + id, ex);
            }
        }
        plugin.getLogger().info("Loaded " + count + " skill weapon(s) from skills/");
        return count;
    }

    /**
     * 只重新读取指定的技能文件（{@code /sc cooldown} 回写后使用），
     * 避免一次命令触发全量重载（会打断玩家正在进行的技能状态）。
     *
     * @return 是否读取成功
     */
    public boolean reloadSkillFile(String id) {
        File file = getSkillFile(id);
        if (file == null || !file.isFile()) {
            return false;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String key = id.toLowerCase(Locale.ROOT);
        skillConfigs.put(key, cfg);
        return true;
    }

    public YamlConfiguration getSkillConfig(String id) {
        return id == null ? null : skillConfigs.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * 从 armor/ 目录加载全部盔甲套装。
     */
    public int loadArmors(com.skillcore.armor.ArmorFactory factory,
                          com.skillcore.armor.ArmorRegistry registry) {
        registry.clear();
        int count = 0;
        for (Map.Entry<String, YamlConfiguration> entry : armorConfigs.entrySet()) {
            String id = entry.getKey();
            ConfigurationSection section = entry.getValue();
            try {
                var set = factory.parse(id, section);
                registry.register(set);
                count++;
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load armor set: " + id, ex);
            }
        }
        plugin.getLogger().info("Loaded " + count + " armor set(s) from armor/");
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
