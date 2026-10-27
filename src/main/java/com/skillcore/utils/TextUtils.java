package com.skillcore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本解析工具 — 同时支持 MiniMessage 与旧式 {@code &} 颜色码。
 * <p>
 * 规则：文本中含 {@code <tag>} 形式标签时按 MiniMessage 解析（支持
 * {@code <gradient>} / {@code <color>} / {@code <bold>} 等），否则按 {@code &} 旧式颜色解析。
 * MiniMessage 分支会先把文本里的 {@code &} 码转成对应的 MiniMessage 标签，
 * 因此 {@code <gradient:red:blue>神剑</gradient> &7稀有} 这种混写也能正常显示。
 * MiniMessage 解析失败时自动回退旧式解析，保证不会抛错。
 */
public final class TextUtils {

    /** 匹配类似 {@code <tag>} / {@code </tag>} / {@code <#RRGGBB>} 的 MiniMessage 标签。 */
    private static final Pattern MINI_TAG = Pattern.compile("<[#a-zA-Z/!][^<>]*>");
    /** 匹配旧式颜色码（{@code &c} / {@code &l} 等）。 */
    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();
    /** 输出 § 颜色码（支持 hex），复用一个实例避免每次 toLegacy 都重建。 */
    private static final LegacyComponentSerializer SECTION =
            LegacyComponentSerializer.builder().character('§').hexColors().build();

    private TextUtils() {
    }

    /** 解析为 Adventure Component（默认取消斜体，客户端显示名/lore 不再倾斜）。 */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        Component result;
        if (looksMiniMessage(input)) {
            try {
                result = MINI.deserialize(legacyCodesToMiniTags(input));
            } catch (Exception ignored) {
                result = LEGACY.deserialize(input);
            }
        } else {
            result = LEGACY.deserialize(input);
        }
        // 显示名/lore 默认斜体是客户端行为，这里统一改为非斜体（除非文本显式写 <italic:true>）
        return result.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * 把 {@code &} 颜色码转成 MiniMessage 标签，供「MiniMessage + & 混写」的文本使用。
     * <p>
     * MiniMessage 本身不解析 {@code &}，不转换的话 {@code <gradient>…</gradient> &7词缀}
     * 会把 {@code &7} 原样显示出来。{@code &} 后面不是合法颜色码时保持原样。
     */
    private static String legacyCodesToMiniTags(String input) {
        if (input.indexOf('&') < 0) {
            return input;
        }
        Matcher m = LEGACY_CODE.matcher(input);
        StringBuilder out = new StringBuilder(input.length() + 16);
        while (m.find()) {
            String tag = miniTagOf(Character.toLowerCase(m.group(1).charAt(0)));
            m.appendReplacement(out, Matcher.quoteReplacement(tag == null ? m.group() : tag));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** 单个旧式颜色码字符 -> MiniMessage 标签（未知返回 null，保持原样）。 */
    private static String miniTagOf(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    /** 是否像 MiniMessage。 */
    public static boolean looksMiniMessage(String input) {
        return input != null && MINI_TAG.matcher(input).find();
    }

    /** 转成旧式 § 字符串（用于不支持 Component 的接口，渐变会退化为单色）。 */
    public static String toLegacy(String input) {
        return SECTION.serialize(parse(input));
    }
}
