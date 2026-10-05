package com.skillcore.armor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 盔甲套装注册表：id -> ArmorSet。
 */
public final class ArmorRegistry {

    private final Map<String, ArmorSet> sets = new ConcurrentHashMap<>();

    public void register(ArmorSet set) {
        if (set != null && set.id() != null) {
            sets.put(set.id().toLowerCase(), set);
        }
    }

    public boolean unregister(String id) {
        return id != null && sets.remove(id.toLowerCase()) != null;
    }

    public Optional<ArmorSet> getOptional(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(sets.get(id.toLowerCase()));
    }

    public ArmorSet get(String id) {
        return getOptional(id).orElse(null);
    }

    public boolean has(String id) {
        return id != null && sets.containsKey(id.toLowerCase());
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
