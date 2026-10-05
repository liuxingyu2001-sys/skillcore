package com.skillcore.hook;

import com.skillcore.SkillCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * PacketEvents 软依赖钩子 — 通过反射发送「方块破坏动画」数据包，
 * 给玩家一种地面被砸碎/开裂的视觉效果（不真实破坏方块，仅客户端表现）。
 * <p>
 * 依赖插件名：{@code packetevents}（插件 jar：packetevents-spigot-*.jar）。
 * 未安装 / 未启用时所有方法安全降级为 no-op，不影响其他特效。
 */
public final class PacketEventsHook {

    private static final int MAX_BLOCKS = 24;

    private static boolean initialized;
    private static boolean available;

    private static Object api;
    private static Object protocolManager;
    private static Object playerManager;

    private static Method getChannelByUuid;
    private static Method sendPacket;
    private static Method getUser;
    private static Method userGetChannel;

    private static Constructor<?> vector3iCtor;
    private static Constructor<?> blockBreakCtor;

    private PacketEventsHook() {
    }

    /** 是否可用（PacketEvents 插件已安装且启用，反射解析成功）。 */
    public static boolean isAvailable() {
        ensureInit();
        return available;
    }

    private static void ensureInit() {
        if (initialized) return;
        initialized = true;
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("packetevents");
            if (plugin == null || !plugin.isEnabled()) {
                available = false;
                return;
            }

            Class<?> pe = Class.forName("com.github.retrooper.packetevents.PacketEvents");
            api = pe.getMethod("getAPI").invoke(null);
            if (api == null) {
                available = false;
                return;
            }

            Class<?> apiClass = Class.forName("com.github.retrooper.packetevents.PacketEventsAPI");
            protocolManager = apiClass.getMethod("getProtocolManager").invoke(api);
            playerManager = apiClass.getMethod("getPlayerManager").invoke(api);

            Class<?> pm = Class.forName("com.github.retrooper.packetevents.manager.protocol.ProtocolManager");
            getChannelByUuid = pm.getMethod("getChannel", UUID.class);
            Class<?> wrapper = Class.forName("com.github.retrooper.packetevents.wrapper.PacketWrapper");
            sendPacket = pm.getMethod("sendPacket", Object.class, wrapper);

            Class<?> playerMgr = Class.forName("com.github.retrooper.packetevents.manager.player.PlayerManager");
            getUser = playerMgr.getMethod("getUser", Object.class);

            Class<?> userClass = Class.forName("com.github.retrooper.packetevents.protocol.player.User");
            userGetChannel = userClass.getMethod("getChannel");

            Class<?> v3i = Class.forName("com.github.retrooper.packetevents.util.Vector3i");
            vector3iCtor = v3i.getConstructor(int.class, int.class, int.class);

            Class<?> bb = Class.forName("com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation");
            blockBreakCtor = bb.getConstructor(int.class, v3i, byte.class);

            available = true;
        } catch (Throwable ex) {
            available = false;
        }
    }

    /**
     * 取玩家的网络通道（发送数据包用）。
     */
    private static Object channel(Player player) {
        try {
            Object ch = getChannelByUuid.invoke(protocolManager, player.getUniqueId());
            if (ch != null) return ch;
        } catch (Throwable ignored) {
        }
        try {
            Object user = getUser.invoke(playerManager, player);
            if (user != null) return userGetChannel.invoke(user);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 向单个玩家发送指定方块的破坏动画（destroyStage 0-9，9 = 几乎碎裂）。
     */
    public static boolean sendBlockBreak(Player viewer, int entityId, int x, int y, int z, int stage) {
        if (!isAvailable() || viewer == null || !viewer.isOnline()) {
            return false;
        }
        try {
            Object channel = channel(viewer);
            if (channel == null) return false;
            Object pos = vector3iCtor.newInstance(x, y, z);
            Object wrapper = blockBreakCtor.newInstance(entityId, pos, (byte) stage);
            sendPacket.invoke(protocolManager, channel, wrapper);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    /**
     * 对一组方块逐帧推进破坏动画，随后恢复原样（客户端表现，服务器方块不变）。
     *
     * @param viewers            能看到效果的玩家（通常是技能落点附近玩家）
     * @param blocks             要「碎裂」的地面方块（自动截断到 {@value MAX_BLOCKS} 个）
     * @param restoreAfterTicks  裂纹完成后多少 tick 恢复原样
     */
    public static void crackAndRestore(Collection<Player> viewers, List<Block> blocks, long restoreAfterTicks) {
        if (!isAvailable() || viewers == null || viewers.isEmpty() || blocks == null || blocks.isEmpty()) {
            return;
        }
        List<Player> online = viewers.stream().filter(Player::isOnline).toList();
        if (online.isEmpty()) return;
        List<Block> list = blocks.stream().limit(MAX_BLOCKS).toList();

        // 渐进裂纹：几帧推进到完全碎裂
        int[] stages = {0, 3, 6, 8, 9};
        long tick = 0;
        for (int stage : stages) {
            final long delay = tick++;
            final int s = stage;
            schedule(delay, () -> sendStage(online, list, s));
        }
        schedule(tick + Math.max(0, restoreAfterTicks), () -> restore(online, list));
    }

    private static void sendStage(List<Player> viewers, List<Block> blocks, int stage) {
        for (Block block : blocks) {
            int entityId = entityId(block);
            for (Player viewer : viewers) {
                sendBlockBreak(viewer, entityId,
                        block.getX(), block.getY(), block.getZ(), stage);
            }
        }
    }

    private static void restore(List<Player> viewers, List<Block> blocks) {
        for (Player viewer : viewers) {
            if (!viewer.isOnline()) continue;
            for (Block block : blocks) {
                // Bukkit 自带 sendBlockChange 会按玩家版本正确编码，清除裂纹覆盖层
                viewer.sendBlockChange(block.getLocation(), block.getBlockData());
            }
        }
    }

    /** 稳定且可复现的方块实体 id（用于裂纹动画标识）。 */
    private static int entityId(Block block) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        return (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
    }

    private static void schedule(long ticks, Runnable task) {
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(0, ticks));
    }
}
