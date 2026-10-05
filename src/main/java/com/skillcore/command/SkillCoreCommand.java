package com.skillcore.command;

import com.skillcore.SkillCorePlugin;
import com.skillcore.utils.MathUtils;
import com.skillcore.utils.MessageUtils;
import com.skillcore.utils.TextUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /skillcore — 技能只绑定在武器上，手持武器才可用。
 * <pre>
 * /sc give &lt;weaponId&gt; [player]     发放技能武器
 * /sc weapons                      列出武器
 * /sc info &lt;weaponId&gt;              武器详情（含变量数值）
 * /sc updatelore &lt;weaponId&gt; [all]  刷新背包内武器 lore
 * /sc reload                       重载配置
 * </pre>
 */
public final class SkillCoreCommand implements CommandExecutor, TabCompleter {

    private final SkillCorePlugin plugin;

    public SkillCoreCommand(SkillCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = plugin.getConfigManager().getPrefix();

        if (args.length == 0) {
            sendHelp(sender, prefix);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> handleGive(sender, args, prefix);
            case "weapons", "list" -> handleList(sender, prefix);
            case "info" -> {
                if (args.length < 2) {
                    MessageUtils.sendPrefixed(sender, prefix, "&e/sc info <weaponId>");
                    return true;
                }
                handleInfo(sender, args[1], prefix);
            }
            case "updatelore", "refreshlore", "lore" -> handleUpdateLore(sender, args, prefix);
            case "cooldown", "cd", "setcooldown" -> handleCooldown(sender, args, prefix);
            case "testdummy", "dummy" -> handleTestDummy(sender, args, prefix);
            case "reload" -> {
                if (!sender.hasPermission("skillcore.admin")) {
                    MessageUtils.sendPrefixed(sender, prefix, "&c无权限.");
                    return true;
                }
                plugin.reloadAll();
                MessageUtils.sendPrefixed(sender, prefix,
                        plugin.getConfigManager().getMessage("reload-done", "&a配置已重载."));
            }
            case "help" -> sendHelp(sender, prefix);
            default -> sendHelp(sender, prefix);
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args, String prefix) {
        if (args.length < 2) {
            MessageUtils.sendPrefixed(sender, prefix, "&e/sc give <weaponId> [player]");
            return;
        }
        String weaponId = args[1];
        Player target = args.length >= 3
                ? plugin.getServer().getPlayer(args[2])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            MessageUtils.sendPrefixed(sender, prefix, "&c目标玩家不在线.");
            return;
        }
        if (plugin.getWeaponManager().giveWeapon(target, weaponId) == null) {
            MessageUtils.sendPrefixed(sender, prefix, "&c未找到武器: &e" + weaponId);
            return;
        }
        MessageUtils.sendPrefixed(sender, prefix,
                "&a已发放 &e" + weaponId + " &a给 &f" + target.getName()
                        + " &7(手持后左右键释放技能)");
    }

    private void handleList(CommandSender sender, String prefix) {
        MessageUtils.sendPrefixed(sender, prefix, "&6===== 技能武器 =====");
        MessageUtils.send(sender, "&7技能绑定在武器上，手持武器才可使用.");
        var all = plugin.getWeaponRegistry().all();
        if (all.isEmpty()) {
            MessageUtils.send(sender, "&7(空) 请在 skills/ 目录配置武器");
            return;
        }
        for (var entry : all) {
            var w = entry.weapon();
            MessageUtils.send(sender, "&e" + w.id() + " &7- " + TextUtils.toLegacy(w.displayName())
                    + " &8| 伤害 " + MathUtils.format1(w.stats().damage())
                    + " CD " + MathUtils.format1(w.stats().cooldown())
                    + "s | R:" + w.rightSkillType()
                    + (w.leftSkillType() != null ? " L:" + w.leftSkillType() : ""));
        }
    }

