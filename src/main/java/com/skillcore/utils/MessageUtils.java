package com.skillcore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Message / chat formatting toolkit.
 */
public final class MessageUtils {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtils() {
    }

    /**
     * Translate ampersand color codes.
     */
    public static String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Strip color codes.
     */
    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(color(input));
    }

    /**
     * 解析 {@code &} 颜色码为 Component。
     * <p>
     * 入参必须是<b>未翻译</b>的原始字符串（保留 {@code &}）：{@code legacyAmpersand()} 只认 {@code &}，
     * 传 {@link #color(String)} 处理过的 {@code §} 文本会原样显示成 "§c"。
     */
    public static Component component(String input) {
        return LEGACY.deserialize(input == null ? "" : input);
    }

    /**
     * Send a color-coded message to a player.
     */
    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(color(message));
    }

    /**
     * Send with prefix from config string.
     */
    public static void sendPrefixed(CommandSender sender, String prefix, String message) {
        send(sender, prefix + message);
    }

    /**
     * Send action bar message.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null) {
            return;
        }
        // 直接交给 component 解析 & 码；不能再先 color() 翻成 §，否则动作栏会原样显示 "§c冷却中"
        player.sendActionBar(component(message));
    }

    /**
     * Send title / subtitle.
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }
        player.showTitle(net.kyori.adventure.title.Title.title(
                component(title == null ? "" : title),
                component(subtitle == null ? "" : subtitle),
                net.kyori.adventure.title.Title.Times.times(
                        net.kyori.adventure.util.Ticks.duration(fadeIn),
                        net.kyori.adventure.util.Ticks.duration(stay),
                        net.kyori.adventure.util.Ticks.duration(fadeOut)
                )
        ));
    }

    /**
     * Replace placeholders like {key} in a template.
     */
    public static String placeholder(String template, String... keyValues) {
        if (template == null) {
            return "";
        }
        if (keyValues == null || keyValues.length < 2) {
            return template;
        }
        String result = template;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] == null) continue;
            result = result.replace("{" + keyValues[i] + "}", keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return result;
    }

    /**
     * Replace then colorize.
     */
    public static String format(String template, String... keyValues) {
        return color(placeholder(template, keyValues));
    }

    /**
     * Progress bar string.
     *
     * @param percent 0.0 - 1.0
     */
    public static String progressBar(double percent, int length, char filled, char empty) {
        percent = MathUtils.clamp(percent, 0.0, 1.0);
        int filledCount = (int) Math.round(percent * length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(i < filledCount ? filled : empty);
        }
        return sb.toString();
    }

    /**
     * HP bar with colors.
     */
    public static String healthBar(double current, double max, int length) {
        double percent = max <= 0 ? 0 : current / max;
        String bar = progressBar(percent, length, '|', '|');
        String colorCode = percent > 0.5 ? "&a" : percent > 0.25 ? "&e" : "&c";
        return colorCode + bar + " &f" + MathUtils.format1(current) + "&7/&f" + MathUtils.format1(max);
    }

    /**
     * Center a message (simple approximation).
     */
    public static String center(String message) {
        if (message == null) {
            return "";
        }
        int spaces = Math.max(0, (32 - strip(message).length()) / 2);
        return " ".repeat(spaces) + message;
    }
}
