package com.skillcore.hook;

import com.skillcore.SkillCorePlugin;
import com.skillcore.weapon.WeaponItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * mcMMO 软依赖：手持技能武器时屏蔽 mcMMO 发往动作栏的提示。
 * <p>
 * 背景：mcMMO 在右键拿起工具 / 超技能冷却 / 战斗涨经验时会不停往动作栏写文本
 * （{@code Feedback.ActionBarNotifications.*} 控制），和武器技能自己的动作栏反馈抢屏，
 * 生效顺序取决于发包时机，玩家看到的就是「右键一直有 mcMMO 技能提示」。
 * <p>
 * 实现方式：监听 mcMMO 的 {@code McMMOPlayerNotificationEvent}（可取消事件），
 * 只在<b>目标消息发往动作栏</b>且<b>接收玩家手持技能武器</b>时取消——
 * 聊天栏消息（含 SendCopyOfMessageToChat 的副本）、其他玩家的通知、没持技能武器的玩家全部不受影响。
 * <p>
 * 全部走反射 + 动态注册事件：不开编译期依赖（mcMMO 未安装时本类不会被调用到反射部分），
 * 也不会因为 mcMMO 升级改了包名就把整个插件拖崩——任一环节失败只记 debug 日志并跳过。
 */
public final class McMMOHook {

    /** mcMMO 玩家通知事件（2.2 / 2.3 版本包名一致）。 */
    private static final String EVENT_CLASS = "com.gmail.nossr50.events.skills.McMMOPlayerNotificationEvent";
    /** 动作栏目的地枚举常量名（{@code McMMOMessageType.ACTION_BAR}）。 */
    private static final String ACTION_BAR = "ACTION_BAR";

    /** 注册事件只需要一个 Listener 壳，实际派发由下面传入的 EventExecutor 完成。 */
    private static final Listener LISTENER = new Listener() {
    };

    private static SkillCorePlugin plugin;
    private static Method getPlayer;
    private static Method getChatMessageType;
    private static boolean registered;

    private McMMOHook() {
    }

    /**
     * 尝试挂接 mcMMO（幂等）。mcMMO 未安装 / 卸载中 / 事件类结构不兼容时静默跳过。
     */
    public static void init(SkillCorePlugin plugin) {
        if (registered || plugin == null) return;
        McMMOHook.plugin = plugin;
        try {
            Plugin mcmmo = Bukkit.getPluginManager().getPlugin("mcMMO");
            if (mcmmo == null || !mcmmo.isEnabled()) {
                return;
            }
            // 用 mcMMO 自己的类加载器加载它的事件类：插件隔离下本插件的加载器看不到对方类
            ClassLoader loader = mcmmo.getClass().getClassLoader();
            Class<?> raw = Class.forName(EVENT_CLASS, false, loader);
            if (!Event.class.isAssignableFrom(raw)) {
                return;
            }
            getPlayer = raw.getMethod("getPlayer");
            getChatMessageType = raw.getMethod("getChatMessageType");

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            Bukkit.getPluginManager().registerEvent(eventClass, LISTENER, EventPriority.HIGHEST,
                    (listener, event) -> handle(event), plugin, false);
            registered = true;
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("mcMMO 动作栏提示屏蔽已启用。");
            }
        } catch (Throwable ex) {
            // mcMMO 版本差异 / 类加载隔离失败：不影响插件启动，退化为不屏蔽
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("mcMMO 动作栏提示屏蔽挂接失败: " + ex);
            }
            reset();
        }
    }

    /**
     * 取消监听（插件禁用 / 热重载时调用；不注销会在热重载后留着旧实例）。
     */
    public static void reset() {
        if (registered) {
            try {
                HandlerList.unregisterAll(LISTENER);
            } catch (Throwable ignored) {
                // 服务端关闭阶段事件系统可能已不可用
            }
        }
        registered = false;
        getPlayer = null;
        getChatMessageType = null;
        plugin = null;
    }

    /**
     * 事件处理：只在「动作栏 + 手持技能武器 + 开关开启」时取消。
     */
    private static void handle(Event event) {
        try {
            if (!(event instanceof Cancellable cancellable) || cancellable.isCancelled()) {
                return;
            }
            Object type = getChatMessageType.invoke(event);
            if (!(type instanceof Enum<?> messageType) || !ACTION_BAR.equals(messageType.name())) {
                return;
            }
            Object value = getPlayer.invoke(event);
            if (!(value instanceof Player player)) {
                return;
            }
            if (!shouldSuppress(player)) {
                return;
            }
            cancellable.setCancelled(true);
            if (plugin != null && plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("屏蔽 mcMMO 动作栏提示: " + player.getName());
            }
        } catch (Throwable ignored) {
            // 单个通知处理失败不影响其他通知，也不影响技能本身
        }
    }

    /** 开关开启且玩家主手拿着技能武器时才屏蔽（普通武器玩家照常收 mcMMO 提示）。 */
    private static boolean shouldSuppress(Player player) {
        SkillCorePlugin core = SkillCorePlugin.getInstance();
        if (core == null || core.getConfigManager() == null) {
            return false;
        }
        if (!core.getConfigManager().getConfig()
                .getBoolean("integrations.mcmmo.suppress-actionbar", true)) {
            return false;
        }
        return WeaponItems.getHeldWeaponId(core, player) != null;
    }
}
