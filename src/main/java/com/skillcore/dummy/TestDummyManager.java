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
        return config.getDouble("dummy.health", 100.0);
    }

    // ------------------------------------------------------------------
    // 生成 / 清除
    // ------------------------------------------------------------------

    public LivingEntity spawn(Location location) {
        return spawn(location, defaultHealth());
    }

    public LivingEntity spawn(Location location, double health) {
        if (location == null || location.getWorld() == null) return null;
        double hp = health > 0 ? health : defaultHealth();
        World world = location.getWorld();
        Zombie zombie = world.spawn(location, Zombie.class, z -> {
            z.setAI(!config.getBoolean("dummy.no-ai", true));
            z.setSilent(config.getBoolean("dummy.silent", true));
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.setCanPickupItems(false);
            z.setCollidable(false);
            z.setCustomNameVisible(true);
            z.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(dummyName()));
            z.getEquipment().clear();
            if (z.getAttribute(Attribute.MAX_HEALTH) != null) {
                AttributeInstance attr = z.getAttribute(Attribute.MAX_HEALTH);
                attr.setBaseValue(hp);
                z.setHealth(hp);
            }
            z.getPersistentDataContainer().set(dummyKey, PersistentDataType.STRING, "true");
        });
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
        save();
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
        save();
        return removed;
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
        if (config == null) return;
        config.set("dummies", new ArrayList<String>());
        List<String> entries = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : tracked.entrySet()) {
            Entity entity = Bukkit.getEntity(e.getKey());
            if (entity == null) continue;
            Location loc = entity.getLocation();
            entries.add(String.join(";",
                    e.getKey().toString(),
                    loc.getWorld().getName(),
                    String.valueOf(loc.getX()),
                    String.valueOf(loc.getY()),
                    String.valueOf(loc.getZ()),
                    String.valueOf(e.getValue())));
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
        for (String raw : config.getStringList("dummies")) {
            String[] parts = raw.split(";");
            if (parts.length < 6) continue;
            try {
                World world = Bukkit.getWorld(parts[1]);
                if (world == null) continue;
                Location loc = new Location(world,
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4]));
                double hp = Double.parseDouble(parts[5]);
                // 延迟 1 tick，确保世界已加载
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawn(loc, hp), 1L);
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Invalid testdummy entry: " + raw);
            }
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
