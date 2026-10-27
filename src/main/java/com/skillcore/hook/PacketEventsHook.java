package com.skillcore.hook;

import com.skillcore.SkillCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
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

    /** 重置缓存（插件禁用 / 热重载时调用）。 */
    public static synchronized void reset() {
        initialized = false;
        available = false;
        api = null;
        protocolManager = null;
        playerManager = null;
        getChannelByUuid = null;
        sendPacket = null;
        getUser = null;
        userGetChannel = null;
        vector3iCtor = null;
        blockBreakCtor = null;
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
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        List<Player> online = viewers.stream().filter(Player::isOnline).toList();
        if (online.isEmpty()) return;
        List<Block> list = blocks.stream().limit(MAX_BLOCKS).toList();
        // 主线程取快照：后续定时任务里不再访问 Bukkit Block 对象（区块可能已卸载）
        List<BlockPos> snapshot = new ArrayList<>(list.size());
        for (Block block : list) {
            BlockData data;
            try {
                data = block.getBlockData();
            } catch (Throwable ex) {
                continue;
            }
            snapshot.add(new BlockPos(block.getWorld(), block.getX(), block.getY(), block.getZ(), data));
        }
        if (snapshot.isEmpty()) return;
        final List<BlockPos> positions = List.copyOf(snapshot);

        // 渐进裂纹：几帧推进到完全碎裂
        int[] stages = {0, 3, 6, 8, 9};
        long tick = 0;
        for (int stage : stages) {
            final long delay = tick++;
            final int s = stage;
            schedule(delay, () -> sendStage(online, positions, s));
        }
        schedule(tick + Math.max(0, restoreAfterTicks), () -> restore(online, positions));
    }

    /** 方块位置 + 原方块数据快照（可在定时任务中安全使用，不持有 Block 引用）。 */
    private record BlockPos(org.bukkit.World world, int x, int y, int z, BlockData data) {
        int entityId() {
            return (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        }
    }

    private static void sendStage(List<Player> viewers, List<BlockPos> blocks, int stage) {
        for (BlockPos block : blocks) {
            for (Player viewer : viewers) {
                if (!viewer.isOnline() || !viewer.getWorld().equals(block.world())) continue;
                sendBlockBreak(viewer, block.entityId(), block.x(), block.y(), block.z(), stage);
            }
        }
    }

    private static void restore(List<Player> viewers, List<BlockPos> blocks) {
        for (BlockPos block : blocks) {
            // 每个方块只取一次世界数据（放在观众循环外），区块未加载时直接用快照，
            // 否则 getBlockAt 会同步加载区块，而这是延迟任务里跑在主线程上的
            BlockData data = block.data();
            if (block.world().isChunkLoaded(block.x() >> 4, block.z() >> 4)) {
                try {
                    data = block.world().getBlockAt(block.x(), block.y(), block.z()).getBlockData();
                } catch (Throwable ignored) {
                    // 取不到就用快照
                }
            }
            org.bukkit.Location loc = new org.bukkit.Location(block.world(), block.x(), block.y(), block.z());
            for (Player viewer : viewers) {
                if (!viewer.isOnline() || !viewer.getWorld().equals(block.world())) continue;
                // 用真实方块数据覆盖，清除客户端裂纹渲染（不修改服务器方块）
                viewer.sendBlockChange(loc, data);
            }
        }
    }

    private static void schedule(long ticks, Runnable task) {
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(0, ticks));
    }
}
