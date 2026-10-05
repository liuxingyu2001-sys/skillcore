package com.skillcore.hook;

import com.skillcore.SkillCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * CraftEngine 模型支持（软依赖，反射调用，未安装 CraftEngine 时全部返回 null/false）。
 * <p>
 * 对应配置键 {@code craftengine_model}：填 CraftEngine 物品/模型 ID，
 * 武器物品与特效显示物都会优先使用该模型，失败则降级原版材质 + CustomModelData。
 */
public final class CraftEngineHook {

    private static volatile boolean initialized;
    private static Class<?> itemsClass;
    private static Method byIdMethod;
    private static Method buildWithPlayerMethod;
    private static Method buildMethod;
    private static Method isCustomItemMethod;
    private static Method getCustomItemIdMethod;

    private CraftEngineHook() {
    }

    /** CraftEngine 插件是否已安装并可用。 */
    public static boolean isAvailable() {
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null) return false;
        if (plugin.getServer().getPluginManager().getPlugin("CraftEngine") == null) {
            return false;
        }
        init();
        return itemsClass != null && byIdMethod != null;
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            itemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            byIdMethod = itemsClass.getMethod("byId", String.class);
            isCustomItemMethod = itemsClass.getMethod("isCustomItem", ItemStack.class);
            getCustomItemIdMethod = itemsClass.getMethod("getCustomItemId", ItemStack.class);
            Class<?> definition = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemDefinition");
            buildWithPlayerMethod = definition.getMethod("buildBukkitItem", Player.class);
            buildMethod = definition.getMethod("buildBukkitItem");
        } catch (Throwable ignored) {
            itemsClass = null;
        }
    }

    /**
     * 按模型 ID 构建物品（无条件构建版本）。
     *
     * @return 构建出的 ItemStack；不可用/失败返回 null
     */
    public static ItemStack buildItem(String modelId) {
        if (modelId == null || modelId.isEmpty() || !isAvailable()) return null;
        try {
            Object definition = byIdMethod.invoke(null, modelId);
            if (definition == null || buildMethod == null) return null;
            Object item = buildMethod.invoke(definition);
            return item instanceof ItemStack stack ? stack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 按模型 ID 构建物品（带玩家上下文，可用于占位符解析）。
     */
    public static ItemStack buildItem(String modelId, Player player) {
        if (modelId == null || modelId.isEmpty() || !isAvailable()) return null;
        if (player == null) return buildItem(modelId);
        try {
            Object definition = byIdMethod.invoke(null, modelId);
            if (definition == null || buildWithPlayerMethod == null) return buildItem(modelId);
            Object item = buildWithPlayerMethod.invoke(definition, player);
            return item instanceof ItemStack stack ? stack : null;
        } catch (Throwable ignored) {
            return buildItem(modelId);
        }
    }

    /** 物品是否为 CraftEngine 自定义物品。 */
    public static boolean isCustomItem(ItemStack item) {
        if (item == null || !isAvailable() || isCustomItemMethod == null) return false;
        try {
            return Boolean.TRUE.equals(isCustomItemMethod.invoke(null, item));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 读取物品的 CraftEngine 模型 ID，非自定义物品返回 null。 */
    public static String modelIdOf(ItemStack item) {
        if (item == null || !isAvailable() || getCustomItemIdMethod == null) return null;
        try {
            Object key = getCustomItemIdMethod.invoke(null, item);
            return key == null ? null : key.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
