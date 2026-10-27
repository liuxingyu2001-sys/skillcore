package com.skillcore.armor;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 盔甲套装注册表：id -> ArmorSet。
 */
public final class ArmorRegistry {

    private final Map<String, ArmorSet> sets = new ConcurrentHashMap<>();

    /** 统一 id 归一化（去空格 + 小写，固定 Locale.ROOT，避免土耳其语等 Locale 下 i 变成 ı）。 */
    private static String normalize(String id) {
        return id == null ? null : id.trim().toLowerCase(Locale.ROOT);
    }

    public void register(ArmorSet set) {
        String id = set == null ? null : normalize(set.id());
        if (id != null && !id.isEmpty()) {
            sets.put(id, set);
        }
    }

    public boolean unregister(String id) {
        String key = normalize(id);
        return key != null && sets.remove(key) != null;
    }

    public Optional<ArmorSet> getOptional(String id) {
        String key = normalize(id);
        return key == null ? Optional.empty() : Optional.ofNullable(sets.get(key));
    }

    public ArmorSet get(String id) {
        return getOptional(id).orElse(null);
    }

    public boolean has(String id) {
        String key = normalize(id);
        return key != null && sets.containsKey(key);
    }

    public Collection<ArmorSet> all() {
        return List.copyOf(sets.values());
    }

    public Collection<String> ids() {
        return List.copyOf(sets.keySet());
    }

    public int size() {
        return sets.size();
    }

    public void clear() {
        sets.clear();
    }
}