    private void handleInfo(CommandSender sender, String weaponId, String prefix) {
        var entry = plugin.getWeaponRegistry().getEntry(weaponId).orElse(null);
        if (entry == null) {
            MessageUtils.sendPrefixed(sender, prefix, "&c未找到武器: &e" + weaponId);
            return;
        }
        var w = entry.weapon();
        var s = w.stats();
        MessageUtils.send(sender, "&6===== " + TextUtils.toLegacy(w.displayName()) + " &6=====");
        MessageUtils.send(sender, "&7ID: &f" + w.id());
        if (w.description() != null && !w.description().isEmpty()) {
            MessageUtils.send(sender, TextUtils.toLegacy(w.description()));
        }
        MessageUtils.send(sender, "&7右键技能: &f" + w.rightSkillType()
                + (w.leftSkillType() != null ? "  &7左键: &f" + w.leftSkillType() : ""));
        MessageUtils.send(sender, "&7伤害: &f" + MathUtils.format1(s.damage())
                + "  &7暴击: &f" + MathUtils.format1(s.criticalChance() * 100) + "%"
                + " x" + MathUtils.format1(s.criticalMultiplier()));
        MessageUtils.send(sender, "&7穿透: &f" + MathUtils.format1(s.armorPenetration() * 100) + "%"
                + "  &7吸血: &f" + MathUtils.format1(s.lifesteal() * 100) + "%");
        MessageUtils.send(sender, "&7冷却: &f" + MathUtils.format1(s.cooldown()) + "s"
                + "  &7范围: &f" + MathUtils.format1(s.range()));
        MessageUtils.send(sender, "&7AOE: &f" + MathUtils.format1(s.aoeRadius())
                + "  &7位移: &f" + MathUtils.format1(s.dashDistance()) + "格");
    }

    /**
     * 管理员手动更新 lore — 按 skills/ 目录当前数值刷新占位符。
     */
    private void handleUpdateLore(CommandSender sender, String[] args, String prefix) {
        if (!sender.hasPermission("skillcore.admin")) {
            MessageUtils.sendPrefixed(sender, prefix, "&c无权限.");
            return;
        }
        if (args.length < 2) {
            MessageUtils.sendPrefixed(sender, prefix, "&e/sc updatelore <weaponId> [player|all]");
            MessageUtils.sendPrefixed(sender, prefix, "&7刷新背包内该武器的 lore（变量换成当前数值）");
            return;
        }
        String weaponId = args[1];

        if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
            int n = plugin.getWeaponManager().refreshLoreAllPlayers(weaponId);
            MessageUtils.sendPrefixed(sender, prefix, "&a已刷新全服 &f" + n + " &a件 &e" + weaponId + " &a的 lore.");
            return;
        }

