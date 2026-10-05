package com.skillcore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/**
 * 文本解析工具 — 同时支持 MiniMessage 与旧式 {@code &} 颜色码。
 * <p>
 * 规则：文本中含 {@code <tag>} 形式标签时按 MiniMessage 解析（支持
 * {@code <gradient>} / {@code <color>} / {@code <bold>} 等），否则按 {@code &} 旧式颜色解析。
 * MiniMessage 解析失败时自动回退旧式解析，保证不会抛错。
 */
public final class TextUtils {

    /** 匹配类似 {@code <tag>} / {@code </tag>} / {@code <#RRGGBB>} 的 MiniMessage 标签。 */
    private static final Pattern MINI_TAG = Pattern.compile("<[#a-zA-Z/!][^<>]*>");

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

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
                result = MINI.deserialize(input);
            } catch (Exception ignored) {
                result = LEGACY.deserialize(input);
            }
        } else {
            result = LEGACY.deserialize(input);
        }
        // 显示名/lore 默认斜体是客户端行为，这里统一改为非斜体（除非文本显式写 <italic:true>）
        return result.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** 是否像 MiniMessage。 */
    public static boolean looksMiniMessage(String input) {
        return input != null && MINI_TAG.matcher(input).find();
    }

    /** 转成旧式 § 字符串（用于不支持 Component 的接口，渐变会退化为单色）。 */
    public static String toLegacy(String input) {
        return LegacyComponentSerializer.builder()
                .character('§')
                .hexColors()
                .build()
                .serialize(parse(input));
    }
}
