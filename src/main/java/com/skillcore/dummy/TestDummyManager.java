package com.skillcore.dummy;

import com.skillcore.SkillCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 测试假人模块 — 生成不会移动/还击的僵尸，用于测试武器伤害数值。
 * <p>
 * 数据持久化到 {@code testdummy.yml}，重启后自动恢复。
 */
public final class TestDummyManager implements Listener {

    private final SkillCorePlugin plugin;
    private final NamespacedKey dummyKey;
    private final Map<UUID, Double> tracked = new ConcurrentHashMap<>();
    private File file;
    private FileConfiguration config;
    /** 恢复过程中不写盘，避免每个假人都重写一次 testdummy.yml。 */
    private boolean restoring;
    /** 主动 clear 时不要保留旧文件条目。 */
    private boolean suppressPreserve;

    public TestDummyManager(SkillCorePlugin plugin) {
        this.plugin = plugin;
        this.dummyKey = new NamespacedKey(plugin, "skillcore_dummy");
        loadConfig();
        restore();
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    private void loadConfig() {
        this.file = new File(plugin.getDataFolder(), "testdummy.yml");
        if (!file.exists()) {
            plugin.saveResource("testdummy.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        loadConfig();
    }

    private String dummyName() {
        return config.getString("dummy.name", "&c测试假人");
    }

    private double defaultHealth() {
        double hp = config.getDouble("dummy.health", 100.0);
        if (!Double.isFinite(hp) || hp <= 0) {
            return 100.0;
        }
        return Math.min(hp, 2048.0);
    }

    // ------------------------------------------------------------------
    // 生成 / 清除
    // ------------------------------------------------------------------

    public LivingEntity spawn(Location location) {
        return spawn(location, defaultHealth());
    }

    public LivingEntity spawn(Location location, double health) {
        if (location == null || location.getWorld() == null) return null;
        double requested = Double.isFinite(health) && health > 0 ? health : defaultHealth();
        final double hp = Math.max(1.0, Math.min(requested, 2048.0));
        World world = location.getWorld();
        String name = dummyName();
        if (name == null || name.isBlank()) {
            name = "&c测试假人";
        }
        final String displayName = name;
        Zombie zombie;
        try {
            zombie = world.spawn(location, Zombie.class, z -> {
                z.setAI(!config.getBoolean("dummy.no-ai", true));
                z.setSilent(config.getBoolean("dummy.silent", true));
                z.setRemoveWhenFarAway(false);
                z.setPersistent(true);
                z.setCanPickupItems(false);
                z.setCollidable(false);
                z.setCustomNameVisible(true);
                z.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(displayName));
                z.getEquipment().clear();
                AttributeInstance attr = z.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(hp);
                    // 服务端会按属性上限收敛，这里按实际上限设置当前血量，避免 setHealth 越界
                    z.setHealth(Math.min(hp, attr.getValue()));
                }
                z.getPersistentDataContainer().set(dummyKey, PersistentDataType.STRING, "true");
            });
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "生成测试假人失败: " + ex.getMessage(), ex);
            return null;
        }
        if (zombie == null) return null;
        tracked.put(zombie.getUniqueId(), hp);
        save();
        return zombie;
    }

    public boolean isDummy(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(dummyKey, PersistentDataType.STRING);
    }

    /**
     * 清除本插件记录 / 世界内所有假人。
     *
     * @return 清除数量
     */
    public int clear() {
        int removed = 0;
        for (UUID id : new ArrayList<>(tracked.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
                removed++;
            }
        }
        tracked.clear();
        suppressPreserve = true;
        try {
            save();
        } finally {
            suppressPreserve = false;
        }
        return removed;
    }

    /**
     * 清除所有世界中带假人标记的实体（用于清理崩溃残留）。
     */
    public int clearAllWorlds() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isDummy(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        tracked.clear();
        suppressPreserve = true;
        try {
            save();
        } finally {
            suppressPreserve = false;
        }
        return removed;
    }

    /** 插件禁用时清理（假人会持久化，这里不删除实体，只保存状态）。 */
    public void shutdown() {
        save();
    }

    public List<LivingEntity> list() {
        List<LivingEntity> result = new ArrayList<>();
        for (UUID id : tracked.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living && living.isValid()) {
                result.add(living);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    private void save() {
        if (config == null || restoring) return;
        // 旧文件条目：用于保留“实体所在区块/世界当前未加载”的假人，避免一次保存就永久丢失
        Map<UUID, String> previous = new java.util.HashMap<>();
        for (String raw : config.getStringList("dummies")) {
            String[] parts = raw.split(";");
            if (parts.length < 6) continue;
            try {
                previous.put(UUID.fromString(parts[0]), raw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : tracked.entrySet()) {
            Entity entity = Bukkit.getEntity(e.getKey());
            if (entity == null) {
                String old = previous.get(e.getKey());
                if (old != null) entries.add(old);
                continue;
            }
            Location loc = entity.getLocation();
            if (loc.getWorld() == null) continue;
            entries.add(String.join(";",
                    e.getKey().toString(),
                    loc.getWorld().getName(),
                    String.valueOf(loc.getX()),
                    String.valueOf(loc.getY()),
                    String.valueOf(loc.getZ()),
                    String.valueOf(e.getValue())));
        }
        // 主动 clear 时不要保留旧条目
        if (!suppressPreserve) {
            for (Map.Entry<UUID, String> e : previous.entrySet()) {
                if (tracked.containsKey(e.getKey())) continue;
                Entity entity = Bukkit.getEntity(e.getKey());
                if (entity == null || !entity.isValid()) {
                    entries.add(e.getValue());
                }
            }
        }
        config.set("dummies", entries);
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save testdummy.yml", ex);
        }
    }

    private void restore() {
        if (config == null) return;
        List<String> entries = config.getStringList("dummies");
        if (entries.isEmpty()) return;
        // 延迟 1 tick，确保世界/区块已就绪；整批恢复期间不写盘，最后统一保存一次
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            restoring = true;
            try {
                for (String raw : entries) {
                    restoreEntry(raw);
                }
            } finally {
                restoring = false;
            }
            save();
        }, 1L);
    }

    private void restoreEntry(String raw) {
        String[] parts = raw.split(";");
        if (parts.length < 6) return;
        try {
            UUID id = UUID.fromString(parts[0]);
            World world = Bukkit.getWorld(parts[1]);
            if (world == null) return;
            double x = Double.parseDouble(parts[2]);
            double y = Double.parseDouble(parts[3]);
            double z = Double.parseDouble(parts[4]);
            double hp = Double.parseDouble(parts[5]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Double.isFinite(hp) || hp <= 0) {
                return;
            }
            Location loc = new Location(world, x, y, z);
            // 僵尸是持久化实体：先加载区块再按 UUID 查找，避免重启后重复生成
            Entity existing = Bukkit.getEntity(id);
            if (existing == null) {
                world.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                existing = Bukkit.getEntity(id);
            }
            if (existing instanceof LivingEntity living && living.isValid() && isDummy(living)) {
                tracked.put(id, hp);
                return;
            }
            spawn(loc, hp);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid testdummy entry: " + raw);
        }
    }

    // ------------------------------------------------------------------
    // 保护假人
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (isDummy(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** 免疫摔落/火焰/中毒/虚空等环境伤害，只接受战斗伤害（方便测数值）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (isDummy(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isDummy(event.getEntity()) || isDummy(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (isDummy(event.getEntity())) {
            tracked.remove(event.getEntity().getUniqueId());
            event.getDrops().clear();
            save();
        }
    }
}
