package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Centralised text parsing for SummitCustomArmor.
 *
 * Supported formats (can be mixed freely in any string):
 *
 *   & codes      — §-style colour codes  e.g. &6, &l, &r
 *   <#RRGGBB>    — Hex colour            e.g. <#FF5500>text</color>  or  <#FF5500>text</#FF5500>
 *   <gradient:#hex1:#hex2>text</gradient>
 *                — Gradient between two hex colours
 *   Any MiniMessage tag — <bold>, <italic>, <underlined>, <strikethrough>,
 *                         <obfuscated>, <rainbow>, <transition:...>, etc.
 *
 * Config examples:
 *   name: "<gradient:#4DCCFF:#FFFFFF>Key Finder Chestplate</gradient>"
 *   message: "%prefix% &aYou found a <#FFD700>Legendary Key</color>!"
 *   lore line: "<#888888>Owner: <white>%owner%"
 */
public final class TextUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {}

    /**
     * Parses a string that may contain & codes AND/OR MiniMessage tags.
     * & codes are converted to MiniMessage first so they work alongside
     * gradient, hex colour, etc.
     *
     * Returns a Component with italic decoration explicitly disabled
     * (suitable for item display names and lore).
     */
    public static Component parse(String text) {
        return parseRaw(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Like parse() but does NOT disable italic — use for chat messages.
     */
    public static Component parseMessage(String text) {
        return parseRaw(text);
    }

    // -------------------------------------------------------------------------

    private static Component parseRaw(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        // Step 1: convert & codes to §  (legacy → plain string with § chars)
        String legacyConverted = LEGACY.serialize(LEGACY.deserialize(text));

        // Step 2: convert § codes to MiniMessage colour tags so MiniMessage
        //         can handle them alongside real MiniMessage tags
        String mm = legacyToMiniMessage(legacyConverted);

        // Step 3: parse through MiniMessage
        return MM.deserialize(mm, TagResolver.empty());
    }

    /**
     * Converts a §-coded string into MiniMessage-compatible colour tags.
     * §6Hello → <gold>Hello</gold>   §rHello → <reset>Hello</reset>
     * Unknown § codes are stripped.
     */
    private static String legacyToMiniMessage(String input) {
        if (!input.contains("§")) return input;

        StringBuilder sb = new StringBuilder(input.length() + 32);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String tag = legacyCodeToTag(code);
                if (tag != null) sb.append(tag);
                i++; // skip the code char
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String legacyCodeToTag(char code) {
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
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default  -> null;
        };
    }
}
