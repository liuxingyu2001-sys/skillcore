package com.skillcore.armor;

import org.bukkit.entity.Player;

/**
 * 盔甲技能上下文 — 集齐整套后提供给 {@link ArmorSkill} 的读写入口。
 */
public final class ArmorContext {

    private final Player player;
    private final ArmorSet set;

    public ArmorContext(Player player, ArmorSet set) {
        this.player = player;
        this.set = set;
    }

    public Player player() {
        return player;
    }

    public ArmorSet set() {
        return set;
    }

    /** 当前套装的加成数值（套装未知时返回空加成）。 */
    public ArmorStats stats() {
        return set == null ? ArmorStats.EMPTY : set.stats();
    }
}
