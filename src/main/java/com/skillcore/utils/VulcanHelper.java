package com.skillcore.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Vulcan 反作弊兼容（软依赖，反射调用）。
 * <p>
 * 技能位移/突刺期间可每 tick 调用 {@link #clearMovementViolations(Player)} 清零
 * 移动类检测的 VL，避免纯速度位移被误判惩罚。未安装 Vulcan 时为无操作。
 */
public final class VulcanHelper {

    private static Object vulcanApi;
    private static boolean tried;

    private static Method getChecksMethod;
    private static Method setVlMethod;
    private static Method getCategoryMethod;
    private static Method getNameMethod;

    private static final String[] MOVEMENT_CHECKS = {
            "speed", "fly", "velocity", "entityspeed", "boatfly", "scaffold", "groundspoof", "noslow"
    };

    private VulcanHelper() {
    }

    /** 插件禁用时重置缓存，避免重载后引用旧对象。 */
    public static void reset() {
        vulcanApi = null;
        tried = false;
        getChecksMethod = null;
        setVlMethod = null;
        getCategoryMethod = null;
        getNameMethod = null;
    }

    /** 位移期间每 tick 调用：清零该玩家所有移动类检测的 VL。 */
    public static void clearMovementViolations(Player player) {
        if (player == null) return;
        if (!tried) {
            tryConnect();
            tried = true;
        }
        if (vulcanApi == null) return;
        try {
            Object checksObj = getChecksMethod.invoke(vulcanApi, player);
            if (!(checksObj instanceof List<?> checks)) return;
            for (Object check : checks) {
                if (isMovementCheck(check)) {
                    setVlMethod.invoke(check, 0);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isMovementCheck(Object check) {
        try {
            String cat = (String) getCategoryMethod.invoke(check);
            if (cat != null && cat.equalsIgnoreCase("MOVEMENT")) return true;
            String name = (String) getNameMethod.invoke(check);
            if (name != null) {
                for (String mc : MOVEMENT_CHECKS) {
                    if (name.equalsIgnoreCase(mc)) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void tryConnect() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Vulcan");
        if (p == null) return;
        Class<?> apiClass;
        try {
            apiClass = Class.forName("me.frep.vulcan.api.VulcanAPI");
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            vulcanApi = Class.forName("me.frep.vulcan.api.VulcanAPI$Factory").getMethod("getApi").invoke(null);
            getChecksMethod = apiClass.getMethod("getChecks", Player.class);
        } catch (Exception e1) {
            try {
                vulcanApi = apiClass.getMethod("getApi").invoke(null);
                getChecksMethod = apiClass.getMethod("getChecks", Player.class);
            } catch (Exception e2) {
                vulcanApi = null;
                return;
            }
        }
        if (vulcanApi == null) return;
        try {
            Class<?> checkClass = Class.forName("me.frep.vulcan.api.check.Check");
            setVlMethod = checkClass.getMethod("setVl", int.class);
            getCategoryMethod = checkClass.getMethod("getCategory");
            getNameMethod = checkClass.getMethod("getName");
        } catch (Exception ignored) {
            vulcanApi = null;
        }
    }
}
