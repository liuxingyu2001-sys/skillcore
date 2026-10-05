package com.skillcore.command;

import com.skillcore.SkillCorePlugin;
import com.skillcore.utils.MathUtils;
import com.skillcore.utils.MessageUtils;
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
            MessageUtils.send(sender, "&7(空) 请在 weapons.yml 配置武器");
            return;
        }
        for (var entry : all) {
            var w = entry.weapon();
            MessageUtils.send(sender, "&e" + w.id() + " &7- " + w.displayName()
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
        MessageUtils.send(sender, "&6===== " + w.displayName() + " &6=====");
        MessageUtils.send(sender, "&7ID: &f" + w.id());
        if (w.description() != null && !w.description().isEmpty()) {
            MessageUtils.send(sender, w.description());
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
     * 管理员手动更新 lore — 按 weapons.yml 当前数值刷新占位符。
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

    private void sendHelp(CommandSender sender, String prefix) {
        MessageUtils.send(sender, "&6===== SkillCore =====");
        MessageUtils.send(sender, "&7技能绑定在武器上 — 手持武器才能使用技能");
        MessageUtils.send(sender, "&e/sc give <weaponId> [player] &7- 发放技能武器");
        MessageUtils.send(sender, "&e/sc weapons &7- 武器列表");
        MessageUtils.send(sender, "&e/sc info <weaponId> &7- 武器详情");
        MessageUtils.send(sender, "&e/sc updatelore <weaponId> [player|all] &7- 刷新 lore");
        MessageUtils.send(sender, "&e/sc reload &7- 重载配置");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(Arrays.asList("give", "weapons", "info", "updatelore", "reload", "help"));
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "info", "updatelore" -> {
                    String input = args[1].toLowerCase(Locale.ROOT);
                    for (String id : plugin.getWeaponRegistry().ids()) {
                        if (id.startsWith(input)) {
                            result.add(id);
                        }
                    }
                }
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
            }
        }
        return result;
    }
}