        Player target = args.length >= 3
                ? plugin.getServer().getPlayer(args[2])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            MessageUtils.sendPrefixed(sender, prefix, "&c目标玩家不在线.");
            return;
        }
        int n = plugin.getWeaponManager().refreshLore(target, weaponId);
        MessageUtils.sendPrefixed(sender, prefix,
                "&a已刷新 &f" + target.getName() + " &a背包内 &f" + n + " &a件 &e" + weaponId + " &a的 lore.");
    }

    /**
     * /sc cooldown &lt;weaponId&gt; &lt;秒&gt; — 设置某技能的冷却（写回 skills/&lt;id&gt;.yml 并重载）。
     */
    private void handleCooldown(CommandSender sender, String[] args, String prefix) {
        if (!sender.hasPermission("skillcore.admin")) {
            MessageUtils.sendPrefixed(sender, prefix, "&c无权限.");
            return;
        }
        if (args.length < 3) {
            MessageUtils.sendPrefixed(sender, prefix, "&e/sc cooldown <weaponId> <秒> &7(0 = 无冷却)");
            return;
        }
        String weaponId = args[1];
        double seconds;
        try {
            seconds = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            MessageUtils.sendPrefixed(sender, prefix, "&c秒数无效: &e" + args[2]);
            return;
        }
        if (seconds < 0) seconds = 0;
        if (plugin.getWeaponRegistry().get(weaponId) == null
                && plugin.getConfigManager().getSkillFile(weaponId) == null) {
            MessageUtils.sendPrefixed(sender, prefix, "&c未找到技能: &e" + weaponId);
            return;
        }
        if (!plugin.getConfigManager().setSkillStat(weaponId, "cooldown", seconds)) {
            MessageUtils.sendPrefixed(sender, prefix, "&c写入失败，请检查 skills/&e" + weaponId + ".yml");
            return;
        }
        plugin.reloadAll();
        MessageUtils.sendPrefixed(sender, prefix,
                "&a已将 &e" + weaponId + " &a的冷却设为 &f" + MathUtils.format1(seconds) + "s &7(已写回并重载)");
    }

    private void handleTestDummy(CommandSender sender, String[] args, String prefix) {
        if (!sender.hasPermission("skillcore.admin")) {
            MessageUtils.sendPrefixed(sender, prefix, "&c无权限.");
            return;
        }
        var manager = plugin.getTestDummyManager();
        if (manager == null) {
            MessageUtils.sendPrefixed(sender, prefix, "&c测试假人模块未启用.");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "spawn";
        switch (action) {
            case "spawn", "create" -> {
                if (!(sender instanceof Player player)) {
                    MessageUtils.sendPrefixed(sender, prefix, "&c只有玩家可以生成假人.");
                    return;
                }
                double health = args.length >= 3 ? parseDouble(args[2], 100.0) : 0;
                var dummy = manager.spawn(player.getLocation(), health);
                if (dummy == null) {
                    MessageUtils.sendPrefixed(sender, prefix, "&c生成失败.");
                } else {
                    MessageUtils.sendPrefixed(sender, prefix,
                            "&a已生成测试假人 &7(生命 " + MathUtils.format1(dummy.getHealth()) + ")");
                }
            }
            case "list" -> {
                var dummies = manager.list();
                MessageUtils.sendPrefixed(sender, prefix, "&6测试假人: &f" + dummies.size());
                int i = 1;
                for (var d : dummies) {
                    var loc = d.getLocation();
                    MessageUtils.send(sender, "&7#" + i++ + " &f" + loc.getWorld().getName()
                            + " &7(" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")"
                            + " &c" + MathUtils.format1(d.getHealth()) + "&7/&f"
                            + MathUtils.format1(com.skillcore.utils.DamageUtils.getMaxHealth(d)));
                }
            }
            case "clear", "remove" -> {
                boolean all = args.length >= 3 && args[2].equalsIgnoreCase("all");
                int n = all ? manager.clearAllWorlds() : manager.clear();
                MessageUtils.sendPrefixed(sender, prefix, "&a已清除 &f" + n + " &a个测试假人.");
            }
            default -> MessageUtils.sendPrefixed(sender, prefix,
                    "&e/sc testdummy spawn [health] &7| &e list &7| &e clear [all]");
        }
    }

    private double parseDouble(String raw, double def) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void sendHelp(CommandSender sender, String prefix) {
        MessageUtils.send(sender, "&6===== SkillCore =====");
        MessageUtils.send(sender, "&7技能绑定在武器上 — 手持武器才能使用技能");
        MessageUtils.send(sender, "&e/sc give <weaponId> [player] &7- 发放技能武器");
        MessageUtils.send(sender, "&e/sc weapons &7- 武器列表");
        MessageUtils.send(sender, "&e/sc info <weaponId> &7- 武器详情");
        MessageUtils.send(sender, "&e/sc updatelore <weaponId> [player|all] &7- 刷新 lore");
        MessageUtils.send(sender, "&e/sc cooldown <weaponId> <秒> &7- 设置技能冷却");
        MessageUtils.send(sender, "&e/sc testdummy spawn [health] &7- 生成测试假人");
        MessageUtils.send(sender, "&e/sc reload &7- 重载配置");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(Arrays.asList("give", "weapons", "info", "updatelore", "cooldown", "testdummy", "reload", "help"));
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "info", "updatelore", "cooldown", "cd", "setcooldown" -> {
                    String input = args[1].toLowerCase(Locale.ROOT);
                    for (String id : plugin.getWeaponRegistry().ids()) {
                        if (id.startsWith(input)) {
                            result.add(id);
                        }
                    }
                }
                case "testdummy", "dummy" -> result.addAll(Arrays.asList("spawn", "list", "clear"));
                default -> {
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    result.add(p.getName());
                }
            } else if (args[0].equalsIgnoreCase("updatelore")) {
                result.add("all");
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    result.add(p.getName());
                }
            } else if ((args[0].equalsIgnoreCase("testdummy") || args[0].equalsIgnoreCase("dummy"))
                    && args[1].equalsIgnoreCase("clear")) {
                result.add("all");
            }
        }
        return result;
    }
}
