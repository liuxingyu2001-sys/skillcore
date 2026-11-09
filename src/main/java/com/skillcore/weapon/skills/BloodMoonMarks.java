package com.skillcore.weapon.skills;

import com.skillcore.utils.PotionUtils;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 血月印记 — 血月之镰左右技能共享的目标标记状态。
 * <p>
 * 语义：被回旋镰命中 / 普攻概率命中的目标会挂上印记（发光 + 粒子），
 * 左键「死神收割」命中带印记目标时额外造成伤害并引爆印记。
 * <p>
 * 状态只按目标 UUID + 到期时间存，读取时顺手清理过期项；
 * 同时有数量上限，避免大量目标把 map 撑爆。插件禁用 / 重载时 {@link #clear()}。
 */
final class BloodMoonMarks {

    /** 目标 UUID -> 到期时间戳（毫秒）。 */
    private static final Map<UUID, Long> MARKS = new ConcurrentHashMap<>();
    /** 上限：超过直接整体清空，避免无界增长。 */
    private static final int MAX_ENTRIES = 2048;

    private BloodMoonMarks() {
    }

    /** 给目标挂印记并加发光提示；{@code durationTicks <= 0} 时忽略。 */
    static void apply(LivingEntity target, int durationTicks) {
        if (target == null || durationTicks <= 0 || !target.isValid() || target.isDead()) return;
        long now = System.currentTimeMillis();
        purgeExpired(now);
        if (MARKS.size() >= MAX_ENTRIES) {
            MARKS.clear();
        }
        MARKS.put(target.getUniqueId(), now + durationTicks * 50L);
        // 发光让目标在远处也看得见 —— 交互反馈的一部分
        PotionUtils.glowing(target, durationTicks);
    }

    /** 是否带有效印记（顺手清理过期条目）。 */
    static boolean isMarked(LivingEntity target) {
        if (target == null) return false;
        Long expireAt = MARKS.get(target.getUniqueId());
        if (expireAt == null) return false;
        if (expireAt <= System.currentTimeMillis() || !target.isValid() || target.isDead()) {
            MARKS.remove(target.getUniqueId());
            return false;
        }
        return true;
    }

    /** 消耗印记（引爆）；返回是否真的消耗了一个有效印记。 */
    static boolean consume(LivingEntity target) {
        if (!isMarked(target)) return false;
        MARKS.remove(target.getUniqueId());
        return true;
    }

    /** 清理过期条目。 */
    static void purgeExpired() {
        purgeExpired(System.currentTimeMillis());
    }

    private static void purgeExpired(long now) {
        if (MARKS.isEmpty()) return;
        MARKS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    /** 清空全部印记（插件禁用 / 重载）。 */
    static void clear() {
        MARKS.clear();
    }
}
